package uci.midi2ptz.gui;


import lombok.Getter;
import lombok.Setter;
import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.*;
import uci.midi2ptz.core.MainControlCore.ControlPanelId;
import uci.midi2ptz.midi.MidiControllerManager;
import uci.midi2ptz.ptz.PTZCamera;
import uci.midi2ptz.ptz.PTZCameraManager;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;


public class MainGUI implements Runnable{


    // ==================== GUI ELEMENTS ====================

    //Main graphic window
    private JFrame frame;
    private JPanel contentPanel;

    //--- responsive menu elements
    private JMenu camerasMenu;



    // ==================== CORE INTERFACE ====================

    private ControlEventBus eventBus;
    private PTZCameraManager cameraManager;
    @Getter @Setter
    private volatile boolean firstLaunch;
    private AppConfigManager configManager;
    private MidiControllerManager midiManager;

    private volatile boolean initialized = false;
    @Getter
    private boolean shuttingDown = false;

    private final Map<MainControlCore.ControlPanelId, GUIPanel> panels = new EnumMap<>(MainControlCore.ControlPanelId.class);

    public MainGUI(ControlEventBus eventBus, AppConfigManager configManager, MidiControllerManager midiManager, boolean firstLaunch) {
        this.configManager = configManager;
        this.eventBus = eventBus;
        this.midiManager = midiManager;
        this.firstLaunch = firstLaunch;
        eventBus.register(this::handleControlEvent);

    }

    //============= GUI API METHODS =====================

    public void handleControlEvent(ControlEvent event) {

        //DEBUG
        System.out.println("GUI RECEIVED EVENT -> " + event + " THREAD=" + Thread.currentThread().getName());


        GUIPanel panel = panels.get(event.getPanel());

        if (panel != null && event.getAction().equalsIgnoreCase("UPDATE_CAMERA_STATUS"))  {
            //DEBUG
            System.out.println("MAIN GUI  Status info EVENT: " + event);
            refreshStatusInfo(event.getPanel());
        }

        if (panel != null) {
            panel.updateFromEvent(event);
        }
    }

    // ==================== RUN ====================

    @Override
    public void run() {
        SwingUtilities.invokeLater(this::initialize);
    }

    // ====================
    //        INIT
    // ====================

