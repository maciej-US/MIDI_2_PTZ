package uci.bcd2ptz.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    private SystemConfig system;

    private Map<String, Object> user;

    private Map<String, Object> presets;

    public AppConfig() {

        this.system = new SystemConfig();
        this.user = new HashMap<>();
        this.presets = new HashMap<>();
    }

    public SystemConfig getSystem() {
        return system;
    }

    public void setSystem(SystemConfig system) {
        this.system = system;
    }

    public Map<String, Object> getUser() {
        return user;
    }

    public void setUser(Map<String, Object> user) {
        this.user = user;
    }

    public Map<String, Object> getPresets() {
        return presets;
    }

    public void setPresets(Map<String, Object> presets) {
        this.presets = presets;
    }

    // ======================================
    // SYSTEM
    // ======================================
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemConfig {

        private MidiConfig midi = new MidiConfig();
        private List<CameraConfig> cameras = new ArrayList<>();
        private GuiConstraints guiConstraints = new GuiConstraints();
        private Map<String, Object> transport = new HashMap<>();
    }
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GuiConstraints {

        private String lcdFontPath;

    }

    // ======================================
    // MIDI
    // ======================================
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MidiConfig {
        private MidiEndpoint input = new MidiEndpoint();
        private MidiEndpoint output = new MidiEndpoint();
    }
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MidiEndpoint {

        private String deviceName;

        private int deviceIndex;

        private String configFile;

    }

    // ======================================
    // CAMERAS
    // ======================================
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CameraConfig {

        private String cameraId;

        private String displayName;

        private String ipAddress;

        private int port;

        private String viscaDefinition;

        private String assignedGuiPanel;

        private String assignedMidiPanel;

    }


    //TRANSPORT
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transport{

        private long socketTimeout;

        private int localPort;

    }

}