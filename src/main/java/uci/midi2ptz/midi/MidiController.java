package uci.midi2ptz.midi;

import lombok.Getter;
import lombok.SneakyThrows;
import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.core.MainControlCore;
import uci.midi2ptz.core.ControlEvent;
import uci.midi2ptz.core.ControlEventBus;
import uci.midi2ptz.core.ControlEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static uci.midi2ptz.core.ControlEvent.EventSource.MIDI;
import static uci.midi2ptz.core.ControlEvent.EventSource.SYSTEM;

/**
 * Central MIDI controller integration layer.
 *
 * <p>This class coordinates communication between physical MIDI hardware and the application control subsystem.</p>
 *
 * <p>It translates incoming MIDI messages into  {@link ControlEvent} instances and converts application events
 * into hardware feedback such as LEDs, indicators and controller state updates.</p> *
 * <h2>Main Responsibilities</h2> *
 * <ul>
 *     <li>MIDI input processing</li>
 *     <li>MIDI output and LED feedback</li>
 *     <li>Control-to-action translation</li>
 *     <li>Jog wheel processing</li>
 *     <li>Button debouncing</li>
 *     <li>Runtime panel reassignment</li>
 *     <li>Status synchronization</li>
 * </ul>
 *
 * <p>The controller serves as the bridge between the physical MIDI device and the application event bus.</p>
 */
@Getter
public class MidiController implements ControlEventListener {

    private static final Logger LOGGER = Logger.getLogger(MidiController.class.getName());
    private MidiInputEndpoint midiInputController;
    private MidiOutputEndpoint midiOutputController;
    private final MidiConfig config;
    private final ControlEventBus outputBus;
    private final MainControlCore.RunMode mode;
    private final AppConfig appConfig;

    private volatile boolean running = false;
    /**
     * Controls temporary event-capture mode used while requesting controller state information.
     */
    private volatile boolean captureMode = false;

    /**
     * Remembers the currently selected camera assignment panel used for camera-selection LEDs.
     */
    private MainControlCore.ControlPanelId camLedAssignment;
    private Thread controllerThread;
    private Thread jogWatchdogThread;

    private final BlockingQueue<ControlEvent> controlQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MidiEvent> statusQueue = new LinkedBlockingQueue<>();
    private final Map<String, Integer> pendingCCValues = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Reverse lookup table used for translating ControlEvents into MIDI control identifiers.
     */
    private final Map<String, String> outputBindings = new HashMap<>();

    //Jog params
    private volatile long lastJogEventTime = 0;
    private volatile int lastJogValue = 64;
    private volatile ControlEvent lastJogEvent;

    /**
     * Watchdog timeout used to generate implicit jog-stop events when no further movement data is received.
     */
    private static final long JOG_TIMEOUT_MS = 50;
    private final Set<Integer> jogCCNumbers = new HashSet<>();

    // API
    private final MidiControllerManager controllerManager;

    /**
     * Creates a MIDI controller instance.
     *
     * @param midiConfig MIDI controller configuration
     * @param outputBus event bus used for publishing control events
     * @param mode application runtime mode
     * @param controllerManager MIDI controller manager
     */
    @SneakyThrows
    public MidiController(AppConfig appConfig, MidiConfig midiConfig, ControlEventBus outputBus,
                          MainControlCore.RunMode mode, MidiControllerManager controllerManager) {

        this.mode = mode;
        this.config = midiConfig;
        this.outputBus = outputBus;
        this.controllerManager = controllerManager;
        this.camLedAssignment = MainControlCore.ControlPanelId.A;
        this.appConfig = appConfig;

        //Reverse binding index
        for (Map.Entry<String, MidiBindingDefinition> entry : midiConfig.getBindings().entrySet()) {

            MidiBindingDefinition binding = entry.getValue();
            outputBindings.put(binding.getPanel() + ":" + binding.getAction(), entry.getKey());
        }
        this.midiInputController = null;
        this.midiOutputController = null;

        try {
            this.midiInputController = MidiEndpointFactory.createInputEndpoint(appConfig);
            this.midiOutputController = MidiEndpointFactory.createOutputEndpoint(appConfig);
            midiInputController.start();
            midiOutputController.start();
        }catch (Exception e){
            if(midiInputController != null){
                midiInputController.close();
                appConfig.getSystem().setRunMode(MainControlCore.RunMode.NO_HARDWARE);
            }
            if(midiOutputController != null){
                midiOutputController.close();
                appConfig.getSystem().setRunMode(MainControlCore.RunMode.NO_HARDWARE);
            }
        }

        if (mode.equals(MainControlCore.RunMode.FULL)){
            start();
        }

        outputBus.register(this);
    }

