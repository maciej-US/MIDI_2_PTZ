//package uci.bcd2ptz.test;

@Deprecated

public class MidiOutputTest {

   /*
    public static void main(String[] args) {

        try {

            String midiConfigFile =
                    "C:\\GIT\\BCD2PTZ\\BCD2PTZ\\src\\main\\resources\\BCD3000.json";

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

            MidiController controller =
                    new MidiController(
                            midiConfig,
                            midiConfig.getInputDeviceIndex(),
                            midiConfig.getOutputDeviceIndex()
                    );

            controller.start();

            Scanner scanner =
                    new Scanner(System.in);

            System.out.println();
            System.out.println("===================================");
            System.out.println(" MIDI OUTPUT TEST");
            System.out.println("===================================");
            System.out.println();

            while (true) {

                System.out.println(
                        "[ENTER] LED ON"
                );

                scanner.nextLine();

                controller.sendControlEvent(
                        new ControlEvent(
                                SYSTEM,
                                "ABSOLUTE_MODE_INDICATOR_A",
                                "A",
                                "LED ON",
                                1
                        )
                );

                System.out.println("[ENTER] LED BLINK"
                );

                scanner.nextLine();

                controller.sendControlEvent(
                        new ControlEvent(
                                SYSTEM,
                                "ABSOLUTE_MODE_INDICATOR_A",
                                "A",
                                "BLINKING",
                                2
                        )
                );

                System.out.println("[ENTER] LED OFF");

                scanner.nextLine();

                controller.sendControlEvent(
                        new ControlEvent(
                                SYSTEM,
                                "ABSOLUTE_MODE_INDICATOR_A",
                                "A",
                                "LED OFF",
                                0
                        )
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "MIDI OUTPUT TEST ERROR"
            );

            e.printStackTrace();
        }
    }*/
}
