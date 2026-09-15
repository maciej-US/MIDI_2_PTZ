package uci.bcd2ptz.gui;


import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlEvent;
import uci.bcd2ptz.core.ControlEventBus;
import uci.bcd2ptz.core.ControlEventListener;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.visca.FieldDefinition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;

import static uci.bcd2ptz.core.ControlEvent.EventSource.GUI;


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

    private final Map<ControlCore.ControlPanelId, GUIPanel> panels = new EnumMap<>(ControlCore.ControlPanelId.class);

    public MainGUI(ControlEventBus outputBus) {
        this.outputBus = outputBus;
    }

    //============= GUI API METHODS =====================

    @Override
    public void onControlEvent(ControlEvent event) {

        if (!initialized) {
            return;
        }
        SwingUtilities.invokeLater(() -> handleControlEvent(event));
    }

    public void handleControlEvent(
            ControlEvent event) {

        //DEBUG:
        System.out.println("======== MAIN GUI ============= \nhandleControlEvent: " + event);

        GUIPanel panel =panels.get(event.getPanel());

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

        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        //=================== CAMERA PANELS ==============

        panelA = new GUIPanel(
                ControlCore.ControlPanelId.A,
                outputBus,
                cameraManager
        );
        this.panels.put(ControlCore.ControlPanelId.A, panelA);
        //panelA.run();

        panelB = new GUIPanel(
                ControlCore.ControlPanelId.B,
                outputBus,
                cameraManager
        );
        this.panels.put(ControlCore.ControlPanelId.B, panelB);

        panelC = new GUIPanel(
                ControlCore.ControlPanelId.C,
                outputBus,
                cameraManager
        );
        this.panels.put(ControlCore.ControlPanelId.C, panelC);

        panelD = new GUIPanel(
                ControlCore.ControlPanelId.D,
                outputBus,
                cameraManager
        );
        this.panels.put(ControlCore.ControlPanelId.D, panelD);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));

        //----------- vertical separator
        VerticalSeparator verticalSeparator1 = new VerticalSeparator();
        VerticalSeparator verticalSeparator2 = new VerticalSeparator();
        VerticalSeparator verticalSeparator3 = new VerticalSeparator();

        content.add(panelA);
        content.add(verticalSeparator1);
        content.add(panelB);
       /* content.add(verticalSeparator2);
        content.add(panelC);
        content.add(verticalSeparator3);
        content.add(panelD);*/

        frame.add(content, BorderLayout.CENTER);

        frame.setSize(2 * this.panelA.getWidth() + 24, panelA.getHeight() + 15);

        frame.setVisible(true);

        //DEBUG
        System.out.println("GUI Initialized");

        initialized = true;

    }

    // ============================================
    //        INCOMING ControlEvent HANDLING
    // ============================================

    /*@Override
    public void onControlEvent(ControlEvent event) {

        SwingUtilities.invokeLater(() -> handleControlEvent(event));
    }
*/
    /*public void handleControlEvent(ControlEvent event) {

        //DEBUG
        /*System.out.println("======= GUI ============= \n->EVENT from: " + event.getEventSource() +
                "CONTROL ID = " + event.getControlID() + " PANEL: " + event.getPanel() + "   VALUE = " + event.getValue());

        if (!initialized) {
            return;
        }

        SwingUtilities.invokeLater(() -> {

            switch (event.getAction()) {

                case "PAN":
                    jogIndicator.setValueSilently(event.getValue());
                    break;

                case "TILT":
                    tiltSlider.setValueSilently(event.getValue());
                    break;

                case "ZOOM":
                    zoomSlider.setValueSilently(event.getValue());
                    break;

                case "FOCUS":
                    focusSlider.setValueSilently(event.getValue());
                    break;

                case "EXPOSURE_COMPENSATION":
                    exposureCompensationKnob.setValueSilently(event.getValue());
                    break;

                case "EXPOSURE_GAIN":
                    exposureCompensationKnob.setValueSilently(event.getValue());
                    break;

                case "EXPOSURE_SHUTTER":
                    shutterKnob.setValueSilently(event.getValue());
                    break;

                case "EXPOSURE_IRIS":
                    irisKnob.setValueSilently(event.getValue());
                    break;

                case "WHITE_BALANCE_MODE":
                    updateWBModeSwitch(event.getValue());
                    break;

                case "WHITE_BALANCE_PUSH_INDICATOR":
                    handleWBOnePushStatus(event.getValue());
                    break;

                case "ABSOLUTE_MODE_INDICATOR":
                    handleAbsoluteModeStatus(event.getValue());
                    break;

                case "AUTO_SHOOT_INDICATOR":
                    handleAutoFollowStatus(event.getValue());
                    break;

                case "EXPOSURE_MODE_INDICATOR":
                    handleExposureModeStatus(event.getValue());
                    break;

                case "FOCUS_MODE_INDICATOR":
                    handleFocusMode(event.getValue());
                    break;

                case "TALLY_INDICATOR":
                    if (event.getValue() == 1) {
                        onAirIndicator.setOnAir(true);
                    } else {
                        onAirIndicator.setOnAir(false);
                    }
                    break;
            }
        });
    }

    // ====================
    //       HANDLERY
    // ====================

    // =========== CONTROL ELEMENTS UPDATE =================


    public void updateWBModeSwitch(int value) {

        whiteBalanceMode.setValueSilently(value);
        if (value > 106) {
            whiteBalanceRGainKnob.setActive(true);
            whiteBalanceBGainKnob.setActive(true);
        } else {
            whiteBalanceRGainKnob.setActive(false);
            whiteBalanceBGainKnob.setActive(false);
        }

    }

    // ==================== INDICATORS UPDATE ====================

    private void handleAbsoluteModeStatus(int state) {

        switch (state) {

            case 0:
                PTZModeButton.setMode(ButtonLED.LedMode.OFF);
                tiltSlider.setJoystickMode(true);
                jogIndicator.setJoystickMode(true);
                break;

            case 1:
                PTZModeButton.setMode(ButtonLED.LedMode.ON);
                tiltSlider.setJoystickMode(false);
                jogIndicator.setJoystickMode(false);
                break;

            case 2:
                PTZModeButton.setMode(ButtonLED.LedMode.BLINK);
                tiltSlider.setJoystickMode(true);
                jogIndicator.setJoystickMode(false);
                break;
        }
    }

    private void handleAutoFollowStatus(int state) {

        switch (state) {
            case 0:
                autoFollowModeButton.setMode(ButtonLED.LedMode.OFF);
                tiltSlider.setEnabled(true);
                zoomSlider.setEnabled(true);
                PTZModeButton.setEnabled(true);
                break;

            case 1:
                autoFollowModeButton.setMode(ButtonLED.LedMode.ON);
                tiltSlider.setEnabled(false);
                zoomSlider.setEnabled(false);
                PTZModeButton.setEnabled(false);
                break;
        }
    }

    private void handleFocusMode(int state) {

        switch (state) {

            case 0:
                focusModeButton.setMode(ButtonLED.LedMode.OFF);
                focusSlider.setEnabled(true);
                focusOnePushButton.setEnabled(true);
                focusOnePushButton.setMode(ButtonLED.LedMode.BLINK);
                focusMoveUpButton.setEnabled(true);
                focusMoveDownButton.setEnabled(true);
                break;

            case 1:
                focusModeButton.setMode(ButtonLED.LedMode.ON);
                focusSlider.setEnabled(false);
                focusOnePushButton.setEnabled(false);
                focusOnePushButton.setMode(ButtonLED.LedMode.OFF);
                focusMoveUpButton.setEnabled(false);
                focusMoveDownButton.setEnabled(false);
                break;
        }
    }

    private void handleExposureModeStatus(int state) {

        switch (state) {
            case 0:
                exposureModeButton.setMode(ButtonLED.LedMode.OFF);
                exposureLabel.setText("GAIN");
                FieldDefinition gain = cameraManager.getCamera("CAM_A")
                        .getController().getFieldDefinition("exposureGainPosition");
                exposureCompensationKnob.configure(gain);
                shutterKnob.setActive(true);
                irisKnob.setActive(true);
                break;

            case 1:
                exposureModeButton.setMode(ButtonLED.LedMode.ON);
                FieldDefinition exposureCompensation = cameraManager.getCamera("CAM_A")
                        .getController().getFieldDefinition("exposureCompensationPosition");
                exposureCompensationKnob.configure(exposureCompensation);
                exposureLabel.setText("EXP. CMP");
                shutterKnob.setActive(false);
                irisKnob.setActive(false);
                break;
        }
    }


    private void handleWBOnePushStatus(int state) {

        switch (state) {
            case 0:
                onePushWB.setMode(ButtonLED.LedMode.OFF);
                break;

            case 1:
                onePushWB.setMode(ButtonLED.LedMode.ON);
                break;

            case 2:
                onePushWB.setMode(ButtonLED.LedMode.BLINK);
                break;
        }
    }
*/
    // ========================== GETTERS & SETTERS

    public void setCameraManager(PTZCameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }


    public boolean isInitialized() {

        return this.initialized;
    }
}