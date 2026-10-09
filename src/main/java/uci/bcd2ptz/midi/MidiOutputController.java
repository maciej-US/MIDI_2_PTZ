package uci.bcd2ptz.midi;

import uci.bcd2ptz.core.ControlCore;

import javax.sound.midi.*;
import javax.swing.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MidiOutputController  {


    private Receiver receiver;
    private MidiDevice device;


    private final boolean hardwareBlinking;
    private Thread blinkThread;
    private final Map<Integer, Integer> blinkingOutputs = new ConcurrentHashMap<>();
    private Timer blinkTimer;
    private boolean blinkState = false;

    /** Konstruktor
     *
     * @param deviceIndex
     * @param hardwareBlinking
     */
    public MidiOutputController(int deviceIndex, boolean hardwareBlinking, ControlCore.RunMode mode) throws Exception {

        this.hardwareBlinking = hardwareBlinking;

        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        this.device = MidiSystem.getMidiDevice(devices[deviceIndex]);
        this.device.open();
        receiver = device.getReceiver();

        System.out.println("MIDI OUT ready: " + devices[deviceIndex].getName());

        if (!hardwareBlinking && !mode.equals(ControlCore.RunMode.FULL)) {
            initBlinkEngine();
        }
    }

    /**
     * Główna metoda wysyłki.
     */
    public void send(MidiEvent event) {

        if (event == null) {
            return;
        }

        switch (event.getType()) {

            case CC:
                sendCC(
                        event.getNumber(),
                        event.getValue()
                );
                break;

            case NOTE:
                sendNote(
                        event.getChannel(),
                        event.getNumber(),
                        event.getValue()
                );
                break;
        }
    }

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
            System.err.println("MIDI CC send error");
            e.printStackTrace();
        }
    }

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
            System.err.println("MIDI NOTE send error");
            e.printStackTrace();
        }
    }

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
                    e.printStackTrace();
                }
            }

        }, "MidiBlinkEngine");

        blinkThread.setDaemon(true);
        blinkThread.start();
    }
    /**
     * Włączenie programowego migania.
     */
    public void enableBlink( int cc, int onValue) {

        if (hardwareBlinking) {
            return;
        }
        blinkingOutputs.put(cc, onValue);
    }

    /**
     * Wyłączenie programowego migania.
     */
    public void disableBlink(int cc) {
        blinkingOutputs.remove(cc);
    }

    /**
     * Natychmiastowe ustawienie LED.
     */
    public void setOutputValue(int cc, int value) {

        disableBlink(cc);
        sendCC(cc, value);
    }

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