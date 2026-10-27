package uci.midi2ptz.midi;

/**
 * Describes a MIDI device detected by the Java MIDI subsystem.
 *
 * <p>The record contains basic information used for device discovery, configuration and selection within the application.</p>
 *
 * <p>Both input and output capabilities are represented using the transmitter and receiver counts reported by the underlying
 * MIDI implementation.</p>
 *
 * @param deviceName human-readable device name
 * @param deviceIndex device index returned by the MIDI subsystem
 * @param rxTransmitters number of MIDI transmitters exposed by the device. A value >= 0 usually indicates that the device can act as a MIDI input.
 *
 * @param txReceivers number of MIDI receivers exposed by the device. A value >= 0 usually indicates that the device can act as a MIDI output.
 */
public record MidiDeviceInfo(
        String deviceName,
        int deviceIndex,
        int rxTransmitters,
        int txReceivers) {

    /**
     * Returns a compact device description suitable for UI controls, configuration dialogs and debugging output.
     *
     * @return formatted device name and index
     */
    @Override
    public String toString() {

        return deviceName
                + " ["
                + deviceIndex
                + "]";
    }
}