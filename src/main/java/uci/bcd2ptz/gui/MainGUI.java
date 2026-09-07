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

import static uci.bcd2ptz.core.ControlEvent.EventSource.GUI;


public class MainGUI implements Runnable, ControlEventListener {


    // ==================== GUI ELEMENTS ====================

    private JFrame frame;
    /*private JComboBox<String> midiComboBox;
    private JButton connectButton;*/

    private VerticalSlider tiltASlider;
    private VerticalSlider zoomASlider;

    private JogIndicator jogAIndicator;

    //KNOBS
    private RotaryKnob exposureCompensationKnob;
    private RotaryKnob irisKnob;
    private RotaryKnob shutterKnob;
    private JLabel exposureLabel;

    //BUTTONS
    private final buttonLED tiltModeButton = new buttonLED("ABS");
    private final buttonLED autoFollowModeButton = new buttonLED("A-SH");
    private final buttonLED exposureModeButton = new buttonLED ("AUTO");

    private final OnAirIndicator onAirIndicator = new OnAirIndicator();

    //==================== VISUAL CONSTANSTS =================
    Color labelColor = new Color(200,200,200);
    Color backgroundColor = new Color (40, 40, 40);

    // ==================== CORE INTERFACE ====================

    private ControlEventBus outputBus;
    private PTZCameraManager cameraManager;

