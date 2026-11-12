package uci.midi2ptz.midi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.usb4java.*;
import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.MainControlCore;
import uci.midi2ptz.core.ControlEventBus;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;

import static javax.sound.midi.MidiSystem.getMidiDeviceInfo;
import static uci.midi2ptz.Midi2PTZ.isMidiDeviceAvailable;
import static uci.midi2ptz.core.MainControlCore.LOGGER;

/**
 * Central MIDI subsystem manager.
 *
 * <p>This class is responsible for creating, validating, configuring and managing the lifecycle of the active
 * {@link MidiController} instance. The manager supports both the Java MIDI transport and low-levelUSB MIDI transport implementations.</p><
 *
 * <p>In addition to controller lifecycle management, it maintains the routing layer between physical MIDI panels and logical
 * application control panels.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *     <li>MIDI controller initialization</li>
 *     <li>MIDI configuration validation</li>
 *     <li>Device availability verification</li>
 *     <li>Controller lifecycle management</li>
 *     <li>MIDI-to-core panel assignment mapping</li>
 *     <li>MIDI indicator synchronization</li>
 * </ul>
 *
 * <p>The manager maintains a one-to-one relationship between physical MIDI panels and logical application control panels.</p>
 * <h2>Panel Mapping Model</h2>
 *
 * <pre>
 * MIDI Panel A  ---> Core Panel C
 * MIDI Panel B  ---> Core Panel A
 *
 * midiToCoreBindings: MIDI -> CORE
 *
 * coreToMidiBindings: CORE -> MIDI
 * </pre>
 *
 * <p>Both mappings are maintained simultaneously to provide efficient lookups in either direction.</p>
 */
public class MidiControllerManager {



    //API ELEMTS
    private final AppConfigManager configManager;
    private final ControlEventBus eventBus;
    private final MainControlCore.RunMode runMode;

    //MIDI
    private MidiController controller;


    /**
     * Mapping between physical MIDI panels and logical application control panels.
     */
    private final Map<MainControlCore.ControlPanelId, MainControlCore.ControlPanelId> midiToCoreBindings = new EnumMap<>(MainControlCore.ControlPanelId.class);

    /**
     * Reverse lookup mapping between logical application panels and physical MIDI panels.
     */
    private final Map<MainControlCore.ControlPanelId, MainControlCore.ControlPanelId> coreToMidiBindings = new EnumMap<>(MainControlCore.ControlPanelId.class);

    /**
     * Creates a MIDI subsystem manager.
     *
     * @param configManager application configuration manager
     * @param eventBus control event bus
     * @param runMode application runtime mode
     */
    public MidiControllerManager(AppConfigManager configManager, ControlEventBus eventBus, MainControlCore.RunMode runMode) {

        this.configManager = configManager;
        this.eventBus = eventBus;
        this.runMode = runMode;
    }

    /**
     * Validates configuration and starts the MIDI subsystem.
     *
     * <p>If a controller is already running, it is shut down and recreated using the current configuration.</p>
     *
     * <p>The method verifies that:</p>     *
     * <ul>
     *     <li>MIDI configuration exists,</li>
     *     <li>required devices are configured,</li>
     *     <li>configured hardware is available.</li>
     * </ul>
     *
     * @return initialization result
     */
    public ConfigurationUpdateResults initialize() {

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        if (controller!= null && controller.isRunning()){
            stopController();
        }

        if (!isConfigured()) {
            results.getErrors().put(AppConfigManager.ConfigurationSection.MIDI, "MIDI not configured.");
            return results;
        }

        if (!isAvailable()) {
            results.getErrors().put(AppConfigManager.ConfigurationSection.MIDI, "MIDI device not available.");
            return results;
        }
        startController();

        return results;
    }

    /**
     * Verifies that all required MIDI configuration
     * values are present.
     *
     * @return {@code true} if the MIDI subsystem is configured
     */
    private boolean isConfigured() {

        AppConfig.MidiConfig midi = configManager
                        .getConfig()
                        .getSystem()
                        .getMidi();

        if (midi == null ||
                midi.getMidiTransport() == null ||
                midi.getController() == null ||
                midi.getController().getConfigFile() == null ||
                midi.getController().getConfigFile().isBlank()) {
            return false;
        }

            return switch (midi.getMidiTransport()) {
                case GENERAL_MIDI -> midi.getJavaMidi() != null
                        && midi.getJavaMidi().getInputDeviceIndex() >= 0
                        && midi.getJavaMidi().getOutputDeviceIndex() >= 0;
                case USB_DIRECT -> midi.getUsbDirect() != null
                        && midi.getUsbDirect().getVid() > 0
                        && midi.getUsbDirect().getPid() > 0
                        && midi.getUsbDirect().getInterfaceNumber() >= 0
                        && midi.getUsbDirect().getInputEndpoint() != 0
                        && midi.getUsbDirect().getOutputEndpoint() != 0;
            };
        }

