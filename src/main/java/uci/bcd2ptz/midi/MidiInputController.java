package uci.bcd2ptz.midi;

import javax.sound.midi.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MidiInputController
        implements Receiver, Runnable {

    private final int deviceIndex;

    private MidiDevice device;

    private final BlockingQueue<MidiEvent> eventQueue =
            new LinkedBlockingQueue<>();

    public MidiInputController(int deviceIndex) {

        this.deviceIndex = deviceIndex;
    }


    @Override
    public void run() {

        try {

            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();

            device = MidiSystem.getMidiDevice(devices[deviceIndex]);
            device.open();
            Transmitter transmitter = device.getTransmitter();
            transmitter.setReceiver(this);

            //DEBUG:
            //System.out.println("MIDI IN ready: " + devices[deviceIndex].getName());

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {

        if (!(message instanceof ShortMessage)) {
            return;
        }

        ShortMessage sm = (ShortMessage) message;

        //DEBUG:
        //System.out.println("MIDI InputController received: " + message);

        MidiEvent event = null;

        int channel = sm.getChannel() + 1;
        int command =sm.getCommand();
        int number = sm.getData1();
        int value = sm.getData2();

        if (command == ShortMessage.CONTROL_CHANGE) {
            event =
                    new MidiEvent(
                            MidiEvent.Type.CC,
                            channel,
                            number,
                            value,
                            Integer.toString(this.deviceIndex)
                    );
        }

        else if (
                command == ShortMessage.NOTE_ON
                        || command
                        == ShortMessage.NOTE_OFF) {

            event =
                    new MidiEvent(
                            MidiEvent.Type.NOTE,
                            channel,
                            number,
                            value,
                            Integer.toString(this.deviceIndex)
                    );
        }

        if (event != null) {

            eventQueue.offer(event);

            //DEBUG:
            /*System.out.println(
                    "QUEUE PUT: "
                            + event
            );*/
        }
    }

    @Override
    public void close() {

        if (device != null
                && device.isOpen()) {

            device.close();
        }
    }

    public BlockingQueue<MidiEvent>  getEventQueue() {

        return eventQueue;
    }
}