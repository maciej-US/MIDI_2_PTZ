package uci.midi2ptz.visca;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds VISCA command payloads from protocol definitions.
 *
 * <p>This class converts high-level camera operations into raw VISCA packet payloads using command, field and encoding
 * definitions loaded from a {@link ViscaConfig} instance.</p>
 *
 * <p>The builder resolves placeholders defined in command templates, applies value mappings and performs protocol-specific encoding
 * required by the target camera implementation.</p>
 *
 * <h2>Responsibilities</h2> *
 * <ul>
 *     <li>VISCA command template processing</li>
 *     <li>Field placeholder resolution</li>
 *     <li>Value mapping and conversion</li>
 *     <li>VISCA nibble encoding</li>
 *     <li>Protocol packet generation</li>
 * </ul>
 *
 * <p>The implementation is entirely data-driven and relies on external VISCA configuration files instead of hardcoded command definitions.</p>
 */
public class ViscaCommandBuilder {

    private final ViscaConfig config;

    /**
     * Creates a command builder for the specified VISCA configuration.     *
     * @param config VISCA protocol definition
     */
    public ViscaCommandBuilder(ViscaConfig config) {
        this.config = config;
    }

    /**
     * Builds a VISCA command packet using the supplied parameters.
     *
     * <p>The command template is loaded from the VISCA configuration and all placeholders are replaced with encoded parameter values.</p>
     *
     * @param commandName logical command name
     * @param parameters command parameters
     *
     * @return encoded VISCA packet payload
     *
     * @throws IllegalArgumentException if the command, field definition or required parameter is missing
     */
    public byte[] buildCommand(String commandName, Map<String, Object> parameters) {

        CommandDefinition command =config.getCommands().get(commandName);

        if (command == null) {
            throw new IllegalArgumentException("Unknown VISCA command: "  + commandName);
        }

        List<Byte> payload = new ArrayList<>();

        for (String token : command.getCommandTemplate()) {

            if (!isPlaceholder(token)) {payload.add((byte) Integer.parseInt(token, 16));
                continue;
            }

            String fieldName = extractFieldName(token);
            Object value = parameters.get(fieldName);

            if (value == null) {
                throw new IllegalArgumentException("Brak wartości pola: " + fieldName);
            }

            FieldDefinition field = config.getFields().get(fieldName);

            if (field == null) {
                throw new IllegalArgumentException("Brak definicji pola: " + fieldName);
            }

            encodeField(payload,field,value);
        }
        return toByteArray(payload);
    }

    /**
     * Builds a parameterless VISCA command.
     *
     * @param commandName logical command name @return encoded VISCA packet payload
     */
    public byte[] buildCommand(String commandName) {

        return buildCommand(commandName,Map.of());
    }

    /**
     * Encodes a field value according to its VISCA definition.
     *
     * <p>The selected encoding strategy determines how the value is converted into protocol bytes.</p>
     *
     * @param output destination packet buffer
     * @param field field definition
     * @param value field value
     */
    private void encodeField(List<Byte> output, FieldDefinition field, Object value) {

        EncodingDefinition encoding = field.getEncoding();

        int numericValue = mapValue(field, value);

        switch (encoding.getType()) {

            case "byte":
            case "enum":
            case "mapping":
                output.add((byte) numericValue);
                break;

            case "visca-nibble":
                encodeNibble(output,numericValue,encoding.getSegments());
                break;

            default:
                throw new UnsupportedOperationException("Nieznany typ kodowania: " + encoding.getType());
        }
    }

