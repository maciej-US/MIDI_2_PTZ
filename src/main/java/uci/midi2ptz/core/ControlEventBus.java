package uci.midi2ptz.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ControlEventBus {

    private final BlockingQueue<ControlEvent>queue = new LinkedBlockingQueue<>();

    public void publish(ControlEvent event) {
        queue.offer(event);
    }

    public ControlEvent take() throws InterruptedException {
        return queue.take();
    }
}