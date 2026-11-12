package uci.midi2ptz.midi;

import uci.midi2ptz.config.AppConfig;

/**
 * Factory responsible for creating MIDI transport endpoints.
 *
 * <p>The factory provides transport-specific implementations of {@link MidiInputEndpoint} and {@link MidiOutputEndpoint}
 * based on the currently configured MIDI transport mode.</p>
 *
 * <p>Supported transport types:</p>
 * <ul>
 *     <li>{@link AppConfig.MidiTransport#GENERAL_MIDI}</li>
 *     <li>{@link AppConfig.MidiTransport#USB_DIRECT}</li>
 * </ul>
 *
 * <p>For USB_DIRECT mode a shared {@link UsbMidiTransport} instance is reused by all endpoints.</p>
 */

public final class MidiEndpointFactory {

    /** Shared USB transport instance used by USB_DIRECT input and output endpoints.
     *  USB transport must be shared between input and output endpoints because both directions operate on the same device.*/
    private static UsbMidiTransport usbTransport;

    private MidiEndpointFactory() {
    }

    /**
     * Returns the shared USB transport instance.
     *
     * <p>The transport is lazily created on first use and reused by both input and output endpoints.</p>     *
     * @param config MIDI transport configuration
     * @return shared USB transport
     */
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

    /**
     * Creates a MIDI input endpoint matching the configured transport mode.
     *
     * @param appConfig application configuration
     * @return initialized MIDI input endpoint
     * @throws IllegalStateException when endpoint creation fails
     */
    public static MidiInputEndpoint createInputEndpoint(AppConfig appConfig) throws IllegalStateException{

        AppConfig.MidiConfig config = appConfig.getSystem().getMidi();

        return switch (config.getMidiTransport()) {
            case GENERAL_MIDI -> new JavaMidiInputEndpoint(config.getJavaMidi().getInputDeviceIndex());
            case USB_DIRECT -> new UsbMidiInputEndpoint(getUsbTransport(config));
            default -> throw new IllegalStateException("Unsupported MIDI input transport mode: " + appConfig.getSystem().getMidi().getMidiTransport());
        };
    }

    /**
     * Creates a MIDI output endpoint matching the configured transport mode.
     *
     * @param appConfig application configuration
     * @return initialized MIDI output endpoint
     * @throws IllegalStateException when endpoint creation fails
     */
    public static MidiOutputEndpoint createOutputEndpoint(AppConfig appConfig)throws IllegalStateException {

        AppConfig.MidiConfig config = appConfig.getSystem().getMidi();
        boolean hardwareBlinking = config.getController().isHardwareBlinking();

        return switch (config.getMidiTransport()) {
            case GENERAL_MIDI -> new JavaMidiOutputEndpoint(config.getJavaMidi().getOutputDeviceIndex(), hardwareBlinking);
            case USB_DIRECT -> new UsbMidiOutputEndpoint(getUsbTransport(config), hardwareBlinking);
            default -> throw new IllegalStateException("Unsupported MIDI output transport mode: " + appConfig.getSystem().getMidi().getMidiTransport());
        };
    }

    /**
     * Releases the shared USB transport instance.     *
     * <p>The method has no effect when USB_DIRECT transport was not initialized.</p>
     */
    public static synchronized void shutdown() {

        if (usbTransport != null) {
            usbTransport.close();
            usbTransport = null;
        }
    }

}
