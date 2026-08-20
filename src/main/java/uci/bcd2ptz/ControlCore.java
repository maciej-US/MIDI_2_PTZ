package uci.bcd2ptz;

import uci.bcd2ptz.gui.buttonLED;
import uci.bcd2ptz.midi.MidiController;
import uci.bcd2ptz.midi.MidiOutputController;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.visca.FieldDefinition;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class ControlCore {


    public enum Control {
        TILT,
        FOCUS,
        AUTO_FOLLOW
    }
    public enum ModeState {
        JOYSTICK,
        ABSOLUTE,
        WAIT_NEUTRAL,
        AUTO,
        MANUAL
    }
    public enum Panel {
        A,
        B
    }

    // Przesunięcie kamery - opdcjonalnie też do przejścia na konfigurowalne z config.json
    private static final double PAN_STEP_SLOW = 0.5;
    private static final double PAN_STEP_MEDIUM = 1.75;
    private static final double PAN_STEP_FAST = 3.0;

    // KAMERY i MAPOWANIA
    private final PTZCameraManager cameraManager;
    private final Map<Panel, PTZCamera> panelAssignments = new EnumMap<>(Panel.class);
    private final Map<Control, ModeState> controlStates = new EnumMap<>(Control.class);
    private final Map<Integer, MainGUI.UiControl> ccMap = new HashMap<>();

    // Komunikacja z MIDI, GUI i VISCA
    private MidiController midiController;
    private MainGUI gui;

    // Kontrola statusu
    private boolean waitingForStateSync = false;
    private boolean acceptingPanTilt = true;
    private volatile long panelALastJogTime = 0;
    private volatile long panelBLastJogTime = 0;
    private static final long JOG_TIMEOUT_MS = 100;

    /* ========================================
     *        KONSTRUKTOR i URUCHOMIANIE
       ========================================*/

    /**
     * Konstruktor klasy MidiToPTZ
     * @param gui
     * @param cameraManager
     * @param midiController
     */

    public ControlCore(MainGUI gui, PTZCameraManager cameraManager, MidiController midiController) {

        this.gui = gui;
        this.cameraManager = cameraManager;

        this.midiController = midiController;

        controlStates.put(Control.TILT, ModeState.JOYSTICK);
        controlStates.put(Control.FOCUS, ModeState.AUTO);
    }


    /**
     * Uruchamia główną pętlę sterowania systemu.
     *
     * Metoda inicjalizuje mapowania kontrolek MIDI, odświeża wskaźniki GUI
     * oraz uruchamia dedykowany wątek nasłuchujący kolejki zdarzeń MIDI.
     *
     * Obsługiwane są dwa typy komunikatów:
     * <ul>
     *     <li>CC (Control Change) - wartości suwaków, enkoderów i pokręteł</li>
     *     <li>NT (Note On/Off) - przyciski funkcyjne i przełączniki</li>
     * </ul>
     *
     * Wątek pracuje w sposób ciągły przez cały czas działania aplikacji
     * i przekazuje odebrane komunikaty do odpowiednich metod obsługi.
     *
     * @see #midiHandleCC(String)
     * @see #handleMidiNote(String)
     * @see #initMappings()
     */

    public void start() {

        initMappings();

        updatePTZModeIndicators();

        startJogWatchdog();

        new Thread(() -> {

            BlockingQueue<String> queue =
                    midiController
                            .getInput()
                            .getParsedQueue();

            while (true) {
                try {
                    String msg = queue.take();

                    if (msg.startsWith("CC_")) {
                        midiHandleCC(msg);
                    } else if (msg.startsWith("NT_")) {
                        handleMidiNote(msg);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }, "MIDI->PTZ").start();
    }


    /* =================================
                  MIDI → SYSTEM
       ================================*/

    private void midiHandleCC(String msg) {

        String[] p = msg.split("_");

        int cc = Integer.parseInt(p[2]);
        int value = Integer.parseInt(p[3]);

        MainGUI.UiControl control = ccMap.get(cc);

        if (control == null) return; // ignoruj nieznane

        // ===== STATE SYNC tylko dla TILT =====

        if (waitingForStateSync && control == MainGUI.UiControl.TILT) {

            waitingForStateSync = false;

            if (isNeutral(value)) {
                enterJoystickMode(Control.TILT);
            } else {
                enterWaitNeutral();
            }
            return;
        }
        gui.updateGUIControl(control, value);

        // ===== LOGIKA =====

        PTZCamera camera = getCamera(Panel.A); //TODO: docelowo wybór kamery na podstawie aktywnego panelu

        if (camera == null) {
            return;
        }

        switch (control) {
            case TILT:
                handleMidiTilt(Panel.A, value);
                break;

            case ZOOM:
                handleMidiZoom(Panel.A, value);
                break;

            case JOG:
                handleMidiPan(Panel.A, value);
                break;
        }
    }

    //TODO: Obsługa innych trybów pracy JOGA
    private void handleMidiPan(
            Panel panel,
            int value) {

        //SCAFF
        //System.out.println("handleMidiPan - Received value: " + value);

        PTZCamera camera = getCamera(panel);

        if (camera == null) {return;}

        //obsługa JOG impulsowego typu +/- neutral
        long now = System.currentTimeMillis();
        long delta = now - getJogTimestamp(panel);
        updateJogTimestamp(panel);
        int diff = value - 64;

        //wykrycie zatrzymania
        if (diff ==0){
            if (controlStates.get(Control.TILT) != ModeState.ABSOLUTE) {
                camera.stop();
            }
            return;
        }

        int direction = diff > 0 ? 1 : -1;
        int guiSpeed =(delta < 30) ? 5 : (delta < 200 ? 3 : 2);

        gui.updateGUIJog(direction, guiSpeed);

        if (controlStates.get(Control.TILT) == ModeState.ABSOLUTE) {

            int speed =(delta < 30) ? 15 : (delta < 200 ? 10 : 4);

            double panStep = getPanStep(speed);
            double newPan = camera.getPan() + (panStep * direction);

            camera.setPan(newPan);
            updateCameraPosition(camera, speed);

        } else {
            int speed =(delta < 30) ? 10 : (delta < 200 ? 6 : 2);
            camera.movePanTilt(direction * speed,0);
        }
    }


    private void handleMidiTilt(
            Panel panel,
            int value) {

        //SCAFF
        //System.out.println("handleMidiTilt - Received value: " + value);

        ModeState tiltMode = controlStates.get(Control.TILT);

        if (tiltMode == ModeState.WAIT_NEUTRAL) {
            if (isNeutral(value)) {enterJoystickMode(Control.TILT);
            }
            return;
        }

        if (!acceptingPanTilt) {return;}

        PTZCamera camera = getCamera(panel);

        if (camera == null) {return;}

        if (tiltMode == ModeState.ABSOLUTE) {
            double oldTilt = camera.getTilt();
            double newTilt = midiToDegrees(camera, "tiltPosition", value);
            double delta = Math.abs(newTilt - oldTilt);

            //TODO: dopracować synchro szybkości do ruchu hebla
            //int speed = Math.max(1,Math.min(15,(int)Math.round(delta)));
            int speed = 8;
            camera.setTilt(newTilt);
            updateCameraPosition(camera, speed);
        } else {
            camera.movePanTilt(0,value -64);
        }
    }


    //TODO: zaimplementować zoom w trybie joystick i zweryfikowac tryb absolute
    private void handleMidiZoom(Panel panel, int value) {

        PTZCamera camera = getCamera(panel);

        if (camera == null) {return;}

        camera.setZoom(value);
        camera.moveZoom(value);
    }

    private void handleMidiNote(String msg) {

        String[] p = msg.split("_");

        int note = Integer.parseInt(p[2]);
        int value = Integer.parseInt(p[3]);

        if (value <= 0) return;

        switch (note) {

            case 5: // TILT MODE
                handlePTZMode(Control.TILT);
                break;

            case 10: // FUTURE FOCUS
                handleFocusMode(Control.FOCUS);
                break;
        }
    }

    /* ===========================
              GUI → CORE
       ==========================*/

    public void handleGUITilt(int value) {
        midiHandleCC("CC_1_11_" + value);
    }

    public void handleGUIZoom(int value) {
        midiHandleCC("CC_1_0_" + value);
    }

    public void handleGUIJog(int direction, int speed) {

        gui.updateGUIControl(
                MainGUI.UiControl.JOG,
                direction * speed
        );

        if (!acceptingPanTilt) {return;}

        PTZCamera camera = getCamera(Panel.A);

        if (camera == null) {return;}

        if (controlStates.get(Control.TILT) == ModeState.ABSOLUTE) {

            double panStep = getPanStep(speed);
            double newPan = camera.getPan() + (panStep * direction);

            camera.setPan(newPan);
            updateCameraPosition(camera, speed);

        } else {

            /*
             * Tryb JOYSTICK.
             * Zachowujemy stare mapowanie GUI→MIDI.
             */

            int value =
                    64 + (direction * speed * 5);

            camera.movePan(value);
        }
    }

    public void handleGUIJogStop() {

        gui.updateGUIControl(MainGUI.UiControl.JOG, 0);

        PTZCamera camera = getCamera(Panel.A);
        if (camera == null) {return;}
        camera.stop();
    }


    public void onGuiModeClick(Control control) {
        handlePTZMode(control);
    }

     /* =========================================================
            CORE -> GUI INDICATORS, CORE -> MIDI INDICATORS
       =========================================================*/

    private void updatePTZModeIndicators() {

        switch (controlStates.get(Control.TILT)) {

            case JOYSTICK:
                if (midiController != null && midiController.getOutput() != null) {
                    midiController
                            .getOutput()
                            .setLed(
                                    21,
                                    MidiOutputController.LedMode.OFF
                            );
                }
                gui.updateLed(MainGUI.UiControl.TILT_MODE_LED, buttonLED.LedMode.OFF);
                break;

            case ABSOLUTE:
                if (midiController != null && midiController.getOutput() != null) {
                    midiController
                            .getOutput()
                            .setLed(
                                    21,
                                    MidiOutputController.LedMode.ON
                            );
                }
                gui.updateLed(MainGUI.UiControl.TILT_MODE_LED, buttonLED.LedMode.ON);
                break;

            case WAIT_NEUTRAL:
                if (midiController != null && midiController.getOutput() != null) {
                    midiController
                            .getOutput()
                            .setLed(
                                    21,
                                    MidiOutputController.LedMode.BLINK
                            );
                }
                gui.updateLed(MainGUI.UiControl.TILT_MODE_LED, buttonLED.LedMode.BLINK);
                break;
        }
    }


    /* =======================================
                OBSŁUGA TRYBÓW KONTROLI
       =======================================*/

    private void handlePTZMode(Control control) {

        // ======= TILT MODE ==========
        if (control == Control.TILT) {}
        switch (controlStates.get(Control.TILT)) {

            case JOYSTICK:
                enterAbsoluteMode();
                System.out.println("Enter ABS mode");
                break;

            case ABSOLUTE:
                enterWaitNeutral();
                System.out.println("Enter JOY mode");
                break;

            case WAIT_NEUTRAL:
                System.out.println("Still waiting...");
                break;
        }
    }

    private void enterAbsoluteMode() {

        controlStates.put(Control.TILT, ModeState.ABSOLUTE);
        acceptingPanTilt = true;

        gui.updateTiltSlider(64);

        updatePTZModeIndicators();

        PTZCamera camera =
                getCamera(Panel.A);

        if (camera == null) {
            return;
        }

        camera.syncPosition(() ->
                System.out.println("ABS READY")
        );
    }

    private void enterWaitNeutral() {

        controlStates.put(Control.TILT, ModeState.WAIT_NEUTRAL);

        acceptingPanTilt = false;

        updatePTZModeIndicators();

        System.out.println("WAITING FOR NEUTRAL");
    }

    private void enterJoystickMode(Control control) {

        controlStates.put(control, ModeState.JOYSTICK);
        acceptingPanTilt = true;

        updatePTZModeIndicators();
        gui.updateTiltSlider(64);
    }

    private void handleFocusMode(Control control) {
        System.out.println("TODO: focus");
    }

    private boolean isNeutral(int v) {
        return Math.abs(v - 64) <= 1;
    }

    /* ==========================================
             ZARZĄDANIE I STEROWANIE KAMERAMI
       ==========================================*/

    public void assignCamera(Panel panel, PTZCamera camera) {

        panelAssignments.put(panel,camera);

        //SCAFF do testów
        System.out.println(panel + " -> " + camera.getCameraId());
    }

    private PTZCamera getCamera(Panel panel) {

        return panelAssignments.get(panel);
    }

    //TODO: dodac obsługe prędkości kamery z konfiguracji JSON
    private void updateCameraPosition(
            PTZCamera camera, int speed) {camera.moveAbsolute(camera.getPan(),camera.getTilt(), speed);
    }

    /* ============================
             METODY POMOCNICZE
       ============================  */

    //kalkulatory

    @SuppressWarnings("unchecked")
    private double midiToDegrees(
            PTZCamera camera,
            String fieldName,
            int midiValue) {

        FieldDefinition field = camera.getFieldDefinition(fieldName);

        if (field == null) {
            throw new IllegalArgumentException("Nie znaleziono pola: "+ fieldName);
        }

        Map<String, Object> mapping = field.getMapping();
        Map<String, Object> controllerRange = (Map<String, Object>)mapping.get("controllerRange");

        double minDegrees = ((Number)controllerRange.get("minDegrees")).doubleValue();
        double neutralDegrees = ((Number)controllerRange.get("neutralDegrees")).doubleValue();
        double maxDegrees = ((Number)controllerRange.get("maxDegrees")).doubleValue();

        double degrees;

        // zabezpieczenie
        midiValue = Math.max(0, Math.min(127, midiValue));

        // zakres dodatni
        if (midiValue >= 64) {
            double ratio = (midiValue - 64.0) / (127.0 - 64.0);
            degrees = neutralDegrees + (ratio * (maxDegrees - neutralDegrees));
        } else {
            // zakres ujemny
            double ratio = (64.0 - midiValue) / 64.0;
            degrees = neutralDegrees - (ratio * (neutralDegrees - minDegrees));
        }

        //SCAFF
        //System.out.printf("MIDI=%d -> %.2f°%n", midiValue, degrees);

        return degrees;
    }

    private double getPanStep(
            int speed) {

        switch (speed) {

            case 1:
                return PAN_STEP_SLOW;

            case 2:
                return PAN_STEP_MEDIUM;

            default:
                return PAN_STEP_FAST;
        }
    }

    //Mapowanie
    private void loadDefaultMappings() {
        ccMap.put(11, MainGUI.UiControl.TILT);
        ccMap.put(0, MainGUI.UiControl.ZOOM);
        ccMap.put(19, MainGUI.UiControl.JOG);
    }

    private void initMappings() {

        loadDefaultMappings();
    }

    //kontrolki i watchdogi
    private void updateJogTimestamp(Panel panel) {

        long now = System.currentTimeMillis();

        switch (panel) {
            case A:
                panelALastJogTime = now;
                break;

            case B:
                panelBLastJogTime = now;
                break;
        }
    }

    private void startJogWatchdog() {

        Thread watchdog = new Thread(() -> {
                    while (true) {
                        try {Thread.sleep(50);

                            long now =System.currentTimeMillis();
                            checkPanelJogTimeout(Panel.A,now);
                            checkPanelJogTimeout(Panel.B,now);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, "JogWatchdog");

        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void checkPanelJogTimeout(
            Panel panel,
            long now) {

        PTZCamera camera = getCamera(panel);

        if (camera == null) {return;}

        long lastTime = getJogTimestamp(panel);

        if (lastTime == 0) {return;}

        if ((now - lastTime) > JOG_TIMEOUT_MS) {
            camera.movePanTilt(
                    0,
                    0
            );

            switch (panel) {
                case A:
                    panelALastJogTime = 0;
                    break;
                case B:
                    panelBLastJogTime = 0;
                    break;
            }
        }
    }

    //Gettery i settery

    public  ModeState getMode(Control control) {
        return this.controlStates.get(control);
    }

    private long getJogTimestamp(Panel panel) {

        switch (panel) {
            case A:
                return panelALastJogTime;
            case B:
                return panelBLastJogTime;
            default:
                return 0;
        }
    }
}