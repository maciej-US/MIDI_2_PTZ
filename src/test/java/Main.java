import uci.bcd2ptz.ViscaPresetController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Main {

    public static void main(String[] args) {
        String host = "100.75.75.100";
        int port = 52381; // zmień na 100 jeśli faktycznie taki masz

        try (Socket socket = new Socket(host, port)) {

            System.out.println("Połączono z kamerą");

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            ViscaPresetController.recallPreset(95);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}