package uci.bcd2ptz;

import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class MainGUI implements Runnable {

    private JFrame frame;
    private JComboBox<String> midiComboBox;
    private JButton connectButton;
    private VerticalSlider tiltSlider;
    private VerticalSlider zoomSlider;
    private Thread consumerThread;
    private JogIndicator jogIndicator;
    //private JLabel modeLabel;
    buttonLED tiltModeButton = new buttonLED("ABS");
    buttonLED autoFollowModeButton = new buttonLED("TRC");

    private boolean tallyOn = false;
    private MidiOutputController midiOut;
    private boolean absoluteMode = false;
    private List<Integer> validDeviceIndexes = new ArrayList<>();


    @Override
    public void run() {
        SwingUtilities.invokeLater(this::initialize);
    }

    private void initialize() {
        frame = new JFrame("BCD2PTZ - MIDI");
        frame.setSize(500, 350);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout());

        midiComboBox = new JComboBox<>();
        midiComboBox.setPreferredSize(new Dimension(300, 25));

        connectButton = new JButton("Połącz");
        connectButton.addActionListener(this::connectToDevice);

        topPanel.add(midiComboBox);
        topPanel.add(connectButton);

        frame.add(topPanel, BorderLayout.NORTH);

        loadMidiDevices();

        // TILT slider
        tiltSlider = new VerticalSlider();
        tiltSlider.setPreferredSize(new Dimension(80, 200));
        tiltSlider.setJoystickMode(!absoluteMode);
        tiltSlider.setValue(64);

        JLabel tiltLabel = new JLabel("TILT", SwingConstants.CENTER);
        tiltLabel.setForeground(Color.BLACK);

        JPanel tiltPanel = new JPanel();
        tiltPanel.setLayout(new BorderLayout());
        tiltPanel.setOpaque(false);
        tiltPanel.add(tiltLabel, BorderLayout.NORTH);
        tiltPanel.add(tiltSlider, BorderLayout.CENTER);
        JPanel tiltBottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        tiltBottomPanel.setOpaque(false);
        tiltBottomPanel.add(tiltModeButton);
        tiltPanel.add(tiltBottomPanel, BorderLayout.SOUTH);
       // frame.add(tiltPanel, BorderLayout.WEST);

        //Zoom slider
        zoomSlider = new VerticalSlider();
        zoomSlider.setPreferredSize(new Dimension(80, 200));
        zoomSlider.setJoystickMode(absoluteMode == true);

        JLabel zoomLabel = new JLabel("ZOOM", SwingConstants.CENTER);
        zoomLabel.setForeground(Color.BLACK);
        JPanel zoomPanel = new JPanel();
        zoomPanel.setLayout(new BorderLayout());
        zoomPanel.setOpaque(false);
        zoomPanel.add(zoomLabel, BorderLayout.NORTH);
        zoomPanel.add(zoomSlider, BorderLayout.CENTER);
        JPanel zoomBottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        zoomBottomPanel.setOpaque(false);
        zoomBottomPanel.add(autoFollowModeButton);
        zoomPanel.add(zoomBottomPanel, BorderLayout.SOUTH);
       // frame.add(zoomPanel, BorderLayout.EAST);

        //PAn Jog
        jogIndicator = new JogIndicator();
        jogIndicator.setPreferredSize(new Dimension(200, 80));
        //frame.add(jogIndicator, BorderLayout.CENTER);

        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new GridBagLayout());
        controlsPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 20, 5, 20);
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.VERTICAL;

        gbc.gridx = 0;
        controlsPanel.add(tiltPanel, gbc);

        gbc.gridx = 1;
        controlsPanel.add(jogIndicator, gbc);

        gbc.gridx = 2;
        controlsPanel.add(zoomPanel, gbc);

        frame.add(controlsPanel, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private void loadMidiDevices() {
        try {
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();

            midiComboBox.removeAllItems();
            validDeviceIndexes.clear();

            for (int i = 0; i < devices.length; i++) {
                MidiDevice device = MidiSystem.getMidiDevice(devices[i]);

                int maxTransmitters = device.getMaxTransmitters();
                int maxReceivers = device.getMaxReceivers();

                // INPUT = ma transmittery (-1 = unlimited → też OK)
                boolean hasInput = maxTransmitters != 0;

                if (hasInput) {
                    String name = String.format(
                            "%s [%s] (IN:%d OUT:%d)",
                            devices[i].getName(),
                            devices[i].getDescription(),
                            maxTransmitters,
                            maxReceivers
                    );

                    midiComboBox.addItem(name);
                    validDeviceIndexes.add(i);
                }
            }

            // Debug info
            if (midiComboBox.getItemCount() == 0) {
                midiComboBox.addItem("Brak urządzeń INPUT!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectToDevice(ActionEvent e) {
        int selectedIndex = midiComboBox.getSelectedIndex();

        if (selectedIndex < 0) {
            JOptionPane.showMessageDialog(frame, "Brak urządzenia!");
            return;
        }

        int deviceIndex = validDeviceIndexes.get(selectedIndex);

        System.out.println("Łączenie z urządzeniem indeks: " + deviceIndex);

        MidiListener listener = MidiListener.getInstance(deviceIndex);
        midiOut = MidiOutputController.getInstance(4);

        Thread listenerThread = new Thread(listener, "MIDI-Listener");
        listenerThread.start();
        ViscaPTZController.startPanWatchdog();

        //resetowanie LEDów
        for (int cc = 0; cc <= 127; cc++) {
            midiOut.ledOff(cc);

            try {
                Thread.sleep(5); // ✅ KLUCZ
            } catch (InterruptedException ignored) {}

        }

        // tutaj dopiero uruchamiamy consumer
        BlockingQueue<String> queue = listener.getMessageQueue();

        consumerThread = new Thread(() -> {
            while (true) {
                try {
                    String msg = queue.take();

                    System.out.println(msg);

                    if (msg.startsWith("CC_")) {
                        String[] parts = msg.split("_");

                        int cc = Integer.parseInt(parts[2]);
                        int value = Integer.parseInt(parts[3]);

                        // SLIDER TILT (VOLUME - CC 11)

                        if (cc == 11) {
                            SwingUtilities.invokeLater(() -> tiltSlider.setValue(value));
                            if (absoluteMode) {
                                ViscaPTZController.tiltAbsolute(value);
                            } else {
                                ViscaPTZController.tilt(value);
                            }
                        }

                        // SLIDER ZOOM (PITCH - CC 0)
                        if (cc == 0) {
                            SwingUtilities.invokeLater(() -> zoomSlider.setValue(value));
                            ViscaPTZController.zoom(value);
                        }

                        // JOG PAN (JOG WHEEL - CC 19)
                        if (cc == 19) {

                            SwingUtilities.invokeLater(() -> {

                                int speed;
                                int direction;

                                if (value == 64) {
                                    jogIndicator.setJog(0, 0);
                                    return;
                                }

                                if (value > 64) {
                                    direction = 1; // prawo
                                    speed = value - 64;
                                } else {
                                    direction = -1; // lewo
                                    speed = 64 - value;
                                }

                                int jogSpeed;

                                if (speed <= 1) {
                                    jogSpeed = 1;
                                } else if (speed <= 3) {
                                    jogSpeed = 2;
                                } else {
                                    jogSpeed = 3;
                                }

                                System.out.println("Jog Params: Direction=" + direction + ", Speed=" + jogSpeed);

                                jogIndicator.setJog(direction, jogSpeed);
                            });

                            if (absoluteMode) {
                                ViscaPTZController.panAbsolute(value);
                            } else {
                                ViscaPTZController.pan(value);
                            }
                        }

                    }

                    if (msg.startsWith("NOTE_")) {
                        String[] parts = msg.split("_");

                        int channel = Integer.parseInt(parts[1]);
                        int note = Integer.parseInt(parts[2]);
                        int value = Integer.parseInt(parts[3]);

                        // TALLY (NOTE 18) - toggle on/off
                        if (note == 18 && value > 0) {

                            tallyOn = !tallyOn; // toggle

                            if (tallyOn) {
                                ViscaPTZController.recallPreset(96);
                                midiOut.ledOn(18); // zapal LED
                            } else {
                                ViscaPTZController.recallPreset(97);
                                midiOut.ledOff(18); // zgaś LED
                            }
                        }

                        // MODE (NOTE 5) - toggle TILT absolute/joystick
                        if (note == 5 && value > 0) {

                            absoluteMode = !absoluteMode;

                            if (absoluteMode) {
                                midiOut.ledOn(21);
                                tiltSlider.setJoystickMode(false);
                                tiltModeButton.setLight(true);

                            } else {
                                midiOut.ledOff(21);
                                tiltSlider.setJoystickMode(true);
                                tiltModeButton.setLight(false);
                            }
                        }
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, "MIDI-Consumer");

        consumerThread.start();

        connectButton.setEnabled(false);
        midiComboBox.setEnabled(false);
    }

}