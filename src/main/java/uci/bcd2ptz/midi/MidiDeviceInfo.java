package uci.bcd2ptz.midi;

import lombok.Getter;

@Getter
public class MidiDeviceInfo {

    private String deviceName;
    private int rxTransmitters;
    private int txReceivers;
    private int deviceIndex;

    public MidiDeviceInfo(
            String deviceName,
            int deviceIndex,
            int rxTransmitters,
            int txReceivers) {

        this.deviceName = deviceName;
        this.deviceIndex = deviceIndex;
        this.rxTransmitters = rxTransmitters;
        this.txReceivers = txReceivers;
    }

    @Override
    public String toString() {

        return deviceName
                + " ["
                + deviceIndex
                + "]";
    }

}