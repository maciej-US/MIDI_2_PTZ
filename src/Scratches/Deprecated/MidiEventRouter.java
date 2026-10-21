package uci.midi2ptz.midi;

import javax.sound.midi.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Deprecated
public class MidiEventRouter implements Receiver, Runnable {

    public enum OutputMode {
        RAW,
        PARSED
    }

    private static MidiEventRouter instance;

    // ===== Kolejki =====
    private final BlockingQueue<ShortMessage> rawQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> parsedQueue = new LinkedBlockingQueue<>();

    private OutputMode mode = OutputMode.PARSED;

    // ===== MIDI I/O =====
    private Transmitter transmitter;
    private Receiver receiver;

    // ===== KONSTRUKTORY =====

    // ✅ ONE DEVICE (IN=OUT)
    private MidiEventRouter(int deviceIndex) {
        init(deviceIndex, deviceIndex);
    }

    // ✅ SEPARATE IN / OUT
    private MidiEventRouter(int inputIndex, int outputIndex) {
        init(inputIndex, outputIndex);
    }

    private void init(int inputDeviceIndex, int outputDeviceIndex) {

        try {
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();

            // ===== INPUT (czytamy z urządzenia) =====
            MidiDevice inputDevice = MidiSystem.getMidiDevice(devices[inputDeviceIndex]);
            inputDevice.open();

            transmitter = inputDevice.getTransmitter();
            transmitter.setReceiver(this);

            System.out.println("MIDI INPUT (read): " + devices[inputDeviceIndex].getName());

            // ===== OUTPUT (wysyłamy do urządzenia) =====
            MidiDevice outputDevice = MidiSystem.getMidiDevice(devices[outputDeviceIndex]);
            outputDevice.open();

            receiver = outputDevice.getReceiver();

            System.out.println("MIDI OUTPUT (write): " + devices[outputDeviceIndex].getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //============= CONFIGURATION ================
    public static int findDeviceIndexByName(String name, boolean requireTransmitter) {

        try {
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();

            for (int i = 0; i < devices.length; i++) {

                MidiDevice d = MidiSystem.getMidiDevice(devices[i]);

                boolean valid = requireTransmitter
                        ? d.getMaxTransmitters() != 0
                        : d.getMaxReceivers() != 0;

                if (valid && devices[i].getName().contains(name)) {
                    return i;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    // ===== SINGLETON =====

    public static synchronized MidiEventRouter getInstance(int deviceIndex) {
        if (instance == null) {
            instance = new MidiEventRouter(deviceIndex);
        }
        return instance;
    }

    public static synchronized MidiEventRouter getInstance(int inputIndex, int outputIndex) {
        if (instance == null) {
            instance = new MidiEventRouter(inputIndex, outputIndex);
        }
        return instance;
    }

    // ===== TRYB =====

    public void setOutputMode(OutputMode mode) {
        this.mode = mode;
    }

    // ===== KOLEJKI =====

    public BlockingQueue<ShortMessage> getRawQueue() {
        return rawQueue;
    }

    public BlockingQueue<String> getParsedQueue() {
        return parsedQueue;
    }

    // ===== RECEIVER MIDI IN =====

    @Override
    public void send(MidiMessage message, long timeStamp) {

        if (!(message instanceof ShortMessage)) return;

        ShortMessage sm = (ShortMessage) message;

        //System.out.println("RAW MIDI RECEIVED: " + sm); // 🔥 DEBUG

        try {

            rawQueue.put(sm);

            if (mode == OutputMode.PARSED) {
                String parsed = parseMessage(sm);
                if (parsed != null) {
                    parsedQueue.put(parsed);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ===== PARSER =====

    private String parseMessage(ShortMessage sm) {

        int command = sm.getCommand();
        int channel = sm.getChannel() + 1;
        int d1 = sm.getData1();
        int d2 = sm.getData2();

        switch (command) {

            case ShortMessage.CONTROL_CHANGE:
                return "CC_" + channel + "_" + d1 + "_" + d2;

            case ShortMessage.NOTE_ON:
                return "NT_" + channel + "_" + d1 + "_" + d2;

            case ShortMessage.NOTE_OFF:
                return "NT_" + channel + "_" + d1 + "_0";

            default:
                return null;
        }
    }

    // ===== MIDI OUT =====

    public void sendCC(int channel, int cc, int value) {

        try {
            if (receiver == null) return;

            ShortMessage msg = new ShortMessage();
            msg.setMessage(ShortMessage.CONTROL_CHANGE, channel - 1, cc, value);

            receiver.send(msg, -1);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendNote(int channel, int note, int velocity) {

        try {
            if (receiver == null) return;

            ShortMessage msg = new ShortMessage();
            msg.setMessage(ShortMessage.NOTE_ON, channel - 1, note, velocity);

            receiver.send(msg, -1);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendRaw(ShortMessage msg) {

        try {
            if (receiver == null) return;
            receiver.send(msg, -1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== RUN =====

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // ===== CLOSE =====

    @Override
    public void close() {

        try {
            if (transmitter != null) transmitter.close();
            if (receiver != null) receiver.close();
        } catch (Exception ignored) {}
    }
}
