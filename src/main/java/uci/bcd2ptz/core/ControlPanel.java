package uci.bcd2ptz.core;

import lombok.*;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.core.ControlCore.Control;
import uci.bcd2ptz.core.ControlCore.ModeState;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@RequiredArgsConstructor
public class ControlPanel {

    private final ControlCore.ControlPanelId id;

    private PTZCamera assignedCamera;

    private boolean acceptingPanTilt;
    private long jogTimestamp;
    private final Map<Control, Integer> midiCatch = new ConcurrentHashMap<>();

    private final Map<ControlCore.Control, ControlCore.ModeState> states = new EnumMap<>(ControlCore.Control.class);

    public void setMode(Control control, ControlCore.ModeState modeState) {
        states.put(control, modeState);
    }

    public ModeState getMode(Control control) {
        return states.get(control);
    }

    public void updateJogTimestamp(){
        this.jogTimestamp = System.currentTimeMillis();
    }

}