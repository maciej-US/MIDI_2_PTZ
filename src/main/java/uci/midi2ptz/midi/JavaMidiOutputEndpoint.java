package uci.midi2ptz.midi;

import javax.sound.midi.*;
import static uci.midi2ptz.core.MainControlCore.LOGGER;

/**
 * Java Sound MIDI output endpoint.
 *
 * <p>This implementation provides a MIDI output transport based on the standard Java MIDI subsystem ({@link javax.sound.midi.MidiSystem}).</p>
 *
 * <p>The endpoint is responsible only for translating application-level {@link MidiEvent} objects into Java MIDI messages and transmitting them
 * to the selected MIDI output device.</p>
 *
 * <p>Blinking logic and output state management are inherited from {@link AbstractMidiOutputEndpoint}.</p>
 * <p>This implementation is intended for devices exposed through the Java Sound MIDI subsystem and is independent of the USB_DIRECT transport.</p>
 */
public class JavaMidiOutputEndpoint extends AbstractMidiOutputEndpoint {

    /** Java MIDI receiver used for message transmission.*/
    private Receiver receiver;

    /** Opened Java MIDI output device.*/
    private MidiDevice device;

    /** Java MIDI device index.*/
    private final int deviceIndex;

    /**
     * Creates a Java MIDI output endpoint.
     *
     * <p>The physical MIDI device is not opened during construction.  Device initialization is performed by {@link #start()}.</p>
     *
     * @param deviceIndex index of the Java MIDI output device
     * @param hardwareBlinking indicates whether the controller supports native blinking*/
    public JavaMidiOutputEndpoint(int deviceIndex, boolean hardwareBlinking) {

        super(hardwareBlinking);
        this.deviceIndex = deviceIndex;
    }

    /**
     * Opens the selected MIDI output device and prepares it for message transmission.
     * <p>The underlying Java MIDI receiver is obtained from the selected {@link MidiDevice}.
     * @throws Exception if the MIDI device cannot be opened
     */
    @Override
    public void start() throws Exception {
        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        this.device = MidiSystem.getMidiDevice(devices[deviceIndex]);
        this.device.open();
        receiver = device.getReceiver();

        LOGGER.fine("MIDI OUT ready: " + devices[deviceIndex].getName());
    }

    /**
     * Sends a MIDI Control Change message.
     *
     * @param cc MIDI controller number
     * @param value MIDI controller value
     */
    @Override
    protected void sendCC( int cc, int value) {

        if (receiver == null) {
            return;
        }

        try {
            ShortMessage msg = new ShortMessage();
            msg.setMessage(
                    ShortMessage.CONTROL_CHANGE,
                    0,
                    cc,
                    value
            );
            receiver.send(msg, -1);

        } catch (Exception e) {
            LOGGER.warning ("MIDI CC send error: " + e.getMessage());
        }
    }

    /**
     * Sends a MIDI Note On or Note Off message.
     *
     * @param channel MIDI channel
     * @param note MIDI note number
     * @param value note velocity or note state value
     */
    @Override
    protected void sendNote(int channel, int note, int value) {

        if (receiver == null) {
            return;
        }

        try {
            ShortMessage msg = new ShortMessage();

            if (value > 0) {
                msg.setMessage(
                        ShortMessage.NOTE_ON,
                        channel,
                        note,
                        value
                );
            } else {
                msg.setMessage(
                        ShortMessage.NOTE_OFF,
                        channel,
                        note,
                        0
                );
            }

            receiver.send(msg, -1);

        } catch (Exception e) {
            LOGGER.warning("MIDI NOTE send error: " + e.getMessage());
        }
    }

    /**
     * Releases all resources associated with this endpoint, including the software blinking engine and Java MIDI resources.
     * <p>The underlying MIDI receiver is closed and the physical MIDI device is disconnected from the Java MIDI subsystem.</p>
     */
    @Override
    public void close() {

        stopBlinkEngine();

        if (receiver != null) {
            receiver.close();
            receiver = null;
        }

        if (device != null && device.isOpen()) {
            device.close();
            LOGGER.fine("MIDI OUT Closed");
        }
    }

}