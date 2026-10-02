package uci.bcd2ptz.ptz;

import uci.bcd2ptz.core.ControlCore.*;
import uci.bcd2ptz.core.ControlEvent;
import uci.bcd2ptz.core.ControlEventBus;
import uci.bcd2ptz.visca.ViscaConfig;
import uci.bcd2ptz.visca.ViscaPTZController;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static uci.bcd2ptz.core.ControlEvent.EventSource.SYSTEM;

public class PTZCameraManager {

    //ROUTING, ASSIGNMENTS, PANELS
    private final Map<String, PTZCamera> cameras = new ConcurrentHashMap<>();
    private final Map<ControlPanelId, PTZCamera> guiPanelAssignments = new ConcurrentHashMap<>();
    private final Map<ControlPanelId, PTZCamera> midiPanelAssignments = new ConcurrentHashMap<>();

    //UDP communication
    private final Map <String, PendingPacket> pendingPackets = new ConcurrentHashMap<>();
    private Thread receiverThread;
    private DatagramSocket socket = null;
    private String localIP;
    private int localPort;
    private boolean running = true;

    private static final Logger LOGGER = Logger.getLogger(PTZCameraManager.class.getName());
    private ControlEventBus inputBus;

    public PTZCameraManager(String localIP, int localPort, ControlEventBus eventBus)  {

        this.inputBus = eventBus;
        this.localIP = localIP;
        this.localPort = localPort;

        try {
            this.socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind (new InetSocketAddress(
                    InetAddress.getByName(localIP),
                    localPort
            ));
        }catch (Exception e){
            System.err.println("Unable to establish socket for IP " + localIP + ":" + localPort);
        }

        this.receiverThread =
                new Thread(() -> {
                    while (running) {

                        DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                        try {
                            socket.receive(packet);

                            String senderKey = packet.getAddress().getHostAddress() + ":" + packet.getPort();

                            PendingPacket pending = pendingPackets.get(senderKey);

                            if (pending != null) {
                                pending.getResponses().put(Arrays.copyOf(
                                                packet.getData(),
                                                packet.getLength()));
                                pending.markResponseReceived();
                                pendingPackets.remove(senderKey);
                            }
                        }catch (IOException e){
                            LOGGER.warning("Communication error for camera on " + packet.getAddress() +":" + packet.getPort());
                        }catch (Exception e){
                            LOGGER.warning("Packet error for camera " + packet.getAddress() +":" + packet.getPort());
                        }

                    }

                }, "VISCA_RX");

        this.receiverThread.setDaemon(true);
        this.receiverThread.start();
    }

    /* ==================================
                 UDP TRAFFIC
       ==================================*/

    public PendingPacket sendPacket(PTZCamera camera, byte[] udpPacket) throws IOException, InterruptedException {

        synchronized (camera.getTransactionLock()) {
            DatagramPacket packet =
                    new DatagramPacket(
                            udpPacket,
                            udpPacket.length,
                            InetAddress.getByName(
                                    camera.getIpAddress()
                            ),
                            camera.getCameraPort()
                    );

            PendingPacket pending = new PendingPacket(
                    udpPacket,
                    camera.getIpAddress(),
                    camera.getCameraPort()
            );

            try {
                pendingPackets.put(camera.getIpAddress() + ":" + camera.getCameraPort(), pending);
                socket.send(packet);
                return pending;
            } finally{}
        }
    }


    /* ==================================
              CAMERA MANAGEMENT
       ==================================*/

    public PTZCamera getCamera(String cameraId) {

        PTZCamera camera = cameras.get(cameraId);

        if (camera == null) {
            throw new IllegalArgumentException(
                    "Nie znaleziono kamery: "
                            + cameraId
            );
        }
        return camera;
    }

    public PTZCamera addCamera(String cameraId, String ipAddress, int cameraPort, int localPort, int socketTimeout, String displayName, ViscaConfig config,
                               ControlPanelId assignedCorePanel, ControlPanelId assignedMidiPanel) throws Exception {

        PTZCamera camera = new PTZCamera(cameraId, ipAddress, cameraPort, localPort, socketTimeout, displayName, config, this);

        camera.setIpAddress(ipAddress);
        camera.setCameraPort(cameraPort);
        camera.markOnline();

        cameras.put(cameraId, camera);
        guiPanelAssignments.put(assignedCorePanel, camera);

        //MIDI assignment
        if (assignedMidiPanel != null){
            midiPanelAssignments.put(assignedMidiPanel, camera);
        }
        //DEBUG:
        /*System.out.println("CAM=" + cameraId + " GUI=" + assignedGuiPanel + " MIDI=" + assignedMidiPanel);*/

        return camera;
    }

    public void assignCorePanel(ControlPanelId panel, PTZCamera camera) {

        guiPanelAssignments.put(panel, camera);
    }

