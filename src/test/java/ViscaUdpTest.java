import uci.bcd2ptz.ViscaPTZController;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ViscaUdpTest {

    //private static final Logger LOGGER = Logger.getLogger(ViscaPTZController.class.getName());

    private static final String VISCA_HOST = "100.75.75.100";
    private static final int VISCA_PORT = 52381;
    private static final int VISCA_SESSION = 0x01;

    private static DatagramSocket socket;

    static {
        try {
            socket = new DatagramSocket(52381);
            socket.setSoTimeout(1000);
            System.out.println("Socket bound to port: " + socket.getLocalPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static int sessionId =1;

    private static String bytesToHex(byte[] data, int length) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[i]));
            if (i < length - 1) sb.append(' ');
        }
        return sb.toString();
    }


    public static void main(String[] args) {

        /*try {
            String host = "100.75.75.100";
            int port = 52381;

            InetAddress address = InetAddress.getByName(host);
            DatagramSocket socket = new DatagramSocket();

             ✅ RUCH W GÓRĘ
            byte[] moveUp = new byte[]{
                    0x01, 0x00, 0x00, 0x09,
                    0x00, 0x00, 0x00, 0x01,
                    (byte) 0x81,
                    (byte) 0x01,
                    (byte) 0x06,
                    (byte) 0x01,
                    (byte) 0x08,
                    (byte) 0x08,
                    (byte) 0x03,
                    (byte) 0x01,
                    (byte) 0xFF
            };

            // ✅ STOP
            byte[] stop = new byte[]{
                    0x01, 0x00, 0x00, 0x09,
                    0x00, 0x00, 0x00, 0x01,
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

            // ✅ WYŚLIJ RUCH
            System.out.println("Ruch w górę...");
            DatagramPacket movePacket =
                    new DatagramPacket(moveUp, moveUp.length, address, port);

            socket.send(movePacket);

            // ✅ CZEKAMY (np. 1 sekunda)
            Thread.sleep(1000);

            // ✅ WYŚLIJ STOP
            System.out.println("Stop...");
            DatagramPacket stopPacket =
                    new DatagramPacket(stop, stop.length, address, port);

            socket.send(stopPacket);

            socket.close();
            ViscaPresetController.recallPreset(97);



        } catch (Exception e) {
            e.printStackTrace();
        }*/


        try {
            InetAddress address = InetAddress.getByName(VISCA_HOST);

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
            packet[7] = (byte) (sessionId++ & 0xFF);

            System.arraycopy(visca, 0, packet, 8, visca.length);

            // Wyświetl pakiet w postaci HEX
            System.out.println(bytesToHex(packet, packet.length));

            DatagramPacket dp = new DatagramPacket(packet, packet.length, address, VISCA_PORT);

            socket.send(dp);

            Thread.sleep(50); // ✅ ważne

            byte[] buffer = new byte[64];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);

            socket.receive(response);

            // Wyświetl odpowiedź w postaci HEX
            System.out.println(bytesToHex(buffer, response.getLength()));

        } catch (Exception e) {

            System.err.println (e);

        }
    }

    }