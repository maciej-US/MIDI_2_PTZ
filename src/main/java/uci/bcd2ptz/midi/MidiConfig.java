package uci.bcd2ptz.midi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidiConfig {

    /**
     * Nazwa kontrolera MIDI.
     */
    private String controllerName;

    /**
     * Fizyczne elementy kontrolera.
     */
    private Map<String, MidiControlDefinition> controls;

    /**
     * Powiązanie fizycznych elementów z funkcjami systemu.
     */
    private Map<String, MidiBindingDefinition> bindings;

    private Integer inputDeviceIndex;
    private Integer outputDeviceIndex;
    private boolean hardwareBlinking;
    private Integer jogResolution;
}