    /**
     * Verifies that the configured MIDI device is currently
     * available in the operating system.
     *
     * @return {@code true} if the configured device is available
     */
    private boolean isAvailable() {

        AppConfig.MidiConfig midi =
                configManager
                        .getConfig()
                        .getSystem()
                        .getMidi();

        if (midi == null ||
                midi.getMidiTransport() == null) {
            return false;
        }

        return switch (midi.getMidiTransport()) {
            case GENERAL_MIDI -> {
                String deviceName = midi.getController().getDeviceName();
                yield deviceName != null && isMidiDeviceAvailable(deviceName);
            }
            case USB_DIRECT ->
                    isUsbDeviceAvailable((short) midi.getUsbDirect().getVid(), (short) midi.getUsbDirect().getPid());
        };
    }

    /**
     * Determines whether the MIDI controller is currently active.
     *
     * @return {@code true} if the controller is running
     */
    public boolean isMidiControllerRunning(){
        return controller != null && controller.isRunning();
    }

    /**
     * Returns the active MIDI controller instance.
     *
     * @return active controller or {@code null}
     */
    public MidiController getMidiController(){
        return this.controller;
    }

    /**
     * Creates and starts a MIDI controller using the current application configuration.
     *
     * <p>The method loads the external MIDI mapping file, initializes controller routing and establishes panel assignments.
     * Panel assignments are restored from the current application configuration before controller startup.</p>
     */
    private void startController() {

        try {

            AppConfig appConfig = configManager.getConfig();

            AppConfig.MidiConfig midi = appConfig.getSystem().getMidi();

            ObjectMapper mapper = new ObjectMapper();

            MidiConfig midiConfig = mapper.readValue(new File(midi.getController().getConfigFile()), MidiConfig.class);

            controller = new MidiController(
                            configManager.getConfig(),
                            midiConfig,
                            eventBus,
                            runMode,
                            this
                    );

           //=== MIDI BINGIGS TO CORE PANELS
            clearBindings();
            for (AppConfig.CameraConfig cameraConfig : appConfig.getSystem().getCameras()){
                String midiPanel = cameraConfig.getAssignedMidiPanel();
                String corePanel = cameraConfig.getAssignedGuiPanel();

                if (midiPanel == null || midiPanel.isBlank() || corePanel == null || corePanel.isBlank()){
                    continue;
                }
                assignMidiPanel(MainControlCore.ControlPanelId.valueOf(midiPanel),
                                MainControlCore.ControlPanelId.valueOf(corePanel));
            }

            controller.start();
            LOGGER.fine("[MIDI] Started: " + midiConfig.getControllerName());

        } catch (Exception e) {
            controller = null;
            LOGGER.warning("Unable to initialize controller: " + e.getMessage());
        }
    }

    /**
     * Stops and disposes of the currently active MIDI controller instance.
     */
    public void stopController() {

        if (controller != null) {
            controller.shutdown();
            controller = null;
            LOGGER.fine("[MIDI] Stopped.");
        }
    }

    /**
     * Refreshes camera-selection indicators on all active MIDI panels.
     *
     * <p>The displayed indicators reflect the current mapping between logical control panels and MIDI panels.</p>
     */
    public void updateMidiIndicators() {

        for (MainControlCore.ControlPanelId corePanel : MainControlCore.ControlPanelId.values()) {
            MainControlCore.ControlPanelId midiPanel = getMidiBinding(corePanel);

            if (midiPanel != null) {
                controller.updateCameraIndicators(midiPanel);
            }
        }
    }

    /**
     * Returns the logical application panel assigned to a MIDI panel.
     *
     * @param midiPanel physical MIDI panel @return associated application panel or {@code null}
     */
    public MainControlCore.ControlPanelId getCoreBinding(MainControlCore.ControlPanelId midiPanel) {

        return midiToCoreBindings.get(midiPanel);
    }

