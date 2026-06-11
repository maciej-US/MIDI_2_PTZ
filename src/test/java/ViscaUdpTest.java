import uci.bcd2ptz.ViscaPresetController;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ViscaUdpTest {

    public static void main(String[] args) {

        try {
            String host = "100.75.75.100";
            int port = 52381;

            InetAddress address = InetAddress.getByName(host);
            DatagramSocket socket = new DatagramSocket();

            /*// ✅ RUCH W GÓRĘ
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

            socket.close();*/
            ViscaPresetController.recallPreset(97);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}