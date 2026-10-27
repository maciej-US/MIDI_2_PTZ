package uci.midi2ptz.visca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


/**
 * Defines a VISCA command specification loaded from a VISCA  protocol definition file.
 *
 * <p>A command definition describes how a logical camera operation is translated into one or more VISCA protocol messages and how
 * the corresponding response should be interpreted.</p>
 *
 * <p>Command definitions are used by the VISCA controller layer to generate protocol packets dynamically without hardcoding
 * camera-specific command sequences.</p>
 *
 * <p>The command definition model allows camera protocols to be described through external JSON files rather than hardcoded
 * Java implementations. This makes it possible to support multiple camera models by providing different VISCA configuration files.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class CommandDefinition {

    /**
     * Human-readable command description.
     *
     * <p>Primarily intended for documentation, debugging and configuration maintenance.</p>
     */
    private String description;

    /**
     * Command category.
     *
     * <p>Examples may include command, inquiry, setter, action or other protocol-specific operation types defined by
     * the VISCA configuration.</p>
     */
    private String type;

    /**
     * Expected VISCA response type.
     *
     * <p>This field determines how incoming packets should be interpreted and validated after command execution.</p>
     */
    private String responseType;

    /**
     * VISCA command template.
     *
     * <p>The template contains the packet definition used to construct the final VISCA command sent to the camera.</p>     *
     * <p>Template entries may include fixed protocol bytes and parameter placeholders resolved during command execution.</p>
     */
    private List<String> commandTemplate;

    /**
     * Expected VISCA response template.
     *
     * <p>The template describes the packet structure that should be received from the camera after executing the command.</p>     *
     * <p>It is used for response validation, parameter extraction and protocol synchronization.</p>
     */
    private List<String> responseTemplate;
}