    public MainGUI(ControlEventBus outputBus){
        this.outputBus = outputBus;
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
        frame.setSize(500, 450);
        frame.setBackground(new Color(80,80,80));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ================= MENU =================

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Plik");
        JMenuItem exitItem =new JMenuItem("Zamknij");

        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4,InputEvent.ALT_DOWN_MASK));
        exitItem.addActionListener(e -> {
            frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));
        });

        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        /* =========================
            IMAGE CONTROL PANEL
         =========================*/

        JPanel exposurePanel = new JPanel(new GridBagLayout());
        exposurePanel.setBackground(backgroundColor);
        GridBagConstraints expPanelGbc = new GridBagConstraints();
        expPanelGbc.insets = new Insets(5, 5, 5,5 );

        //mode switch
        exposureModeButton.setOnClick(() -> {
                    ControlEvent event =
                            new ControlEvent(
                                    GUI,
                                    "EXPOSURE_MODE_KEY",
                                    ControlCore.ControlPanelId.A,
                                    "EXPOSURE_MODE_KEY",
                                    1
                            );
                    outputBus.publish(event);
                });

        // IRIS

        irisKnob = new RotaryKnob();

        FieldDefinition field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("exposureIrisPosition");
        irisKnob.configure(field);

        irisKnob.setPreferredSize(new Dimension(60, 60));
        irisKnob.setValue(64);
        irisKnob.setOnValueChange(
                value -> {ControlEvent event = new ControlEvent(
                        GUI,
                        "EXPOSURE_IRIS",
                        ControlCore.ControlPanelId.A,
                        "EXPOSURE_IRIS",
                        value
                );
                    outputBus.publish(event);
                }
        );

        JLabel irisLabel = new JLabel("IRIS",SwingConstants.CENTER);
        irisLabel.setForeground(labelColor);

        //--- Iris knob
        expPanelGbc.gridx = 1;
        expPanelGbc.gridy = 0;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(irisKnob, expPanelGbc);

        //--- iris label
        expPanelGbc.gridx = 1;
        expPanelGbc.gridy = 1;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(irisLabel, expPanelGbc);

        // SHUTTER

        shutterKnob = new RotaryKnob();

        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("exposureShutterPosition");
        shutterKnob.configure(field);

        shutterKnob.setPreferredSize(new Dimension(60, 60));
        shutterKnob.setValue(64);
        shutterKnob.setOnValueChange(
                value -> {ControlEvent event = new ControlEvent(
                                    GUI,
                                    "EXPOSURE_SHUTTER",
                                    ControlCore.ControlPanelId.A,
                                    "EXPOSURE_SHUTTER",
                                    value
                            );
                    outputBus.publish(event);
                }
        );

        JLabel shutterLabel = new JLabel("SHUTTER",SwingConstants.CENTER);
        shutterLabel.setForeground(labelColor);

        //--- shutter knob
        expPanelGbc.gridx = 0;
        expPanelGbc.gridy = 0;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(shutterKnob, expPanelGbc);

        //--- shutter label
        expPanelGbc.gridx = 0;
        expPanelGbc.gridy = 1;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(shutterLabel, expPanelGbc);

        // EXPOSURE COMPENSATION

        exposureCompensationKnob = new RotaryKnob();

        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("exposureCompensationPosition");
        exposureCompensationKnob.configure(field);

        exposureCompensationKnob.setPreferredSize(new Dimension(60, 60));
        exposureCompensationKnob.setValue(64);
        exposureCompensationKnob.setOnValueChange(
                value -> {ControlEvent event = new ControlEvent(
                        GUI,
                        "EXPOSURE_COMPENSATION",
                        ControlCore.ControlPanelId.A,
                        "EXPOSURE_COMPENSATION",
                        value
                );
                    outputBus.publish(event);
                }
        );

        exposureLabel = new JLabel("EXP. COMP.",SwingConstants.CENTER);
        exposureLabel.setForeground(labelColor);

        //--- exposure/gain knob
        expPanelGbc.gridx = 2;
        expPanelGbc.gridy = 0;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(exposureCompensationKnob, expPanelGbc);

        //--- exposure/gain label
        expPanelGbc.gridx = 2;
        expPanelGbc.gridy = 1;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(exposureLabel, expPanelGbc);

        //========== EXPOSURE MODE BUTTON
        expPanelGbc.gridx = 5;
        expPanelGbc.gridy = 0;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        expPanelGbc.insets = new Insets(5, 15, 5,15);
        exposurePanel.add(exposureModeButton, expPanelGbc);

        // ==== TILT ====
        tiltASlider = new VerticalSlider();
        tiltASlider.setPreferredSize(new Dimension(80, 200));
        tiltASlider.setJoystickMode(true);
        tiltASlider.setValue(64);
        tiltASlider.setOnValueChange(
                value -> {

                    ControlEvent event =
                            new ControlEvent(
                                    GUI,
                                    "TILT",
                                    ControlCore.ControlPanelId.A,
                                    "TILT",
                                    value
                            );

                    //DEBUG:
                    //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

                    outputBus.publish(event);
                }
        );

        JLabel tiltLabel = new JLabel("TILT", SwingConstants.CENTER);
        tiltLabel.setForeground(labelColor);

        JPanel tiltPanel = new JPanel(new BorderLayout());
        tiltPanel.setBackground(backgroundColor);
        tiltPanel.add(tiltLabel, BorderLayout.NORTH);
        tiltPanel.add(tiltASlider, BorderLayout.CENTER);
        JPanel tiltBottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        tiltBottomPanel.setBackground(backgroundColor);
        tiltBottomPanel.setOpaque(false);
        tiltBottomPanel.add(tiltModeButton);

        tiltModeButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            "ABSOLUTE_MODE_KEY",
                            ControlCore.ControlPanelId.A,
                            "ABSOLUTE_MODE_KEY",
                            1
                    );

            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

            outputBus.publish(event);
        });

        tiltPanel.add(tiltBottomPanel, BorderLayout.SOUTH);

        // ==== ZOOM ====
        zoomASlider = new VerticalSlider();
        zoomASlider.setPreferredSize(new Dimension(80, 200));
        zoomASlider.setJoystickMode(false);
        zoomASlider.setOnValueChange(
                value -> {
                    ControlEvent event =
                            new ControlEvent(
                                    GUI,
                                    "ZOOM",
                                    ControlCore.ControlPanelId.A,
                                    "ZOOM",
                                    value
                            );
                    //DEBUG:
                    //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

                    outputBus.publish(event);
                }
        );

        JLabel zoomLabel = new JLabel("ZOOM", SwingConstants.CENTER);
        zoomLabel.setForeground(labelColor);

        JPanel zoomPanel = new JPanel(new BorderLayout());
        zoomPanel.setBackground(backgroundColor);
        zoomPanel.add(zoomLabel, BorderLayout.NORTH);
        zoomPanel.add(zoomASlider, BorderLayout.CENTER);
        JPanel zoomBottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        zoomBottomPanel.setBackground(backgroundColor);
        zoomBottomPanel.setOpaque(false);
        zoomBottomPanel.add(autoFollowModeButton);
        autoFollowModeButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            "AUTO_SHOOT_KEY",
                            ControlCore.ControlPanelId.A,
                            "AUTO_SHOOT_KEY",
                            1
                    );
            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

            outputBus.publish(event);
        });

        zoomPanel.add(zoomBottomPanel, BorderLayout.SOUTH);

        onAirIndicator.setPreferredSize(new Dimension(60, 40));

        // ==== JOG ====
        jogAIndicator = new JogIndicator();
        jogAIndicator.setPreferredSize(new Dimension(200, 80));
        jogAIndicator.setOnJogChange((direction, speed) -> {
                int value =  direction * speed;
                ControlEvent event =
                        new ControlEvent(
                                GUI,
                                "GUI_JOG_A",
                                ControlCore.ControlPanelId.A,
                                "PAN",
                                value
                        );
                //DEBUG:
                //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

                outputBus.publish(event);
            }
        );

        JPanel jogPanel =new JPanel(new BorderLayout());
        jogPanel.setBackground(backgroundColor);

        jogPanel.setOpaque(false);

        jogPanel.add(onAirIndicator,BorderLayout.NORTH);
        jogPanel.add(jogAIndicator,BorderLayout.CENTER);

        //========== MAIN LAYOUT ===========

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(backgroundColor);
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridy = 0;
        gbc.insets = new Insets(5, 15, 5, 15);

        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.VERTICAL;

        gbc.gridx = 0;
        controls.add(tiltPanel, gbc);

        gbc.gridx = 1;
        controls.add(jogPanel, gbc);

        gbc.gridx = 2;
        controls.add(zoomPanel, gbc);

        //MAIN PANELS
        frame.add(exposurePanel,BorderLayout.NORTH);
        frame.add(controls, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    // ============================================
    //        INCOMING ControlEvent HANDLING
    // ============================================

    @Override
    public void onControlEvent(ControlEvent event) {

        SwingUtilities.invokeLater(() -> handleControlEvent(event));
    }

    public void handleControlEvent(ControlEvent event) {

        //DEBUG
        /*System.out.println("======= GUI ============= \n->EVENT from: " + event.getEventSource() +
                "CONTROL ID = " + event.getControlID() + " PANEL: " + event.getPanel() + "   VALUE = " + event.getValue());
*/
        SwingUtilities.invokeLater(() -> {

            switch (event.getAction()) {

                case "PAN":
                    updateGUIJog(event.getValue(), event.getPanel());
                    break;

                case "TILT":
                    updateTiltSlider(event.getValue(), event.getPanel());
                    break;

                case "ZOOM":
                    updateZoomSlider(event.getValue(), event.getPanel());
                    break;

                case "EXPOSURE_COMPENSATION":
                    updateExposureCompensationKnob (event.getValue(), event.getPanel());
                    break;

                case "EXPOSURE_GAIN":
                    updateExposureCompensationKnob (event.getValue(), event.getPanel());
                    break;

                case "EXPOSURE_SHUTTER":
                    updateShutterKnob (event.getValue(), event.getPanel());
                    break;

                case "EXPOSURE_IRIS":
                    updateIrisKnob (event.getValue(), event.getPanel());
                    break;

                case "ABSOLUTE_MODE_INDICATOR":
                    handleAbsoluteModeStatus(event.getValue(), event.getPanel());
                    break;

                case "AUTO_SHOOT_INDICATOR":
                    handleAutoFollowStatus(event.getValue(), event.getPanel());
                    break;

                case "EXPOSURE_MODE_INDICATOR":
                    handleExposureModeStatus(event.getValue(), event.getPanel());
                    break;

                case "TALLY_INDICATOR":
                    if (event.getValue() == 1){
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

    public void updateTiltSlider(int value, ControlCore.ControlPanelId panelId) {
        switch (panelId) {
            case A:
                tiltASlider.setValueSilently(value);
                break;
            case B:
                //placeholder panel B
        }
    }

    public void updateZoomSlider(int value, ControlCore.ControlPanelId panelId) {
        switch (panelId) {
            case A:
                zoomASlider.setValueSilently(value);
                break;
            case B:
                //placeholder panel B
        }
    }

    public void updateGUIJog(int value, ControlCore.ControlPanelId panelId) {

        switch (panelId){
            case A:
                jogAIndicator.setValueSilently(value);
                break;
            case B:
                // placeholder Panel B

        }
    }

    public void updateExposureCompensationKnob(int value, ControlCore.ControlPanelId panelId) {
        switch (panelId) {
            case A:
                exposureCompensationKnob.setValueSilently(value);
                break;
            case B:
                //placeholder panel B
        }
    }

    public void updateShutterKnob(int value, ControlCore.ControlPanelId panelId) {
        switch (panelId) {
            case A:
                shutterKnob.setValueSilently(value);
                break;
            case B:
                //placeholder panel B
        }
    }

    public void updateIrisKnob(int value, ControlCore.ControlPanelId panelId) {
        switch (panelId) {
            case A:
                irisKnob.setValueSilently(value);
                break;
            case B:
                //placeholder panel B
        }
    }


    // ==================== INDICATORS UPDATE ====================

    private void handleAbsoluteModeStatus(int state, ControlCore.ControlPanelId panelId) {

        switch (state) {

            case 0:
                switch (panelId) {
                    case A:
                        tiltModeButton.setMode(buttonLED.LedMode.OFF);
                        tiltASlider.setJoystickMode(true);
                        jogAIndicator.setJoystickMode(true);
                        break;
                    case B:
                        //placeholder panel B
                }
                break;

            case 1:
                switch (panelId) {
                    case A:
                        tiltModeButton.setMode(buttonLED.LedMode.ON);
                        tiltASlider.setJoystickMode(false);
                        jogAIndicator.setJoystickMode(false);
                        break;
                    case B:
                        //placeholder panel B
                }
                break;

            case 2:
                switch (panelId){
                    case A:
                        tiltModeButton.setMode(buttonLED.LedMode.BLINK);
                        tiltASlider.setJoystickMode(true);
                        jogAIndicator.setJoystickMode(false);
                        break;
                    case B:
                        //placeholder panel B
                }
                break;
        }
    }

    private void handleAutoFollowStatus(int state, ControlCore.ControlPanelId panelId) {

        switch (state) {

            case 0:
                switch (panelId) {
                    case A:
                        autoFollowModeButton.setMode(buttonLED.LedMode.OFF);
                        tiltASlider.setEnabled(true);
                        zoomASlider.setEnabled(true);
                        tiltModeButton.setEnabled(true);
                        break;
                    case B:
                        //placeholder panel B
                }
                break;

            case 1:
                switch (panelId) {
                    case A:
                        autoFollowModeButton.setMode(buttonLED.LedMode.ON);
                        tiltASlider.setEnabled(false);
                        zoomASlider.setEnabled(false);
                        tiltModeButton.setEnabled(false);
                        break;
                    case B:
                        //placeholder panel B
                }
                break;

        }
    }

    private void handleExposureModeStatus(int state, ControlCore.ControlPanelId panelId) {

        switch (state) {

            case 0:
                switch (panelId) {
                    case A:
                        exposureModeButton.setMode(buttonLED.LedMode.OFF);
                        exposureLabel.setText("GAIN");
                        FieldDefinition gain = cameraManager.getCamera("CAM_A")
                                .getController().getFieldDefinition("exposureGainPosition");
                        exposureCompensationKnob.configure(gain);
                        shutterKnob.setActive(true);
                        irisKnob.setActive(true);
                        break;
                    case B:
                        //placeholder panel B
                }
                break;

            case 1:
                switch (panelId) {
                    case A:
                        exposureModeButton.setMode(buttonLED.LedMode.ON);
                        FieldDefinition exposureCompensation = cameraManager.getCamera("CAM_A")
                                .getController().getFieldDefinition("exposureCompensationPosition");
                        exposureCompensationKnob.configure(exposureCompensation);
                        exposureLabel.setText("EXP. CMP");
                        shutterKnob.setActive(false);
                        irisKnob.setActive(false);
                        break;
                    case B:
                        //placeholder panel B
                }
                break;

        }
    }

    // ========================== GETTERS & SETTERS

    public void setCameraManager(PTZCameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }
}

