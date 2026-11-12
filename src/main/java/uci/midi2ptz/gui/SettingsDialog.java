package uci.midi2ptz.gui;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.MainControlCore;
import uci.midi2ptz.midi.MidiControllerManager;
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

import static uci.midi2ptz.core.MainControlCore.LOGGER;

/**
 * Application configuration editor dialog.
 *
 * <p>Provides configuration of:</p>
 *
 * <ul>
 *     <li>PTZ cameras,</li>
 *     <li>MIDI controller transport,</li>
 *     <li>network transport settings.</li>
 * </ul>
 *
 * <p>The dialog edits an isolated configuration model and delegates validation and runtime reconfiguration to {@link AppConfigManager}.</p>
 */
public class SettingsDialog extends JDialog {

    //MIDI page
    private JRadioButton generalMidiRadio;
    private JRadioButton usbDirectRadio;
    private JComboBox<MidiDeviceInfo> inputComboBox;
    private JComboBox<MidiDeviceInfo> outputComboBox;
    private JTextField midiMappingFileField;
    private JComboBox<MidiControllerManager.UsbMidiDeviceInfo>usbDeviceComboBox;
    private JLabel usbInterfaceDisplayInfo;

    //* Hidden transport metadata used when building USB_DIRECT configuration.
    private JLabel usbVidValue;
    private JLabel usbPidValue;
    private JLabel usbInterfaceValue;


    //CAMERA page
    private final Map<MainControlCore.ControlPanelId, CameraPanelComponents> cameraPanels = new LinkedHashMap<>();

    private final Map<MainControlCore.ControlPanelId, JLabel> cameraStatusLabels;
    private final Map<MainControlCore.ControlPanelId, JButton> cameraTestButtons;

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

        JPanel generalPanel =new JPanel(new BorderLayout());

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

        AppConfig config = configManager.getConfig();
        boolean autoIP = config.getSystem().getTransport().isAutoIp();
        systemPanelComponents.autoIpCheckBox.setSelected(autoIP);
        String finalLocalAutoIP = localAutoIP;
        systemPanelComponents.autoIpCheckBox.addActionListener(e -> {
            boolean autoIp = systemPanelComponents.autoIpCheckBox.isSelected();
            systemPanelComponents.localIpField.setEnabled(!autoIp);
            if (autoIp) {
                systemPanelComponents.localIpField.setText(finalLocalAutoIP);
            }

        });

        String localIp = config.getSystem().getTransport().getLocalIp();
        if (localIp == null || localIp.isBlank()) {
            localIp = "";
        }
        systemPanelComponents.localIpField.setText(localIp);
        systemPanelComponents.localIpField.setEnabled(!systemPanelComponents.autoIpCheckBox.isSelected());
        if (systemPanelComponents.autoIpCheckBox.isSelected()){
            systemPanelComponents.localIpField.setText(localAutoIP);
        }

        int localPort = config.getSystem().getTransport().getLocalPort();
        if (localPort < 0 || localPort > 65535) {
            localPort = 52381;
        }
        systemPanelComponents.localPortField.setText(String.valueOf(localPort));

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

