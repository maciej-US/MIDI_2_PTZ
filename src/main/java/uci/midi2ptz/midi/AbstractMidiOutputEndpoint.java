package uci.midi2ptz.midi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static uci.midi2ptz.core.MainControlCore.LOGGER;

public abstract class AbstractMidiOutputEndpoint implements  MidiOutputEndpoint {

    private final boolean hardwareBlinking;

    private Thread blinkThread;

    private final Map<Integer, Integer>
            blinkingOutputs =
            new ConcurrentHashMap<>();

    private boolean blinkState;

    protected AbstractMidiOutputEndpoint(boolean hardwareBlinking) {

        this.hardwareBlinking = hardwareBlinking;

        if (!hardwareBlinking) {
            startBlinkEngine();
        }
    }


    /**
     * Initializes the software blinking engine.
     *
     * <p>A dedicated background thread periodically toggles registered outputs to emulate hardware blinking.</p>
     *
     * <p>The blink engine is only used when the target MIDI controller does not provide native blinking support.</p>
     */
    private void startBlinkEngine() {

        if (blinkThread != null) {
            return;
        }

        blinkThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    blinkState = !blinkState;

                    for (Map.Entry<Integer, Integer> entry : blinkingOutputs.entrySet()) {
                        sendCC(entry.getKey(), blinkState ? entry.getValue() : 0);
                    }

                    Thread.sleep(150);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;

                } catch (Exception e) {
                    LOGGER.warning("Internal blinking engine error: " + e.getMessage());
                }
            }

        }, "MidiBlinkEngine");

        blinkThread.setDaemon(true);
        blinkThread.start();
    }

     void stopBlinkEngine() {

        if (blinkThread != null) {
            blinkThread.interrupt();
            blinkThread = null;
        }
        blinkingOutputs.clear();
    }

    /**
     * Enables software-controlled blinking for a MIDI output.
     *
     * <p>If hardware blinking is supported by the controller,  this method has no effect.</p>
     *
     * @param cc controller number
     * @param onValue value sent during the active blink phase
     */
    public void enableBlink( int cc, int onValue) {

        if (hardwareBlinking) {
            return;
        }
        blinkingOutputs.put(cc, onValue);
    }
    /**
     * Disables blinking for the specified output.
     *
     * @param cc controller number
     */
    public void disableBlink(int cc) {
        blinkingOutputs.remove(cc);
    }

    @Override
    public void send(
            MidiEvent event) {

        if (event == null) {
            return;
        }

        switch (event.type()) {

            case CC ->
                    sendCC(
                            event.number(),
                            event.value()
                    );

            case NOTE ->
                    sendNote(
                            event.channel(),
                            event.number(),
                            event.value()
                    );
        }
    }

    /**
     * Sets an output value immediately.
     *
     * <p>Any active software blinking for the specified output is automatically stopped before the value is sent.</p>
     *
     * @param cc controller number
     * @param value output value
     */

    public void setOutputValue(int cc, int value) {

        disableBlink(cc);
        sendCC(cc, value);
    }


    @Override
    public boolean supportsHardwareBlinking() {
        return hardwareBlinking;
    }

    protected abstract void sendCC(
            int cc,
            int value
    );

    protected abstract void sendNote(
            int channel,
            int note,
            int value
    );

}
