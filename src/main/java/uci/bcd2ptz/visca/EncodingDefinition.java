package uci.bcd2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)

public class EncodingDefinition {

    private String type;

    private List<Integer> segments;

    private Map<String, Integer> mapping;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Integer> getSegments() {
        return segments;
    }

    public Map<String, Integer> getMapping(){return this.mapping;}

    public void setSegments(List<Integer> segments) {
        this.segments = segments;
    }

    @Override
    public String toString() {
        return "EncodingDefinition{" +
                "type='" + type + '\'' +
                ", segments=" + segments +
                '}';
    }
}