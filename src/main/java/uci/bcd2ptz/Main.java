package uci.bcd2ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import uci.bcd2ptz.config.AppConfig;
import uci.bcd2ptz.config.AppConfigManager;
import uci.bcd2ptz.config.ConfigurationUpdateResults;
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
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

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
             cameraManager.initialize();
             midiManager.initialize();

             // ================= CORE =================

             ControlCore.RunMode mode = midiManager.isMidiControllerRunning() ? ControlCore.RunMode.FULL : ControlCore.RunMode.NO_HARDWARE;

             ControlCore core = new ControlCore(
                     cameraManager,
                     midiManager,
                     eventBus,
                     mode
             );

             core.start();
             configManager.setCore(core);

             // ================= GUI =================

             core.setGUI(gui);
             //--synchronizacja stanów
             while (!gui.isInitialized()) {Thread.sleep(50);}
             configManager.setGui(gui);
             core.syncInterface();
             gui.refreshStatusInfo();
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