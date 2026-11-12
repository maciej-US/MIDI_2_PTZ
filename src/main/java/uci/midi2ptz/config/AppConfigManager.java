package uci.midi2ptz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import lombok.Setter;
import org.usb4java.*;
import uci.midi2ptz.core.MainControlCore;
import uci.midi2ptz.core.ControlEventBus;
import uci.midi2ptz.core.ControlPanel;
import uci.midi2ptz.gui.MainGUI;
import uci.midi2ptz.midi.*;
import uci.midi2ptz.ptz.PTZCameraManager;
import uci.midi2ptz.visca.ViscaConfig;

import javax.sound.midi.*;
import java.io.File;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.util.*;

import static uci.midi2ptz.core.MainControlCore.LOGGER;

/**
 * Central configuration management service for the MIDI2PTZ application.
 *
 * <p>This class is responsible for loading, validating, storing and applying application configuration changes at runtime.
 * The manager owns the active application configuration and coordinates validation and reconfiguration of runtime subsystems.</p>
 * <p>The manager coordinates configuration updates across multiple subsystems, including:</p>
 * <ul>
 *     <li>MIDI device configuration and mapping validation,</li>
 *     <li>VISCA/PTZ camera configuration and connectivity checks,</li>
 *     <li>network transport initialization and reconfiguration,</li>
 *     <li>GUI refresh and runtime synchronization.</li>
 * </ul>
 *
 * <p>Configuration changes are applied using a staged validation process to ensure that invalid settings cannot be committed to the running system.</p>
 *
 *<h2 Validation Workflow</h2>
 *
 * <pre> Configuration File
 *          |
 *          v
 *  JSON Deserialization
 *          |
 *          v
 *  MIDI/USB Validation
 *          |
 *          v
 * Transport Validation
 *          |
 *          v
 *   Camera Validation
 *          |
 *          v
 * Runtime Reconfiguration </pre>
 *
 * <p>MIDI/USB validation includes validation of the selected transport backend and controller definition file.</p> *
 * <p>Any validation failure immediately aborts the update process and returns the corresponding error details.</h>
 */
public class AppConfigManager {

    /**
     * Logical configuration subsystems identifiers participating in validation and runtime reconfiguration.     *
     * <p>Used by {@link ConfigurationUpdateResults} to associate validation  and configuration errors with a specific application component.</p>
     */
    public enum ConfigurationSection {
            CONFIG_MANAGER,     /** Configuration manager subsystem */
            TRANSPORT,          /** Network transport subsystem */
            MIDI,               /** MIDI device support subsystem */
            CAMERA,             /** PTZ camera managing subsystem */
            GUI,                /** User Interface subsystem */
            JSON_OPERATIONS     /** JSON serialization and deserialization operations */
    }

    /**
    * Configuration update modes.
    */
    public enum UpdateMode {
        VALIDATE_ONLY,          /** Validate configuration without applying any changes.*/
        COMMIT,                 /** Validate configuration and applying changes to the running application. */
        INITIAL_LOAD,           /** Initial application startup using an existing configuration file.*/
        FIRST_LAUNCH            /** First application launch using a newly generated configuration.*/
    }

    /**
     * Currently active application configuration.
     */
    @Getter
    private AppConfig config;

    @Setter
    private volatile PTZCameraManager cameraManager;
    @Getter @Setter
    private volatile MidiControllerManager midiManager;
    @Setter
    private volatile MainGUI gui;
    @Setter
    private volatile MainControlCore core;
    private final ControlEventBus eventBus;

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final File configFile;

    /**
     * Indicates that no valid configuration file existed and a default configuration was generated.
     */
    @Getter @Setter
    private boolean firstLaunch = true;

    /**
     * Creates a new configuration manager instance.
     *
     * @param configPath path to the application configuration file
     * @param eventBus application event bus used for subsystem communication
     */
    public AppConfigManager(String configPath, ControlEventBus eventBus) {

        this.eventBus = eventBus;
        this.configFile = new File(configPath);
    }

    /**
     * Loads configuration from the default configuration file and performs  validation according to the specified update mode.
     * <p>If validation succeeds, the loaded configuration becomes the active application configuration.</p>
     *
     * @param mode configuration update mode
     * @return validation and update result information
     */
    public ConfigurationUpdateResults loadConfiguration(UpdateMode mode){

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();
        AppConfig newConfig;

        try {
            newConfig = MAPPER.readValue(configFile, AppConfig.class);
        }catch (Exception e){
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
            return results;
        }

        results = updateConfiguration(newConfig, mode);

        if (results.getErrors().isEmpty()){
            this.config = newConfig;
            return results;
        }

        return results;
    }