    private void initialize() {
        frame = new JFrame("BCD2PTZ - MIDI");
        frame.setBackground(new Color(80, 80, 80));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        frame.setLayout(new BorderLayout());

        // ================= MENU =================

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Plik");

        JMenuItem loadConfigItem = new JMenuItem("Wczytaj konfigurację...");
        loadConfigItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        loadConfigItem.addActionListener(e -> importConfiguration());

        JMenuItem saveConfigItem = new JMenuItem("Zapisz konfigurację");
        saveConfigItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveConfigItem.addActionListener(e -> saveConfiguration(configManager.getConfig()));

        JMenuItem exportConfigItem = new JMenuItem("Eksportuj konfigurację");
        exportConfigItem.addActionListener(e -> saveConfigurationAs(configManager.getConfig()));

        JMenuItem setupItem = new JMenuItem(     "Ustawienia");
        setupItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK));
        setupItem.addActionListener(e -> showSettingsDialog());

        JMenuItem exitItem = new JMenuItem(      "Zamknij");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
        exitItem.addActionListener(e -> {
            shutdown();
        });

        fileMenu.add(loadConfigItem);
        fileMenu.add(saveConfigItem);
        fileMenu.add(exportConfigItem);
        fileMenu.addSeparator();
        fileMenu.add(setupItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        camerasMenu = new JMenu("Kamery");

        for (ControlPanelId panelId : ControlPanelId.values()) {

            JMenu cameraMenu = new JMenu("Kamera " + panelId.name());
            JMenuItem reconnectItem = new JMenuItem("Odśwież");
            reconnectItem.addActionListener(e -> {
                reconnectCamera(panelId);
            });
            JMenuItem assignMidiA = new JMenuItem("==> MIDI Panel A");
            assignMidiA.addActionListener(e -> assignMidiPanel(
                            panelId,
                            ControlPanelId.A
                    )
            );
            JMenuItem assignMidiB = new JMenuItem("==> MIDI Panel B");
            assignMidiB.addActionListener(e -> assignMidiPanel(
                            panelId,
                            ControlPanelId.B
                    )
            );

            cameraMenu.add(reconnectItem);
            cameraMenu.addSeparator();
            cameraMenu.add(assignMidiA);
            cameraMenu.add(assignMidiB);

            PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);
            cameraMenu.setEnabled(camera != null);

            camerasMenu.add(cameraMenu);
        }

        menuBar.add(camerasMenu);

        frame.setJMenuBar(menuBar);

        //=================== CAMERA PANELS ==============

        contentPanel = new JPanel();

        contentPanel.setLayout(new BoxLayout(contentPanel,BoxLayout.X_AXIS));
        rebuildPanels();

        frame.add(contentPanel, BorderLayout.CENTER);

        //frame.setSize(2 * this.panelA.getWidth() + 24, panelA.getHeight() + 15);
        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);

        initialized = true;

        if (firstLaunch) {
            JOptionPane.showMessageDialog(
                    frame,
                    """
                    Aplikacja nie została jeszcze skonfigurowana.
        
                    Skonfiguruj połączenie z kamerami,
                    transport oraz kontroler MIDI.
                    """,
                    "Pierwsze uruchomienie",
                    JOptionPane.INFORMATION_MESSAGE
            );

            showSettingsDialog();
        }

    }

    private GUIPanel createPanel(ControlPanelId panelId) {

        GUIPanel panel =
                new GUIPanel(
                        panelId,
                        eventBus,
                        this,
                        cameraManager,
                        midiManager,
                        configManager
                );

        panels.put(panelId, panel);

        return panel;
    }

    public void rebuildPanels() {

        contentPanel.removeAll();

        panels.clear();

        boolean first = true;

        for (ControlPanelId panelId : ControlPanelId.values()) {

            PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);

            if (camera == null) {
                continue;
            }

            if (!first) {
                contentPanel.add(new VerticalSeparator());
            }

            contentPanel.add(createPanel(panelId));
            first = false;
        }

        contentPanel.revalidate();
        contentPanel.repaint();
        refreshCameraMenu();

        frame.pack();
        refreshStatusInfo();
    }

    public void shutdown(){

        if (configManager.getConfig().isChanged()){
            int result = JOptionPane.showConfirmDialog(
                    frame,
                    "Konfiguracja została zmieniona.\nCzy chcesz zapisać zmiany?",
                    "Niezapisane zmiany",
                    JOptionPane.YES_NO_CANCEL_OPTION);
            if (result == JOptionPane.CANCEL_OPTION) {
                shuttingDown = false;
                return;
            }
            if (result == JOptionPane.YES_OPTION) {
                ConfigurationUpdateResults results = configManager.saveConfiguration();
                if (!results.getErrors().isEmpty()){
                    JOptionPane.showMessageDialog(
                            frame,
                            "Nie udało się zapisać konfiguracji:\n"
                                    + results.getErrors(),
                            "Błąd",
                            JOptionPane.ERROR_MESSAGE
                    );
                    shuttingDown = false;
                    return;
                }
            }
            if (result == JOptionPane.NO_OPTION) {
                configManager.getConfig().setChanged(false);
                shuttingDown = true;
            }
        }

        cameraManager.shutdown();

        midiManager.stopController();

        frame.dispose();
        System.exit(0);
    }

    //===  MIDI PANELS STATUS UPDATES
    public void refreshPanelBindings() {

        for (GUIPanel panel : panels.values()) {
            ControlPanelId midiBinding = midiManager.getMidiBinding(panel.panelId);
            panel.getMidiPanelInfo().setValue(
                    midiBinding == null ?
                            null :
                            midiBinding.toString());
        }
    }

    //=== CAMERAS STATUS UPDATE
    public void reconnectCamera(
            ControlPanelId panelId) {

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);
        if (camera == null) {
            return;
        }
        cameraManager.refreshConnection(panelId);
    }

    private void assignMidiPanel(ControlPanelId cameraPanel, ControlPanelId midiPanel) {
        ControlEvent event = new ControlEvent(
                ControlEvent.EventSource.GUI,
                midiPanel,
                "CAMERA_SELECT_" + cameraPanel.name(),
                1
        );
        eventBus.publish(event);
    }

    public void refreshStatusInfo() {

        for (GUIPanel panel : panels.values()) {
            panel.refreshStatusInfo();
        }
    }

    public void refreshStatusInfo(ControlPanelId panelId) {

        if (panels.containsKey(panelId)) {
           panels.get(panelId).refreshStatusInfo();
        }
    }

    public void reloadConfiguration() {

        rebuildPanels();
        refreshStatusInfo();

        midiManager.updateMidiIndicators();
    }

    private void importConfiguration() {

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));

        if (chooser.showOpenDialog(frame)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        ConfigurationUpdateResults results = configManager.loadConfiguration(chooser.getSelectedFile());

        if (!results.getErrors().isEmpty()) {

            JOptionPane.showMessageDialog(
                    frame,
                    formatErrors(results),
                    "Błąd importu konfiguracji",
                    JOptionPane.ERROR_MESSAGE
            );

            return;
        }

        reloadConfiguration();

        JOptionPane.showMessageDialog(
                frame,
                "Konfiguracja została poprawnie załadowana.",
                "Import konfiguracji",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    public void saveConfiguration(AppConfig appConfig) {
        try {
            configManager.saveConfiguration(appConfig);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,"Błąd zapisu konfiguracji:\n"+ ex.getMessage(),"Błąd",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfigurationAs(AppConfig config) {

        JFileChooser chooser = new JFileChooser();

        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files","json"));

        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            File targetFile = chooser.getSelectedFile();

            if (!targetFile.getName().toLowerCase().endsWith(".json")) {
                targetFile = new File(targetFile.getAbsolutePath() + ".json");
            }

            configManager.saveConfiguration(config, targetFile);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Błąd zapisu", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void showSettingsDialog() {

        new SettingsDialog(
                frame,
                configManager,
                cameraManager,
                this
        ).setVisible(true);
    }

    private void refreshCameraMenu() {

        int index = 0;

        for (ControlPanelId panelId : ControlPanelId.values()) {

            JMenu cameraMenu = (JMenu) camerasMenu.getItem(index++);
            PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);

            boolean available = camera != null;
            cameraMenu.setEnabled(available);
        }
    }

    // ========== GETTERS, SETTERS, HELPERS

    public void setCameraManager(PTZCameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    public boolean isInitialized() {

        return this.initialized;
    }

    private void actionPerformed(ActionEvent e) {
        new SettingsDialog(
                frame,
                configManager,
                cameraManager,
                this
        ).setVisible(true);
    }

    public String formatErrors(
            ConfigurationUpdateResults results) {

        StringBuilder sb = new StringBuilder("Podczas aktualizacji konfiguracji wystąpiły błędy:\n\n");

        for (Map.Entry<AppConfigManager.ConfigurationSection,String> entry : results.getErrors().entrySet()) {
            sb.append("• ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n");
        }
        return sb.toString();
    }

}