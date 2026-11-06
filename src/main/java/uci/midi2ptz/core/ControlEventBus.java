package uci.midi2ptz.core;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe event bus used for asynchronous communication  between application components.
 *
 * <p>The event bus provides a simple producer-consumer mechanism based on a {@link BlockingQueue}. Events can be published by
 * any subsystem and consumed by one or more processing threads.</p>
 *
 * <p>This class forms the central communication channel between MIDI input handlers, GUI components, control logic and other
 * runtime services.</p>
 *
 * <p>Events are processed in FIFO (First In, First Out) order.</p>
 *
 * <h2>Design Notes</h2>
 * <p>The event bus intentionally provides a single shared queue rather than a publish-subscribe implementation. All events are
 * consumed by the central control runtime, which acts as the sole event dispatcher for the application.</p>
 */
public class ControlEventBus {


    private final List<ControlEventListener> listeners = new CopyOnWriteArrayList<>();

    public void register(ControlEventListener listener) {
        listeners.add(listener);
    }

    public void unregister(ControlEventListener listener) {
        listeners.remove(listener);
    }

    public void publish (ControlEvent event) {
        for (ControlEventListener listener : listeners){
            //DEBUG
            //System.out.println("EVENT -> " + event.getAction() + " THREAD=" + Thread.currentThread().getName());

            listener.onControlEvent(event);
        }
    }
}