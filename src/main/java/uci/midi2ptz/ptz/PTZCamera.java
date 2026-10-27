package uci.midi2ptz.ptz;

import lombok.Data;
import uci.midi2ptz.visca.FieldDefinition;
import uci.midi2ptz.visca.ViscaConfig;
import uci.midi2ptz.visca.ViscaPTZController;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Represents a single PTZ camera managed by the application.
 *
 * <p>This class serves as a high-level abstraction of a physical  PTZ camera. It maintains camera state, parameter cache and
 * communication settings while delegating protocol-specific  operations to the associated {@link ViscaPTZController}.</p>
 * <p>A camera instance exposes a simplified API for camera control, including pan/tilt movement, zoom, focus, exposure, white balance
 * and preset management.</p>
 *
 * <h2>Responsibilities</h2> *
 * <ul>
 *     <li>Maintain runtime camera state</li>
 *     <li>Store cached parameter values</li>
 *     <li>Provide high-level camera control methods</li>
 *     <li>Manage camera communication settings</li>
 *     <li>Delegate VISCA command execution</li>
 * </ul>
 */
@Data
public class PTZCamera {

    private final String cameraId;
    private final String model;

    //TRANSPORT
    private String ipAddress;
    private int cameraPort;
    private int localPort;
    private InetAddress address;
    private DatagramSocket socket;
    private final Object transactionLock = new Object();
    private int viscaSessionID = 1;

    //PARAMETERS
    /**
     * Cached camera parameter values.
     **
     * <p>The cache stores the last known state reported by the camera, reducing the number of VISCA inquiry commands required during normal operation.</p>
     */
    private final Map<String, ParameterState> parameters = new ConcurrentHashMap<>();
    private boolean online;
    private long lastUpdateTime;
    private String displayName;

    private final ViscaPTZController controller;
    private final PTZCameraManager cameraManager;

    /**
     * Creates and initializes a PTZ camera instance.
     *
     * <p>The constructor initializes transport parameters,  creates the associated VISCA controller and loads
     * default camera parameter definitions.</p>
     *
     * @param cameraId unique camera identifier
     * @param ipAddress camera IP address
     * @param cameraPort VISCA communication port
     * @param localPort local UDP port
     * @param socketTimeout communication timeout in milliseconds
     * @param displayName user-visible camera name
     * @param config VISCA camera definition
     * @param cameraManager owning camera manager
     *
     * @throws Exception if camera initialization fails
     */
    public PTZCamera(String cameraId, String ipAddress, int cameraPort, int localPort, int socketTimeout, String displayName,
            ViscaConfig config, PTZCameraManager cameraManager) throws Exception {

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
        InetAddress localAddress = InetAddress.getByName(cameraManager.getLocalIp());
        this.socket.bind(new InetSocketAddress(localAddress, 52381));
        this.socket.connect(this.address, this.cameraPort);

        Map<String, ParameterState> parameters = new HashMap<>();

        this.socket.setSoTimeout(socketTimeout);

        this.controller = new ViscaPTZController(this, config, this.socket.getSoTimeout());

        //====== CONFIGURATION ==========

        initializeDefaultParameters(config);
        syncPosition();
    }

    /**
     * Changes camera power state.
     *
     * @param powerOn {@code true} to power on,
     *                {@code false} to power off
     */
    public void setPower(boolean powerOn){
        if (powerOn) {
            controller.executeViscaCommand("powerOn");
        }else{
            controller.executeViscaCommand("powerOff");
        }
    }

    /**
     * Moves the camera to an absolute pan/tilt position.
     *
     * @param pan target pan angle
     * @param tilt target tilt angle
     * @param speed movement speed
     */
    public void moveAbsolute(double pan, double tilt, int speed) {
        controller.moveAbsolute(pan, tilt,speed);
    }

    /**
     * Starts relative pan and tilt movement.
     *
     * @param panValue pan speed
     * @param tiltValue tilt speed
     */
    public void movePanTilt(int panValue, int tiltValue){
        controller.movePanTilt(panValue, tiltValue);
    }

    /**
     * Moves zoom to an absolute position.
     *
     * @param value target zoom position
     */
    public void moveZoomAbsolute(int value) {
        controller.moveZoomAbsolute(value);
    }

    /**
     * Moves the camera to its configured home position.
     */
    public void parkCamera(){
        controller.ptzHomePosition();
    }

