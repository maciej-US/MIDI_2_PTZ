package uci.midi2ptz;

import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.ControlCore;
import uci.midi2ptz.core.ControlEventBus;
import uci.midi2ptz.gui.MainGUI;
import uci.midi2ptz.midi.MidiControllerManager;
import uci.midi2ptz.ptz.PTZCameraManager;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Midi2PTZ {

     static void main(String[] args)throws Exception {

        //TODO: Splash SCREEN

        // ======= GENRAL CONFIG =============

         Path configDir = Paths.get("config");

         if (!Files.exists(configDir)) {
             Files.createDirectories(configDir);
         }

        //======== CONFIGURATION STAGE ==============

         ControlEventBus eventBus = new ControlEventBus();

         AppConfigManager configManager = new AppConfigManager("config/config.json", eventBus);

         PTZCameraManager cameraManager = new PTZCameraManager(eventBus, configManager);

         MidiControllerManager midiManager = new MidiControllerManager(configManager, eventBus, ControlCore.RunMode.FULL);

         configManager.setCameraManager(cameraManager);
         configManager.setMidiManager(midiManager);

         ConfigurationUpdateResults results = configManager.loadOrCreateDefault();
         if (!results.getErrors().isEmpty()){
            System.err.println((results));
        }

         //========== FIRST LAUNCH CHECK ==================

         boolean firsLaunch = configManager.isFirstLaunch();

         //DEBUG

         MainGUI gui = new MainGUI(
                         eventBus,
                         configManager,
                         midiManager,
                         firsLaunch
                 );

         gui.setCameraManager(cameraManager);
         new Thread(gui).start();
         //gui.run();

         // ================= RUNTIME MODE =================

         if (!firsLaunch) {
             try {
                 startRuntime(gui, configManager, cameraManager, midiManager, eventBus);
             }catch (Exception e){
                 e.printStackTrace();
             }
         }
    }

    /* =====================================
           HELPERS, CONFIGS, CHECKERS
       =====================================*/

    public static ConfigurationUpdateResults startRuntime (
            MainGUI gui,
            AppConfigManager configManager,
            PTZCameraManager cameraManager,
            MidiControllerManager midiManager,
            ControlEventBus eventBus) throws Exception{

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        cameraManager.initialize();

        midiManager.initialize();

        ControlCore.RunMode mode =
                midiManager.isMidiControllerRunning()
                        ? ControlCore.RunMode.FULL
                        : ControlCore.RunMode.NO_HARDWARE;

        ControlCore core = new ControlCore(
                configManager,
                cameraManager,
                midiManager,
                eventBus,
                mode
        );

        core.start();

        configManager.setCore(core);

        core.setGUI(gui);

        while (!gui.isInitialized()) {
            Thread.sleep(50);
        }

        configManager.setGui(gui);

        cameraManager.reconfigureCameras();
        core.syncInterface();

        gui.refreshStatusInfo();

        //SCAFF
        //midiManager.getMidiController().clearPanel(ControlPanelId.B);

        return results;
    }

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