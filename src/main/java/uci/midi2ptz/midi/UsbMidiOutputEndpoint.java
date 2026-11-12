package uci.midi2ptz.midi;

/** USB MIDI output endpoint implementation.
 *
 * <p>Sends normalized {@link MidiEvent} instances through a shared {@link UsbMidiTransport} using raw USB MIDI packets.</p>
 *
 * <p>LED blinking support is inherited from {@link AbstractMidiOutputEndpoint}.</p> */
public class UsbMidiOutputEndpoint extends AbstractMidiOutputEndpoint {

    /**  Shared USB MIDI transport. */
    private final UsbMidiTransport transport;

    /** Creates a USB MIDI output endpoint.
     *
     * @param transport shared USB MIDI transport
     * @param hardwareBlinking indicates whetherthe target controller supports native LED blinking */
    public UsbMidiOutputEndpoint(UsbMidiTransport transport, boolean hardwareBlinking) {

        super(hardwareBlinking);
        this.transport = transport;
    }

    /**  Starts USB MIDI communication. */
    @Override
    public void start() {
        transport.start();
    }

    /** Sends a MIDI Control Change message through the USB MIDI transport.
     *
     * @param cc controller number
     * @param value controller value*/
    @Override
    protected void sendCC(int cc, int value) {

        transport.sendPacket(
                new byte[]{
                        (byte) 0x0B,
                        (byte) 0xB0,
                        (byte) cc,
                        (byte) value
                }
        );
    }

    /** Sends a MIDI Note On or Note Off message through the USB MIDI transport.
     *
     * @param channel MIDI channel
     * @param note note number
     * @param value note velocity/value*/
    @Override
    protected void sendNote(
            int channel,
            int note,
            int value) {

        int status =
                value > 0
                        ? 0x90 + channel
                        : 0x80 + channel;

        transport.sendPacket(
                new byte[]{
                        (byte) 0x09,
                        (byte) status,
                        (byte) note,
                        (byte) value
                }
        );
    }


    /** Releases endpoint resources.
     *
     * <p>The underlying USB transport is managed by {@link MidiEndpointFactory} and is not closed here.</p>*/
    @Override
    public void close() {
    }
}
