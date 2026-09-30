package uci.bcd2ptz.midi;

import com.fasterxml.jackson.databind.ObjectMapper;
import uci.bcd2ptz.AppConfig;
import uci.bcd2ptz.AppConfigManager;
import uci.bcd2ptz.Main;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlEventBus;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import static uci.bcd2ptz.core.ControlCore.ControlPanelId.*;

public class MidiControllerManager implements AppConfigManager.ConfigurationListener {


    //API ELEMTS
    private final AppConfigManager configManager;
    private final ControlEventBus eventBus;
    //private final PTZCameraManager cameraManager;

    private final ControlCore.RunMode runMode;

    //MIDI
    private MidiController controller;
    private final Map<ControlCore.ControlPanelId, ControlCore.ControlPanelId> midiToCoreBindings = new EnumMap<>(ControlCore.ControlPanelId.class);
    private final Map<ControlCore.ControlPanelId, ControlCore.ControlPanelId> coreToMidiBindings = new EnumMap<>(ControlCore.ControlPanelId.class);

    public MidiControllerManager(
            AppConfigManager configManager,
            ControlEventBus eventBus,
            //PTZCameraManager cameraManager,
            ControlCore.RunMode runMode) {

        this.configManager = configManager;
        this.eventBus = eventBus;
        //this.cameraManager = cameraManager;
        this.runMode = runMode;
    }

    public void initialize() {

        reloadController();
    }

    private void reloadController() {

        stopController();

        if (!isConfigured()) {

            System.out.println("[MIDI] Not configured.");
            return;
        }

        if (!isAvailable()) {

            System.out.println("[MIDI] Device not available.");
            return;
        }
        startController();
    }

    private boolean isConfigured() {

        AppConfig.MidiConfig midi =
                configManager
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

        return Main.isMidiDeviceAvailable(deviceName);
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

    private void stopController() {

        if (controller != null) {
            controller.shutdown();
            controller = null;
            System.out.println("[MIDI] Stopped.");
        }
    }

    @Override
    public void configurationChanged(AppConfig config) {
        System.out.println("[MIDI] Configuration changed.");
        reloadController();
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

    public void unassignMidiPanel(ControlCore.ControlPanelId midiPanel) {

        ControlCore.ControlPanelId corePanel = midiToCoreBindings.remove(midiPanel);

        if (corePanel != null) {
            coreToMidiBindings.remove(corePanel);
        }
    }

    public void clearBindings() {

        midiToCoreBindings.clear();
        coreToMidiBindings.clear();
    }

}