package uci.bcd2ptz.visca;

import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.ptz.ParameterState;
import uci.bcd2ptz.ptz.PendingPacket;

import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Configurable VISCA PTZ controller, based on camera configuration and commands syntax from JSON data file.
 */
public class ViscaPTZController {

    private final ViscaCommandBuilder commandBuilder;
    private static final Logger LOGGER = Logger.getLogger(ViscaPTZController.class.getName());

    //Camera <=> controller connection>
    private final PTZCamera camera;
    private PTZCameraManager cameraManager;

    //Visca payloads and wrapping templates
    private final Map<String, byte[]> udpTemplate = new LinkedHashMap<>();
    private final Map<String, byte[]> viscaPayloadType = new LinkedHashMap<>();
    private final int payloadOffset;

    private static final Object viscaLock = new Object();

    /**
     * Controller class constructor
     *
     * @param config plik konfiguracyjny VISCA (JSON)
     */

    public ViscaPTZController(PTZCamera camera, ViscaConfig config) {
        //podłączenie kamery i CommandBuildera
        this.commandBuilder = new ViscaCommandBuilder(config);
        this.camera = camera;
        this.cameraManager = camera.getCameraManager();

        //Wczytanie formatek pakietów UDP i typó komuynikatów VISCA
        Map<String, Object> transport = commandBuilder.getConfig().getTransport();
        Map<String, Object> packetType = (Map<String, Object>) transport.get("packetType");
        Map<String, Object> payloadSize = (Map<String, Object>) transport.get("payloadSize");
        Map<String, Object> sequence = (Map<String, Object>) transport.get("sequence");

        udpTemplate.put("type", new byte[((Number) packetType.get("size")).intValue()]);
        udpTemplate.put("payloadLength", new byte[((Number) payloadSize.get("size")).intValue()]);
        udpTemplate.put("sequence", new byte[ ((Number) sequence.get("size")).intValue()]);

        this.payloadOffset = udpTemplate.get("type").length + udpTemplate.get("payloadLength").length + udpTemplate.get("sequence").length;


        Map<String, Object> types = (Map<String, Object>)
                        ((Map<String, Object>)
                                transport.get("packetType"))
                                .get("types");

        types.forEach((name, definition) ->
                viscaPayloadType.put(
                        name,
                        parseByteArray((List<String>) definition)
                )
        );

        //INITIAL STATUS
    }

    /* ===================================================
           COMMUNICATION AND VISCA COMMANDS HANDLING
       ===================================================*/


    /** Parameterless VISCA command execution
     * @param commandName
     */
    public void executeViscaCommand(String commandName) {

        try {
            byte[] command = commandBuilder.buildCommand(commandName);
            sendViscaCommand(command,"command");

        } catch (Exception e) {
            System.err.println("VISCA command failed: " + commandName);
            e.printStackTrace();
        }
    }

    /**
     *  Parametric VISCA command execution
     * @param commandName
     * @param parameters - mapa <String, Object> zawierająca parametry wywołania
     * @return
     */
    public void executeViscaCommand(String commandName, Map<String, Object> parameters) {

        try {
            byte[] command = commandBuilder.buildCommand(commandName, parameters);
            sendViscaCommand(command,"command");

            //DEBUG:
            //System.out.println("========= CONTROLLER VISCA =========\nTX: " + bytesToHex(command, command.length));

        } catch (Exception e) {
            System.err.println("VISCA command failed: " + commandName);
            e.printStackTrace();
        }
    }

