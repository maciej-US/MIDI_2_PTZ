package uci.bcd2ptz.ptz;

import lombok.Data;
import uci.bcd2ptz.visca.FieldDefinition;
import uci.bcd2ptz.visca.ViscaConfig;
import uci.bcd2ptz.visca.ViscaPTZController;

import java.lang.reflect.Parameter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class PTZCamera {

    private final String cameraId;

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

    /*
     * Sterownik przypisany do tej kamery
     */
    private final ViscaPTZController controller;

    public PTZCamera(
            String cameraId,
            String ipAddress,
            int cameraPort,
            int localPort,
            ViscaConfig config)
            throws Exception {

        //DEBUG:
        //System.out.println("Creating Camera " + cameraId + " Local port: " + localPort);

        this.cameraId = cameraId;

        this.ipAddress = ipAddress;
        this.localPort = localPort;
        this.cameraPort = cameraPort;
        this.address = InetAddress.getByName(this.ipAddress);

        this.socket = new DatagramSocket(null);
        this.socket.setReuseAddress(true);
        this.socket.bind(new InetSocketAddress(localPort));
        this.socket.connect(this.address, this.cameraPort);

        Map<String, ParameterState> parameters = new HashMap<>();

        this.socket.setSoTimeout(1000);

        this.controller = new ViscaPTZController(this, config);

        //====== CONFIGURATION ==========
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

        syncPosition();

        //DEBUG:
       /* System.out.println("============ CAMERA CONSTRUCTOR =============\nPARAMETERS LIST: " + this.parameters.keySet());
        System.out.println("PAN prameter: " + this.parameters.get("panPosition").toString());
        System.out.println("TILT prameter: " + this.parameters.get("tiltPosition").toString());*/
    }

    public void touch() {

        lastUpdateTime = System.currentTimeMillis();
    }

    /*
     * =========================================
     *               API kamery
     * =========================================
     */

    public void moveAbsolute(double pan, double tilt, int speed) {
        controller.moveAbsolute(pan, tilt,speed);
    }

    public void movePanTilt(int panValue, int tiltValue){
        controller.movePanTilt(panValue, tiltValue);
    }

    public void moveZoomAbsolute(int value) {
        controller.moveZoomAbsolute(value);
    }

    public void stop() {
        controller.stopPanTilt();
    }

    public void adjustExposureParameter(String parameterName, int direction){

        //DEBUG
        //System.out.println("============ PTZ CAMERA ============ \n"+
        //        "adjusting " + parameterName + "  Direction = " + direction);

        Map<String, Object> params = new HashMap<>();
        params.put("direction", direction > 0 ? "up" : "down");
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

    public boolean powerStatusRequest(){
        //DEBUG:
        //System.out.println("Camera " + this.cameraId + " power status request");
        return controller.powerStatusRequest();
    }

    public void executeViscaCommand (String commandName, Map<String, Object> params){
        controller.executeViscaCommand(commandName, params);
    }

    /*
     * =========================================
     *     GETTERS, SETTERS, HELPERS
     * ========================================= */

    public ParameterState getParameter(String parameterName) {

        ParameterState parameter = this.parameters.get(parameterName);

        if (parameter != null) {
            return parameter;
        }

        //Pobranie i aktualizacja stanu z kamery jeżeli nie ma w pamięci

        /*//DEBUG
        System.out.println(parameterName + "Parameter request send");*/
        controller.parameterInquiry(parameterName);

        //DEBUG
        /*System.out.println(parameterName + "Parameter Received. Name: " + parameterName + " Values: " + parameters.get(parameterName).toString());*/

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