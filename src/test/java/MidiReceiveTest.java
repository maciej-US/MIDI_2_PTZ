package uci.bcd2ptz;

import javax.sound.midi.*;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;

public class MidiReceiveTest {

    public static void main(String[] args) {
        try {

            // ✅ lista urządzeń MIDI
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

            System.out.println("Dostępne urządzenia MIDI (INPUT):");

            for (int i = 0; i < infos.length; i++) {
                MidiDevice dev = MidiSystem.getMidiDevice(infos[i]);

//                if (dev.getMaxTransmitters() != 0) {
                    System.out.println(i + ": " +
                            infos[i].getName() + " [" +
                            infos[i].getDescription() + "]");
                }
//            }

            // ✅ wybór urządzenia
            System.out.print("Wybierz indeks urządzenia: ");
            Scanner scanner = new Scanner(System.in);
            int index = scanner.nextInt();

            System.out.println("Start nasłuchu MIDI...");

            // ✅ Twój listener
            MidiEventRouter listener = MidiEventRouter.getInstance(index);

            Thread listenerThread = new Thread(listener, "MIDI-Listener");
            listenerThread.start();

            BlockingQueue<String> queue = listener.getParsedQueue();

            long lastTime = System.currentTimeMillis();

            int count = 0;
            long start = lastTime;


            // ✅ główna pętla testowa
            while (true) {

                String msg = queue.take();

                long now = System.currentTimeMillis();
                long delta = now - lastTime;
                lastTime = now;

                count++;

                // ✅ parsowanie
                if (msg.startsWith("CC_")) {
                    String[] p = msg.split("_");

                    int ch = Integer.parseInt(p[1]);
                    int cc = Integer.parseInt(p[2]);
                    int val = Integer.parseInt(p[3]);

                    System.out.println(String.format(
                            "[%4d ms] CC   ch=%d cc=%3d val=%3d",
                            delta, ch, cc, val
                    ));
                }

                else if (msg.startsWith("NOTE_")) {
                    String[] p = msg.split("_");

                    int ch = Integer.parseInt(p[1]);
                    int note = Integer.parseInt(p[2]);
                    int val = Integer.parseInt(p[3]);

                    System.out.println(String.format(
                            "[%4d ms] NOTE ch=%d note=%3d val=%3d",
                            delta, ch, note, val
                    ));
                }

                else {
                    System.out.println(String.format(
                            "[%4d ms] RAW  %s",
                            delta, msg
                    ));
                }



                /*// ✅ statystyka co 50 wiadomości
                if (count % 50 == 0) {
                    long elapsed = now - start;
                    double rate = (count * 1000.0) / elapsed;

                    System.out.println(String.format(
                            ">>> RATE: %.1f msg/s (%d msgs)",
                            rate, count
                    ));
                }*/
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}