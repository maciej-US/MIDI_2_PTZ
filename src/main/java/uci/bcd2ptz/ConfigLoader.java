package uci.bcd2ptz;

import java.io.File;
import java.nio.file.Files;

public class ConfigLoader {

    private static final String CONFIG_FILE = "config.json";

    public static AppConfig loadConfig() {

        try {
            File file = new File(CONFIG_FILE);

            if (!file.exists()) {
                return null; // brak config
            }

            String content = new String(Files.readAllBytes(file.toPath()));

            // 🔥 super prosty parsing (na teraz)
            AppConfig config = new AppConfig();

            config.midiInputName = extract(content, "midiInput");
            config.midiOutputName = extract(content, "midiOutput");

            return config;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String extract(String json, String key) {

        String pattern = "\"" + key + "\"\\s*:\\s*\"";

        int start = json.indexOf(pattern);

        if (start == -1) return null;

        start += pattern.length();
        int end = json.indexOf("\"", start);

        return json.substring(start, end);
    }
}