package uci.bcd2ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class AppConfigManager {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final File configFile;

    public AppConfigManager(
            String configPath) {

        this.configFile =
                new File(configPath);
    }

    public AppConfig load()
            throws IOException {

        return MAPPER.readValue(
                configFile,
                AppConfig.class
        );
    }

    public void save(AppConfig config) throws IOException {

        MAPPER.writeValue(
                configFile,
                config
        );
    }

    public boolean exists() {

        return configFile.exists();
    }

    public AppConfig loadOrCreateDefault()
            throws IOException {

        if (!exists()) {

            AppConfig config = createDefaultConfig();
            save(config);
            return config;
        }

        return load();
    }

    public AppConfig createDefaultConfig() {

        AppConfig config = new AppConfig();
        config.setUser(new HashMap<>());
        config.setPresets(new HashMap<>());
        return config;
    }
}