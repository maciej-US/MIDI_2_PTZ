import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ViscaPresetController {

    private static final String HOST = "100.75.75.100";
    private static final int PORT = 52381;

    public static void recallPreset(int preset) {

        try {
            InetAddress address = InetAddress.getByName(HOST);
            DatagramSocket socket = new DatagramSocket();

            // ✅ VISCA
            byte[] visca = new byte[]{
                    (byte) 0x81,
                    (byte) 0x01,
                    (byte) 0x04,
                    (byte) 0x3F,
                    (byte) 0x02,
                    (byte) preset,
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

            System.out.println("PRESET CALL: " + preset);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}