    /**
     *  Execution of VISCA status inquires
     * @param commandName
     * @return byte array containing VISCA response payload or null if no response received
     */
    private byte[] executeViscaInquiry(String commandName) {

        try {
            byte[] command = commandBuilder.buildCommand(commandName);

            PendingPacket pending = sendViscaCommand(command, "inquiry");

            if (pending == null){
                return null;
            }

            long timeout = System.currentTimeMillis() + 1000;

            while (System.currentTimeMillis() < timeout) {

                long remaining = timeout - System.currentTimeMillis();

                byte[] packet = pending.getResponses().poll(
                        remaining,
                        TimeUnit.MILLISECONDS);

                if (packet == null || packet.length <= payloadOffset){
                    continue;
                }

                byte[] viscaPayload = Arrays.copyOfRange(packet, this.payloadOffset, packet.length);

                //SKIP ACK
                if ((viscaPayload[0] & 0xFF) == 0x90 && (viscaPayload[1] & 0xF0) == 0x40) {
                    continue;
                }
                if ((viscaPayload[0] & 0xFF) == 0x90 && (viscaPayload[1] & 0xF0) == 0x50){
                    //DEBUG:
                    //System.out.println("Received Payload:"  + bytesToHex(viscaPayload, viscaPayload.length));

                    return viscaPayload;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Inquiry failed " + commandName, e);
        }
        return null;
    }

    private byte[] wrapViscaPayload(byte[] viscaPayload, String type) {

        byte [] commandType = viscaPayloadType.get(type);

        if (commandType == null) {
            throw new IllegalArgumentException("Unknown packet type: " + type);
        }

        byte[] typeBytes = udpTemplate.get("type");

        // obliczanie długości pakietu
        int packetSize =
                udpTemplate.get("type").length +
                udpTemplate.get("payloadLength").length +
                udpTemplate.get("sequence").length +
                viscaPayload.length;

        byte[] packet = new byte[packetSize];

        // Packet Type
        System.arraycopy(
                commandType,
                0,
                packet,
                0,
                typeBytes.length);

        // Obliczanie i dodawania payloadSize
        int payloadLength = viscaPayload.length;

        int payloadSizeOffset = udpTemplate.get("type").length;

        int payloadSizeLength =udpTemplate.get("payloadLength").length;

        for (int i = 0;i < payloadSizeLength;i++) {
            packet[payloadSizeOffset
                       + payloadSizeLength
                       - 1
                       - i
                    ]
                    =(byte)(payloadLength>> (8 * i));
        }


         // Sequence Number
        int sequence = camera.getViscaSessionID();

        //DEBUG:
       /* System.out.println("========== VISCA command wrapper ==========");
        System.out.println("New session = " + sequence);
        System.out.println("CALLER = " + Thread.currentThread().getStackTrace()[2]);*/

        int sequenceOffset =  udpTemplate.get("type").length + udpTemplate.get("payloadLength").length;

        int sequenceLength = udpTemplate.get("sequence").length;

        for (int i = 0;i < sequenceLength;i++) {
            packet[
                    sequenceOffset
                            + sequenceLength
                            - 1
                            - i
                    ] =(byte)(sequence>> (8 * i));
        }

        camera.setViscaSessionID(sequence + 1);

         // VISCA Payload
        int payloadOffset =  udpTemplate.get("type").length +
                udpTemplate.get("payloadLength").length +
                udpTemplate.get("sequence").length;

        System.arraycopy(
                viscaPayload,
                0,
                packet,
                payloadOffset,
                viscaPayload.length
        );

        //DEBUG:
        //System.out.println("UDP Pakiet send: " + bytesToHex(packet, packet.length));

        return packet;
    }

    /**
     * Wysyła komendę VISCA w formacie VISCA-over-UDP do kamery.
     *
     * Opakowuje payload VISCA w nagłówek AVer UDP (8 bajtów) i wysyła pakiet.
     *
     * @param viscaPayload tablica bajtów zawierająca komendę VISCA (bez nagłówka UDP)
     * @param type typ komendy (inquiry/command)
     */
    public PendingPacket sendViscaCommand(byte[] viscaPayload, String type) {


        //DEBUG:
        //System.out.println("VISCA TX [" +Thread.currentThread().getName() + "] : " + bytesToHex(viscaPayload,viscaPayload.length));
        try {
            byte[] command =wrapViscaPayload(viscaPayload,type);

            //DEBUG:
            //System.out.println("======= Send VISCA command ============\nUDP TX: " + bytesToHex(command,command.length));

            DatagramPacket dp =
                    new DatagramPacket(
                            command,
                            command.length,
                            InetAddress.getByName(
                                 camera.getIpAddress()
                            ),
                            camera.getCameraPort()
                    );

            //camera.getSocket().send(dp);
            return cameraManager.sendPacket(camera, command);

        } catch (Exception e) {

            LOGGER.log(Level.SEVERE,"Błąd wysyłania komendy VISCA", e);
        }
        return null;
    }

     /* =====================================
            CAMERA STATE SYNCHRONIZATION
       =====================================*/

    public boolean syncPosition() {

        try {
            byte [] response = executeViscaInquiry("panTiltPositionRequest");

            if (response == null || response.length < 10){
                LOGGER.log(Level.WARNING, "No valid PAN/TILT inquiry response received");
                return false;
            }

            List <String> responseTemplate = commandBuilder.getCommand("panTiltPositionRequest").getResponseTemplate();

            int responseOffset = 0;

            Map <String, Integer> decodedValues = new HashMap<>();

            for (String token : responseTemplate){
                if (!token.startsWith("{")|| !token.endsWith("}")) {
                    responseOffset++;
                    continue;
                }
                if (token.startsWith("{") && token.endsWith("}")){
                    FieldDefinition field = getFieldDefinition(token.substring(1, token.length() -1));
                    if (field == null){
                        LOGGER.log(Level.WARNING, "No " + token + " field definition in response template");
                        continue;
                    }
                    EncodingDefinition encoding = field.getEncoding();
                    int currentValue = 0;
                    int length = encoding.getSegments().size();
                    for (int i = 0; i < length; i++){
                        //DEBUG:
                        //System.out.println("token: "+ token + "    | Response " + bytesToHex(response, response.length));
                        currentValue = (currentValue << 4) | (response[responseOffset + i] & 0x0F);
                    }
                    responseOffset += length;
                    //DEBUG:
                    //System.out.println("Decoded " + token + " = " + currentValue);

                    decodedValues.put(token.substring(1, token.length() -1), currentValue);

                }
            }

            //DEBUG:
            //System.out.println("Receiveid PAN/TILT position" + decodedValues.toString());

            int panVisca = decodedValues.get("panPosition");
            int tiltVisca = decodedValues.get("tiltPosition");


            FieldDefinition panField = getFieldDefinition("panPosition");
            Map<String, Object> panMapping = panField.getMapping();
            Map<String, Object> panDeviceRange = (Map<String, Object>) panMapping.get("deviceRange");
            Map<String, Object> panRange = (Map<String, Object>) panMapping.get("mechanicalRange");

            //DEBUG:
            //System.out.println("PAN RANGE = " + panDeviceRange);

            int positivePanLimit = Integer.parseInt((String) panDeviceRange.get("positiveLimit"), 16);
            int negativePanLimit = Integer.parseInt((String) panDeviceRange.get("negativeLimit"), 16);
            double maxPanDegrees = ((Number) panRange.get("maxDegrees")).doubleValue();
            double minPanDegrees = ((Number) panRange.get("minDegrees")).doubleValue();
            double panDegrees;
            if (panVisca <= positivePanLimit) {
                panDegrees = ((double) panVisca / positivePanLimit) * maxPanDegrees;
            } else {
                int span = 0x10000 - negativePanLimit;
                int delta = 0x10000 - panVisca;
                panDegrees = -((double) delta / span) * Math.abs(minPanDegrees);
            }
            camera.getParameter("panPosition").setCurrent(panDegrees);

            FieldDefinition tiltField = getFieldDefinition("tiltPosition");
            Map<String, Object> tiltMapping = tiltField.getMapping();
            Map<String, Object> tiltDeviceRange = (Map<String, Object>) tiltMapping.get("deviceRange");

            //DEBUG
            //System.out.println("TILT RANGE = " + tiltDeviceRange);

            Map<String, Object> tiltRange = (Map<String, Object>) tiltMapping.get("mechanicalRange");
            int positiveTiltLimit = Integer.parseInt((String) tiltDeviceRange.get("upLimit"), 16);
            int negativeTiltLimit = Integer.parseInt((String) tiltDeviceRange.get("downLimit"), 16);
            double maxTiltDegrees = ((Number) tiltRange.get("maxDegrees")).doubleValue();
            double minTiltDegrees = ((Number) tiltRange.get("minDegrees")).doubleValue();
            double tiltDegrees;
            if (tiltVisca <= positiveTiltLimit) {
                tiltDegrees = ((double) tiltVisca / positiveTiltLimit) * maxTiltDegrees;
            } else {
                int span = 0x10000 - negativeTiltLimit;
                int delta = 0x10000 - tiltVisca;
                tiltDegrees = ((double) delta / span) * Math.abs(minTiltDegrees);
            }
            camera.getParameter("tiltPosition").setCurrent(tiltDegrees);

            //DEBUG:
            //System.out.println("Camera position updated: PAN DEG: " + panDegrees + " | hex: " + panVisca + "    TILT DEG: " + tiltDegrees + "  |  hex: " + tiltVisca);
            return true;

        } catch (Exception e) {
            System.out.println("SYNC POSITION ERROR");
            e.printStackTrace();
            return false;
        }
    }

    public boolean parameterInquiry(String parameterName) {

        try {
            //Wywołanie pan/tilt - wariant nietypowy
            if ("panPosition".equals(parameterName) ||
                    "tiltPosition".equals(parameterName)) {
                return syncPosition();
            }

            return executeParameterInquiry(parameterName);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Parameter inquiry failsed for " + parameterName, e);
            return false;
        }
    }


    private boolean executeParameterInquiry(String parameterName){
        //Sprawdzenie definicji inquiry
        CommandDefinition inquiry = commandBuilder.getCommand(parameterName);

        if (inquiry == null) {
            LOGGER.warning("No inquiry definition: " + parameterName);
            return false;
        }

        byte[] response = executeViscaInquiry(parameterName);

        //debug:
        //System.out.println("Controler parameterInquiry RX: " + bytesToHex(response, response.length));

        if (response == null || response.length < 4) {
            LOGGER.warning("Invalid " + parameterName + " inquiry response." +
            "\nReceived response:" + ((response ==null) ? "null" : bytesToHex(response, response.length)));
            return false;
        }

        // Pobranie definicji pola

        FieldDefinition field = getFieldDefinition(parameterName);

        if (field == null) {
            LOGGER.warning("Invalid " + parameterName + " field definition.");
            return false;
        }

        // Dekodowanie odpowiedzi VISCA
        List<String> responseTemplate = commandBuilder.getCommand(parameterName).getResponseTemplate();

        if (responseTemplate == null || responseTemplate.isEmpty()) {
            LOGGER.warning("Invalid " + parameterName + " response definition.");
            return false;
        }

        String fieldName = null;
        int fieldIndex = -1;
        for (int i = 0; i < responseTemplate.size(); i++) {
            String token = responseTemplate.get(i);
            if (token.startsWith("{") && token.endsWith("}")) {
                fieldName = token.substring(1, token.length() - 1);
                fieldIndex = i;
                break;
            }
        }
        if (fieldName == null) {
            LOGGER.warning("Invalid " + parameterName + " response field definition.");
            return false;
        }

        //debug
        //System.out.println("FIELD INDEX = " + fieldIndex + "  response size = " + responseTemplate.size());


//        for (int i = 0; i < field.getEncoding().getSegments().size(); i++) {
//            System.out.println("RESP[" + (fieldIndex  + i) + "] = " + String.format("%02X",response[fieldIndex + i]));
//        }

        long currentValue = 0;
        EncodingDefinition encoding = field.getEncoding();

        switch (encoding.getType()) {

            case "visca-nibble":
                int nibbleLength = encoding.getSegments().size();
                for (int i = 0; i < nibbleLength; i++) {
                    int value = response[fieldIndex + i] & 0x0F;
                    currentValue = (currentValue << 4 | value);
                }
                break;
            case "enum":
                currentValue = response[fieldIndex] & 0xFF;
                break;
            default:
                LOGGER.log(Level.WARNING, "Unsupported VISCA encoding type:" + encoding.getType());
                return false;
        }

        //parsowanie na wartości uzytkowe

        Map<String, Object> mapping = field.getMapping();
        String mappingType = (String)mapping.get("type");
        Map<String, Object> values = (Map<String, Object>) field.getMapping().get("values");

        //Debug
        //System.out.println("Mapping values:\n" + values.toString());

        switch (mappingType.toLowerCase()) {

            //--- lookup-type map parsing + nearest match
            case "lookup":
                if (values == null){
                    LOGGER.log(Level.WARNING, "No lookup mapping found for " + parameterName);
                    return false;
                }

                Long nearestDistance = Long.MAX_VALUE;
                String nearestKey = null;

                long minValue = Long.MAX_VALUE;
                String minKey = null;

                long maxValue = Long.MIN_VALUE;
                String maxKey = null;

                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    long lookupValue = ((Number) entry.getValue()).longValue();

                    //DEBUG
                    //System.out.println("lookup value = " + lookupValue + " Current value = " + currentValue);

                    // Exact match
                    if (lookupValue == currentValue) {
                        currentValue = Long.parseLong(entry.getKey());
                        nearestKey = null;
                        break;
                    }

                    //Nearest value
                    long distance = Math.abs(lookupValue - currentValue);

                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestKey = entry.getKey();
                    }

                    // Min / Max tracking
                    if (lookupValue < minValue) {
                        minValue = lookupValue;
                        minKey = entry.getKey();
                    }

                    if (lookupValue > maxValue) {
                        maxValue = lookupValue;
                        maxKey = entry.getKey();
                    }
                }

                // No exact match found
                if (nearestKey != null) {
                    if (currentValue < minValue) {
                        currentValue = Long.parseLong(minKey);
                    } else if (currentValue > maxValue) {
                        currentValue = Long.parseLong(maxKey);
                    } else {
                        currentValue = Long.parseLong(nearestKey);
                    }
                    //DEBUG
                    //System.out.println("Lookup correction: raw=" + currentValue + " mapped=" + nearestKey);
                }
                break;

            // parsing for enum maps (AF MODE, AE MODE, WB MODE)
            case "enum":
                if (values == null){
                    LOGGER.log(Level.WARNING, "No lookup mapping found for " + parameterName);
                    return false;
                }
                //Debug
                //System.out.println("Mapping values:\n" + values.toString() + "current value: " + currentValue);

                String enumValue = (String)values.get(String.format("%02X", currentValue));

                if( enumValue == null){
                    LOGGER.warning("Unknown enum value for " + parameterName + ": " + currentValue);
                    return false;
                }

                ParameterState state = camera.getParameters().get(parameterName);

                if (state == null) {
                    state = new ParameterState();

                    camera.getParameters().put(parameterName, state);
                }

                state.setCurrentState(enumValue);
                break;

            // plain numeric values (ZOOM, FOCUS)
            default:
                state = camera.getParameters().get(parameterName);

                if (state == null) {
                    state = new ParameterState();
                    camera.getParameters().put(parameterName, state);
                }

                state.setCurrent(currentValue);

                if (field.getRange() != null) {
                    state.setMin(field.getMinRangeValue());
                    state.setMax(field.getMaxRangeValue());
                }
        }

        // Aktualizacja stanu w ParameterState kamery

        ParameterState state = camera.getParameters().get(parameterName);

        if (state == null) {
            state = new ParameterState();
            camera.getParameters().put(parameterName, state);
        }

        state.setCurrent(currentValue);

        if (field.getRange() != null) {
            state.setMin(((Number) field.getMapping().get("mappedMin")).doubleValue());
            state.setMax(((Number) field.getMapping().get("mappedMax")).doubleValue());
        }
        return true;
    }

