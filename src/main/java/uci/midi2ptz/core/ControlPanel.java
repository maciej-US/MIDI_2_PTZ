package uci.midi2ptz.core;

import lombok.*;
import uci.midi2ptz.core.MainControlCore.Control;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Represents the runtime state of a logical control panel.
 *
 * <p>A control panel groups a set of controls associated with a single  camera, operator workspace or application context. The class stores
 * panel-specific state information used by the control engine during event processing.</p>
 *
 * <p>The panel maintains control states, MIDI catch values and  timing information required by jog and transport operations.</p>
 *
 * <p>Instances of this class are managed by the control core and synchronized with GUI and MIDI controller components.</p>
 */
@Data
@RequiredArgsConstructor
public class ControlPanel {

    /** Unique identifier of the control panel. */
    private final MainControlCore.ControlPanelId id;

    /**
     * Indicates whether pan/tilt operations are currently accepted.
     *
     * <p>This flag may be used to temporarily disable camera movement processing while still allowing other controls to operate.</p>
     */
    private boolean acceptingPanTilt;

    /**
     * Timestamp of the last jog-wheel related activity.
     *
     * <p>The value is typically expressed in milliseconds since the epoch and may be used to detect inactivity or calculate control timing.</p>
     */
    private long jogTimestamp;

    /**
     * Runtime MIDI catch values for individual controls.
     *
     * <p>This map stores the last known MIDI value associated with each  control and is used to implement soft-takeover (MIDI catch)
     * behaviour, preventing parameter jumps when physical controller  positions do not match application state.</p>
     * <p>This mechanism implements MIDI soft takeover. A control becomes active only after the physical controller reaches the current
     * software value, preventing sudden parameter changes after panel switching or configuration updates.</p>
     */
    private final Map<Control, Integer> midiCatch = new ConcurrentHashMap<>();

    /**
     * Current state of controls belonging to this panel.
     *
     * <p>The map tracks the active mode of each control and is used by the runtime logic to determine control behaviour and GUI synchronization.</p>
     */
    private final Map<MainControlCore.Control, MainControlCore.ModeState> states = new EnumMap<>(MainControlCore.Control.class);
}