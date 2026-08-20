package uci.bcd2ptz;


import javax.sound.midi.MidiEvent;

public interface MidiEventListener {
    void onMidiEvent(MidiEvent event);
}

