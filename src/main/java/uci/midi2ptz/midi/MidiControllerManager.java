package uci.midi2ptz.midi;

import com.fasterxml.jackson.databind.ObjectMapper;
import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.Midi2PTZ;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.ControlCore;
import uci.midi2ptz.core.ControlEventBus;

import java.io.File;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static uci.midi2ptz.Midi2PTZ.isMidiDeviceAvailable;

public class MidiControllerManager {


    //API ELEMTS
    private final AppConfigManager configManager;
    private final ControlEventBus eventBus;
    private final ControlCore.RunMode runMode;

    //MIDI
    private MidiController controller;
    private final Map<ControlCore.ControlPanelId, ControlCore.ControlPanelId> midiToCoreBindings = new EnumMap<>(ControlCore.ControlPanelId.class);
    private final Map<ControlCore.ControlPanelId, ControlCore.ControlPanelId> coreToMidiBindings = new EnumMap<>(ControlCore.ControlPanelId.class);

    public MidiControllerManager(AppConfigManager configManager, ControlEventBus eventBus, ControlCore.RunMode runMode) {

        this.configManager = configManager;
        this.eventBus = eventBus;
        //this.cameraManager = cameraManager;
        this.runMode = runMode;
    }

    public ConfigurationUpdateResults initialize() {

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        if (controller!= null && controller.isRunning()){
            stopController();
        }

        if (!isConfigured()) {
            results.getErrors().put(AppConfigManager.ConfigurationSection.MIDI, "MIDI not configured.");
            return results;
        }

        if (!isAvailable()) {
            results.getErrors().put(AppConfigManager.ConfigurationSection.MIDI, "MIDI device not available.");
            return results;
        }
        startController();

        return results;
    }

    private boolean isConfigured() {

        AppConfig.MidiConfig midi = configManager
                        .getConfig()
                        .getSystem()
                        .getMidi();

        return midi != null
                && midi.getInput() != null
                && midi.getOutput() != null
                && midi.getInput().getDeviceIndex() >= 0
                && midi.getOutput().getDeviceIndex() >= 0
                && midi.getInput().getConfigFile() != null
                && !midi.getInput()
                .getConfigFile()
                .isBlank();
    }

    private boolean isAvailable() {

        String deviceName =
                configManager
                        .getConfig()
                        .getSystem()
                        .getMidi()
                        .getInput()
                        .getDeviceName();

        return isMidiDeviceAvailable(deviceName);
    }

    public boolean isMidiControllerRunning(){
        if (this.controller != null) {
            return this.controller.isRunning();
        }else{
            return false;
        }
    }

    public MidiController getMidiController(){
        return this.controller;
    }

    private void startController() {

        try {

            AppConfig config = configManager.getConfig();

            AppConfig.MidiEndpoint input =
                    config.getSystem()
                            .getMidi()
                            .getInput();

            AppConfig.MidiEndpoint output =
                    config.getSystem()
                            .getMidi()
                            .getOutput();

            ObjectMapper mapper = new ObjectMapper();

            MidiConfig midiConfig = mapper.readValue(new File(input.getConfigFile()), MidiConfig.class);

            controller = new MidiController(
                            midiConfig,
                            output.getDeviceIndex(),
                            input.getDeviceIndex(),
                            eventBus,
                            runMode,
                            this
                    );

           //=== MIDI BINGIGS TO CORE PANELS
            clearBindings();
            for (AppConfig.CameraConfig cameraConfig : config.getSystem().getCameras()){
                String midiPanel = cameraConfig.getAssignedMidiPanel();
                String corePanel = cameraConfig.getAssignedGuiPanel();

                if (midiPanel == null || midiPanel.isBlank() || corePanel == null || corePanel.isBlank()){
                    continue;
                }

                //DEBUG
                //System.out.println("MIDI panel: " + midiPanel + "   CORE panel: " + corePanel);

                assignMidiPanel(ControlCore.ControlPanelId.valueOf(midiPanel),
                                ControlCore.ControlPanelId.valueOf(corePanel));
            }

            controller.start();

            System.out.println("[MIDI] Started: " + midiConfig.getControllerName());

        } catch (Exception e) {
            controller = null;
            e.printStackTrace();
        }
    }

    public void stopController() {

        if (controller != null) {
            controller.shutdown();
            controller = null;
            System.out.println("[MIDI] Stopped.");
        }
    }

    public void updateMidiIndicators() {

        Set<ControlCore.ControlPanelId> activeMidiPanels = new HashSet<>();

        for (ControlCore.ControlPanelId corePanel : ControlCore.ControlPanelId.values()) {
            ControlCore.ControlPanelId midiPanel = getMidiBinding(corePanel);

            if (midiPanel != null) {
                activeMidiPanels.add(midiPanel);

                //DEBUG
                System.out.println("CORE " + corePanel + " -> MIDI " + midiPanel);
                controller.updateCameraIndicators(midiPanel);
            }
        }

    }

    //============================
    //   GETTERS & SETTERS
    //============================

    public ControlCore.ControlPanelId getCoreBinding(
            ControlCore.ControlPanelId midiPanel) {

        return midiToCoreBindings.get(midiPanel);
    }

    public ControlCore.ControlPanelId getMidiBinding(
            ControlCore.ControlPanelId corePanel) {

        return coreToMidiBindings.get(corePanel);
    }

    public void assignMidiPanel(ControlCore.ControlPanelId midiPanel, ControlCore.ControlPanelId corePanel) {

        //DEBUG
        //System.out.println("REassigning panels MIDI " + midiPanel + " to " + corePanel );

        ControlCore.ControlPanelId oldCore = midiToCoreBindings.get(midiPanel);

        if (oldCore != null) {
            coreToMidiBindings.remove(oldCore);
        }

        ControlCore.ControlPanelId oldMidi = coreToMidiBindings.get(corePanel);

        if (oldMidi != null) {
            midiToCoreBindings.remove(oldMidi);
        }

        coreToMidiBindings.put(corePanel,midiPanel);
        if (midiPanel != null) {
            midiToCoreBindings.put(midiPanel, corePanel);
        }
    }

    public void clearBindings() {

        midiToCoreBindings.clear();
        coreToMidiBindings.clear();
    }

    //=============================
    //    STATIC LOADING METHODS
    //=============================

    public static void validateConfiguration(AppConfig config, ControlCore.RunMode mode) throws Exception {

        //Debug
        System.out.println("[MIDI CONTROLLER] config validation in " + mode + " mode");

        AppConfig.MidiEndpoint input = config.getSystem().getMidi().getInput();
        AppConfig.MidiEndpoint output = config.getSystem().getMidi().getOutput();

        ObjectMapper mapper = new ObjectMapper();

        MidiConfig midiConfig = mapper.readValue(new File(input.getConfigFile()), MidiConfig.class);

        MidiController controller =
                new MidiController(
                        midiConfig,
                        output.getDeviceIndex(),
                        input.getDeviceIndex(),
                        null,
                        mode,
                        null
                );

        controller.shutdown();
    }



}