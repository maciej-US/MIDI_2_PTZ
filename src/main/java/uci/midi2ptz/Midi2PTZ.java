package uci.midi2ptz;

import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.MainControlCore;
import uci.midi2ptz.core.ControlEventBus;
import uci.midi2ptz.gui.MainGUI;
import uci.midi2ptz.gui.SplashScreen;
import uci.midi2ptz.midi.MidiControllerManager;
import uci.midi2ptz.ptz.PTZCameraManager;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Midi2PTZ {

    private static SplashScreen splashScreen;

     static void main(String[] args)throws Exception {

         splashScreen = new SplashScreen();
         SplashScreen.show();

        // ======= GENRAL CONFIG =============

         initializeApplicationFolders();

         Path configDir = Paths.get("config");

         if (!Files.exists(configDir)) {
             Files.createDirectories(configDir);
         }

        //======== CONFIGURATION STAGE ==============

         ControlEventBus eventBus = new ControlEventBus();

         AppConfigManager configManager = new AppConfigManager("config/config.json", eventBus);

         PTZCameraManager cameraManager = new PTZCameraManager(eventBus, configManager);

         MidiControllerManager midiManager = new MidiControllerManager(configManager, eventBus, MainControlCore.RunMode.FULL);

         configManager.setCameraManager(cameraManager);
         configManager.setMidiManager(midiManager);

         ConfigurationUpdateResults results = configManager.loadOrCreateDefault();
         System.out.println("Config loaded = " + (configManager.getConfig() != null));

         System.out.println(results);

         if (!results.getErrors().isEmpty()){
            System.err.println((results));
        }

         //========== FIRST LAUNCH CHECK ==================

         boolean firsLaunch = configManager.isFirstLaunch();

         MainGUI gui = new MainGUI(
                         eventBus,
                         configManager,
                         midiManager,
                         firsLaunch
                 );

         gui.setCameraManager(cameraManager);
         new Thread(gui).start();

         // ================= RUNTIME MODE =================

         if (!firsLaunch) {
             try {
                 startRuntime(gui, configManager, cameraManager, midiManager, eventBus);
                 splashScreen.hide();
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

        MainControlCore.RunMode mode =
                midiManager.isMidiControllerRunning()
                        ? MainControlCore.RunMode.FULL
                        : MainControlCore.RunMode.NO_HARDWARE;

        configManager.getConfig().getSystem().setRunMode(mode);

        System.out.println("MidiManger status: " + mode);

        //MainControlCore.RunMode mode = configManager.getConfig().getSystem().getRunMode();

        MainControlCore core = new MainControlCore(
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
        splashScreen.hide();

        //results = cameraManager.reconfigureCameras();

        core.syncInterface();

        gui.rebuildPanels();
        gui.refreshStatusInfo();


        return results;
    }

    //--- Midi
    public static boolean isMidiDeviceAvailable(String deviceName) {


            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

            for (MidiDevice.Info info : infos) {

                if (info.getName().equalsIgnoreCase(deviceName)) {
                    return true;
                }
            }

        return false;
    }

    private static void initializeApplicationFolders() {

        try {
            Files.createDirectories(Paths.get("config"));
            Files.createDirectories(Paths.get("logs"));
            Files.createDirectories(Paths.get("presets"));

        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to create application directories", e);
        }
    }
}