package uci.midi2ptz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import lombok.Setter;
import uci.midi2ptz.core.ControlCore;
import uci.midi2ptz.core.ControlEventBus;
import uci.midi2ptz.core.ControlPanel;
import uci.midi2ptz.gui.MainGUI;
import uci.midi2ptz.midi.*;
import uci.midi2ptz.ptz.PTZCameraManager;
import uci.midi2ptz.visca.ViscaConfig;

import javax.sound.midi.*;
import java.io.File;
import java.net.DatagramSocket;
import java.util.*;


import static javax.sound.midi.MidiSystem.getMidiDeviceInfo;
import static uci.midi2ptz.Midi2PTZ.startRuntime;

public class AppConfigManager {

    //Configuration management
    private AppConfig config;
    public enum ConfigurationSection {
            CONFIG_MANAGER,
            TRANSPORT,
            MIDI,
            CAMERA,
            GUI,
            JSON_OPERATIONS
    };

    public enum UpdateMode {
        VALIDATE_ONLY,
        COMMIT,
        INITIAL_LOAD,
        INITIAL_COMMIT,
        DEFAULT_LOAD
    }

    //private final List<ConfigurationListener>listeners = new ArrayList<>();

    //API
    private volatile PTZCameraManager cameraManager;
    @Getter @Setter
    private volatile MidiControllerManager midiManager;
    private volatile MainGUI gui;
    @Setter
    private volatile ControlCore core;
    private volatile ControlEventBus eventBus;

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final File configFile;

    @Getter @Setter
    private boolean firstLaunch = true;

    public AppConfigManager(String configPath, ControlEventBus eventBus) {

        this.eventBus = eventBus;
        this.configFile = new File(configPath);
    }

    public ConfigurationUpdateResults loadConfiguration(UpdateMode mode) {

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();
        AppConfig newConfig;

        try {
            newConfig = MAPPER.readValue(configFile, AppConfig.class);
        }catch (Exception e){
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
            return results;
        }
        results = updateConfiguration(newConfig, mode);

        if (!results.getErrors().isEmpty()){
            return results;
        }
        return results;
    }

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

