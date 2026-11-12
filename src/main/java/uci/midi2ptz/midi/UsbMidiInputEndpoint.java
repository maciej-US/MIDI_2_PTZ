package uci.midi2ptz.midi;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import static uci.midi2ptz.core.MainControlCore.LOGGER;

/** USB MIDI input endpoint implementation.
 *
 * <p>Receives raw USB MIDI packets from a shared {@link UsbMidiTransport}, converts them into normalized {@link MidiEvent}
 * instances and publishes them through an internal event queue.</p>
 *
 * <p>The endpoint supports Control Change and Note On/Off messages.</p>
 */
public class UsbMidiInputEndpoint implements MidiInputEndpoint {

    /** Shared USB MIDI transport.*/
    private final UsbMidiTransport transport;

    /** Queue used to deliver translated MIDI events.*/
    private final BlockingQueue<MidiEvent> eventQueue = new LinkedBlockingQueue<>();

    /** USB packet receiver worker thread.*/
    private Thread receiverThread;

    /** Indicates whether packet reception is currently active. */
    private volatile boolean running;

    /** Creates a USB MIDI input endpoint.
     *
     * @param transport shared USB MIDI transport */

    public UsbMidiInputEndpoint( UsbMidiTransport transport) {

        this.transport = transport;
    }

    /**Starts USB MIDI packet reception.
     *
     * <p>A background worker continuously reads packets from the transport and translates them into {@link MidiEvent} instances.</p>*/
    @Override
    public void start() {

        transport.start();
        running = true;
        receiverThread = new Thread(() -> {
            while (running) {
                try {
                    byte[] packet = transport.readPacket(500);

                    if (packet == null) {
                        continue;
                    }

                    MidiEvent event = MidiEventParser.fromUsbPacket(packet, String.valueOf(transport.getInterfaceId()));

                    if (event != null) {
                        eventQueue.offer(event);
                    }

                } catch (Exception e) {
                    LOGGER.warning(e.getMessage());
                }
            }

        }, "USB-MIDI-IN");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    /** Returns the queue containi*g translated MIDI events receiv*d from the USB device.*/
    @Override
    public BlockingQueue<MidiEvent> getEventQueue() {

        return eventQueue;
    }

    /** Stops packet reception and terminates the receiver thread.*/
    @Override
    public void close() {

        running = false;

        if (receiverThread != null) {
            receiverThread.interrupt();
            receiverThread = null;
        }
    }
}