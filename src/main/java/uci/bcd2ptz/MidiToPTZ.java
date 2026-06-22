package uci.bcd2ptz;

import javax.swing.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class MidiToPTZ {

    //=================== ENUMS =================

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

    private final Map<Control, ModeState> controlStates = new EnumMap<>(Control.class);

    private MidiEventRouter midi;
    private MidiOutputController midiOut;

    private MainGUI gui;

    //kontrola statusu i komunikacji MIDI
    private boolean waitingForStateSync = false;
    private boolean acceptingPanTilt = true;
    private long lastJogTime = 0;

    public MidiToPTZ(MidiEventRouter midi, MidiOutputController midiOut, MainGUI gui) {
        this.midi = midi;
        this.midiOut = midiOut;
        this.gui = gui;
        controlStates.put(Control.TILT, ModeState.JOYSTICK);
        controlStates.put(Control.FOCUS, ModeState.AUTO);
    }

    // ==================== START ====================

    public void start() {

        updateTiltIndicators();

        new Thread(() -> {

            BlockingQueue<String> queue = midi.getParsedQueue();

            while (true) {
                try {
                    String msg = queue.take();

                    if (msg.startsWith("CC_")) {
                        handleCC(msg);
                    } else if (msg.startsWith("NT_")) {
                        handleNote(msg);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }, "MIDI->PTZ").start();
    }

    // ==================== MIDI → SYSTEM ====================

    private void handleCC(String msg) {


        String[] p = msg.split("_");

        int cc = Integer.parseInt(p[2]);
        int value = Integer.parseInt(p[3]);

       // ===== RESPONSE NA stateRequest =====

        if (waitingForStateSync && cc == 11) {

            waitingForStateSync = false;

            if (isNeutral(value)) {
                enterJoystickMode(Control.TILT);
            } else {
                enterWaitNeutral();
            }

            return;
        }

        ModeState tiltMode = controlStates.get(Control.TILT);

        //---- WAIT MODE ---------------
        if (tiltMode == ModeState.WAIT_NEUTRAL) {
            if (isNeutral(value)) {
                enterJoystickMode(Control.TILT);
            }
            return;
        }

        gui.updateTiltSlider(value);

        if (!acceptingPanTilt) return;

        switch (cc) {

            case 11: // TILT
                if (controlStates.get(Control.TILT) == ModeState.ABSOLUTE) {
                    ViscaPTZController.tiltAbsolute(value);

                } else {
                    ViscaPTZController.tilt(value);
                }
                break;

            case 0: // ZOOM
                gui.updateZoomSlider(value);
                ViscaPTZController.zoom(value);
                break;

            case 19: // JOG
                handleJog(value);
                break;
        }
    }

    private void handleJog(int value) {

        long now = System.currentTimeMillis();
        long delta = now - lastJogTime;
        lastJogTime = now;

        int direction = value > 64 ? 1 : -1;
        int speed = (delta < 30) ? 3 : (delta < 200 ? 2 : 1);

        gui.updateJog(direction, speed);

        if (controlStates.get(Control.TILT) == ModeState.ABSOLUTE) {
            ViscaPTZController.panAbsolute(value + speed * direction);
        } else {
            ViscaPTZController.pan(value + speed * direction);
        }
    }

    private void handleNote(String msg) {

        String[] p = msg.split("_");

        int note = Integer.parseInt(p[2]);
        int value = Integer.parseInt(p[3]);

        if (value <= 0) return;

        switch (note) {

            case 5: // TILT MODE
                handleModeButton(Control.TILT);
                break;

            case 10: // FUTURE FOCUS
                toggleFocus();
                break;
        }
    }

    // ==================== GUI → SYSTEM ====================

    public void onGuiTilt(int value) {
        handleCC("CC_1_11_" + value);
    }

    public void onGuiZoom(int value) {
        handleCC("CC_1_0_" + value);
    }

    public void onGuiModeClick(Control control) {
        handleModeButton(control);
    }

    // ==================== MODE ====================

    private void handleModeButton(Control control) {

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

        updateTiltIndicators();

        ViscaPTZController.syncPosition(() ->
                System.out.println("ABS READY")
        );
    }

    private void enterWaitNeutral() {

        controlStates.put(Control.TILT, ModeState.WAIT_NEUTRAL);

        acceptingPanTilt = false;

        updateTiltIndicators();

        System.out.println("WAITING FOR NEUTRAL");
    }



    private void enterJoystickMode(Control control) {

        controlStates.put(control, ModeState.JOYSTICK);
        acceptingPanTilt = true;

        updateTiltIndicators();
        gui.updateTiltSlider(64);
    }

    // ==================== INDICATORS ====================

    private void updateTiltIndicators() {

        switch (controlStates.get(Control.TILT)) {

            case JOYSTICK:
                midiOut.setLed(21, MidiOutputController.LedMode.OFF);
                gui.setTiltModeLed(buttonLED.LedMode.OFF);
                break;

            case ABSOLUTE:
                midiOut.setLed(21, MidiOutputController.LedMode.ON);
                gui.setTiltModeLed(buttonLED.LedMode.ON);
                break;

            case WAIT_NEUTRAL:
                midiOut.setLed(21, MidiOutputController.LedMode.BLINK);
                gui.setTiltModeLed(buttonLED.LedMode.BLINK);
                break;
        }
    }

    // ==================== FOCUS ====================

    private void toggleFocus() {
        System.out.println("TODO: focus");
    }

    private boolean isNeutral(int v) {
        return Math.abs(v - 64) <= 1;
    }

    //Gettery i settery

    public  ModeState getMode(Control control) {
        return this.controlStates.get(control);
    }

}