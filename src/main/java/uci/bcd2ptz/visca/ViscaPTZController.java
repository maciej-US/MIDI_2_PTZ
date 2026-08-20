package uci.bcd2ptz.visca;

import uci.bcd2ptz.ptz.PTZCamera;

import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Ujednolicony kontroler VISCA  na protokole VISCA over UDP.
 */
public class ViscaPTZController {


    public enum ViscaPacketType {
        COMMAND,
        INQUIRY,
        RESPONSE
    }


    private final ViscaCommandBuilder commandBuilder;
    private static final Logger LOGGER = Logger.getLogger(ViscaPTZController.class.getName());

    //Konfguracja połączenia sieciowego z parametrów kamery ()
    private final PTZCamera camera;

    //Templatka wrapowania payloadów Visca
    private final Map<String, byte[]> udpTemplate = new LinkedHashMap<>();

    private final Map<String, byte[]> viscaPayloadType = new LinkedHashMap<>();

    // stara architektura - docelowo do wywalenia

    private static volatile long lastPanCommandTime = 0;
    private static volatile boolean panMoving = false;
    private static final Object viscaLock = new Object();
    private static final int PAN_LEFT_LIMIT  = toSignedPan(0x8A14); // ~ -30252
    private static final int PAN_RIGHT_LIMIT = toSignedPan(0x762C); // ~ +30252


    /**
     * Tworzy nowy kontroler VISCA dla podanej kamery.
     *
     * @param viscaConfigFile ścieżka do pliku konfiguracyjnego VISCA (JSON)
     */

    public ViscaPTZController(PTZCamera camera, String viscaConfigFile) {
        //podłączenie kamery i CommandBuildera
        this.commandBuilder = new ViscaCommandBuilder(viscaConfigFile);
        this.camera = camera;

        //Wczytanie formatek pakietów UDP i typó komuynikatów VISCA
        Map<String, Object> transport = commandBuilder.getConfig().getTransport();
        Map<String, Object> packetType = (Map<String, Object>) transport.get("packetType");
        Map<String, Object> payloadSize = (Map<String, Object>) transport.get("payloadSize");
        Map<String, Object> sequence = (Map<String, Object>) transport.get("sequence");
        Map<String, Object> payload =(Map<String, Object>) transport.get("payload");

        udpTemplate.put("type", new byte[((Number) packetType.get("size")).intValue()]);
        udpTemplate.put("payloadLength", new byte[((Number) payloadSize.get("size")).intValue()]);
        udpTemplate.put("sequence", new byte[ ((Number) sequence.get("size")).intValue()]);

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
    }

    /* ===================================================
             OBSŁUGA WYGENEROWANCYH PAYLOADÓW VISCA i KOMUNIKACJI
       ===================================================*/

