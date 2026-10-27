package uci.midi2ptz.midi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * Represents a complete MIDI controller configuration.
 *
 * <p>This class defines the physical controller layout, hardware capabilities and logical action mappings used
 * by the MIDI subsystem.</p>
 *
 * <p>Configuration instances are typically loaded from JSON definition files and used by {@code MidiController} and
 * {@code MidiControllerManager} during initialization.</p>
 *
 * <h2>Configuration Structure</h2> *
 * <ul>
 *     <li>Controller metadata</li>
 *     <li>Physical control definitions</li>
 *     <li>Logical action bindings</li>
 *     <li>MIDI device assignments</li>
 *     <li>Hardware-specific capabilities</li>
 * </ul>
 * <pre>
 * MidiConfig
 * ├── controls
 * ├── bindings
 * ├── inputDeviceIndex
 * ├── outputDeviceIndex
 * ├── hardwareBlinking
 * └── jogResolution
 * </pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidiConfig {

    /**
     * Human-readable controller name.
     *
     * <p>This value is typically used for identification, logging and configuration validation.</p>
     */
    private String controllerName;

    /**
     * Physical controls available on the MIDI device.
     *
     * <p>The key identifies a control element while the value describes its characteristics, MIDI messages and supported features.</p>
     */
    private Map<String, MidiControlDefinition> controls;

    /**
     * Logical action bindings.
     *
     * <p>Defines how physical controls are mapped to application functions.</p>
     *
     * <p>The key references a control defined in {@link #controls} while the value specifies the associated runtime action.</p>
     */
    private Map<String, MidiBindingDefinition> bindings;

    /**
     * Index of the MIDI input device.
     *
     * <p>The index corresponds to the position reported by the Java MIDI subsystem.</p>
     */
    private Integer inputDeviceIndex;

    /**
     * Index of the MIDI output device.
     *
     * <p>The output device is typically used for sending LED, indicator and feedback messages back to the controller.</p>
     */
    private Integer outputDeviceIndex;

    /**
     * Indicates whether the controller natively supports hardware blinking effects.
     *
     * <p>If enabled, LED blinking can be offloaded to the device instead of being emulated in software.</p>
     */
    private boolean hardwareBlinking;

    /**
     * Resolution used for jog wheel processing.
     *
     * <p>The value defines how many logical steps are generated across the full jog wheel movement range and is used when
     * converting MIDI movement data into camera position changes.</p>
     */
    private Integer jogResolution;
}