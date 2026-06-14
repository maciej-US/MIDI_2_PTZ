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
    private VerticalSlider panSlider;
    private VerticalSlider zoomSlider;
    private Thread consumerThread;
    private JogIndicator jogIndicator;
    private JLabel modeLabel;

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
        frame.setSize(500, 120);
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

        panSlider = new VerticalSlider();
        panSlider.setPreferredSize(new Dimension(80, 200));
        modeLabel = new JLabel("MODE: JOYSTICK");
        frame.add(modeLabel, BorderLayout.SOUTH);

        zoomSlider = new VerticalSlider();
        zoomSlider.setPreferredSize(new Dimension(80, 200));

        jogIndicator = new JogIndicator();
        jogIndicator.setPreferredSize(new Dimension(200, 80));

        frame.add(panSlider, BorderLayout.WEST);
        frame.add(zoomSlider, BorderLayout.EAST);
        frame.add(jogIndicator, BorderLayout.CENTER);

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
                            SwingUtilities.invokeLater(() -> panSlider.setValue(value));
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

                                int jogSpeed = Math.min(3, Math.max(1, speed / 21 + 1));

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
                                System.out.println("TALLY ON -> preset 96");
                                ViscaPTZController.recallPreset(96);
                                midiOut.ledOn(18); // zapal LED
                            } else {
                                System.out.println("TALLY OFF -> preset 97");
                                ViscaPTZController.recallPreset(97);
                                midiOut.ledOff(18); // zgaś LED
                            }
                        }
                        if (note == 5 && value > 0) {

                            absoluteMode = !absoluteMode;

                            if (absoluteMode) {
                                System.out.println("TRYB: ABSOLUTE TILT");
                                midiOut.ledOn(21);
                                modeLabel.setText("MODE: ABSOLUTE");

                            } else {
                                System.out.println("TRYB: JOYSTICK");
                                midiOut.ledOff(21);
                                modeLabel.setText("MODE: JOYSTICK");
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