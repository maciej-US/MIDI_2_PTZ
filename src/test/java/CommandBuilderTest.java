package uci.bcd2ptz.visca;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class CommandBuilderTest {

    private static final String CAMERA_IP = "100.75.75.100";
    private static final int CAMERA_PORT = 52381;

    private static int sessionId = 1;

    public static void main(String[] args) {

        try {

            String configFile =
                    "C:\\GIT\\BCD2PTZ\\BCD2PTZ\\src\\main\\resources\\AVER_PTZ310";

            ObjectMapper mapper = new ObjectMapper();

            ViscaConfig config =
                    mapper.readValue(
                            new File(configFile),
                            ViscaConfig.class
                    );

            System.out.println();
            System.out.println("Camera: "
                    + config.getCameraName());

            Scanner scanner = new Scanner(System.in);

            System.out.print("PAN [-170 .. 170]: ");
            double panDeg = scanner.nextDouble();

            System.out.print("TILT [-30 .. 90]: ");
            double tiltDeg = scanner.nextDouble();

            System.out.print("Speed [1..24]: ");
            int speed = scanner.nextInt();

            /*ViscaCommandBuilder builder =
                    new ViscaCommandBuilder(config);*/

            Map<String, Object> params =
                    new HashMap<>();

            params.put("panSpeed", speed);
            params.put("tiltSpeed", speed);

            params.put("panPosition", panDeg);
            params.put("tiltPosition", tiltDeg);

           /* byte[] visca =
                    builder.build(
                            "absolutePosition",
                            params
                    );*/

            System.out.println();
            System.out.println("VISCA PAYLOAD");
/*
            System.out.println(
                    bytesToHex(visca)
            );

            byte[] packet =
                    wrapVisca(visca);

            send(packet);*/

            System.out.println();
            System.out.println("COMMAND SENT");
            /*System.out.print("PAN VISCA (HEX, np. 762C): ");
            String panHex = scanner.nextLine().trim();

            System.out.print("TILT VISCA (HEX, np. 468B): ");
            String tiltHex = scanner.nextLine().trim();

            System.out.print("Speed [1..24]: ");
            int speed = Integer.parseInt(
                    scanner.nextLine().trim()
            );


            int panVisca =
                    Integer.parseInt(panHex, 16);

            int tiltVisca =
                    Integer.parseInt(tiltHex, 16);

            System.out.println();
            System.out.println(
                    "PAN VISCA  : "
                            + String.format("%04X", panVisca)
            );

            System.out.println(
                    "TILT VISCA : "
                            + String.format("%04X", tiltVisca)
            );

            ViscaCommandBuilder builder =
                    new ViscaCommandBuilder(config);

            Map<String, Object> params =
                    new HashMap<>();

            params.put("panSpeed", speed);
            params.put("tiltSpeed", speed);

            params.put("panPosition", panVisca);
            params.put("tiltPosition", tiltVisca);

            byte[] visca =
                    builder.build(
                            "absolutePosition",
                            params
                    );

            System.out.println();
            System.out.println("VISCA PAYLOAD");
            System.out.println(
                    bytesToHex(visca)
            );

            byte[] packet =
                    wrapVisca(visca);

            send(packet);

            System.out.println();
            System.out.println("COMMAND SENT");*/

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private static byte[] wrapVisca(
            byte[] payload) {

        int length =
                payload.length + 4;

        byte[] packet =
                new byte[8 + payload.length];

        packet[0] = 0x01;
        packet[1] = 0x00;
        packet[2] = 0x00;
        packet[3] = (byte) length;

        packet[4] = 0x00;
        packet[5] = 0x00;
        packet[6] = 0x00;
        packet[7] = (byte) (sessionId++);

        System.arraycopy(
                payload,
                0,
                packet,
                8,
                payload.length
        );

        return packet;
    }

    private static void send(
            byte[] packet) throws Exception {

        DatagramSocket socket =
                new DatagramSocket();

        DatagramPacket dp =
                new DatagramPacket(
                        packet,
                        packet.length,
                        InetAddress.getByName(CAMERA_IP),
                        CAMERA_PORT
                );

        socket.send(dp);
        socket.close();
    }

    private static String bytesToHex(
            byte[] bytes) {

        StringBuilder sb =
                new StringBuilder();

        for (byte b : bytes) {

            sb.append(
                    String.format(
                            "%02X ",
                            b
                    )
            );
        }

        return sb.toString();
    }
}