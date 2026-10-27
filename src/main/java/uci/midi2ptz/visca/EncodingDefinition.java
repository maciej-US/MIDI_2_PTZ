package uci.midi2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Defines how values are encoded and decoded within the VISCA protocol.
 *
 * <p>An encoding definition specifies the binary representation  used when converting application values to VISCA packet fields
 * and vice versa.</p>
 *
 * <p>Different encoding strategies may be used depending on the parameter type, including segmented numeric values, enumerations
 * and protocol-specific lookup tables.</p>
 *
 * <p>Encoding definitions are loaded from VISCA configuration files and consumed by the VISCA controller during command generation
 * and response parsing.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncodingDefinition {

    /**
     * Encoding strategy type.     *
     * <p>Typical examples include numeric, enum or other camera-specific encoding mechanisms.</p>
     */
    private String type;

    /**
     * Segment widths used during value encoding and decoding.
     * <p>Each entry specifies the number of bits or nibbles assigned to a particular segment of the encoded value.</p>
     * <p>For example, a VISCA value represented as four hexadecimal digits may be described using multiple segments.</p>
     */
    private List<Integer> segments;

    /**
     * Logical-to-protocol value mapping.
     *
     * <p>Used primarily by enumerated parameters where a human-readable value must be translated into a VISCA protocol code.</p>
     *
     * <p>Example:</p>
     * <pre>
     * AUTO     -> 0
     * MANUAL   -> 3
     * ONE_PUSH -> 5
     * </pre>
     */
    private Map<String, Integer> mapping;

    /**
     * Returns the encoding strategy type.
     *
     * @return encoding type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the encoding segment definition.
     *
     * @return segment definition
     */
    public List<Integer> getSegments() {
        return segments;
    }

    /**
     * Returns the logical value mapping table.
     *
     * @return logical-to-protocol mapping
     */
    public Map<String, Integer> getMapping() {
        return this.mapping;
    }

    /**
     * Returns a human-readable description of the encoding
     * definition intended for debugging and logging.
     *
     * @return textual encoding description
     */
    @Override
    public String toString() {
        return "EncodingDefinition{" +
                "type='" + type + '\'' +
                ", segments=" + segments +
                '}';
    }
}