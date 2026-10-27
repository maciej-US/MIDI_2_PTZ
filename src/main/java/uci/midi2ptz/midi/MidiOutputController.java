package uci.midi2ptz.midi;

import uci.midi2ptz.core.MainControlCore;

import javax.sound.midi.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static uci.midi2ptz.core.MainControlCore.LOGGER;

/**
 * MIDI input device adapter.
 *
 * <p>This class provides the connection between a physical MIDI input device and the application's internal event model.</p>
 *
 * <p>Incoming MIDI messages received from the Java MIDI subsystem  are converted into normalized {@link MidiEvent} instances and
 * placed into an internal event queue for further processing by the MIDI controller layer.</p>
 *
 * <p>The implementation supports Control Change (CC) and Note On/Off messages.</p>
 */
public class MidiOutputController  {

    private Receiver receiver;
    private final MidiDevice device;

    /**
     * Indicates whether the MIDI controller supports native hardware blinking.
     */
    private final boolean hardwareBlinking;

    /**
     * Background worker responsible for generating software blinking effects.
     */
    private Thread blinkThread;

    /**
     * Outputs currently managed by the software blinking engine.     *
     * <p>The key represents the controller number and the value represents the active LED intensity or state.</p>
     */
    private final Map<Integer, Integer> blinkingOutputs = new ConcurrentHashMap<>();
    private boolean blinkState = false;

    /**
     * Creates and initializes a MIDI output controller.
     *
     * <p>The target MIDI output device is opened and prepared for feedback transmission.</p>
     *
     * @param deviceIndex MIDI output device index
     * @param hardwareBlinking indicates whether the controller supports native blinking effects
     * @param mode application runtime mode
     *
     * @throws Exception if the MIDI device cannot be opened
     */
    public MidiOutputController(int deviceIndex, boolean hardwareBlinking, MainControlCore.RunMode mode) throws Exception {

        this.hardwareBlinking = hardwareBlinking;

        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        this.device = MidiSystem.getMidiDevice(devices[deviceIndex]);
        this.device.open();
        receiver = device.getReceiver();

        System.out.println("MIDI OUT ready: " + devices[deviceIndex].getName());

        if (!hardwareBlinking && mode.equals(MainControlCore.RunMode.FULL)) {
            initBlinkEngine();
        }
    }

    /**
     * Sends a MIDI event to the output device.
     *
     * <p>The event is translated into the appropriate MIDI message and transmitted to the controller hardware.</p>
     *
     * @param event event to send
     */
    public void send(MidiEvent event) {

        if (event == null) {
            return;
        }

        switch (event.type()) {

            case CC:
                sendCC(
                        event.number(),
                        event.value()
                );
                break;

            case NOTE:
                sendNote(
                        event.channel(),
                        event.number(),
                        event.value()
                );
                break;
        }
    }

    /**
     * Sends a MIDI Control Change message.
     *
     * @param cc controller number
     * @param value controller value
     */
    private void sendCC( int cc, int value) {

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
     * @param note note number
     * @param value note velocity or state value
     */
    private void sendNote(int channel, int note, int value) {

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
     * Initializes the software blinking engine.
     *
     * <p>A dedicated background thread periodically toggles registered outputs to emulate hardware blinking.</p>
     *
     * <p>The blink engine is only used when the target MIDI controller does not provide native blinking support.</p>
     */
    private void initBlinkEngine() {

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

    /**
     * Shuts down the MIDI output controller and releases all hardware resources.
     *
     * <p>The blink engine is stopped, active blink registrations are removed and the MIDI device is closed.</p>
     */
    public void close() {

        if (blinkThread != null) {
            blinkThread.interrupt();
            blinkThread = null;
        }

        blinkingOutputs.clear();

        if (receiver != null) {
            receiver.close();
            receiver = null;
        }

        if (device != null && device.isOpen()) {

            device.close();
            System.out.println("MIDI OUT stopped.");
        }
    }

}