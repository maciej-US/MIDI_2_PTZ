package uci.bcd2ptz.midi;

import lombok.Data;

@Data
public class MidiBindingDefinition {

    /**
     * Funkcja systemowa.
     */
    private String action;
    private String panel;
    private boolean reassignable;
}