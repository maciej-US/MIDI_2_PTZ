package uci.bcd2ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlCore.ControlPanelId;
import uci.bcd2ptz.core.ControlEventBus;
import uci.bcd2ptz.gui.MainGUI;
import uci.bcd2ptz.midi.MidiController;
import uci.bcd2ptz.midi.MidiControllerManager;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.visca.ViscaConfig;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static uci.bcd2ptz.core.ControlEvent.EventSource.GUI;

public class Main {

    private MidiController midiController;

     static void main(String[] args)
            throws Exception {

        //TODO: Splash SCREEN

        // ======= GENRAL CONFIG =============

         Path configDir = Paths.get("config");

         if (!Files.exists(configDir)) {
             Files.createDirectories(configDir);
         }

        //======== CONFIG LOADER ==============

        AppConfigManager configManager = new AppConfigManager("config/config.json");

        AppConfig appConfig = configManager.loadOrCreateDefault();
        ControlCore.RunMode mode = ControlCore.RunMode.FULL;

        //==========ControlEvent BUS for ControlCoreListener

         ControlEventBus eventBus = new ControlEventBus();


         // ================= CAMERAS =================

        //TODO: Automatyka pobierania adresu IP
        PTZCameraManager cameraManager = new PTZCameraManager("100.75.75.78", 52381, eventBus);

        for (AppConfig.CameraConfig camera : appConfig.getSystem().getCameras()) {

            System.out.println(
                    "CONFIG ASSIGNEMNTS: "
                            + camera.getCameraId()
                            + " GUI=" + camera.getAssignedGuiPanel()
                            + " MIDI=" + camera.getAssignedMidiPanel()
            );
        }

        ObjectMapper viscaMapper = new ObjectMapper();

        for (AppConfig.CameraConfig cameraConfig : appConfig.getSystem().getCameras()) {

            ViscaConfig viscaConfig = viscaMapper.readValue(new File(cameraConfig.getViscaDefinition()),ViscaConfig.class);

            ControlPanelId assignedGuiPanel;
            try {
                assignedGuiPanel = ControlPanelId.valueOf(cameraConfig.getAssignedGuiPanel());
            } catch (Exception e){
                throw new RuntimeException(
                        "Camera " + cameraConfig.getCameraId() + " has no assigned GUI panel"
                        );
            }

            ControlPanelId assignedMidiPanel = null;

            if (cameraConfig.getAssignedMidiPanel() != null && !cameraConfig.getAssignedMidiPanel().isBlank()){
                assignedMidiPanel = ControlPanelId.valueOf(cameraConfig.getAssignedMidiPanel());
            }

            //DEBUG:
            //System.out.println("ASSIGNED MIDI [Main] " + cameraConfig.getCameraId() + " --> " + assignedMidiPanel);

            PTZCamera camera =
                    cameraManager.addCamera(
                            cameraConfig.getCameraId(),
                            cameraConfig.getIpAddress(),
                            cameraConfig.getPort(),
                            cameraConfig.getPort(),
                            (int)appConfig.getSystem().getTransport().get("socketTimeout"),
                            cameraConfig.getDisplayName(),
                            viscaConfig,
                            assignedGuiPanel,
                            assignedMidiPanel
                    );

            System.out.println(
                    cameraConfig.getCameraId()
                            + " name: "
                            + cameraConfig.getDisplayName()
                            + " status: "
                            + camera
                            + " Online: "
                            + camera.powerStatusRequest()
            );
        }

        //DEBUG
        System.out.println("PANEL ASSIGNMENT STATUS\n"
                //+ "MIDI PANELS " + cameraManager.getMidiAssignements()
                + "GUI PANELS  " + cameraManager.getCoreAssignements());


        // ================ MIDI ============

         MidiControllerManager midiManager = new MidiControllerManager(
                                configManager,
                                eventBus,
                                mode
                                );

         configManager.addConfigurationListener(midiManager);

         midiManager.initialize();

         if(!midiManager.isMidiControllerRunning()){
             mode = ControlCore.RunMode.NO_HARDWARE;
         }

        // ================= CORE =================

        ControlCore core = new ControlCore(
                        cameraManager,
                        midiManager,
                        eventBus,
                        mode
                );

        core.start();

        // ================= GUI =================

        MainGUI gui = new MainGUI(eventBus, configManager, midiManager);
        gui.setCameraManager(cameraManager);

        new Thread(gui).start();
        core.setGUI(gui);
        //--synchronizacja stanów
        while (!gui.isInitialized()){
            Thread.sleep(50);
        }
        core.syncInterface();
        gui.refreshStatusInfo();

        // ================= WARN =================

        if (mode == ControlCore.RunMode.NO_HARDWARE) {

            SwingUtilities.invokeLater(
                    () -> JOptionPane.showMessageDialog(
                            null,
                            "Nie znaleziono urządzenia MIDI.\nTryb bez hardware.",
                            "BCD2PTZ",
                            JOptionPane.WARNING_MESSAGE
                    )
            );
        }
    }

    /* =====================================
           HELPERS, CONFIGS, CHECKERS
       =====================================*/

    //--- Midi
    public static boolean isMidiDeviceAvailable(String deviceName) {


            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

            for (MidiDevice.Info info : infos) {

                if (info.getName()
                        .equalsIgnoreCase(deviceName)) {
                    //DEBUG
                    //System.out.println("KONSOLA AKTYWNA");
                    return true;
                }
            }

        return false;
    }
}