    /**
     *  Stops all movement in PAN and TILT direction.
     */
    public void stopPanTilt() {
        controller.stopPanTilt();
    }


    /** Controls camera tally indication
     *
     * @param tallyState
     *        {@code 0} - OFF
     *        {@code 1} - ON
     */
    public void tally(int tallyState){
        switch (tallyState){
            case 0:
                controller.executeViscaCommand("tallyOff");
                break;
            case 1:
                controller.executeViscaCommand("tallyOn");
                break;
            default:
                break;
        }
    }

    /**
     * Enables or disables the*camera auto-follow feature.
     *  param auto*ollowMode
     *        {@code 0} - OFF
     *        {@code 1} - ON
     */
    public void autoFollow(int autoFollowMode){
        switch (autoFollowMode){
            case 0:
                controller.executeViscaCommand("autoFollowOff");
                break;
            case 1:
                controller.executeViscaCommand("autoFollowOn");
                break;
            default:
                break;
        }
    }

    /**
     *  Adjusts an exposure-related parameter.
     * <p>The actual command depends on the supplied parameter name and direction.</p>
     *
     * @param parameterName parameter identifier
     * @param direction*positive values increase the parameter, negative values decrease it
     */
    public void adjustExposureParameter(String parameterName, int direction){

        Map<String, Object> params = new HashMap<>();
        params.put("direction", direction > 0 ? "UP" : "DOWN");
        controller.executeViscaCommand(parameterName, params);

    }

    public void recallPreset(String preset) {
        controller.recallPreset(preset);
    }

    /**
     * Synchronizes camera position withithe internal cache.
     *
     * @return {@code true} if synchronization succeeded.
     */
    public boolean syncPosition() {
        return controller.syncPosition();
    }

    /**
     * Requests the current camera power status.
     *
     * @return {@code 1} if powered on, {@code 0} if powered off,  {@code -1} if communication failed.
     */
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

    /**
     * Changes*white balance operating mode.
     *
     * <p>Supported modes depend on camera capabilities defined in the VISCA configuration.</p>
     *
     * @param mode white balance mode
     */
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

    /**
     * Executes a one-push white balance calibration.
     */
    public void onePushWBal(){
        controller.executeViscaCommand("whiteBalanceOnePush");
    }

    /**
     * Changes focus operating mode.
     * Supported modes include AUTO, MANAAL and ONE_PUSH.</p>
     *
     * @param mode focus mode name
     */
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

    /**
     * Triggers a one-push autofocus operation.
     */
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

    /**
     * Mark the camera as online.
     */
    public void markOnline(){
        this.online = true;
    }

    /**
     * Mark the camera as offline.
     */
    public void markOffline(){
        this.online = false;
    }

    /**
     * Retrieves a camera parameter.
     *
     * <p>If the param*ter is not present in the local ca*he,  a VISCA inquiry request is automatically issued.</p>
     *
     * @param parameterName parameter identifier
     * @return parameter state or {*code null}
     */
    public ParameterState getParameter(String parameterName) {

        ParameterState parameter = this.parameters.get(parameterName);

        //System.out.println("==== CAMERA ======\nparameter requested: " + parameterName);

        if (parameter != null) {
            return parameter;
        }

        //Retrieve and update camera parameter value.
        controller.parameterInquiry(parameterName);
        return this.parameters.get(parameterName);
    }

    /**
     * Retrieve* a camera parameter.
     *
     * @param parameterName parameter identifier
     * @param forceUpdate forces a camera inquiry even if the parameter already exists in cache
     * @return parameter state
     */
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

    /**
     * Returns the user-visible camera description.
     *
     * @return display name
     */
    public String getDescription(){
        return this.displayName;
    }

    /**
     * Clamps a pan value to the*supported camera range.
     *
     * @param value requested pan position
     * @return corrected pan position
     */
    public double clampPan( double value){

        ParameterState panState = this.getParameter("panPosition");
        return Math.max (panState.getMin(), Math.min(value, panState.getMax()));
    }

    /**
     * Clamps a tilt value to the supported camera range.
     *
     * @param value requested tilt position
     * @return corrected tilt posi*ion
     */
    public double clampTilt( double value){

        ParameterState tiltState = this.getParameter("tiltPosition");
        return Math.max (tiltState.getMin(), Math.min(value, tiltState.getMax()));
    }

    /**
     * Returns a compact camera description*intended for logging and debugging purposes.
     *
     * @return textual*camera representation
     */
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