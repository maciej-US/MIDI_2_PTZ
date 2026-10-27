package uci.midi2ptz.core;

/**
 * Receives ControlEvents published by the control subsystem.
 *
 * <p>Implementations may represent GUI components,
 * hardware controllers or application services.</p>
 */
@FunctionalInterface
public interface ControlEventListener {

    /**
     * Handles an incoming control event.
     * @param event received event
     */
    void onControlEvent(ControlEvent event);
}

