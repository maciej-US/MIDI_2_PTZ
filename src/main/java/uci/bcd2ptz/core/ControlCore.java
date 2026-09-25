package uci.bcd2ptz.core;

import uci.bcd2ptz.gui.MainGUI;
import uci.bcd2ptz.midi.MidiController;
import uci.bcd2ptz.midi.MidiControllerManager;
import uci.bcd2ptz.ptz.PTZCamera;
import uci.bcd2ptz.ptz.PTZCameraManager;
import uci.bcd2ptz.ptz.ParameterState;
import uci.bcd2ptz.visca.FieldDefinition;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import static uci.bcd2ptz.core.ControlCore.Control.*;
import static uci.bcd2ptz.core.ControlCore.ControlPanelId.*;
import static uci.bcd2ptz.core.ControlEvent.EventSource.*;

public class ControlCore {


    public enum Control {
        TILT,
        PAN,
        ZOOM,
        PTZ_MODE,
        FOCUS,
        FOCUS_MODE,
        AUTO_FOLLOW,
        TALLY,
        EXPOSURE_MODE,
        EXPOSURE_COMPENSATION,
        EXPOSURE_GAIN,
        EXPOSURE_IRIS,
        EXPOSURE_SHUTTER,
        WHITE_BALANCE_MODE,
        WHITE_BALANCE_PUSH,
        WHITE_BALANCE_R_GAIN,
        WHITE_BALANCE_B_GAIN
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
        B,
        C,
        D,
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
    private final MidiControllerManager midiManager;
    private final MidiController midiController;
    private MainGUI gui;
    private final ControlEventBus inputBus;


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
     * @param
     */

    public ControlCore(PTZCameraManager cameraManager, MidiControllerManager midiManager, ControlEventBus inputBus, RunMode mode) {

        this.cameraManager = cameraManager;
        this.midiManager = midiManager;
        this.midiController = midiManager.getMidiController();
        this.inputBus = inputBus;
        this.mode = mode;

        for (ControlPanelId panelId : ControlPanelId.values()) {

            if (cameraManager.getCoreAssignedCamera(panelId) != null) {
                panels.put(
                        panelId,
                        createPanel(panelId)
                );
            }
        }
    }


    /**
     * Uruchamia główną pętlę sterowania systemu.
     * Metoda inicjalizuje mapowania kontrolek MIDI, odświeża wskaźniki GUI
     * oraz uruchamia dedykowany wątek nasłuchujący kolejki zdarzeń MIDI.
     * Obsługiwane są dwa typy komunikatów:
     * <ul>
     *     <li>CC (Control Change) - wartości suwaków, enkoderów i pokręteł</li>
     *     <li>NT (Note On/Off) - przyciski funkcyjne i przełączniki</li>
     * </ul>
     * Wątek pracuje w sposób ciągły przez cały czas działania aplikacji
     * i przekazuje odebrane komunikaty do odpowiednich metod obsługi.
     *
     * @see #handleControlEvent(ControlEvent)     *
     * */


