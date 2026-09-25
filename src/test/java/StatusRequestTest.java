import javax.sound.midi.*;
import java.util.Scanner;

public class StatusRequestTest {

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

                );
            }

            /*System.out.print(
                    "\nWybierz urządzenie: "
            );

            Scanner scanner =  new Scanner(System.in);

            int index =
                    scanner.nextInt();*/

            MidiDevice receiverDev = MidiSystem.getMidiDevice(infos[5]);
            MidiDevice transmitterDev = MidiSystem.getMidiDevice(infos[4]);

            receiverDev.open();
            Transmitter receiver = receiverDev.getTransmitter();
            receiver.setReceiver(
                    new Receiver() {

                        @Override
                        public void send(
                                MidiMessage message,
                                long timeStamp) {

                            if (!(message instanceof ShortMessage sm)) {
                                return;
                            }

                            if (sm.getCommand() != ShortMessage.CONTROL_CHANGE){
                                return;
                            }

                            //System.out.printf("RECEIVED: CC = %3d  VALUE = %3d%n", sm.getData1(), sm.getData2());

                        }

                        @Override
                        public void close() {
                        }
                    }
            );

            transmitterDev.open();

            ShortMessage request = new ShortMessage();
            request.setMessage(ShortMessage.CONTROL_CHANGE, 0, 100, 127);
            transmitterDev.getReceiver().send(request, -1);

            System.out.println("Request send: " + request.toString());

            System.out.println(
                    "\nMIDI OPEN: "
                            + receiverDev.getDeviceInfo()
                            .getName()
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