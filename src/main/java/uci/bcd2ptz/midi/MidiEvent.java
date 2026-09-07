package uci.bcd2ptz.midi;

import lombok.Getter;

@Getter
public class MidiEvent {

    public enum Type { CC, NOTE }

    private final Type type;
    private final int channel;
    private final int number;
    private final int value;
    private final String deviceId;

    public MidiEvent(Type type, int channel, int number, int value, String deviceID) {
        this.type = type;
        this.channel = channel;
        this.number = number;
        this.value = value;
        this.deviceId = deviceID;
    }

}