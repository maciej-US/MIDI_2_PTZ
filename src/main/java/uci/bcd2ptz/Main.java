package uci.bcd2ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlEventBus;
import uci.bcd2ptz.gui.MainGUI;
import uci.bcd2ptz.midi.MidiConfig;
import uci.bcd2ptz.midi.MidiController;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.visca.ViscaConfig;
import uci.bcd2ptz.core.ControlCore.RunMode.*;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.swing.*;
import java.io.File;

public class Main {

    public static void main(String[] args)
            throws Exception {

        //TODO: Splash SCREEN

        //======== CONFIG LOADER ==============

        AppConfigManager configManager =
                new AppConfigManager(
                        "src/main/resources/config.json"
                );

        AppConfig appConfig = configManager.loadOrCreateDefault();
        ControlCore.RunMode mode = ControlCore.RunMode.FULL;

        //==========ControlEvent BUS for ControlCoreListener
        ControlEventBus eventBus = new ControlEventBus();

        // ================= CAMERAS =================

        PTZCameraManager cameraManager = new PTZCameraManager();

        ObjectMapper viscaMapper = new ObjectMapper();

        for (AppConfig.CameraConfig cameraConfig : appConfig.getSystem().getCameras()) {

            ViscaConfig viscaConfig =
                    viscaMapper.readValue(
                            new File(
                                    cameraConfig.getViscaDefinition()
                            ),
                            ViscaConfig.class
                    );

            PTZCamera camera =
                    cameraManager.addCamera(
                            cameraConfig.getCameraId(),
                            cameraConfig.getIpAddress(),
                            cameraConfig.getPort(),
                            cameraConfig.getPort(),
                            viscaConfig
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


        /* =================
                  MIDI
          =================*/

        //====== ACCESIBILITY CHECK ===========

        boolean midiAvailable = isMidiDeviceAvailable(appConfig.getSystem().getMidi().getInput().getDeviceName());

        String startupWarning = null;

        if (!midiAvailable) {

            mode = ControlCore.RunMode.NO_HARDWARE;

            startupWarning =
                    "Nie znaleziono konsolety MIDI:\n\n"
                            + appConfig.getSystem().getMidi().getInput().getDeviceName()
                            + "\n\n"
                            + "System uruchomiony w trybie NO HARDWARE.";
        }

        MidiController midiController = null;

        //DEBUG:
        //System.out.println("MAIN MODE: " + mode.toString());

        if (mode != ControlCore.RunMode.NO_HARDWARE) {

            AppConfig.MidiEndpoint midiInput =
                    appConfig.getSystem()
                            .getMidi()
                            .getInput();

            AppConfig.MidiEndpoint midiOutput =
                    appConfig.getSystem()
                            .getMidi()
                            .getOutput();

            ObjectMapper midiMapper = new ObjectMapper();

            MidiConfig midiConfig =
                    midiMapper.readValue(
                            new File(
                                    midiInput.getConfigFile()
                            ),
                            MidiConfig.class
                    );

            try {
                midiController =
                        new MidiController(
                                midiConfig,
                                midiOutput.getDeviceIndex(),
                                midiInput.getDeviceIndex(),
                                eventBus,
                                mode
                        );

                midiController.start();
                System.out.println(
                        "[MIDI] Loaded config: "
                                + midiConfig.getControllerName()
                );

            } catch (Exception e) {

                mode = ControlCore.RunMode.NO_HARDWARE;
                e.printStackTrace();

                midiController = null;
            }
        }

        // ================= CORE =================

        ControlCore core =
                new ControlCore(
                        cameraManager,
                        midiController,
                        eventBus,
                        mode
                );

        for (AppConfig.CameraConfig config
                : appConfig.getSystem().getCameras()) {

            PTZCamera camera =
                    cameraManager.getCamera(
                            config.getCameraId()
                    );

            core.assignCamera(
                    ControlCore.ControlPanelId.valueOf(
                            config.getAssignedPanel()
                    ),
                    camera
            );
        }

        core.start();

        // ================= GUI =================

        MainGUI gui = new MainGUI(eventBus);
        gui.setCameraManager(cameraManager);
        new Thread(gui).start();
        core.setGUI(gui);
        //--synchronizacja stanów
        while (!gui.isInitialized()){
            Thread.sleep(50);
        }
        core.syncInterface();

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
           HELPERS, ADD-ONS, CHECKERS
       =====================================*/

    public static boolean isMidiDeviceAvailable(
            String deviceName) {


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