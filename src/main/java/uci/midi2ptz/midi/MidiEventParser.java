package uci.midi2ptz.midi;

import javax.sound.midi.ShortMessage;

/**
 * Utility responsible for translating raw MIDI transport messages into normalized {@link MidiEvent} instances.
 *
 * <p>The parser provides a common representation for events originating from different MIDI transports:</p>
 *
 * <ul>
 *     <li>Java Sound MIDI ({@link ShortMessage})</li>
 *     <li>USB_DIRECT raw MIDI packets</li>
 * </ul>
 *
 * <p>Only Control Change (CC) and Note On/Off messages are currently translated. Unsupported message types are ignored.</p>
 */
public final class MidiEventParser {

    /** Utility class.*/
    private MidiEventParser() {
    }

    /**
     * Converts a Java MIDI {@link ShortMessage} into a normalized {@link MidiEvent}.
     *
     * <p>Supported message types:</p>
     * <ul>
     *     <li>{@link ShortMessage#CONTROL_CHANGE}</li>
     *     <li>{@link ShortMessage#NOTE_ON}</li>
     *     <li>{@link ShortMessage#NOTE_OFF}</li>
     * </ul>
     *
     * @param sm source MIDI message
     * @param deviceId logical source device identifier
     *
     * @return translated MIDI event or {@code null} when the message type is unsupported
     */
    public static MidiEvent fromShortMessage(ShortMessage sm, String deviceId) {

        int channel = sm.getChannel() + 1;      // Internal model uses MIDI channels 1-16, Java MIDI exposes channels as 0-15.
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

    /**
     * Converts a raw USB-MIDI packet into a normalized {@link MidiEvent}.
     *
     * <p>The packet is expected to contain a standard USB MIDI event payload.</p>
     *
     * <p>Supported message types:</p>
     * <ul>
     *     <li>Control Change (0xB0)</li>
     *     <li>Note On (0x90)</li>
     *     <li>Note Off (0x80)</li>
     * </ul>
     *
     * @param packet USB-MIDI packet
     * @param deviceId logical source device identifier
     *
     * @return translated MIDI event or {@code null} when the packet is invalid or contains an unsupported message type
     */
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