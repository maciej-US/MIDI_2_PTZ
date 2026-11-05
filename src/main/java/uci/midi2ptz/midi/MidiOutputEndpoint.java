package uci.midi2ptz.midi;

public interface MidiOutputEndpoint extends AutoCloseable {

    void start() throws Exception;

    void send(MidiEvent event);

    void setOutputValue(int cc, int value);

    void enableBlink(int cc, int onValue);

    void disableBlink(int cc);

    boolean supportsHardwareBlinking();
}
