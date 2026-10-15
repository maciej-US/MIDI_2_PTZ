package uci.midi2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class ViscaConfig {

    private String cameraName;

    private Map<String, CommandDefinition> commands;

    private Map<String, FieldDefinition> fields;

    public String getCameraName() {
        return cameraName;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
    }

    public Map<String, CommandDefinition> getCommands() {
        return commands;
    }

    public void setCommands(Map<String, CommandDefinition> commands) {
        this.commands = commands;
    }

    public Map<String, FieldDefinition> getFields() {
        return fields;
    }

    public void setFields(Map<String, FieldDefinition> fields) {
        this.fields = fields;
    }

    private Map<String, Object> transport;
}