    public void start() {

        if (mode != RunMode.NO_HARDWARE && midiController != null) {
            midiController.start();
        }

        //AUtomiation initial states configuration

        for (ControlPanel panel : panels.values()){
            PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
            if (camera != null) {
                //--- exposure mode
                ParameterState stateParam = new ParameterState();
                stateParam.setCurrentState("AUTO");
                camera.setExposureMode ("AUTO");
                camera.getParameters().put("exposureMode", stateParam);
                //--- white balance mode
                stateParam = new ParameterState();
                stateParam.setCurrentState("AUTO");
                camera.getParameters().put("whiteBalanceMode", stateParam);
                camera.setWhiteBalanceMode("AUTO");
                // --- focus
                stateParam = new ParameterState();
                stateParam.setCurrentState("AUTO");
                camera.getParameters().put("focusPosition", stateParam);
                camera.setFocusMode("AUTO");
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

        //REBINDING MIDI
        if (event.getAction().startsWith("CAMERA_SELECT_")){
            handleCameraSelect(event);
            return;
        }

        ControlPanel targetPanel = panels.get(resolveControlPanel(event));

        // DEBUG
       /* System.out.println("=========== CORE ========= \nhandleControlEvent: SOURCE" + event.getEventSource() + "   Source panel: " + event.getPanel() + " destination panel: " + targetPanel.getId() + "\n" +
                " action: " + event.getAction() + " value: " + event.getValue());
        System.out.println("Panel: " + targetPanel.getId());*/

        if (targetPanel == null) {
            //DEBUG
            //System.out.println("Panel not found: " + event.getPanel());
            return;
        }

        PTZCamera camera = cameraManager.getCoreAssignedCamera(targetPanel.getId());

        ControlEvent coreEvent = new ControlEvent(
                SYSTEM,
                targetPanel.getId(),
                event.getAction(),
                event.getValue()
        );

        int value = coreEvent.getValue();
        String action =coreEvent.getAction();

        /* Synchronizacja po wyjściu z ABS */

        if (waitingForStateSync && "TILT".equals(action)) {

            waitingForStateSync = false;

            if (isNeutral(value)) {enterJoystickMode(targetPanel, TILT);
            } else {
                enterWaitNeutral(targetPanel, TILT);
            }
            return;
        }

        switch (action) {

            case "REQUEST_RESPONSE":
                handleMidiStatusResponse(coreEvent);
                return;

            case "PAN":
                handlePan(coreEvent);
                break;

            case "TILT":
                handleTilt(coreEvent);
                break;

            case "ZOOM":
                handleZoom(coreEvent);
                break;

            case "FOCUS_MODE":
                handleFocusMode(coreEvent);
                break;

            case "FOCUS_UP_KEY":
                handleFocus(coreEvent);
                break;

            case "FOCUS_DOWN_KEY":
                handleFocus(coreEvent);
                break;

            case "FOCUS":
                if (targetPanel.getStates().get(FOCUS_MODE) == ModeState.MANUAL) {
                    handleFocus(coreEvent);
                }
                break;

            case "ONE_PUSH_FOCUS_KEY":
                if (targetPanel.getStates().get(FOCUS_MODE) == ModeState.MANUAL){
                    handleFocusOnePush(coreEvent);
                }
                break;

            case "EXPOSURE_COMPENSATION":
                handleCameraImageParameter(coreEvent);
                break;

            case "EXPOSURE_IRIS":
                handleCameraImageParameter(coreEvent);
                break;

            case "EXPOSURE_SHUTTER":
                handleCameraImageParameter(coreEvent);
                break;

            case "PTZ_MODE":
                if (coreEvent.getValue() == 0) {return;}
                handlePTZMode(coreEvent);
                break;

            case "TALLY":
                if (coreEvent.getValue() == 0) {return;}
                    handleTally(coreEvent);
                break;

            case "AUTO_FOLLOW":
                if (coreEvent.getValue() == 0) {return;}
                    handleAutoFollow(coreEvent);
                break;

            case "EXPOSURE_MODE":
                if (coreEvent.getValue() == 0) {return;}
                handleExposureMode(coreEvent);
                break;

            case "WHITE_BALANCE_MODE":
                handleWBMode(coreEvent);
                break;

            case "ONE_PUSH_WHITE_BALANCE_KEY":
                handleWBPush(coreEvent);
                break;

            case "WHITE_BALANCE_R_GAIN":
                handleCameraImageParameter(coreEvent);
                break;

            case "WHITE_BALANCE_B_GAIN":
                handleCameraImageParameter(coreEvent);
                break;

            case "CAMERA_SELECT_A":
            case "CAMERA_SELECT_B":
            case "CAMERA_SELECT_C":
            case "CAMERA_SELECT_D":
                handleCameraSelect(coreEvent);
                break;

            default:
                System.out.println(
                        "Unknown action: "
                                + action
                );
        }
    }

    //TODO: Obsługa innych trybów pracy JOGA albo wyrzucenie obsługi trybów do MidiController - tu tylko obsługa z gotową prędkością
    private void handlePan(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        int value = event.getValue();

        //Locked when in Auto Follow mode
        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            return;
        }
        //DEBUG:
//        System.out.println("===========CONTROL CORE ===============\n" +
//                "handle PAN " + controlId + " -> Received value: " + value);

        PTZCamera camera =  cameraManager.getCoreAssignedCamera(event.getPanel());

        if (camera == null) {return;}

        ModeState mode = panel.getStates().get(PTZ_MODE);
        int speed;

        //DEBUG:
        //System.out.println("MODE = " + mode);

        switch (mode) {

            case WAIT_NEUTRAL:
                break;

            case JOYSTICK:
                if (value == 0) {
                    camera.stopPanTilt();
                    //Debug;
                    //System.out.println("=> STOP REQUEST");
                    return;
                }
                //Debug
                //System.out.println("=> PAN speed = " + speed);
                camera.movePanTilt(value * 4, 0);
                ControlEvent eventJOY = new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        event.getAction(),
                        value);

                sendControlEvent(eventJOY);
                return;

            case ABSOLUTE:
                //DEBUG:
                //System.out.println("ABSOLUTE");

                double fullrange = camera.getParameter("panPosition").getMax() - camera.getParameter("panPosition").getMin();
                double deegressPerTick = fullrange / midiController.getConfig().getJogResolution();
                double delta = value * deegressPerTick;
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
                        panel.getId(),
                        event.getAction(),
                        value);

                sendControlEvent(eventABS);
                break;
        }
    }

    private void handleTilt(ControlEvent event) {

        //DEBUG
        //System.out.println("handleMidiTilt - Received value: " + value);

        ControlPanel panel = panels.get(event.getPanel());
        int value = event.getValue();

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

        if (!panel.isAcceptingPanTilt()) {
            return;
        }

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
        if (camera == null) {return;}

        //DEBUG:
        //System.out.println("MODE: " + panel.getStates().get(TILT));

        if (panel.getStates().get(PTZ_MODE) == ModeState.ABSOLUTE) {

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

        ControlEvent returnEvent = new ControlEvent(
                SYSTEM,
                panel.getId(),
                event.getAction(),
                value);

        sendControlEvent(returnEvent);
    }

    //TODO: zaimplementować zoom w trybie joystick
    private void handleZoom(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        int value = event.getValue();

        //Locked when in Auto Follow mode
        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            return;
        }

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());

