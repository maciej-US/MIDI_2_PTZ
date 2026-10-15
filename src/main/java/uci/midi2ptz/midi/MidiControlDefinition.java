package uci.midi2ptz.midi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidiControlDefinition {

    /**
     * jog,  slider,  button, led
     */
    private String type;

    /**
     * input,  output
     */
    private String direction;

    /**
     * cc,  note
     */
    private String messageType;

    /**
     *  kanał CC
     */
    private Integer channel;

    /**
     * numer nuty
     */
    private Integer note;

    /**
     * dodatkowe parametry typu: range, states, neutral, clockwise, counterClockwise
     * zależnie od typu elementu
     */
    private Map<String, Object> properties;
}
