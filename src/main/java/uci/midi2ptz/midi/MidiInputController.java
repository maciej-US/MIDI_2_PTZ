package uci.midi2ptz.midi;

import lombok.Getter;
import lombok.Setter;
import uci.midi2ptz.core.MainControlCore;

import javax.sound.midi.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * MIDI input device adapter.
 *
 * <p>This class provides the connection between a physical MIDI input device and the application's internal event model.</p>
 *
 * <p>Incoming MIDI messages received from the Java MIDI subsystem are converted into normalized {@link MidiEvent} instances and
 * placed into an internal event queue for further processing by the MIDI controller layer.</p>
 *
 * <p>The implementation supports Control Change (CC) and Note On/Off messages.</p>
 * <h2>Event Flow</h2>
 *
 * <pre>
 * MIDI Device
 *      |
 *      v
 * MidiInputController
 *      |
 *      v
 * MidiEvent
 *      |
 *      v
 * eventQueue
 *      |
 *      v
 * MidiController
 * </pre>
 */
public class MidiInputController implements Receiver {

    private final int deviceIndex;
    private final MidiDevice device;

    /**
     * Queue containing translated MIDI events.
     *
     * <p>Incoming MIDI messages are converted to application-level {@link MidiEvent} instances and enqueued for asynchronous
     * processing by the MIDI controller subsystem.</p>
     */
    @Getter
    private final BlockingQueue<MidiEvent> eventQueue = new LinkedBlockingQueue<>();

    /**
     * Indicates whether the input controller is currently active.
     */
    @Getter @Setter
    private boolean running = false;

    /**
     * Creates and initializes a MIDI input device.
     *
     * <p>The specified MIDI device is opened and, when running in {@link MainControlCore.RunMode#FULL} mode, this class is registered
     * as its message receiver.</p>
     *
     * @param deviceIndex index of the MIDI device reported by the Java MIDI subsystem
     * @param mode application runtime mode
     *
     * @throws Exception if the device cannot be opened or initialized
     */
    public MidiInputController(int deviceIndex, MainControlCore.RunMode mode) throws Exception {

        this.deviceIndex = deviceIndex;

        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        this.device = MidiSystem.getMidiDevice(devices[deviceIndex]);
        device.open();
        Transmitter transmitter = device.getTransmitter();

        if (mode == MainControlCore.RunMode.FULL) {
            transmitter.setReceiver(this);
        }

    }

    /**
     * Receives a MIDI message from the Java MIDI subsystem.
     *
     * <p>Supported message types are converted into internal {@link MidiEvent} objects and forwarded to the event queue.</p>     *
     * <ul>
     *     <li>{@code CONTROL_CHANGE} → {@link MidiEvent.Type#CC}</li>
     *     <li>{@code NOTE_ON} / {@code NOTE_OFF} → {@link MidiEvent.Type#NOTE}</li>
     * </ul>
     *
     * @param message received MIDI message
     * @param timeStamp message timestamp supplied by the MIDI subsystem
     */
    @Override
    public void send(MidiMessage message, long timeStamp) {

        if (!(message instanceof ShortMessage sm)) {
            return;
        }

        MidiEvent event = null;

        int channel = sm.getChannel() + 1;
        int command =sm.getCommand();
        int number = sm.getData1();
        int value = sm.getData2();

        if (command == ShortMessage.CONTROL_CHANGE) {
            event = new MidiEvent(
                    MidiEvent.Type.CC,
                    channel,
                    number,
                    value,
                    Integer.toString(this.deviceIndex)
            );
        }

        else if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF) {

            event = new MidiEvent(
                        MidiEvent.Type.NOTE,
                        channel,
                        number,
                        value,
                        Integer.toString(this.deviceIndex)
            );
        }

        if (event != null) {
            eventQueue.offer(event);
        }
    }

    /**
     * Closes the underlying MIDI device and releases all associated system resources.
     *
     * <p>Calling this method multiple times is safe.</p>
     */
    @Override
    public void close() {

        if (device != null && device.isOpen()) {
            device.close();
        }
    }
}