package uci.midi2ptz.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Lightweight synchronous event bus used for communication  between runtime subsystems.
 *
 * <p>The bus implements a simple publish-subscribe model. Any component may publish a {@link ControlEvent} and any
 * registered listener will receive the event immediately in the publishing thread.</p>
 *
 * <p>The implementation is thread-safe and allows listeners to be registered and removed during runtime without
 * affecting event delivery.</p>
 *
 * <h2>Architecture</h2>
 * <p>The event bus acts as the central communication channel between:</p> *
 * <ul>
 *     <li>MIDI controllers,</li>
 *     <li>control core,</li>
 *     <li>GUI components,</li>
 *     <li>camera management services,</li>
 *     <li>configuration services.</li>
 * </ul>
 *
 * <p>Events are delivered synchronously in registration order. Long-running processing should therefore be delegated to
 * dedicated worker threads instead of blocking the event bus.</p>
 * <p>The bus is intentionally synchronous. Event processing is expected to be lightweight. Subsystems requiring asynchronous processing should
 * manage their own worker threads.</p>
 */
public class ControlEventBus {

    /** Registered event listeners. */
    private final List<ControlEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a new event listener.
     * @param listener listener to register
     */
    public void register(ControlEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously registered listener.
     * @param listener listener to remove
     */
    public void unregister(ControlEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Publishes an event to all registered listeners.
     *
     * <p>The event is delivered synchronously in the calling thread.</p>
     * <p>Listeners are invoked sequentially in the order hey were registered.</p>
     *
     * @param event event to publish
     */
    public void publish (ControlEvent event) {
        for (ControlEventListener listener : listeners){
            //DEBUG
            //System.out.println("EVENT -> " + event.getAction() + " THREAD=" + Thread.currentThread().getName());

            listener.onControlEvent(event);
        }
    }
}