package uci.midi2ptz.midi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * Describes a physical MIDI control element.
 *
 * <p>This class defines how a hardware control is represented  within the application. It contains information about the
 * control type, MIDI message format and device-specific configuration parameters.</p>
 *
 * <p>Instances are loaded from MIDI controller definition files and used by the MIDI subsystem for message decoding and
 * feedback generation.</p>
 * <p>This class describes what a control physically is, while {@link MidiBindingDefinition} describes what the
 * control does within the application.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidiControlDefinition {

    /**
     * Type of the physical control element.
     *
     * <p>Typical values include:</p>
     * <ul>
     *     <li>{@code jog}</li>
     *     <li>{@code slider}</li>
     *     <li>{@code button}</li>
     *     <li>{@code led}</li>
     * </ul>
     */
    private String type;

    /**
     * Direction of MIDI communication.
     *
     * <p>Supported values:</p>
     *
     * <ul>
     *     <li>{@code input} - receives user interaction events</li>
     *     <li>{@code output} - accepts feedback messages from the application</li>
     * </ul>
     */
    private String direction;

    /**
     * MIDI message type used by the control.
     *
     * <p>Typical values:</p>     *
     * <ul>
     *     <li>{@code cc} - Control Change message</li>
     *     <li>{@code note} - Note On/Off message</li>
     * </ul>
     */
    private String messageType;

    /**
     * MIDI controller number or CC channel identifier.
     *
     * <p>Used when {@link #messageType} is set to {@code cc}.</p>
     */
    private Integer channel;

    /**
     * MIDI note number.
     *
     * <p>Used when {@link #messageType} is set to {@code note}.</p>
     */
    private Integer note;

    /**
     * Additional control-specific properties.
     *
     * <p>The available entries depend on the control type and may contain information such as:</p>     *
     * <ul>
     *     <li>value range definitions,</li>
     *     <li>available LED states,</li>
     *     <li>neutral positions,</li>
     *     <li>clockwise/counter-clockwise values,</li>
     *     <li>hardware-specific parameters.</li>
     * </ul>
     */
    private Map<String, Object> properties;
}