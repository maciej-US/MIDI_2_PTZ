package uci.midi2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class CommandDefinition {

    private String description;

    private String type;

    private String responseType;

    private List<String> commandTemplate;

    private List <String> responseTemplate;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getCommandTemplate() {
        return commandTemplate;
    }

    public List<String> getResponseTemplate() {return responseTemplate;}

    public void setCommandTemplate(List<String> commandTemplate) {
        this.commandTemplate = commandTemplate;
    }
}