    @Deprecated
    public boolean powerStatusRequest() {

        try {

            CommandDefinition inquiry = commandBuilder.getCommand("powerStatusRequest");

            //DEBUG
            //System.out.println("INquiry data\n" + inquiry.toString());

            if(inquiry ==null || inquiry.getResponseTemplate() ==null){
                LOGGER.log(Level.WARNING, "Missing response template for powerStatusRequest");
                return false;
            }

            byte[] response = executeViscaInquiry("powerStatusRequest");

            if (response == null) {
                LOGGER.log(Level.WARNING, "No power state response");
                return false;
            }

            List<String> responseTemplate = inquiry.getResponseTemplate();
            if (responseTemplate == null || responseTemplate.isEmpty()){
                LOGGER.warning("Invalid powerStatus response definition.");
                return false;
            }

            int offset = 0;
            for (String token : responseTemplate){
                if(!token.startsWith("{") && !token.endsWith("}")){
                    offset++;
                    continue;
                }

                FieldDefinition field = getFieldDefinition(token.substring(1, token.length() -1));
                if ("enum".equalsIgnoreCase(inquiry.getResponseType())){
                    String key = String.format("%02X", response[offset] & 0xFF);
                    Object mappedValue = field.getMapping().get(key);
                    if (mappedValue == null){
                        LOGGER.warning("Unknown enum value: " + key);
                        return false;
                    }
                    if ("ON".equalsIgnoreCase(mappedValue.toString())){
                        return true;
                    } else {
                        return false;
                    }
                }
            }

            System.out.println("NO VALID POWER STATUS RESPONSE");
            return false;

        } catch (Exception e) {
            System.out.println("POWER STATUS TEST FAILED");
            e.printStackTrace();
            return false;
        }
    }