    private byte[] wrapViscaPayload(byte[] viscaPayload, String type) {


        byte [] commandType = viscaPayloadType.get(type);

        if (commandType == null) {

            throw new IllegalArgumentException(
                    "Unknown packet type: " + type);
        }

        byte[] typeBytes =
                udpTemplate.get("type");

        // obliczanie długości pakietu
        int packetSize =
                udpTemplate.get("type").length +
                udpTemplate.get("payloadLength").length +
                udpTemplate.get("sequence").length +
                viscaPayload.length;

        byte[] packet =
                new byte[packetSize];

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

        int sequenceOffset =  udpTemplate.get("type").length +
                                udpTemplate.get("payloadLength").length;

        int sequenceLength = udpTemplate.get("sequence").length;

        for (int i = 0;i < sequenceLength;i++) {
            packet[
                    sequenceOffset
                            + sequenceLength
                            - 1
                            - i
                    ] =(byte)(sequence>> (8 * i));
        }

        camera.setViscaSessionID(
                sequence + 1
        );

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
    public void sendViscaCommand(
            byte[] viscaPayload,
            String type) {

        try {
            byte[] command =
                    wrapViscaPayload(
                            viscaPayload,
                            type
                    );

            DatagramPacket dp =
                    new DatagramPacket(
                            command,
                            command.length,
                            InetAddress.getByName(
                                    camera.getIpAddress()
                            ),
                            camera.getPort()
                    );

            camera.getSocket().send(dp);

        } catch (Exception e) {

            LOGGER.log(
                    Level.SEVERE,
                    "Błąd wysyłania komendy VISCA",
                    e
            );
        }
    }


    /* =====================================
            Synchronizacja stanów
       =====================================*/

    /**
     * Wysyła zapytanie VISCA o aktualną pozycję kamery, odbiera odpowiedź i uaktualnia parametry pozycji kamery w
     * PTZCamera pdodłączonej logiocznie do kontrolera.
     */
    @SuppressWarnings("unchecked")
    public void requestPosition() {

        try {
            byte[] visca =
                    commandBuilder.build(
                            "panTiltPositionRequest",
                            new HashMap<>()
                    );

            String commandType = commandBuilder.getCommand("panTiltPositionRequest").getType();

            // Czyszczenie połączenia i wysyłka
            clearViscaInterface();
            Thread.sleep(50);
            flushSocket();

            sendViscaCommand(visca, commandType);

            Thread.sleep(50);

            byte[] buffer =
                    new byte[64];

            for (int i = 0; i < 15; i++) {

                DatagramPacket response =
                        new DatagramPacket(
                                buffer,
                                buffer.length
                        );

                camera.getSocket().receive(response);

                byte[] data = Arrays.copyOf(
                        buffer,
                        response.getLength());

                if (data.length < 10) {
                    continue;
                }

                int offset = (data[0] == (byte) 0xFF) ? 9 : 8;

                byte responseType = data[offset + 1];

                // Odpowiedź z pozycją kamery

                if (responseType == 0x50) {

                    // PAN VISCA
                    int panVisca =
                            ((data[offset + 2] & 0x0F) << 12)
                                    | ((data[offset + 3] & 0x0F) << 8)
                                    | ((data[offset + 4] & 0x0F) << 4)
                                    | (data[offset + 5] & 0x0F);


                    // TILT VISCA
                    int tiltVisca =
                            ((data[offset + 6] & 0x0F) << 12)
                                    | ((data[offset + 7] & 0x0F) << 8)
                                    | ((data[offset + 8] & 0x0F) << 4)
                                    | (data[offset + 9] & 0x0F);

                    camera.setPanVisca(panVisca);
                    camera.setTiltVisca(tiltVisca);

                    //Parsowanie na stopnie PAN i TILT

                    FieldDefinition panField = commandBuilder.getField("panPosition");
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
                    camera.setPan(panDegrees);

                    FieldDefinition tiltField = commandBuilder.getField("tiltPosition");
                    Map<String, Object> tiltMapping = tiltField.getMapping();
                    Map<String, Object> tiltDeviceRange = (Map<String, Object>) tiltMapping.get("deviceRange");
                    Map<String, Object> tiltRange = (Map<String, Object>) tiltMapping.get("mechanicalRange");
                    int positiveTiltLimit = Integer.parseInt((String) tiltDeviceRange.get("positiveLimit"), 16);
                    int negativeTiltLimit = Integer.parseInt((String) tiltDeviceRange.get("negativeLimit"), 16);
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
                    camera.setTilt(tiltDegrees);
                    // debug
                    LOGGER.fine("VISCA ignore type=" + String.format("0x%02X", responseType));
                }
            }
        } catch (Exception e) {
            LOGGER.fine("VISCA request timeout / brak poprawnej odpowiedzi");
        }
    }


    /** =======================================================
     *     OBSŁUGA RUCHU KAMERY
        ======================================================= */

    public void moveAbsolute(
            double panValue,
            double tiltValue,
            int speed) {

        //SCAFF
        //System.out.println("moveAbsolute - Received values: PAN " + panValue + "  |  TILT " + tiltValue + "  | SPEED " + speed);

        synchronized (viscaLock) {

            //ograniczniki zakresów do sprzętu
            FieldDefinition panField = commandBuilder.getField("panPosition");
            FieldDefinition tiltField = commandBuilder.getField("tiltPosition");

            Map<String,Object> panMapping = panField.getMapping();
            Map<String,Object> panRange = (Map<String,Object>)panMapping.get("mechanicalRange");

            double minPan =((Number)panRange.get("minDegrees")).doubleValue();
            double maxPan =((Number)panRange.get("maxDegrees")).doubleValue();

            Map<String,Object> tiltMapping =tiltField.getMapping();
            Map<String,Object> tiltRange =(Map<String,Object>)tiltMapping.get("controllerRange");

            double minTilt =((Number)tiltRange.get("minDegrees")).doubleValue();
            double maxTilt =((Number)tiltRange.get("maxDegrees")).doubleValue();

            double boundedPan =Math.max(minPan, Math.min(maxPan, panValue));
            double boundedTilt = Math.max(minTilt, Math.min(maxTilt,tiltValue));

            //SCAFF
            //System.out.println("moveAbsolute - Used values: PAN " + boundedPan + "  |  TILT " + boundedTilt);

            try {
                Map<String,Object> params =
                        new HashMap<>();

                params.put("panSpeed", speed);
                params.put("tiltSpeed", speed);

                params.put("panPosition", boundedPan);
                params.put("tiltPosition", boundedTilt);

                byte[] command =
                        commandBuilder.build(
                                "absolutePosition",
                                params
                        );

                String commandType = commandBuilder.getCommand("absolutePosition").getType();

                sendViscaCommand(command, commandType);

                camera.setPan(boundedPan);
                camera.setTilt(boundedTilt);

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

    public void movePanTilt(
            int panValue,
            int tiltValue) {

        //SCAFF
        //System.out.println("movePanTilt - Received values: PAN " + panValue + "  |  TILT " + tiltValue);
       /* if (panValue == 0 && tiltValue == 0){
            System.out.println("movePanTilt - STOP REQUEST");
            camera.stop();
        }*/

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

        byte[] command = commandBuilder.build("movePanTilt", params);

        sendViscaCommand(command,"command");
    }

    /**
     * Zatrzymaj ruch PAN i TILT.
     */
    public void stopPanTilt() {

        byte[] payload =
                commandBuilder.build(
                        "stopPanTilt",
                        Collections.emptyMap()
                );

        sendViscaCommand(
                payload,
                "command"
        );

        //DEBUG:
        //System.out.println("UDP Pakiet send: " + bytesToHex(payload, payload.length));
    }

    /**
     * Sterowanie TILT w trybie "jog" (joystick).
     *
     * Prędkość zależy od odchylenia wartości MIDI CC względem środka (64).
     * Jeżeli odchylenie jest zbyt małe, następuje zatrzymanie ruchu.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    public void moveTilt(int ccValue) {
        int center = 64;
        int diff = ccValue - center;

        if (Math.abs(diff) < 2) {
            stopPanTilt();
            return;
        }

        int speed = Math.min(0x10, Math.abs(diff) / 4 + 1);
        byte ts = (byte) speed;

        byte[] visca = new byte[]{
                (byte) 0x81,
                (byte) 0x01,
                (byte) 0x06,
                (byte) 0x01,
                (byte) 0x08,
                ts,
                (byte) 0x03,
                (byte) (diff > 0 ? 0x01 : 0x02),
                (byte) 0xFF
        };

        sendViscaCommand(visca, "command");
    }

    /**
     * DEPRECADED Ustawienie pozycji TILT na wartość absolutną (z kontrolera typu slider z parametrami w kodzie).
     *
     * Metoda pobiera aktualną pozycję PAN aby wysłać kompletną komendę
     * ustawienia pozycji (PAN + TILT) w jednym pakiecie VISCA.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
   /* public static void tiltAbsolute(int ccValue) {

        synchronized (viscaLock) {

            try {

                int tilt = mapTilt(ccValue);

                // ✅ NIE ruszamy PAN
                int pan = currentPan;

                byte[] visca = new byte[]{
                        (byte) 0x81,
                        (byte) 0x01,
                        (byte) 0x06,
                        (byte) 0x02,
                        (byte) 0x08,
                        (byte) 0x08,

                        (byte) ((pan >> 12) & 0x0F),
                        (byte) ((pan >> 8) & 0x0F),
                        (byte) ((pan >> 4) & 0x0F),
                        (byte) (pan & 0x0F),

                        (byte) ((tilt >> 12) & 0x0F),
                        (byte) ((tilt >> 8) & 0x0F),
                        (byte) ((tilt >> 4) & 0x0F),
                        (byte) (tilt & 0x0F),

                        (byte) 0xFF
                };

                sendViscaCommand(visca);

                // ✅ aktualizujemy lokalny stan
                currentTilt = tilt;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Błąd TILT ABS", e);
            }
        }
    }*/

    //DEPRECATED: sterowanie PAN w trybie absolutnym (z kontrolera typu slider bez zmiennej konfiguracji).
    /*public static void panAbsolute(int ccValue) {

        synchronized (viscaLock) {

            try {

                int center = 64;
                int diff = ccValue - center;

                if (Math.abs(diff) < 1) return;

                // prędkość VISCA
                byte panSpeed;
                if (Math.abs(diff) == 1) {
                    panSpeed = 0x03;
                } else if (Math.abs(diff) <= 3) {
                    panSpeed = 0x0C;
                } else {
                    panSpeed = 0x15;
                }

                byte tiltSpeed = 0x01;

                int newPan = currentPan + (50 * diff * (-1));

                // wrap
                newPan = (newPan + 0x10000) % 0x10000;

                // LIMIT
                if (!isPanInRange(newPan)) {
                    LOGGER.fine("PAN LIMIT");
                    return;
                }

                // ✅ zapamiętaj poprzednią wartość (rollback)
                int oldPan = currentPan;

                currentPan = newPan;


                sendAbsolute(currentPan, currentTilt, panSpeed, tiltSpeed);

            } catch (Exception e) {

                // ✅ cofnięcie stanu przy błędzie (ważne!)
                LOGGER.log(Level.SEVERE, "PAN ABS ERROR", e);

            }
        }
    }*/


    /**
     * Sterowanie PAN w trybie "jog" (stała prędkość zależna od wychylenia).
     *
     * Aktualizuje znacznik czasu ostatniej komendy (watchdog) aby
     * zapobiec ciągłemu ruchowi w przypadku braku kolejnych impulsów.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    public void pan(int ccValue) {
        int center = 64;
        int diff = ccValue - center;

        if (Math.abs(diff) < 1) {
            stopPanTilt();
            panMoving = false;
            return;
        }

        int absDiff = Math.abs(diff);
        byte ps;

        if (absDiff == 1) {
            ps = 0x04; // wolno
        } else if (absDiff <= 3) {
            ps = 0x08; // średnio
        } else {
            ps = 0x10; // szybko
        }

        byte[] visca = new byte[]{
                (byte) 0x81,
                (byte) 0x01,
                (byte) 0x06,
                (byte) 0x01,
                ps,
                (byte) 0x08,
                (byte) (diff > 0 ? 0x02 : 0x01),
                (byte) 0x03,
                (byte) 0xFF
        };

        sendViscaCommand(visca, "command");

        lastPanCommandTime = System.currentTimeMillis();
        panMoving = true;

        LOGGER.fine("PAN diff=" + diff + " speed=" + (ps & 0xFF));
    }

    public void syncPosition(Runnable onComplete) {

        new Thread(() -> {

            try {

                // ✅ 1. zatrzymaj kamerę
                stopPanTilt();

                Thread.sleep(80); // ✅ KLUCZOWE

                byte[] response = null;

                requestPosition();

                Thread.sleep(50); // retry spacing

                /*
                camera.setPanVisca(parsePan(response));
                camera.setTiltVisca(parseTilt(response));*/

                System.out.println("SYNC OK PAN=" + camera.getPanVisca() + " TILT=" + camera.getTiltVisca());

                if (onComplete != null) {
                    onComplete.run();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }, "VISCA-SYNC").start();
    }

    /** DEPRECATED
     * Uruchamia wątek watchdog monitorujący komendy PAN.
     *
     * Jeżeli przez określony czas (~75ms) nie nadejdzie nowa komenda pan,
     * watchdog wywoła {@link #stopPanTilt()} aby zatrzymać ruch kamery.
     */
   /* public void startPanWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);

                    if (panMoving) {
                        long now = System.currentTimeMillis();
                        if (now - lastPanCommandTime > 75) {
                            LOGGER.info("PAN TIMEOUT → STOP");
                            stop();
                            panMoving = false;

                        }
                    }

                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Watchdog przerwany", e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Błąd w watchdog", e);
                }
            }
        });

        watchdog.setDaemon(true);
        watchdog.start();
    }*/


    /**
     * Sterowanie ZOOM kamerą (wartość CC mapowana na zakres VISCA).
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    public void zoom(int ccValue) {
        try {
            int maxZoom = 0x6120;
            int zoom = (ccValue * maxZoom) / 127;

            int p = (zoom >> 12) & 0x0F;
            int q = (zoom >> 8) & 0x0F;
            int r = (zoom >> 4) & 0x0F;
            int s = zoom & 0x0F;

            byte[] visca = new byte[]{
                    (byte) 0x81,
                    (byte) 0x01,
                    (byte) 0x04,
                    (byte) 0x47,
                    (byte) p,
                    (byte) q,
                    (byte) r,
                    (byte) s,
                    (byte) 0xFF
            };

            sendViscaCommand(visca, "command");
            LOGGER.fine("ZOOM CC=" + ccValue + " VISCA=" + String.format("%04X", zoom));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Błąd komendy ZOOM", e);
        }
    }


    /**
     * Wznowienie zapisanego presetu kamery.
     *
     * @param preset numer presetu (1-255)
     */
    public void recallPreset(int preset) {
        try {
            byte[] visca = new byte[]{
                    (byte) 0x81,
                    (byte) 0x01,
                    (byte) 0x04,
                    (byte) 0x3F,
                    (byte) 0x02,
                    (byte) preset,
                    (byte) 0xFF
            };

            sendViscaCommand(visca, "command");
            LOGGER.fine("RECALL PRESET: " + preset);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Błąd recall presetu", e);
        }
    }


/*    public void enterAbsoluteMode() {

        synchronized (viscaLock) {

            byte[] response = requestPosition();

            if (response == null) {
                LOGGER.warning("Nie mogę pobrać pozycji startowej");
                return;
            }

            *//*currentPan = parsePan(response);
            currentTilt = parseTilt(response);*//*

            LOGGER.info("ABS INIT pan=" + String.format("%04X", camera.getPan())
                    + " tilt=" + String.format("%04X", camera.getTilt()));
        }
    }*/


    /**
     * Uruchamia wątek śledzący aktualną pozycję PAN kamery.
     *
     * Metoda tworzy wątek deemonowy, który okreslowo wysyła zapytania VISCA
     * o aktualną pozycję kamery (co ~150ms) i aktualizuje zmienne stanu:
     * - {@code currentPan} — aktualna pozycja PAN (w formacie VISCA, 0x0000-0xFFFF)
     * - {@code lastValidPan} — ostatnia pomyślnie odebrania pozycja
     * - {@code panInitialized} — flaga inicjalizacji
     *
     * Wątek jest uruchamiany jako daemon (nie blokuje wyjście aplikacji).
     * W case'u błędu transmisji (timeout, błąd sieci) metoda loguje ostrzeżenie
     * i kontynuuje próby zapytań.
     *
     * Typowe zastosowanie:
     * {@code
     * ViscaPTZController.startPositionTracker();
     * // Po chwili: currentPan zawiera aktualną pozycję kamery
     * }
     *
     * @see #requestPosition()
     * @see #parsePan(byte[])

     */
    /*public static void PositionTracker() {

        Thread t = new Thread(() -> {

            while (true) {

                try {
                    byte[] response = requestPosition();

                    if (response != null) {
                        int pan = parsePan(response);

                        currentPan = pan;
                        lastValidPan = pan;
                        panInitialized = true;

                        //LOGGER.fine("TRACK PAN=" + String.format("%04X", pan));
                    }

                    Thread.sleep(1000);

                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Tracker error", e);
                }
            }
        });

        t.setDaemon(true);
        t.start();
    }*/

    public void clearViscaInterface() {
        try {
            byte[] visca = new byte[] {
                    (byte) 0x88,
                    (byte) 0x01,
                    (byte) 0x00,
                    (byte) 0x01,
                    (byte) 0xFF
            };

            sendViscaCommand(visca, "command");

            System.out.println("VISCA IF_Clear sent");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void flushSocket() {
        try {
            camera.getSocket().setSoTimeout(10);

            byte[] buffer = new byte[64];
            DatagramPacket p = new DatagramPacket(buffer, buffer.length);

            while (true) {
                camera.getSocket().receive(p);
                //System.out.println("FLUSH: " + bytesToHex(buffer, p.getLength()));
            }

        } catch (Exception ignored) {
        } finally {
            try {
                camera.getSocket().setSoTimeout(1000);
            } catch (Exception ignored) {}
        }
    }

    /* ============================================
           GETTERY, SETTERY, METODY POMOCNICZE
       ============================================*/

    public FieldDefinition getFieldDefinition(
            String fieldName) {

        return commandBuilder.getField(
                fieldName
        );
    }

    private byte[] parseByteArray(
            List<String> values) {

        byte[] result =
                new byte[values.size()];

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
     * Parsuje z odpowiedzi VISCA wartość PAN.
     *
     * @param response bufor z odpowiedzi VISCA
     * @return wartość PAN w formacie VISCA (0-65535)
     */
    private static int parsePan(byte[] response) {

        // ✅ wykryj offset (czy jest FF na początku)
        int offset = (response[0] == (byte) 0xFF) ? 9 : 8;

        int pan =
                ((response[offset + 2] & 0x0F) << 12) |
                        ((response[offset + 3] & 0x0F) << 8) |
                        ((response[offset + 4] & 0x0F) << 4) |
                        (response[offset + 5] & 0x0F);

        return pan;
    }

    private static int parseTilt(byte[] response) {

        // ✅ bezpieczeństwo
        if (response == null || response.length < 12) {
            throw new IllegalArgumentException("Za krótka odpowiedź VISCA do parseTilt");
        }

        // ✅ offset (AVer czasem daje FF na początku)
        int offset = (response[0] == (byte) 0xFF) ? 9 : 8;

        // ✅ dodatkowe zabezpieczenie zakresu
        if (response.length < offset + 10) {
            throw new IllegalArgumentException("Niepełna ramka VISCA (tilt)");
        }

        int tilt =
                ((response[offset + 6] & 0x0F) << 12) |
                        ((response[offset + 7] & 0x0F) << 8) |
                        ((response[offset + 8] & 0x0F) << 4) |
                        (response[offset + 9] & 0x0F);

        return tilt;
    }

    private double viscaPanToDegrees(
            int value) {

        return camera.getPan();
    }

    private double viscaTiltToDegrees(
            int value) {

        return camera.getTilt();
    }


    //DEBUGGER

    public static String bytesToHex(byte[] data, int length) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[i]));
            if (i < length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static int toSignedPan(int pan) {

        if (pan > 0x7FFF) {
            return pan - 0x10000;
        }

        return pan;
    }

}

