package uci.bcd2ptz.ptz;

import lombok.Data;
import uci.bcd2ptz.core.ControlEvent;
import uci.bcd2ptz.visca.FieldDefinition;
import uci.bcd2ptz.visca.ViscaConfig;
import uci.bcd2ptz.visca.ViscaPTZController;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class PTZCamera {

    private final String cameraId;

    private final String model;

    //Transport
    private String ipAddress;
    private int cameraPort;
    private int localPort;
    private InetAddress address;
    private DatagramSocket socket;


    private int viscaSessionID = 1;

    //Parameters
    private final Map<String, ParameterState> parameters = new ConcurrentHashMap<>();
    private boolean online;
    private long lastUpdateTime;
    private String displayName;

    /*
     * Sterownik przypisany do tej kamery
     */
    private final ViscaPTZController controller;
    private final PTZCameraManager cameraManager;

    public PTZCamera(
            String cameraId,
            String ipAddress,
            int cameraPort,
            int localPort,
            int socketTimeout,
            String displayName,
            ViscaConfig config,
            PTZCameraManager cameraManager)
            throws Exception {

        //DEBUG:
        //System.out.println("Creating Camera " + cameraId + " Local port: " + localPort);

        this.cameraId = cameraId;
        this.displayName = displayName;
        this.model = config.getCameraName();


        this.ipAddress = ipAddress;
        this.localPort = localPort;
        this.cameraPort = cameraPort;
        this.address = InetAddress.getByName(this.ipAddress);

        this.socket = new DatagramSocket(null);
        this.socket.setReuseAddress(true);

        this.cameraManager = cameraManager;

        //SCAFF
        InetAddress localAddress = InetAddress.getByName("100.75.75.78");
        this.socket.bind(new InetSocketAddress(localAddress, 52381));

        //Debug
        System.out.println("CAMERA " + cameraId + " IP " + ipAddress + " PORT " + localPort +
                "\nCONSOLE IP " + socket.getLocalAddress() + " PORT " + socket.getLocalPort() + "\n");


        //this.socket.bind(new InetSocketAddress(localPort));
        this.socket.connect(this.address, this.cameraPort);

        Map<String, ParameterState> parameters = new HashMap<>();

        this.socket.setSoTimeout(socketTimeout);

        this.controller = new ViscaPTZController(this, config, this.socket.getSoTimeout());

        //====== CONFIGURATION ==========

        initializeDefaultParameters(config);
        syncPosition();

        //DEBUG:
        /* System.out.println ("============ CAMERA CONSTRUCTOR =============\nPARAMETERS LIST: " + this.parameters.keySet());
        System.out.println("PAN prameter: " + this.parameters.get("panPosition").toString());
        System.out.println("TILT prameter: " + this.parameters.get("tiltPosition").toString());
        System.out.println("FOCUS prameter: " + this.parameters.get("focus").toString());*/
    }

    public void touch() {

        lastUpdateTime = System.currentTimeMillis();
    }

    /*
     * =========================================
     *               API kamery
     * =========================================
     */

    public void setPower (boolean powerOn){
        if (powerOn) {
            controller.executeViscaCommand("powerOn");
        }else{
            controller.executeViscaCommand("powerOff");
        }
    }

    public void moveAbsolute(double pan, double tilt, int speed) {
        controller.moveAbsolute(pan, tilt,speed);
    }

    public void movePanTilt(int panValue, int tiltValue){
        controller.movePanTilt(panValue, tiltValue);
    }

    public void moveZoomAbsolute(int value) {
        controller.moveZoomAbsolute(value);
    }

    public void stopPanTilt() {
        controller.stopPanTilt();
    }

    public void adjustExposureParameter(String parameterName, int direction){

        //DEBUG
        //System.out.println("============ PTZ CAMERA ============ \n"+
        //        "adjusting " + parameterName + "  Direction = " + direction);

        Map<String, Object> params = new HashMap<>();
        params.put("direction", direction > 0 ? "UP" : "DOWN");
        controller.executeViscaCommand(parameterName, params);

    }

    public void recallPreset(String preset) {
        controller.recallPreset(preset);
    }

    public boolean syncPosition() {
        //DEBUG:
        //System.out.println("Camera " + this.cameraId + " position request");
        return controller.syncPosition();
    }

    public int powerStatusRequest() {

        boolean communicationOk =  controller.powerStatusRequest();

        if (!communicationOk) {
            return -1;
        }

        ParameterState state = parameters.get("powerStatus");
        if (state == null || state.getCurrentState() == null) {
            return -1;
        }

        return (int)state.getCurrent();
    }

    public void setWhiteBalanceMode(String mode){

        Map <String, Object> params = new HashMap<>();
        switch (mode) {
            case "AUTO":
                params.put("whiteBalanceMode", "AUTO");
                break;

            case "MANUAL":
                params.put("whiteBalanceMode", "MANUAL");
                break;

            case "ONE_PUSH":
                params.put("whiteBalanceMode", "ONE_PUSH");
                break;

            case "INDOOR":
                params.put("whiteBalanceMode", "INDOOR");
                break;

            case "OUTDOOR":
                params.put("whiteBalanceMode", "OUTDOOR");
                break;

            case "ATW":
                params.put("whiteBalanceMode", "ATW");
                break;
        }
        executeViscaCommand("whiteBalanceMode", params);
    }

    public void onePushWBal(){
        controller.executeViscaCommand("whiteBalanceOnePush");
    }

    public void setFocusMode(String mode){

        Map <String, Object> params = new HashMap<>();
        switch (mode) {
            case "AUTO":
                params.put("focusMode", "AUTO");
                break;

            case "MANUAL":
                params.put("focusMode", "MANUAL");
                break;

            case "ONE_PUSH":
                params.put("focusMode", "ONE_PUSH");
                break;
        }
        executeViscaCommand("focusMode", params);
    }

    public void onePushAutofocus(){

        executeViscaCommand("onePushAutofocus");
    }

    public void moveFocusAbsolute (int value){
        controller.moveFocusAbsolute(value);
    }

    public void setExposureMode (String mode) {

        Map <String, Object> params = new HashMap<>();

        //"00" : "AUTO",
        //          "03" : "MANUAL",
        //          "0A" : "SHUTTER_PRIORITY",
        //          "0B" : "IRIS_PRIORITY",
        //          "0D" : "BRIGHT"
        switch (mode) {
            case "AUTO":
                params.put("exposureMode", "AUTO");
                break;

            case "MANUAL":
                params.put("exposureMode", "MANUAL");
                break;

            case "SHUTTER_PRIOROTY":
                params.put("exposureMode", "SHUTTER_PRIORITY");
                break;

            case "IRIS_PRIOROTY":
                params.put("exposureMode", "IRIS_PRIORITY");
                break;

            case "BRIGHT":
                params.put("exposureMode", "BRIGHT");
                break;
        }
        executeViscaCommand("exposureMode", params);

    }

    //VISCA COMMAND EXECUTION

    private void executeViscaCommand (String commandName, Map<String, Object> params){

        controller.executeViscaCommand(commandName, params);
    }

    private void executeViscaCommand (String commandName){

        controller.executeViscaCommand(commandName);
    }

    /*
     * =========================================
     *     GETTERS, SETTERS, HELPERS
     * ========================================= */

    private void initializeDefaultParameters(ViscaConfig config) {

        //Positions limits
        FieldDefinition panField =config.getFields().get("panPosition");
        Map<String, Object> mapping = panField.getMapping();
        Map<String, Object> mechanicalRange = (Map<String, Object>) mapping.get("mechanicalRange");
        ParameterState panState = new ParameterState();
        panState.setMax(((Number) mechanicalRange.get("maxDegrees")).doubleValue());
        panState.setMin(((Number) mechanicalRange.get("minDegrees")).doubleValue());
        this.parameters.put("panPosition", panState);

        FieldDefinition tiltField = config.getFields().get("tiltPosition");
        mapping = tiltField.getMapping();
        mechanicalRange = (Map<String, Object>) mapping.get("mechanicalRange");
        ParameterState tiltState = new ParameterState();
        tiltState.setMax(((Number) mechanicalRange.get("maxDegrees")).doubleValue());
        tiltState.setMin(((Number) mechanicalRange.get("minDegrees")).doubleValue());
        this.parameters.put("tiltPosition", tiltState);

        ParameterState param;

        param = new ParameterState();
        param.setCurrentState("AUTO");

        FieldDefinition field = controller.getFieldDefinition("focusPosition");

        param.setMin(field.getMinRangeValue());
        param.setMax(field.getMaxRangeValue());

        parameters.put("focusPosition", param);
        setFocusMode("AUTO");

        param = new ParameterState();
        param.setCurrentState("AUTO");

        parameters.put("whiteBalanceMode",param);
        setWhiteBalanceMode("AUTO");
    }

    public void markOnline(){
        this.online = true;
    }

    public void markOffline(){
        this.online = false;
    }

    public ParameterState getParameter(String parameterName) {

        ParameterState parameter = this.parameters.get(parameterName);

        //System.out.println("==== CAMERA ======\nparameter requested: " + parameterName);

        if (parameter != null) {
            return parameter;
        }

        //Pobranie i aktualizacja stanu z kamery jeżeli nie ma w pamięci

        //DEBUG
        //System.out.println(parameterName + "Parameter request send");
        controller.parameterInquiry(parameterName);

        //DEBUG
        //System.out.println(parameterName + "Parameter Received. Name: " + parameterName + " Values: " + parameters.get(parameterName).toString());

        return this.parameters.get(parameterName);
    }

    public ParameterState getParameter(String parameterName, boolean forceUpdate) {

        ParameterState parameter = this.parameters.get(parameterName);

        if (parameter != null && !forceUpdate) {
            return parameter;
        }

        //Pobranie i aktualizacja stanu z kamery jeżeli nie ma w pamięci

        //System.out.println(parameterName + "Parameter request send");
        controller.parameterInquiry(parameterName);
        return this.parameters.get(parameterName);
    }

    public String getDescription(){
        return this.displayName;
    }

    public double clampPan( double value){

        ParameterState panState = this.getParameter("panPosition");
        return Math.max (panState.getMin(), Math.min(value, panState.getMax()));
    }

    public double clampTilt( double value){

        ParameterState tiltState = this.getParameter("tiltPosition");
        return Math.max (tiltState.getMin(), Math.min(value, tiltState.getMax()));
    }

    @Override

    public String toString() {

        return String.format(
                "PTZCamera[id=%s, ip=%s:%d]",
                cameraId,
                ipAddress,
                cameraPort
        );
    }
}