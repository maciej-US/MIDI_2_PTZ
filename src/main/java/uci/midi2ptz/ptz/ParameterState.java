package uci.midi2ptz.ptz;

import lombok.Data;

/**
 * Represents the runtime state of a camera parameter.
 *
 * <p>The class is used to store both the numeric value and the logical operating state of a PTZ camera parameter.</p>
 *
 * <p>Depending on the parameter type, the state may represent:</p> *
 * <ul>
 *     <li>a continuous value, such as pan, tilt, zoom or focus position,</li>
 *     <li>a mode selection, such as exposure mode or white balance mode,</li>
 *     <li>an automation state, such as AUTO, MANUAL, ON or OFF.</li>
 * </ul>
 * <p>For example, an exposure parameter may simultaneously have a numeric value ({@code current = 5}) and a logical mode ({@code currentState = "MANUAL"}).</p> *
 *
 * <p>Instances of this class are maintained by {@code PTZCamera} and synchronized with the camera hardware, GUI and MIDI controller indicators.</p>
 */
@Data
public class ParameterState {

    /** Minimum allowed parameter value.*/
    private double min;

    /** Maximum allowed parameter value.*/
    private double max;

    /**
     * Current numeric parameter value.
     *
     * <p>The interpretation depends on the parameter type, for example position, gain level, shutter value or zoom setting.</p>
     */
    private double current;

    /**
     * Current logical state of the parameter.
     *
     * <p>Typical values include:</p>     *
     * <ul>
     *     <li>{@code AUTO}</li>
     *     <li>{@code MANUAL}</li>
     *     <li>{@code ON}</li>
     *     <li>{@code OFF}</li>
     *     <li>{@code ACTIVE}</li>
     * </ul>
     */
    private String currentState;
}