    /**
     * Loads and validates configuration from an external file.
     * <p>The loaded configuration is validated as a runtime configuration and may be applied to the running application depending on validation results.</p>
     *
     * @param file source configuration file
     * @return validation and update result information
     */
    public ConfigurationUpdateResults loadConfiguration(File file){

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();
        AppConfig newConfig;

        try {
            newConfig = MAPPER.readValue(file, AppConfig.class);
        }catch (Exception e){
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
            return results;
        }

        results = updateConfiguration(newConfig, UpdateMode.COMMIT);

        return results;
    }

    /**
     * Saves the currently active configuration to the configured
     * application configuration file.
     *
     * @return operation result containing any serialization errors
     */
    public ConfigurationUpdateResults saveConfiguration() {

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();
        try {
            MAPPER.writeValue(configFile, this.config);
        }catch (Exception e){
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
        }
        firstLaunch = false;
        this.config.setChanged(false);
        return results;
    }

    /**
     * Validates and saves the specified configuration using the default configuration file path.     *
     * <p>The configuration is validated before serialization.</p>
     *
     * @param config configuration to save
     * @return save operation result
     */
    public ConfigurationUpdateResults saveConfiguration(AppConfig config) {

        ConfigurationUpdateResults results = updateConfiguration(config, UpdateMode.VALIDATE_ONLY);
        if (!results.getErrors().isEmpty()){
            return results;
        }
        try {
            MAPPER.writeValue(configFile, config);
        }catch (Exception e){
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
        }
        firstLaunch = false;
        this.config.setChanged(false);
        return results;
    }

    /**
     * Validates and saves the provided configuration to a specific target file.
     *
     * @param config configuration to save
     * @param targetFile destination file
     * @return save operation result
     */
    public ConfigurationUpdateResults saveConfiguration(AppConfig config, File targetFile) {

        ConfigurationUpdateResults results = updateConfiguration(config, UpdateMode.VALIDATE_ONLY);
        if (!results.getErrors().isEmpty()){
            return results;
        }

        try {
            MAPPER.writeValue(targetFile, config);
        }catch (Exception e){
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
        }
        config.setChanged(false);
        return results;
    }

    /**
     * Checks whether the application configuration file exists.
     *
     * @return {@code true} if the configuration file exists, otherwise {@code false}
     */
    public boolean configurationExists() {

        return configFile.exists();
    }

    /**
     * Loads the existing configuration or creates a new default configuration.
     *
     * <p>If the configuration file does not exist or contains invalid data, a new default configuration instance is generated.</p>     *
     * <p>This method is intended to be used during application startup.</p>
     *
     * @return configuration initialization result
     */
    public ConfigurationUpdateResults loadOrCreateDefault() {

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        try {
            // FIRST LAUNCH
            if (!configurationExists()) {
                config = createDefaultConfig();
                config.setChanged(true);
                firstLaunch = true;
                return results;
            }

            // LOAD CONFIG
            try {
                AppConfig loadedConfig = MAPPER.readValue(configFile, AppConfig.class);

                // STRUCTURE VALIDATION
                if (!isConfigurationStructureValid(loadedConfig)) {
                    LOGGER.warning("Configuration invalid.\nCreating default configuration.");
                    try {
                        Files.deleteIfExists(configFile.toPath());
                    } catch (Exception ignore) {}

                    config = createDefaultConfig();
                    config.setChanged(true);
                    firstLaunch = true;

                    return results;
                }

                // CONFIG ACCEPTED
                config = loadedConfig;
                firstLaunch = false;
                return loadConfiguration(UpdateMode.INITIAL_LOAD);

            } catch (Exception e) {
                LOGGER.warning("Unable to load configuration: " + e.getMessage());

                try {
                    Files.deleteIfExists(configFile.toPath());
                } catch (Exception ignore) {}

                config = createDefaultConfig();
                config.setChanged(true);
                firstLaunch = true;

                return results;
            }

        } catch (Exception e) {
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
            return results;
        }
    }

    /**
     * Creates a minimal valid configuration model.
     *
     * <p>The configuration contains initialized subsystem structures but does not create any MIDI or camera definitions.</p>
     * @return newly created configuration instance
     */
    public AppConfig createDefaultConfig() {

        AppConfig config = new AppConfig();
        config.setUser(new HashMap<>());
        config.setPresets(new HashMap<>());
        this.config = config;
        return config;
    }

