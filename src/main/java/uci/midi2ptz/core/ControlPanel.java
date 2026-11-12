package uci.midi2ptz.core;

import lombok.*;
import uci.midi2ptz.core.MainControlCore.Control;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Runtime state associated with a logical control panel.
 *
 * <p>A control panel represents a logical operator workspace managed by {@link MainControlCore}. A panel may be associated
 * with a camera, a GUI panel and a physical MIDI controller panel, but remains independent of any specific transport implementation.</p>
 *
 * <p>The class stores runtime-only information required by the  control engine, including active control states, MIDI soft
 * takeover data and jog-wheel timing information.</p>
 *
 * <p>Instances are created and managed by the control core and  synchronized through the event bus.</p>
 */
@Data
@RequiredArgsConstructor
public class ControlPanel {

    /** Unique identifier of the logical control panel. */
    private final MainControlCore.ControlPanelId id;

    /**
     * Indicates whether pan and tilt commands are currently accepted.
     * <p>The flag may temporarily disable movement processing while preserving operation of other camera controls.</p>
     */

    private boolean acceptingPanTilt;

    /**
     * Timestamp of the last jog-wheel activity.
     * <p>Used by watchdog and timeout logic to detect inactivity and generate stop events when necessary.</p>
     */

    private long jogTimestamp;

    /**
     * Soft-takeover state for MIDI controls.
     *
     * <p>Stores the last known physical value for each controller element. A control becomes active only after the physical
     * controller reaches the current software value, preventing abrupt parameter jumps after panel switching, camera changes
     * or configuration reloads.</p>
     */
    private final Map<Control, Integer> midiCatch = new ConcurrentHashMap<>();

    /**
     * Current mode state of all controls assigned to this panel.
     *
     * <p>The map stores runtime control states used by the control engine to evaluate user input, synchronize GUI indicators
     * and maintain controller feedback.</p>
     *
     * <p>Examples include active camera functions, toggle states and control-specific operating modes.</p>
     */
    private final Map<MainControlCore.Control, MainControlCore.ModeState> states = new EnumMap<>(MainControlCore.Control.class);
}