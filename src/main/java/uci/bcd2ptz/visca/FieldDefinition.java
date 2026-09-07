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
    private String type;
    private Map<String,Object> range;
    private Map<String, Object> mapping;

    private String description;

    public long getMinRangeValue(){
        if(range == null){
            throw new IllegalStateException("Range is not defined for this field.");
        }
        Object value = range.get("min");
        return value instanceof Number ? ((Number)value).longValue() : null;
    }

    public long getMaxRangeValue(){
        if(range == null){
            throw new IllegalStateException("Range is not defined for this field.");
        }
        Object value = range.get("max");
        return value instanceof Number ? ((Number)value).longValue() : null;
    }

    public Integer getMappedValue(String key){
        if (mapping == null){
            throw new IllegalStateException("Mapping is not defined for this field.");
        }
        Object value = mapping.get(key);
        return value instanceof Number ? ((Number)value).intValue() : null;
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "encoding=" + encoding +
                ", mapping=" + mapping +
                ", description='" + description + '\'' +
                '}';
    }
}