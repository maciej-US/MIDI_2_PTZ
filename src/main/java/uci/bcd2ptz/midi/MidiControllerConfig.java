package uci.bcd2ptz.midi;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class MidiControllerConfig {

    private final MidiConfig config;

    public MidiControllerConfig(
            String configFile) {

        try {

            ObjectMapper mapper = new ObjectMapper();
            config = mapper.readValue(new File(configFile), MidiConfig.class);
            System.out.println("[MIDI] Loaded config: " + config.getControllerName());

        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot load MIDI configuration",e);
        }
    }

    public MidiConfig getConfig() {
        return config;
    }
}