package uci.midi2ptz.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root configuration model for the MIDI2PTZ application. *
 * <p>This class represents the complete application configuration loaded from and persisted to a JSON configuration file.</p> *
 * <p>The configuration is divided into three major sections:</p>
 *
 * <ul>
 *     <li><b>system</b> - application infrastructure and hardware-related settings,</li>
 *     <li><b>user</b> - user-specific settings stored as arbitrary key-value pairs,</li>
 *     <li><b>presets</b> - user-defined presets and application state data.</li>
 * </ul>
 *
 * <p>The class is designed for serialization and deserialization using * the Jackson JSON library.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {


    /** System-level application configuration.*/
    @Getter @Setter
    private SystemConfig system;

    /** User-specific settings stored as key-value pairs.*/
    @Getter @Setter
    private Map<String, Object> user;

    /** User-defined presets and runtime configuration data.*/
    @Getter @Setter
    private Map<String, Object> presets;

    /**
     * Internal runtime flag.
     *
     * <p>This field is not intended to be persisted in the configuration  file and is used by the configuration manager to determine
     * whether a save operation is required.</p>
     */
    boolean changed;

    /** Creates a new configuration instance initialized with default values.*/
    public AppConfig() {

        this.system = new SystemConfig();
        this.user = new HashMap<>();
        this.presets = new HashMap<>();
        this.changed = false;
    }

    /**
     * Contains all system-related configuration sections.
     *
     * <p>This includes MIDI devices, PTZ cameras, GUI resources, and network transport parameters.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemConfig {

        /** MIDI subsystem configuration.*/
        private MidiConfig midi = new MidiConfig();

        /** List of configured PTZ cameras.*/
        private List<CameraConfig> cameras = new ArrayList<>();

        /** GUI resource and limitation definitions.*/
        private GuiConstraints guiConstraints = new GuiConstraints();

        /** Network transport configuration.*/
        private Transport transport = new Transport();
    }

    /**
     * Defines GUI resources and application-specific UI constraints.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GuiConstraints {
    }

    /**
     * Configuration of the MIDI subsystem.
     *
     * <p>Defines the input and output MIDI devices used by the application.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MidiConfig {

        /** MIDI input device configuration.*/
        private MidiEndpoint input = new MidiEndpoint();

        /** MIDI output device configuration.*/
        private MidiEndpoint output = new MidiEndpoint();
    }

    /**
     * Represents a MIDI input or output endpoint.
     *
     * <p>An endpoint describes a physical or virtual MIDI device
     * accessible through the operating system MIDI API.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MidiEndpoint {

        /** Human-readable MIDI device name as reported by the operating system.*/
        private String deviceName;

        /** Numerical MIDI device index returned by the MIDI API.         *
         * <p>This value can be used as an alternative device identification method when device names are not unique.</p>*/
        private int deviceIndex;

        /** Path to a device-specific MIDI mapping definition file.*/
        private String configFile;

    }

    /**
     * Configuration of a single PTZ camera instance.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CameraConfig {

        /** Unique camera identifier within the application.*/
        private String cameraId;

        /** User-friendly camera name displayed in the GUI.*/
        private String displayName;

        /** IPv4 or hostname address of the camera. */
        private String ipAddress;

        /** Network communication port used by the camera.*/
        private int port;

        /** Name or path of the VISCA protocol definition used for camera communication.*/
        private String viscaDefinition;

        /** GUI panel identifier assigned to the camera.*/
        private String assignedGuiPanel;

        /** MIDI control panel identifier assigned to the camera.*/
        private String assignedMidiPanel;

    }


    /**
     * Network transport configuration.
     *
     * <p>Defines local socket parameters used by the VISCA-over-IP communication layer.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transport{

        /**
         * Socket timeout in milliseconds.
         *
         * <p>Defines the maximum time the application waits for a response before considering the operation failed.</p>
         */
        private long socketTimeout;

        /**
         * Local UDP port used by the application.
         *
         * <p>A value of {@code 0} instructs the operating system to automatically allocate an available ephemeral port.</p>
         */
        private int localPort;

        /**
         * Local UDP IP address used by the application.
         */
        private String localIp;

        /**
         * Indicates that application uses automated IP recognition from system, or uses fixed IP address set in configuration
         */
        boolean autoIp;

    }

}