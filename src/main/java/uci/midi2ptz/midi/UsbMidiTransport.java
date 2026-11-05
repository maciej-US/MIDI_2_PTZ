package uci.midi2ptz.midi;

import lombok.Getter;
import org.usb4java.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class UsbMidiTransport implements AutoCloseable {

    private  final short VID;
    private  final short PID;

    private Context context;
    private DeviceHandle handle;
    @Getter
    private int interfaceId;
    private byte inputEndpoint;
    private byte outputEndpoint;

    public UsbMidiTransport(short vid, short pid, int interfaceId, byte inputEndpoint, byte outputEndpoint) {
        this.VID = vid;
        this.PID = pid;
        this.interfaceId = interfaceId;
        this.inputEndpoint = inputEndpoint;
        this.outputEndpoint = outputEndpoint;
        this.context = new Context();
    }

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
