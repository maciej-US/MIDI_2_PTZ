package uci.bcd2ptz.gui;

import uci.bcd2ptz.AppConfig;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlEvent;
import uci.bcd2ptz.core.ControlEventBus;
import uci.bcd2ptz.core.ControlEventListener;
import uci.bcd2ptz.midi.MidiControllerManager;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.visca.FieldDefinition;

import javax.swing.*;
import java.awt.*;

import static uci.bcd2ptz.core.ControlEvent.EventSource.GUI;
import static uci.bcd2ptz.core.ControlEvent.EventSource.SYSTEM;
import static uci.bcd2ptz.gui.ButtonLED.*;


public class GUIPanel
        extends JPanel
        implements ControlEventListener{


    // ==================== GUI ELEMENTS ====================

    //SLIDERS
    private VerticalSlider tiltSlider;
    private VerticalSlider zoomSlider;

    //JOG
    private JogIndicator jogIndicator;

    //KNOBS
    private RotaryKnob exposureCompensationKnob;
    private RotaryKnob irisKnob;
    private RotaryKnob shutterKnob;
    private JLabel exposureLabel;
    private RotaryKnob whiteBalanceRGainKnob;
    private RotaryKnob whiteBalanceBGainKnob;
    private RotaryKnob focusKnob;

    //SWITCHES
    private RotarySwitch whiteBalanceMode;

    //BUTTONS
    private final ButtonLED PTZModeButton = new ButtonLED("ABS");
    private final ButtonLED autoFollowModeButton = new ButtonLED("A-SH");
    private final ButtonLED exposureModeButton = new ButtonLED("AUTO");
    private final ButtonLED focusModeButton = new ButtonLED("A-FC");
    private final ButtonLED focusOnePushButton = new ButtonLED("PUSH");
    private final ButtonLED onePushWB = new ButtonLED("WB");
    private final Button focusMoveUpButton = new Button(">>");
    private final Button focusMoveDownButton = new Button("<<");

    //INDICATORS
    private final OnAirIndicator onAirIndicator = new OnAirIndicator();
    private Dseg14Display midiPanelInfo = null;

    //==================== VISUAL CONSTANSTS =================
    Color labelColor = new Color(200, 200, 200);
    Color backgroundColor = new Color(40, 40, 40);

    // ==================== CORE INTERFACE ====================

    private final ControlEventBus outputBus;
    private final PTZCameraManager cameraManager;
    private final MidiControllerManager midiManager;
    ControlCore.ControlPanelId panelId;
    private volatile boolean initialized = false;
    private final AppConfig appConfig;


    //============ CONSTRUCTOR ===================

    public GUIPanel(ControlCore.ControlPanelId panelId, ControlEventBus outputBus,  PTZCameraManager cameraManger, MidiControllerManager midiManager, AppConfig appConfig) {

        super (new GridBagLayout());

        //DEBUG:
        //System.out.println("GUIPanel " + panelId + " constructor");

        this.cameraManager = cameraManger;
        this.midiManager = midiManager;
        this.panelId = panelId;
        this.outputBus = outputBus;
        this.appConfig = appConfig;

        initialize();
    }


    // ====================
    //        INIT
    // ====================

    private void initialize() {

        //DEBUG
        //System.out.println("GUIPanel " + panelId + " initialize");

        /* ===================================
            MAIN PANEL ELEMENTS DEFINITIONS
         ====================================*/

        //========EXPOSURE CONTROL ELEMENTS

        //------ Auto mode Button
        exposureModeButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "EXPOSURE_MODE",
                            1
                    );
            outputBus.publish(event);
        });

        //----- IRIS knob
        irisKnob = new RotaryKnob();

        FieldDefinition field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("exposureIrisPosition");
        irisKnob.configure(field);
        irisKnob.setValue(64);
        irisKnob.setOnValueChange(
                value -> {
                    ControlEvent event = new ControlEvent(
                            GUI,
                            panelId,
                            "EXPOSURE_IRIS",
                            value
                    );
                    outputBus.publish(event);
                }
        );
        //---- IRIS label
        JLabel irisLabel = new JLabel("IRIS", SwingConstants.CENTER);
        irisLabel.setForeground(labelColor);

        //---- SHUTTER knob
        shutterKnob = new RotaryKnob();

        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("exposureShutterPosition");
        shutterKnob.configure(field);
        shutterKnob.setValue(64);
        shutterKnob.setOnValueChange(
                value -> {
                    ControlEvent event = new ControlEvent(
                            GUI,
                            panelId,
                            "EXPOSURE_SHUTTER",
                            value
                    );
                    outputBus.publish(event);
                }
        );

        // ---- SHUTTER Lable
        JLabel shutterLabel = new JLabel("SHUTTER", SwingConstants.CENTER);
        shutterLabel.setForeground(labelColor);

        // ---- EXPOSURE COMPENSATION / GAIN knob
        exposureCompensationKnob = new RotaryKnob();

        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("exposureCompensationPosition");
        exposureCompensationKnob.configure(field);
        exposureCompensationKnob.setValue(64);
        exposureCompensationKnob.setOnValueChange(
                value -> {
                    ControlEvent event = new ControlEvent(
                            GUI,
                            panelId,
                            "EXPOSURE_COMPENSATION",
                            value
                    );
                    outputBus.publish(event);
                }
        );

        //--- EXPOSURE COMPENSATION / GAIN label
        exposureLabel = new JLabel("EXP. COMP.", SwingConstants.CENTER);
        exposureLabel.setForeground(labelColor);


        // ==== WHITE BALANCE CONTROL ELEMENTS

        // ---- WhiteBalance Mode SWITCH
        whiteBalanceMode = new RotarySwitch();
        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("whiteBalanceMode");
        whiteBalanceMode.configure(field);
        whiteBalanceMode.setValue(0);
        whiteBalanceMode.setOnValueChange(
                value -> {
                    ControlEvent event = new ControlEvent(
                            GUI,
                            panelId,
                            "WHITE_BALANCE_MODE",
                            value
                    );

                    //DEBUG:
                    //System.out.println("WHITE BALANCE MODE EVENT: "+ event);

                    outputBus.publish(event);
                }
        );

        //----- WHITE BALANCE SWITCH Label
        JLabel whiteBalanceModeLabel = new JLabel("WH. BAL", SwingConstants.CENTER);
        whiteBalanceModeLabel.setForeground(labelColor);


        //--- ONE PUSH WHITE BALANCE button
        onePushWB.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "ONE_PUSH_WHITE_BALANCE_KEY",
                            1
                    );
            outputBus.publish(event);
        });

        //--- WB BLUE GAIN knob
        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("whiteBalanceBGainPosition");
        whiteBalanceBGainKnob = new RotaryKnob();
        whiteBalanceBGainKnob.configure(field);
        whiteBalanceBGainKnob.setValue(32);
        whiteBalanceBGainKnob.setOnValueChange(
                value -> {
                    ControlEvent event = new ControlEvent(
                            GUI,
                            panelId,
                            "WHITE_BALANCE_B_GAIN",
                            value
                    );
                    outputBus.publish(event);
                }
        );

        //--- WHITE BALANCE BLUE GAIN label
        JLabel whiteBalanceBGainLabel = new JLabel("BLUE GAIN", SwingConstants.CENTER);
        whiteBalanceBGainLabel.setForeground(labelColor);


        //-------- WB Red GAIN KNOB
        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("whiteBalanceRGainPosition");
        whiteBalanceRGainKnob = new RotaryKnob();
        whiteBalanceRGainKnob.configure(field);
        whiteBalanceRGainKnob.setValue(32);
        whiteBalanceRGainKnob.setOnValueChange(
                value -> {
                    ControlEvent event = new ControlEvent(
                            GUI,
                            panelId,
                            "WHITE_BALANCE_R_GAIN",
                            value
                    );
                    outputBus.publish(event);
                }
        );

        //---- WHITE BALANCE RED GAIN label
        JLabel whiteBalanceRGainLabel = new JLabel("RED GAIN", SwingConstants.CENTER);
        whiteBalanceRGainLabel.setForeground(labelColor);


        // ==== TILT PANEL ELEMENTS

        // --- TILT Slider
        tiltSlider = new VerticalSlider();
        tiltSlider.setJoystickMode(true);
        tiltSlider.setValue(64);
        tiltSlider.setOnValueChange(
                value -> {
                    ControlEvent event =
                            new ControlEvent(
                                    GUI,
                                    panelId,
                                    "TILT",
                                    value
                            );
                    //DEBUG:
                    //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());
                    outputBus.publish(event);
                }
        );

        //---- TILT Label
        JLabel tiltLabel = new JLabel("TILT", SwingConstants.CENTER);
        tiltLabel.setForeground(labelColor);

        // ==== ZOOM PANEL ELEMENTS

        // --- ZOOM Slider
        zoomSlider = new VerticalSlider();
        zoomSlider.setJoystickMode(false);
        zoomSlider.setOnValueChange(
                value -> {
                    ControlEvent event =
                            new ControlEvent(
                                    GUI,
                                    panelId,
                                    "ZOOM",
                                    value
                            );
                    //DEBUG:
                    //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

                    outputBus.publish(event);
                }
        );

        // ---- ZOOM label
        JLabel zoomLabel = new JLabel("ZOOM", SwingConstants.CENTER);
        zoomLabel.setForeground(labelColor);

        // ====== FOCUS CONTROL ELEMENTS

        // ---- FOCUS knob
        focusKnob = new RotaryKnob();
        field = cameraManager.getCamera("CAM_A").getController().getFieldDefinition("focusPosition");
        focusKnob.configure(field);
        focusKnob.setOnValueChange(
                value -> {
                    ControlEvent event =
                            new ControlEvent(
                                    GUI,
                                    panelId,
                                    "FOCUS",
                                    value
                            );
                    //DEBUG:
                    //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

                    outputBus.publish(event);
                }
        );

        // ---- FOCUS label
        JLabel focusLabel = new JLabel("FOCUS", SwingConstants.CENTER);
        focusLabel.setForeground(labelColor);

        // ---- FOCUS UP Button
        focusMoveUpButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "FOCUS_UP_KEY",
                            1
                    );
            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

            outputBus.publish(event);
        });

        // ---- FOCUS DOWN Button
        focusMoveDownButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "FOCUS_DOWN_KEY",
                            1
                    );
            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

            outputBus.publish(event);
        });

        focusOnePushButton.setMode(LedMode.OFF);
        focusOnePushButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "ONE_PUSH_FOCUS_KEY",
                            1
                    );
            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction() + "  source = " + event.getEventSource());
            outputBus.publish(event);
        });

        // ==== JOG ====
        jogIndicator = new JogIndicator();
        jogIndicator.setOnJogChange((direction, speed) -> {
                    int value = direction * speed;
                    ControlEvent event =
                            new ControlEvent(
                                    GUI,
                                    panelId,
                                    "PAN",
                                    value
                            );
                    outputBus.publish(event);
                }
        );

        //====BUTTONS PANEL BUTTONS

        // ---- AUTOFOLLOW button
        autoFollowModeButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "AUTO_FOLLOW",
                            1
                    );
            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

            outputBus.publish(event);
        });

        // ---- FOCUS MODE Button
        focusModeButton.setMode(LedMode.ON);
        focusModeButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "FOCUS_MODE",
                            1
                    );
            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());

            outputBus.publish(event);
        });

        // ---- PTZ MODE BUTTON
        PTZModeButton.setOnClick(() -> {
            ControlEvent event =
                    new ControlEvent(
                            GUI,
                            panelId,
                            "PTZ_MODE",
                            1
                    );

            //DEBUG:
            //System.out.println("PUBLISH: " + event.getAction()+ "  source = " + event.getEventSource());
            outputBus.publish(event);
        });


        //=========== INFO PANEL elements

        //--- ON AIR indicator
        onAirIndicator.setPreferredSize(new Dimension(80, 30));
        onAirIndicator.setMaximumSize(new Dimension(80, 30));
        onAirIndicator.setMinimumSize(new Dimension(80, 30));

        //--- MIDI Panel indicatori
        midiPanelInfo = new Dseg14Display(appConfig.getSystem().getGuiConstraints().getLcdFontPath());
        midiPanelInfo.setValue(String.valueOf(midiManager.getMidiBinding(panelId)));

        //==================================
        //             MAIN LAYOUT
        // =================================

        //==== EXPOSURE PANEL
        JPanel exposurePanel = new JPanel(new GridBagLayout());
        exposurePanel.setBackground(backgroundColor);
        GridBagConstraints expPanelGbc = new GridBagConstraints();
        expPanelGbc.insets = new Insets(3, 1, 1, 1);

        //--- Iris knob ===> EXPOSURE PANEL
        expPanelGbc.gridx = 1;
        expPanelGbc.gridy = 0;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(irisKnob, expPanelGbc);

        //--- iris label ===> EXPOSURE PANEL
        expPanelGbc.gridx = 1;
        expPanelGbc.gridy = 1;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.NORTH;
        exposurePanel.add(irisLabel, expPanelGbc);

        //--- shutter knob ===> EXPOSURE PANEL
        expPanelGbc.gridx = 0;
        expPanelGbc.gridy = 0;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(shutterKnob, expPanelGbc);

        //--- shutter label ===> EXPOSURE PANEL
        expPanelGbc.gridx = 0;
        expPanelGbc.gridy = 1;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.NORTH;
        exposurePanel.add(shutterLabel, expPanelGbc);

        //--- exposure/gain knob ===> EXPOSURE PANEL
        expPanelGbc.gridx = 2;
        expPanelGbc.gridy = 0;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        exposurePanel.add(exposureCompensationKnob, expPanelGbc);

        //--- exposure/gain label ===> EXPOSURE PANEL
        expPanelGbc.gridx = 2;
        expPanelGbc.gridy = 1;
        expPanelGbc.gridheight = 1;
        expPanelGbc.anchor = GridBagConstraints.NORTH;
        exposurePanel.add(exposureLabel, expPanelGbc);

        //--- EXPOSURE AUTO MODE button ===> EXPOSURE PANEL
        expPanelGbc.gridx = 3;
        expPanelGbc.gridy = 0;
        expPanelGbc.anchor = GridBagConstraints.CENTER;
        expPanelGbc.insets = new Insets(2, 5, 2, 10);
        exposurePanel.add(exposureModeButton, expPanelGbc);

        // ===== WHITE BALANCE PANEL
        JPanel whiteBalancePanel = new JPanel(new GridBagLayout());
        whiteBalancePanel.setBackground(backgroundColor);
        GridBagConstraints whiteBalanceGbc = new GridBagConstraints();
        whiteBalanceGbc.insets = new Insets(2, 5, 2, 5);

        //--- Red gain knob ===> WHITE BALANCE PANEL
        whiteBalanceGbc.gridx = 0;
        whiteBalanceGbc.gridy = 0;
        whiteBalanceGbc.gridheight = 1;
        whiteBalanceGbc.anchor = GridBagConstraints.CENTER;
        whiteBalancePanel.add(whiteBalanceRGainKnob, whiteBalanceGbc);

        //--- exposure/gain label ===> WHITE BALANCE PANEL
        whiteBalanceGbc.gridx = 0;
        whiteBalanceGbc.gridy = 1;
        whiteBalanceGbc.gridheight = 1;
        whiteBalanceGbc.anchor = GridBagConstraints.NORTH;
        whiteBalancePanel.add(whiteBalanceRGainLabel, whiteBalanceGbc);


        //--- BLUE gain knob ===> WHITE BALANCE PANEL
        whiteBalanceGbc.gridx = 1;
        whiteBalanceGbc.gridy = 0;
        whiteBalanceGbc.gridheight = 1;
        whiteBalanceGbc.anchor = GridBagConstraints.CENTER;
        whiteBalancePanel.add(whiteBalanceBGainKnob, whiteBalanceGbc);

        //--- exposure/gain label ===> WHITE BALANCE PANEL
        whiteBalanceGbc.gridx = 1;
        whiteBalanceGbc.gridy = 1;
        whiteBalanceGbc.gridheight = 1;
        whiteBalanceGbc.anchor = GridBagConstraints.NORTH;
        whiteBalancePanel.add(whiteBalanceBGainLabel, whiteBalanceGbc);

        //--- WB Mode switch ==> WB PANEL
        whiteBalanceGbc.insets = new Insets(2, 8, 2, 8);
        whiteBalanceGbc.gridx = 2;
        whiteBalanceGbc.gridy = 0;
        whiteBalancePanel.add(whiteBalanceMode, whiteBalanceGbc);

        //--- WB Mode label ==> WB PANEL
        whiteBalanceGbc.insets = new Insets(2, 2, 2, 2);
        whiteBalanceGbc.gridx = 2;
        whiteBalanceGbc.gridy = 1;
        whiteBalancePanel.add(whiteBalanceModeLabel, whiteBalanceGbc);

        //--- WB ONE PUSH button ===> WB PANEL
        whiteBalanceGbc.gridx = 3;
        whiteBalanceGbc.gridy = 0;
        whiteBalancePanel.add(onePushWB, whiteBalanceGbc);


        //===== BUTTONS panel
        JPanel buttonsPanel = new JPanel(new GridBagLayout());
        buttonsPanel.setBackground(backgroundColor);
        GridBagConstraints buttonsPanelGBC = new GridBagConstraints();
        buttonsPanelGBC.insets = new Insets(2, 4, 2, 4);
        buttonsPanelGBC.gridy = 0;

        //----- AbsoluteMode Button ===> Buttons Panel
        buttonsPanelGBC.gridx = 0;
        buttonsPanel.add(PTZModeButton, buttonsPanelGBC);
        //---- AutoFollow Button ===> Buttons Panel
        buttonsPanelGBC.gridx = 1;
        buttonsPanel.add(autoFollowModeButton, buttonsPanelGBC);
        //---- Focus Mode Button ===> Buttons Panel
        buttonsPanelGBC.gridx = 2;
        buttonsPanel.add(focusModeButton, buttonsPanelGBC);

        //===== FOCUS PANEL
        JPanel focusPanel = new JPanel(new GridBagLayout());
        focusPanel.setBackground(backgroundColor);
        GridBagConstraints focusGBC = new GridBagConstraints();
        focusGBC.fill = GridBagConstraints.NONE;
        focusGBC.anchor = GridBagConstraints.CENTER;

        // ---- FOCUS Label ===> FOCUS PANEL
        focusGBC.insets = new Insets(0,0,2,0);
        focusGBC.gridx = 0;
        focusGBC.gridy = 0;
        focusGBC.gridwidth = 2;
        focusPanel.add(focusLabel, focusGBC);

        // ---- FOCUS Knob ===> FOCUS PANEL
        focusGBC.insets = new Insets(0,0,4,0);
        focusGBC.gridy = 1;
        focusPanel.add(focusKnob, focusGBC);

        // ---- FOCUS UP button ===> FOCUS PANEL
        focusGBC.gridwidth = 1;
        focusGBC.gridx = 1;
        focusGBC.gridy = 2;
        focusPanel.add(focusMoveUpButton, focusGBC);

        // ---- FOCUS DOWN button ===> FOCUS PANEL
        focusGBC.gridx = 0;
        focusGBC.gridy = 2;
        focusPanel.add(focusMoveDownButton, focusGBC);

        // ---- ONE PUSH FOCUS button ===> FOCUS PANEL
        focusGBC.insets = new Insets(3,0,0,0);
        focusGBC.gridwidth = 2;
        focusGBC.gridx = 0;
        focusGBC.gridy = 3;
        focusPanel.add(focusOnePushButton, focusGBC);


        // ==== TILT PANEL
        JPanel tiltPanel = new JPanel(new BorderLayout());
        tiltPanel.setBackground(backgroundColor);

        // ---- TILT slider ===> TILT PANEL
        tiltPanel.add(tiltLabel, BorderLayout.NORTH);

        // ---- TILT label ===> TILT PANEL
        tiltPanel.add(tiltSlider, BorderLayout.CENTER);


        //===== ZOOM Panel
        JPanel zoomPanel = new JPanel(new BorderLayout());
        zoomPanel.setBackground(backgroundColor);

        // ---- ZOOM slider ===> ZOOM PANEL
        zoomPanel.add(zoomLabel, BorderLayout.NORTH);

        // ---- ZOOM label ===> ZOOM PANEL
        zoomPanel.add(zoomSlider, BorderLayout.CENTER);

        // ===== UPPER PANEL COMPOSITION

        JPanel upperPanel = new JPanel(new GridBagLayout());
        upperPanel.setBackground(backgroundColor);
        GridBagConstraints upperPanelGBC = new GridBagConstraints();
        upperPanelGBC.insets = new Insets(3, 3, 2, 3);
        upperPanelGBC.fill = GridBagConstraints.NONE;
        upperPanelGBC.anchor = GridBagConstraints.CENTER;

        // ---- WB PANEL ===> UPPER PANEL
        upperPanelGBC.gridx= 0;
        upperPanelGBC.gridy= 0;
        upperPanel.add(whiteBalancePanel, upperPanelGBC);

        // ---- horizontal separator
        HorizontalSeparator upperHorSeparator = new HorizontalSeparator();
        upperPanelGBC.insets = new Insets(5, 5, 5, 5);
        upperPanelGBC.fill = GridBagConstraints.HORIZONTAL;
        upperPanelGBC.gridy = 1;
        upperPanel.add(upperHorSeparator, upperPanelGBC);

        // ----- EXPOSURE PANEL ===> UPPER PANEL
        upperPanelGBC.insets = new Insets(0, 0, 0, 0);
        upperPanelGBC.fill = GridBagConstraints.NONE;
        upperPanelGBC.gridy= 2;
        upperPanel.add(exposurePanel, upperPanelGBC);

        // ---- Vertical separator
        VerticalSeparator upperVerSeparator = new VerticalSeparator();
        upperPanelGBC.fill = GridBagConstraints.VERTICAL;
        upperPanelGBC.insets = new Insets(5, 5, 5, 5);
        upperPanelGBC.weighty = 1.0;
        upperPanelGBC.gridy = 0;
        upperPanelGBC.gridx = 1;
        upperPanelGBC.gridheight = 3;
        upperPanel.add(upperVerSeparator, upperPanelGBC);

        // ----- FOCUS PANEL ===> UPPER PANEL
        upperPanelGBC.gridx= 2;
        upperPanelGBC.gridy= 0;
        upperPanelGBC.gridheight = 3;
        upperPanel.add(focusPanel, upperPanelGBC);

        // ======== STATUS PANEL COMPOSITION
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBackground(backgroundColor);
        GridBagConstraints statusPanelGBC = new GridBagConstraints();
        statusPanelGBC.fill = GridBagConstraints.NONE;
        statusPanelGBC.insets = new Insets(0, 6, 0, 6);

        // --- MIDI panel indicator ===> STATUS PANEL
        statusPanelGBC.gridx = 0;
        statusPanel.add(midiPanelInfo, statusPanelGBC);

        //---  ON AIR indicator ===> STATUS PANEL
        statusPanelGBC.gridx = 1;
        statusPanel.add(onAirIndicator, statusPanelGBC);


        //====== CENTRAL PANEL COMPOSITION

        JPanel centralPanel = new JPanel(new GridBagLayout());
        centralPanel.setBackground(backgroundColor);
        GridBagConstraints centralPanelGBC = new GridBagConstraints();
        centralPanelGBC.insets = new Insets(2, 0, 2, 0);
        centralPanelGBC.gridx = 0;

        //------- Buttons panel ===> CENTRAL PANEL
        centralPanelGBC.gridy = 0;
        centralPanel.add(buttonsPanel, centralPanelGBC);

        // ------ STATUS PANEL indicator ===> CENTRAL PANEL
        centralPanelGBC.gridy = 1;
        centralPanel.add(statusPanel, centralPanelGBC);

        //---- Jog component ==> CENTRAL PANEL
        centralPanelGBC.gridy = 2;
        centralPanel.add(jogIndicator, centralPanelGBC);

        //======== CONTROL PANEL COMPOSITION
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBackground(backgroundColor);
        GridBagConstraints controlPanelGBC = new GridBagConstraints();
        controlPanelGBC.insets = new Insets(0, 1, 0, 1);
        controlPanelGBC.gridy = 0;

        //------ Tilt slider ===> CONTROL PANEL
        controlPanelGBC.gridx = 0;
        //controlPanelGBC.fill = GridBagConstraints.BOTH;
        controlPanel.add(tiltPanel, controlPanelGBC);

        //-------- Central Panel  ====> CONTROL PANEL
        controlPanelGBC.gridx = 1;
        //controlPanelGBC.fill = GridBagConstraints.BOTH;
        controlPanel.add(centralPanel, controlPanelGBC);

        //-------- Zoom slider  ====> CONTROL PANEL
        controlPanelGBC.gridx = 2;
        //controlPanelGBC.fill = GridBagConstraints.BOTH;
        controlPanel.add(zoomPanel, controlPanelGBC);


        //========== MAIN PANEL COMPOSITION

        setBackground(new Color(80, 80, 80));
        setLayout(new BorderLayout());
        setSize(460, 520);


        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(backgroundColor);
        GridBagConstraints mainPanelGBC = new GridBagConstraints();
        mainPanelGBC.insets = new Insets(0, 2, 3, 0);
        mainPanelGBC.gridx = 0;

        //------- UPPER PANEL ===> MAIN PANEL
        mainPanelGBC.gridy = 0;
        mainPanelGBC.gridwidth = 3;
        mainPanel.add(upperPanel, mainPanelGBC);

        //----- SEPARATOR ==> MAIN PANEL
        HorizontalSeparator horizontalSeparator1 = new HorizontalSeparator();
        mainPanelGBC.insets = new Insets(2, 5, 2, 5);
        mainPanelGBC.gridy = 1;
        mainPanelGBC.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(horizontalSeparator1, mainPanelGBC);

        //--------- COntrol panel ===> MAIN PANEL
        mainPanelGBC.insets = new Insets(0, 0, 0, 0);
        mainPanelGBC.gridy = 2;
        mainPanel.add(controlPanel, mainPanelGBC);
        add(mainPanel, BorderLayout.CENTER);

        setVisible(true);

        //DEBUG
        //System.out.println("GUI Panel " + panelId + " Initialized");

        initialized = true;
    }

    // ============================================
    //        INCOMING ControlEvent HANDLING
    // ============================================

    @Override
    public void onControlEvent(ControlEvent event) {

        if (event.getPanel() != panelId){
            return;
        }

        SwingUtilities.invokeLater(() -> handleControlEvent(event));
    }

    public void handleControlEvent(ControlEvent event) {

        //DEBUG
       /* System.out.println("======= GUI panel " + panelId + " ============= \n->EVENT from: " + event.getEventSource() +
                "  ACTION = " + event.getAction() + " PANEL: " + event.getPanel() + "   VALUE = " + event.getValue());*/

        if (!initialized) {
            return;
        }

        if (event.getEventSource() != SYSTEM){
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
                    focusKnob.setValueSilently(event.getValue());
                    break;

                case "EXPOSURE_COMPENSATION":
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

                case "WHITE_BALANCE_R_GAIN":
                    whiteBalanceRGainKnob.setValueSilently(event.getValue());
                    break;

                case "WHITE_BALANCE_B_GAIN":
                    whiteBalanceBGainKnob.setValueSilently(event.getValue());
                    break;

                case "WHITE_BALANCE_PUSH_INDICATOR":
                    handleWBOnePushStatus(event.getValue());
                    break;

                case "ABSOLUTE_MODE_INDICATOR":
                    handleAbsoluteModeStatus(event.getValue());
                    break;

                case "AUTO_FOLLOW_INDICATOR":
                    handleAutoFollowStatus(event.getValue());
                    break;

                case "EXPOSURE_MODE_INDICATOR":
                    handleExposureModeStatus(event.getValue());
                    break;

                case "ONE_PUSH_FOCUS_INDICATOR":
                    focusOnePushButton.setMode(
                        switch (event.getValue()) {
                            case 1 -> LedMode.ON;
                            case 2 -> LedMode.BLINK;
                            default -> LedMode.OFF;
                        });

                case "FOCUS_MODE_INDICATOR":
                    handleFocusMode(event.getValue());
                    break;

                case "TALLY_INDICATOR":
                    onAirIndicator.setOnAir(event.getValue() == 1);
                    break;

                //IGNORED EVENTS
                case "CAM_SELECT_1_INDICATOR":
                case "CAM_SELECT_2_INDICATOR":
                case "CAM_SELECT_3_INDICATOR":
                case "CAM_SELECT_4_INDICATOR":
                case "PANEL_SELECT_INDICATOR":

                    break;

                default:
                    System.out.println("GUI => Unhandled event: " + event);
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
                PTZModeButton.setMode(LedMode.OFF);
                tiltSlider.setJoystickMode(true);
                jogIndicator.setJoystickMode(true);
                break;

            case 1:
                PTZModeButton.setMode(LedMode.ON);
                tiltSlider.setJoystickMode(false);
                jogIndicator.setJoystickMode(false);
                break;

            case 2:
                PTZModeButton.setMode(LedMode.BLINK);
                tiltSlider.setJoystickMode(true);
                jogIndicator.setJoystickMode(false);
                break;
        }
    }

    private void handleAutoFollowStatus(int state) {

        switch (state) {
            case 0:
                autoFollowModeButton.setMode(LedMode.OFF);
                tiltSlider.setEnabled(true);
                zoomSlider.setEnabled(true);
                PTZModeButton.setEnabled(true);
                break;

            case 1:
                autoFollowModeButton.setMode(LedMode.ON);
                tiltSlider.setEnabled(false);
                zoomSlider.setEnabled(false);
                PTZModeButton.setEnabled(false);
                break;
        }
    }

    private void handleFocusMode(int state) {

        switch (state) {

            case 0:
                focusModeButton.setMode(LedMode.OFF);
                focusKnob.setEnabled(true);
                focusOnePushButton.setEnabled(true);
                focusOnePushButton.setMode(LedMode.BLINK);
                focusMoveUpButton.setEnabled(true);
                focusMoveDownButton.setEnabled(true);
                break;

            case 1:
                focusModeButton.setMode(LedMode.ON);
                focusKnob.setEnabled(false);
                focusOnePushButton.setEnabled(false);
                focusOnePushButton.setMode(LedMode.OFF);
                focusMoveUpButton.setEnabled(false);
                focusMoveDownButton.setEnabled(false);
                break;
        }
    }

    private void handleExposureModeStatus(int state) {

        switch (state) {
            case 0:
                exposureModeButton.setMode(LedMode.OFF);
                exposureLabel.setText("GAIN");
                FieldDefinition gain = cameraManager.getCamera("CAM_A")
                        .getController().getFieldDefinition("exposureGainPosition");
                exposureCompensationKnob.configure(gain);
                shutterKnob.setActive(true);
                irisKnob.setActive(true);
                break;

            case 1:
                exposureModeButton.setMode(LedMode.ON);
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
                onePushWB.setMode(LedMode.OFF);
                break;

            case 1:
                onePushWB.setMode(LedMode.ON);
                break;

            case 2:
                onePushWB.setMode(LedMode.BLINK);
                break;
        }
    }

    //MIDI INDICATOR update
    public Dseg14Display getMidiPanelInfo(){
        return this.midiPanelInfo;
    }

}