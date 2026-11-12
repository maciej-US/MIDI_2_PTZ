package uci.midi2ptz.midi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static uci.midi2ptz.core.MainControlCore.LOGGER;

/**
 * Base implementation of a MIDI output endpoint.
 *
 * <p>The class provides common functionality shared by all MIDI output transports, including software-emulated LED
 * blinking for devices that do not support hardware blinking.</p>
 *
 * <p>Concrete implementations are responsible for delivering  MIDI messages to the underlying transport and must implement
 * {@link #sendCC(int, int)} and  {@link #sendNote(int, int, int)}.</p>
 */

public abstract class AbstractMidiOutputEndpoint implements  MidiOutputEndpoint {

    /** Indicates whether the target controller supports native LED blinking.*/
    private final boolean hardwareBlinking;

    /** Background worker responsible for software blinking emulation.*/
    private Thread blinkThread;

    private final Map<Integer, Integer>
            blinkingOutputs =
            new ConcurrentHashMap<>();

    /** Current phase of the software blink cycle.*/
    private boolean blinkState;

    /**
     * Creates a MIDI output endpoint.
     * @param hardwareBlinking true when blinking is performed natively by the controller
     */
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

    /** Stops the software blinking engine and clears all registered blinking outputs.*/
    protected void stopBlinkEngine() {

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

    /** Dispatches a generic MIDI event to the appropriate low-level transport method.     *
     * @param event event to transmit */
    @Override
    public void send(MidiEvent event) {

        if (event == null) {
            return;
        }

        switch (event.type()) {
            case CC ->
                    sendCC(event.number(), event.value());

            case NOTE ->
                    sendNote(event.channel(), event.number(), event.value());
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


    /**Returns whether the underlying controller supports native LED blinking.
     * @return true if blinking is implemented by hardware*/
    @Override
    public boolean supportsHardwareBlinking() {
        return hardwareBlinking;
    }

    /**Sends a Control Change message.
     *
     * @param cc controller number
     * @param value controller value*/
    protected abstract void sendCC(int cc, int value);

    /**Sends a Note message.
     *
     * @param channel MIDI channel
     * @param note note number
     * @param value velocity/value*/
    protected abstract void sendNote(int channel, int note, int value);

}
