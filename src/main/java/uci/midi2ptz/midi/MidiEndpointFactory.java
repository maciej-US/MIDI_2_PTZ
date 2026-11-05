package uci.midi2ptz.midi;

import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.core.MainControlCore;

public final class MidiEndpointFactory {

    private static UsbMidiTransport usbTransport;

    private MidiEndpointFactory() {
    }

    private static synchronized UsbMidiTransport getUsbTransport (AppConfig.MidiConfig config){

        if (usbTransport == null) {

            usbTransport =
                    new UsbMidiTransport(
                            (short) config.getUsbDirect().getVid(),
                            (short) config.getUsbDirect().getPid(),
                            config.getUsbDirect().getInterfaceNumber(),
                            (byte) config.getUsbDirect().getInputEndpoint(),
                            (byte) config.getUsbDirect().getOutputEndpoint()
                    );
        }
        return usbTransport;
    }

    public static MidiInputEndpoint createInputEndpoint(AppConfig appConfig) throws Exception {

        AppConfig.MidiConfig config = appConfig.getSystem().getMidi();

        switch (config.getMidiTransport()) {

            case GENERAL_MIDI:
                return new JavaMidiInputEndpoint(config.getJavaMidi().getInputDeviceIndex());

            case USB_DIRECT:

                return new UsbMidiInputEndpoint(getUsbTransport(config));

            default:
                throw new IllegalStateException("Unsupported MIDI input transport mode: " + appConfig.getSystem().getMidi().getMidiTransport());
        }
    }

    public static MidiOutputEndpoint createOutputEndpoint(AppConfig appConfig) throws Exception {

        AppConfig.MidiConfig config = appConfig.getSystem().getMidi();
        boolean hardwareBlinking = config.getController().isHardwareBlinking();

        switch (config.getMidiTransport()) {

            case GENERAL_MIDI:
                return new JavaMidiOutputEndpoint(config.getJavaMidi().getOutputDeviceIndex(), hardwareBlinking);

            case USB_DIRECT:

                return new UsbMidiOutputEndpoint(getUsbTransport(config), hardwareBlinking);

            default:
                throw new IllegalStateException("Unsupported MIDI output transport mode: " + appConfig.getSystem().getMidi().getMidiTransport());
        }
    }

    public static synchronized void shutdown() {

        if (usbTransport != null) {

            usbTransport.close();

            usbTransport = null;
        }
    }

}
