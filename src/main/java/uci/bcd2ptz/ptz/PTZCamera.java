package uci.bcd2ptz.ptz;

import lombok.Data;
import uci.bcd2ptz.visca.FieldDefinition;
import uci.bcd2ptz.visca.ViscaPTZController;

import java.net.DatagramSocket;
import java.net.InetAddress;

@Data
public class PTZCamera {

    private final String cameraId;

    private String ipAddress;

    private int port;

    private InetAddress address;

    private DatagramSocket socket;

    private int viscaSessionID = 1;

    private double pan;
    private double tilt;
    private double zoom;

    private boolean online;

    private long lastUpdateTime;

    /*
     * Debugging / diagnostyka
     */
    private volatile int panVisca;

    private volatile int tiltVisca;

    private volatile int zoomVisca;

    /*
     * Sterownik przypisany do tej kamery
     */
    private final ViscaPTZController controller;

    public PTZCamera(
            String cameraId,
            String ipAddress,
            int port,
            String viscaConfigFile)
            throws Exception {

        this.cameraId = cameraId;

        this.ipAddress = ipAddress;
        this.port = port;

        this.address =
                InetAddress.getByName(
                        ipAddress
                );

        this.socket =
                new DatagramSocket();

        this.socket.setSoTimeout(1000);

        this.controller =
                new ViscaPTZController(
                        this,
                        viscaConfigFile
                );
    }

    public void touch() {

        lastUpdateTime =
                System.currentTimeMillis();
    }

    /*
     * =========================================
     *               API kamery
     * =========================================
     */

    public void moveAbsolute(
            double pan,
            double tilt,
            int speed) {

        controller.moveAbsolute(
                pan,
                tilt,
                speed
        );
    }

    public void movePanTilt(
            int panValue,
            int tiltValue){

        controller.movePanTilt(panValue, tiltValue);
    }

    /**DEPRECATED */
    public void movePan(
            int value) {

        controller.pan(value);
    }

    /**DEPRECATED */
    public void moveTilt(
            int value) {

        controller.moveTilt(value);
    }

    public void moveZoom(
            int value) {

        controller.zoom(value);
    }

    public void recallPreset(
            int preset) {

        controller.recallPreset(
                preset
        );
    }

    public void syncPosition(
            Runnable onComplete) {

        controller.syncPosition(
                onComplete
        );
    }

    public void stop() {

        controller.stopPanTilt();
    }

    /*
     * =========================================
     *     GETTERY I SETTERY
     * ========================================= */

    public FieldDefinition getFieldDefinition(
            String fieldName) {

        return controller.getFieldDefinition(
                fieldName
        );
    }

    @Override
    public String toString() {

        return String.format(
                "PTZCamera[id=%s, ip=%s:%d, pan=%.2f, tilt=%.2f, zoom=%.2f, online=%s]",
                cameraId,
                ipAddress,
                port,
                pan,
                tilt,
                zoom,
                online
        );
    }
}