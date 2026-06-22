package uci.bcd2ptz;

public class MidiEvent {

    public enum Type { CC, NOTE }

    private final Type type;
    private final int channel;
    private final int number;
    private final int value;

    public MidiEvent(Type type, int channel, int number, int value) {
        this.type = type;
        this.channel = channel;
        this.number = number;
        this.value = value;
    }

    public Type getType() { return type; }
    public int getChannel() { return channel; }
    public int getNumber() { return number; }
    public int getValue() { return value; }
}