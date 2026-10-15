package uci.midi2ptz.gui;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.ControlCore;
import uci.midi2ptz.ptz.PTZCamera;
import uci.midi2ptz.ptz.PTZCameraManager;
import uci.midi2ptz.midi.MidiDeviceInfo;
import uci.midi2ptz.visca.ViscaConfig;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static uci.midi2ptz.core.ControlCore.LOGGER;

//Data management
public class SettingsDialog extends JDialog {

    //MIDI page
    private JComboBox<MidiDeviceInfo> inputComboBox;
    private JComboBox<MidiDeviceInfo> outputComboBox;
    private JTextField midiMappingFileField;


    //CAMERA page
    private final Map<ControlCore.ControlPanelId, CameraPanelComponents> cameraPanels = new LinkedHashMap<>();

    private final Map<ControlCore.ControlPanelId, JLabel> cameraStatusLabels;
    private final Map<ControlCore.ControlPanelId, JButton> cameraTestButtons;

    //GENERAL page
    GeneralPanelComponents systemPanelComponents = new GeneralPanelComponents();

    private final AppConfigManager configManager;
    private final PTZCameraManager cameraManager;
    private final MainGUI gui;

    private final Color okColor = new Color (0, 150, 0);
    private final Color errorColor = new Color (200, 0, 0);

