package uci.bcd2ptz.core;

import uci.bcd2ptz.gui.MainGUI;
import uci.bcd2ptz.midi.MidiController;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.ptz.ParameterState;
import uci.bcd2ptz.visca.FieldDefinition;
import uci.bcd2ptz.visca.ViscaPTZController;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static uci.bcd2ptz.core.ControlCore.Control.*;
import static uci.bcd2ptz.core.ControlCore.ControlPanelId.*;
import static uci.bcd2ptz.core.ControlEvent.EventSource.*;

public class ControlCore {


    public enum Control {
        TILT,
        PAN,
        ZOOM,
        FOCUS,
        AUTO_FOLLOW,
        TALLY,
        EXPOSURE_MODE,
        EXPOSURE_COMPENSATION,
        EXPOSURE_GAIN,
        EXPOSURE_IRIS,
        EXPOSURE_SHUTTER
    }

    public enum ModeState {
        JOYSTICK,
        ABSOLUTE,
        WAIT_NEUTRAL,
        LOCK,
        ACTIVE,
        AUTO,
        MANUAL,
        ON,
        BLINKING,
        OFF,
        IRIS_PRIORITY,
        SHUTTER_PRIORITY
    }
    public enum ControlPanelId {
        A,
        B
    }
    public enum RunMode {
        FULL,
        NO_HARDWARE
    }

    // STANY SYSTEMU
    private final Map<ControlPanelId, ControlPanel> panels = new EnumMap<>(ControlPanelId.class);
    private RunMode mode;


    // COMMUNICATION With MIDI, GUI & CAMERAS
    private final PTZCameraManager cameraManager;
    private MidiController midiController;
    private MainGUI gui;
    private ControlEventBus inputBus;


    // Kontrola statusu
    private boolean waitingForStateSync = false;

    //LOGGER
    private static final Logger LOGGER = Logger.getLogger(ControlCore.class.getName());

     /*========================================
             KONSTRUKTOR i URUCHOMIANIE
       ========================================*/

     /**
     * Konstruktor klasy MidiToPTZ
     *
     * @param cameraManager
     * @param midiController
     */

