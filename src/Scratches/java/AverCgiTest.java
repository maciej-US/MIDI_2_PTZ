import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class AverCgiTest {

    public static void main(String[] args) {
        try {
            String ip = "100.75.75.100";

            String username = "srnkc";
            String password = "admin123";

            // ✅ parametry PTZ
            //String cmd = "SetPtzf=4,5,3";
            String cmd = "go_home";

            String urlString = "http://" + ip + "/cgi-bin?cmd=" + cmd;
            System.out.println(urlString);

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");

            // ✅ BASIC AUTH (tu działa, skoro GUI działało)
            String auth = username + ":" + password;
            String encoded = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encoded);

            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int responseCode = conn.getResponseCode();
            System.out.println("HTTP: " + responseCode);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}