package uci.bcd2ptz.midi;

import lombok.Getter;
import uci.bcd2ptz.core.ControlCore;
import uci.bcd2ptz.core.ControlEvent;
import uci.bcd2ptz.core.ControlEventBus;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static uci.bcd2ptz.core.ControlEvent.EventSource.MIDI;

@Getter
public class MidiController {

    private final MidiInputController input;
    private final MidiOutputController output;
    private final MidiConfig config;
    private ControlEventBus outputBus;
    private ControlCore.RunMode mode;

    private volatile boolean started = false;
    private volatile boolean captureMode = false;

    private final BlockingQueue<ControlEvent>controlQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MidiEvent>statusQueue = new LinkedBlockingQueue<>();
    private final Map<String,Integer>pendingCCValues = new ConcurrentHashMap<>();
    private final Map<String,ScheduledFuture<?>>pendingTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    //Jog params
    private volatile long lastJogEventTime = 0;
    private volatile int lastJogValue = 64;
    private static volatile ControlEvent lastJogEvent;
    private static final long JOG_TIMEOUT_MS = 50;
    private final Set<Integer> jogCCNumbers = new HashSet<>();

    public MidiController(
            MidiConfig config,
            int inputDeviceIndex,
            int outputDeviceIndex,
            ControlEventBus outputBus,
            ControlCore.RunMode mode) {

        this.mode = mode;
        this.input = new MidiInputController(inputDeviceIndex);
        this.output = new MidiOutputController(outputDeviceIndex, config.isHardwareBlinking());
        this.config = config;
        this.outputBus = outputBus;
    }

