package uci.bcd2ptz;

import java.net.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ujednolicony kontroler VISCA dla kamery PTZ AVer serii PTC31xxx.
 *
 * Obsługuje wszystkie funkcje:
 * - Sterowanie PAN/TILT (tryb "jog" i absolutne pozycjonowanie)
 * - Sterowanie ZOOM
 * - Recall presetsów
 * - Mechanizm watchdog dla komend PAN
 *
 * Bazuje na protokole VISCA over UDP.
 */
public class ViscaPTZController {

    private static final Logger LOGGER = Logger.getLogger(ViscaPTZController.class.getName());

    private static final String VISCA_HOST = "100.75.75.100";
    private static final int VISCA_PORT = 52381;
    private static final int VISCA_SESSION = 0x01;

    // Stan watchdog dla PAN
    private static volatile int lastValidPan = 0x0000;
    private static volatile int stablePan = 0x0000;
    private static volatile long lastPanCommandTime = 0;
    private static volatile long lastPanUpdateTime = 0;
    private static volatile boolean panMoving = false;
    private static volatile boolean panInitialized = false;
    private static final Object viscaLock = new Object();
    private static long lastTiltTime = 0;
    private static final long TILT_INTERVAL_MS = 50; // 20 Hz
    private static volatile int currentPan = 0;
    private static volatile int currentTilt = 0;
    private static final int PAN_LEFT_LIMIT  = toSignedPan(0x8A14); // ~ -30252
    private static final int PAN_RIGHT_LIMIT = toSignedPan(0x762C); // ~ +30252

    private static InetAddress address;
    private static DatagramSocket socket;

    static {
        try {
            // socket
            socket = new DatagramSocket(52381);
            socket.setSoTimeout(1000);
            System.out.println("Socket bound to port: " + socket.getLocalPort());

            // address
            address = InetAddress.getByName(VISCA_HOST);

            LOGGER.info("VISCA address resolved: " +
                    address.getHostAddress() +
                    " (" + address.getHostName() + ")");

            // Uruchom tracker PAN
            //PositionTracker();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Błąd inicjalizacji VISCA (socket/adres)", e);
        }
    }
    private static int sessionId =1;

    // ==================== METODY PRYWATNE ====================

    /**
     * Wysyła komendę VISCA w formacie VISCA-over-UDP do kamery.
     *
     * Opakowuje payload VISCA w nagłówek AVer UDP (8 bajtów) i wysyła pakiet.
     *
     * @param viscaPayload tablica bajtów zawierająca komendę VISCA (bez nagłówka UDP)
     */
    private static void sendViscaCommand(byte[] viscaPayload) {
        try {
            //InetAddress address = InetAddress.getByName(VISCA_HOST);

            int length = viscaPayload.length + 4;
            byte[] packet = new byte[8 + viscaPayload.length];

            // Nagłówek AVer UDP
            packet[0] = 0x01;
            packet[1] = 0x00;
            packet[2] = 0x00;
            packet[3] = (byte) length;

            // Session ID
            packet[4] = 0x00;
            packet[5] = 0x00;
            packet[6] = 0x00;
            packet[7] = (byte) VISCA_SESSION;

            // Payload VISCA
            System.arraycopy(viscaPayload, 0, packet, 8, viscaPayload.length);

            DatagramPacket dp = new DatagramPacket(packet, packet.length, address, VISCA_PORT);
            socket.send(dp);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Błąd wysyłania komendy VISCA", e);
        }
    }

