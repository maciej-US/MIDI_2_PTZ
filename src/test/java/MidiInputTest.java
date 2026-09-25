package uci.bcd2ptz.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import uci.bcd2ptz.core.ControlEvent;
import uci.bcd2ptz.midi.MidiConfig;
import uci.bcd2ptz.midi.MidiController;

import java.io.File;
import java.util.concurrent.BlockingQueue;

import static uci.bcd2ptz.core.ControlCore.RunMode.FULL;

public class MidiInputTest {

    public static void main(String[] args) {

        try {

            String midiConfigFile =
                    "C:\\GIT\\BCD2PTZ\\BCD2PTZ\\src\\main\\resources\\BCD3000.json";

            /*
             * Wczytanie konfiguracji kontrolera
             */
            ObjectMapper mapper =
                    new ObjectMapper();

            MidiConfig midiConfig =
                    mapper.readValue(
                            new File(midiConfigFile),
                            MidiConfig.class
                    );

            System.out.println(
                    "[MIDI] Loaded config: "
                            + midiConfig.getControllerName()
            );

            /*
             * Utworzenie kontrolera MIDI
             */
            MidiController controller =
                    new MidiController(
                            midiConfig,
                            midiConfig.getInputDeviceIndex(),
                            midiConfig.getOutputDeviceIndex(),
                            null,
                            FULL,
                            null
                    );

            controller.start();


            System.out.println();
            System.out.println("===================================");
            System.out.println(" MIDI CONTROLLER TEST");
            System.out.println("===================================");
            System.out.println();

            BlockingQueue<ControlEvent> queue =
                    controller.getControlQueue();

            while (true) {

                ControlEvent event =
                        queue.take();

                System.out.println(
                        formatEvent(event)
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "Błąd testera MIDI"
            );

            e.printStackTrace();
        }
    }

    private static String formatEvent(
            ControlEvent event) {

        return String.format(
                "[CONTROL] SOURCE= %s ID=%s MIDI= %S VALUE=%d",
                event.getEventSource(),
                event.getAction(),
                event.getValue()
        );
    }
}