/*
package uci.bcd2ptz;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.logging.Logger;

import static uci.bcd2ptz.visca.ViscaPTZController.*;

public class PositionCalibrationTest {

    private static final String CAMERA_IP = "100.75.75.100";
    private static final int VISCA_PORT = 52381;
    private static final Logger LOGGER = Logger.getLogger(PositionCalibrationTest.class.getName());

    private static int sessionId = 1;

    public static void main(String[] args) {

        try {

            Scanner scanner = new Scanner(System.in);

            DatagramSocket socket =
                    new DatagramSocket();

            socket.setSoTimeout(2000);

            InetAddress address =
                    InetAddress.getByName(
                            CAMERA_IP
                    );

            int[] testAngles = {
                    0,
                    30,
                    60,
                    90,
                    -30
            };

            */
/*byte[] response =
                    requestPosition();*//*

            */
/*byte[] response = new byte[] { 0, 1 };

            if (response == null) {

                System.out.println(
                        "Brak odpowiedzi"
                );

            }*//*


            System.out.println(
                    bytesToHex(
                            response,
                            response.length
                    )
            );

            for (int angle : testAngles) {

                System.out.println(
                        "\nUstaw TILT na "
                                + angle
                                + "° i ENTER"
                );

                scanner.nextLine();

                byte[] response =
                        requestPosition();

                if (response == null) {

                    System.out.println(
                            "Brak odpowiedzi"
                    );

                    continue;
                }

                System.out.println(
                        bytesToHex(
                                response,
                                response.length
                        )
                );
            }

            socket.close();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }



}*/