    /**
     * Starts MIDI processing services.
     *
     * <p>The method initializes background worker threads responsible for handling incoming MIDI events and jog wheel timeout monitoring.</p>
     *
     * <p>After startup, incoming hardware events are translated into {@link ControlEvent} instances and forwarded to the application event bus.</p>
     */
    public synchronized void start() {

        if (mode == MainControlCore.RunMode.NO_HARDWARE) {
            return;
        }

        if (running) {
            return;
        }

        running = true;

        /* MIDI In initialize*/
        for (MidiControlDefinition control : config.getControls().values()) {

            if (!"jog".equalsIgnoreCase(control.getType())) {
                continue;
            }
            jogCCNumbers.add(control.getChannel()
            );
        }

        controllerThread = new Thread(() -> {
                    while (running) {
                        try {
                            MidiEvent event = midiInputController.getEventQueue().take();

                            //Silent mode
                            if (captureMode) {
                                statusQueue.offer(event);
                                continue;
                            }

                            ControlEvent controlEvent = translateInput(event);

                            if (controlEvent != null) {
                                if ("PAN".equals(controlEvent.getAction()) ||
                                        "TILT".equals(controlEvent.getAction()) ||
                                        "ZOOM".equals(controlEvent.getAction())) {
                                    outputBus.publish(controlEvent);
                                } else {
                                    debounceControlEvent(controlEvent);
                                }
                            }

                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            LOGGER.warning("MIDI error: " + e.getMessage());
                        }
                    }
                }, "MIDI-CONTROLLER" );

        controllerThread.setDaemon(true);
        controllerThread.start();

        jogWatchdogThread =new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(20);

                    if (captureMode) {
                        continue;
                    }

                    if (lastJogEvent == null) {
                        continue;
                    }

                    long now = System.currentTimeMillis();

                    if (now - lastJogEventTime < JOG_TIMEOUT_MS) {
                        continue;
                    }

                    ControlEvent stopEvent =
                            new ControlEvent(
                                    ControlEvent.EventSource.MIDI,
                                    lastJogEvent.getPanel(),
                                    lastJogEvent.getAction(),
                                    0
                            );
                    outputBus.publish(stopEvent);

                    lastJogEvent = null;
                } catch (InterruptedException ie){
                    break;
                } catch (Exception e) {
                    LOGGER.warning ("MIDI error: " + e.getMessage());
                }
            }

        }, "JOG-WATCHDOG" );

        jogWatchdogThread.setDaemon(true);
        jogWatchdogThread.start();
        running = true;

        System.out.println("MIDI Controller started" );
    }

    /**
     * Stops MIDI processing and releases all hardware resources.
     *
     * <p>The controller threads are terminated, device connections are closed and internal queues are cleared.</p>
     */
    @SneakyThrows
    public synchronized void shutdown() {

        running = false;

        lastJogEvent = null;
        lastJogEventTime = 0;

        if (controllerThread != null) {
            controllerThread.interrupt();
        }

        if (jogWatchdogThread != null) {
            jogWatchdogThread.interrupt();
        }

        if (midiInputController != null) {
            midiInputController.close();
        }

        if (midiOutputController != null) {
            midiOutputController.close();
        }

        pendingTasks.values().forEach(task -> task.cancel(true));

        pendingTasks.clear();

        controlQueue.clear();
        statusQueue.clear();
        System.out.println("MIDI controller stopped");
    }

    /**
     * Converts an incoming MIDI event into a logical  application control event.
     *
     * <p>The method resolves control bindings, panel assignments and special controller functions before generating a {@link ControlEvent}.</p>
     *
     * @param event received MIDI event
     * @return translated control event or {@code null}
     */
    private ControlEvent translateInput(MidiEvent event) {

        for (Map.Entry<String, MidiControlDefinition> entry : config.getControls().entrySet()) {

            String controlId = entry.getKey();
            MidiControlDefinition control = entry.getValue();

            if (!matches(control, event)) {
                continue;
            }

            MidiBindingDefinition binding = config.getBindings().get(controlId);

            if (binding == null) {
                // MIDI value not mapped to system function
                return null;
            }

            //PANEL SWITCHING
            if ("PANEL_SELECT_A".equals(binding.getAction())) {
                this.camLedAssignment = MainControlCore.ControlPanelId.A;
                reassignControls(MainControlCore.ControlPanelId.A);
                updateCueIndicators();
                updateCameraIndicators(MainControlCore.ControlPanelId.A);
                return null;
            }

            if ("PANEL_SELECT_B".equals(binding.getAction())) {
                this.camLedAssignment = MainControlCore.ControlPanelId.B;
                reassignControls(MainControlCore.ControlPanelId.B);
                updateCueIndicators();
                updateCameraIndicators(MainControlCore.ControlPanelId.B);
                return null;
            }

            //MIDI JOG handling
            if ("jog".equalsIgnoreCase(control.getType())) {
                ControlEvent jogEvent = handleJog(event);
                lastJogEvent = jogEvent;
                return jogEvent;
            }

            //MIDI Note control handling
            if ("note".equalsIgnoreCase(control.getMessageType())) {
                int state = event.value() > 0 ? 1 : 0;
                return new ControlEvent(
                        MIDI,
                        MainControlCore.ControlPanelId.valueOf(binding.getPanel()),
                        binding.getAction(),
                        state
                );
            } else {
                //MIDI CC values handling
                return new ControlEvent(
                        MIDI,
                        MainControlCore.ControlPanelId.valueOf(binding.getPanel()),
                        binding.getAction(),
                        event.value()
                );
            }
        }
        return null;
    }

    /**
     * Converts an application control event into MIDI feedback.
     *
     * <p>The generated output typically controls LEDs, indicators and hardware feedback elements.</p>
     *
     * @param control MIDI control definition
     * @param event application event
     */
    private void translateOutput(MidiControlDefinition control, ControlEvent event) {

        MainControlCore.ControlPanelId outputPanel = event.getPanel();

        if (event.getAction().startsWith("CAM_SELECT_")
                && event.getAction().endsWith("_INDICATOR")) {

            MainControlCore.ControlPanelId midiPanel = event.getPanel();

            // Camera not assigned to Midi
            if (midiPanel == null) {
                return;
            }

            // Different LED assignement
            if (midiPanel != this.camLedAssignment) {
                return;
            }

            outputPanel = camLedAssignment;
        }

        String controlName = outputBindings.get(outputPanel + ":" + event.getAction());

        if (controlName == null) {
            if (controllerManager.getMidiBinding(event.getPanel()) != null) {
                LOGGER.log(Level.WARNING, "No MIDI binding for action: " + event.getAction() + " panel: " + event.getPanel());
            }
            return;
        }

        MidiControlDefinition midiControl = config.getControls().get(controlName);
        if (midiControl == null) {
            LOGGER.log(Level.WARNING, "No MIDI definition for control: " + controlName);
            return;
        }

        if ("request".equalsIgnoreCase(control.getType())) {

            Map<String, Object> states = (Map<String, Object>) control.getProperties().get("states" );
            midiOutputController.setOutputValue(control.getChannel(), ((Number) states.get("request" )).intValue());
        }

        if ("led".equalsIgnoreCase(midiControl.getType())) {

            Map<String, Object> states = (Map<String, Object>) midiControl.getProperties().get("states" );

            if (states == null) {
                return;
            }

            switch (event.getValue()) {
                case 0:
                    midiOutputController.setOutputValue(midiControl.getChannel(), ((Number) states.get("off" )).intValue());
                    break;

                case 1:
                    midiOutputController.setOutputValue(midiControl.getChannel(), ((Number) states.get("on" )).intValue());
                    break;

                case 2:
                    if (config.isHardwareBlinking()) {
                        midiOutputController.setOutputValue(midiControl.getChannel(), ((Number) states.get("blink" )).intValue());
                    } else {
                        midiOutputController.enableBlink(midiControl.getChannel(), ((Number) states.get("on" )).intValue());
                    }
                    break;
            }
        }
    }

    /**
     * Determines whether a MIDI event matches a control definition.
     *
     * @param control control definition
     * @param event received MIDI event
     * @return {@code true} if the event belongs to the control
     */
    private boolean matches(MidiControlDefinition control, MidiEvent event) {

        if ("cc".equalsIgnoreCase(control.getMessageType())) {
            return event.type() == MidiEvent.Type.CC
                    && control.getChannel() != null
                    && control.getChannel() == event.number();
        }

        if ("note".equalsIgnoreCase(control.getMessageType())) {

            return event.type() == MidiEvent.Type.NOTE
                    && control.getNote() != null
                    && control.getNote() == event.number();
        }
        return false;
    }

    /**
     * Processes an application event and converts it into MIDI feedback.
     *
     * <p>Depending on the event type, this may result in LED updates, indicator state changes, blinking effects or status requests
     * sent to the physical controller.</p>
     * @param event event received from the application
     */
    @Override
    public void onControlEvent(ControlEvent event) {

        if (event == null) {
            return;
        }

        if (event.getEventSource() != SYSTEM){
            return;
        }

        if ("STATUS_REQUEST".equals(event.getAction())) {
            captureMode = true;
            midiInputController.getEventQueue().clear();
            statusQueue.clear();
            try {
                ControlEvent response = handleStatusRequest(event);
                if (response != null) {
                    outputBus.publish(response);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                statusQueue.clear();
                captureMode = false;
            }
            return;
        }

        for (Map.Entry<String, MidiBindingDefinition> entry : config.getBindings().entrySet()) {

            if (!entry.getValue().getAction().equals(event.getAction())) {
                continue;
            }

            MidiControlDefinition control = config.getControls().get(entry.getKey());

            if (control == null) {
                continue;
            }
            translateOutput(control, event);
        }
    }

    /**
     * Applies software debouncing to incoming control events.
     *
     * <p>If multiple events with the same action arrive within the debounce interval, only the last one is forwarded.</p>
     *
     * @param event event to debounce
     */
    private void debounceControlEvent(ControlEvent event) {

        String key = event.getAction();

        ScheduledFuture<?> oldTask = pendingTasks.get(key);

        if (oldTask != null) {
            oldTask.cancel(false);
        }

        ScheduledFuture<?> newTask =
                scheduler.schedule(
                        () -> outputBus.publish(event),
                        50,
                        TimeUnit.MILLISECONDS
                );

        pendingTasks.put(key, newTask);
    }

    /**
     * Processes jog wheel movement events.
     *
     * <p>MIDI jog wheel values are converted into signed movement deltas and translated into application control events.</p>
     *
     * @param event MIDI input event
     * @return generated control event or {@code null}
     */
    private ControlEvent handleJog(MidiEvent event) {

        int delta = event.value() - 64;

        if (delta == 0) {
            return null;
        }

        for (Map.Entry<String, MidiControlDefinition> entry : config.getControls().entrySet()) {

            String controlId = entry.getKey();

            MidiControlDefinition control = entry.getValue();

            if (!matches(control, event)) {
                continue;
            }

            MidiBindingDefinition binding = config.getBindings().get(controlId);

            if (binding == null) {
                return null;
            }

            lastJogValue = delta;

            lastJogEventTime = System.currentTimeMillis();

            return new ControlEvent(
                    MIDI,
                    MainControlCore.ControlPanelId.valueOf(binding.getPanel()),
                    binding.getAction(),
                    delta
            );
        }

        return null;
    }

    /**
     * Performs a MIDI controller status query.
     *
     * <p>The method temporarily enters capture mode and waits for the response generated by the controller hardware.</p>
     *
     * @param request status request event
     * @return status response event or {@code null}
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    private ControlEvent handleStatusRequest(ControlEvent request) throws InterruptedException {

        statusQueue.clear();

        MidiControlDefinition control = config.getControls().get("STATUS_REQUEST" );

        translateOutput(
                control,
                request
        );

        //Matcher
        MidiControlDefinition requestControl = null;
        for (Map.Entry<String, MidiBindingDefinition> entry : config.getBindings().entrySet()) {
            MidiBindingDefinition binding = entry.getValue();
            if (!binding.getAction().equals(request.getAction())) {
                continue;
            }
            if (!binding.getPanel().equals(request.getPanel().name())) {
                continue;
            }
            requestControl = config.getControls().get(entry.getKey());
            break;
        }

        if (requestControl == null) {
            LOGGER.info("Uknown request target value " + request.getAction() + " -> " + request.getPanel().name());
        }

        long timeout = System.currentTimeMillis() + 1000;

        while (System.currentTimeMillis() < timeout) {

            MidiEvent midiEvent = statusQueue.poll(50, TimeUnit.MILLISECONDS);

            if (midiEvent == null || midiEvent.type() != MidiEvent.Type.CC) {
                continue;
            }

            if (requestControl == null) {
                continue;
            }
            if (midiEvent.number() != requestControl.getChannel()) {
                continue;
            }

            ControlEvent response = new ControlEvent(
                    MIDI,
                    request.getPanel(),
                    "REQUEST_RESPONSE",
                    midiEvent.value()
            );

            Thread.sleep(100);
            statusQueue.clear();

            return response;

        }
        return null;
    }

    /**
     * Reassigns all dynamic controls to a different logical panel.
     *
     * <p>Only bindings marked as reassignable are affected.</p>
     * <p>This mechanism allows a single physical controller section to operate on different cameras during runtime.</p>
     *
     * @param panel target control panel
     */
    private void reassignControls(MainControlCore.ControlPanelId panel) {

        String targetPanel = panel.name();

        for (MidiBindingDefinition binding : config.getBindings().values()) {
            if (binding.isReassignable()) {
                binding.setPanel(targetPanel);
            }
        }

        outputBindings.clear();
        for (Map.Entry<String, MidiBindingDefinition> entry : config.getBindings().entrySet()) {

            MidiBindingDefinition binding = entry.getValue();
            outputBindings.put(binding.getPanel() + ":" + binding.getAction(), entry.getKey());
        }

    }

    /**
     * Updates controller panel-selection indicators.
     *
     * <p>The active panel indicator is enabled while inactive panel indicators are disabled.</p>
     */
    private void updateCueIndicators() {

        outputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        MainControlCore.ControlPanelId.A,
                        "PANEL_SELECT_INDICATOR",
                        this.camLedAssignment == MainControlCore.ControlPanelId.A ? 1 : 0
                )
        );

        outputBus.publish(
                new ControlEvent(
                        SYSTEM,
                        MainControlCore.ControlPanelId.B,
                        "PANEL_SELECT_INDICATOR",
                        this.camLedAssignment == MainControlCore.ControlPanelId.B ? 1 : 0
                )
        );
    }

    /**
     * Updates camera selection indicators for the specified MIDI panel.
     *
     * @param selectedPanel panel whose indicators should be updated
     */
    public void updateCameraIndicators(MainControlCore.ControlPanelId selectedPanel) {

        MainControlCore.ControlPanelId guiBinding = controllerManager.getCoreBinding(selectedPanel);

        int activeIndex = -1;

        if (guiBinding != null) {
            activeIndex = guiBinding.ordinal();
        }

        for (int i = 0;
             i < MainControlCore.ControlPanelId.values().length;
             i++) {

            outputBus.publish(
                    new ControlEvent(
                            SYSTEM,
                            selectedPanel,
                            "CAM_SELECT_" + (i + 1) + "_INDICATOR",
                            i == activeIndex ? 1 : 0
                    )
            );
        }
    }

    /**
     * Clears indicators associated with a logical panel.
     *
     * <p>All MIDI controls currently assigned to the specified panel are reset to their inactive state.</p>     *
     * @param panelId panel to clear
     */
    public void clearPanel(MainControlCore.ControlPanelId panelId) {

        Map<String, MidiBindingDefinition> bindings = config.getBindings();

        for (MidiBindingDefinition binding : bindings.values()) {

            if (!panelId.name().equals(binding.getPanel())) {
                continue;
            }
            ControlEvent event = new ControlEvent(
                    SYSTEM,
                    panelId,
                    binding.getAction(),
                    0);
            outputBus.publish (event);
        }
    }

}