    public void removeCamera(String cameraId) {

        PTZCamera camera = cameras.remove(cameraId);

        if (camera == null) {
            return;
        }

        try{
            camera.stopPanTilt();
        }catch (Exception e){
            LOGGER.warning("Unable to stop camera: " + cameraId);
        }

        pendingPackets.remove(camera.getCameraId() + ":" + camera.getCameraPort());
        guiPanelAssignments.values().removeIf(c -> c == camera);
        midiPanelAssignments.values().removeIf(c -> c == camera);
    }

    public PTZCamera getCoreAssignedCamera(ControlPanelId panel) {

        return guiPanelAssignments.get(panel);
    }

    private ControlPanelId getAssignedCorePanel(PTZCamera camera) {

        for (Map.Entry<ControlPanelId, PTZCamera> entry
                : guiPanelAssignments.entrySet()) {

            if (entry.getValue() == camera) {
                return entry.getKey();
            }
        }

        return null;
    }

    public boolean isCameraOnline (ControlPanelId panel) {

        PTZCamera camera = getCoreAssignedCamera(panel);

        if (camera == null) {
            System.out.println ("NO ASSIGNED CAMERA PANEL: " + panel);
            return false;
        }
        return camera.isOnline();
    }

    public boolean testConnection(String ip,int port,String configPath){

        LOGGER.info("TESTING CONNECTION for IP " + ip + ":" + port + "\nCONFIG FILE: " + configPath);

        try{
            return ViscaPTZController.testConnection(this, ip, port, configPath);

        }catch (Exception e){
            LOGGER.warning("TESTING CONNECTION for IP " + ip + ":" + port + "\nCONFIG FILE: " + configPath + "FAILED. \n" + e.getMessage());
        }


        return true;
    }

    public String CorePanelBinding(ControlPanelId midiPanel) {

        PTZCamera camera = midiPanelAssignments.get(midiPanel);

        if (camera == null) {
            return null;
        }

        for (Map.Entry<ControlPanelId, PTZCamera> entry : guiPanelAssignments.entrySet()) {

            if (camera.equals(entry.getValue())) {
                return entry.getKey().name();
            }
        }

        return null;
    }

    public Map<String, PTZCamera> getCameras() {
        return cameras;
    }

    public String getCoreAssignements(){
        return guiPanelAssignments.toString();
    }

    public String getCameraDescription (ControlPanelId panel){

        PTZCamera camera = getCoreAssignedCamera(panel);

        if (camera == null) {
            return "";
        }
        return camera.getDescription();

    }

    public void notifyCameraStatusChanged(PTZCamera camera, boolean online) {

        //DEBUG
        //System.out.println("STATUS EVENT => "+ camera.getCameraId() + " ONLINE=" + online + " CALLER=" + Thread.currentThread().getStackTrace()[2]);

        ControlPanelId panel = getAssignedCorePanel(camera);

        ControlEvent event =
                new ControlEvent(
                        SYSTEM,
                        panel,
                        "UPDATE_CAMERA_STATUS",
                        online ? 1 : 0
                );

        this.inputBus.publish(event);
    }

    public boolean refreshConnection(ControlPanelId panelId) {

        PTZCamera camera = getCoreAssignedCamera(panelId);

        if (camera == null) {
            LOGGER.warning("No camera assigned to panel " + panelId);
            return false;
        }

        try {
            int powerStatus = camera.powerStatusRequest();

            switch (powerStatus) {
                case -1:
                    LOGGER.warning("Camera " + camera.getCameraId() + " does not respond.");
                    camera.markOffline();
                    notifyCameraStatusChanged(camera,false);
                    return false;

                case 0:
                    LOGGER.info("Camera " + camera.getCameraId() + " is powered OFF. Sending POWER ON.");
                    camera.setPower(true);
                    Thread.sleep(1000);
                    powerStatus = camera.powerStatusRequest();
                    if (powerStatus == 1) {
                        camera.markOnline();
                        notifyCameraStatusChanged(camera,true);
                        return true;
                    }

                    camera.markOffline();
                    notifyCameraStatusChanged(camera,false);
                    return false;

                case 1:
                    camera.markOnline();
                    notifyCameraStatusChanged(camera,true);
                    return true;

                default:
                    LOGGER.warning("Unknown power status for camera " + camera.getCameraId() + ": " + powerStatus);
                    camera.markOffline();
                    notifyCameraStatusChanged(camera,false);
                    return false;
            }

        } catch (Exception e) {
            camera.markOffline();
            notifyCameraStatusChanged(camera,false);
            LOGGER.warning("Refresh connection failed for " + camera.getCameraId());
            return false;
        }
    }

    public void shutdown(){
        running = false;
        for (PTZCamera camera : cameras.values()){
            try{
                camera.parkCamera();
            }catch (Exception e){
                LOGGER.warning("Unable to stop camera: " + camera.getCameraId());
            }
        }
        socket.close();
        receiverThread.interrupt();
    }

    public int getLocalPort(){
        return this.localPort;
    }

    public String getLocalIp(){
        return this.localIP;
    }

}