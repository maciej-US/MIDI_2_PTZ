package uci.midi2ptz.core;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ControlEvent {

    public enum EventSource{
        MIDI,
        GUI,
        SYSTEM
    }
    private EventSource eventSource;
    //private String controlID;
    private ControlCore.ControlPanelId panel;
    private String action;
    private int value;
}