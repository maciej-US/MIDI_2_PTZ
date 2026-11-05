package uci.midi2ptz.midi;

public class UsbMidiOutputEndpoint
        extends AbstractMidiOutputEndpoint {

    private final UsbMidiTransport transport;

    public UsbMidiOutputEndpoint(UsbMidiTransport transport, boolean hardwareBlinking) {

        super(hardwareBlinking);
        this.transport = transport;
    }

    @Override
    public void start() {
        transport.start();
    }

    @Override
    protected void sendCC(int cc, int value) {

        transport.sendPacket(
                new byte[]{
                        (byte) 0x0B,
                        (byte) 0xB0,
                        (byte) cc,
                        (byte) value
                }
        );
    }

    @Override
    protected void sendNote(
            int channel,
            int note,
            int value) {

        int status =
                value > 0
                        ? 0x90 + channel
                        : 0x80 + channel;

        transport.sendPacket(
                new byte[]{
                        (byte) 0x09,
                        (byte) status,
                        (byte) note,
                        (byte) value
                }
        );
    }

    @Override
    public void close() {
        // transport zamyka Factory
    }
}
