package uci.bcd2ptz;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {


        //TODO: ładowanie konfiguracjiz pliku.
        // SCAFF:  (ustawianie ręczne!)
        int midiIn = 5; //MIDI IN z konsoli
        int midiOut = 4; //MIDI OUT z konsoli

        MidiEventRouter midi = MidiEventRouter.getInstance(midiIn, midiOut);
        midi.setOutputMode(MidiEventRouter.OutputMode.PARSED);

        MidiOutputController midiOutCtrl = MidiOutputController.getInstance(midiOut);

        // ✅ GUI
        MainGUI gui = new MainGUI();
        new Thread(gui).start();

        // ✅ CORE
        MidiToPTZ core = new MidiToPTZ(midi, midiOutCtrl, gui);
        gui.setCore(core);

        // ✅ start systemu
        core.start();

        // ✅ VISCA watchdog
        ViscaPTZController.startPanWatchdog();
    }
}