    public ControlCore(PTZCameraManager cameraManager, MidiController midiController, ControlEventBus inputBus, RunMode mode) {

        this.cameraManager = cameraManager;
        this.midiController = midiController;
        this.inputBus = inputBus;
        this.mode = mode;

        panels.put(A, new ControlPanel(A));
        panels.get(A).getStates().put(TILT, ModeState.JOYSTICK);
        panels.get(A).getStates().put(FOCUS, ModeState.AUTO);
        panels.get(A).getStates().put(TALLY, ModeState.OFF);
        panels.get(A).getStates().put(AUTO_FOLLOW, ModeState.OFF);
        panels.get(A).getStates().put(EXPOSURE_MODE, ModeState.AUTO);
        panels.get(A).setAcceptingPanTilt(true);

        panels.put(B, new ControlPanel(B));
        panels.get(B).getStates().put(TILT, ModeState.JOYSTICK);
        panels.get(B).getStates().put(FOCUS, ModeState.AUTO);
        panels.get(B).getStates().put(TALLY, ModeState.OFF);
        panels.get(B).getStates().put(AUTO_FOLLOW, ModeState.OFF);
        panels.get(B).getStates().put(EXPOSURE_MODE, ModeState.AUTO);
        panels.get(B).setAcceptingPanTilt(true);

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
     * @see #handleControlEvent(ControlEvent)     *
     * */


    public void start() {

        if (mode != RunMode.NO_HARDWARE && midiController != null) {
            midiController.start();
        }

        for (ControlPanel panel : panels.values()){
            PTZCamera camera = panel.getAssignedCamera();
            if (camera != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("exposureMode", "AUTO");
                camera.executeViscaCommand("exposureMode", params);
                params.put("EXPOSURE_IRIS", "LOCK");
                params.put("EXPOSURE_SHUTTER", "LOCK");
            }
        }

        new Thread(() -> {

            while (true) {
                try {
                    ControlEvent event = inputBus.take();
                    //DEBUG:
                   // System.out.println("CONTROL CORE QUEQUE LENGTH = " + queue.size());
                    handleControlEvent(event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }, "CONTROL_CORE").start();
    }

     /*==========================================
               HANDLE MOVE AND PARAMS CHANGE
       ==========================================*/

    private void handleControlEvent(ControlEvent event) {

        if (event == null) {
            return;
        }

        //String panelId = event.getPanel();

        ControlPanel panel = panels.get(event.getPanel());

        // DEBUG
        //System.out.println("=========== CORE ========= \nhandleControlEvent: " + event.getEventSource() + " action: " + event.getAction());
        //System.out.println("Panel: " + panel.getId());

        if (panel == null) {
            System.out.println("Panel not found: " + event.getPanel());
            return;
        }

        PTZCamera camera = panel.getAssignedCamera();

        if (camera == null) {
            System.out.println("Panel " + event.getPanel() + " has no assigned camera");
            return;
        }

        int value = event.getValue();
        String action =event.getAction();

        /* Synchronizacja po wyjściu z ABS */

        if (waitingForStateSync && "TILT".equals(action)) {

            waitingForStateSync = false;

            if (isNeutral(value)) {enterJoystickMode(panel, TILT);
            } else {
                enterWaitNeutral(panel, TILT);
            }
            return;
        }

        switch (action) {

            case "REQUEST_RESPONSE":
                handleMidiStatusResponse(event);
                return;

            case "PAN":
                handlePan(panel, value, event.getAction());
                break;

            case "TILT":
                handleTilt(panel, value, event.getAction());
                break;

            case "ZOOM":
                handleZoom(panel, value, event.getAction());
                break;

            case "EXPOSURE_COMPENSATION":
                if(panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO) {
                    handleExposureParameter(panel, value, "exposureCompensation", event.getAction());
                    //handleExposureCompensation(panel, value, event.getAction());
                }
                if(panel.getStates().get(EXPOSURE_MODE) == ModeState.MANUAL) {
                    handleExposureParameter(panel, value, "exposureGain", event.getAction());
                    //handleExposureGain(panel, value, event.getAction());
                }
                break;

            case "EXPOSURE_IRIS":
                handleExposureParameter(panel, value, "exposureIris", event.getAction());
                break;

            case "EXPOSURE_SHUTTER":
                handleExposureParameter(panel, value, "exposureShutter", event.getAction());
                break;

            case "ABSOLUTE_MODE_KEY":
                if (event.getValue() == 0) {return;}
                handlePTZMode(panel,TILT);
                break;

            case "TALLY_KEY":
                if (event.getValue() == 0) {return;}
                    handleTally(panel, TALLY);
                break;

            case "AUTO_SHOOT_KEY":
                if (event.getValue() == 0) {return;}
                    handleAutoFollow(panel, AUTO_FOLLOW);
                break;

            case "EXPOSURE_MODE_KEY":
                if (event.getValue() == 0) {return;}
                handleExposureMode(panel, EXPOSURE_MODE);
                break;

            default:
                System.out.println(
                        "Unknown action: "
                                + action
                );
        }
    }

    //TODO: Obsługa innych trybów pracy JOGA albo wyrzucenie obsługi trybów do MidiController - tu tylko obsługa z gotową prędkością
    private void handlePan(ControlPanel panel, int value, String controlId) {

        //Locked when in Auto Follow mode
        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            return;
        }
        //DEBUG:
//        System.out.println("===========CONTROL CORE ===============\n" +
//                "handle PAN " + controlId + " -> Received value: " + value);

        PTZCamera camera = panel.getAssignedCamera();

        if (camera == null) {return;}

        ModeState mode = panel.getStates().get(TILT);
        int speed = 0;

        //DEBUG:
        //System.out.println("MODE = " + mode);

        switch (mode) {

            case WAIT_NEUTRAL:
                break;

            case JOYSTICK:
                if (value == 0) {
                    camera.stop();
                    //Debug;
                    //System.out.println("=> STOP REQUEST");
                    return;
                }
                //Debug
                //System.out.println("=> PAN speed = " + speed);
                camera.movePanTilt(value * 4, 0);
                ControlEvent eventJOY = new ControlEvent(
                        SYSTEM,
                        "PAN",
                        panel.getId(),
                        controlId,
                        value);

                gui.handleControlEvent(eventJOY);
                return;

            case ABSOLUTE:
                //DEBUG:
                //System.out.println("ABSOLUTE");

                double fullrange = camera.getParameter("panPosition").getMax() - camera.getParameter("panPosition").getMin();
                double deegressPerTick = fullrange / midiController.getConfig().getJogResolution();
                Double delta = value * deegressPerTick;
                double stepSize = Math.abs(delta);
                if ( stepSize <= 5.0){
                    speed = 10;
                } else {
                    speed = Math.min((10 + (int)(Math.round(stepSize - 5.0) * 8.0 /15)), 18);
                }
                double newPan = camera.getParameter("panPosition").getCurrent() - delta;
                camera.getParameter("panPosition").setCurrent(newPan);
                camera.moveAbsolute(camera.getParameter("panPosition").getCurrent(), camera.getParameter("tiltPosition").getCurrent(), speed);

                ControlEvent eventABS = new ControlEvent(
                        SYSTEM,
                        "PAN",
                        panel.getId(),
                        controlId,
                        value);

                gui.handleControlEvent(eventABS);
                break;
        }
    }

    private void handleTilt(ControlPanel panel, int value, String controlId) {

        //DEBUG
        //System.out.println("handleMidiTilt - Received value: " + value);

        //Locked when in Auto Follow mode
        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            return;
        }

        if (panel.getStates().get(TILT) == ModeState.WAIT_NEUTRAL) {
            if (isNeutral(value)) {
                enterJoystickMode(panel, TILT);
            }
            return;
        }

        if (!panel.isAcceptingPanTilt()) {return;}

        PTZCamera camera = panel.getAssignedCamera();

        if (camera == null) {return;}

        //DEBUG:
        //System.out.println("MODE: " + panel.getStates().get(TILT));

        if (panel.getStates().get(TILT) == ModeState.ABSOLUTE) {

            double oldTilt = camera.getParameter("tiltPosition").getCurrent();
            double newTilt = midiToDegrees(camera, "tiltPosition", value);

            //TODO: dopracować synchro szybkości do ruchu hebla
            //double delta = Math.abs(newTilt - oldTilt);
            //int speed = Math.max(1,Math.min(15,(int)Math.round(delta)));
            int speed = 8;

            //DEBUG:
            /*System.out.println(
                    "ABS TILT: value=" + value +
                            " current=" + camera.getTilt() +
                            " target=" + newTilt
            );*/

            camera.getParameter("tiltPosition").setCurrent(newTilt);
            camera.moveAbsolute(camera.getParameter("panPosition").getCurrent(), camera.getParameter("tiltPosition").getCurrent(), speed);
        } else {
            camera.movePanTilt(0,value -64);
        }

        ControlEvent event = new ControlEvent(
                SYSTEM,
                "TILT",
                panel.getId(),
                controlId,
                value);

        if (mode == RunMode.FULL) {
            midiController.sendControlEvent(event);
        }
        gui.handleControlEvent(event);
    }

    //TODO: zaimplementować zoom w trybie joystick
    private void handleZoom(ControlPanel panel, int value, String controlId) {

        //Locked when in Auto Follow mode
        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            return;
        }

        PTZCamera camera = panel.getAssignedCamera();

        if (camera == null) {return;}

        FieldDefinition zoomField =camera.getController()
                        .getFieldDefinition("zoomPosition");

        Map<String, Object> mapping =zoomField.getMapping();
        Map<String, Object> mechanicalRange = (Map<String, Object>)mapping.get("mechanicalRange");

        int maxZoom = ((Number) mechanicalRange.get("max")).intValue();

        int  zoomPosition = (value * maxZoom) / 170;

        //DEBUG
        /*System.out.println("========= CONTROL CORE ===========" +
                "\nEVENT value = " + value +
                "\nZOOM max position = " + maxZoom +
                "\nZOOM position = " + zoomPosition);*/

        camera.getParameter("zoomPosition").setCurrent(zoomPosition);
        camera.moveZoomAbsolute(zoomPosition);

        ControlEvent event =
                new ControlEvent(
                        SYSTEM,
                        "ZOOM",
                        panel.getId(),
                        controlId,
                        value
                );

        gui.handleControlEvent(event);
    }

    private void handleExposureParameter(ControlPanel panel, int midiValue, String parameterName, String guiEventName) {

        //DEBUG
        System.out.println("=========== CORE ================\n" +
                "handleExposureParameter: " + parameterName + " VAL received: " + midiValue + "  event ACTION: " + guiEventName);

        PTZCamera camera = panel.getAssignedCamera();

        if (camera == null) {
            return;
        }

        String parameterInquiry = parameterName + "Position";
        //DEBUG
        //System.out.println("parameterInquiry string: " + parameterInquiry);


        ParameterState parameter = camera.getParameter(parameterInquiry,true);

        if (parameter == null) {
            LOGGER.warning("No "+ parameterName + " in camera " + camera.getCameraId());
            return;
        }

        FieldDefinition field = camera.getController().getFieldDefinition(parameterInquiry);
        int targetValue = midiToMappedValue(midiValue,field);
        int currentValue = (int) parameter.getCurrent();

        if (targetValue == currentValue) {
            return;
        }

        // MIDI control clutching
        if (panel.getStates().get(Control.valueOf(guiEventName)) == ModeState.LOCK){

            int lastMidi = panel.getMidiCatch().get(Control.valueOf(guiEventName));
            int expectedMidi = mappedValueToMidi(currentValue, field);

            boolean crossed = (
                    (lastMidi < expectedMidi &&  midiValue >= expectedMidi) ||
                            (lastMidi > expectedMidi &&  midiValue <= expectedMidi)
                );

            //debug
            System.out.println("KNOB CLUTCHED. EXPECTED: " + expectedMidi + "  RECEIVED: " + midiValue);
            if (!crossed){
                return;
            }
            panel.getStates().put(Control.valueOf(guiEventName), ModeState.ON);
        }


        int minLevel = (int) parameter.getMin();
        int maxLevel = (int) parameter.getMax();
        int delta = targetValue - currentValue;

        //DEBUG
        //System.out.println("Target value for " + parameterName + " = " + targetValue);

        if (delta > 0) {
            for (int i = 0; i < delta; i++) {
                if ((currentValue + i) > maxLevel) {
                    break;
                }
                camera.adjustExposureParameter(parameterName,1);
            }
        } else {
            for (int i = 0; i < Math.abs(delta); i++) {
                if ((currentValue - i) < minLevel) {
                    break;
                }
                camera.adjustExposureParameter(parameterName,-1);
            }
        }

        parameter.setCurrent(targetValue);

        int normalizedMidi = mappedValueToMidi(targetValue, field);

        ControlEvent event = new ControlEvent(
                        SYSTEM,
                        guiEventName,
                        panel.getId(),
                        guiEventName,
                        normalizedMidi
                );

        gui.handleControlEvent(event);
    }

    /* ==============================================================================
                HANDLE CAMERA MODES (PTZ move mode, exposition mode, automations)
       ==============================================================================*/

    private void handlePTZMode(ControlPanel panel, Control control) {

        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            return;
        }

        // ======= TILT MODE ==========
        if (control == TILT) {

        //DEBUG
        //System.out.println("Current ABS/JOY state: " + panel.getStates().get(TILT));

            switch (panel.getStates().get(control)) {
                case JOYSTICK:
                    enterAbsoluteMode(panel, control);
                    break;

                case ABSOLUTE:
                    ControlEvent stateRequest = new ControlEvent(
                            SYSTEM,
                            "TILT",
                            panel.getId(),
                            "STATUS_REQUEST",
                            0
                    );
                    midiController.sendControlEvent(stateRequest);
                    break;

                case WAIT_NEUTRAL:
                    break;
            }
        }
    }

