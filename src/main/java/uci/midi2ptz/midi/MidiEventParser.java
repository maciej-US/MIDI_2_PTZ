package uci.midi2ptz.midi;

import javax.sound.midi.ShortMessage;

public final class MidiEventParser {

    private MidiEventParser() {
    }

    public static MidiEvent fromShortMessage(ShortMessage sm, String deviceId) {

        int channel = sm.getChannel() + 1;
        int command = sm.getCommand();
        int number = sm.getData1();
        int value = sm.getData2();

        if (command == ShortMessage.CONTROL_CHANGE) {

            return new MidiEvent(
                    MidiEvent.Type.CC,
                    channel,
                    number,
                    value,
                    deviceId
            );
        }

        if (command == ShortMessage.NOTE_ON
                || command == ShortMessage.NOTE_OFF) {

            return new MidiEvent(
                    MidiEvent.Type.NOTE,
                    channel,
                    number,
                    value,
                    deviceId
            );
        }
        return null;
    }

    public static MidiEvent fromUsbPacket(
            byte[] packet,
            String deviceId) {

        if (packet.length < 4) {
            return null;
        }

        int status = packet[1] & 0xFF;
        int data1 = packet[2] & 0xFF;
        int data2 = packet[3] & 0xFF;
        int command = status & 0xF0;
        int channel = (status & 0x0F) + 1;

        if (command == 0xB0) {
            return new MidiEvent(
                    MidiEvent.Type.CC,
                    channel,
                    data1,
                    data2,
                    deviceId
            );
        }

        if (command == 0x90 || command == 0x80) {

            return new MidiEvent(
                    MidiEvent.Type.NOTE,
                    channel,
                    data1,
                    data2,
                    deviceId
            );
        }
        return null;
    }

}