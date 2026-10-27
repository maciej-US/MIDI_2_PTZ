package uci.midi2ptz.visca;

import com.fasterxml.jackson.databind.ObjectMapper;
import uci.midi2ptz.ptz.PTZCamera;
import uci.midi2ptz.ptz.PTZCameraManager;
import uci.midi2ptz.ptz.ParameterState;
import uci.midi2ptz.ptz.PendingPacket;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Configurable VISCA-over-IP camera controller.
 *
 * <p>This class provides a protocol-independent PTZ camera control  layer driven entirely by external VISCA configuration files.</p>
 *
 * <p>It translates high-level camera operations into VISCA protocol messages, manages VISCA-over-IP transport packets and synchronizes
 * camera state with the application runtime.</p>
 *
 * <h2>Responsibilities</h2> *
 * <ul>
 *     <li>VISCA command execution</li>
 *     <li>VISCA inquiry processing</li>
 *     <li>VISCA-over-IP packet wrapping</li>
 *     <li>Camera parameter synchronization</li>
 *     <li>Protocol response decoding</li>
 *     <li>Connection health monitoring</li>
 * </ul>
 *
 * <h2>Architecture</h2> *
 * <pre>
 * ControlCore
 *      |
 *      v
 * PTZCamera
 *      |
 *      v
 * ViscaPTZController
 *      |
 *      v
 * ViscaCommandBuilder
 *      |
 *      v
 * VISCA-over-IP
 *      |
 *      v
 * Camera
 * </pre>
 */

public class ViscaPTZController {

    private final ViscaCommandBuilder commandBuilder;
    private static final Logger LOGGER = Logger.getLogger(ViscaPTZController.class.getName());
    /**
     * Timeout used while waiting for VISCA inquiry responses from the camera.
     */
    private final long RESPONSE_TIMEOUT;

    //Camera <=> controller connection>
    private final PTZCamera camera;
    private final PTZCameraManager cameraManager;

    /**
     * Mapping between logical transport packet types and binary VISCA-over-IP packet headers.
     */
    private final Map<String, byte[]> udpTemplate = new LinkedHashMap<>();
    private final Map<String, byte[]> viscaPayloadType = new LinkedHashMap<>();

    /**
     * Number of transport header bytes preceding the VISCA payload within received packets.
     */
    private final int payloadOffset;

    /**
     * Global VISCA protocol lock.
     *
     **<p>Ensures that multi-packet VISCA transactions are executed sequentially.</p>
     */
    private static final Object viscaLock = new Object();


    /**
     * Creates a VISCA controller for the specified camera.
     *
     * @param camera associated camera instance
     * @param config VISCA protocol definition
     * @param responseTimeout response timeout in milliseconds
     */
    public ViscaPTZController(PTZCamera camera, ViscaConfig config, long responseTimeout) {

        this.commandBuilder = new ViscaCommandBuilder(config);
        this.camera = camera;
        this.cameraManager = camera.getCameraManager();
        this.RESPONSE_TIMEOUT = responseTimeout;

        Map<String, Object> transport = commandBuilder.getConfig().getTransport();
        Map<String, Object> packetType = (Map<String, Object>) transport.get("packetType");
        Map<String, Object> payloadSize = (Map<String, Object>) transport.get("payloadSize");
        Map<String, Object> sequence = (Map<String, Object>) transport.get("sequence");

        udpTemplate.put("type", new byte[((Number) packetType.get("size")).intValue()]);
        udpTemplate.put("payloadLength", new byte[((Number) payloadSize.get("size")).intValue()]);
        udpTemplate.put("sequence", new byte[ ((Number) sequence.get("size")).intValue()]);

        this.payloadOffset = udpTemplate.get("type").length + udpTemplate.get("payloadLength").length + udpTemplate.get("sequence").length;


        Map<String, Object> types = (Map<String, Object>)((Map<String, Object>)transport.get("packetType")).get("types");

        types.forEach((name, definition) ->viscaPayloadType.put(
                        name,
                        parseByteArray((List<String>) definition)
                )
        );
    }

    /**
     * Executes a parameterless VISCA command.
     *
     * @param commandName logical command name
     */
    public void executeViscaCommand(String commandName) {

        try {
            byte[] command = commandBuilder.buildCommand(commandName);
            sendViscaCommand(command,"command");

        } catch (Exception e) {
            System.err.println("VISCA command failed: " + commandName);
        }
    }

