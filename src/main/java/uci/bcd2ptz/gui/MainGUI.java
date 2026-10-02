package uci.bcd2ptz.gui;


import com.fasterxml.jackson.databind.ObjectMapper;
import uci.bcd2ptz.AppConfig;
import uci.bcd2ptz.AppConfigManager;
import uci.bcd2ptz.core.*;
import uci.bcd2ptz.core.ControlCore.ControlPanelId;
import uci.bcd2ptz.midi.MidiControllerManager;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.visca.ViscaConfig;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static uci.bcd2ptz.core.ControlCore.LOGGER;


public class MainGUI implements Runnable, ControlEventListener {


    // ==================== GUI ELEMENTS ====================

    //Main graphic window
    private JFrame frame;
    private JPanel contentPanel;

    //--- responsive menu elements
    private JMenu camerasMenu;



    // ==================== CORE INTERFACE ====================

    private ControlEventBus outputBus;
    private PTZCameraManager cameraManager;
    private volatile boolean initialized = false;
    private AppConfig appConfig;
    private AppConfigManager configManager;
    private MidiControllerManager midiManager;

    private final Map<ControlCore.ControlPanelId, GUIPanel> panels = new EnumMap<>(ControlCore.ControlPanelId.class);

    public MainGUI(ControlEventBus outputBus, AppConfigManager configManager, MidiControllerManager midiManager){
        this.configManager = configManager;
        this.appConfig = configManager.getConfig();
        this.outputBus = outputBus;
        this.midiManager = midiManager;
    }

    //============= GUI API METHODS =====================

    @Override
    public void onControlEvent(ControlEvent event) {

        if (!initialized) {
            return;
        }
        SwingUtilities.invokeLater(() -> handleControlEvent(event));
    }

    public void handleControlEvent(ControlEvent event) {

        //DEBUG:
        //System.out.println("======== MAIN GUI ============= \nhandleControlEvent: " + event);

        GUIPanel panel = panels.get(event.getPanel());

        //DEBUG
        //System.out.println("GUI ROUTE: " + event.getAction() + " -> PANEL " + event.getPanel());

        if (panel != null && event.getAction().equalsIgnoreCase("UPDATE_CAMERA_STATUS"))  {
            refreshStatusInfo(event.getPanel());
        }

        if (panel != null) {
            panel.handleControlEvent(event);
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
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ================= MENU =================

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Plik");

        JMenuItem loadConfigItem = new JMenuItem("Wczytaj konfigurację...");
        loadConfigItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        loadConfigItem.addActionListener(e -> importConfiguration());

        JMenuItem saveConfigItem = new JMenuItem("Zapisz konfigurację");
        saveConfigItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveConfigItem.addActionListener(e -> saveConfiguration(appConfig));

        JMenuItem exportConfigItem = new JMenuItem("Eksportuj konfigurację");
        exportConfigItem.addActionListener(e -> saveConfigurationAs(appConfig));

        JMenuItem setupItem = new JMenuItem(     "Ustawienia");
        setupItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK));
        setupItem.addActionListener(this::actionPerformed);

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

        //DEBUG
        //System.out.println("GUI Initialized");

        initialized = true;

    }

    private GUIPanel createPanel(
            ControlPanelId panelId) {

        GUIPanel panel =
                new GUIPanel(
                        panelId,
                        outputBus,
                        this,
                        cameraManager,
                        midiManager,
                        appConfig
                );

        panels.put(panelId, panel);

        return panel;
    }

    public void rebuildPanels() {

        contentPanel.removeAll();

        panels.clear();

        boolean first = true;

        for (ControlPanelId panelId
                : ControlPanelId.values()) {

            PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);

            if (camera == null) {
                continue;
            }

            if (!first) {contentPanel.add(new VerticalSeparator());
            }

            contentPanel.add(createPanel(panelId));
            first = false;
        }

        contentPanel.revalidate();
        contentPanel.repaint();
        //refreshCameraMenu();

        frame.pack();
        refreshStatusInfo();
    }

    public void shutdown(){
        cameraManager.shutdown();
        midiManager.stopController();
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }

    //===  MIDI PANELS STATUS UPDATES
    public void refreshPanelBindings() {

        for (GUIPanel panel : panels.values()) {
            //DEBUG
            //System.out.println("MIDI DSPL VALUE" + String.valueOf(midiManager.getMidiBinding(panel.panelId)));
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
        outputBus.publish(event);
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

    public void reloadConfiguration(AppConfig config) {

        this.appConfig = config;

        Set<String> existingCameras = new HashSet<>(cameraManager.getCameras().keySet());

        for (AppConfig.CameraConfig cameraConfig : config.getSystem().getCameras()) {
            try {
                ObjectMapper mapper = new ObjectMapper();

                ViscaConfig viscaConfig = mapper.readValue(new File(cameraConfig.getViscaDefinition()), ViscaConfig.class);
                ControlPanelId corePanel = ControlPanelId.valueOf(cameraConfig.getAssignedGuiPanel());

                ControlPanelId midiPanel = null;
                if (cameraConfig.getAssignedMidiPanel() != null) {
                    midiPanel = ControlPanelId.valueOf(cameraConfig.getAssignedMidiPanel());
                }

                int socketTimeout =((Number) config.getSystem().getTransport().get("socketTimeout")).intValue();

                //DEBUG
                System.out.println( "Loading " + cameraConfig.getCameraId());

                cameraManager.addCamera(cameraConfig.getCameraId(), cameraConfig.getIpAddress(), cameraConfig.getPort(), cameraManager.getLocalPort(),
                        socketTimeout, cameraConfig.getDisplayName(), viscaConfig, corePanel, midiPanel);

                existingCameras.remove(cameraConfig.getCameraId());

            } catch (Exception ex) {
                LOGGER.warning("Unable to reload camera " + cameraConfig.getCameraId() + ": " + ex.getMessage());
            }
        }

        for (String cameraId : existingCameras) {
            cameraManager.removeCamera(cameraId);
        }

        configManager.notifyConfigurationChanged();
        rebuildPanels();
        refreshStatusInfo();
        midiManager.updateMidiIndicators();
    }

    private void importConfiguration() {

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files","json"));

        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            AppConfig config = configManager.load(chooser.getSelectedFile());
            //DEbug
            System.out.println("File " + chooser.getSelectedFile().getName() + "  readed.");
            System.out.println("AppConfig values \n" + appConfig.toString());
            reloadConfiguration(config);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame,"Błąd wczytywania konfiguracji:\n" + ex.getMessage(),"Błąd",JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveConfiguration(AppConfig appConfig) {
        try {
            configManager.save(appConfig);
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

            configManager.save(config, targetFile);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Błąd zapisu", JOptionPane.ERROR_MESSAGE);
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
                midiManager,
                this
        ).setVisible(true);
    }
}