    private void enterAbsoluteMode(ControlPanel panel, Control control) {

        panel.getStates().put(control, ModeState.ABSOLUTE);
        panel.setAcceptingPanTilt(true);

        PTZCamera camera = panel.getAssignedCamera();

        if (camera == null) {
            return;
        }

        updateIndicator(panel, "ABSOLUTE_MODE_INDICATOR", 1);

        LOGGER.warning("Camera " + camera.getCameraId() + " position synchronization failed");

        //Debug
        /*if (camera.syncPosition()) {
            System.out.println("PAN position: " + camera.getParameter("panPosition").getCurrent());
            System.out.println("TILT position: " + camera.getParameter("tiltPosition").getCurrent());
            System.out.println("ABS READY");
        } else {

            System.out.println("PAN position: " + camera.getParameter("panPosition").getCurrent());
            System.out.println("TILT position: " + camera.getParameter("tiltPosition").getCurrent());
            System.out.println("ABS SYNC FAILED");
        }*/
        //System.out.println("CONTROL CORE MODE STATUS: " + mode.toString());
        //System.out.println( "MIDI event send: " + event.toString());
    }

    private void enterWaitNeutral(ControlPanel panel, Control control) {

        panel.getStates().put(control, ModeState.WAIT_NEUTRAL);
        panel.setAcceptingPanTilt(false);

        ControlEvent event = new ControlEvent(
                SYSTEM,
                "ABSOLUTE_MODE_INDICATOR",
                panel.getId(),
                "ABSOLUTE_MODE_INDICATOR",
                2);

        if (mode == RunMode.FULL) {
            midiController.sendControlEvent(event);
        }
        gui.handleControlEvent(event);

        System.out.println("WAITING FOR NEUTRAL");
    }

