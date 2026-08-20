package uci.bcd2ptz.midi;

import lombok.Data;

import java.util.Map;

@Data
public class MidiConfig {

    private String controllerName;

    private Map<String, MidiCommandDefinition> input;

    private Map<String, MidiCommandDefinition> output;

    private Map<String, MidiCommandDefinition> system;
}