    /**
     * Creates MIDI transport configuration page.
     * <p>Supports both GENERAL_MIDI and USB_DIRECT transport backends.</p>
     */
    private JPanel createMidiPanel(AppConfig config) {

        JPanel panel = new JPanel( new GridBagLayout());
        GridBagConstraints midiGBC = new GridBagConstraints();

        midiGBC.insets = new Insets(3,5,3,5);
        midiGBC.anchor = GridBagConstraints.WEST;

        AppConfig.MidiConfig midi = config.getSystem().getMidi();

        generalMidiRadio = new JRadioButton("General MIDI");
        usbDirectRadio = new JRadioButton("USB Direct");
        ButtonGroup transportGroup = new ButtonGroup();
        transportGroup.add(generalMidiRadio);
        transportGroup.add(usbDirectRadio);

        if (midi.getMidiTransport() == AppConfig.MidiTransport.USB_DIRECT) {
            usbDirectRadio.setSelected(true);
        } else {
            generalMidiRadio.setSelected(true);
        }

        //--- MIDI INPUT
        DefaultComboBoxModel<MidiDeviceInfo> inputModel = new DefaultComboBoxModel<>();
        inputModel.addElement(null);
        for (MidiDeviceInfo device : MidiControllerManager.getMidiInputs()) {
            if (device.rxTransmitters() >= 0) {
                inputModel.addElement(device);
            }
        }
        inputComboBox = new JComboBox<>(inputModel);
        String configuredInput =midi.getJavaMidi().getInputDeviceName();

        if (configuredInput != null) {
            for (int i = 0; i < inputComboBox.getItemCount(); i++) {
                MidiDeviceInfo device = inputComboBox.getItemAt(i);
                if (device != null && configuredInput.equals(device.deviceName())) {
                    inputComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }

        //--- MIDI OUTPUT
        DefaultComboBoxModel<MidiDeviceInfo> outputModel = new DefaultComboBoxModel<>();
        outputModel.addElement(null);
        for (MidiDeviceInfo device : MidiControllerManager.getMidiOutputs()) {
            if (device.txReceivers() >= 0) {
                outputModel.addElement(device);
            }
        }
        outputComboBox = new JComboBox<>(outputModel);
        String configuredOutput = midi.getJavaMidi().getOutputDeviceName();
        if (configuredOutput != null) {
            for (int i = 0; i < outputComboBox.getItemCount(); i++) {
                MidiDeviceInfo device = outputComboBox.getItemAt(i);
                if (device != null && configuredOutput.equals(device.deviceName())) {
                    outputComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }

        //USB-DIRECT
        DefaultComboBoxModel<MidiControllerManager.UsbMidiDeviceInfo> usbModel = new DefaultComboBoxModel<>();

        for (MidiControllerManager.UsbMidiDeviceInfo device : MidiControllerManager.getUsbMidiDevices()) {
            usbModel.addElement(device);
        }

        usbDeviceComboBox = new JComboBox<>(usbModel);

        for (int i = 0; i < usbDeviceComboBox.getItemCount(); i++) {
            MidiControllerManager.UsbMidiDeviceInfo device = usbDeviceComboBox.getItemAt(i);

            if (device.vid() == midi.getUsbDirect().getVid() && device.pid() == midi.getUsbDirect().getPid()) {
                usbDeviceComboBox.setSelectedIndex(i);
                break;
            }
        }

        usbVidValue = new JLabel();
        usbPidValue = new JLabel();
        usbInterfaceValue = new JLabel();
        usbInterfaceDisplayInfo = new JLabel();

        Runnable updateUsbInfo = () -> {
            MidiControllerManager.UsbMidiDeviceInfo device = (MidiControllerManager.UsbMidiDeviceInfo)usbDeviceComboBox.getSelectedItem();

            if (device == null) {
                usbVidValue.setText("");
                usbPidValue.setText("");
                usbInterfaceValue.setText("");

                usbInterfaceDisplayInfo.setText("VID: -\nPID: -\nInterface: -");
                return;
            }


            usbVidValue.setText(String.valueOf(device.vid()));
            usbPidValue.setText(String.valueOf(device.pid()));
            usbInterfaceValue.setText(String.valueOf(device.interfaceNumber()));

            usbInterfaceDisplayInfo.setText(
                    String.format(
                            "<html>"
                                    + "VID: 0x%04X<br>"
                                    + "PID: 0x%04X<br>"
                                    + "Interface: %d"
                                    + "</html>",
                            device.vid(),
                            device.pid(),
                            device.interfaceNumber()
                    )
            );
        };

        usbDeviceComboBox.addActionListener(e -> updateUsbInfo.run());
        updateUsbInfo.run();

        Runnable updateTransportControls = () -> {
            boolean generalMidi = generalMidiRadio.isSelected();
            inputComboBox.setEnabled(generalMidi);
            outputComboBox.setEnabled(generalMidi);
            usbDeviceComboBox.setEnabled(!generalMidi);
        };
        generalMidiRadio.addActionListener(e -> updateTransportControls.run());
        usbDirectRadio.addActionListener(e -> updateTransportControls.run());
        updateTransportControls.run();



        //Mapping file

        midiMappingFileField = new JTextField(midi.getController().getConfigFile());

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

        midiGBC.gridx = 0;
        midiGBC.gridy = 0;
        panel.add(new JLabel("Tryb kontrolera sprzętowego:"), midiGBC);

        midiGBC.gridx = 1;
        panel.add(generalMidiRadio, midiGBC);
        midiGBC.gridx = 2;
        panel.add(usbDirectRadio, midiGBC);


        //--- MIDI Input device
        midiGBC.gridx = 0;
        midiGBC.gridy = 1;
        panel.add(new JLabel("Urządzenie wejściowe MIDI: "), midiGBC);

        midiGBC.gridx = 1;
        midiGBC.weightx = 1.0;
        midiGBC.fill = GridBagConstraints.HORIZONTAL;
        panel.add(inputComboBox, midiGBC);

        //--- MIDI OUTPUT device
        midiGBC.gridx = 0;
        midiGBC.gridy = 2;
        panel.add(new JLabel("Urządzenie wyjściowe MIDI:"), midiGBC);

        midiGBC.gridx = 1;
        midiGBC.weightx = 1.0;
        midiGBC.fill = GridBagConstraints.HORIZONTAL;
        panel.add(outputComboBox, midiGBC);


        //USB_DIRECT device
        midiGBC.gridx = 0;
        midiGBC.gridy = 3;
        panel.add( new JLabel("Urządzenie USB:"), midiGBC);

        midiGBC.gridx = 1;
        midiGBC.gridwidth = 2;
        panel.add(usbDeviceComboBox, midiGBC);
        midiGBC.gridwidth = 1;

        midiGBC.gridx = 1;
        midiGBC.gridy = 4;
        panel.add(usbInterfaceDisplayInfo,midiGBC);


        //---- Mapping file
        midiGBC.gridx = 0;
        midiGBC.gridy = 5;
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

    /**
     * Creates MIDI transport configuration page.
     * <p>Supports both GENERAL_MIDI and USB_DIRECT transport backends.</p>
     */
    private JPanel createCameraPanel(AppConfig config) {

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS)
        );

        for (MainControlCore.ControlPanelId panelId : MainControlCore.ControlPanelId.values()) {
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

    private CameraPanelComponents createCameraConfigPanel(MainControlCore.ControlPanelId panelId, AppConfig.CameraConfig cameraConfig) {

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

    /**
     * Builds a complete configuration model from current dialog values.     *
     * <p>The returned configuration is not validated and has not yet been applied.</p>
     */
    private AppConfig buildConfiguration(){

        ObjectMapper mapper = new ObjectMapper();
        AppConfig config = mapper.convertValue(configManager.getConfig(), AppConfig.class);

        //SYSTEM TAB

        AppConfig.Transport transport = config.getSystem().getTransport();
        transport.setAutoIp(systemPanelComponents.autoIpCheckBox.isSelected());
        transport.setLocalIp(systemPanelComponents.localIpField.getText().trim());
        transport.setLocalPort(Integer.parseInt(systemPanelComponents.localPortField.getText().trim()));
        transport.setSocketTimeout(150);

        //CAMERAS TAB
        config.getSystem().getCameras().clear();
        for (Map.Entry<MainControlCore.ControlPanelId, CameraPanelComponents> entry : cameraPanels.entrySet()) {
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

        AppConfig.MidiConfig midi = config.getSystem().getMidi();

        midi.getController().setConfigFile(midiMappingFileField.getText().trim());

        midi.setMidiTransport(generalMidiRadio.isSelected()? AppConfig.MidiTransport.GENERAL_MIDI : AppConfig.MidiTransport.USB_DIRECT);

        switch (midi.getMidiTransport()) {
            case GENERAL_MIDI:
                MidiDeviceInfo input = (MidiDeviceInfo) inputComboBox.getSelectedItem();

                if (input != null) {
                    midi.getController().setDeviceName(input.deviceName());
                    midi.getJavaMidi().setInputDeviceIndex(input.deviceIndex());
                    midi.getJavaMidi().setInputDeviceName(input.deviceName());

                } else {
                    midi.getJavaMidi().setInputDeviceIndex(-1);
                }

                MidiDeviceInfo output = (MidiDeviceInfo) outputComboBox.getSelectedItem();

                if (output != null) {
                    midi.getJavaMidi().setOutputDeviceIndex(output.deviceIndex());
                    midi.getJavaMidi().setInputDeviceName(output.deviceName());
                } else {
                    midi.getJavaMidi().setOutputDeviceIndex(-1);
                }
                break;

            case USB_DIRECT:

                midi.getUsbDirect().setVid(Integer.parseInt(usbVidValue.getText().trim()));
                midi.getUsbDirect().setPid(Integer.parseInt(usbPidValue.getText().trim()));

                midi.getUsbDirect().setInterfaceNumber(Integer.parseInt(usbInterfaceValue.getText().trim()));
                midi.getUsbDirect().setInputEndpoint(129);
                midi.getUsbDirect().setOutputEndpoint(1);
                break;
        }

        return config;

    }

    /**
     * Validates and applies configuration changes entered by the user.
     */
    private void updateConfiguration() {

        if (!validateConfigurationForm()){
            return;
        }

        AppConfig config = buildConfiguration();

        AppConfigManager.UpdateMode mode = gui.isFirstLaunch() ? AppConfigManager.UpdateMode.INITIAL_LOAD : AppConfigManager.UpdateMode.COMMIT;

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

    /**
     * Performs client-side validation of configuration form fields.
     * <p>This validation complements runtime validation performed by {@link AppConfigManager}.</p>
     */
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

    /**
     * Refreshes displayed camera connection states using the current runtime camera registry.
     */
    private void refreshCameraStatuses() {

        for (MainControlCore.ControlPanelId panelId : cameraPanels.keySet()) {

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

        MainControlCore.ControlPanelId panelId;
    }

    @Data
    private static class GeneralPanelComponents{
        JCheckBox autoIpCheckBox;
        JTextField localIpField;
        JTextField localPortField;
    }

}