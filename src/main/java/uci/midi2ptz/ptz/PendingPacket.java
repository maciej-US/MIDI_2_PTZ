package uci.midi2ptz.ptz;

import lombok.Data;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Data
public class PendingPacket {

    private final byte[] payload;

    //private byte[] response = null;
    private final BlockingQueue<byte[]> responses = new LinkedBlockingQueue<>();

    private final String targetIp;

    private final int targetPort;

    private final long timestamp;

    private volatile boolean responseReceived = false;

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

    public void markResponseReceived() {
        responseReceived = true;
    }

    public boolean waitForResponse(long timeoutMs)
            throws InterruptedException {

        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (responseReceived) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
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