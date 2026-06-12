import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Controller wysyłający komendy VISCA (PTZ) do kamery przez UDP.
 *
 * Zawiera metody do sterowania pan/tilt (zarówno w trybie "jog" jak i
 * ustawiania pozycji absolutnej), wysyłania zapytań o pozycję oraz
 * mechanizm watchdog dla komend pan.
 */
public class ViscaPTZController {

    private static final String HOST = "100.75.75.100";
    private static final int PORT = 52381;
    private static volatile long lastPanCommandTime = 0;
    private static volatile boolean panMoving = false;


    /**
     * Wyślij komendę PTZ w formacie VISCA-over-UDP.
     *
     * @param ps   szybkość pan (pan speed)
     * @param ts   szybkość tilt (tilt speed)
     * @param panDir  kierunek pan (kod VISCA)
     * @param tiltDir kierunek tilt (kod VISCA)
     */
    //  Wyślij dowolną komendę PTZ
    private static void send(byte ps, byte ts, byte panDir, byte tiltDir) {

        try {
            InetAddress address = InetAddress.getByName(HOST);
            DatagramSocket socket = new DatagramSocket();

            byte[] visca = new byte[]{
                    (byte) 0x81,
                    (byte) 0x01,
                    (byte) 0x06,
                    (byte) 0x01,
                    ps,
                    ts,
                    panDir,
                    tiltDir,
                    (byte) 0xFF
            };

            int length = visca.length + 4;

            byte[] packet = new byte[8 + visca.length];

            // header
            packet[0] = 0x01;
            packet[1] = 0x00;
            packet[2] = 0x00;
            packet[3] = (byte) length;

            // session
            packet[4] = 0x00;
            packet[5] = 0x00;
            packet[6] = 0x00;
            packet[7] = 0x01;

            System.arraycopy(visca, 0, packet, 8, visca.length);

            DatagramPacket dp = new DatagramPacket(packet, packet.length, address, PORT);
            socket.send(dp);

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Zatrzymaj ruch we wszystkich osiach (PAN + TILT).
     * Metoda deleguje do prywatnej metody {@link #send(byte, byte, byte, byte)}.
     */
    // STOP wszystkich osi
    public static void stop() {
        send((byte) 0x08, (byte) 0x08, (byte) 0x03, (byte) 0x03);
    }

    /**
     * Sterowanie TILT w trybie "jog" (joystick).
     *
     * Prędkość zależy od odchylenia wartości MIDI CC względem środka (64).
     * Jeżeli odchylenie jest zbyt małe, następuje zatrzymanie ruchu.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    // TILT (joystick)
    public static void tilt(int ccValue) {

        int center = 64;
        int diff = ccValue - center;

        if (Math.abs(diff) < 2) {
            stop();
            return;
        }

        //  prędkość zależna od wychylenia
        int speed = Math.min(0x10, Math.abs(diff) / 4 + 1);

        byte ts = (byte) speed;

        if (diff > 0) {
            // GÓRA
            send((byte) 0x08, ts, (byte) 0x03, (byte) 0x01);
        } else {
            // DÓŁ
            send((byte) 0x08, ts, (byte) 0x03, (byte) 0x02);
        }
    }
    /**
     * Ustawienie pozycji TILT na wartość absolutną (z kontrolera typu slider).
     *
     * Metoda pobiera aktualną pozycję PAN aby wysłać kompletną komendę
     * ustawienia pozycji (PAN + TILT) w jednym pakiecie VISCA.
     *
     * @param ccValue wartość MIDI CC (0-127)
     */
    // TILT (slider - absolutna pozycja)
    public static void tiltAbsolute(int ccValue) {

        try {
            InetAddress address = InetAddress.getByName(HOST);
            DatagramSocket socket = new DatagramSocket();

            // ✅ pobierz AKTUALNY pan
            byte[] response = requestPosition();
            int pan = parsePan(response);

            int tilt = mapTilt(ccValue);

            byte ps = 0x08;
            byte ts = 0x08;

            byte[] visca = new byte[]{
                    (byte) 0x81,
                    (byte) 0x01,
                    (byte) 0x06,
                    (byte) 0x02,
                    ps,
                    ts,

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

            int length = visca.length + 4;

            byte[] packet = new byte[8 + visca.length];

            packet[0] = 0x01;
            packet[1] = 0x00;
            packet[2] = 0x00;
            packet[3] = (byte) length;

            packet[4] = 0x00;
            packet[5] = 0x00;
            packet[6] = 0x00;
            packet[7] = 0x01;

            System.arraycopy(visca, 0, packet, 8, visca.length);

            socket.send(new DatagramPacket(packet, packet.length, address, PORT));

            socket.close();

            System.out.println("ABS TILT pan=" + Integer.toHexString(pan) +
                    " tilt=" + Integer.toHexString(tilt));

        } catch (Exception e) {
            e.printStackTrace();
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
    //  PAN (stała prędkość z joga)
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

        //  3 poziomy prędkości
        if (absDiff == 1) {
            ps = 0x04; // wolno
        } else if (absDiff <= 3) {
            ps = 0x08; // średnio
        } else {
            ps = 0x10; // szybko
        }

        if (diff > 0) {
            // PRAWO
            send(ps, (byte) 0x08, (byte) 0x02, (byte) 0x03);
        } else {
            // LEWO
            send(ps, (byte) 0x08, (byte) 0x01, (byte) 0x03);
        }

        //  watchdog timing
        lastPanCommandTime = System.currentTimeMillis();
        panMoving = true;

        //  debug (polecam zostawić)
        System.out.println("PAN diff=" + diff + " speed=" + (ps & 0xFF));
    }

    /**
     * Uruchamia wątek watchdog monitorujący komendy PAN.
     *
     * Jeżeli przez określony czas (tutaj ~100ms) nie nadejdzie nowa komenda
     * pan, watchdog wywoła {@link #stop()} aby zatrzymać ruch kamery.
     */
    public static void startPanWatchdog() {

        Thread watchdog = new Thread(() -> {

            while (true) {
                try {
                    Thread.sleep(50);

                    if (panMoving) {

                        long now = System.currentTimeMillis();

                        //  jeśli brak impulsu > 150ms → STOP
                        if (now - lastPanCommandTime > 100) {

                            System.out.println("PAN TIMEOUT -> STOP");

                            stop();
                            panMoving = false;
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * Mapuje wartość MIDI CC na zakres VISCA dla osi tilt.
     * Zwraca wartość 16-bitową w formacie wykorzystywanym przez VISCA.
     *
     * @param cc wartość MIDI CC (0-127)
     * @return wartość VISCA (0-65535)
     */
    private static int mapTilt(int cc) {

        int centerCC = 64;

        // różnica względem środka
        int delta = cc - centerCC;

        // zakres działa na "połówce koła"
        // czyli ile VISCA przypada na 1 jednostkę MIDI
        double scale = 65536.0 / 128.0; // pełne koło

        int visca = (int)(delta * scale);

        // wrap around (najważniejsze)
        visca = (visca + 65536) % 65536;

        return visca;
    }

    /**
     * Wysyła zapytanie VISCA o aktualną pozycję kamery i odbiera odpowiedź.
     *
     * @return bufor z odpowiedzią lub null w przypadku błędu/timeoutu
     */
    public static byte[] requestPosition() {

        try {
            InetAddress address = InetAddress.getByName(HOST);
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(200);

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
            packet[7] = 0x01;

            System.arraycopy(visca, 0, packet, 8, visca.length);

            socket.send(new DatagramPacket(packet, packet.length, address, PORT));

            // ✅ odbiór odpowiedzi
            byte[] buffer = new byte[64];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);

            socket.receive(response);

            socket.close();

            return buffer;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parsuje z odpowiedzi VISCA wartość PAN (część pozycji).
     *
     * @param response bufor z odpowiedzi VISCA
     * @return wartość PAN w formacie VISCA (0-65535)
     */
    private static int parsePan(byte[] response) {

        if (response == null) return 0;

        // właściwe offsety VISCA
        int pan =
                ((response[4] & 0x0F) << 12) |
                        ((response[5] & 0x0F) << 8) |
                        ((response[6] & 0x0F) << 4) |
                        (response[7] & 0x0F);

        return pan;
    }

}