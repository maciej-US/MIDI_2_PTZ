package uci.midi2ptz.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import uci.midi2ptz.config.AppConfigManager.ConfigurationSection;

@Data
public class ConfigurationUpdateResults {
    private boolean success;
    private Map<ConfigurationSection, String> errors = new LinkedHashMap<>();

    public void addError (ConfigurationSection section, String message) {
        errors.put(section, message);
    }

}