        if (!results.getErrors().isEmpty()){
            return results;
        }
        return results;

    }

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

    public ConfigurationUpdateResults saveConfiguration(AppConfig config, File targetFile){

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

    public boolean exists() {

        return configFile.exists();
    }

    public ConfigurationUpdateResults loadOrCreateDefault() {

        try {
            if (!exists()) {
                this.config = createDefaultConfig();
                this.config.setChanged(true);
                return new ConfigurationUpdateResults();
            }
            firstLaunch = false;
            return loadConfiguration(UpdateMode.DEFAULT_LOAD);

        }catch (Exception e){
            ConfigurationUpdateResults results = new ConfigurationUpdateResults();
            results.getErrors().put(ConfigurationSection.JSON_OPERATIONS, e.getMessage());
            return results;
        }
    }

    public AppConfig createDefaultConfig() {

        AppConfig config = new AppConfig();
        config.setUser(new HashMap<>());
        config.setPresets(new HashMap<>());
        this.config = config;
        return config;
    }

    public void setCameraManager (PTZCameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    public void setGui (MainGUI gui) {
        this.gui = gui;
    }

    //--- Config update after changes
    public void updateMidiBinding(ControlPanel targetCorePanel, ControlCore.ControlPanelId midiPanel){

        String cameraId = "CAM_" + targetCorePanel.getId();

        this.config.getSystem()
                .getCameras()
                .stream()
                .filter(c -> cameraId.equals(c.getCameraId()))
                .findFirst()
                .ifPresent(c -> {
                            c.setAssignedMidiPanel(
                                    midiPanel.toString()
                            );
                        }
                );

        this.config.setChanged(true);
    }



    public ConfigurationUpdateResults updateConfiguration (AppConfig newConfig, UpdateMode updateMode){

        //DEBUG
        /*System.out.println("updateConfiguration [configManager] running mode" + updateMode);
        System.out.println("updateConfiguration [configManager] received config\n" + newConfig);*/

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        Map<String, Object> newTransportConfig = newConfig.getSystem().getTransport();
        AppConfig.MidiConfig newMidiConfig = newConfig.getSystem().getMidi();
        List<AppConfig.CameraConfig> newCamerasConfig = new ArrayList<>(newConfig.getSystem().getCameras());

        boolean transportChanged;
        boolean midiChanged;
        boolean camerasChanged;
        boolean hardwareValidation =
                !updateMode.equals(UpdateMode.INITIAL_LOAD)
                && !updateMode.equals(UpdateMode.DEFAULT_LOAD)
                && !updateMode.equals(UpdateMode.INITIAL_COMMIT);

        boolean deploymentRequired = updateMode == UpdateMode.COMMIT || updateMode == UpdateMode.INITIAL_COMMIT;

        DatagramSocket preparedSocket = null;

        //STAGE 1
        //Transport validation and changes calculation

        if (updateMode == UpdateMode.INITIAL_LOAD
                || updateMode == UpdateMode.INITIAL_COMMIT
                || updateMode == UpdateMode.DEFAULT_LOAD ) {

            transportChanged = true;
            midiChanged = true;
            camerasChanged = true;
            try {
                preparedSocket = PTZCameraManager.validateTransport(newConfig);
            }catch (Exception e){
                results.getErrors().put(ConfigurationSection.TRANSPORT, e.getMessage());
                return results;
            }

        } else {

            // PTZ Transport configuration update
            Map<String, Object> oldTransportConfig = this.config.getSystem().getTransport();
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

        if (midiChanged && updateMode != UpdateMode.DEFAULT_LOAD) {
            MidiDevice input = null;
            MidiDevice output = null;
            MidiConfig midiMapping = null;

            AppConfig.MidiConfig midiConfig = newConfig.getSystem().getMidi();

            try {
                //--- Midi Config file
                String mappingPath = midiConfig.getInput().getConfigFile();
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
                e.printStackTrace();
                results.getErrors().put(ConfigurationSection.MIDI, e.getMessage());
                //DEBUG
                System.out.println("[CFG MAN] return after MIDI config file validation");
                return results;
            }
            if (hardwareValidation) {
                try {
                    MidiControllerManager.validateConfiguration(newConfig, ControlCore.RunMode.TEST);
                } catch (Exception e) {
                    results.getErrors().put(ConfigurationSection.MIDI, e.getMessage());
                    return results;
                }
            }
        }


        // STAGE 3 - camera configuration validation

        if (camerasChanged) {
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
                    if (hardwareValidation) {
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
                //DEBUG
                System.out.println("[CFG MAN] return after cameras validation " + results.getErrors().get(ConfigurationSection.CAMERA));
                return results;
            }
        }

        if (updateMode.equals(UpdateMode.VALIDATE_ONLY)){
            try {
                if (preparedSocket != null && !preparedSocket.isClosed()) {
                    preparedSocket.close();
                }
            }catch (Exception ignored){}

            return results;
        }

        this.config = newConfig;

        if (updateMode == UpdateMode.INITIAL_COMMIT) {
            try {
                results = cameraManager.updateTransportSocket(preparedSocket);
                if (!results.getErrors().isEmpty()){
                  return results;
                }
                results = startRuntime(gui, this, cameraManager, midiManager, eventBus);
                if (!results.getErrors().isEmpty()){
                    return results;
                }
                return results;
            } catch (Exception e) {
                return results;
            }
        }

        if (updateMode == UpdateMode.DEFAULT_LOAD) {

            results = cameraManager.updateTransportSocket(preparedSocket);

            if (!results.getErrors().isEmpty()) {
                return results;
            }

        }

        if (deploymentRequired) {

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

    public void refreshAfterReconfiguration() {

        if (gui != null) {
            gui.reloadConfiguration();
        }

        if (core != null) {
            core.reconfigurePanels();
            core.syncInterface();
        }

    }

    public AppConfig getConfig() {
        return this.config;
    }

    public static List<MidiDeviceInfo> getMidiDevices() {

        List<MidiDeviceInfo> result = new ArrayList<>();

        MidiDevice.Info[] infos = getMidiDeviceInfo();

        for (int i = 0; i < infos.length; i++) {

            try {
                MidiDevice device = MidiSystem.getMidiDevice(infos[i]);

                result.add(new MidiDeviceInfo(infos[i].getName(), i, device.getMaxTransmitters(), device.getMaxReceivers()));

            } catch (MidiUnavailableException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static List<MidiDeviceInfo> getMidiInputs() {

        return getMidiDevices()
                .stream()
                .filter(d ->
                        d.getRxTransmitters() >= 0)
                .toList();
    }

    public static List<MidiDeviceInfo> getMidiOutputs() {

        return getMidiDevices()
                .stream()
                .filter(d ->
                        d.getTxReceivers() >= 0)
                .toList();
    }

}