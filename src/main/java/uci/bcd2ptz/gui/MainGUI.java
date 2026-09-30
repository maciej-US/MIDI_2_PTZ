package uci.bcd2ptz.gui;


import uci.bcd2ptz.AppConfig;
import uci.bcd2ptz.AppConfigManager;
import uci.bcd2ptz.core.*;
import uci.bcd2ptz.core.ControlCore.ControlPanelId;
import uci.bcd2ptz.midi.MidiControllerManager;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;


public class MainGUI implements Runnable, ControlEventListener {


    // ==================== GUI ELEMENTS ====================

    private JFrame frame;
    /*private JComboBox<String> midiComboBox;
    private JButton connectButton;*/

    //Camera PANELS
    private GUIPanel panelA;
    private GUIPanel panelB;
    private GUIPanel panelC;
    private GUIPanel panelD;

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

        JMenuItem exitItem = new JMenuItem("Zamknij");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
        exitItem.addActionListener(e -> {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        });

        JMenuItem setupItem = new JMenuItem("Ustawienia");
        setupItem.addActionListener(this::actionPerformed
        );


        fileMenu.add(setupItem);
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        //=================== CAMERA PANELS ==============

        JPanel content = new JPanel();
        content.setLayout(
                new BoxLayout(content, BoxLayout.X_AXIS)
        );

        boolean first = true;

        for (ControlPanelId panelId : ControlPanelId.values()) {

            PTZCamera camera = cameraManager.getCoreAssignedCamera(panelId);

            if (camera == null) {
                continue;
            }

            if (!first) {
                content.add(new VerticalSeparator());
            }

            GUIPanel panel = new GUIPanel(panelId, outputBus, cameraManager, midiManager, appConfig);
            panels.put(panelId, panel);
            content.add(panel);

            first = false;
        }

        frame.add(content, BorderLayout.CENTER);

        //frame.setSize(2 * this.panelA.getWidth() + 24, panelA.getHeight() + 15);
        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);

        //DEBUG
        //System.out.println("GUI Initialized");

        initialized = true;

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
}