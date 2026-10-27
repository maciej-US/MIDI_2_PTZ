package uci.midi2ptz.midi;

/**
 * Represents a MIDI message received from or sent to a MIDI device.
 *
 * <p>This record provides a normalized representation of MIDI events  used internally by the application. Hardware-specific MIDI messages
 * are converted into this format before being processed by the control subsystem.</p>
 *
 * <p>Only the MIDI message types required by the application are represented.</p>
 *
 * <p>In the MIDI2PTZ runtime, CC messages are primarily used for continuous camera controls such as pan, tilt, zoom and focus,
 * while NOTE messages are commonly used for buttons, mode switches and status indicators.</p>
 *
 * @param type MIDI message type
 * @param channel MIDI channel or controller identifier associated with the event
 * @param number MIDI note number or controller number
 * @param value event value
 * @param deviceId identifier of the source or destination MIDI device
 */
public record MidiEvent(Type type, int channel, int number, int value, String deviceId) {

    /**
     * Supported MIDI message types.
     */
    public enum Type {

        /**
         * MIDI Control Change (CC) message.
         *
         * <p>Typically used for sliders, knobs, faders, jog wheels and similar continuous controls.</p>
         */
        CC,

        /**
         * MIDI Note On/Off message.
         *
         * <p>Typically used for buttons, pads and indicator controls represented as note events.</p>
         */
        NOTE
    }
}