package uci.midi2ptz.midi;

import java.util.concurrent.BlockingQueue;

public interface MidiInputEndpoint extends AutoCloseable {

    void start() throws Exception;

    void close();

    BlockingQueue<MidiEvent> getEventQueue();
}
