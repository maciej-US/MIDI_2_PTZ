package uci.bcd2ptz.midi;

import lombok.Getter;
import uci.bcd2ptz.config.AppConfig;
import uci.bcd2ptz.config.AppConfigManager;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlEvent;
import uci.bcd2ptz.core.ControlEventBus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static uci.bcd2ptz.core.ControlEvent.EventSource.MIDI;

@Getter
public class MidiController {

    private static final Logger LOGGER = Logger.getLogger(MidiController.class.getName());
    private MidiInputController midiInputController;
    private MidiOutputController midiOutputController;
    private final MidiConfig config;
    private ControlEventBus outputBus;
    private ControlCore.RunMode mode;

    private volatile boolean running = false;
    private volatile boolean captureMode = false;
    private ControlCore.ControlPanelId camLedAssignment;
    private Thread inputThread;
    private Thread controllerThread;
    private Thread jogWatchdogThread;

    private final BlockingQueue<ControlEvent> controlQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MidiEvent> statusQueue = new LinkedBlockingQueue<>();
    private final Map<String, Integer> pendingCCValues = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Map<String, String> outputBindings = new HashMap<>();

    //Jog params
    private volatile long lastJogEventTime = 0;
    private volatile int lastJogValue = 64;
    private volatile ControlEvent lastJogEvent;
    private static final long JOG_TIMEOUT_MS = 50;
    private final Set<Integer> jogCCNumbers = new HashSet<>();

    // API
    private final MidiControllerManager controllerManager;

    public MidiController(
            MidiConfig config,
            int inputDeviceIndex,
            int outputDeviceIndex,
            ControlEventBus outputBus,
            ControlCore.RunMode mode,
            MidiControllerManager controllerManager) throws Exception {

        this.mode = mode;
        this.config = config;
        this.outputBus = outputBus;
        this.controllerManager = controllerManager;
        this.camLedAssignment = ControlCore.ControlPanelId.A;

        //Reverse binding index
        for (Map.Entry<String, MidiBindingDefinition> entry : config.getBindings().entrySet()) {

            MidiBindingDefinition binding = entry.getValue();
            outputBindings.put(binding.getPanel() + ":" + binding.getAction(), entry.getKey());
        }
        this.midiInputController = null;
        this.midiOutputController = null;

        try {
            this.midiInputController = new MidiInputController(inputDeviceIndex, mode);
            this.midiOutputController = new MidiOutputController(outputDeviceIndex, config.isHardwareBlinking(), mode);
        }catch (Exception e){
            if(midiInputController != null){
                midiInputController.close();
            }
            if(midiOutputController != null){
                midiOutputController.close();
            }
        }

        if (mode.equals(ControlCore.RunMode.FULL)){
            start();
        }
    }