    public SettingsDialog(JFrame owner, AppConfigManager appConfigManager,
                          PTZCameraManager cameraManager, MainGUI gui) {


        super(owner, "Ustawienia", true);

        this.configManager = appConfigManager;
        this.cameraManager = cameraManager;
        this.gui = gui;

        this.cameraStatusLabels = new HashMap<>();
        this.cameraTestButtons = new HashMap<>();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Kamery",createCameraPanel(appConfigManager.getConfig()));
        tabs.addTab("System",createGeneralPanel());
        tabs.addTab("MIDI",createMidiPanel(appConfigManager.getConfig()));

        add(tabs, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        setSize(700, 600);
        setLocationRelativeTo(owner);
    }

    private JPanel createGeneralPanel() {

        JPanel generalPanel = new JPanel();

        JPanel transportPanel = new JPanel(new GridBagLayout());
        GridBagConstraints transportGbc = new GridBagConstraints();
        transportGbc.insets = new Insets(2,3,2,3);


        String localAutoIP = "";
        try {
            localAutoIP = InetAddress.getLocalHost().getHostAddress();
        }catch (Exception e){
            LOGGER.warning("No active network connection");
        }

        systemPanelComponents = new GeneralPanelComponents();
        systemPanelComponents.autoIpCheckBox = new JCheckBox("IP Auto");
        systemPanelComponents.localIpField = new JTextField(15);
        systemPanelComponents.localPortField = new JTextField(6);

        Map<String, Object> transportConfig = configManager.getConfig().getSystem().getTransport();
        boolean autoIP = Boolean.TRUE.equals(transportConfig.get("autoIp"));
        systemPanelComponents.autoIpCheckBox.setSelected(autoIP);
        String finalLocalAutoIP = localAutoIP;
        systemPanelComponents.autoIpCheckBox.addActionListener(e -> {
            boolean autoIp = systemPanelComponents.autoIpCheckBox.isSelected();
            systemPanelComponents.localIpField.setEnabled(!autoIp);
            if (autoIp) {
                systemPanelComponents.localIpField.setText(finalLocalAutoIP);
            }

        });

        systemPanelComponents.localIpField.setText(String.valueOf(transportConfig.getOrDefault("localIp", "")));
        systemPanelComponents.localIpField.setEnabled(!systemPanelComponents.autoIpCheckBox.isSelected());
        if (systemPanelComponents.autoIpCheckBox.isSelected()){
            systemPanelComponents.localIpField.setText(localAutoIP);
        }

        systemPanelComponents.localPortField.setText(String.valueOf(transportConfig.getOrDefault("localPort", "52381")));

        transportGbc.gridx = 0;
        transportGbc.gridy = 0;
        transportGbc.gridwidth = 2;
        transportPanel.add(systemPanelComponents.autoIpCheckBox, transportGbc);

        transportGbc.gridwidth = 1;

        transportGbc.gridx = 0;
        transportGbc.gridy = 1;
        transportGbc.anchor = GridBagConstraints.EAST;
        transportPanel.add(new JLabel("Adres IP aplikacji:"), transportGbc);

        transportGbc.gridx = 1;
        transportGbc.anchor = GridBagConstraints.WEST;
        transportPanel.add(systemPanelComponents.localIpField, transportGbc);

        transportGbc.gridx = 0;
        transportGbc.gridy = 2;
        transportGbc.anchor = GridBagConstraints.EAST;
        transportPanel.add(new JLabel("Port:"), transportGbc);

        transportGbc.gridx = 1;
        transportGbc.anchor = GridBagConstraints.WEST;
        transportPanel.add(systemPanelComponents.localPortField, transportGbc);

        generalPanel.add(transportPanel, BorderLayout.NORTH);

        return generalPanel;
    }

    private JPanel createMidiPanel(AppConfig config) {

        JPanel panel = new JPanel( new GridBagLayout());
        GridBagConstraints midiGBC = new GridBagConstraints();

        midiGBC.insets = new Insets(3,5,3,5);
        midiGBC.anchor = GridBagConstraints.WEST;

        AppConfig.MidiConfig midi = config.getSystem().getMidi();

        //--- MIDI INPUT
        DefaultComboBoxModel<MidiDeviceInfo> inputModel = new DefaultComboBoxModel<>();
        inputModel.addElement(null);
        for (MidiDeviceInfo device : AppConfigManager.getMidiInputs()) {
            if (device.getRxTransmitters() >= 0) {
                inputModel.addElement(device);
            }
        }
        inputComboBox = new JComboBox<>(inputModel);
        String configuredInput =
                midi.getInput().getDeviceName();

        if (configuredInput != null) {
            for (int i = 0; i < inputComboBox.getItemCount(); i++) {
                MidiDeviceInfo device = inputComboBox.getItemAt(i);
                if (device != null && configuredInput.equals(device.getDeviceName())) {
                    inputComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }

        //--- MIDI OUTPUT
        DefaultComboBoxModel<MidiDeviceInfo> outputModel = new DefaultComboBoxModel<>();
        outputModel.addElement(null);
        for (MidiDeviceInfo device : AppConfigManager.getMidiOutputs()) {
            if (device.getTxReceivers() >= 0) {
                outputModel.addElement(device);
            }
        }
        outputComboBox = new JComboBox<>(outputModel);
        String configuredOutput = midi.getOutput().getDeviceName();
        if (configuredOutput != null) {
            for (int i = 0; i < outputComboBox.getItemCount(); i++) {
                MidiDeviceInfo device = outputComboBox.getItemAt(i);
                if (device != null && configuredOutput.equals(device.getDeviceName())) {
                    outputComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }

        midiMappingFileField = new JTextField(config.getSystem().getMidi().getInput().getConfigFile());

        JButton browseButton = new JButton("Przeglądaj...");
        browseButton.addActionListener(e -> {
            File configPath = new File("config");
            if (!configPath.exists()) {
                configPath.mkdirs();
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(configPath);
            chooser.setFileFilter(new FileNameExtensionFilter("JSON Files","json"));

            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();

                try {
                    Path configDir = Paths.get("config");
                    Files.createDirectories(configDir);
                    Path targetFile =configDir.resolve(selectedFile.getName());

                    Files.copy(selectedFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                    midiMappingFileField.setText("config/"+ selectedFile.getName());

                } catch (IOException ex) {

                    JOptionPane.showMessageDialog(
                            panel,
                            "Nie udało się skopiować pliku konfiguracji MIDI.",
                            "Błąd",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        //======== LAYOUT COMPOSITION
        //--- Input device
        midiGBC.gridx = 0;
        midiGBC.gridy = 0;
        panel.add(new JLabel("Urządzenie wejściowe MIDI: "), midiGBC);

        midiGBC.gridx = 1;
        midiGBC.weightx = 1.0;
        midiGBC.fill = GridBagConstraints.HORIZONTAL;
        panel.add(inputComboBox, midiGBC);

        //--- Input device
        midiGBC.gridx = 0;
        midiGBC.gridy = 1;
        panel.add(new JLabel("Urządzenie wyjściowe MIDI:"), midiGBC);

        midiGBC.gridx = 1;
        midiGBC.weightx = 1.0;
        midiGBC.fill = GridBagConstraints.HORIZONTAL;
        panel.add(outputComboBox, midiGBC);

        //---- Mapping file
        //--- Input device
        midiGBC.gridx = 0;
        midiGBC.gridy = 2;
        panel.add(new JLabel("KOnfiguracja mapowania MIDI:"), midiGBC);

        midiGBC.gridx = 1;
        midiGBC.weightx = 1.0;
        midiGBC.fill = GridBagConstraints.HORIZONTAL;
        panel.add(midiMappingFileField, midiGBC);

        midiGBC.gridx = 2;
        midiGBC.weightx = 0;
        midiGBC.fill = GridBagConstraints.NONE;
        panel.add(browseButton, midiGBC);

        return panel;
    }

    private JPanel createCameraPanel(AppConfig config) {

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS)
        );

        for (ControlCore.ControlPanelId panelId : ControlCore.ControlPanelId.values()) {
            AppConfig.CameraConfig cameraConfig = null;

            for (AppConfig.CameraConfig camera : config.getSystem().getCameras()) {
                if (panelId.name().equals(camera.getAssignedGuiPanel())) {
                    cameraConfig = camera;
                    break;
                }
            }

            CameraPanelComponents panel = createCameraConfigPanel(panelId, cameraConfig);

            cameraPanels.put(panelId, panel);

            container.add(panel.getRootPanel());

            container.add(Box.createVerticalStrut(5));
        }

        JPanel wrapper =  new JPanel(new BorderLayout());

        wrapper.add(new JScrollPane(container),BorderLayout.CENTER);

        refreshCameraStatuses();

        return wrapper;
    }

    private JPanel createButtonPanel() {

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton applyButton = new JButton("Zastosuj");
        applyButton.addActionListener(e -> {
            updateConfiguration();
        });

        JButton saveButton = new JButton("Zapisz");
        saveButton.addActionListener(e -> {
            updateConfiguration();
            gui.saveConfiguration(configManager.getConfig());
            dispose();
        });

        JButton cancelButton = new JButton("Anuluj");
        cancelButton.addActionListener(e -> dispose());

        panel.add(applyButton);
        panel.add(saveButton);
        panel.add(cancelButton);

        return panel;
    }

    private CameraPanelComponents createCameraConfigPanel(ControlCore.ControlPanelId panelId, AppConfig.CameraConfig cameraConfig) {

        JPanel leftPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        CameraPanelComponents cameraPanelComponents = new CameraPanelComponents();

        // ==== COMPONENTS
        // --- Description
        String description = cameraConfig != null? cameraConfig.getDisplayName() : "";
        cameraPanelComponents.descriptionField = new JTextField(description, 30);

       // --- IP + port
        String ip = cameraConfig != null? cameraConfig.getIpAddress() : "";
        JTextField ipField =  new JTextField(ip, 15);
        cameraPanelComponents.ipField = ipField;

        String port = cameraConfig != null ? String.valueOf(cameraConfig.getPort()) : "";
        JTextField portField = new JTextField(port, 5);
        cameraPanelComponents.portField = portField;

        cameraPanelComponents.ipField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String ip = cameraPanelComponents.ipField.getText().trim();
                if (ip.isEmpty()) {
                    return;
                }

                if (!isValidIpAddress(ip)) {
                    cameraPanelComponents.ipField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                } else {
                    cameraPanelComponents.ipField.setBorder(UIManager.getBorder("TextField.border"));
                    if (cameraPanelComponents.portField.getText().isBlank()) {
                        cameraPanelComponents.portField.setText("52381");
                    }
                }
            }
        });

        // --- MIDI assignemtn
        cameraPanelComponents.midiPanelCombo = new JComboBox<>(new String[]{"", "A", "B"});
        cameraPanelComponents.midiPanelCombo.addActionListener(e -> {
            String selected = (String) cameraPanelComponents.midiPanelCombo.getSelectedItem();

            if (selected == null || selected.isBlank()) {
                return;
            }

            for (CameraPanelComponents other : cameraPanels.values()) {
                if (other == cameraPanelComponents) {
                    continue;
                }

                Object otherValue =other.midiPanelCombo.getSelectedItem();
                if (selected.equals(otherValue)) {
                    other.midiPanelCombo.setSelectedItem("");
                }
            }
        });

        if (cameraConfig != null && cameraConfig.getAssignedMidiPanel() != null) {
            cameraPanelComponents.midiPanelCombo.setSelectedItem(cameraConfig.getAssignedMidiPanel());
        }

        // Konfiguracja + Browse
        cameraPanelComponents.configField = new JTextField();
        cameraPanelComponents.configField.setEditable(false);

        if (cameraConfig != null ){
            PTZCamera camera = cameraManager.getCameras().get("CAM_" + panelId.name());
            if (camera != null  && camera.getModel() != null){
                cameraPanelComponents.configField.setText(camera.getModel());
                cameraPanelComponents.configPath = configManager.getConfig().getSystem().getCameras().stream()
                        .filter(cam -> cam.getAssignedGuiPanel().equals(panelId.name()))
                        .findFirst()
                        .map(AppConfig.CameraConfig::getViscaDefinition)
                        .orElse(null);
            }
        }else {
            cameraPanelComponents.configField.setText("");
        }

        cameraPanelComponents.browseButton = new JButton("Wczytaj");
        cameraPanelComponents.browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            File configDir = new File("config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            chooser.setCurrentDirectory(configDir);
            chooser.setFileFilter(new FileNameExtensionFilter("JSON Files","json"));

            if (chooser.showOpenDialog(SettingsDialog.this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                cameraPanelComponents.configPath = selectedFile.getAbsolutePath();
                try{
                    ObjectMapper viscaMapper = new ObjectMapper();
                    ViscaConfig config = viscaMapper.readValue (selectedFile, ViscaConfig.class);

                    Path configPath = Paths.get("config");
                    Files.createDirectories(configPath);
                    Path targetFile = configPath.resolve(selectedFile.getName());
                    Files.copy(selectedFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);

                    cameraPanelComponents.configPath = "config/" + selectedFile.getName();
                    cameraPanelComponents.configField.setText(config.getCameraName()
                    );
                }catch (Exception ex){
                    JOptionPane.showMessageDialog(
                            SettingsDialog.this,
                            "Błąd wczytania konfiguracji kamery",
                            "Błąd",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });


        //status panel
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.insets = new Insets(3, 0, 3, 0);

        Dimension prefRightPanel = rightPanel.getPreferredSize();
        rightPanel.setPreferredSize(new Dimension(120, prefRightPanel.height));

        JLabel statusTitle = new JLabel("STATUS");

        JLabel statusValue = new JLabel(" ");
        cameraStatusLabels.put(panelId, statusValue);
        Font baseFont = statusValue.getFont();
        statusValue.setFont(baseFont.deriveFont(Font.BOLD));
        statusValue.setFont(baseFont.deriveFont(baseFont.getSize2D() * 1.3f));

        JButton testButton = new JButton("TEST");
        testButton.addActionListener(e -> {

            boolean online;
            PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);

            if (camera != null) {
                online = cameraManager.refreshConnection(panelId);
            }else{

                if(!isValidIpAddress(cameraPanelComponents.ipField.getText().trim())){
                    statusValue.setText("BŁAD IP");
                    statusValue.setForeground(errorColor);
                    return;
                }
                //DEBUG
                if(cameraPanelComponents.configPath == null || cameraPanelComponents.configPath.isBlank()){
                    statusValue.setText("BŁAD CFG");
                    statusValue.setForeground(errorColor);
                    return;
                }
                online = cameraManager.testConnection(
                        cameraPanelComponents.ipField.getText().trim(),
                        Integer.parseInt(cameraPanelComponents.portField.getText().trim()),
                        cameraPanelComponents.configPath);
            }

            if (online){
                statusValue.setText("ONLINE");
                statusValue.setForeground(okColor);
            }else{
                statusValue.setText("OFFLINE");
                statusValue.setForeground(errorColor);
            }
            gui.refreshStatusInfo(panelId);
            //debug:
            //System.out.println("Refresh panel " + panelId + " CAM STATUS ONLINE: " + online);
        });

        // ======== LAYOUT
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Opis
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        leftPanel.add( new JLabel("Opis:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 5;
        leftPanel.add(cameraPanelComponents.descriptionField,gbc);

        //--- IP + port
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;

        gbc.gridx = 0;
        leftPanel.add(new JLabel("Adres IP:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        leftPanel.add(ipField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        leftPanel.add(new JLabel("Port:"), gbc);

        //-- add portField
        gbc.gridx = 3;
        leftPanel.add(portField, gbc);

        //--- Midi
        gbc.gridx = 4;
        leftPanel.add(new JLabel("Panel MIDI:"), gbc);
        gbc.gridx = 5;
        leftPanel.add(cameraPanelComponents.midiPanelCombo, gbc);

        //--- Config + browser
        gbc.gridy = 2;
        gbc.gridx = 0;
        leftPanel.add(new JLabel("Konfiguracja:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        leftPanel.add(cameraPanelComponents.configField, gbc);

        gbc.gridx = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        leftPanel.add(cameraPanelComponents.browseButton, gbc);

        //--- Status


        cameraTestButtons.put(panelId, testButton);

        rightGbc.gridx=0;
        rightGbc.gridy=0;
        rightPanel.add(statusTitle, rightGbc);
        rightGbc.gridy=1;
        rightPanel.add(statusValue, rightGbc);
        rightGbc.gridy=2;
        rightPanel.add(testButton, rightGbc);


        JPanel cameraPanel = new JPanel(new BorderLayout());
        cameraPanel.setBorder(BorderFactory.createTitledBorder("Kamera " + panelId.name()));
        cameraPanel.add(leftPanel, BorderLayout.CENTER);
        cameraPanel.add(rightPanel, BorderLayout.EAST);

        cameraPanelComponents.rootPanel = cameraPanel;
        cameraPanelComponents.statusLabel = statusValue;
        cameraPanelComponents.testButton = testButton;

        return cameraPanelComponents;
    }

    //==============================
    //   BUTTONS HANDLERS
    //==============================

    private AppConfig buildConfiguration(){

        ObjectMapper mapper = new ObjectMapper();
        AppConfig config = mapper.convertValue(configManager.getConfig(), AppConfig.class);

        //SYSTEM TAB

        Map<String, Object> transport = config.getSystem().getTransport();
        transport.put("autoIp", systemPanelComponents.autoIpCheckBox.isSelected());
        transport.put("localIp", systemPanelComponents.localIpField.getText().trim());
        transport.put("localPort", Integer.parseInt(systemPanelComponents.localPortField.getText().trim()));
        transport.put("socketTimeout", 150);

        //CAMERAS TAB
        config.getSystem().getCameras().clear();
        for (Map.Entry<ControlCore.ControlPanelId, CameraPanelComponents> entry : cameraPanels.entrySet()) {
            String camIp = entry.getValue().ipField.getText().trim();
            if (camIp.isBlank()){
                continue;
            }
            AppConfig.CameraConfig cameraConfig = new AppConfig.CameraConfig();
            cameraConfig.setCameraId("CAM_" + entry.getKey().name());
            cameraConfig.setDisplayName(entry.getValue().descriptionField.getText().trim());
            cameraConfig.setIpAddress(entry.getValue().ipField.getText().trim());
            cameraConfig.setPort(Integer.parseInt(entry.getValue().portField.getText().trim()));
            cameraConfig.setAssignedGuiPanel(entry.getKey().name());
            String midiPanel = (String) entry.getValue().midiPanelCombo.getSelectedItem();
            cameraConfig.setAssignedMidiPanel(midiPanel== null || midiPanel.isBlank() ? null : midiPanel);
            cameraConfig.setViscaDefinition(entry.getValue().configPath);

            config.getSystem().getCameras().add(cameraConfig);
        }

        //MIDI TAB
        MidiDeviceInfo input = (MidiDeviceInfo) inputComboBox.getSelectedItem();
        if (input != null) {
            config.getSystem().getMidi().getInput().setDeviceName(input.getDeviceName());
            config.getSystem().getMidi().getInput().setDeviceIndex(input.getDeviceIndex());
        } else {
            config.getSystem().getMidi().getInput().setDeviceName(null);
            config.getSystem().getMidi().getInput().setDeviceIndex(-1);
        }

        MidiDeviceInfo output = (MidiDeviceInfo) outputComboBox.getSelectedItem();
        if (output != null) {
            config.getSystem().getMidi().getOutput().setDeviceName(output.getDeviceName());
            config.getSystem().getMidi().getOutput().setDeviceIndex(output.getDeviceIndex());
        } else {
            config.getSystem().getMidi().getOutput().setDeviceName(null);
            config.getSystem().getMidi().getOutput().setDeviceIndex(-1);
        }

        String configFile = midiMappingFileField.getText();

        config.getSystem().getMidi().getInput().setConfigFile(configFile);
        config.getSystem().getMidi().getOutput().setConfigFile(configFile);

        return config;

    }

    private void updateConfiguration() {

        if (!validateConfigurationForm()){
            return;
        }

        AppConfig config = buildConfiguration();

        AppConfigManager.UpdateMode mode = gui.isFirstLaunch() ? AppConfigManager.UpdateMode.INITIAL_COMMIT : AppConfigManager.UpdateMode.COMMIT;

        ConfigurationUpdateResults result = configManager.updateConfiguration(config, mode);

        if (!result.getErrors().isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    gui.formatErrors(result),
                    "Błąd konfiguracji",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        gui.setFirstLaunch(false);
        //gui.reloadConfiguration();
    }

    private boolean validateConfigurationForm() {

        boolean valid = true;

        // ===== TRANSPORT
        if (!systemPanelComponents.autoIpCheckBox.isSelected()) {

            String ip = systemPanelComponents.localIpField.getText().trim();
            systemPanelComponents.localIpField.setBorder(UIManager.getBorder("TextField.border"));

            if (!isValidIpAddress(ip)) {
                systemPanelComponents.localIpField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                valid = false;
            }
        }

        try {
            int port = Integer.parseInt(systemPanelComponents.localPortField.getText().trim());
            if (port < 1 || port > 65535) {
                systemPanelComponents.localPortField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                valid = false;
            }

        } catch (Exception e) {
            valid = false;
        }

        // ===== MIDI
        if (midiMappingFileField.getText().trim().isBlank()) {
            midiMappingFileField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
            valid = false;
        }

        // ===== CAMERAS
        for (CameraPanelComponents panel : cameraPanels.values()) {
            String ip = panel.ipField.getText().trim();
            if (ip.isBlank()) {
                continue;
            }

            if (!isValidIpAddress(ip)) {
                panel.ipField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                valid = false;
            }

            if (panel.configPath == null || panel.configPath.isBlank()) {
                panel.configField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                valid = false;
            }

            try {

                int port = Integer.parseInt(panel.portField.getText().trim());

                if (port < 1 || port > 65535) {
                    panel.portField.setBorder(BorderFactory.createLineBorder(Color.RED,2));
                    valid = false;
                }

            } catch (Exception e) {
                panel.portField.setBorder(BorderFactory.createLineBorder(Color.RED,2));
                valid = false;
            }
        }
        return valid;
    }

    //===============================
    //       HELPERS, RENDERS
    //===============================

    private void refreshCameraStatuses() {

        for (ControlCore.ControlPanelId panelId : cameraPanels.keySet()) {

            CameraPanelComponents panel = cameraPanels.get(panelId);

            PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);
            if (camera == null){
                panel.statusLabel.setText("------");
                panel.statusLabel.setForeground(Color.GRAY);
                continue;
            }
            boolean online = cameraManager.isCameraOnline(panelId);

            if (online) {
                panel.statusLabel.setText("ONLINE");
                panel.statusLabel.setForeground(okColor);

            } else {
                panel.statusLabel.setText("OFFLINE");
                panel.statusLabel.setForeground(errorColor);
            }
        }
    }

    private boolean isValidIpAddress(String ip) {

        if (ip == null || ip.isBlank()) {
            return false;
        }

        String[] octets = ip.trim().split("\\.");

        if (octets.length != 4) {
            return false;
        }

        try {
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    //==============================
    //      PRIVATE CLASSES
    //==============================
    @Data
    private static class CameraPanelComponents {

        JPanel rootPanel;

        JTextField descriptionField;
        JTextField ipField;
        JTextField portField;

        JComboBox<String> midiPanelCombo;

        String configPath;
        JTextField configField;
        JButton browseButton;

        JLabel statusLabel;

        JButton testButton;

        ControlCore.ControlPanelId panelId;
    }

    @Data
    private static class GeneralPanelComponents{
        JCheckBox autoIpCheckBox;
        JTextField localIpField;
        JTextField localPortField;
    }



}