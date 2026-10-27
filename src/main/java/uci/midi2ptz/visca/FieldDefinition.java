package uci.midi2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;

import java.util.Map;

/**
 * Describes a VISCA protocol field definition.
 *
 * <p>A field definition specifies how a camera parameter is represented within the VISCA protocol, including its encoding,
 * valid value range and any protocol-specific mappings.</p>
 *
 * <p>Field definitions are used during both command generation and response parsing to translate between camera protocol
 * values and application-level parameter representations.</p>
 *
 * <p>Examples of fields include:</p>
 *
 * <ul>
 *     <li>panPosition</li>
 *     <li>tiltPosition</li>
 *     <li>zoomPosition</li>
 *     <li>focusPosition</li>
 *     <li>whiteBalanceMode</li>
 *     <li>exposureMode</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class FieldDefinition {

    /**
     * Value encoding definition used by this field.
     *
     * <p>The encoding describes how values are converted between application data types and protocol packets.</p>
     */
    @Getter
    private EncodingDefinition encoding;

    /**
     * Field type.
     *
     * <p>The exact interpretation depends on the VISCA definition and may represent numeric, enum or protocol-specific value types.</p>
     */
    private String type;

    /**
     * Logical value range definition.
     *
     * <p>Typical entries include:</p>
     * <ul>
     *     <li>{@code min}</li>
     *     <li>{@code max}</li>
     *     <li>{@code step}</li>
     * </ul>
     */
    private Map<String, Object> range;

    /**
     * Protocol-specific field mapping configuration.
     *
     * <p>This structure may contain lookup tables, mechanical ranges, enumeration mappings and other field-specific metadata.</p>
     */
    private Map<String, Object> mapping;

    /**
     * Human-readable field description.
     */
    private String description;

    /**
     * Returns the minimum supported value for this field.
     *
     * <p>The value is resolved from the logical range definition or, when unavailable, from the field's mechanical range definition.</p>
     * @return minimum supported value     *
     * @throws IllegalStateException if no range information is available
     */
    public long getMinRangeValue(){
        if(range != null){
            Object value = range.get("min");
            if(value instanceof Number number){
                return number.longValue();
            }
        }

        if (mapping != null){
            Object mechanicalRangeObject = mapping.get("mechanicalRange");
            if (mechanicalRangeObject instanceof Map<?, ?> mechanicalRange){
                Object value = mechanicalRange.get("min");
                if (value instanceof Number){
                    return ((Number) value).longValue();
                }
            }
        }
        throw new IllegalStateException("Range is not defined for this field.");
    }

    /**
     * Returns the maximum supported value for this field.
     *
     * <p>The value is resolved from the logical range definition or, when unavailable, from the field's mechanical range definition.</p>     *
     * @return maximum supported value     *
     * @throws IllegalStateException if no range informationis available
     */
    public long getMaxRangeValue(){
        if(range != null){
            Object value = range.get("max");
            if(value instanceof Number number){
                return number.longValue();
            }
        }

        if (mapping != null){
            Object mechanicalRangeObject = mapping.get("mechanicalRange");
            if (mechanicalRangeObject instanceof Map<?, ?> mechanicalRange){
                Object value = mechanicalRange.get("max");
                if (value instanceof Number){
                    return ((Number) value).longValue();
                }
            }
        }
        throw new IllegalStateException("Range is not defined for this field.");
    }


    /**
     * Returns a textual field description intended for
     * debugging and diagnostic output.
     *
     * @return field description
     */
    @Override
    public String toString() {

        return "FieldDefinition{" +
                "encoding=" + encoding +
                ", mapping=" + mapping +
                ", description='" + description + '\'' +
                '}';
    }
}