    /* ====================================================
                    CAMERA MOVEMENT HANDLERS
        =================================================== */

    public void moveAbsolute(double panValue, double tiltValue, int speed) {

        //SCAFF
        //System.out.println("moveAbsolute - Received values: PAN " + panValue + "  |  TILT " + tiltValue + "  | SPEED " + speed);

        PTZCamera camera = this.camera;

        synchronized (viscaLock) {

            //ograniczniki zakresów do sprzętu

            double clampeddPan = camera.clampPan(panValue);
            double clampedTilt = camera.clampTilt(tiltValue);

            //SCAFF
            //System.out.println("moveAbsolute - Used values: PAN " + boundedPan + "  |  TILT " + boundedTilt);

            try {
                Map<String,Object> params =
                        new HashMap<>();

                params.put("panSpeed", speed);
                params.put("tiltSpeed", speed);

                params.put("panPosition", clampeddPan);
                params.put("tiltPosition", clampedTilt);

                byte[] command = commandBuilder.buildCommand("absolutePosition", params);
                String commandType = commandBuilder.getCommand("absolutePosition").getType();
                sendViscaCommand(command, commandType);

                camera.getParameter("panPosition").setCurrent(clampeddPan);
                camera.getParameter("tiltPosition").setCurrent(clampedTilt);

            } catch (Exception e) {

                LOGGER.log(
                        Level.SEVERE,
                        "MOVE ABS ERROR",
                        e
                );
            }
        }
    }

