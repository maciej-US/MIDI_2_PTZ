package uci.bcd2ptz;

import javax.sound.midi.*;

public class MidiOutputController {

    private static MidiOutputController instance;

    private Receiver receiver;

    private MidiOutputController(int deviceIndex) {
        try {
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
            MidiDevice device = MidiSystem.getMidiDevice(devices[deviceIndex]);

            device.open();
            receiver = device.getReceiver();

            System.out.println("MIDI OUT gotowe: " + devices[deviceIndex].getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ✅ singleton - bez parametru (na stałe OUT = 4)
    public static synchronized MidiOutputController getInstance(int deviceIndex) {
        if (instance == null) {
            instance = new MidiOutputController(deviceIndex);
        }
        return instance;
    }

    // ✅ LED ON (>=64)
    public void ledOn(int cc) {
        sendCC(cc, 127); // można też 64
    }

    // ✅ LED OFF (<64)
    public void ledOff(int cc) {
        sendCC(cc, 0);
    }

    private void sendCC(int cc, int value) {
        try {
            ShortMessage msg = new ShortMessage();

            // ✅ CONTROL CHANGE (kanał 1 → 0)
            msg.setMessage(ShortMessage.CONTROL_CHANGE, 0, cc, value);

            receiver.send(msg, -1);

            System.out.println("MIDI OUT → CC_" + cc + " = " + value);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}