    /**
     * Updates MIDI-to-core panel assignment.     *
     * <p>The mapping is stored in the camera definition and persisted with the main configuration.</p>
     */
    public void updateMidiBinding(ControlPanel targetCorePanel, MainControlCore.ControlPanelId midiPanel){

        String cameraId = "CAM_" + targetCorePanel.getId();

        this.config.getSystem()
                .getCameras()
                .stream()
                .filter(c -> cameraId.equals(c.getCameraId()))
                .findFirst()
                .ifPresent(c -> c.setAssignedMidiPanel(
                        midiPanel.toString()
                )
                );

        this.config.setChanged(true);
    }

    /**
     * /**
     *  Validates and optionally applies a candidate configuration.
     * <p>The method performs staged validation of transport, MIDI, controller mapping, VISCA definitions and camera connectivity before any
     *  runtime reconfiguration is attempted.</p>
     *
     *  <p>Depending on {@link UpdateMode}, validation may be performed without affecting the running system.</p>
     *
     * <p>The update process consists of multiple validation stages:</p>
     * <ol>
     *     <li>change detection,</li>
     *     <li>MIDI configuration validation,</li>
     *     <li>network transport validation,</li>
     *     <li>camera and VISCA validation,</li>
     *     <li>runtime subsystem reconfiguration.</li>
     * </ol>
     * <p>Depending on the selected update mode, validation may be performed without modifying the running application.</p>
     *
     * @param newConfig candidate configuration
     * @param updateMode update strategy
     * @return validation and update results
     */
    public ConfigurationUpdateResults updateConfiguration (AppConfig newConfig, UpdateMode updateMode){

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        AppConfig.Transport newTransportConfig = newConfig.getSystem().getTransport();
        AppConfig.MidiConfig newMidiConfig = newConfig.getSystem().getMidi();
        List<AppConfig.CameraConfig> newCamerasConfig = new ArrayList<>(newConfig.getSystem().getCameras());

        boolean transportChanged;
        boolean midiChanged;
        boolean camerasChanged;
        boolean runtimeValidation = updateMode == UpdateMode.COMMIT || updateMode == UpdateMode.VALIDATE_ONLY;

        DatagramSocket preparedSocket = null;

        //STAGE 1
        //Changes calculation
        if (updateMode == UpdateMode.INITIAL_LOAD || updateMode == UpdateMode.FIRST_LAUNCH) {
            transportChanged = true;
            midiChanged = true;
            camerasChanged = true;
        } else {
            // PTZ Transport configuration update
            AppConfig.Transport oldTransportConfig = this.config.getSystem().getTransport();
            transportChanged = !Objects.equals(oldTransportConfig, newTransportConfig);

            // MIDI configuration update
            AppConfig.MidiConfig oldMidiConfig = this.config.getSystem().getMidi();
            midiChanged = !Objects.equals(oldMidiConfig, newMidiConfig);

            // CAMERAS configuration update
            List<AppConfig.CameraConfig> oldCamerasConfig = new ArrayList<>(this.config.getSystem().getCameras());
            newCamerasConfig.sort(Comparator.comparing(AppConfig.CameraConfig::getCameraId));
            oldCamerasConfig.sort(Comparator.comparing(AppConfig.CameraConfig::getCameraId));
            camerasChanged = !Objects.equals(oldCamerasConfig, newCamerasConfig);
        }

        if (!results.getErrors().isEmpty()){
            return results;
        }

        // STAGE 2 - MIDI validation

        if (midiChanged && runtimeValidation) {

            MidiConfig midiMapping;
            AppConfig.MidiConfig midiConfig = newConfig.getSystem().getMidi();

            try {
                //--- Midi Config file
                String mappingPath = midiConfig.getController().getConfigFile();
                if (mappingPath == null || mappingPath.isBlank()) {
                    throw new IllegalArgumentException("Brak ścieżki pliku konfiguracji MIDI");
                }
                File midiConfigFile = new File(mappingPath);

                if (!midiConfigFile.exists()) {
                    throw new IllegalArgumentException("Nie znaleziono pliku konfiguracji MIDI");
                }

                ObjectMapper mapper = new ObjectMapper();
                midiMapping = mapper.readValue(midiConfigFile, MidiConfig.class);

                if (midiMapping == null
                        || midiMapping.getControls() == null
                        || midiMapping.getControls().isEmpty()
                        || midiMapping.getBindings() == null
                        || midiMapping.getBindings().isEmpty()) {

                    throw new IllegalArgumentException("Błędna zawartość pliku konfiguracji MIDI");
                }
            } catch (Exception e) {
                results.getErrors().put(ConfigurationSection.MIDI, e.getMessage());
                return results;
            }
            if (runtimeValidation) {
                try {
                    MidiControllerManager.validateConfiguration(newConfig, MainControlCore.RunMode.TEST);
                } catch (Exception e) {
                    results.getErrors().put(ConfigurationSection.MIDI, e.getMessage());
                    return results;
                }
            }
        }

        //STAGE 3 - TRANSPORT VALIDATION

        if (runtimeValidation) {
            try {
                preparedSocket = PTZCameraManager.validateTransport(newConfig);
            }catch (Exception e) {
                results.getErrors().put(ConfigurationSection.TRANSPORT, e.getMessage());
                return results;
            }
        }

        if (updateMode == UpdateMode.INITIAL_LOAD){
            cameraManager.initializeTransport(newConfig);
        }

        // STAGE 4 - camera configuration validation
        if (camerasChanged && runtimeValidation) {
            try {
                for (AppConfig.CameraConfig camera : newConfig.getSystem().getCameras()) {

                    // ===== VISCA CONFIG
                    String viscaPath = camera.getViscaDefinition();
                    if (viscaPath == null || viscaPath.isBlank()) {
                        throw new IllegalArgumentException("Brak pliku konfiguracji VISCA dla " + camera.getCameraId());
                    }

                    File viscaFile = new File(viscaPath);
                    if (!viscaFile.exists()) {
                        throw new IllegalArgumentException("Nie znaleziono pliku konfiguracji VISCA dla " + camera.getCameraId());
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.readValue(viscaFile, ViscaConfig.class);

                    // ===== CONNECTION TEST
                    if (runtimeValidation) {
                        boolean online = cameraManager.testConnection(
                                camera.getIpAddress(),
                                camera.getPort(),
                                viscaPath
                        );

                        if (!online) {
                            throw new IllegalArgumentException("Brak połączenia z kamerą " +
                                    camera.getCameraId() + " (" + camera.getIpAddress() + ":" + camera.getPort() + ")");
                        }
                    }
                }

            } catch (Exception e) {
                results.getErrors().put(
                        ConfigurationSection.CAMERA,
                        e.getMessage()
                );
                return results;
            }
        }

        this.config = newConfig;

        if (updateMode == UpdateMode.VALIDATE_ONLY){
            try {
                if (preparedSocket != null && !preparedSocket.isClosed()) {
                    preparedSocket.close();
                }
            }catch (Exception ignored){}

            return results;
        }


        if (updateMode == UpdateMode.INITIAL_LOAD) {
            try {
                cameraManager.initializeTransport(this.config);
                if (!results.getErrors().isEmpty()){
                    return results;
                }
                return results;
            } catch (Exception e) {
                return results;
            }
        }

        if (updateMode == UpdateMode.FIRST_LAUNCH){
            gui.rebuildPanels();
        }

        if (runtimeValidation) {

            if (transportChanged) {
                results = cameraManager.updateTransportSocket(preparedSocket);
            }

            if (camerasChanged || transportChanged) {
                results = cameraManager.reconfigureCameras();
            }

            if (midiChanged) {
                results = midiManager.initialize();
            }
        }

        refreshAfterReconfiguration();
        return results;
    }

    /**
     * Synchronizes runtime subsystems after a  successful configuration update.
     *
     * <p>Reloads GUI configuration, rebuilds control panels and synchronizes controller state with active PTZ devices.</p>
     */
    public void refreshAfterReconfiguration() {

        if (gui != null) {
            gui.reloadConfiguration();
            gui.rebuildPanels();
        }

        if (core != null) {
            core.reconfigurePanels();
            core.syncInterface();
        }
    }

    /**
     * Performs a basic structural validation of a configuration object.
     * <p>The method verifies that all required top-level configuration sections are present.</p>
     *
     * @param config configuration to validate
     * @return {@code true} if the required structure is present, otherwise {@code false}
     */
    private boolean isConfigurationStructureValid(AppConfig config){

        if (config == null) {
            return false;
        }

        if (config.getSystem() == null) {
            return false;
        }

        if (config.getSystem().getTransport() == null) {
            return false;
        }

        if (config.getSystem().getMidi() == null) {
            return false;
        }

        return config.getSystem().getCameras() != null;
    }



}