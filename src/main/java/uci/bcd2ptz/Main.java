package uci.bcd2ptz;

import uci.bcd2ptz.midi.MidiController;
import uci.bcd2ptz.midi.MidiEventRouter;
import uci.bcd2ptz.midi.MidiOutputController;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;

import javax.swing.*;

public class Main {

     public static void main(String[] args) throws Exception {

        enum RunMode {
            FULL,
            NO_HARDWARE
        }

        RunMode mode;
        MidiEventRouter midi = null;
        MidiOutputController midiOut = null;

        //AppConfig config = ConfigLoader.loadConfig();
         AppConfig config = new AppConfig();

        // ================= CONFIG =================

        if (config == null) {

            System.out.println("Brak config.json -> NO_HARDWARE");
            mode = RunMode.NO_HARDWARE;

        } else {

            /*int inIndex = MidiEventRouter.findDeviceIndexByName(
                    config.midiInputName, true);

            int outIndex = MidiEventRouter.findDeviceIndexByName(
                    config.midiOutputName, false);*/

            int inIndex = 5;
            int outIndex = 4;

            if (inIndex == -1 || outIndex == -1) {

                System.out.println("Nie znaleziono urządzenia MIDI -> NO_HARDWARE");
                mode = RunMode.NO_HARDWARE;

            } else {

                mode = RunMode.FULL;

                midi = MidiEventRouter.getInstance(inIndex, outIndex);
                midi.setOutputMode(MidiEventRouter.OutputMode.PARSED);

                midiOut = MidiOutputController.getInstance(outIndex);
            }
        }

        // ================= GUI =================

        MainGUI gui = new MainGUI();
        new Thread(gui).start();

         // ================= PTZ CAMERA MANAGER =================

         PTZCameraManager cameraManager =
                 new PTZCameraManager();

         //TODO: przejście na pobieranie ścieżki z config.json
         String viscaConfig =
                 "C:\\GIT\\BCD2PTZ\\BCD2PTZ\\src\\main\\resources\\AVER_PTZ310.json";


         PTZCamera cameraA = cameraManager.addCamera("CAM_A","100.75.75.100",52381, viscaConfig);
         //ViscaPTZController controller = new ViscaPTZController(cameraA, viscaConfig);
         //cameraA.setController(controller);
         System.out.println("CAM_A status: " + cameraA);

         String cameraB = null;


         // ================= MIDI CONTROLLER CONFIG =================

         String midiConfig =
                 "C:\\GIT\\BCD2PTZ\\BCD2PTZ\\src\\main\\resources\\BCD3000.json";

         MidiController midiController =
                 new MidiController(
                         midiConfig,
                         5,
                         4
                 );

         ControlCore core =
                 new ControlCore(
                         gui,
                         cameraManager,
                         midiController
                 );

        //TODO: pobranie przypisania kamery z config.json
         core.assignCamera(ControlCore.Panel.A,cameraManager.getCamera("CAM_A"));
         core.start();

        // ================= WARN =================

        if (mode == RunMode.NO_HARDWARE) {

            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            null,
                            "Nie znaleziono urządzenia MIDI.\nTryb bez hardware.",
                            "BCD2PTZ",
                            JOptionPane.WARNING_MESSAGE
                    )
            );
        }

        // ================= VISCA =================

        //ViscaPTZController.startPanWatchdog();
    }
}