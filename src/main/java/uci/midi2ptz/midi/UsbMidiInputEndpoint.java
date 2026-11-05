package uci.midi2ptz.midi;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UsbMidiInputEndpoint implements MidiInputEndpoint {

    private final UsbMidiTransport transport;

    private final BlockingQueue<MidiEvent> eventQueue = new LinkedBlockingQueue<>();

    private Thread receiverThread;

    private volatile boolean running;

    public UsbMidiInputEndpoint( UsbMidiTransport transport) {

        this.transport = transport;
    }

    @Override
    public void start() {

        transport.start();
        running = true;
        receiverThread = new Thread(() -> {
            while (running) {
                try {
                    byte[] packet = transport.readPacket(500);

                    if (packet == null) {
                        continue;
                    }

                    MidiEvent event = parsePacket(packet);

                    if (event != null) {
                        eventQueue.offer(event);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }, "USB-MIDI-IN");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    @Override
    public BlockingQueue<MidiEvent> getEventQueue() {

        return eventQueue;
    }

    @Override
    public void close() {

        running = false;

        if (receiverThread != null) {
            receiverThread.interrupt();
        }
    }

    private MidiEvent parsePacket(
            byte[] packet) {

        if (packet.length < 4) {
            return null;
        }

        int status = packet[1] & 0xFF;
        int data1 = packet[2] & 0xFF;
        int data2 = packet[3] & 0xFF;
        int command = status & 0xF0;
        int channel = status & 0x0F;

        String deviceId = String.valueOf(transport.getInterfaceId());

        switch (command) {

            case 0xB0:

                return new MidiEvent(
                        MidiEvent.Type.CC,
                        channel,
                        data1,
                        data2,
                        deviceId
                );

            case 0x90:

            case 0x80:

                return new MidiEvent(
                        MidiEvent.Type.NOTE,
                        channel,
                        data1,
                        data2,
                        deviceId
                );

            default:

                return null;
        }
    }
}