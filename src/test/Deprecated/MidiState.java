//package uci.bcd2ptz;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MidiState {

    private final Map<Integer, Integer> ccValues = new ConcurrentHashMap<>();

    public void updateCC(int cc, int value) {
        ccValues.put(cc, value);
    }

    public int getCC(int cc) {
        return ccValues.getOrDefault(cc, -1);
    }
}

