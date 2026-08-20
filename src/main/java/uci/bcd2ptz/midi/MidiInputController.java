package uci.bcd2ptz.midi;

import javax.sound.midi.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MidiInputController implements Receiver, Runnable {

    private static MidiInputController instance;

    private final int deviceIndex;
    private MidiDevice device;
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    // PRIVATE CONSTRUCTOR
    private MidiInputController(int deviceIndex) {
        this.deviceIndex = deviceIndex;
    }

    // SINGLETON
    public static synchronized MidiInputController getInstance(int deviceIndex) {
        if (instance == null) {
            instance = new MidiInputController(deviceIndex);
        }
        return instance;
    }

    // THREAD START
    @Override
    public void run() {
        try {
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();

            device = MidiSystem.getMidiDevice(devices[deviceIndex]);
            device.open();

            Transmitter transmitter = device.getTransmitter();
            transmitter.setReceiver(this);

            System.out.println("MidiListener uruchomiony: " + devices[deviceIndex].getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // MIDI RECEIVE
    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (message instanceof ShortMessage) {
            ShortMessage sm = (ShortMessage) message;

            int command = sm.getCommand();
            int channel = sm.getChannel() + 1;
            int data1 = sm.getData1();
            int data2 = sm.getData2();

            String result = null;

            if (command == ShortMessage.CONTROL_CHANGE) {
                result = "CC_" + channel + "_" + data1 + "_" + data2;
            } else if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF) {
                result = "NOTE_" + channel + "_" + data1 + "_" + data2;
            }


            if (result != null) {
                messageQueue.offer(result);
            }
        }
    }

    @Override
    public void close() {
        if (device != null && device.isOpen()) {
            device.close();
        }
    }

    // PUBLIC ACCESS TO QUEUE
    public BlockingQueue<String> getMessageQueue() {
        return messageQueue;
    }
}