    public synchronized void start() {

        //DEBUG
        //System.out.println("MODE: " + mode.toString());
        if (mode == ControlCore.RunMode.NO_HARDWARE){
            return;
        }

        if (started) {
            return;
        }

        started = true;

        /*
         * Uruchomienie kontrolera wejścia MIDI
         */
        Thread inputThread = new Thread(input,"MIDI-INPUT");

        for (MidiControlDefinition control : config.getControls().values()) {

            if (!"jog".equalsIgnoreCase(control.getType())) {
                continue;
            }
            jogCCNumbers.add(
                    control.getChannel()
            );
        }

        inputThread.setDaemon(true);
        inputThread.start();

        /*
         * Translacja MIDI -> ControlEvent
         */

        Thread controllerThread =
                new Thread(() -> {
                    while (true) {
                        try {
                            MidiEvent event = input.getEventQueue().take();
                            //DEBUG
                            //System.out.println( "EVENT IN -> " + event.getType() + "  " + event.getNumber() + "  " + event.getValue());

                            //Silent mode
                            if(captureMode){
                                statusQueue.offer (event);
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
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, "MIDI-CONTROLLER");

        controllerThread.setDaemon(true);
        controllerThread.start();

        Thread jogWatchdog =
                new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(20);

                            if (captureMode){
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
                                            lastJogEvent.getControlID(),
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
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                }, "JOG-WATCHDOG");

        jogWatchdog.setDaemon(true);

        jogWatchdog.start();

        System.out.println("MidiController started");
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

            //MIDI JOG handling
            if ("jog".equalsIgnoreCase(control.getType())){
                ControlEvent jogEvent =  handleJog(event);
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
                        controlId,
                        ControlCore.ControlPanelId.valueOf(binding.getPanel()),
                        binding.getAction(),
                        state
                );
            }else {
                //MIDI CC values handling
                return new ControlEvent(
                        MIDI,
                        controlId,
                        ControlCore.ControlPanelId.valueOf(binding.getPanel()),
                        binding.getAction(),
                        event.getValue()
                );
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void translateOutput(MidiControlDefinition control, ControlEvent event) {

        //SCAFF
        //System.out.println("translate output  " + event.toString() + "  |  " + control.toString());

       if("request".equalsIgnoreCase(control.getType())){

           Map<String, Object> states = (Map<String, Object>)control.getProperties().get("states");
           output.setOutputValue(control.getChannel(), ((Number)states.get("request")).intValue());
       }

       if ("led".equalsIgnoreCase(control.getType())) {

            Map<String, Object> states = (Map<String, Object>)control.getProperties().get("states");

           if (states == null) {
               return;
           }

            switch (event.getValue()) {
                case 0:
                    output.setOutputValue(control.getChannel(),((Number) states.get("off")).intValue());
                    break;

                case 1:
                    output.setOutputValue(control.getChannel(),((Number) states.get("on")).intValue());
                    break;

                case 2:
                    if (config.isHardwareBlinking()) {
                         output.setOutputValue(control.getChannel(),((Number) states.get("blink")).intValue());
                    } else {
                         output.enableBlink(control.getChannel(),((Number) states.get("on")).intValue());
                    }
                    break;
            }
        }
    }

    private boolean matches(MidiControlDefinition control, MidiEvent event) {

        if ("cc".equalsIgnoreCase(control.getMessageType())) {
            return event.getType() == MidiEvent.Type.CC
                    && control.getChannel() != null
                    && control.getChannel()== event.getNumber();
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
        //System.out.println("==== SEND CONTROL EVENT ======= \n --> " + event.toString());

        if ("STATUS_REQUEST".equals(event.getAction())){

            //DEBUG
            //System.out.println("============ MIDI CONTROLLER ===========\nCALL   -> " + event.getAction() + "  control: " + event.getControlID());

            captureMode = true;
            input.getEventQueue().clear();
            statusQueue.clear();
            try {
                ControlEvent response = handleStatusRequest(event);
                if (response != null){
                    outputBus.publish(response);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally{
                statusQueue.clear();
                captureMode = false;
            }
            return;
        }


        for (Map.Entry<String, MidiBindingDefinition> entry : config.getBindings().entrySet()) {

            if (!entry.getValue().getAction().equals(event.getControlID())) {
                continue;
            }

            MidiControlDefinition control = config.getControls().get(entry.getKey());

            if (control == null) {
                continue;
            }
            translateOutput(control,event);
        }
    }

    private void debounceControlEvent(ControlEvent event) {

        String key =event.getControlID();

        ScheduledFuture<?> oldTask = pendingTasks.get(key);

        if (oldTask != null) {oldTask.cancel(false);}

        ScheduledFuture<?> newTask =
                scheduler.schedule(
                        () -> outputBus.publish(event),
                        100,
                        TimeUnit.MILLISECONDS
                );

        pendingTasks.put(key,newTask);
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
                    controlId,
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

        MidiControlDefinition control = config.getControls().get("STATUS_REQUEST");

        translateOutput(
                control,
                request
            );

        //Matcher
        MidiControlDefinition requestControl = null;
        for (Map.Entry<String, MidiBindingDefinition> entry : config.getBindings().entrySet()) {
            MidiBindingDefinition binding = entry.getValue();
            if (!binding.getAction().equals(request.getControlID())) {
                continue;
            }
            if (!binding.getPanel().equals(request.getPanel().name())){
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

            if (midiEvent == null || midiEvent.getType() != MidiEvent.Type.CC){
                continue;
            }

            //DEBUG
            //System.out.println("CAPTURED MIDI:  " + midiEvent.getType() + "  " + midiEvent.getValue() + " LOOK for: " + request.getControlID());

            if (requestControl == null) {
                //DEBUG
                //System.out.println("EMPTY MIDI....");
                continue;
            }
            if (midiEvent.getNumber() != requestControl.getChannel()){
                //DEBUG
                //System.out.println("NOT MATCHING INPUT....");
                continue;
            }

            //DEBUG:
            //System.out.println("MATCHED. SENDING ControlEvent");

            ControlEvent response = new ControlEvent(
                    MIDI,
                    request.getControlID(),
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

}