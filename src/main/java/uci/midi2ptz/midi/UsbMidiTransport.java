package uci.midi2ptz.midi;

import lombok.Getter;
import org.usb4java.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Low-level USB MIDI transport implementation.
 *
 * <p>The transport manages direct communication with a USB MIDI device using the libusb backend. It is responsible for device
 * discovery, interface claiming and packet-level USB transfers.</p>
 *
 * <p>The class operates below the MIDI endpoint layer and exposes only raw USB MIDI packet transmission and reception services.</p>
 *
 * <p>A single transport instance is typically shared by both {@link UsbMidiInputEndpoint} and {@link UsbMidiOutputEndpoint}.</p> */
public class UsbMidiTransport implements AutoCloseable {

    /** USB vendor identifier. */
    private  final short VID;
    /** USB product identifier. */
    private  final short PID;

    /** Active libusb context. */
    private Context context;

    /** Open device handle. */
    private DeviceHandle handle;

    /** Claimed USB MIDI interface number. */
    @Getter
    private final int interfaceId;
    /** USB bulk IN endpoint. */
    private final byte inputEndpoint;
    /** USB bulk OUT endpoint. */
    private final byte outputEndpoint;

    /**
     * Creates a USB MIDI transport descriptor.
     *
     * <p>The physical device is not opened during  construction. USB initialization is performed by {@link #start()}.</p>
     *
     * @param vid USB vendor identifier
     * @param pid USB product identifier
     * @param interfaceId MIDI interface num*er
     * @param inputEndpoint USB IN endpoint
     * @param outputEndpoint USB OUT endpoint */
    public UsbMidiTransport(short vid, short pid, int interfaceId, byte inputEndpoint, byte outputEndpoint) {
        this.VID = vid;
        this.PID = pid;
        this.interfaceId = interfaceId;
        this.inputEndpoint = inputEndpoint;
        this.outputEndpoint = outputEndpoint;
        this.context = new Context();
    }

    /**
     * Opens the USB MIDI device and claims the configured MIDI interface.
     *
     *<p>The target device is located using the configured VID/PID pair</p>
     *
     * @throws RuntimeException if device discovery, opening or interface claiming fails */
    public void start() {

        if (handle != null) {
            return;
        }

        int result = LibUsb.init(context);

        if (result != LibUsb.SUCCESS) {
            throw new RuntimeException("LibUSB init failed: " + LibUsb.strError(result));
        }
        DeviceList list = new DeviceList();
        LibUsb.getDeviceList(context, list);

        try {
            Device device = getDevice(list);
            if (device == null) {
                throw new RuntimeException("USB MIDI device VID=" + Integer.toHexString(VID & 0xFFFF) + " PID=" + Integer.toHexString(PID & 0xFFFF) + " not found.");
            }

            this.handle = new DeviceHandle();

            result = LibUsb.open(device, handle);
            if (result != LibUsb.SUCCESS) {
                throw new RuntimeException("Error opening USB Device: " + LibUsb.strError(result));
            }

            result = LibUsb.claimInterface(handle, interfaceId);
            if (result != LibUsb.SUCCESS) {
                throw new RuntimeException("Error claiming USB interface: " + LibUsb.strError(result));
            }

        }finally {
            LibUsb.freeDeviceList(list, true);
        }

    }

    /**
     * Releases the claimed USB interface and closes all libusb resources.
     * <p>Calling this method multiple times is safe.</p> */
    @Override
    public void close() {

        if (handle != null) {

            LibUsb.releaseInterface(handle, interfaceId);
            LibUsb.close(handle);
            handle = null;
        }

        if (context != null) {
            LibUsb.exit(context);
            context = null;
        }
    }

    /** Reads a single USB MIDI packet.
     *
     * @param timeout transfer timeout in milliseconds
     * @return received packet or {@code null} when the operation times out     *
     * @throws RuntimeException when*the USB transfer fails
     */
    public byte[] readPacket(int timeout) {

        ByteBuffer buffer = BufferUtils.allocateByteBuffer(64);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();

        int rc = LibUsb.bulkTransfer(
                        handle,
                        inputEndpoint,
                        buffer,
                        transferred,
                        timeout
                );

        if (rc == LibUsb.ERROR_TIMEOUT) {
            return null;
        }

        if (rc != LibUsb.SUCCESS) {
            throw new RuntimeException(
                    LibUsb.strError(rc)
            );
        }

        int len = transferred.get(0);

        byte[] result = new byte[len];

        for (int i = 0; i < len; i++) {
            result[i] = buffer.get(i);
        }

        return result;
    }

    /** Sends a*raw USB MIDI packet.
     *
     * @param packet packet to transmit
     * @throws RuntimeException when the USB transfer fails */
    public void sendPacket(byte[] packet) throws RuntimeException{

        ByteBuffer out =BufferUtils.allocateByteBuffer(packet.length);
        out.put(packet);
        out.rewind();

        IntBuffer transferred = BufferUtils.allocateIntBuffer();

        int rc = LibUsb.bulkTransfer(handle, outputEndpoint, out, transferred, 1000);

        if (rc != LibUsb.SUCCESS) {
            throw new RuntimeException(LibUsb.strError(rc));
        }
    }

    /**
     * Searches the USB device list for the configured VID/PID pair.
     *
     * @param list enumerated USB devices
     * @return matching device or {@code null} */
    private Device getDevice(DeviceList list) {

        for (Device device : list) {

            DeviceDescriptor desc = new DeviceDescriptor();

            if (LibUsb.getDeviceDescriptor(device, desc) != LibUsb.SUCCESS) {
                continue;
            }

            if ((desc.idVendor() & 0xFFFF) == VID && (desc.idProduct() & 0xFFFF) == PID) {
                return device;
            }
        }
        return null;
    }


}