    public synchronized void start() {

        if (mode == ControlCore.RunMode.NO_HARDWARE) {
            return;
        }

        if (running) {
            return;
        }

        running = true;

        /*
         * Uruchomienie kontrolera wejścia MIDI
         */
        //inputThread = new Thread(midiInputController, "MIDI-INPUT" );

        for (MidiControlDefinition control : config.getControls().values()) {

            if (!"jog".equalsIgnoreCase(control.getType())) {
                continue;
            }
            jogCCNumbers.add(control.getChannel()
            );
        }

        /*inputThread.setDaemon(true);
        inputThread.start();*/

        /*
         * Translacja MIDI -> ControlEvent
         */

        controllerThread = new Thread(() -> {
                    while (running) {
                        try {
                            MidiEvent event = midiInputController.getEventQueue().take();
                            //DEBUG
                            //System.out.println( "EVENT IN -> " + event.getType() + "  " + event.getNumber() + "  " + event.getValue());

                            //Silent mode
                            if (captureMode) {
                                statusQueue.offer(event);
                                //DEBUG
                                //System.out.println( "STATUS CAPUTRE -> " + event.getNumber() + "  " + event.getValue());

                                continue;
                            }

                            long now = System.currentTimeMillis();

                            ControlEvent controlEvent = translateInput(event);

                            if (controlEvent != null) {

                                //DEBUG:
                                //System.out.println("PUBLISH: " + controlEvent.getAction()+ "  source = " + controlEvent.getEventSource());
                                if ("PAN".equals(controlEvent.getAction()) ||
                                        "TILT".equals(controlEvent.getAction()) ||
                                        "ZOOM".equals(controlEvent.getAction())) {
                                    outputBus.publish(controlEvent);
                                } else {
                                    debounceControlEvent(controlEvent);
                                }

                                //DEBUG
                                //System.out.println("MIDI Controller event controlID: " + controlEvent.getControlID());
                            }

                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
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

                    //DEBUG:
                    /*System.out.println("==== JOG TIMEOUT =====");
                    System.out.println("PUBLISH: " + stopEvent.getAction()+ "  source = " + stopEvent.getEventSource());
*/
                    outputBus.publish(stopEvent);

                    lastJogEvent = null;
                } catch (InterruptedException ie){
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }, "JOG-WATCHDOG" );

        jogWatchdogThread.setDaemon(true);
        jogWatchdogThread.start();
        running = true;

        System.out.println("MIDI Controller started" );
    }

    public synchronized void shutdown() {

        running = false;

        lastJogEvent = null;
        lastJogEventTime = 0;

        if (inputThread != null) {
            inputThread.interrupt();
        }

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

    private ControlEvent translateInput(MidiEvent event) {

        //DEBUG
        //System.out.println("====== MIDI INPUT =====\n Received: Type = " + event.getType() + "  | value = " + event.getValue());


        for (Map.Entry<String, MidiControlDefinition> entry : config.getControls().entrySet()) {

            String controlId = entry.getKey();
            MidiControlDefinition control = entry.getValue();

            if (!matches(control, event)) {
                continue;
            }

            MidiBindingDefinition binding = config.getBindings().get(controlId);

            if (binding == null) {
                // brak mapowania na funkcję systemową
                return null;
            }

            //DEBUG
            //System.out.println("Received: " + event.getType() + " | VAL: " + event.getValue() + " | CC " + event.getChannel()
            //       + "\nBINDING: " + controlId + " =>> " + binding.getAction());

            //PANEL SWITCHING
            if ("PANEL_SELECT_A".equals(binding.getAction())) {
                this.camLedAssignment = ControlCore.ControlPanelId.A;
                reassignControls(ControlCore.ControlPanelId.A);
                updateCueIndicators();
                updateCameraIndicators(ControlCore.ControlPanelId.A);
                return null;
            }

            if ("PANEL_SELECT_B".equals(binding.getAction())) {
                this.camLedAssignment = ControlCore.ControlPanelId.B;
                reassignControls(ControlCore.ControlPanelId.B);
                updateCueIndicators();
                updateCameraIndicators(ControlCore.ControlPanelId.B);
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
                int state = event.getValue() > 0 ? 1 : 0;
                //DEBUG
                //System.out.println ("======== MIDI CONTROLLER ======= \nnoteHandle: " + event.getNumber());
                return new ControlEvent(
                        MIDI,
                        ControlCore.ControlPanelId.valueOf(binding.getPanel()),
                        binding.getAction(),
                        state
                );
            } else {
                //MIDI CC values handling
                return new ControlEvent(
                        MIDI,
                        ControlCore.ControlPanelId.valueOf(binding.getPanel()),
                        binding.getAction(),
                        event.getValue()
                );
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked" )
    private void translateOutput(MidiControlDefinition control, ControlEvent event) {

        //DEBUG
       /* System.out.println("translate output from panel " + event.getPanel() + " ==> " + event.getAction());// + "  |  " + control.toString());
        System.out.println("getMidiBinding("+ event.getPanel() + ")="  + controllerManager.getMidiBinding(event.getPanel()));*/

        ControlCore.ControlPanelId outputPanel = event.getPanel();

        if (event.getAction().startsWith("CAM_SELECT_")
                && event.getAction().endsWith("_INDICATOR")) {

            ControlCore.ControlPanelId midiPanel = event.getPanel();

            // kamera nie jest przypisana do żadnego panelu MIDI
            if (midiPanel == null) {
                return;
            }

            // LED-y aktualnie pokazują inny panel MIDI
            if (midiPanel != this.camLedAssignment) {
                return;
            }

            outputPanel = camLedAssignment;
        }

        //System.out.println("LEDS bindend to panel: " + camLedAssignment + "   |  event panel: " + event.getPanel() + "  | MIDI panel:" + outputPanel);

        String controlName = outputBindings.get(outputPanel + ":" + event.getAction());

        if (controlName == null) {
            if (controllerManager.getMidiBinding(event.getPanel()) != null) {
                LOGGER.log(Level.WARNING, "No MIDI binding for action: " + event.getAction() + " panel: " + event.getPanel());
            }
            return;
        }

        MidiControlDefinition midiControl = config.getControls().get(controlName);
        //Debug
        //System.out.println("Binded Control : " + controlName + " MIDI CONTROL: " + midiControl.getChannel());

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

    private boolean matches(MidiControlDefinition control, MidiEvent event) {

        if ("cc".equalsIgnoreCase(control.getMessageType())) {
            return event.getType() == MidiEvent.Type.CC
                    && control.getChannel() != null
                    && control.getChannel() == event.getNumber();
        }

        if ("note".equalsIgnoreCase(control.getMessageType())) {

            return event.getType() == MidiEvent.Type.NOTE
                    && control.getNote() != null
                    && control.getNote() == event.getNumber();
        }
        return false;
    }

    public void sendControlEvent(ControlEvent event) {

        //DEBUG
        /*System.out.println(
                "SEND EVENT\n" +
                        "CORE PANEL = " + event.getPanel() +
                        "\nMIDI PANEL = " + controllerManager.getMidiBinding(event.getPanel()) +
                        "\nACTION = " + event.getAction()
        );*/

        if ("STATUS_REQUEST".equals(event.getAction())) {

            //DEBUG
            //System.out.println("============ MIDI CONTROLLER ===========\nCALL   -> " + event.getAction() + "  control: " + event.getControlID());

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

    /* =================================
               JOG HANDLING
       ================================= */

    private ControlEvent handleJog(MidiEvent event) {

        int delta = event.getValue() - 64;

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
                    ControlCore.ControlPanelId.valueOf(binding.getPanel()),
                    binding.getAction(),
                    delta
            );
        }

        return null;
    }

    /* =================================
          SYSTEM STATUS REQUEST HANDLING
       =================================*/

    private ControlEvent handleStatusRequest(ControlEvent request) throws InterruptedException {

        //DEBUG
        //System.out.println("======== MIDI CONTROLLER ==========\nhandleStatusRequest  ->" + request.getControlID());

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
            //System.out.println("Uknown request target value " + request.getControlID() + " -> " + request.getPanel().name());
        }

        long timeout = System.currentTimeMillis() + 1000;

        while (System.currentTimeMillis() < timeout) {

            MidiEvent midiEvent = statusQueue.poll(50, TimeUnit.MILLISECONDS);

            if (midiEvent == null || midiEvent.getType() != MidiEvent.Type.CC) {
                continue;
            }

            //DEBUG
            //System.out.println("CAPTURED MIDI:  " + midiEvent.getType() + "  " + midiEvent.getValue() + " LOOK for: " + request.getControlID());

            if (requestControl == null) {
                //DEBUG
                //System.out.println("EMPTY MIDI....");
                continue;
            }
            if (midiEvent.getNumber() != requestControl.getChannel()) {
                //DEBUG
                //System.out.println("NOT MATCHING INPUT....");
                continue;
            }

            //DEBUG:
            //System.out.println("MATCHED. SENDING ControlEvent");

            ControlEvent response = new ControlEvent(
                    MIDI,
                    request.getPanel(),
                    "REQUEST_RESPONSE",
                    midiEvent.getValue()
            );

            Thread.sleep(100);
            statusQueue.clear();

            return response;

        }
        return null;
    }


    /* =================================
           DYNAMIC CONTROL SWITCHING
      ================================== */

    private void reassignControls(ControlCore.ControlPanelId panel) {

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

    private void updateCueIndicators() {

        sendControlEvent(
                new ControlEvent(
                        MIDI,
                        ControlCore.ControlPanelId.A,
                        "PANEL_SELECT_INDICATOR",
                        this.camLedAssignment == ControlCore.ControlPanelId.A ? 1 : 0
                )
        );

        sendControlEvent(
                new ControlEvent(
                        MIDI,
                        ControlCore.ControlPanelId.B,
                        "PANEL_SELECT_INDICATOR",
                        this.camLedAssignment == ControlCore.ControlPanelId.B ? 1 : 0
                )
        );
    }

    public void updateCameraIndicators(ControlCore.ControlPanelId selectedPanel) {

        ControlCore.ControlPanelId guiBinding = controllerManager.getCoreBinding(selectedPanel);

        int activeIndex = -1;

        if (guiBinding != null) {
            activeIndex = guiBinding.ordinal();
        }

        for (int i = 0;
             i < ControlCore.ControlPanelId.values().length;
             i++) {

            sendControlEvent(
                    new ControlEvent(
                            MIDI,
                            selectedPanel,
                            "CAM_SELECT_" + (i + 1) + "_INDICATOR",
                            i == activeIndex ? 1 : 0
                    )
            );
        }
    }

    //===============================
    //    CONFIGURATION UPDATING
    //===============================


}