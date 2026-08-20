package uci.bcd2ptz.visca;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ViscaCommandBuilder {

    private final ViscaConfig config;

    public ViscaCommandBuilder(String viscaConfigFile) {

        ObjectMapper mapper = new ObjectMapper();

        File configFile = new File(viscaConfigFile);

        if (!configFile.exists()) {

            System.err.println(
                    "[VISCA] Nie znaleziono pliku konfiguracji: "
                            + viscaConfigFile
            );

            throw new IllegalStateException(
                    "Brak pliku konfiguracji VISCA"
            );
        }

        try {

            this.config =
                    mapper.readValue(
                            configFile,
                            ViscaConfig.class
                    );

            System.out.println(
                    "[VISCA] Załadowano profil kamery: "
                            + config.getCameraName()
            );

        } catch (IOException e) {

            System.err.println(
                    "[VISCA] Błąd parsowania pliku: "
                            + viscaConfigFile
            );

            e.printStackTrace();

            throw new IllegalStateException(
                    "Niepoprawna konfiguracja VISCA",
                    e
            );
        }
        System.out.println("TRANSPORT = " + config.getTransport());
    }

    /**
     * Generuje payload VISCA dla wskazanej komendy.
     */

    public byte[] build(String commandName, Map<String, Object> parameters) {

        CommandDefinition command =config.getCommands().get(commandName);

        if (command == null) {
            throw new IllegalArgumentException(
                    "Nieznana komenda: "  + commandName);
        }

        List<Byte> payload = new ArrayList<>();

        for (String token : command.getTemplate()) {

            if (!isPlaceholder(token)) {
                payload.add(
                        (byte) Integer.parseInt(
                                token,
                                16
                        )
                );
                continue;
            }

            String fieldName =
                    extractFieldName(token);

            Object value =
                    parameters.get(fieldName);

            if (value == null) {
                throw new IllegalArgumentException(
                        "Brak wartości pola: " + fieldName);
            }

            FieldDefinition field = config.getFields().get(fieldName);

            if (field == null) {
                throw new IllegalArgumentException(
                        "Brak definicji pola: " + fieldName);
            }

            encodeField(
                    payload,
                    field,
                    value
            );
        }
        return toByteArray(payload);
    }

    /**
     * Logika mapowania PAN/TILT.
     */
    private void encodeField(
            List<Byte> output,
            FieldDefinition field,
            Object value) {

        EncodingDefinition encoding =
                field.getEncoding();

        if (encoding == null) {

            throw new IllegalArgumentException(
                    "Brak definicji encoding dla pola"
            );
        }

        int numericValue =
                mapValue(field, value);

        //SCAF: - do wywalenia
        /*System.out.println(
                "FIELD=" + field.getDescription()
        );

        System.out.println(
                "INPUT=" + value
        );

        System.out.println(
                "MAPPED=" + numericValue +
                        " (" +
                        String.format("%04X", numericValue) +
                        ")"
        );*/

        switch (encoding.getType()) {
            case "byte":
                output.add(
                        (byte) numericValue
                );
                break;

            case "visca-nibble":
                encodeNibble(
                        output,
                        numericValue,
                        encoding.getSegments()
                );
                break;

            default:
                throw new UnsupportedOperationException(
                        "Nieznany typ kodowania: "
                                + encoding.getType()
                );
        }
    }

    /**
     * Mapowanie wartości PAN/TILT na podstawie konfigruacji kamery.
     */
    private int mapValue(
            FieldDefinition field,
            Object value) {

        if (!(value instanceof Number)) {

            throw new IllegalArgumentException(
                    "Pole nie jest liczbą: " + value
            );
        }

        if (field.getMapping() == null) {

            return ((Number) value).intValue();
        }

        String mappingType =
                (String) field.getMapping().get("type");

        if (mappingType == null) {

            return ((Number) value).intValue();
        }

        double numeric =
                ((Number) value).doubleValue();

        switch (mappingType) {

            case "signed-center":
                return mapSignedCenter(
                        numeric,
                        field
                );

            case "circular":
                return mapCircular(
                        numeric,
                        field
                );

            default:
                return ((Number) value).intValue();
        }
    }

    /**
     * Mapowanie wartości kątowych dla parametru w trybie L-C-R (PAN)
     */

    private int mapSignedCenter(
            double degrees,
            FieldDefinition field) {

        Map<String, Object> mapping =
                field.getMapping();

        Map<String, Object> deviceRange =
                (Map<String, Object>)mapping.get("deviceRange");

        Map<String, Object> mechanicalRange =
                (Map<String, Object>)mapping.get("mechanicalRange");

        int positiveLimit =
                Integer.parseInt(
                        (String) deviceRange.get(
                                "positiveLimit"),
                        16);

        int negativeLimit =
                Integer.parseInt(
                        (String) deviceRange.get(
                                "negativeLimit"),
                        16);

        double leftDegrees =
                ((Number) mechanicalRange.get(
                        "minDegrees")).doubleValue();

        double rightDegrees =
                ((Number) mechanicalRange.get(
                        "maxDegrees")).doubleValue();

        if (degrees >= 0) {

            return (int) Math.round(
                    (degrees / rightDegrees)
                            * positiveLimit
            );
        }

        int negativeSteps =
                0x10000 - negativeLimit;

        int steps =
                (int) Math.round(
                        (Math.abs(degrees)
                                / Math.abs(leftDegrees))
                                * negativeSteps
                );

        return (0x10000 - steps)
                & 0xFFFF;
    }
    /**
     * Mapowanie wartości kątowych dla parametru w trybie cvircular (TILT)
     */

    @SuppressWarnings("unchecked")
    private int mapCircular(
            double degrees,
            FieldDefinition field) {

        Map<String, Object> mapping =
                field.getMapping();

        Map<String, Object> deviceRange =
                (Map<String, Object>) mapping.get(
                        "deviceRange"
                );

        Map<String, Object> controllerRange =
                (Map<String, Object>) mapping.get(
                        "controllerRange"
                );

        int upLimit =
                Integer.parseInt(
                        (String) deviceRange.get(
                                "upLimit"
                        ),
                        16
                );

        int downLimit =
                Integer.parseInt(
                        (String) deviceRange.get(
                                "downLimit"
                        ),
                        16
                );

        double minDegrees =
                ((Number) controllerRange.get(
                        "minDegrees"
                )).doubleValue();

        double maxDegrees =
                ((Number) controllerRange.get(
                        "maxDegrees"
                )).doubleValue();

        // Soft-limits
        degrees = Math.max(
                minDegrees,
                Math.min(
                        maxDegrees,
                        degrees
                )
        );

        int value;

        if (degrees >= 0) {

            value =
                    (int) Math.round(
                            (degrees / maxDegrees)
                                    * upLimit
                    );

        } else {

            double ratio =
                    Math.abs(degrees)
                            / Math.abs(minDegrees);

            int span =
                    0x10000 - downLimit;

            value =
                    (0x10000
                            - (int) Math.round(
                            ratio * span
                    )) & 0xFFFF;
        }

        System.out.printf(
                "TILT %.2f -> %04X (%d)%n",
                degrees,
                value & 0xFFFF,
                value
        );

        return value & 0xFFFF;
    }

    /**
     * Konwersja liczby na ciąg nibble VISCA.
     */
    private void encodeNibble(
            List<Byte> output,
            int value,
            List<Integer> segments) {

        if (segments == null || segments.isEmpty()) {

            throw new IllegalArgumentException(
                    "Brak definicji segmentów"
            );
        }

        for (Integer shift : segments) {

            int nibble =
                    (value >> shift)
                            & 0x0F;

            output.add(
                    (byte) nibble
            );
        }
    }

    private boolean isPlaceholder(
            String token) {

        return token.startsWith("{")
                && token.endsWith("}");
    }

    private String extractFieldName(
            String token) {

        return token.substring(
                1,
                token.length() - 1
        );
    }

    private byte[] toByteArray(
            List<Byte> list) {

        byte[] result =
                new byte[list.size()];

        for (int i = 0; i < list.size(); i++) {

            result[i] = list.get(i);
        }

        return result;
    }

    /* ================================
     *    GETTERY I SETTERY
     * =================================*/

    public ViscaConfig getConfig() {
        return config;
    }

    public FieldDefinition getField(
            String fieldName) {

        return config.getFields()
                .get(fieldName);
    }

    public CommandDefinition getCommand(
            String commandName) {

        return config.getCommands()
                .get(commandName);
    }

}