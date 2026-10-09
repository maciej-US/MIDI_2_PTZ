package uci.bcd2ptz.midi;

import lombok.Getter;
import lombok.Setter;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlCore.RunMode.*;

import javax.sound.midi.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MidiInputController implements Receiver {

    private final int deviceIndex;
    private MidiDevice device;
    @Getter
    private final BlockingQueue<MidiEvent> eventQueue = new LinkedBlockingQueue<>();
    @Getter @Setter
    private boolean running = false;

    public MidiInputController(int deviceIndex, ControlCore.RunMode mode) throws Exception {

        this.deviceIndex = deviceIndex;

        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
        this.device = MidiSystem.getMidiDevice(devices[deviceIndex]);
        device.open();
        Transmitter transmitter = device.getTransmitter();

        if (mode == ControlCore.RunMode.FULL) {
            transmitter.setReceiver(this);
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

}