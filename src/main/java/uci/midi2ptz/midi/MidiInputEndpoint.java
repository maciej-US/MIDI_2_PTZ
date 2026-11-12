package uci.midi2ptz.midi;

import java.util.concurrent.BlockingQueue;

/** Common interface for MIDI input transports. *
 * <p>Implementations receive data from a physical MIDI source and expose translated {@link MidiEvent} instances through an event queue.</p> */
public interface MidiInputEndpoint extends AutoCloseable {

    /** Opens and initializes the input endpoint.
     * @throws Exception if initialization fails
     */
    void start() throws Exception;

    /** Closes the endpoint and releases all associated resources.*/
    @Override
    void close();

    BlockingQueue<MidiEvent> getEventQueue();
}
