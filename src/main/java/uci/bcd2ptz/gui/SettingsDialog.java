package uci.bcd2ptz.gui;


import uci.bcd2ptz.AppConfig;
import uci.bcd2ptz.AppConfigManager;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.midi.MidiDeviceInfo;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

//Data management
public class SettingsDialog extends JDialog {

    private JTextField midiMappingPathField;
    private JComboBox<MidiDeviceInfo> inputComboBox;
    private JComboBox<MidiDeviceInfo> outputComboBox;
    private JTextField midiMappingFileField;

    private AppConfig appConfig;
    private final AppConfigManager configManager;

    public SettingsDialog(
            JFrame owner,
            AppConfigManager appConfigManager,
            PTZCameraManager cameraManager) {


        super(owner, "Ustawienia", true);

        this.configManager = appConfigManager;
        this.appConfig = appConfigManager.getConfig();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ogólne",createGeneralPanel());
        tabs.addTab("MIDI",createMidiPanel(appConfig));
        tabs.addTab("Kamery",createCameraPanel(appConfig));

        add(tabs, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        setSize(700, 500);
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

        String[] columns = {
                "ID",
                "Nazwa",
                "Adres IP",
                "Port",
                "GUI",
                "MIDI"
        };

        DefaultTableModel model =
                new DefaultTableModel(
                        columns,
                        0
                );

        for (AppConfig.CameraConfig camera
                : config.getSystem()
                .getCameras()) {

            model.addRow(
                    new Object[]{
                            camera.getCameraId(),
                            camera.getDisplayName(),
                            camera.getIpAddress(),
                            camera.getPort(),
                            camera.getAssignedGuiPanel(),
                            camera.getAssignedMidiPanel()
                    }
            );
        }

        JTable table =
                new JTable(model);

        return new JPanel(
                new BorderLayout()
        ) {{
            add(
                    new JScrollPane(table),
                    BorderLayout.CENTER
            );
        }};
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

    //==============================
    //   BUTTONS HANDLERS
    //==============================

    private void applyConfiguration() {

        MidiDeviceInfo input = (MidiDeviceInfo) inputComboBox.getSelectedItem();

        MidiDeviceInfo output = (MidiDeviceInfo) outputComboBox.getSelectedItem();

        if (input != null) {
            appConfig.getSystem()
                    .getMidi()
                    .getInput()
                    .setDeviceName(
                            input.getDeviceName()
                    );

            appConfig.getSystem()
                    .getMidi()
                    .getInput()
                    .setDeviceIndex(
                            input.getDeviceIndex()
                    );
        }

        if (output != null) {

            appConfig.getSystem()
                    .getMidi()
                    .getOutput()
                    .setDeviceName(
                            output.getDeviceName()
                    );

            appConfig.getSystem()
                    .getMidi()
                    .getOutput()
                    .setDeviceIndex(
                            output.getDeviceIndex()
                    );
        }

        String configFile = midiMappingFileField.getText();

        appConfig.getSystem()
                .getMidi()
                .getInput()
                .setConfigFile(configFile);

        appConfig.getSystem()
                .getMidi()
                .getOutput()
                .setConfigFile(configFile);

        configManager.notifyConfigurationChanged();

        System.out.println("Configuration applied.");
    }

    private void saveConfiguration() {

        applyConfiguration();
        try {
            configManager.save(appConfig);
            configManager.notifyConfigurationChanged();
            JOptionPane.showMessageDialog(this, "Konfiguracja zapisana.","Ustawienia",JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Błąd zapisu konfiguracji:\n"+ ex.getMessage(),"Błąd",JOptionPane.ERROR_MESSAGE);
        }

    }



    //===============================
    //       HELPERS, RENDERS
    //===============================


}