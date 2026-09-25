package uci.bcd2ptz.ptz;

import uci.bcd2ptz.core.ControlCore.*;
import uci.bcd2ptz.visca.ViscaConfig;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PTZCameraManager {

    //ROUTING, ASSIGNMENTS, PANELS
    private final Map<String, PTZCamera> cameras = new ConcurrentHashMap<>();
    private final Map<ControlPanelId, PTZCamera> guiPanelAssignments = new ConcurrentHashMap<>();
    private final Map<ControlPanelId, PTZCamera> midiPanelAssignments = new ConcurrentHashMap<>();

    //UDP communication
    private final Map <String, PendingPacket> pendingPackets = new ConcurrentHashMap<>();
    private Thread receiverThread;
    private DatagramSocket socket = null;

    private static final Logger LOGGER = Logger.getLogger(PTZCameraManager.class.getName());

    public PTZCameraManager(String localIP, int localPort)  {

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
                    while (true) {

                        DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                        try {
                            socket.receive(packet);

                            String senderKey = packet.getAddress().getHostAddress() + ":" + packet.getPort();

                            PendingPacket pending = pendingPackets.get(senderKey);

                            if (pending != null) {
                                pending.getResponses().put(Arrays.copyOf(
                                                packet.getData(),
                                                packet.getLength()));
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

        pendingPackets.put(camera.getIpAddress() + ":" + camera.getCameraPort(), pending);

        socket.send(packet);
        return pending;
    }


    /* ==================================
              CAMERA MANAGEMENT
       ==================================*/

    public PTZCamera getCamera(
            String cameraId) {

        PTZCamera camera = cameras.get(cameraId);

        if (camera == null) {
            throw new IllegalArgumentException(
                    "Nie znaleziono kamery: "
                            + cameraId
            );
        }
        return camera;
    }

    public PTZCamera addCamera(String cameraId, String ipAddress, int cameraPort, int localPort, ViscaConfig config,
                               ControlPanelId assignedCorePanel, ControlPanelId assignedMidiPanel) throws Exception {

        PTZCamera camera = new PTZCamera(cameraId, ipAddress, cameraPort, localPort, config, this);

        camera.setIpAddress(ipAddress);
        camera.setCameraPort(cameraPort);

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

   /* public void assignMidiPanel(ControlPanelId panel, PTZCamera camera) {

        if (panel == null || camera == null) {
            return;
        }

        //DEBUG
        System.out.println("MIDI ASSIGNMENT: " + panel + " -> " + camera.getCameraId());

        // Existing binding control
        ControlPanelId currentPanel = null;

        for (Map.Entry<ControlPanelId, PTZCamera> entry : midiPanelAssignments.entrySet()) {
            if (camera.equals(entry.getValue())) {
                currentPanel = entry.getKey();
                break;
            }
        }

        // Rebinding
        if (currentPanel != null && currentPanel != panel) {
            midiPanelAssignments.remove(currentPanel);
        }

        //Target binding
        midiPanelAssignments.put(panel,camera);

        //DEBUG
        System.out.println("CURRENT MIDI BINDINGS: \n" + midiPanelAssignments.toString());
    }*/

    public void removeCamera(String cameraId) {
        cameras.remove(cameraId);
    }

    public PTZCamera getCoreAssignedCamera(ControlPanelId panel) {

        return guiPanelAssignments.get(panel);
    }

    /*public PTZCamera getMidiAssignedCamera(ControlPanelId panel) {

        return midiPanelAssignments.get(panel);
    }*/

    /*public String getMidiPanelBinding(ControlPanelId corePanel) {

        PTZCamera camera = guiPanelAssignments.get(corePanel);

        if (camera == null) {
            return null;
        }

        for (Map.Entry<ControlPanelId, PTZCamera> entry : midiPanelAssignments.entrySet()) {

            if (camera.equals(entry.getValue())) {
                return entry.getKey().name();
            }
        }

        return null;
    }*/

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

    /*public String getMidiAssignements(){
        return midiPanelAssignments.toString();
    }*/

    public String getCoreAssignements(){
        return guiPanelAssignments.toString();
    }
}