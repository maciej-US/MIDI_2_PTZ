package uci.bcd2ptz.midi;

import javax.sound.midi.*;
import javax.swing.Timer;
import java.util.HashMap;
import java.util.Map;

/**
 * Kontroler wyjścia MIDI odpowiedzialny za:
 * <ul>
 *     <li>Sterowanie diodami LED kontrolera MIDI (CC)</li>
 *     <li>Wysyłanie komunikatów MIDI (NOTE / CC)</li>
 *     <li>Obsługę trybu migania LED (BLINK)</li>
 * </ul>
 *
 * <p>Klasa działa jako singleton – tylko jedna instancja na aplikację.</p>
 */
public class MidiOutputController {

    /**
     * Tryb pracy diody LED.
     */
    public enum LedMode {
        OFF,
        ON,
        BLINK
    }

    /** Mapa aktualnych stanów LED */
    private final Map<Integer, LedMode> ledStates = new HashMap<>();

    /** Aktualny stan blinkera (ON/OFF) */
    private boolean blinkState = false;

    /** Timer odpowiedzialny za miganie LED */
    private Timer blinkTimer;

    /** Singleton instance */
    private static MidiOutputController instance;

    /** Odbiornik MIDI (OUT) */
    private Receiver receiver;

    // ==================== KONSTRUKTOR ====================

    /**
     * Tworzy kontroler MIDI OUT.
     *
     * @param deviceIndex indeks urządzenia MIDI OUT
     */
    private MidiOutputController(int deviceIndex) {
        try {
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
            MidiDevice device = MidiSystem.getMidiDevice(devices[deviceIndex]);

            device.open();
            receiver = device.getReceiver();

            System.out.println("MIDI OUT gotowe: " + devices[deviceIndex].getName());

            initBlinkEngine();

        } catch (Exception e) {
            System.err.println("Błąd inicjalizacji MIDI OUT");
            e.printStackTrace();
        }
    }

    // ==================== SINGLETON ====================

    /**
     * Pobiera instancję kontrolera.
     *
     * @param deviceIndex indeks urządzenia MIDI OUT
     * @return singleton MidiOutputController
     */
    public static synchronized MidiOutputController getInstance(int deviceIndex) {
        if (instance == null) {
            instance = new MidiOutputController(deviceIndex);
        }
        return instance;
    }

    // ==================== LED CONTROL ====================

    /**
     * Ustawia tryb pracy diody LED przypisanej do danego CC.
     *
     * @param cc numer kontrolera MIDI
     * @param mode tryb LED
     */
    public synchronized void setLed(int cc, LedMode mode) {

        ledStates.put(cc, mode);

        switch (mode) {

            case ON:
                sendCC(cc, 127);
                break;

            case OFF:
                sendCC(cc, 0);
                break;

            case BLINK:
                // start od ON (opcjonalne)
                sendCC(cc, 127);
                break;
        }
    }

    // ==================== MIDI SEND ====================

    /**
     * Wysyła komunikat Control Change.
     *
     * @param cc numer kontrolera
     * @param value wartość (0–127)
     */
    public void sendCC(int cc, int value) {

        if (receiver == null) return;

        try {
            ShortMessage msg = new ShortMessage();
            msg.setMessage(ShortMessage.CONTROL_CHANGE, 0, cc, value);
            receiver.send(msg, -1);

        } catch (Exception e) {
            System.err.println("Błąd wysyłania CC");
            e.printStackTrace();
        }
    }

    /**
     * Wysyła NOTE ON.
     *
     * @param note numer nuty
     * @param velocity prędkość (0–127)
     */
    public void sendNoteOn(int note, int velocity) {

        if (receiver == null) return;

        try {
            ShortMessage msg = new ShortMessage();
            msg.setMessage(ShortMessage.NOTE_ON, 0, note, velocity);
            receiver.send(msg, -1);

        } catch (Exception e) {
            System.err.println("Błąd NOTE ON");
            e.printStackTrace();
        }
    }

    /**
     * Wysyła NOTE OFF.
     *
     * @param note numer nuty
     * @param velocity prędkość
     */
    public void sendNoteOff(int note, int velocity) {

        if (receiver == null) return;

        try {
            ShortMessage msg = new ShortMessage();
            msg.setMessage(ShortMessage.NOTE_OFF, 0, note, velocity);
            receiver.send(msg, -1);

        } catch (Exception e) {
            System.err.println("Błąd NOTE OFF");
            e.printStackTrace();
        }
    }

    // ==================== BLINK ENGINE ====================

    /**
     * Inicjalizuje silnik migania LED.
     * Timer zmienia stan LED BLINK co ~166 ms.
     */
    private synchronized void initBlinkEngine() {

        if (blinkTimer != null) return;

        blinkTimer = new Timer(166, e -> {

            blinkState = !blinkState;

            synchronized (this) {
                for (Map.Entry<Integer, LedMode> entry : ledStates.entrySet()) {

                    if (entry.getValue() == LedMode.BLINK) {
                        sendCC(entry.getKey(), blinkState ? 127 : 0);
                    }
                }
            }
        });

        blinkTimer.start();
    }

    // ==================== UTIL ====================

    /**
     * Resetuje wszystkie LED do stanu OFF.
     */
    public void ledStateReset() {

        for (int i = 0; i <= 127; i++) {
            sendCC(i, 0);
        }
    }

    /**
     * Wysyła żądanie synchronizacji stanu urządzenia.
     * (zależne od implementacji kontrolera MIDI)
     */
    public void stateRequest() {
        sendCC(100, 127); // TODO: przenieść do stałej konfiguracyjnej
    }
}