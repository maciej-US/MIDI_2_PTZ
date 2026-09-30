package uci.bcd2ptz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    private SystemConfig system;

    private Map<String, Object> user;

    private Map<String, Object> presets;



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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemConfig {

        private MidiConfig midi;

        private List<CameraConfig> cameras;

        public MidiConfig getMidi() {
            return midi;
        }

        public void setMidi(MidiConfig midi) {
            this.midi = midi;
        }

        public List<CameraConfig> getCameras() {
            return cameras;
        }

        public void setCameras(List<CameraConfig> cameras) {
            this.cameras = cameras;
        }

        private GuiConstraints guiConstraints;

        public GuiConstraints getGuiConstraints() {
            return guiConstraints;
        }

        public void setGuiConstraints(GuiConstraints guiConstraints) {this.guiConstraints = guiConstraints;}

        private Map<String, Object> transport;

        public Map<String, Object> getTransport() {return transport; }

        public void setTransport(Map<String, Object> transport) { this.transport = transport; }


    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GuiConstraints {

        private String lcdFontPath;

        public String getLcdFontPath() {
            return lcdFontPath;
        }

        public void setLcdFontPath(String lcdFontPath) {
            this.lcdFontPath = lcdFontPath;
        }
    }

    // ======================================
    // MIDI
    // ======================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MidiConfig {

        private MidiEndpoint input;

        private MidiEndpoint output;

        public MidiEndpoint getInput() {
            return input;
        }

        public void setInput(MidiEndpoint input) {
            this.input = input;
        }

        public MidiEndpoint getOutput() {
            return output;
        }

        public void setOutput(MidiEndpoint output) {
            this.output = output;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MidiEndpoint {

        private String deviceName;

        private int deviceIndex;

        private String configFile;

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public int getDeviceIndex() {
            return deviceIndex;
        }

        public void setDeviceIndex(int deviceIndex) {
            this.deviceIndex = deviceIndex;
        }

        public String getConfigFile() {
            return configFile;
        }

        public void setConfigFile(String configFile) {
            this.configFile = configFile;
        }
    }

    // ======================================
    // CAMERAS
    // ======================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CameraConfig {

        private String cameraId;

        private String displayName;

        private String ipAddress;

        private int port;

        private String viscaDefinition;

        private String assignedGuiPanel;

        private String assignedMidiPanel;

        public String getCameraId() {
            return cameraId;
        }

        public void setCameraId(String cameraId) {
            this.cameraId = cameraId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getViscaDefinition() {
            return viscaDefinition;
        }

        public void setViscaDefinition(String viscaDefinition) {
            this.viscaDefinition = viscaDefinition;
        }

        public String getAssignedGuiPanel() {
            return assignedGuiPanel;
        }

        public void setAssignedGuiPanel(String assignedGuiPanel) {
            this.assignedGuiPanel = assignedGuiPanel;
        }

        public String getAssignedMidiPanel() {
            return assignedMidiPanel;
        }

        public void setAssignedMidiPanel(String assignedMidiPanel) {
            this.assignedMidiPanel = assignedMidiPanel;
        }
    }


    //TRANSPORT
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transport{

        private long socketTimeout;

        public long getSocketTimeout() {
            return this.socketTimeout;
        }

        public void setSocketTimeout(long socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

    }

}