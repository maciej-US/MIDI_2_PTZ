package uci.bcd2ptz.gui;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import uci.bcd2ptz.AppConfig;
import uci.bcd2ptz.AppConfigManager;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.midi.MidiDeviceInfo;
import uci.bcd2ptz.visca.ViscaConfig;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

//Data management
public class SettingsDialog extends JDialog {

    //MIDI page
    private JComboBox<MidiDeviceInfo> inputComboBox;
    private JComboBox<MidiDeviceInfo> outputComboBox;
    private JTextField midiMappingFileField;

    //CAMERA PANEL
    private final Map<ControlCore.ControlPanelId, CameraPanelComponents> cameraPanels = new HashMap<>();

    private final Map<ControlCore.ControlPanelId, JLabel> cameraStatusLabels;
    private final Map<ControlCore.ControlPanelId, JButton> cameraTestButtons;

    private AppConfig appConfig;
    private final AppConfigManager configManager;
    private final PTZCameraManager cameraManager;
    private final MainGUI gui;

    private final Color okColor = new Color (0, 150, 0);
    private final Color errorColor = new Color (200, 0, 0);

    public SettingsDialog(
            JFrame owner,
            AppConfigManager appConfigManager,
            PTZCameraManager cameraManager,
            MainGUI gui) {


        super(owner, "Ustawienia", true);

        this.configManager = appConfigManager;
        this.appConfig = appConfigManager.getConfig();
        this.cameraManager = cameraManager;
        this.gui = gui;

        this.cameraStatusLabels = new HashMap<>();
        this.cameraTestButtons = new HashMap<>();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ogólne",createGeneralPanel());
        tabs.addTab("MIDI",createMidiPanel(appConfig));
        tabs.addTab("Kamery",createCameraPanel(appConfig));

        add(tabs, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        setSize(700, 600);
        setLocationRelativeTo(owner);
    }

    private JPanel createGeneralPanel() {

        JPanel panel = new JPanel(
                new BorderLayout()
        );

        panel.add(
                new JLabel(
                        "Ustawienia ogólne systemu będą dostępne w kolejnych wersjach.",
                        SwingConstants.CENTER
                ),
                BorderLayout.CENTER
        );

        return panel;
    }

    private JPanel createMidiPanel(AppConfig config) {

        JPanel panel = new JPanel( new GridBagLayout());
        GridBagConstraints midiGBC = new GridBagConstraints();

        midiGBC.insets = new Insets(3,5,3,5);
        midiGBC.anchor = GridBagConstraints.WEST;

        AppConfig.MidiConfig midi = config.getSystem().getMidi();

        //--- MIDI INPUT
        DefaultComboBoxModel<MidiDeviceInfo> inputModel = new DefaultComboBoxModel<>();
        for (MidiDeviceInfo device : AppConfigManager.getMidiInputs()) {
            if (device.getRxTransmitters() >= 0) {
                inputModel.addElement(device);
            }
        }
        inputComboBox = new JComboBox<>(inputModel);

        //--- MIDI OUTPUT
        DefaultComboBoxModel<MidiDeviceInfo> outputModel = new DefaultComboBoxModel<>();
        for (MidiDeviceInfo device : AppConfigManager.getMidiOutputs()) {
            if (device.getTxReceivers() >= 0) {
                outputModel.addElement(device);
            }
        }
        outputComboBox = new JComboBox<>(outputModel);

        midiMappingFileField = new JTextField(config.getSystem().getMidi().getInput().getConfigFile());
        JButton browseButton = new JButton("Przeglądaj...");
        browseButton.addActionListener(e -> {
           JFileChooser chooser = new JFileChooser();
           chooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
           if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION){
               midiMappingFileField.setText(chooser.getSelectedFile().getAbsolutePath());
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
        applyButton.addActionListener(e -> applyConfiguration());

        JButton saveButton = new JButton("Zapisz");
        saveButton.addActionListener(e -> saveConfiguration());

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

        CameraPanelComponents components = new CameraPanelComponents();


        // ==== COMPONENTS
        // --- Description
        String description = cameraConfig != null? cameraConfig.getDisplayName() : "";
        components.descriptionField = new JTextField(description, 30);

       // --- IP + port
        String ip = cameraConfig != null? cameraConfig.getIpAddress() : "";
        JTextField ipField =  new JTextField(ip, 15);
        components.ipField = ipField;

        String port = cameraConfig != null ? String.valueOf(cameraConfig.getPort()) : "";
        JTextField portField = new JTextField(port, 5);
        components.portField = portField;

        components.ipField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String ip = components.ipField.getText().trim();
                if (ip.isEmpty()) {
                    return;
                }

                if (!isValidIpAddress(ip)) {
                    components.ipField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                } else {
                    components.ipField.setBorder(components.portField.getBorder());
                    if (components.portField.getText().isBlank()) {
                        components.portField.setText("52381");
                    }
                }
            }
        });

        // --- MIDI assignemtn
        components.midiPanelCombo = new JComboBox<>(new String[]{"","A","B","C","D"});



        // Konfiguracja + Browse
        components.configField = new JTextField();
        components.configField.setEditable(false);

        if (cameraConfig != null ){
            PTZCamera camera = cameraManager.getCamera("CAM_" + panelId.name());
            if (camera != null  && camera.getModel() != null){
                components.configField.setText( camera.getModel());
            }
        }else {
            components.configField.setText("");
        }

        components.browseButton = new JButton("Wczytaj");
        components.browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("JSON Files","json"));

