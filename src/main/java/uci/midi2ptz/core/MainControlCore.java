package uci.midi2ptz.core;

import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.gui.MainGUI;
import uci.midi2ptz.midi.MidiController;
import uci.midi2ptz.midi.MidiControllerManager;
import uci.midi2ptz.ptz.PTZCamera;
import uci.midi2ptz.ptz.PTZCameraManager;
import uci.midi2ptz.ptz.ParameterState;
import uci.midi2ptz.visca.FieldDefinition;

import java.util.*;
import java.util.logging.Logger;

import static uci.midi2ptz.core.MainControlCore.Control.*;
import static uci.midi2ptz.core.MainControlCore.ControlPanelId.*;
import static uci.midi2ptz.core.ControlEvent.EventSource.*;

/**
 * Central application control engine.
 *
 * <p>MainControlCore is the primary business-logic component of the application. It receives control events from the
 * application event bus, translates them into PTZ camera operations and publishes resulting state changes back to
 * external listeners.</p>
 *
 * <p>The control core coordinates interactions between:</p> *
 * <ul>
 *     <li>MIDI controllers,</li>
 *     <li>PTZ camera devices,</li>
 *     <li>graphical user interfaces,</li>
 *     <li>camera automation modules,</li>
 *     <li>runtime configuration services.</li>
 * </ul>
 *
 * <p>The class subscribes to user-generated events originating from GUI and MIDI components and acts as the authoritative
 * source of application state.</p>
 * <h2>Responsibilities</h2> *
 * <ul>
 *     <li>PTZ motion control</li>
 *     <li>Focus management</li>
 *     <li>Exposure management</li>
 *     <li>White balance control</li>
 *     <li>Auto-follow control</li>
 *     <li>Tally management</li>
 *     <li>Camera and panel assignment management</li>
 *     <li>State synchronization</li>
 *     <li>Runtime state synchronization</li>
 *     <li>Control state publication through the event bus</li>
 * </ul>
 *
 * <h2>Event Flow</h2>
 *
 * <pre>
 * MIDI Controller                   GUI Component
 *       |                                |
 *       +--------------------------------+
 *                       |
 *                       v
 *            Event Bus (GUI or MIDI events)
 *                       |
 *                       v
 *                 MainControlCore
 *                       |
 *                       v
 *             Event Bus (SYSTEM events)
 *                       |
 *        +--------------+----------------+
 *        v              v                v
 *   MIDI Feedback    PTZ Cameras    GUI Component
 * </pre>
 *
 * <p>MainControlCore contains no direct GUI logic and no hardware-specific MIDI logic. All communication occurs
 * through {@link ControlEvent} instances exchanged via the {@link ControlEventBus}.</p>
 */
public class MainControlCore implements ControlEventListener{

    /**
     * Logical control identifiers used by the control engine independently of transport, GUI or MIDI implementation.
     */
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

    /**
     * Generic runtime states used by various  controls.
     * <p>Not every value applies to every control type.</p>
     */
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

    /**
     * Identifies a logical control panel managed by the control core.
     *
     * <p>Each panel is typically associated with one PTZ camera and may be dynamically assigned to a physical MIDI controller panel.</p>
     */
    public enum ControlPanelId {
        A,
        B,
        C,
        D,
    }

    /**
     * Defines the application runtime mode.
     */
    public enum RunMode {
        FULL,           /** Full operation with cameras and hardware controller enabled.*/
        TEST,           /** Validation-only mode used during configuration validation.*/
        NO_HARDWARE     /** Runtime mode without physical cameras or MIDI/USB hardware.*/
    }

    // SYSTEM STATE VARIABLES
    private final Map<ControlPanelId, ControlPanel> panels = new EnumMap<>(ControlPanelId.class);
    private final RunMode mode;
    private final AppConfigManager configManager;


    // COMMUNICATION With MIDI, GUI & CAMERAS
    private final PTZCameraManager cameraManager;
    private final MidiControllerManager midiManager;
    private final MidiController midiController;
    private MainGUI gui;
    //private Thread controlThread;
    //private volatile boolean running;
    private final ControlEventBus inputBus;

    // RUNTIME BEHAVIOUR
    /**
     * Indicates that the controller is waiting for a neutral control position before re-entering joystick mode.
     */
    private boolean waitingForStateSync = false;

    //LOGGER
    public static final Logger LOGGER = Logger.getLogger(MainControlCore.class.getName());