    /**
     * Mapuje wartość MIDI CC na zakres VISCA dla osi tilt.
     *
     * @param cc wartość MIDI CC (0-127)
     * @return wartość VISCA (0-65535)
     */
    private static int mapTilt(int cc) {
        int centerCC = 64;
        int delta = cc - centerCC;
        double scale = 65536.0 / 128.0;
        int visca = (int) (delta * scale);
        return (visca + 65536) % 65536;
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

    private static void sendAbsolute(int pan, int tilt, byte ps, byte ts) throws Exception {

        byte[] visca = new byte[]{
                (byte) 0x81,
                (byte) 0x01,
                (byte) 0x06,
                (byte) 0x02,
                ps,   // ✅ PAN SPEED
                ts,   // ✅ TILT SPEED

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
    }

    /**
     * Wysyła zapytanie VISCA o aktualną pozycję kamery i odbiera odpowiedź.
     *
     * @return bufor z odpowiedzią lub null w przypadku błędu/timeoutu
     */
    private static byte[] requestPosition() {

        try {
            byte[] visca = new byte[]{
                    (byte) 0x81,
                    (byte) 0x09,
                    (byte) 0x06,
                    (byte) 0x12,
                    (byte) 0xFF
            };

            int length = visca.length + 4;
            byte[] packet = new byte[8 + visca.length];

            packet[0] = 0x01;
            packet[1] = 0x00;
            packet[2] = 0x00;
            packet[3] = (byte) length;

            packet[4] = 0x00;
            packet[5] = 0x00;
            packet[6] = 0x00;
            packet[7] = (byte) VISCA_SESSION;

            System.arraycopy(visca, 0, packet, 8, visca.length);

            System.out.println("Wysłane VISCA: " + bytesToHex(packet, packet.length));

            // wysyłka
            DatagramPacket dp = new DatagramPacket(packet, packet.length, address, VISCA_PORT);
            socket.send(dp);

            Thread.sleep(20);

            byte[] buffer = new byte[64];


            // Filtr właściwego response z kamery
            for (int i = 0; i < 7; i++) {

                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);

                byte[] data = Arrays.copyOf(buffer, response.getLength());
                System.out.println("Odebrane VISCA: " + bytesToHex(data, packet.length));


                if (data.length < 10) continue;

                // offset (zależny od FF na początku)
                int offset = (data[0] == (byte) 0xFF) ? 9 : 8;

                // czy to RESPONSE pozycji
                if (data.length >= offset + 10 &&
                        (data[offset] & 0xF0) == 0x90 &&
                        data[offset + 1] == 0x50) {

                    return data; // JEDYNY poprawny case
                }

                // opcjonalny debug
                LOGGER.fine("Ignoruję pakiet VISCA: " + bytesToHex(data, data.length));
            }

            // brak właściwej odpowiedzi
            return null;

        } catch (Exception e) {
            LOGGER.fine("VISCA request timeout / brak poprawnej odpowiedzi");
            return null;
        }
    }

    /**
     * Konwertuje tablicę bajtów na sformatowany ciąg heksadecymalny.
     *
     * Każdy bajt jest reprezentowany jako dwie cyfry heksadecymalne (0-F),
     * oddzielone spacją. Przydatne do debugowania pakietów UDP w formacie VISCA.
     *
     * Przykład:
     * {@code
     * byte[] data = {0x01, (byte)0xFF, 0x00, 0x42};
     * String hex = bytesToHex(data, data.length);
     * // Wynik: "01 FF 00 42"
     * }
     *
     * @param data tablica bajtów do konwersji (może być null)
     * @param length liczba bajtów do konwersji
     * @return sformatowany ciąg heksadecymalny, oddzielony spacjami,
     *         lub "null" jeśli tablica jest pusta
     *
     * @see String#format(String, Object...)
     */
    private static String bytesToHex(byte[] data, int length) {
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

    private static final int PAN_MAX_LEFT  = 0x75EC; // = 0x10000 - 0x8A14
    private static final int PAN_MAX_RIGHT = 0x762C;

    private static int panDistance(int from, int to) {
        int diff = to - from;

        if (diff > 32767) diff -= 65536;
        if (diff < -32768) diff += 65536;

        return diff;
    }

    private static boolean isPanInRange(int pan) {

        int signed = toSignedPan(pan);

        return signed >= PAN_LEFT_LIMIT && signed <= PAN_RIGHT_LIMIT;
    }

    // ==================== METODY PUBLICZNE ====================

    /**
     * Zatrzymaj ruch we wszystkich osiach (PAN + TILT).
     */
    public static void stop() {
        byte[] visca = new byte[]{
                (byte) 0x81,
                (byte) 0x01,
                (byte) 0x06,
                (byte) 0x01,
                (byte) 0x08,
                (byte) 0x08,
                (byte) 0x03,
                (byte) 0x03,
                (byte) 0xFF
        };
        sendViscaCommand(visca);
    }

    /**
     * Sterowanie TILT w trybie "jog" (joystick).
     *
     * Prędkość zależy od odchylenia wartości MIDI CC względem środka (64).
     * Jeżeli odchylenie jest zbyt małe, następuje zatrzymanie ruchu.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    public static void tilt(int ccValue) {
        int center = 64;
        int diff = ccValue - center;

        if (Math.abs(diff) < 2) {
            stop();
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

        sendViscaCommand(visca);
    }

    /**
     * Ustawienie pozycji TILT na wartość absolutną (z kontrolera typu slider).
     *
     * Metoda pobiera aktualną pozycję PAN aby wysłać kompletną komendę
     * ustawienia pozycji (PAN + TILT) w jednym pakiecie VISCA.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    public static void tiltAbsolute(int ccValue) {

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
    }

    public static void panAbsolute(int ccValue) throws Exception {

        synchronized (viscaLock) {

            int center = 64;
            int diff = ccValue - center;

            if (Math.abs(diff) < 1) return;

            // ✅ prędkość VISCA
            byte panSpeed = 0x05;
            byte tiltSpeed = 0x01;
            if (Math.abs(diff) == 1) {
                panSpeed = 0x05;   // wolno
            } else if (Math.abs(diff) <= 3) {
                panSpeed = 0x0C;   // średnio
            } else {
                panSpeed = 0x15;   // szybko
            }

            // ✅ kierunek jako delta pozycji (mały krok, nie velocity!)
            int direction = (diff > 0) ? 1 : -1;

            int newPan = currentPan + (direction * 200); // mikro przesunięcie

            // ✅ wrap
            newPan = (newPan + 0x10000) % 0x10000;

            // ✅ LIMIT
            if (!isPanInRange(newPan)) {
                LOGGER.fine("PAN LIMIT");
                return;
            }

            currentPan = newPan;

            sendAbsolute(currentPan, currentTilt, panSpeed, tiltSpeed);
        }
    }

    /**
     * Sterowanie PAN w trybie "jog" (stała prędkość zależna od wychylenia).
     *
     * Aktualizuje znacznik czasu ostatniej komendy (watchdog) aby
     * zapobiec ciągłemu ruchowi w przypadku braku kolejnych impulsów.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    public static void pan(int ccValue) {
        int center = 64;
        int diff = ccValue - center;

        if (Math.abs(diff) < 1) {
            stop();
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

        sendViscaCommand(visca);

        lastPanCommandTime = System.currentTimeMillis();
        panMoving = true;

        LOGGER.fine("PAN diff=" + diff + " speed=" + (ps & 0xFF));
    }

    /**
     * Uruchamia wątek watchdog monitorujący komendy PAN.
     *
     * Jeżeli przez określony czas (~75ms) nie nadejdzie nowa komenda pan,
     * watchdog wywoła {@link #stop()} aby zatrzymać ruch kamery.
     */
    public static void startPanWatchdog() {
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
    }


    /**
     * Sterowanie ZOOM kamerą (wartość CC mapowana na zakres VISCA).
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    public static void zoom(int ccValue) {
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

            sendViscaCommand(visca);
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
    public static void recallPreset(int preset) {
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

            sendViscaCommand(visca);
            LOGGER.fine("RECALL PRESET: " + preset);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Błąd recall presetu", e);
        }
    }


    public static void enterAbsoluteMode() {

        synchronized (viscaLock) {

            byte[] response = requestPosition();

            if (response == null) {
                LOGGER.warning("Nie mogę pobrać pozycji startowej");
                return;
            }

            currentPan = parsePan(response);
            currentTilt = parseTilt(response);

            LOGGER.info("ABS INIT pan=" + String.format("%04X", currentPan)
                    + " tilt=" + String.format("%04X", currentTilt));
        }
    }


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
     * @see #currentPan
     * @see #lastValidPan
     * @see #panInitialized
     */
    public static void PositionTracker() {

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
    }

}

