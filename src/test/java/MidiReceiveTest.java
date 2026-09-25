package uci.bcd2ptz;

import javax.sound.midi.*;
import java.util.Scanner;

public class MidiReceiveTest {

    public static void main(String[] args) {

        try {

            MidiDevice.Info[] infos =
                    MidiSystem.getMidiDeviceInfo();

            System.out.println("Dostępne urządzenia MIDI:");

            for (int i = 0; i < infos.length; i++) {

                MidiDevice device =
                        MidiSystem.getMidiDevice(
                                infos[i]
                        );

                System.out.println(
                        i + ": "
                                + infos[i].getName()
                                + " ["
                                + infos[i].getDescription()
                                + "]  VENDOR: "
                                + infos[i].getVendor()
                                + "   VERSION: "
                                + infos[i].getVersion()
                                + " TX="
                                + device.getMaxTransmitters()
                                + " RX="
                                + device.getMaxReceivers()

                );
            }

            System.out.print(
                    "\nWybierz urządzenie: "
            );

            Scanner scanner =
                    new Scanner(System.in);

            int index =
                    scanner.nextInt();

            MidiDevice device =
                    MidiSystem.getMidiDevice(
                            infos[index]
                    );

            device.open();

            System.out.println(
                    "\nMIDI OPEN: "
                            + device.getDeviceInfo()
                            .getName()
            );

            final long[] lastEventTime = {
                    System.currentTimeMillis()
            };

            final int[] burstCount = {
                    0
            };

            final int[] totalCount = {
                    0
            };

            Transmitter transmitter =
                    device.getTransmitter();

            transmitter.setReceiver(
                    new Receiver() {

                        @Override
                        public void send(
                                MidiMessage message,
                                long timeStamp) {

                            long now =
                                    System.currentTimeMillis();

                            long delta =
                                    now - lastEventTime[0];

                            if (delta > 100) {

                                if (burstCount[0] > 0) {

                                    System.out.println(
                                            "\n=== JOG BURST END ==="
                                    );

                                    System.out.println(
                                            "Impulsów: "
                                                    + burstCount[0]
                                    );

                                    System.out.println(
                                            "====================\n"
                                    );
                                }

                                burstCount[0] = 0;
                            }

                            lastEventTime[0] = now;

                            if (!(message instanceof ShortMessage)) {
                                return;
                            }

                            ShortMessage sm =
                                    (ShortMessage) message;

                            totalCount[0]++;
                            burstCount[0]++;

                            int command =
                                    sm.getCommand();

                            int channel =
                                    sm.getChannel();

                            int data1 =
                                    sm.getData1();

                            int data2 =
                                    sm.getData2();

                            String type =
                                    switch (command) {

                                        case ShortMessage.CONTROL_CHANGE ->
                                                "CC";

                                        case ShortMessage.NOTE_ON ->
                                                "NOTE_ON";

                                        case ShortMessage.NOTE_OFF ->
                                                "NOTE_OFF";

                                        default ->
                                                "OTHER";
                                    };

                            System.out.printf(
                                    "#%05d | Δ=%4d ms | BURST=%3d | %s | CH=%d | D1=%3d | D2=%3d%n",
                                    totalCount[0],
                                    delta,
                                    burstCount[0],
                                    type,
                                    channel,
                                    data1,
                                    data2
                            );
                        }

                        @Override
                        public void close() {
                        }
                    }
            );

            System.out.println(
                    "\nNasłuch MIDI uruchomiony..."
            );

            while (true) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}