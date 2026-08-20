package uci.bcd2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class FieldDefinition {

    @Getter
    private EncodingDefinition encoding;
    private Map<String,Object> range;
    private Map<String, Object> mapping;

    private String description;

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "encoding=" + encoding +
                ", mapping=" + mapping +
                ", description='" + description + '\'' +
                '}';
    }
}