    /**
     * Creates a new control engine instance.
     *
     * @param configManager configuration manager
     * @param cameraManager camera management subsystem
     * @param midiManager MIDI controller manager
     * @param inputBus event bus used for receiving control events
     * @param mode runtime mode
     */
    public MainControlCore(AppConfigManager configManager, PTZCameraManager cameraManager, MidiControllerManager midiManager,
                           ControlEventBus inputBus, RunMode mode) {

        this.configManager = configManager;
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

        inputBus.register(this);
    }

    /**
     * Initializes runtime subsystems and default camera states.     *
     * <p>No event-processing thread is created. Events are received  synchronously through the {@link ControlEventBus} listener interface.</p>
     */
    public void start() {

        if (configManager.getConfig().getSystem().getRunMode() == RunMode.FULL
                && midiController != null) {
            midiController.start();
        }

        //Initial states configuration

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
    }

     /*==========================================
               HANDLE MOVE AND PARAMS CHANGE
       ==========================================*/

    /**
     * Receives and processes control events from the application event bus.
     *
     * <p>The control core acts as the central application logic processor. GUI actions and MIDI controller input are received
     * through the event bus and translated into PTZ camera commands, mode transitions and state synchronization events.</p>
     *
     * <p>Events originating from {@link ControlEvent.EventSource#SYSTEM} are ignored because they represent notifications generated by the control core
     * itself and are intended for external listeners such as the GUI or MIDI controller.</p>
     *
     * @param event event received from the event bus
     */
    @Override
    public void onControlEvent(ControlEvent event) {

        if (event == null) {
            return;
        }

        if (event.getEventSource() == SYSTEM){
            return;
        }

        //REBINDING MIDI
        if (event.getAction().startsWith("CAMERA_SELECT_")){
            handleCameraSelect(event);
            return;
        }

        ControlPanel targetPanel = panels.get(resolveControlPanel(event));

        if (targetPanel == null) {
            return;
        }

        ControlEvent coreEvent = new ControlEvent(
                SYSTEM,
                targetPanel.getId(),
                event.getAction(),
                event.getValue()
        );

        PTZCamera camera = cameraManager.getCoreAssignedCamera(targetPanel.getId());

        //ONLINE?OFFLINE STATUS UPDATE EVENT
        if (event.getAction().equalsIgnoreCase("UPDATE_CAMERA_STATUS")){
            inputBus.publish(event);
            return;
        }

        if (!camera.isOnline()){
            if (coreEvent.getAction().equalsIgnoreCase("POWER_STATUS_REQUEST")){
                camera.powerStatusRequest();
            }else{
                return;
            }
        }

        int value = coreEvent.getValue();
        String action = coreEvent.getAction();

        /* Waiting for neutral on entering in Joystick mode */
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
            case "EXPOSURE_IRIS":
            case "EXPOSURE_SHUTTER":
            case "WHITE_BALANCE_R_GAIN":
            case "WHITE_BALANCE_B_GAIN":
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

            case "CAMERA_SELECT_A":
            case "CAMERA_SELECT_B":
            case "CAMERA_SELECT_C":
            case "CAMERA_SELECT_D":
                handleCameraSelect(coreEvent);
                break;

            case "STATUS_REQUEST":

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

        PTZCamera camera =  cameraManager.getCoreAssignedCamera(event.getPanel());

        if (camera == null) {return;}

        ModeState mode = panel.getStates().get(PTZ_MODE);
        int speed;

        switch (mode) {

            case WAIT_NEUTRAL:
                break;

            case JOYSTICK:
                if (value == 0) {
                    camera.stopPanTilt();
                    return;
                }
                camera.movePanTilt(value * 4, 0);
                ControlEvent eventJOY = new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        event.getAction(),
                        value);

                inputBus.publish(eventJOY);
                return;

            case ABSOLUTE:
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

                inputBus.publish(eventABS);
                break;
        }
    }

    private void handleTilt(ControlEvent event) {

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

        if (panel.getStates().get(PTZ_MODE) == ModeState.ABSOLUTE) {

            double oldTilt = camera.getParameter("tiltPosition").getCurrent();
            double newTilt = midiToDegrees(camera, "tiltPosition", value);

            //TODO: dopracować synchro szybkości do ruchu hebla
            //double delta = Math.abs(newTilt - oldTilt);
            //int speed = Math.max(1,Math.min(15,(int)Math.round(delta)));
            int speed = 8;

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

        inputBus.publish(returnEvent);
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

        camera.getParameter("zoomPosition").setCurrent(zoomPosition);
        camera.moveZoomAbsolute(zoomPosition);

        ControlEvent returnEvent =
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        event.getAction(),
                        value
                );

        inputBus.publish(returnEvent);

        //    gui.handleControlEvent(returnEvent);
    }

    private void handleFocusMode(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        Control control = Control.valueOf(event.getAction());
        int value = event.getValue();

        if (control != FOCUS_MODE || value == 0){
            return;
        }

        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
        if (camera == null){
            return;
        }

        ParameterState param = camera.getParameter("focusPosition");

        //System.out.println("Previous focus mode: " + param.getCurrentState());

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

            inputBus.publish(returnEvent);

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

            inputBus.publish(returnEvent);

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
            inputBus.publish(returnEvent);
        }
    }

    private void handleCameraImageParameter(ControlEvent event) {

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
        }

        ParameterState parameter = camera.getParameter(parameterName + "Position",true);

        if (parameter == null) {
            LOGGER.warning("No "+ event.getAction() + " parameter in camera " + camera.getCameraId());
            return;
        }

        FieldDefinition field = camera.getController().getFieldDefinition(parameterName+"Position");
        int targetValue = midiToMappedValue(midiValue,field);
        int currentValue = (int) parameter.getCurrent();

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

        ControlEvent returnEvent = new ControlEvent(
                SYSTEM,
                panel.getId(),
                event.getAction(),
                normalizedMidi
        );

        inputBus.publish(returnEvent);
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

        // ======= TILT MODE ==========
        if (control != PTZ_MODE) {
            return;
        }
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
                //midiController.inputBus.publish(stateRequest);
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

        inputBus.publish(new ControlEvent(
                SYSTEM,
                panel.getId(),
                "ABSOLUTE_MODE_INDICATOR",
                1));
    }

    private void enterWaitNeutral(ControlPanel panel, Control control) {

        panel.getStates().put(PTZ_MODE, ModeState.WAIT_NEUTRAL);
        panel.getStates().put(TILT, ModeState.WAIT_NEUTRAL);
        //panel.getStates().put(ZOOM, ModeState.WAIT_NEUTRAL);
        panel.setAcceptingPanTilt(false);
        //panel.setAcceptingZoom(false);

        inputBus.publish (new ControlEvent(
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

            inputBus.publish(new ControlEvent(
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

        ModeState tallyStatus = null;

        if (panel.getStates().get(TALLY) == ModeState.OFF) {
            camera.tally(1);
            tallyStatus = ModeState.ON;
            inputBus.publish(new ControlEvent(SYSTEM,
                    panel.getId(),
                    "TALLY_INDICATOR",
                    1));
        }
        if (panel.getStates().get(TALLY) == ModeState.ON) {
            camera.tally(0);
            tallyStatus = ModeState.OFF;
            inputBus.publish(new ControlEvent(SYSTEM,
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

        ModeState autoFollowStatus = null;

        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.OFF) {
            camera.autoFollow(1);
            autoFollowStatus = ModeState.ON;
            inputBus.publish(new ControlEvent (
                    SYSTEM,
                    panel.getId(),
                    "AUTO_FOLLOW_INDICATOR",
                    1));
        }

        if (panel.getStates().get(AUTO_FOLLOW) == ModeState.ON) {
            camera.autoFollow(0);
            autoFollowStatus = ModeState.OFF;
            inputBus.publish(new ControlEvent (
                    SYSTEM,
                    panel.getId(),
                    "AUTO_FOLLOW_INDICATOR",
                    0));
        }

        panel.getStates().put(AUTO_FOLLOW, autoFollowStatus);
        }

    private void handleExposureMode(ControlEvent event) {

        if (!event.getAction().equals("EXPOSURE_MODE")){
            return;
        }

        ControlPanel panel = panels.get(event.getPanel());
        PTZCamera camera = cameraManager.getCoreAssignedCamera(panel.getId());
        ModeState nextMode;

        ControlEvent returnEvent = null;

        switch (panel.getStates().get(EXPOSURE_MODE)
        ) {
            case MANUAL:
                nextMode = ModeState.AUTO;
                //params.put("exposureMode", "AUTO");
                //camera.executeViscaCommand("exposureMode", params);
                camera.setExposureMode("AUTO");
                //panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                //panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);
                /*midiController.inputBus.publish(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/

                inputBus.publish(new ControlEvent(
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
               /* midiController.inputBus.publish(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/
                //panel.getStates().put(EXPOSURE_GAIN, ModeState.LOCK);

                //-- iris clutching
                //camera.getParameter("exposureIrisPosition", true);
               /* midiController.inputBus.publish(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/
                //panel.getStates().put(EXPOSURE_IRIS, ModeState.LOCK);

                //---- shutter clutching
                //camera.getParameter("exposureIrisPosition", true);
               /* midiController.inputBus.publish(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "STATUS_REQUEST",
                        0
                ));*/
                //panel.getStates().put(EXPOSURE_SHUTTER, ModeState.LOCK);

                //--- mode indicator event
                inputBus.publish(new ControlEvent(
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
                inputBus.publish( new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        0));
        }

        panel.getStates().put(EXPOSURE_MODE, nextMode);

        //inputBus.publish(returnEvent);
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
        ControlEvent returnEvent;

        if (targetModeName.equalsIgnoreCase("ONE_PUSH")){
            param = new ParameterState();
            param.setCurrentState("ACTIVE");
            camera.getParameters().put("onePushWBal", param);

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


        inputBus.publish(returnEvent);

        params.put("whiteBalanceMode",targetModeName);
        camera.setWhiteBalanceMode(targetModeName);

        camera.getParameter("whiteBalanceMode").setCurrent(targetMode);
        camera.getParameter("whiteBalanceMode").setCurrentState(targetModeName);

        inputBus.publish(
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

        ParameterState param = camera.getParameter("onePushWBal");

        ControlEvent returnEvent = null;
        String newState = "LOCK";

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

                break;

            case "ON":
                returnEvent = new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "WHITE_BALANCE_PUSH_INDICATOR",
                        2
                );
                newState = "ACTIVE";
                break;

            default:
                newState = "OFF";
                break;
        }

        inputBus.publish(returnEvent);
        camera.getParameter("onePushWBal").setCurrentState(newState);
    }

    private boolean isNeutral(int v) {
        return Math.abs(v - 64) <= 1;
    }

    private void handleMidiStatusResponse(ControlEvent event) {

        ControlPanel panel = panels.get(event.getPanel());
        if (panel == null) {
            return;
        }

        int expectedMidi = -1;

        switch (event.getAction()) {

            case "EXPOSURE_COMPENSATION":
                 if (ModeState.MANUAL.equals(panel.getStates().get("EXPOSURE_MODE"))) {
                    panel.getMidiCatch().put(Control.EXPOSURE_GAIN, event.getValue());
                    if (expectedMidi == event.getValue()) {
                        panel.getStates().put(Control.EXPOSURE_GAIN, ModeState.ON);
                    } else {
                        //panel.getStates().put(Control.EXPOSURE_GAIN, ModeState.LOCK);
                    }
                } else {
                     panel.getMidiCatch().put(EXPOSURE_COMPENSATION, event.getValue());
                    if (expectedMidi == event.getValue()) {
                        panel.getStates().put(EXPOSURE_COMPENSATION, ModeState.ON);
                    } else {
                        //panel.getStates().put(EXPOSURE_COMPENSATION, ModeState.LOCK);
                    }
                }
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
                    enterJoystickMode(panel, TILT);
                } else{
                    enterWaitNeutral(panel, TILT);
                }
                break;
        }
    }

    private void handleFocus(ControlEvent event){

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

        inputBus.publish(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "FOCUS",
                        newValue
                ));
    }

    private void handleFocusOnePush (ControlEvent event){

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

    /**
     * Publishes the complete indicator state for all control panels.
     *
     * <p>Used during application startup, reconfiguration and panel reassignment.</p>
     */
    private void initializeStatusIndicators() {

        for (ControlPanel panel : panels.values()) {
            initializeStatusIndicators(panel);

        }
        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        A,
                        "PANEL_SELECT_INDICATOR",
                        1
                )
        );
        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        B,
                        "PANEL_SELECT_INDICATOR",
                        0
                )
        );
    }

    private void initializeStatusIndicators(ControlPanel panel) {

        //debug
        System.out.println("RunMode: " + configManager.getConfig().getSystem().getRunMode());



        if (midiManager.getMidiBinding(panel.getId()) == null ){
            if (configManager.getConfig().getSystem().getRunMode() != RunMode.NO_HARDWARE ) {
                midiManager.getMidiController().clearPanel(panel.getId());
            }
            updateCameraIndicators();
        }

        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "ABSOLUTE_MODE_INDICATOR",
                        panel.getStates().get(TILT) == ModeState.ABSOLUTE ? 1 : 0
                )
        );

        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "AUTO_FOLLOW_INDICATOR",
                        panel.getStates().get(AUTO_FOLLOW) == ModeState.OFF ? 0 : 1
                )
        );

        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "ONE_PUSH_FOCUS_INDICATOR",
                        0
                )
        );

        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "FOCUS_MODE_INDICATOR",
                        panel.getStates().get(FOCUS_MODE) == ModeState.AUTO ? 1 : 0
                )
        );

        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "EXPOSURE_MODE_INDICATOR",
                        panel.getStates().get(EXPOSURE_MODE) == ModeState.AUTO ? 1 : 0
                )
        );

        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "WHITE_BALANCE_PUSH_INDICATOR",
                        0
                )
        );

        inputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "TALLY_INDICATOR",
                        0
                )
        );
        updateCameraIndicators();
    }

    private void updateCameraIndicators() {

        if(configManager.getConfig().getSystem().getRunMode() != RunMode.FULL){
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
                inputBus.publish(new ControlEvent(
                        SYSTEM,
                        panel.getId(),
                        "CAM_SELECT_" + i + "_INDICATOR",
                        i == selectedCamera ? 1 : 0));
            }
        }
    }

    /* ==========================================
                    CAMERA MANAGEMENT
       ==========================================*/

    /**
     * Rebinds a physical MIDI panel to a different logical camera panel.
     *
     * <p>The mapping is updated in runtime, synchronized with the GUI and persisted in application configuration.</p>
     */
    private void handleCameraSelect(ControlEvent event) {

        if (event.getValue() == 0) {
            return;
        }

        ControlPanelId targetCorePanel = ControlPanelId.valueOf(event.getAction().substring("CAMERA_SELECT_".length()));

        ControlPanelId midiPanel = event.getPanel();

        ControlPanelId currentMidiBinding = midiManager.getMidiBinding(targetCorePanel);

        if (currentMidiBinding == midiPanel) {
            return;
        }
        midiManager.assignMidiPanel(midiPanel,targetCorePanel);

        configManager.updateMidiBinding(panels.get(targetCorePanel), midiPanel);

        updateCameraIndicators();
        initializeStatusIndicators(panels.get(targetCorePanel));
        gui.refreshPanelBindings();
    }


    /* ============================
             HELPERS
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
            return (int) Math.round(shiftedValue * 127.0 / shiftedRange);
        }
        return (int) Math.round(mappedValue * 127.0 / mappedMax);
    }

    //--------   GETTERS, SETTER, SUPPORTS

    /**
     * Attaches the graphical user interface to the control core.
     *
     * @param gui GUI instance used for state synchronization and visual updates
     */
    public void setGUI (MainGUI gui){
        this.gui = gui;
    }

    /**
     * Publishes the current runtime state to external listeners.
     *
     * <p>The generated SYSTEM events allow GUI and MIDI controller implementations to rebuild their visual state.</p>
     */
    public void syncInterface(){

        if (this.gui != null) {
            initializeStatusIndicators();
        }
        if (configManager.getConfig().getSystem().getRunMode() == RunMode.FULL){
            updateCameraIndicators();
        }
    }

    /**
     * Rebuilds the internal control panel registry.
     *
     * <p>The method removes all existing panel instances and recreates them based on the current camera-to-panel assignments.</p>
     *
     * <p>Typically invoked after configuration changes affecting camera mappings.</p>
     */
    public void reconfigurePanels() {

        panels.clear();

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
     * Creates and initializes a new logical control panel.
     *
     * <p>Default operational states are assigned for all supported camera control functions.</p>
     *
     * @param panelId panel identifier
     * @return initialized control panel instance
     */
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

    /**
     * Resolves an incoming event to a logical core control panel.
     *
     * <p>GUI events already reference logical panel identifiers.</p>
     * <p>MIDI events are translated from physical controller panels to logical Core panels using mappings maintained by {@link MidiControllerManager}.</p>
     */
    private ControlPanelId resolveControlPanel(ControlEvent event) {

        switch (event.getEventSource()) {

            case MIDI:
                return midiManager.getCoreBinding(
                        event.getPanel()
                );

            case GUI:
            case SYSTEM:
                return event.getPanel();

            default:
                return null;
        }
    }

}