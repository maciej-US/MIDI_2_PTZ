package uci.bcd2ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import uci.bcd2ptz.midi.MidiDeviceInfo;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AppConfigManager {

    //Configuration management
    private AppConfig config;
    private final List<ConfigurationListener>listeners = new ArrayList<>();

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final File configFile;

    public AppConfigManager(String configPath) {

        this.configFile = new File(configPath);
    }

    public AppConfig load() throws IOException {
        this.config = MAPPER.readValue(configFile, AppConfig.class);
        return this.config;    }

    public void save(AppConfig config) throws IOException {

        MAPPER.writeValue(configFile, config);
    }

    public boolean exists() {

        return configFile.exists();
    }

    public AppConfig loadOrCreateDefault() throws IOException {

        if (!exists()) {
            AppConfig config = createDefaultConfig();
            save(config);
            return config;
        }

        return load();
    }

    public AppConfig createDefaultConfig() {

        AppConfig config = new AppConfig();
        config.setUser(new HashMap<>());
        config.setPresets(new HashMap<>());
        this.config = config;
        return config;
    }

    //--- Config update after changes
    public interface ConfigurationListener{
        void configurationChanged(AppConfig config);
    }

    public void addConfigurationListener(ConfigurationListener listener) {
        listeners.add(listener);
    }

    public void notifyConfigurationChanged() {

        System.out.println("CONFIG CHANGE NOTIFICATION");
        for (ConfigurationListener listener : listeners) {
            System.out.println("NOTIFY -> " + listener.getClass().getName());
            listener.configurationChanged(config);
        }
    }

    public AppConfig getConfig() {
        return this.config;
    }

    public static List<MidiDeviceInfo> getMidiDevices() {

        List<MidiDeviceInfo> result = new ArrayList<>();

        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

        for (int i = 0; i < infos.length; i++) {

            try {
                MidiDevice device = MidiSystem.getMidiDevice(infos[i]);

                result.add(new MidiDeviceInfo(infos[i].getName(), i, device.getMaxTransmitters(), device.getMaxReceivers()));

            } catch (MidiUnavailableException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static List<MidiDeviceInfo> getMidiInputs() {

        return getMidiDevices()
                .stream()
                .filter(d ->
                        d.getRxTransmitters() >= 0)
                .toList();
    }

    public static List<MidiDeviceInfo> getMidiOutputs() {

        return getMidiDevices()
                .stream()
                .filter(d ->
                        d.getTxReceivers() >= 0)
                .toList();
    }

}