    private void enterJoystickMode(ControlPanel panel, Control control) {

        panel.getStates().put(control, ModeState.JOYSTICK);
        panel.setAcceptingPanTilt(true);
        updateIndicator(panel, "ABSOLUTE_MODE_INDICATOR", 0);
    }

    private void handleTally(ControlPanel panel, Control control) {

        if (control == TALLY) {

            PTZCamera camera = panel.getAssignedCamera();

            //DEBUG
            //System.out.println("=> TALLY HANDLER " + panel.getStates().get(TALLY));
            ControlEvent event = null;
            ModeState tallyStatus = null;

            if (panel.getStates().get(TALLY) == ModeState.OFF) {
                camera.recallPreset("tally_On");
                tallyStatus = ModeState.ON;
                updateIndicator(panel, "TALLY_INDICATOR", 1);
            }
            if (panel.getStates().get(TALLY) == ModeState.ON) {
                camera.recallPreset("tally_Off");
                tallyStatus = ModeState.OFF;
                updateIndicator(panel, "TALLY_INDICATOR", 0);
            }

            panel.getStates().put(TALLY, tallyStatus);
        }
    }

    private void handleAutoFollow(ControlPanel panel, Control control) {

        if (control == AUTO_FOLLOW) {

            PTZCamera camera = panel.getAssignedCamera();

            //DEBUG
            //System.out.println("=> ATOFOLLOW HANDLER " + panel.getStates().get(TALLY));
            ControlEvent event = null;
            ModeState autoFollowStatus = null;

            if (panel.getStates().get(AUTO_FOLLOW) == ModeState.OFF) {
                camera.recallPreset("smartShoot_On");
                autoFollowStatus = ModeState.ON;
                updateIndicator(panel, "AUTO_SHOOT_INDICATOR", 1);
            }

            if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
                camera.recallPreset("smartShoot_Off");
                autoFollowStatus = ModeState.OFF;
                updateIndicator(panel, "AUTO_SHOOT_INDICATOR", 0);
            }

            panel.getStates().put(AUTO_FOLLOW, autoFollowStatus);
        }
    }

    private void handleExposureMode(ControlPanel panel,Control control) {

        if (control != EXPOSURE_MODE){
            return;
        }

        PTZCamera camera = panel.getAssignedCamera();
        ModeState nextMode;

        ControlEvent event = null;
        Map <String, Object> params = new HashMap<>();

        switch (panel.getStates().get(EXPOSURE_MODE)
        ) {
            case MANUAL:
                nextMode = ModeState.AUTO;
                params.put("exposureMode", "AUTO");
                camera.executeViscaCommand("exposureMode", params);
                panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                event = new ControlEvent(
                        SYSTEM,
                        "EXPOSURE_COMPENSATION",
                        panel.getId(),
                        "STATUS_REQUEST",
                        0

                );
                midiController.sendControlEvent(event);

                event = new ControlEvent(
                        SYSTEM,
                        "EXPOSURE_MODE_INDICATOR",
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        1);
                break;

            case AUTO:
                nextMode = ModeState.MANUAL;
                params.put("exposureMode", "MANUAL");
                camera.executeViscaCommand("exposureMode", params);

                //---- gain clutching
                //camera.getParameter("exposureGainPosition", true);
                event = new ControlEvent(
                        SYSTEM,
                        "EXPOSURE_COMPENSATION",
                        panel.getId(),
                        "STATUS_REQUEST",
                        0

                );
                panel.getStates().put(EXPOSURE_GAIN, ModeState.LOCK);
                midiController.sendControlEvent(event);

                //-- iris clutching
                //camera.getParameter("exposureIrisPosition", true);
                event = new ControlEvent(
                        SYSTEM,
                        "EXPOSURE_IRIS",
                        panel.getId(),
                        "STATUS_REQUEST",
                        0

                );
                panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                midiController.sendControlEvent(event);

                //---- shutter clutching
                //camera.getParameter("exposureIrisPosition", true);
                event = new ControlEvent(
                        SYSTEM,
                        "EXPOSURE_SHUTTER",
                        panel.getId(),
                        "STATUS_REQUEST",
                        0

                );
                panel.getStates().put(EXPOSURE_SHUTTER, ModeState.LOCK);
                midiController.sendControlEvent(event);

                //--- mode indicator event
                event = new ControlEvent(
                        SYSTEM,
                        "EXPOSURE_MODE_INDICATOR",
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        0);
                break;

            default:
                nextMode = ModeState.AUTO;
                params.put("exposureMode", "AUTO");
                camera.executeViscaCommand("exposureMode", params);
                event = new ControlEvent(
                        SYSTEM,
                        "EXPOSURE_MODE_INDICATOR",
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        0);

        }

        panel.getStates().put(EXPOSURE_MODE, nextMode);

        if (mode == RunMode.FULL) {
            midiController.sendControlEvent(event);
        }
        gui.handleControlEvent(event);
    }

    private boolean isNeutral(int v) {
        return Math.abs(v - 64) <= 1;
    }

    private void handleMidiStatusResponse(ControlEvent event) {

        System.out.println ("========= REQUEST MIDI VALUE ======= \n--> " + event.getControlID() + "  VAL = " + event.getValue());

        ControlPanel panel = panels.get(event.getPanel());
        if (panel == null) {
            return;
        }

        int expectedMidi = -1;

        switch (event.getControlID()) {

            case "EXPOSURE_COMPENSATION":
                 if (ModeState.MANUAL.equals(panel.getStates().get("EXPOSURE_MODE"))) {
                    //DEBUG
                    System.out.print("GAIN SETTING => ");
                    panel.getMidiCatch().put(Control.EXPOSURE_GAIN, event.getValue());
                    if (expectedMidi == event.getValue()) {
                        panel.getStates().put(Control.EXPOSURE_GAIN, ModeState.ON);
                    } else {
                        panel.getStates().put(Control.EXPOSURE_GAIN, ModeState.LOCK);
                    }
                } else {
                     //DEBUG
                     System.out.print("EXPOSURE COMP. SETTING => ");
                     panel.getMidiCatch().put(EXPOSURE_COMPENSATION, event.getValue());
                    if (expectedMidi == event.getValue()) {
                        panel.getStates().put(EXPOSURE_COMPENSATION, ModeState.ON);
                    } else {
                        panel.getStates().put(EXPOSURE_COMPENSATION, ModeState.LOCK);
                    }
                }
                //DEBUG
                System.out.println(" VALUE = " + event.getValue());

                break;

            case "EXPOSURE_IRIS":
                panel.getMidiCatch().put(Control.EXPOSURE_IRIS, event.getValue());
                if (expectedMidi == event.getValue()) {
                    panel.getStates().put(EXPOSURE_IRIS, ModeState.ON);
                } else {
                    panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                }

                break;

            case "EXPOSURE_SHUTTER":
                panel.getMidiCatch().put(EXPOSURE_SHUTTER, event.getValue());
                if (expectedMidi == event.getValue()) {
                    panel.getStates().put(EXPOSURE_SHUTTER, ModeState.ON);
                } else {
                    panel.getStates().put(EXPOSURE_SHUTTER, ModeState.LOCK);
                }
                break;

            case "TILT":
                if (isNeutral(event.getValue())) {
                    enterJoystickMode(panel, Control.TILT);
                } else{
                    enterWaitNeutral(panel, TILT);
                }
                break;
        }

        //DEBUG
        System.out.println("===== MIDI CHATCHES ======= \n"
                + panel.getMidiCatch().toString());
    }

    private void handleFocusMode(Control control) {
        System.out.println("TODO: focus");
    }

    private void updateStatusIndicators() {

        for (ControlPanel panel : panels.values()) {

            updateIndicator(panel,
                    "ABSOLUTE_MODE_INDICATOR",
                    panel.getStates().get(TILT) == ModeState.ABSOLUTE ? 1 : 0 );

            updateIndicator(
                    panel,
                    "AUTO_SHOOT_INDICATOR",
                    panel.getStates().get(AUTO_FOLLOW) == ModeState.OFF ? 0: 1);

            updateIndicator(
                    panel,
                    "EXPOSURE_MODE_INDICATOR",
                    panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO ? 1 : 0);

            updateIndicator(
                    panel,
                    "TALLY_INDICATOR",
                    0);
        }
    }

    private void updateIndicator(ControlPanel panel, String action, int value) {

        ControlEvent event = new ControlEvent(
                SYSTEM,
                action,
                panel.getId(),
                action,
                value
        );

        if (mode == RunMode.FULL) {
            midiController.sendControlEvent(event);
        }
        gui.handleControlEvent(event);

    }

    /* ==========================================
             ZARZĄDANIE I STEROWANIE KAMERAMI
       ==========================================*/

    public void assignCamera(ControlPanelId panel, PTZCamera camera) {

        panels.get(panel).setAssignedCamera(camera);
        //DEBUG do testów
        System.out.println(panel + " -> " + camera.getCameraId());
    }

    private PTZCamera getCamera(ControlPanelId panel) {

        return panels.get(panel).getAssignedCamera();
    }

    /* ============================
             METODY POMOCNICZE
       ============================
*/
    //kalkulatory

    @SuppressWarnings("unchecked")
    private double midiToDegrees(PTZCamera camera,String fieldName,int midiValue) {

        FieldDefinition field = camera.getController().getFieldDefinition(fieldName);

        if (field == null) {
            throw new IllegalArgumentException("Nie znaleziono pola: "+ fieldName);
        }

        Map<String, Object> mapping = field.getMapping();
        Map<String, Object> controllerRange = (Map<String, Object>)mapping.get("mechanicalRange");

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

    private int midiToMappedValue(int midivalue, FieldDefinition field) {

        Map<String, Object> mapping = field.getMapping();

        if (mapping == null) {
            throw new IllegalArgumentException("Filed has no mapping definition");
        }

        String mappingType = (String) mapping.get("type");

        if ("lookup".equalsIgnoreCase(mappingType)) {
            int mappedMin = ((Number) mapping.get("mappedMin")).intValue();
            int mappedMax = ((Number) mapping.get("mappedMax")).intValue();
            int steps = mappedMax - mappedMin + 1;
            int bucket = (int) Math.floor(midivalue * steps /128.0);

            if (bucket >= steps){
                bucket = steps -1;
            }
            return mappedMin + bucket;
        }

        Map<String, Object> range = field.getRange();
        if (range == null){
            throw new IllegalStateException("Unsupported mapping type: " + mappingType);
        }
        int mappedMin = ((Number) range.get("min")).intValue();
        int mappedMax = ((Number) range.get("max")).intValue();
        int steps = mappedMax - mappedMin + 1;
        int bucket = (int) Math.floor(midivalue * steps / 128.0);

        if (bucket >= steps) {
            bucket = steps - 1;
        }
        return bucket;
    }

    private int mappedValueToMidi(int mappedValue,FieldDefinition field) {

        if (field == null) {
            return 64;
        }

        Map<String, Object> mapping = field.getMapping();

        if (mapping == null) {
            return 64;
        }

        int mappedMin = ((Number) mapping.get("mappedMin")).intValue();
        int mappedMax = ((Number) mapping.get("mappedMax")).intValue();

        if (mappedMin < 0) {

            int shiftedValue = mappedValue - mappedMin;
            int shiftedRange = mappedMax - mappedMin;
            //debug
           /* System.out.println("value to mapped MIDI = " + mappedValue + " |==> shifted\n mappedMin = " + mappedMin + "  mappedMax = "+ mappedMax +
                    "\shiftedValue = " + shiftedValue + "  shiftedrange = " + shiftedRange);*/
            return (int) Math.round(shiftedValue * 127.0 / shiftedRange);
        }
        //debug
        /*System.out.println("value to mapped MIDI => standard");*/
        return (int) Math.round(mappedValue * 127.0 / mappedMax);
    }

    //Gettery i settery

    public void setGUI (MainGUI gui){

        this.gui = gui;
        if (this.gui != null) {
            updateStatusIndicators();
        }
    }

}