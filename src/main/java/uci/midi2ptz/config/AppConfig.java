package uci.midi2ptz.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import uci.midi2ptz.core.MainControlCore;

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

    public enum MidiTransport{
        GENERAL_MIDI,
        USB_DIRECT
    }

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

        /**
         * Application operating mode.
         * <p>Determines which subsystems are active during runtime.</p>
         */
        private MainControlCore.RunMode runMode = MainControlCore.RunMode.FULL;

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
     * <p>Defines the MIDI controller mapping, transport backend, and transport-specific configuration parameters.</p>
     *
     * <p>The subsystem supports multiple transport implementations:</p>     *
     * <ul>
     *     <li><b>GENERAL_MIDI</b> - communication through the Java MIDI API,</li>
     *     <li><b>USB_DIRECT</b> - direct communication with USB MIDI devices using LibUSB.</li>
     * </ul>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MidiConfig {

        /** Selected MIDI transport implementation. */
        private MidiTransport midiTransport;

        /** MIDI controller definition and mapping file configuration. */
        private ControllerDefinition controller;

        /** Java MIDI transport configuration.*/
        private JavaMidiTransportConfig javaMidi;

        /** Direct USB transport configuration.*/
        private UsbDirectTransportConfig usbDirect;
    }

    /**
     * MIDI controller description.
     *
     * <p>Defines the controller mapping file and controller-specific
     * runtime options.</p>
     */
    @Data
    public static class ControllerDefinition {

        /**
         * Human-readable controller name.
         *
         * <p>This value is used only for identification and display  purposes and does not affect device selection.</p>
         */
        private String deviceName;

        /**
         * Path to the MIDI controller definition file.
         * <p>The definition file contains mappings between physical controls and application actions.</p>
         */
        private String configFile;

        /**
         * Indicates whether the controller provides native LED blinking.
         * <p>If enabled, the application uses hardware blinking modes instead of emulating flashing states in software.</p>
         */
        private boolean hardwareBlinking;
    }


    /**
     * Configuration of the Java MIDI transport backend.
     *
     * <p>The transport uses the standard Java Sound MIDI API and  communicates with operating-system MIDI devices.</p>
     */
    @Data
    public static class JavaMidiTransportConfig {

        /** Configured MIDI input device name. */
        private String inputDeviceName;

        /** Configured MIDI output device name. */
        private String outputDeviceName;

        /** MIDI input device index reported by the system MIDI API. */
        private int inputDeviceIndex;

        /** MIDI output device index reported by the system MIDI API. */
        private int outputDeviceIndex;
    }

    /**
     * Configuration of the direct USB MIDI transport backend.
     *
     * <p>This transport bypasses the Java MIDI subsystem and communicates directly with the USB device through LibUSB.</p>     *
     * <p>The values stored here are automatically detected during device selection and normally do not require manual editing.</p>
     */
    @Data
    public static class UsbDirectTransportConfig {

        /** USB Vendor Identifier (VID).*/
        private int vid;

        /** USB Product Identifier (PID).*/
        private int pid;

        /**
         * MIDI streaming interface number.         *
         * <p>The interface must expose a USB MIDI Streaming endpoint.</p>
         */
        private int interfaceNumber;

        /** USB bulk input endpoint used for receiving MIDI packets.*/
        private int inputEndpoint;

        /** USB bulk output endpoint used for transmitting MIDI packets.*/
        private int outputEndpoint;
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