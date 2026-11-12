package uci.midi2ptz.midi;

/** Common interface for MIDI output transports. *
 * <p>Implementations deliver {@link MidiEvent} instances to physical MIDI hardware and may  provide LED and indicator feedback support.</p> */
public interface MidiOutputEndpoint extends AutoCloseable {

    /** Opens and initializes the output endpoint.
     * @throws Exception if initialization fails*/
    void start() throws Exception;

    /** Sends a MIDI event to the output device.
     *  @param event event to transmit */
    void send(MidiEvent event);

    /**Sets an output control to the specified value.
     *
     * @param cc control identifier
     * @param value output value*/
    void setOutputValue(int cc, int value);

    /** Enables blinking for the specified output.
     *
     * @param cc control identifier
     * @param onValue value used during the active blink phase*/
    void enableBlink(int cc, int onValue);

    /** Disables blinking for the specified output.
     * @param cc control identifier */
    void disableBlink(int cc);

    /** Indicates whether the underlying hardware supports native blinking.
     *
     * @return {@code true} if blinking is handled by the device itself*/
    boolean supportsHardwareBlinking();

    /** Closes the endpoint and releases all associated resources.*/
    @Override
    void close();
}