            if (chooser.showOpenDialog(SettingsDialog.this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                components.configPath = selectedFile.getAbsolutePath();
                try{
                    ObjectMapper viscaMapper = new ObjectMapper();
                    ViscaConfig config = viscaMapper.readValue (selectedFile, ViscaConfig.class);
                    components.configField.setText(config.getCameraName()
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

                if(!isValidIpAddress(components.ipField.getText().trim())){
                    statusValue.setText("BŁAD IP");
                    statusValue.setForeground(errorColor);
                    return;
                }
                //DEBUG
                if(components.configPath == null || components.configPath.isBlank()){
                    statusValue.setText("BŁAD CFG");
                    statusValue.setForeground(errorColor);
                    return;
                }
                online = cameraManager.testConnection(panelId,
                        components.ipField.getText().trim(),
                        Integer.parseInt(components.portField.getText().trim()),
                        components.configPath);
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
        leftPanel.add(components.descriptionField,gbc);

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
        leftPanel.add(components.midiPanelCombo, gbc);

        //--- Config + browser
        gbc.gridy = 2;
        gbc.gridx = 0;
        leftPanel.add(new JLabel("Konfiguracja:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        leftPanel.add(components.configField, gbc);

        gbc.gridx = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        leftPanel.add(components.browseButton, gbc);

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

        components.rootPanel = cameraPanel;
        components.statusLabel = statusValue;
        components.testButton = testButton;

        return components;
    }

    //==============================
    //   BUTTONS HANDLERS
    //==============================

    private void applyConfiguration() {

        //=== MIDI
        MidiDeviceInfo input = (MidiDeviceInfo) inputComboBox.getSelectedItem();
        MidiDeviceInfo output = (MidiDeviceInfo) outputComboBox.getSelectedItem();
        if (input != null) {
            appConfig.getSystem().getMidi().getInput().setDeviceName(input.getDeviceName());
            appConfig.getSystem().getMidi().getInput().setDeviceIndex(input.getDeviceIndex());
        }

        if (output != null) {
            appConfig.getSystem().getMidi().getOutput().setDeviceName(output.getDeviceName());
            appConfig.getSystem().getMidi().getOutput().setDeviceIndex(output.getDeviceIndex());
        }

        String configFile = midiMappingFileField.getText();

        appConfig.getSystem().getMidi().getInput().setConfigFile(configFile);
        appConfig.getSystem().getMidi().getOutput().setConfigFile(configFile);

        // ===== CAMERAS



        configManager.notifyConfigurationChanged();
        System.out.println("Configuration applied.");
    }

    private void saveConfiguration() {

        applyConfiguration();
        try {
            configManager.save(appConfig);
            configManager.notifyConfigurationChanged();
            dispose();
            //JOptionPane.showMessageDialog(this, "Konfiguracja zapisana.","Ustawienia",JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Błąd zapisu konfiguracji:\n"+ ex.getMessage(),"Błąd",JOptionPane.ERROR_MESSAGE);
        }

    }


    //===============================
    //       HELPERS, RENDERS
    //===============================

    private void refreshCameraStatuses() {

        for (ControlCore.ControlPanelId panelId : cameraPanels.keySet()) {

            CameraPanelComponents panel = cameraPanels.get(panelId);

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

}