    /**
     *  Ruch PAN/TILT w trybie Joystick
     */

    public void movePanTilt(int panValue, int tiltValue) {

        //SCAFF
       // System.out.println("movePanTilt - Received values: PAN " + panValue + "  |  TILT " + tiltValue);
       /* if (panValue == 0 && tiltValue == 0){
            System.out.println("movePanTilt - STOP REQUEST");
            camera.stop();
        }*/
        //System.out.println("Controller hash = " + System.identityHashCode(this));

        Map<String, Object> params = new HashMap<>();

        // przeliczenie prędkości do pamaterów kamery
        FieldDefinition panSpeedField = commandBuilder.getField("panSpeed");
        Map<String, Object> range = panSpeedField.getRange();
        int minSpeed = ((Number)range.get("min")).intValue();
        int maxSpeed =((Number)range.get("max")).intValue();
        int panSpeed = minSpeed + (int) Math.round((Math.abs(panValue) / 64.0) * (maxSpeed - minSpeed));
        int tiltSpeed = minSpeed + (int) Math.round((Math.abs(tiltValue) / 64.0) * (maxSpeed - minSpeed));

        //ustalenie kierunku kierunek ruchu, wartość 64 = neutralna
        int panDirection = panValue > 0 ? 0x02 : panValue < 0 ? 0x01 : 0x03;
        int tiltDirection = tiltValue > 0 ? 0x01 : tiltValue < 0 ? 0x02 : 0x03;

        //parametry dla commandBuildera i budowa payload VISCA
        params.put("panSpeed", panSpeed);
        params.put("tiltSpeed", tiltSpeed);
        params.put("panDirection", panDirection);
        params.put("tiltDirection", tiltDirection);

        byte[] command = commandBuilder.buildCommand("movePanTilt", params);

        sendViscaCommand(command,"command");
        LOGGER.fine("PAN: speed = " + panSpeed + " | dir = " + panSpeed + " TILT  speed = " + tiltSpeed + " | dir = " + tiltDirection);
    }

