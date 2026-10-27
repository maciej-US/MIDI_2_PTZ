package uci.midi2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;

import java.util.Map;

/**
 * Root VISCA protocol configuration model.
 *
 * <p>This class represents the complete protocol definition for a supported PTZ camera model. Configuration instances
 * are loaded from external JSON files and used by the VISCA subsystem to generate commands, decode responses and translate camera parameters.</p>
 *
 * <p>The configuration contains:</p>
 *
 * <ul>
 *     <li>camera metadata,</li>
 *     <li>VISCA command definitions,</li>
 *     <li>field and parameter definitions,</li>
 *     <li>transport-specific configuration.</li>
 * </ul>
 *
 * <p>The protocol layer is entirely data-driven, allowing support for different camera models without changing the application source code.</p>
 *
 * <h2>Configuration Structure</h2> *
 * <pre>
 * ViscaConfig
 * ├── cameraName
 * ├── commands
 * ├── fields
 * └── transport
 * </pre>
 *
 * <p>Each supported camera model should have its own VISCA configuration file, allowing the same runtime implementation to communicate with different camera
 * families through configuration alone.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViscaConfig {

    /**
     * Human-readable camera model name.
     *
     * <p>This value identifies the camera profile represented by the current VISCA configuration.</p>
     */
    @Getter
    private String cameraName;

    /**
     * Available VISCA command definitions.     *
     * <p>The key represents the logical command name while the value contains the protocol-specific definition used to build and validate VISCA packets.</p>
     */
    @Getter
    private Map<String, CommandDefinition> commands;

    /**
     * Protocol field definitions.     *
     * <p>Fields describe how camera parameters are encoded, decoded and mapped between application values and VISCA protocol data.</p>
     */
    @Getter
    private Map<String, FieldDefinition> fields;

    /**
     * Transport-layer configuration.
     *
     * <p>Contains camera-specific communication parameters used by the VISCA-over-IP implementation.</p>     *
     * <p>Typical entries may include protocol version, packet format options or transport-specific settings.</p>
     */
    @Getter
    private Map<String, Object> transport;
}