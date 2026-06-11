package uci.bcd2ptz;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ViscaZoomController {

    private static final String HOST = "100.75.75.100";
    private static final int PORT = 52381;

    public static void sendZoom(int ccValue) {

        try {
            InetAddress address = InetAddress.getByName(HOST);
            DatagramSocket socket = new DatagramSocket();

            // ✅ przeliczenie zakresu
            int maxZoom = 0x6120;
            int zoom = (ccValue * maxZoom) / 127;

            // ✅ rozbicie VISCA (pqrs)
            int p = (zoom >> 12) & 0x0F;
            int q = (zoom >> 8) & 0x0F;
            int r = (zoom >> 4) & 0x0F;
            int s = zoom & 0x0F;

            // ✅ VISCA payload
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

            // ✅ AVer header (session = 1!)
            int length = visca.length + 4;

            byte[] packet = new byte[8 + visca.length];

            packet[0] = 0x01;
            packet[1] = 0x00;
            packet[2] = 0x00;
            packet[3] = (byte) length;

            packet[4] = 0x00;
            packet[5] = 0x00;
            packet[6] = 0x00;
            packet[7] = 0x01; // ✅ ważne!

            System.arraycopy(visca, 0, packet, 8, visca.length);

            // ✅ wysyłka
            DatagramPacket dp =
                    new DatagramPacket(packet, packet.length, address, PORT);

            socket.send(dp);

            socket.close();

            System.out.println("ZOOM → CC=" + ccValue + " VISCA=" + String.format("%04X", zoom));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}