    /**
     * Converts an application-level value into its VISCA protocol representation.
     *
     * <p>The conversion may involve:</p>     *
     * <ul>
     *     <li>enumeration mapping</li>
     *     <li>lookup table resolution</li>
     *     <li>signed coordinate conversion</li>
     *     <li>camera-specific range mapping</li>
     * </ul>
     *
     * @param field field definition
     * @param value logical value
     *
     * @return encoded numeric value
     *
     * <h2>Template Example</h2>
     *
     * <pre>
     * Command template:
     *     81 01 06 02 {panPosition} {tiltPosition} FF
     *
     * Parameters:
     *     panPosition  = -45.0
     *     tiltPosition = 15.0
     *
     * Result:
     *     VISCA packet generated according to the field encoding definitions.
     * </pre>
     */
    private int mapValue(FieldDefinition field, Object value) {

        EncodingDefinition encoding = field.getEncoding();

        if (encoding == null) {
            throw new IllegalArgumentException("Brak encoding dla pola");
        }

        switch (encoding.getType()) {

            case "enum":
            case "mapping":
                Number mapped = encoding.getMapping().get(value.toString());

                if (mapped == null) {
                    throw new IllegalArgumentException("Brak mapowania dla: " + value);
                }
                return mapped.intValue();

            case "byte":
            case "visca-nibble":

                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("Pole nie jest liczbą: " + value);
                }

                if (field.getMapping() == null) {
                    return ((Number)value).intValue();
                }

                String mappingType = (String) field.getMapping().get("type");

                if (mappingType == null) {
                    return ((Number)value).intValue();
                }

                double numeric = ((Number)value).doubleValue();

                switch (mappingType) {
                    case "signed-center":
                        return mapSignedCenter(numeric, field);

                    case "circular":
                        return mapCircular(numeric, field);

                    default:
                        return ((Number)value).intValue();
                }

            default:
                throw new UnsupportedOperationException(
                        "Nieznany typ kodowania: "
                                + encoding.getType());
        }
    }

    /**
     * Maps a signed pan coordinate to a VISCA device value.
     *
     * <p>The center position is represented by zero while negative and positive positions are mapped to the corresponding protocol ranges.</p>
     *
     * @param degrees target pan position in degrees
     * @param field field definition
     *
     * @return VISCA protocol representation
     */
    private int mapSignedCenter(double degrees, FieldDefinition field) {

        Map<String, Object> mapping = field.getMapping();

        Map<String, Object> deviceRange = (Map<String, Object>)mapping.get("deviceRange");

        Map<String, Object> mechanicalRange = (Map<String, Object>)mapping.get("mechanicalRange");

        int positiveLimit =Integer.parseInt((String) deviceRange.get("positiveLimit"), 16);
        int negativeLimit =Integer.parseInt((String) deviceRange.get("negativeLimit"), 16);

        double leftDegrees = ((Number) mechanicalRange.get("minDegrees")).doubleValue();
        double rightDegrees = ((Number) mechanicalRange.get("maxDegrees")).doubleValue();

        if (degrees >= 0) {
            return (int) Math.round((degrees / rightDegrees) * positiveLimit);
        }

        int negativeSteps = 0x10000 - negativeLimit;
        int steps =(int) Math.round((Math.abs(degrees) / Math.abs(leftDegrees)) * negativeSteps);

        return (0x10000 - steps) & 0xFFFF;
    }
    /**
     * Maps a circular tilt coordinate to a VISCA device value.
     *
     * <p>The generated value respects both the camera mechanical limits and the protocol-specific device range.</p>
     *
     * @param degrees target tilt position in degrees
     * @param field field definition
     *
     * @return VISCA protocol representation
     */
    private int mapCircular(double degrees,FieldDefinition field) {

        Map<String, Object> mapping = field.getMapping();

        Map<String, Object> deviceRange = (Map<String, Object>) mapping.get("deviceRange");
        Map<String, Object> controllerRange =(Map<String, Object>) mapping.get("mechanicalRange");

        int upLimit =Integer.parseInt((String) deviceRange.get("upLimit"), 16);
        int downLimit = Integer.parseInt( (String) deviceRange.get("downLimit"), 16);

        double minDegrees = ((Number) controllerRange.get("minDegrees")).doubleValue();
        double maxDegrees = ((Number) controllerRange.get("maxDegrees")).doubleValue();

        // Soft-limits
        degrees = Math.max(minDegrees,Math.min(maxDegrees,degrees));

        int value;

        if (degrees >= 0) {value = (int) Math.round((degrees / maxDegrees) * upLimit);
        } else {
            double ratio =Math.abs(degrees) / Math.abs(minDegrees);
            int span = 0x10000 - downLimit;
            value = (0x10000- (int) Math.round(ratio * span)) & 0xFFFF;
        }
        return value & 0xFFFF;
    }

    /**
     * Encodes a value as a VISCA nibble sequence.
     *
     * <p>The output is generated according to the configured segment layout supplied by the encoding definition.</p>
     *
     * @param output destination packet buffer
     * @param value numeric value to encode
     * @param segments nibble shift definitions
     *
     * @throws IllegalArgumentException if no segment definition
     *         is available
     */
    private void encodeNibble(List<Byte> output, int value, List<Integer> segments) {

        if (segments == null || segments.isEmpty()) {

            throw new IllegalArgumentException("Brak definicji segmentów");
        }

        for (Integer shift : segments) {
            int nibble =(value >> shift)& 0x0F;
            output.add((byte) nibble);
        }
    }

    /**
     * Determines whether a template token represents a field placeholder.
     *
     * @param token template token
     * @return {@code true} if the token is a placeholder
     */
    private boolean isPlaceholder(String token) {

        return token.startsWith("{") && token.endsWith("}");
    }

    /**
     * Determines whether a template token represents a field placeholder.
     *
     * @param token template token
     * @return {@code true} if the token is a placeholder
     */
    private String extractFieldName(String token) {

        return token.substring(1, token.length() - 1);
    }

    /**
     * Converts a byte list into a primitive byte array.
     *
     * @param list source byte collection
     * @return byte array
     */
    private byte[] toByteArray(List<Byte> list) {

        byte[] result = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }

        return result;
    }

    /**
     * Returns the active VISCA configuration.
     *
     * @return VISCA configuration
     */
    public ViscaConfig getConfig() {
        return config;
    }

    /**
     * Returns a field definition by name.
     *
     * @param fieldName field identifier
     * @return field definition or {@code null}
     */
    public FieldDefinition getField(String fieldName) {

        return config.getFields().get(fieldName);
    }

    /**
     * Returns a command definition by name.
     *
     * @param commandName command identifier
     * @return command definition or {@code null}
     */
    public CommandDefinition getCommand(String commandName) {

        return config.getCommands().get(commandName);
    }

}