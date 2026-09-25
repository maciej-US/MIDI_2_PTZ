package uci.bcd2ptz.ptz;

import lombok.Data;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

@Data
public class PendingPacket {

    private final byte[] payload;

    //private byte[] response = null;
    private final BlockingQueue<byte[]> responses = new LinkedBlockingQueue<>();

    private final String targetIp;

    private final int targetPort;

    private final long timestamp;

    public PendingPacket(
            byte[] payload,
            String targetIp,
            int targetPort) {


        this.payload = payload;
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.timestamp = System.currentTimeMillis();
    }

    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }

    @Override
    public String toString() {
        return "PendingPacket{" +
                ", targetIp='" + targetIp + '\'' +
                ", targetPort=" + targetPort +
                ", age=" + getAge() +
                '}';
    }
}