        if (camera == null) {return;}

        FieldDefinition zoomField =camera.getController().getFieldDefinition("zoomPosition");

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

        ControlEvent returnEvent =
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        event.getAction(),
                        value
                );

        sendControlEvent(returnEvent);

        //    gui.handleControlEvent(returnEvent);
    }

    private void handleFocusMode(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        Control control = Control.valueOf(event.getAction());
        int value = event.getValue();

        //DEbug:
        System.out.println("Handle focus mode event ==> " + event.toString()
        + "\n control value:  " + control.toString());

        if (control != FOCUS_MODE || value == 0){
            return;
        }
        //DEBUG
        //System.out.println("==== CORE ======\nhandleFocusMode for panel " + panel.getStates().toString() + " \ncontrol: " + control + "  VAL = " + value);

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
        if (camera == null){
            return;
        }

        ParameterState param = camera.getParameter("focusPosition");

        System.out.println("Current focus mode: " + param.getCurrent());

        if (param == null){
            param = new ParameterState();
            param.setCurrentState("AUTO");
        }

        ControlEvent returnEvent = null;
        String newState = null;
        if (param.getCurrentState().equals("AUTO")){

            returnEvent = new ControlEvent(
                    SYSTEM,
                    panel.getId(),
                    "ONE_PUSH_FOCUS_INDICATOR",
                    2
            );

            sendControlEvent(returnEvent);

            returnEvent = new ControlEvent(
                    SYSTEM,
                    panel.getId(),
                    "FOCUS_MODE_INDICATOR",
                    0
            );
            newState = "MANUAL";
            //System.out.println("FOCUS SWITCHED TO AUTO");

        } else if (param.getCurrentState().equals("MANUAL")){
            returnEvent = new ControlEvent(
                    SYSTEM,
                    panel.getId(),
                    "ONE_PUSH_FOCUS_INDICATOR",
                    0
            );

            sendControlEvent(returnEvent);

            returnEvent = new ControlEvent(
                    SYSTEM,
                    panel.getId(),
                    "FOCUS_MODE_INDICATOR",
                    1
            );
            newState = "AUTO";
        }

        if (newState != null) {
            camera.getParameter("focusPosition").setCurrentState(newState);
            camera.setFocusMode(newState);
            panel.getStates().put(FOCUS_MODE, ModeState.valueOf(newState));
        }

        if (event != null) {
            sendControlEvent(returnEvent);
        }
    }

    private void handleCameraImageParameter(ControlEvent event) {

        //DEBUG
       /* System.out.println("=========== CORE ================\n" +
                "handleExposureParameter: " + event.getAction() + " VAL received: " + event.getValue());*/
        ControlPanel panel = panels.get(event.getPanel());
        int midiValue = event.getValue();

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());

        if (camera == null) {
            return;
        }

        String parameterName = null;

        switch (event.getAction()){
            case "EXPOSURE_COMPENSATION":
                if(panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO) {
                    parameterName = "exposureCompensation";
                }
                if(panel.getStates().get(EXPOSURE_MODE) == ModeState.MANUAL) {
                    parameterName = "exposureGain";
                }
                break;

            case "EXPOSURE_GAIN":
                if(panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO) {
                    return;
                }
                parameterName =  "exposureGain";
                break;

            case "EXPOSURE_IRIS":
                if(panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO) {
                    return;
                }
                parameterName =  "exposureIris";
                break;

            case "EXPOSURE_SHUTTER":
                if(panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO) {
                    return;
                }
                parameterName =  "exposureShutter";
                break;

            case "WHITE_BALANCE_R_GAIN":
                if(!camera.getParameter("whiteBalanceMode").getCurrentState().equalsIgnoreCase("manual")) {
                    return;
                }
                parameterName =  "whiteBalanceRGain";
                break;

            case "WHITE_BALANCE_B_GAIN":
                if(!camera.getParameter("whiteBalanceMode").getCurrentState().equalsIgnoreCase("manual")) {
                    return;
                }
                parameterName =  "whiteBalanceBGain";
                break;

            default:
                LOGGER.warning("Unsupported camera parameter");
                return;
        };

        //DEBUG
        //System.out.println("parameterInquiry string: " + parameterInquiry);


        ParameterState parameter = camera.getParameter(parameterName + "Position",true);

        if (parameter == null) {
            LOGGER.warning("No "+ event.getAction() + " parameter in camera " + camera.getCameraId());
            return;
        }

        FieldDefinition field = camera.getController().getFieldDefinition(parameterName+"Position");
        int targetValue = midiToMappedValue(midiValue,field);
        int currentValue = (int) parameter.getCurrent();

        //DEBUG
        //System.out.println("parameterInquiry received value: " + currentValue + "   target value: " + targetValue);

        if (targetValue == currentValue) {
            return;
        }

        // TODO: MIDI control clutching
        /*if (panel.getStates().get(Control.valueOf(guiEventName)) == ModeState.LOCK){

            int lastMidi = panel.getMidiCatch().get(Control.valueOf(guiEventName));
            int expectedMidi = mappedValueToMidi(currentValue, field);

            boolean crossed = (
                    (lastMidi < expectedMidi &&  midiValue >= expectedMidi) ||
                            (lastMidi > expectedMidi &&  midiValue <= expectedMidi)
                );

            //debug
            //System.out.println("KNOB CLUTCHED. EXPECTED: " + expectedMidi + "  RECEIVED: " + midiValue);
            if (!crossed){
                return;
            }
            panel.getStates().put(Control.valueOf(guiEventName), ModeState.ON);
        }*/


        int minLevel = (int) parameter.getMin();
        int maxLevel = (int) parameter.getMax();
        int delta = targetValue - currentValue;

        Map<String, Object> range = field.getRange();

        if (range != null) {

            int mappedMax = ((Number) range.get("max")).intValue();

            int step = 1;

            if (mappedMax > 1023) {
                step = 16;
            } else if (mappedMax > 511) {
                step = 8;
            } else if (mappedMax > 255) {
                step = 4;
            } else if (mappedMax > 127) {
                step = 2;
            }
            delta = (int) Math.round((double) delta / step);
        }

        //DEBUG
        /*System.out.println("Target value for " + parameterName + " = " + targetValue
        + "\nLIMITS: UP = " + maxLevel + " DOWN = " + minLevel + " DELTA = "  +delta);*/

        if (delta > 0) {
            for (int i = 0; i < delta; i++) {
                if ((currentValue + i) > maxLevel) {
                    break;
                }
                //DEBUG
                //System.out.println(parameterName + " ==> UP");
                camera.adjustExposureParameter(parameterName,1);
            }
        } else {
            for (int i = 0; i < Math.abs(delta); i++) {
                if ((currentValue - i) < minLevel) {
                    break;
                }
                //DEBUG
                //System.out.println(parameterName + " ==> DOWN");
                camera.adjustExposureParameter(parameterName,-1);
            }
        }

        parameter.setCurrent(targetValue);

        int normalizedMidi = mappedValueToMidi(targetValue, field);

        ControlEvent returnEvent = new ControlEvent(
                SYSTEM,
                panel.getId(),
                event.getAction(),
                normalizedMidi
        );

        sendControlEvent(returnEvent);

        // DEBUG:
        //System.out.println("PARAMETER CHANGE EVENT SEND\n" + event.toString());
    }

    /* ==============================================================================
                HANDLE CAMERA MODES (PTZ move mode, exposition mode, automations)
       ==============================================================================*/

    private void handlePTZMode(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        Control control = Control.valueOf(event.getAction());

        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            return;
        }

        //DEBUG
        System.out.println("PTZ Handler CONTROL: " + control);

        // ======= TILT MODE ==========
        if (control != PTZ_MODE) {
            return;
        }
        //DEBUG
        //System.out.println("Current ABS/JOY state: " + panel.getStates().get(TILT));

        switch (panel.getStates().get(control)) {
            case JOYSTICK:
                enterAbsoluteMode(event);
                break;

            case ABSOLUTE:
               /* ControlEvent stateRequest = new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                );*/
                //midiController.sendControlEvent(stateRequest);
                enterWaitNeutral(panel, control);
                break;

            case WAIT_NEUTRAL:
                break;
        }
    }

    private void enterAbsoluteMode(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        Control control = Control.valueOf(event.getAction());
        panel.getStates().put(control, ModeState.ABSOLUTE);
        panel.setAcceptingPanTilt(true);

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());

        if (camera == null) {
            return;
        }

        sendControlEvent(new ControlEvent(
                SYSTEM,
                panel.getId(),
                "ABSOLUTE_MODE_INDICATOR",
                1));
    }

    private void enterWaitNeutral(ControlPanel panel, Control control) {

        //DEBUG
        System.out.println("WAIT FOR NEUTRAL CONTROL: " + control);
        panel.getStates().put(PTZ_MODE, ModeState.WAIT_NEUTRAL);
        panel.getStates().put(TILT, ModeState.WAIT_NEUTRAL);
        //panel.getStates().put(ZOOM, ModeState.WAIT_NEUTRAL);
        panel.setAcceptingPanTilt(false);
        //panel.setAcceptingZoom(false);

        sendControlEvent (new ControlEvent(
                SYSTEM,
                panel.getId(),
                "ABSOLUTE_MODE_INDICATOR",
                2));


        System.out.println("WAITING FOR NEUTRAL");
    }

    private void enterJoystickMode(ControlPanel panel, Control control) {

        panel.getStates().put(
                control,
                ModeState.JOYSTICK
        );

        boolean ready = panel.getStates().get(TILT) == ModeState.JOYSTICK;
                        //&& panel.getStates().get(ZOOM) == ModeState.JOYSTICK;

        if (ready) {

            panel.getStates().put(
                    PTZ_MODE,
                    ModeState.JOYSTICK
            );

            panel.setAcceptingPanTilt(true);

            sendControlEvent(new ControlEvent(
                    SYSTEM,
                    panel.getId(),
                    "ABSOLUTE_MODE_INDICATOR",
                    0
            ));
        }
    }

    private void handleTally(ControlEvent event) {

        Control control = Control.valueOf(event.getAction());

        if (control != TALLY) {
            return;
        }

        ControlPanel panel = panels.get(event.getPanel());
        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());

        //DEBUG
        //System.out.println("=> TALLY HANDLER " + panel.getStates().get(TALLY));
        ModeState tallyStatus = null;

        if (panel.getStates().get(TALLY) == ModeState.OFF) {
            camera.recallPreset("tally_On");
            tallyStatus = ModeState.ON;
            sendControlEvent(new ControlEvent(SYSTEM,
                    panel.getId(),
                    "TALLY_INDICATOR",
                    1));
        }
        if (panel.getStates().get(TALLY) == ModeState.ON) {
            camera.recallPreset("tally_Off");
            tallyStatus = ModeState.OFF;
            sendControlEvent(new ControlEvent(SYSTEM,
                    panel.getId(),
                    "TALLY_INDICATOR",
                    0));
        }

        panel.getStates().put(TALLY, tallyStatus);

    }

    private void handleAutoFollow( ControlEvent event) {

        Control control = Control.valueOf(event.getAction());

        if (control != AUTO_FOLLOW) {
            return;
        }

        ControlPanel panel = panels.get(event.getPanel());
        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());

        //DEBUG
        //System.out.println("=> ATOFOLLOW HANDLER " + panel.getStates().get(TALLY));
        ModeState autoFollowStatus = null;

        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.OFF) {
            camera.recallPreset("smartShoot_On");
            autoFollowStatus = ModeState.ON;
            sendControlEvent(new ControlEvent (
                    SYSTEM,
                    panel.getId(),
                    "AUTO_FOLLOW_INDICATOR",
                    1));
        }

        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            camera.recallPreset("smartShoot_Off");
            autoFollowStatus = ModeState.OFF;
            sendControlEvent(new ControlEvent (
                    SYSTEM,
                    panel.getId(),
                    "AUTO_FOLLOW_INDICATOR",
                    0));
        }

        panel.getStates().put(AUTO_FOLLOW, autoFollowStatus);
        }

    private void handleExposureMode(ControlEvent event) {

        //DEBUG
        //System.out.println("Handle exposure mode ACTION:" + event.getAction());
        if (!event.getAction().equals("EXPOSURE_MODE")){
            return;
        }

        ControlPanel panel = panels.get(event.getPanel());
        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
        ModeState nextMode;

        ControlEvent returnEvent = null;
        Map <String, Object> params = new HashMap<>();

        switch (panel.getStates().get(EXPOSURE_MODE)
        ) {
            case MANUAL:
                nextMode = ModeState.AUTO;
                //params.put("exposureMode", "AUTO");
                //camera.executeViscaCommand("exposureMode", params);
                camera.setExposureMode("AUTO");
                //panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                //panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                /*midiController.sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/

                sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        1
                ));
                break;

            case AUTO:
                nextMode = ModeState.MANUAL;
                /*params.put("exposureMode", "MANUAL");
                camera.executeViscaCommand("exposureMode", params);*/
                camera.setExposureMode("MANUAL");

                //---- gain clutching
                //camera.getParameter("exposureGainPosition", true);
               /* midiController.sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/
                //panel.getStates().put(EXPOSURE_GAIN, ModeState.LOCK);

                //-- iris clutching
                //camera.getParameter("exposureIrisPosition", true);
               /* midiController.sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/
                //panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);

                //---- shutter clutching
                //camera.getParameter("exposureIrisPosition", true);
               /* midiController.sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/
                //panel.getStates().put(EXPOSURE_SHUTTER, ModeState.LOCK);

                //--- mode indicator event
                sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        0));
                break;

            default:
                nextMode = ModeState.AUTO;
                /*params.put("exposureMode", "AUTO");
                camera.executeViscaCommand("exposureMode", params);*/
                camera.setExposureMode("AUTO");
                sendControlEvent( new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        0));
        }

        panel.getStates().put(EXPOSURE_MODE, nextMode);

        //sendControlEvent(returnEvent);
    }

    private void handleWBMode(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
        int value = event.getValue();

        if (camera == null) {
            return;
        }

        FieldDefinition field = camera.getController().getFieldDefinition("whiteBalanceMode");

        int targetMode = midiToMappedValue(value, field);

        ParameterState parameter = camera.getParameter("whiteBalanceMode", false);
        int currentMode = (int) parameter.getCurrent();

        if (targetMode == currentMode) {
            return;
        }

        Map<String, Integer> modeMapping = field.getEncoding().getMapping();
        String targetModeName = modeMapping.entrySet()
                .stream()
                .filter (e -> e.getValue() == targetMode)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Uknown WB Mode: " + targetMode));



        Map<String,Object> params = new HashMap<>();

        ParameterState param = new ParameterState();
        param.setCurrentState("OFF");
        camera.getParameters().put("onePushWBal", param);
        ControlEvent returnEvent = null;

        if (targetModeName.equalsIgnoreCase("ONE_PUSH")){
            //debug
            //System.out.println ("OnePush WBAL activates");
            param = new ParameterState();
            param.setCurrentState("ACTIVE");
            camera.getParameters().put("onePushWBal", param);
            //DEBUG
           // System.out.println(camera.getParameters().get("onePushWBal").toString());

            returnEvent = new ControlEvent(
                    SYSTEM,
                    panel.getId(),
                    "WHITE_BALANCE_PUSH_INDICATOR",
                    2
            );
        } else {
            returnEvent = new ControlEvent(
                    SYSTEM,
                    panel.getId(),
                    "WHITE_BALANCE_PUSH_INDICATOR",
                    0
            );
        }


        sendControlEvent(returnEvent);

        params.put("whiteBalanceMode",targetModeName);

        //debug
        //System.out.println("PARAMS in handleWBMode: " + params.toString());

        //camera.executeViscaCommand("whiteBalanceMode",params);
        camera.setWhiteBalanceMode(targetModeName);

        camera.getParameter("whiteBalanceMode").setCurrent(targetMode);
        camera.getParameter("whiteBalanceMode").setCurrentState(targetModeName);


        sendControlEvent(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "WHITE_BALANCE_MODE",
                        value
                )
        );
    }

    private void handleWBPush(ControlEvent event) {

        if (!Objects.equals(event.getAction(), "WHITE_BALANCE_PUSH") && event.getValue() == 0){
            return;
        }
        ControlPanel panel = panels.get(event.getPanel());
        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
        if (camera == null){
            return;
        }

        //DEBUG
       // System.out.println("=> WB ONE PUSH value received = " + value + "\n"+
        //        "timestamp: " + System.currentTimeMillis() + "  THREAD: " + Thread.currentThread().getName());
        //System.out.println(camera.getParameters().keySet());

        ParameterState param = camera.getParameter("onePushWBal");

        //DEBUG
        //System.out.println("WBAL PARAM: " + param.toString());

        ControlEvent returnEvent = null;
        String newState = "LOCK";

        //DEBUG
        //System.out.println("CURRENT STATE = " + param.getCurrentState());

        switch (param.getCurrentState()){
            case "ACTIVE":
                camera.onePushWBal();
                returnEvent = new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "WHITE_BALANCE_PUSH_INDICATOR",
                        0
                );
                newState = "ON";

                //DEBUG
                //System.out.println("+>>  TRIGGERED");
                break;

            case "ON":
                returnEvent = new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "WHITE_BALANCE_PUSH_INDICATOR",
                        2
                );
                newState = "ACTIVE";
                //DEBUG
                //System.out.println("+>>  ACTIVATED");
                break;

            default:
                //System.out.println("ONE PUSH LOCKED");
                newState = "OFF";
                break;
        }

        sendControlEvent(returnEvent);
        camera.getParameter("onePushWBal").setCurrentState(newState);
    }

    private boolean isNeutral(int v) {
        return Math.abs(v - 64) <= 1;
    }

    private void handleMidiStatusResponse(ControlEvent event) {

        //System.out.println ("========= REQUEST MIDI VALUE ======= \n--> " + event.getControlID() + "  VAL = " + event.getValue());

        ControlPanel panel = panels.get(event.getPanel());
        if (panel == null) {
            return;
        }

        int expectedMidi = -1;

        switch (event.getAction()) {

            case "EXPOSURE_COMPENSATION":
                 if (ModeState.MANUAL.equals(panel.getStates().get("EXPOSURE_MODE"))) {
                    //DEBUG
                    System.out.print("GAIN SETTING => ");
                    panel.getMidiCatch().put(Control.EXPOSURE_GAIN, event.getValue());
                    if (expectedMidi == event.getValue()) {
                        panel.getStates().put(Control.EXPOSURE_GAIN, ModeState.ON);
                    } else {
                        //panel.getStates().put(Control.EXPOSURE_GAIN, ModeState.LOCK);
                    }
                } else {
                     //DEBUG
                     System.out.print("EXPOSURE COMP. SETTING => ");
                     panel.getMidiCatch().put(EXPOSURE_COMPENSATION, event.getValue());
                    if (expectedMidi == event.getValue()) {
                        panel.getStates().put(EXPOSURE_COMPENSATION, ModeState.ON);
                    } else {
                        //panel.getStates().put(EXPOSURE_COMPENSATION, ModeState.LOCK);
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
                    //panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                }

                break;

            case "EXPOSURE_SHUTTER":
                panel.getMidiCatch().put(EXPOSURE_SHUTTER, event.getValue());
                if (expectedMidi == event.getValue()) {
                    panel.getStates().put(EXPOSURE_SHUTTER, ModeState.ON);
                } else {
                    //panel.getStates().put(EXPOSURE_SHUTTER, ModeState.LOCK);
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
        /*System.out.println("===== MIDI CHATCHES ======= \n"
                + panel.getMidiCatch().toString());*/
    }

    private void handleFocus(ControlEvent event){
        //TODO: handling
        //DEBUG
        //System.out.println ("Focus parameter recived: " + event.getValue() + "\nFocus event recived: " + event.getAction());

        ControlPanel panel = panels.get(event.getPanel());

        //Locked ind AutoFollow Mode
        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON ) {
            return;
        }

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());

        if (camera == null) {return;}

        FieldDefinition focusField =camera.getController().getFieldDefinition("focusPosition");

        Map<String, Object> mapping =focusField.getMapping();
        Map<String, Object> mechanicalRange = (Map<String, Object>)mapping.get("mechanicalRange");

        int maxFocus = ((Number) mechanicalRange.get("max")).intValue();
        int minFocus = ((Number) mechanicalRange.get("min")).intValue();
        int currentFocus = (int)camera.getParameter("focusPosition").getCurrent();
        int focusPosition = 0;

        ControlEvent returnEvent = null;

        //Slider/knob handling

        if (event.getAction().equals("FOCUS")) {

            focusPosition = (event.getValue() * maxFocus) / 170;

            //DEBUG
           /* System.out.println("========= CONTROL CORE =========== \n SLIDER FOCUS handling" +
                "\nEVENT value = " + event.getValue() +
                "\nFOCUS position = " + focusPosition);*/

        }

        if (event.getAction().equals("FOCUS_UP_KEY") || event.getAction().equals("FOCUS_DOWN_KEY")) {

            int focusDelta = event.getAction().equals("FOCUS_UP_KEY")? 2: -2;
            focusPosition = currentFocus + focusDelta;
            if (focusPosition > maxFocus) {
                focusPosition = maxFocus;
            }
            if (focusPosition < minFocus) {
                focusPosition = minFocus;
            }
        }

        camera.getParameter("focusPosition").setCurrent(focusPosition);
        camera.moveFocusAbsolute(focusPosition);

        int newValue = mappedValueToMidi(focusPosition, focusField);

        sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "FOCUS",
                        newValue
                ));
    }

    private void handleFocusOnePush (ControlEvent event){

        //DEBUG
        //System.out.println("ONE PUSH FOCUS event: " + event.toString());
        if (event.getValue() == 1 && event.getAction().equals("ONE_PUSH_FOCUS_KEY")){
            ControlPanel panel = panels.get(event.getPanel());
            PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
            if (camera != null){
                camera.onePushAutofocus();
            }
        }

    }

    /* ==================================
          INITIALIZATION HELPERS
     ====================================*/

    private void initializeStatusIndicators() {

        for (ControlPanel panel : panels.values()) {

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "ABSOLUTE_MODE_INDICATOR",
                            panel.getStates().get(TILT) == ModeState.ABSOLUTE ? 1 : 0
                    )
            );

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "AUTO_FOLLOW_INDICATOR",
                            panel.getStates().get(AUTO_FOLLOW) == ModeState.OFF ? 0 : 1
                    )
            );

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "ONE_PUSH_FOCUS_INDICATOR",
                            0
                    )
            );

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "FOCUS_MODE_INDICATOR",
                            panel.getStates().get(FOCUS_MODE) == ModeState.AUTO ? 1 : 0
                    )
            );

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "EXPOSURE_MODE_INDICATOR",
                            panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO ? 1 : 0
                    )
            );

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "WHITE_BALANCE_PUSH_INDICATOR",
                            0
                    )
            );

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "TALLY_INDICATOR",
                            0
                    )
            );

            sendControlEvent(
                    new ControlEvent(
                            SYSTEM,
                            panel.getId(),
                            "PANEL_SELECT_INDICATOR",
                            panel.getId() == A ? 1 : 0
                    )
            );
        }
    }

    private void updateCameraIndicators() {

        if(mode != RunMode.FULL){
            return;
        }

        for (ControlPanel panel : panels.values()) {

            PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());

            if (camera == null) {
                continue;
            }

            int selectedCamera = switch (camera.getCameraId()) {
                case "CAM_A" -> 1;
                case "CAM_B" -> 2;
                case "CAM_C" -> 3;
                case "CAM_D" -> 4;
                default -> 0;
            };

            for (int i = 1; i <= 4; i++) {

                sendControlEvent(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "CAM_SELECT_" + i + "_INDICATOR",
                        i == selectedCamera ? 1 : 0));
            }
        }
    }

    /* ==========================================
             ZARZĄDANIE I STEROWANIE KAMERAMI
       ==========================================*/

    public void assignToMidiPanel(ControlPanelId midiPanel, ControlPanelId corePanel) {

        midiManager.assignMidiPanel(midiPanel, corePanel);

        //DEBUG do testów
        System.out.println("MIDI panel " + midiPanel + " ->  CORE panel " + corePanel);
    }

    private void handleCameraSelect(ControlEvent event) {

        if (event.getValue() == 0) {
            return;
        }

        // panel CORE wskazany przez przycisk CAMERA_SELECT_X
        ControlPanelId targetCorePanel = ControlPanelId.valueOf(event.getAction().substring("CAMERA_SELECT_".length()));

        // panel MIDI, na którym naciśnięto przycisk
        ControlPanelId midiPanel = event.getPanel();

        ControlPanelId currentMidiBinding = midiManager.getMidiBinding(targetCorePanel);

        // już przypisany
        if (currentMidiBinding == midiPanel) {
            System.out.println("CORE PANEL "+ targetCorePanel+ " ALREADY ASSIGNED TO MIDI PANEL "+ midiPanel);
            return;
        }

        System.out.println("ASSIGN MIDI PANEL " + midiPanel + " -> CORE PANEL " + targetCorePanel);

        midiManager.assignMidiPanel(midiPanel,targetCorePanel);

        updateCameraIndicators();
        gui.refreshPanelBindings();
    }



    /* ============================
             METODY POMOCNICZE
       ============================
*/
    //------ calculators

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

    private int midiToMappedValue(int midiValue, FieldDefinition field) {

        Map<String, Object> mapping = field.getMapping();

        if (mapping == null) {
            throw new IllegalArgumentException("Field has no mapping definition");
        }

        String mappingType = (String) mapping.get("type");

        switch (mappingType.toLowerCase()) {

            case "lookup": {
                int mappedMin = ((Number) mapping.get("mappedMin")).intValue();
                int mappedMax = ((Number) mapping.get("mappedMax")).intValue();

                int steps = mappedMax - mappedMin + 1;
                int bucket = (int) Math.floor(midiValue * steps / 128.0);

                if (bucket >= steps) {
                    bucket = steps - 1;
                }
                return mappedMin + bucket;
            }

            case "enum": {
                Map<String, Object> enumMapping = (Map<String, Object>) field.getMapping().get("values");

                if (enumMapping == null || enumMapping.isEmpty()) {
                    throw new IllegalStateException(
                            "Enum field has no encoding mapping definition");
                }

                int steps = enumMapping.size();

                int bucket = (int) Math.floor(midiValue * steps / 128.0);

                if (bucket >= steps) {
                    bucket = steps - 1;
                }

                return bucket;
            }

            default: {
                Map<String, Object> range = field.getRange();

                if (range == null) {
                    throw new IllegalStateException(
                            "Unsupported mapping type: " + mappingType);
                }

                int mappedMin = ((Number) range.get("min")).intValue();
                int mappedMax = ((Number) range.get("max")).intValue();

                Object stepObj = range.get("step");

                // nowy tryb - mapowanie na kroki logiczne
                if (stepObj != null) {

                    int step = ((Number) stepObj).intValue();

                    int totalSteps =
                            ((mappedMax - mappedMin) / step) + 1;

                    int midiPerStep =
                            Math.max(1, 128 / totalSteps);

                    int mappedStep = midiValue / midiPerStep;

                    if (mappedStep >= totalSteps) {
                        mappedStep = totalSteps - 1;
                    }

                    return mappedStep;
                }

                // stary mechanizm
                int scaleFactor;

                if (mappedMax > 1023) {
                    scaleFactor = 16;
                } else if (mappedMax > 511) {
                    scaleFactor = 8;
                } else if (mappedMax > 255) {
                    scaleFactor = 4;
                } else if (mappedMax > 127) {
                    scaleFactor = 2;
                } else {
                    scaleFactor = 1;
                }

                int mappedValue = mappedMin + (midiValue * scaleFactor);

                if (mappedValue > mappedMax) {
                    mappedValue = mappedMax;
                }

                return mappedValue;
            }
        }
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

    //--------   GETTERS, SETTER, SUPPORTS

    public void setGUI (MainGUI gui){
        this.gui = gui;
    }

    public void syncInterface(){
        if (this.gui != null) {
            initializeStatusIndicators();
        }
        if (mode == RunMode.FULL){
            updateCameraIndicators();
        }
    }

    private void sendControlEvent(ControlEvent event) {

        ControlPanelId midiPanel =
                midiManager.getMidiBinding(event.getPanel());

        if (midiPanel != null && mode == RunMode.FULL) {

            ControlEvent midiEvent = new ControlEvent(
                    SYSTEM,
                    midiPanel,
                    event.getAction(),
                    event.getValue()
            );

            // DEBUG
            //System.out.println("CORE EVENT ROUTING ==> MIDI: " + midiPanel + " ---> GUI PANEL: " + event.getPanel());

            midiController.sendControlEvent(midiEvent);
        }
        gui.handleControlEvent(event);
    }

    private ControlPanel createPanel(ControlPanelId panelId) {

        ControlPanel panel = new ControlPanel(panelId);

        panel.getStates().put(PTZ_MODE,ModeState.JOYSTICK);
        panel.getStates().put(FOCUS, ModeState.AUTO);
        panel.getStates().put(TALLY,ModeState.OFF);
        panel.getStates().put(AUTO_FOLLOW,ModeState.OFF);
        panel.getStates().put(EXPOSURE_MODE,ModeState.AUTO);
        panel.getStates().put(FOCUS_MODE,ModeState.AUTO);
        panel.getStates().put(WHITE_BALANCE_MODE,ModeState.AUTO);
        panel.setAcceptingPanTilt(true);
        return panel;
    }

    private ControlPanelId resolveControlPanel(ControlEvent event) {

        switch (event.getEventSource()) {

            case MIDI: {
                String guiPanel = cameraManager.CorePanelBinding(event.getPanel());
                return guiPanel == null
                        ? null
                        : ControlPanelId.valueOf(guiPanel);
            }

            case GUI:
            case SYSTEM:
                return event.getPanel();

            default:
                return null;
        }
    }
}