    /**
     * Returns the MIDI panel currently assigned to a logical
     * application panel.
     *
     * @param corePanel application control panel
     * @return assigned MIDI panel or {@code null}
     */
    public MainControlCore.ControlPanelId getMidiBinding(MainControlCore.ControlPanelId corePanel) {

        return coreToMidiBindings.get(corePanel);
    }

    /**
     * Creates or updates a MIDI-to-core panel assignment.
     *
     * <p>Existing mappings are automatically removed to preserve the one-to-one relationship between physical MIDI panels and logical control panels.</p>
     *
     * @param midiPanel MIDI panel
     * @param corePanel target application panel
     */
    public void assignMidiPanel(MainControlCore.ControlPanelId midiPanel, MainControlCore.ControlPanelId corePanel) {

        MainControlCore.ControlPanelId oldCore = midiToCoreBindings.get(midiPanel);

        if (oldCore != null) {
            coreToMidiBindings.remove(oldCore);
        }

        MainControlCore.ControlPanelId oldMidi = coreToMidiBindings.get(corePanel);

        if (oldMidi != null) {
            midiToCoreBindings.remove(oldMidi);
        }

        coreToMidiBindings.put(corePanel,midiPanel);
        if (midiPanel != null) {
            midiToCoreBindings.put(midiPanel, corePanel);
        }
    }

    /**
     * Removes all panel routing assignments.
     */
    public void clearBindings() {

        midiToCoreBindings.clear();
        coreToMidiBindings.clear();
    }

    /**
     * Validates a MIDI configuration without starting the application runtime.
     *
     * <p> A temporary controller instance is constructed to verify configuration consistency.</p>
     *
     * <p>The method is primarily used by configuration validation workflows.</p>
     *
     * @param config application configuration
     * @param mode validation runtime mode
     *
     * @throws Exception if validation fails
     */
    public static void validateConfiguration(AppConfig config, MainControlCore.RunMode mode) throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        MidiConfig midiConfig = mapper.readValue(
                        new File(config.getSystem().getMidi().getController().getConfigFile()),
                        MidiConfig.class
                );

        MidiController controller = new MidiController(config, midiConfig, null, mode, null);