    /**
     * Executes a parameterized VISCA command.
     *
     * <p>Command parameters are encoded according to the field definitions contained in the VISCA configuration.</p>
     *
     * @param commandName logical command name
     * @param parameters command parameters
     */
    public void executeViscaCommand(String commandName, Map<String, Object> parameters) {

        try {
            byte[] command = commandBuilder.buildCommand(commandName, parameters);
            sendViscaCommand(command,"command");

        } catch (Exception e) {
            System.err.println("VISCA command failed: " + commandName);
        }
    }

    /**
     * Executes a VISCA inquiry request.
     *
     * <p>The method waits for a completion response and returns the received VISCA payload.</p>
     * @param commandName inquiry command name
     * @return VISCA response payload or {@code null}
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
                    return viscaPayload;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Inquiry failed " + commandName, e);
        }
        return null;
    }

    /**
     * Wraps a VISCA payload into a VISCA-over-IP packet.
     *
     * <p>The resulting packet includes transport headers, payload length information and a protocol sequence number.</p>     *
     * @param viscaPayload raw VISCA command payload @param type transport packet type     *
     * @return complete VISCA-over-IP packet
     */
    private byte[] wrapViscaPayload(byte[] viscaPayload, String type) {

        byte [] commandType = viscaPayloadType.get(type);

        if (commandType == null) {
            throw new IllegalArgumentException("Unknown packet type: " + type);
        }

        byte[] typeBytes = udpTemplate.get("type");

        // Packet size calculation
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

        // Payload size and append
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
        return packet;
    }

    /**
     * Sends a VISCA command using the configured VISCA-over-IP transport protocol.
     *
     * <p>The supplied VISCA payload is wrapped in the transport-specific packet header prior to transmission.</p>
     *
     * @param viscaPayload raw VISCA payload
     * @param type packet type such as {@code command} or {@code inquiry}
     *
     * @return pending packet tracker or {@code null}
     */
    public PendingPacket sendViscaCommand(byte[] viscaPayload, String type) {

        try {
            byte[] command = wrapViscaPayload(viscaPayload, type);

            PendingPacket pending = cameraManager.sendPacket(camera, command);

            if (pending == null) {
                camera.markOffline();
                cameraManager.notifyCameraStatusChanged(camera, false);
                LOGGER.warning("No PendingPacket for camera " + camera.getCameraId());
                return null;
            }

            if ("command".equalsIgnoreCase(type)){
                updateCameraStatus(pending);
            }

            return pending;

        } catch (InterruptedException e) {
            camera.markOffline();
            cameraManager.notifyCameraStatusChanged(camera, false);
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Camera " + camera.getCameraId() + " communication error", e);
        } catch (Exception e){
            cameraManager.notifyCameraStatusChanged(camera, false);
            LOGGER.log(Level.SEVERE, "Camera " + camera.getCameraId() + " sending VISCA command error", e);
        }

        return null;
    }

    /**
     * Synchronizes pan and tilt position information from the physical camera.
     *
     * <p>The received VISCA values are decoded and converted into application-level angular coordinates.</p>
     *
     * @return {@code true} if synchronization succeeded
     */
    public boolean syncPosition() {

        try {
            byte [] response = executeViscaInquiry("panTiltPositionRequest");

            if (response == null || response.length < 10){
                LOGGER.log(Level.WARNING, "No valid PAN/TILT inquiry response received");
                camera.markOffline();
                cameraManager.notifyCameraStatusChanged(camera, false);
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
                        currentValue = (currentValue << 4) | (response[responseOffset + i] & 0x0F);
                    }
                    responseOffset += length;

                    decodedValues.put(token.substring(1, token.length() -1), currentValue);

                }
            }

            int panVisca = decodedValues.get("panPosition");
            int tiltVisca = decodedValues.get("tiltPosition");

            FieldDefinition panField = getFieldDefinition("panPosition");
            Map<String, Object> panMapping = panField.getMapping();
            Map<String, Object> panDeviceRange = (Map<String, Object>) panMapping.get("deviceRange");
            Map<String, Object> panRange = (Map<String, Object>) panMapping.get("mechanicalRange");

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