    /**
     * Zatrzymaj ruch PAN i TILT.
     */
    public void stopPanTilt() {

        executeViscaCommand("stopPanTilt");

        //DEBUG:
        //System.out.println("UDP Pakiet send: " + bytesToHex(payload, payload.length));
    }

    /**
     * Sterowanie ZOOM kamerą
     *
     * @param zoomValue wartość pozycji ZOOM
     */
    public void moveZoomAbsolute(int zoomValue) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("zoomPosition", zoomValue);
            executeViscaCommand("absoluteZoom",params);

            //DEBUG
           // System.out.println("ZOOM ABS = " + zoomValue);
            LOGGER.fine("ZOOM ABS = " + zoomValue);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ZOOM command error",e);
        }
    }

    /**
     * Sterowanie ZOOM kamerą
     *
     * @param focusValue wartość pozycji ZOOM
     */
    public void moveFocusAbsolute(int focusValue) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("focusPosition", focusValue);
            executeViscaCommand("absoluteFocus",params);

            //DEBUG
             System.out.println("FOCUS ABS = " + focusValue);
            LOGGER.fine("FOCUS ABS = " + focusValue);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FOCUS command error",e);
        }
    }


    /**
     * Wznowienie zapisanego presetu kamery.
     *
     * @param presetName nazwa presetu według mapowania w konfiguracji kamety
     */
    public void recallPreset(String presetName) {

        try {
            FieldDefinition presetField = getFieldDefinition("presetId");


            //DEBUG
            /*System.out.println(
                    presetField.getMapping()
            );

            System.out.println(
                    presetField.getMapping()
                            .get(presetName)
                            .getClass()
            );

            System.out.println(
                    "presetName=[" + presetName + "]"
            );

            for (String key : presetField.getMapping().keySet()) {

                System.out.println(
                        "key=[" + key + "]"
                );
            }
*/
            Object mappedValue = presetField.getMapping().get(presetName);

          /*  System.out.println(mappedValue.toString());
*/
            if (mappedValue == null) {
                throw new IllegalArgumentException("Unknown preset Name: " + presetName);
            }

            int presetId = ((Number) mappedValue).intValue();
            Map<String, Object> params =new HashMap<>();
            params.put("presetId",presetId);

            byte[] command = commandBuilder.buildCommand("recallPreset",params);
            String commandType = commandBuilder.getCommand("recallPreset").getType();
            sendViscaCommand(
                    command,
                    commandType
            );

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,"Błąd recall presetu: "+ presetName, e);
        }
    }

    /* ============================================
           GETTERS, SETTERS, INTERNAL METHODS
       ============================================*/

    public FieldDefinition getFieldDefinition(String fieldName) {

        return commandBuilder.getField(fieldName);
    }

    private byte[] parseByteArray(List<String> values) {

        byte[] result = new byte[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] =
                    (byte) Integer.parseInt(
                            values.get(i),
                            16
                    );
        }
        return result;
    }

    /* =====================================
                     DEBUGGER
       ===================================== */

    public static String bytesToHex(byte[] data, int length) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[i]));
            if (i < length - 1) sb.append(' ');
        }
        return sb.toString();
    }


}

