package uci.bcd2ptz.midi;

public class MidiController {

    private final MidiEventRouter input;

    private final MidiOutputController output;

    private final MidiControllerConfig config;

    public MidiController(
            String configFile,
            int inputIndex,
            int outputIndex) {

        //Device configuration and connection
        this.config = new MidiControllerConfig(configFile);
        this.input = MidiEventRouter.getInstance(inputIndex,outputIndex);
        this.input.setOutputMode(MidiEventRouter.OutputMode.PARSED);
        this.output = MidiOutputController.getInstance(outputIndex);
    }

    public MidiEventRouter getInput() {
        return input;
    }

    public MidiOutputController getOutput() {
        return output;
    }

    public MidiControllerConfig getConfig() {
        return config;
    }
}