            return true;

        } catch (Exception e) {
            System.out.println("SYNC POSITION ERROR: " + e.getMessage());
            return false;
        }
    }

    /**
     * Synchronizes a camera parameter with its current value reported by the device.
     *
     * @param parameterName parameter identifier
     * @return {@code true} if synchronization succeeded
     */
    public boolean parameterInquiry(String parameterName) {

        try {
            //PAN or TILT position - protocol specific call
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

    /**
     * Executes a generic VISCA parameter inquiry and updates the local parameter cache.
     *
     * @param parameterName parameter identifier
     * @return {@code true} if a valid response was received
     */
    private boolean executeParameterInquiry(String parameterName){

        CommandDefinition inquiry = commandBuilder.getCommand(parameterName);

        if (inquiry == null) {
            LOGGER.warning("No inquiry definition: " + parameterName);
            return false;
        }

        byte[] response = executeViscaInquiry(parameterName);

        if (response == null || response.length < 4) {
            LOGGER.warning("Invalid " + parameterName + " inquiry response." +
            "\nReceived response:" + ((response ==null) ? "null" : bytesToHex(response, response.length)));
            LOGGER.info("CAMERA " + camera.getCameraId() + " SET OFFLINE.");
            camera.markOffline();
            cameraManager.notifyCameraStatusChanged(camera, false);
            return false;
        }

        if (response.length == 3
                && (response[0] & 0xFF) == 0x90
                && (response[1] & 0xFF) == 0x51
                && (response[2] & 0xFF) == 0xFF) {

            LOGGER.fine("Ignoring VISCA status response: " + bytesToHex(response, response.length));
            return false;
        }

        FieldDefinition field = getFieldDefinition(parameterName);

        if (field == null) {
            LOGGER.warning("Invalid " + parameterName + " field definition.");
            return false;
        }
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

        switch (mappingType.toLowerCase()) {

            //--- lookup-type map parsing + nearest match
            case "lookup":
                if (values == null){
                    LOGGER.log(Level.WARNING, "No lookup mapping found for " + parameterName);
                    return false;
                }

                long nearestDistance = Long.MAX_VALUE;
                String nearestKey = null;

                long minValue = Long.MAX_VALUE;
                String minKey = null;

                long maxValue = Long.MIN_VALUE;
                String maxKey = null;

                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    long lookupValue = ((Number) entry.getValue()).longValue();

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
                }
                break;

            // parsing for enum maps (AF MODE, AE MODE, WB MODE)
            case "enum":
                if (values == null){
                    LOGGER.log(Level.WARNING, "No lookup mapping found for " + parameterName);
                    return false;
                }

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

        // ParameterState update in camera parameters

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

    /**
     * Requests the current camera power state.
     *
     * <p>The resulting value is converted into the internal {@link ParameterState} representation.</p>
     *
     * @return {@code true} if a valid power state response was received
     */
    public boolean powerStatusRequest() {

        try {
            CommandDefinition inquiry = commandBuilder.getCommand("powerStatusRequest");

            if (inquiry == null || inquiry.getResponseTemplate() == null) {
                LOGGER.warning("Missing response template for powerStatusRequest");
                return false;
            }

            byte[] response = executeViscaInquiry("powerStatusRequest");

            if (response == null) {
                LOGGER.warning("No power state response");
                return false;
            }

            List<String> responseTemplate = inquiry.getResponseTemplate();

            int offset = 0;

            for (String token : responseTemplate) {
                if (!token.startsWith("{") || !token.endsWith("}")) {
                    offset++;
                    continue;
                }

                FieldDefinition field = getFieldDefinition(token.substring(1, token.length() - 1));
                String key = String.format("%02X", response[offset] & 0xFF);

                Map<String, Object> values = (Map<String, Object>) field.getMapping().get("values");

                Object mappedValue = values.get(key);

                if (mappedValue == null) {
                    LOGGER.warning("Unknown power status value: " + key);
                    return false;
                }

                String powerState = mappedValue.toString();

                ParameterState state = camera.getParameters().get("powerStatus");

                if (state == null) {
                    state = new ParameterState();
                    camera.getParameters().put("powerStatus", state);
                }

                if ("ON".equalsIgnoreCase(powerState)) {
                    state.setCurrentState("ON");
                    state.setCurrent(1);
                } else {
                    state.setCurrentState("OFF");
                    state.setCurrent(0);
                }
                return true;
            }

            LOGGER.warning("No valid power status response");
            return false;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING,"Power status inquiry failed", e);
            return false;
        }
    }

    /**
     * Updates the camera online/offline status based on communication results.
     *
     * @param pending pending packet tracker
     *
     * @throws InterruptedException if interrupted while waiting
     */
    private void updateCameraStatus(PendingPacket pending) throws InterruptedException {

        if (pending.waitForResponse(RESPONSE_TIMEOUT)) {
            camera.markOnline();
        } else {
            camera.markOffline();
            cameraManager.notifyCameraStatusChanged(camera, false);
            LOGGER.warning("Camera " + camera.getCameraId()+ " does not respond."
            );
        }
    }

    public static boolean testConnection(PTZCameraManager cameraManager, String ipAddress, int port, String configPath) {

        try {

            ObjectMapper mapper = new ObjectMapper();
            ViscaConfig config = mapper.readValue(new File(configPath), ViscaConfig.class);

            PTZCamera testCamera = new PTZCamera("TEST_CAMERA", ipAddress, port,0,1000
                    ,"TEST_CAMERA", config, cameraManager);

            ViscaPTZController controller = new ViscaPTZController(testCamera, config, 1000);

            return controller.powerStatusRequest();

        } catch (Exception e) {
            LOGGER.warning("Connection test failed for " + ipAddress + ":" + port + " : " + e.getMessage());
            return false;
        }
    }

    /**
     * Moves the camera to an absolute pan/tilt position.
     *
     * @param panValue target pan angle
     * @param tiltValue target tilt angle
     * @param speed movement speed
     */
    public void moveAbsolute(double panValue, double tiltValue, int speed) {

        PTZCamera camera = this.camera;

        synchronized (viscaLock) {

            //Hardware limiters
            double clampeddPan = camera.clampPan(panValue);
            double clampedTilt = camera.clampTilt(tiltValue);

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
                LOGGER.log(Level.SEVERE, "MOVE ABS ERROR", e.getMessage());
            }
        }
    }

    /**
     * Starts relative pan/tilt movement.
     *
     * <p>Joystick values are converted into VISCA speed and direction parameters.</p>
     *
     * @param panValue pan control value
     * @param tiltValue tilt control value
     */
    public void movePanTilt(int panValue, int tiltValue) {

        Map<String, Object> params = new HashMap<>();

        // Speed to camera params recalculation
        FieldDefinition panSpeedField = commandBuilder.getField("panSpeed");
        Map<String, Object> range = panSpeedField.getRange();
        int minSpeed = ((Number)range.get("min")).intValue();
        int maxSpeed =((Number)range.get("max")).intValue();
        int panSpeed = minSpeed + (int) Math.round((Math.abs(panValue) / 64.0) * (maxSpeed - minSpeed));
        int tiltSpeed = minSpeed + (int) Math.round((Math.abs(tiltValue) / 64.0) * (maxSpeed - minSpeed));

        //direction setting; 64 = neutral
        int panDirection = panValue > 0 ? 0x02 : panValue < 0 ? 0x01 : 0x03;
        int tiltDirection = tiltValue > 0 ? 0x01 : tiltValue < 0 ? 0x02 : 0x03;

        params.put("panSpeed", panSpeed);
        params.put("tiltSpeed", tiltSpeed);
        params.put("panDirection", panDirection);
        params.put("tiltDirection", tiltDirection);

        byte[] command = commandBuilder.buildCommand("movePanTilt", params);

        sendViscaCommand(command,"command");
        LOGGER.fine("PAN: speed = " + panSpeed + " | dir = " + panSpeed + " TILT  speed = " + tiltSpeed + " | dir = " + tiltDirection);
    }

    /**
     * Stops all active pan and tilt movement.
     */
    public void stopPanTilt() {
        executeViscaCommand("stopPanTilt");
    }

    /**
     * Moves the camera to its configured home position.     */

    public void ptzHomePosition(){
        executeViscaCommand("ptzHomePosition");
    }

    /**
     * Moves zoom to an absolute position.
     * @param zoomValue target zoom position
     */
    public void moveZoomAbsolute(int zoomValue) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("zoomPosition", zoomValue);
            executeViscaCommand("absoluteZoom",params);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ZOOM command error",e);
        }
    }

    /**
     * Moves focus to an absolute position.
     * @param focusValue target focus position
     */
    public void moveFocusAbsolute(int focusValue) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("focusPosition", focusValue);
            executeViscaCommand("absoluteFocus",params);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FOCUS command error",e);
        }
    }

    /**
     * Recalls a previously stored camera preset.     *
     * @param presetName logical preset name defined in the VISCA configuration
     */
    public void recallPreset(String presetName) {

        try {
            FieldDefinition presetField = getFieldDefinition("presetId");

            Object mappedValue = presetField.getMapping().get(presetName);

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

    /**
     * Returns a field definition from the active VISCA configuration.
     *
     * @param fieldName field name
     * @return field definition or {@code null}
     */
    public FieldDefinition getFieldDefinition(String fieldName) {

        return commandBuilder.getField(fieldName);
    }

    /**
     * Converts hexadecimal string values into a binary byte array.
     *
     * @param values hexadecimal values
     * @return byte array representation
     */
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

    /**
     * Converts a byte array*into a hexadecimal string.
     *
     * <p>Intended primarily for diagnostics, debugging and protocol logging.</p>
     *
     * @param data source bytes
     * @param length number of bytes to convert
     * @return formatted hexadecimal string
     */
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