        controller.shutdown();
    }

    private boolean isUsbDeviceAvailable(short vid, short pid) {

        Context context = new Context();

        try {
            int rc = LibUsb.init(context);
            if (rc != LibUsb.SUCCESS) {
                return false;
            }

            DeviceList list = new DeviceList();

            try {
                LibUsb.getDeviceList(context, list);

                for (Device device : list) {
                    DeviceDescriptor descriptor = new DeviceDescriptor();
                    if (LibUsb.getDeviceDescriptor(device, descriptor) != LibUsb.SUCCESS) {
                        continue;
                    }

                    if ((descriptor.idVendor() & 0xFFFF) == (vid & 0xFFFF) && (descriptor.idProduct() & 0xFFFF) == (pid & 0xFFFF)) {
                        return true;
                    }
                }

            } finally {
                LibUsb.freeDeviceList(list, true);
            }
        } finally {
            LibUsb.exit(context);
        }
        return false;
    }

    /**
     * Retrieves all MIDI devices*available in the current system.
     * <p>Both input and output capable devices are returned.</p>
     *
     * return list of detected MIDI devices
     */

    public static List<MidiDeviceInfo> getMidiDevices() {

        List<MidiDeviceInfo> result = new ArrayList<>();

        MidiDevice.Info[] infos = getMidiDeviceInfo();

        for (int i = 0; i < infos.length; i++) {

            try {
                MidiDevice device = MidiSystem.getMidiDevice(infos[i]);

                result.add(new MidiDeviceInfo(infos[i].getName(), i, device.getMaxTransmitters(), device.getMaxReceivers()));

            } catch (MidiUnavailableException e) {
                LOGGER.warning("MIDI device error: " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Retrieves all MIDI devices capable of transmitting MIDI messages to the application.
     *
     * @return available MIDI input devices
     */
    public static List<MidiDeviceInfo> getMidiInputs() {

        return getMidiDevices()
                .stream()
                .filter(d ->
                        d.rxTransmitters() >= 0)
                .toList();
    }

    /**
     * Retrieves all MIDI devices capable of receiving MIDI messages from the application.
     *
     * @return available MIDI output devices
     */
    public static List<MidiDeviceInfo> getMidiOutputs() {

        return getMidiDevices()
                .stream()
                .filter(d ->
                        d.txReceivers() >= 0)
                .toList();
    }

    /** Scans the USB bus for USB-MIDI devices.
     *
     * <p>Only interfaces implementing the USB Audio MIDI Streaming subclass are returned.</p>
     * @return detected USB MIDI interfaces
     */
    public static List<UsbMidiDeviceInfo>getUsbMidiDevices() {

        List<UsbMidiDeviceInfo> result = new ArrayList<>();

        Context context = new Context();

        try {
            int rc = LibUsb.init(context);

            if (rc != LibUsb.SUCCESS) {
                return result;
            }

            DeviceList devices = new DeviceList();

            LibUsb.getDeviceList(context, devices);

            try {
                for (Device device : devices) {

                    DeviceDescriptor desc = new DeviceDescriptor();

                    if (LibUsb.getDeviceDescriptor(device, desc) != LibUsb.SUCCESS) {
                        continue;
                    }

                    UsbMidiDeviceInfo info = detectMidiInterface(device, desc);

                    if (info != null) {
                        result.add(info);
                    }
                }

            } finally {
                LibUsb.freeDeviceList(devices, true);
            }

        } finally {
            LibUsb.exit(context);
        }

        return result;
    }

    /** Detects the first USB-MIDI interface exposed by the specified USB device.
     *
     * @param device USB device
     * @param deviceDescriptor device descriptor
     *
     * @return MIDI interface description or null when no MIDI interface exists*/
    private static UsbMidiDeviceInfo detectMidiInterface(Device device, DeviceDescriptor deviceDescriptor) {

        ConfigDescriptor configDescriptor = new ConfigDescriptor();

        int result = LibUsb.getActiveConfigDescriptor(device, configDescriptor);

        if (result != LibUsb.SUCCESS) {
            return null;
        }

        try {
            Interface[] interfaces = configDescriptor.iface();

            for (Interface usbInterface : interfaces) {
                for (InterfaceDescriptor descriptor : usbInterface.altsetting()) {

                    // USB Audio / MIDI Streaming
                    if (descriptor.bInterfaceClass() != 0x01) {
                        continue;
                    }

                    if (descriptor.bInterfaceSubClass() != 0x03) {
                        continue;
                    }

                    int interfaceNumber = descriptor.bInterfaceNumber();

                    int inputEndpoint = -1;
                    int outputEndpoint = -1;

                    for (EndpointDescriptor endpoint : descriptor.endpoint()) {
                        int address =endpoint.bEndpointAddress() & 0xFF;
                        if ((address & 0x80) != 0) {
                            inputEndpoint = address;
                        } else {
                            outputEndpoint = address;
                        }
                    }

                    if (inputEndpoint < 0 || outputEndpoint < 0) {
                        continue;
                    }

                    String deviceName = String.format("%04X:%04X", deviceDescriptor.idVendor() & 0xFFFF, deviceDescriptor.idProduct()& 0xFFFF);

                    try {
                        DeviceHandle handle = new DeviceHandle();
                        if (LibUsb.open(device, handle) == LibUsb.SUCCESS) {

                            byte index = deviceDescriptor.iProduct();

                            if (index > 0) {
                                byte[] buffer = new byte[256];

                                int len = LibUsb.getStringDescriptor(handle, index, (short) 0x0409, ByteBuffer.wrap(buffer));

                                if (len > 2) {
                                    deviceName = new String(buffer, 2, len - 2, java.nio.charset.StandardCharsets.UTF_16LE);
                                }
                            }

                            LibUsb.close(handle);
                        }

                    } catch (Exception ignored) {
                    }

                    return new UsbMidiDeviceInfo(
                            deviceName,
                            deviceDescriptor.idVendor() & 0xFFFF,
                            deviceDescriptor.idProduct() & 0xFFFF,
                            interfaceNumber,
                            inputEndpoint,
                            outputEndpoint
                    );
                }
            }

        } finally {
            LibUsb.freeConfigDescriptor(configDescriptor);
        }

        return null;
    }

    /**
     * Description of a*discovered USB-MIDI interface.
     *
     * @param deviceName human-readable device name
     * @param vid USB vendor identifier
     * @param pid USB product identifier
     * @param interfaceNumber MIDI interface number
     * @param inputEndpoint USB IN endpoint
     * @param outputEndpoint USB OUT endpoint
     */
    public record UsbMidiDeviceInfo(

            String deviceName,
            int vid,
            int pid,
            int interfaceNumber,
            int inputEndpoint,
            int outputEndpoint
    ) {
        @Override
        public String toString(){
            return deviceName;
        }
    }
}