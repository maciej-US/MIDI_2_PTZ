package uci.bcd2ptz;

import uci.bcd2ptz.gui.JogIndicator;
import uci.bcd2ptz.gui.VerticalSlider;
import uci.bcd2ptz.gui.buttonLED;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainGUI implements Runnable {

    /*public void updateControl(MidiToPTZ.UiControl control, int value) {
    }*/

    //==================== ENUMS & CONSTRAINS =============

    public enum UiControl {
        TILT,
        ZOOM,
        JOG,
        TILT_MODE_LED
    }

    // ==================== GUI ELEMTNST====================

    private JFrame frame;
    private JComboBox<String> midiComboBox;
    private JButton connectButton;

    private VerticalSlider tiltSlider;
    private VerticalSlider zoomSlider;

    private JogIndicator jogIndicator;

    private final buttonLED tiltModeButton = new buttonLED("ABS");
    private final buttonLED autoFollowModeButton = new buttonLED("TRC");

    // ==================== CORE INTERFACE ====================


    //private MidiOutputController midiOut;
    private final List<Integer> validDeviceIndexes = new ArrayList<>();

    private Thread consumerThread;
    private ControlCore core;

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
        frame.setSize(500, 350);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        //TODO: w topPanel dodać menu i konfiguracje hardware

//        JPanel topPanel = new JPanel(new FlowLayout());

//        midiComboBox = new JComboBox<>();
//        midiComboBox.setPreferredSize(new Dimension(300, 25));
//
//        connectButton = new JButton("Połącz");
//        connectButton.addActionListener(this::connectToDevice);
//
//        topPanel.add(midiComboBox);
//        topPanel.add(connectButton);
//
//        frame.add(topPanel, BorderLayout.NORTH);
//
//        loadMidiDevices();

        // ==== TILT ====
        tiltSlider = new VerticalSlider();
        tiltSlider.setPreferredSize(new Dimension(80, 200));
        tiltSlider.setValue(64);
        tiltSlider.setOnValueChange(value -> core.handleGUITilt(value));

        JLabel tiltLabel = new JLabel("TILT", SwingConstants.CENTER);

        JPanel tiltPanel = new JPanel(new BorderLayout());
        tiltPanel.add(tiltLabel, BorderLayout.NORTH);
        tiltPanel.add(tiltSlider, BorderLayout.CENTER);
        JPanel tiltBottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        tiltBottomPanel.setOpaque(false);
        tiltBottomPanel.add(tiltModeButton);

        tiltModeButton.setOnClick(() -> {
            if (core != null) {
                core.onGuiModeClick(ControlCore.Control.TILT);
            }
        });

        tiltPanel.add(tiltBottomPanel, BorderLayout.SOUTH);

        // ==== ZOOM ====
        zoomSlider = new VerticalSlider();
        zoomSlider.setPreferredSize(new Dimension(80, 200));
        zoomSlider.setOnValueChange(value -> core.handleGUIZoom(value));

        JLabel zoomLabel = new JLabel("ZOOM", SwingConstants.CENTER);

        JPanel zoomPanel = new JPanel(new BorderLayout());
        zoomPanel.add(zoomLabel, BorderLayout.NORTH);
        zoomPanel.add(zoomSlider, BorderLayout.CENTER);
        JPanel zoomBottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        zoomBottomPanel.setOpaque(false);
        zoomBottomPanel.add(autoFollowModeButton);

        zoomPanel.add(zoomBottomPanel, BorderLayout.SOUTH);

        // ==== JOG ====
        jogIndicator = new JogIndicator();
        jogIndicator.setPreferredSize(new Dimension(200, 80));

        JPanel controls = new JPanel(new GridBagLayout());
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
        controls.add(jogIndicator, gbc);

        gbc.gridx = 2;
        controls.add(zoomPanel, gbc);

        frame.add(controls, BorderLayout.CENTER);

        frame.setVisible(true);

        //updateGUIIndicators(MidiToPTZ.Control.TILT);

        jogIndicator.setOnJog((dir, speed) -> {
            if (core != null) {
                core.handleGUIJog(dir, speed);
            }
        });

        jogIndicator.setOnStop(() -> {
            if (core != null) {
                core.handleGUIJogStop();
            }
        });

    }

    // ====================
    //        MIDI
    // ====================

    public void setCore(ControlCore core) {
        this.core = core;

        //updateGUIIndicators(MidiToPTZ.Control.TILT);
    }


    // ====================
    //       HANDLERY
    // ====================

    public void updateTiltSlider(int value) {
        SwingUtilities.invokeLater(() -> tiltSlider.setValue(value));
    }

    public void updateZoomSlider(int value) {
        SwingUtilities.invokeLater(() -> zoomSlider.setValue(value));
    }

    public void updateGUIJog(int dir, int speed) {
        SwingUtilities.invokeLater(() -> jogIndicator.setJog(dir, speed));
    }

    public void setTiltModeLed(buttonLED.LedMode mode) {
        SwingUtilities.invokeLater(() -> tiltModeButton.setMode(mode));
    }


    // ==================== INDICATORS ====================

    private void updateGUIIndicators(ControlCore.Control control) {

        if (core == null) {return;}

        ControlCore.ModeState mode = core.getMode(control);

        if (control == ControlCore.Control.TILT) {
            switch (mode) {
                case JOYSTICK:
                    setTiltModeLed(buttonLED.LedMode.OFF);
                    break;

                case ABSOLUTE:
                    setTiltModeLed(buttonLED.LedMode.ON);
                    break;

                case WAIT_NEUTRAL:
                    setTiltModeLed(buttonLED.LedMode.BLINK);
                    break;
            }

        }
    }

    // ==================== FOCUS (placeholder) ====================


    // ==================== CONTROL UPDATES ====================

    public void updateGUIControl(UiControl control, int value) {

        SwingUtilities.invokeLater(() -> {

            switch (control) {

                case TILT:
                    tiltSlider.setValue(value);
                    break;

                case ZOOM:
                    zoomSlider.setValue(value);
                    break;

                case JOG:
                    int dir = value > 0 ? 1 : -1;
                    int speed = Math.abs(value);
                    jogIndicator.setJog(dir, speed);
                    break;
            }
        });
    }

    public void updateLed(UiControl control, buttonLED.LedMode mode) {

        SwingUtilities.invokeLater(() -> {

            switch (control) {

                case TILT_MODE_LED:
                    tiltModeButton.setMode(mode);
                    break;
            }
        });
    }

    // ==================== MIDI EVENT ROUTING  ====================
//    private void handleMidiEvent(MidiEvent event) {
//
//        if (event.getType() == MidiEvent.Type.CC) {
//            handleCC(event);
//        }
//
//        if (event.getType() == MidiEvent.Type.NOTE) {
//            handleNote(event);
//        }

}

