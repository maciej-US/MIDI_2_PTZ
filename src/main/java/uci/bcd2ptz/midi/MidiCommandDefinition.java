package uci.bcd2ptz.midi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidiCommandDefinition {

    private String description;

    private String type;

    private List<String> template;

    private Map<String, Object> metadata;
}