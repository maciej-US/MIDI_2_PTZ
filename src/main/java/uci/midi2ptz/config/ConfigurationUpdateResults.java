package uci.midi2ptz.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import uci.midi2ptz.config.AppConfigManager.ConfigurationSection;


/**
 * Represents the result of a configuration validation or update operation.
 *
 * <p>This class is used by the configuration management subsystem to  collect and report errors encountered during configuration loading,
 * validation, saving, or runtime reconfiguration.</p>
 *
 * <p>Errors are grouped by {@link AppConfigManager.ConfigurationSection} to allow the caller to identify which subsystem failed validation.</p> *
 * <p>An empty error collection indicates a successful operation.</p>
 */
@Data
public class ConfigurationUpdateResults {

    /**
     * Collection of validation or runtime errors.
     *
     * <p>The key identifies the subsystem that generated the error, while the value contains a human-readable error description.</p>
     *
     * <p>A {@link LinkedHashMap} is used to preserve insertion order, allowing errors to be reported in the same sequence as they were detected.</p>
     */
    private Map<ConfigurationSection, String> errors = new LinkedHashMap<>();

    /**
     * Adds an error entry to the result.
     *
     * @param section configuration subsystem that generated the error
     * @param message human-readable error description
     */
    public void addError(ConfigurationSection section, String message) {
        errors.put(section, message);
    }
}
