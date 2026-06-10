package uci.bcd2ptz;

import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class MainGUI implements Runnable {

    private JFrame frame;
    private JComboBox<String> midiComboBox;
    private JButton connectButton;
    private VerticalSlider slider;
    private Thread consumerThread;


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

        slider = new VerticalSlider();
        slider.setPreferredSize(new Dimension(80, 200));

        frame.add(slider, BorderLayout.CENTER);

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

        Thread listenerThread = new Thread(listener);
        listenerThread.start();

        connectButton.setEnabled(false);
        midiComboBox.setEnabled(false);

    }
    BlockingQueue<String> queue = listener.getMessageQueue();

    consumerThread = new Thread(() -> {
        while (true) {
            try {
                String msg = queue.take();

                System.out.println(msg); // nadal na konsolę

                if (msg.startsWith("CC_")) {
                    String[] parts = msg.split("_");

                    int channel = Integer.parseInt(parts[1]);
                    int cc = Integer.parseInt(parts[2]);
                    int value = Integer.parseInt(parts[3]);

                    // interesuje nas CC 11
                    if (cc == 11) {

                        // update GUI na EDT
                        SwingUtilities.invokeLater(() -> slider.setValue(value));
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    });

consumerThread.start();

}