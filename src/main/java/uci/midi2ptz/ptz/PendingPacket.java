package uci.midi2ptz.ptz;

import lombok.Data;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents an outgoing VISCA-over-IP packet awaiting a response.
 *
 * <p>This class acts as a synchronization and tracking object for  asynchronous UDP communication between the application and a
 * PTZ camera.</p>
 *
 * <p>Each instance stores information about the transmitted packet, target endpoint and any responses received from the camera.</p>
 *
 * <p>Instances are created by {@link PTZCameraManager} when a packet  is transmitted and are completed when a corresponding response is
 * received by the UDP receiver thread.</p>
 */
@Data
public class PendingPacket {

    /** Raw payload transmitted to the camera.*/
    private final byte[] payload;

    /**
     * Queue containing response packets received for this request.
     *
     * <p>VISCA commands may generate one or more responses (for example ACK and completion messages). Therefore, responses are stored in a queue rather
     * than a single field.</p>
     */
    private final BlockingQueue<byte[]> responses = new LinkedBlockingQueue<>();

    /** Destination camera IP address.*/
    private final String targetIp;

    /** Destination camera UDP port.*/
    private final int targetPort;

    /** Timestamp indicating when the packet was created and sent.
     * <p>Stored as milliseconds since the Unix epoch.</p>*/
    private final long timestamp;

    /** Indicates whether at least one response packet has been received.*/
    private volatile boolean responseReceived = false;

    /**
     * Creates a new pending packet tracker.
     *
     * @param payload transmitted VISCA payload
     * @param targetIp destination camera IP address
     * @param targetPort destination camera port
     */
    public PendingPacket(
            byte[] payload,
            String targetIp,
            int targetPort) {

        this.payload = payload;
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.timestamp = System.currentTimeMillis();
    }

    /** Returns the packet age.
     *
     * @return elapsed time in milliseconds since creation
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Marks that a response has been received for this request.
     *
     * <p>This method is typically called by the transport receiver thread after matching an incoming UDP packet with this
     * pending request.</p>
     */
    public void markResponseReceived() {
        responseReceived = true;
    }

    /**
     * Waits for a response to arrive.
     *
     * <p>The method blocks until either a response is received or the specified timeout expires.</p>
     *
     * @param timeoutMs maximum wait time in milliseconds
     * @return {@code true} if a response was received, otherwise {@code false}
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public boolean waitForResponse(long timeoutMs) throws InterruptedException {

        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (responseReceived) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    /**
     * Returns a human-readable packet description intended for debugging and logging purposes.
     *
     * @return textual packet representation
     */
    @Override
    public String toString() {
        return "PendingPacket{" +
                ", targetIp='" + targetIp + '\'' +
                ", targetPort=" + targetPort +
                ", age=" + getAge() +
                '}';
    }
}