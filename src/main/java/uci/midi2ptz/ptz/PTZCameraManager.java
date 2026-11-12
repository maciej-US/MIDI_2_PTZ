package uci.midi2ptz.ptz;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import uci.midi2ptz.config.AppConfig;
import uci.midi2ptz.config.AppConfigManager;
import uci.midi2ptz.config.ConfigurationUpdateResults;
import uci.midi2ptz.core.MainControlCore.*;
import uci.midi2ptz.core.ControlEvent;
import uci.midi2ptz.core.ControlEventBus;
import uci.midi2ptz.visca.ViscaConfig;
import uci.midi2ptz.visca.ViscaPTZController;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static uci.midi2ptz.core.ControlEvent.EventSource.SYSTEM;

/**
 * Central PTZ camera management service.
 *
 * <p>This class is responsible for the lifecycle management of PTZ cameras, VISCA-over-IP communication and camera-to-control-panel assignments.</p>
 *
 * <p>The manager acts as the communication layer between application logic and physical PTZ devices. It maintains UDP transport resources,
 * camera instances and assignment mappings used by the runtime.</p>
 *
 * <h2>Main Responsibilities</h2> *
 * <ul>
 *     <li>PTZ camera creation and removal</li>
 *     <li>VISCA-over-IP communication transport management</li>
 *     <li>Camera configuration reload and synchronization</li>
 *     <li>Panel-to-camera routing</li>
 *     <li>Camera connectivity monitoring</li>
 *     <li>Runtime transport reconfiguration</li>
 * </ul>
 *
 * <h2>Communication Model</h2> *
 * <pre>
 * ControlCore
 *      |
 *      v
 * PTZCameraManager
 *      |
 *      v
 * DatagramSocket
 *      |
 *      v
 * PTZ Cameras
 * </pre>
 *
 * <p>A single UDP transport instance is shared between all managed cameras.</p>
 */
public class PTZCameraManager {

    //ROUTING, ASSIGNMENTS, PANELS
    @Getter
    private final Map<String, PTZCamera> cameras = new ConcurrentHashMap<>();
    private final Map<ControlPanelId, PTZCamera> guiPanelAssignments = new ConcurrentHashMap<>();
    @Getter
    private final Map<ControlPanelId, PTZCamera> midiPanelAssignments = new ConcurrentHashMap<>();
    @Setter
    private AppConfigManager appConfigManager;
    private final ControlEventBus inputBus;

    //UDP communication
    private final Map <String, PendingPacket> pendingPackets = new ConcurrentHashMap<>();
    private Thread receiverThread;
    @Getter
    private DatagramSocket socket = null;
    @Getter
    private String localIp;
    @Getter
    private int localPort;
    private boolean running = true;
    private boolean initialized;
    private final Map <String, CommunicationState> communicationStates = new ConcurrentHashMap<>();
    private static final int OFFLINE_THRESHOLD = 3;


    //STATUS
    private static final Logger LOGGER = Logger.getLogger(PTZCameraManager.class.getName());

    /**
     * Describes the state of the UDP transport layer.
     */
    public enum TransportState {
        ONLINE,             /**Transport socket is initialized and operational.*/
        OFFLINE,            /** Transport initialization failed or communication is currently unavailable.*/
        UNINITIALIZED       /** Transport has not yet been initialized.*/
    }

    private TransportState transportState;

    /**
     * Creates a new PTZ camera manager.
     *
     * @param eventBus application event bus used for camera status events
     * @param appConfigManager configuration manager
     */
    public PTZCameraManager(ControlEventBus eventBus, AppConfigManager appConfigManager)  {

        this.inputBus = eventBus;
        this.appConfigManager = appConfigManager;
        this.transportState = TransportState.UNINITIALIZED;
    }


    /**
     * Sends a VISCA-over-IP packet to a camera and registers  a pending response tracker.
     *
     * <p>The method associates the outgoing packet with a {@link PendingPacket} instance that will be completed asynchronously when a response arrives.</p>
     *
     * @param camera target camera
     * @param udpPacket encoded VISCA packet
     * @return pending packet tracking object
     *
     * @throws IOException communication error
     * @throws InterruptedException if the operation is interrupted
     */
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

    /**
     * Creates and registers a new PTZ camera instance.
     *
     * <p>The camera is added to the internal registry and  assigned to the configured control and MIDI panels.</p>
     *
     * @param cameraId unique camera identifier
     * @param ipAddress camera IP address
     * @param cameraPort camera VISCA port
     * @param localPort local transport port
     * @param socketTimeout communication timeout
     * @param displayName user-visible camera name
     * @param config VISCA protocol definition
     * @param assignedCorePanel logical control panel
     * @param assignedMidiPanel assigned MIDI panel
     *
     * @return created camera instance
     *
     * @throws Exception if camera initialization fails
     */
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
        return camera;
    }

    /**
     * Retrieves a registered camera.
     *
     * @param cameraId camera identifier
     * @return camera instance
     *
     * @throws IllegalArgumentException if the camera does not exist
     */
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

    /**
     * Removes a camera from the manager.
     *
     * <p>The camera is detached from all panel assignments and any pending communication resources are cleaned up.</p>
     *
     * @param cameraId identifier of the camera to remove
     */
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

        pendingPackets.remove(camera.getIpAddress() + ":" + camera.getCameraPort());
        guiPanelAssignments.values().removeIf(c -> c == camera);
        midiPanelAssignments.values().removeIf(c -> c == camera);
    }

    /**
     * Reloads camera definitions from the current application configuration.
     *
     * <p>The method creates, updates or removes camera instances to match the active configuration.</p>
     *
     * <p>All routing information, panel assignments and VISCA controller definitions are refreshed.</p>
     *
     * @return reconfiguration result
     */
    public ConfigurationUpdateResults reconfigureCameras() {

        LOGGER.info("reconfigureCameras(): state=" + transportState);

        if (!initialized){
            initialize();
        }
        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        if (transportState != TransportState.ONLINE){
            LOGGER.warning("Transport is OFFLINE. Camera update unavailable");
            results.getErrors().put(AppConfigManager.ConfigurationSection.CAMERA, "Transport is OFFLINE, update unavailable");
            return results;
        }

        Set<String> existingCameras = new HashSet<>(cameras.keySet());

        guiPanelAssignments.clear();
        midiPanelAssignments.clear();

        ObjectMapper viscaMapper = new ObjectMapper();

        for (AppConfig.CameraConfig cameraConfig : appConfigManager.getConfig().getSystem().getCameras()) {

            try {
                ControlPanelId corePanel = ControlPanelId.valueOf(cameraConfig.getAssignedGuiPanel());

                ControlPanelId midiPanel = null;
                if (cameraConfig.getAssignedMidiPanel() != null && !cameraConfig.getAssignedMidiPanel().isBlank()) {
                    midiPanel = ControlPanelId.valueOf(cameraConfig.getAssignedMidiPanel());
                }

                ViscaConfig viscaConfig = viscaMapper.readValue(new File(cameraConfig.getViscaDefinition()),ViscaConfig.class);
                long socketTimeout = Math.max(appConfigManager.getConfig().getSystem().getTransport().getSocketTimeout(),200);

                addCamera(
                        cameraConfig.getCameraId(),
                        cameraConfig.getIpAddress(),
                        cameraConfig.getPort(),
                        localPort,
                        (int)socketTimeout,
                        cameraConfig.getDisplayName(),
                        viscaConfig,
                        corePanel,
                        midiPanel
                );

                existingCameras.remove(cameraConfig.getCameraId()
                );

            }catch (SocketException se){
                LOGGER.warning("Transport error. Switching to OFFLINE MODE.");
                transportState = TransportState.OFFLINE;
            }
            catch (Exception e) {
                results.addError(AppConfigManager.ConfigurationSection.CAMERA, "Unable to reconfigure camera: " + cameraConfig.getCameraId()
                        + "\n ERROR: " + e.getMessage());

                LOGGER.warning("Unable to configure camera " + cameraConfig.getCameraId() + ": " + e.getMessage());
                transportState = TransportState.OFFLINE;
            }
        }

        for (String cameraId : existingCameras) {
            removeCamera(cameraId);
        }

        LOGGER.info("Camera configuration reloaded.");
        return results;
    }

    /**
     * Retrieves the camera assigned to a logical control panel.
     *
     * @param panel control panel identifier
     * @return assigned camera or {@code null}
     */
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

    /**
     * Performs a camera connectivity test using the provided VISCA configuration.
     *
     * @param ip target camera IP address
     * @param port target camera port
     * @param configPath VISCA definition file
     *
     * @return {@code true} if communication succeeds
     */
    public boolean testConnection(String ip,int port,String configPath){

        LOGGER.info("TESTING CONNECTION for IP " + ip + ":" + port + "\nCONFIG FILE: " + configPath);

        try{
            return ViscaPTZController.testConnection(this, ip, port, configPath);

        }catch (Exception e){
            LOGGER.warning("TESTING CONNECTION for IP " + ip + ":" + port + "\nCONFIG FILE: " + configPath + "FAILED. \n" + e.getMessage());
        }
        return false;
    }

    /**
     * Determines whether the camera assigned to the specified panel is currently online.
     *
     * @param panel control panel identifier
     * @return {@code true} if the assigned camera is reachable
     */
    public boolean isCameraOnline (ControlPanelId panel) {

        PTZCamera camera = getCoreAssignedCamera(panel);

        if (camera == null) {
            System.out.println ("NO ASSIGNED CAMERA PANEL: " + panel);
            return false;
        }
        return camera.isOnline();
    }

    /**
     * Returns a human-readable description of the camera assigned to a panel.
     *
     * @param panel control panel identifier
     * @return camera description or empty string
     */
    public String getCameraDescription (ControlPanelId panel){

        PTZCamera camera = getCoreAssignedCamera(panel);

        if (camera == null) {
            return "";
        }
        return camera.getDescription();

    }


    public void communicationReport(String cameraId, boolean success) {

        PTZCamera camera = cameras.get(cameraId);

        if (camera == null) {
            return;
        }

        CommunicationState state = communicationStates.computeIfAbsent(
                        cameraId,
                        id -> new CommunicationState());

        if (success) {
            state.setConsecutiveErrors(0);
            state.lastSuccesfullCommunication = System.currentTimeMillis();
            if (!camera.isOnline()) {
                LOGGER.info("Camera " + cameraId + " communication restored.");
                camera.markOnline();

                notifyCameraStatusChanged(camera, true);
            }
            return;
        }

        int errors = state.getConsecutiveErrors() + 1;
        state.setConsecutiveErrors(errors);
        state.lastErrorTimestamp = System.currentTimeMillis();
        LOGGER.warning("Camera " + cameraId + " communication error " + errors  + "/" + OFFLINE_THRESHOLD);

        if (errors < OFFLINE_THRESHOLD) {
            return;
        }

        if (camera.isOnline()) {
            LOGGER.warning( "Camera " + cameraId + " marked OFFLINE.");
            camera.markOffline();
            notifyCameraStatusChanged(camera,false);
            inputBus.publish(new ControlEvent(SYSTEM,
                    getAssignedCorePanel(camera),
                    "CLEAR_PENDING_EVENTS",
                    0));
        }
    }

    /**
     * Publishes a camera status update event.
     *
     * <p>The generated event is forwarded to the application event bus and may be processed by the GUI, MIDI layer
     * or control runtime.</p>
     *
     * @param camera affected camera
     * @param online new online status
     */
    public void notifyCameraStatusChanged(PTZCamera camera, boolean online) {

        ControlPanelId panel = getAssignedCorePanel(camera);

        ControlEvent event =
                new ControlEvent(
                        SYSTEM,
                        panel,
                        "UPDATE_CAMERA_STATUS",
                        online ? 1 : 0
                );

        //DEBUG
        //System.out.println("CAMERA STATUS NOTIFICATION EVENT: " + event);

        inputBus.publish(event);
    }

    /**
     * Verifies camera connectivity and attempts automatic recovery.
     *
     * <p>The method performs a power status check and may send a power-on command if the camera is reachable but currently powered off.</p>
     *
     * @param panelId target panel
     * @return {@code true} if the camera becomes operational
     */
    public boolean refreshConnection(ControlPanelId panelId) {

        PTZCamera camera = getCoreAssignedCamera(panelId);

        if (camera == null) {
            LOGGER.warning("No camera assigned to panel " + panelId);
            return false;
        }

        try {
            System.out.println(
                    "SEND TO "
                            + camera.getIpAddress()
                            + ":"
                            + camera.getCameraPort()
            );
            int powerStatus = camera.powerStatusRequest();

            switch (powerStatus) {
                case -1:
                    LOGGER.warning("Camera " + camera.getCameraId() + " does not respond.");
                    communicationReport(camera.getCameraId(), false);
                    return false;

                case 0:
                    LOGGER.info("Camera " + camera.getCameraId() + " is powered OFF. Sending POWER ON.");
                    camera.setPower(true);
                    Thread.sleep(1000);
                    powerStatus = camera.powerStatusRequest();
                    if (powerStatus == 1) {
                        communicationReport(camera.getCameraId(), true);
                        return true;
                    }

                    communicationReport(camera.getCameraId(), false);
                    return false;

                case 1:
                    communicationReport(camera.getCameraId(), true);
                    return true;

                default:
                    LOGGER.warning("Unknown power status for camera " + camera.getCameraId() + ": " + powerStatus);
                    communicationReport(camera.getCameraId(), false);
                    return false;
            }

        } catch (Exception e) {
            communicationReport(camera.getCameraId(), false);
            LOGGER.warning("Refresh connection failed for " + camera.getCameraId());
            return false;
        }
    }

    /**
     * Initializes camera transport and communication services.
     *
     * <p>The transport layer is created, the packet receiver thread is started and configured cameras are loaded.</p>
     */
    public void initialize(){

        if (initialized){
            return;
        }

        initializeTransport(appConfigManager.getConfig());

        if (transportState != TransportState.ONLINE){
            LOGGER.info("Camera Manager in OFFLINE mode.");
            return;
        }

        this.receiverThread = new Thread(() -> {
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
                        }catch (SocketException e){
                            if (!running){
                                break;
                            }
                        }
                        catch (IOException e){
                            LOGGER.warning("Communication error for camera on " + packet.getAddress() +":" + packet.getPort());
                        }catch (Exception e){
                            LOGGER.warning("Packet error for camera " + packet.getAddress() +":" + packet.getPort());
                        }
                    }
                }, "VISCA_RX");

        this.receiverThread.setDaemon(true);
        this.receiverThread.start();

        initialized = true;

        if (transportState != TransportState.ONLINE) {
            LOGGER.info("Camera Manager started in OFFLINE mode.");
            return;
        }

        ConfigurationUpdateResults results = reconfigureCameras();

        if (!results.getErrors().isEmpty()) {
            LOGGER.warning("CAMERA configuration errors: " + results.getErrors());
        }
    }

    /**
     * Initializes the UDP transport layer using the supplied application configuration.
     *
     * @param config active configuration
     */
    public void initializeTransport (AppConfig config){

        DatagramSocket socket = null;
        try{
            socket = validateTransport(config);
        }catch (Exception e){
            LOGGER.warning("Socket Error: " + e.getMessage());
            transportState = TransportState.OFFLINE;
        }

        if (socket != null){
            updateTransportSocket(socket);
            localIp = socket.getLocalAddress().getHostAddress();
            localPort = socket.getLocalPort();
            transportState = TransportState.ONLINE;
        }
    }

    /**
     * Validates transport configuration and creates a testable UDP socket instance.
     * <p>The socket is created and bound but is not installed as the active manager transport.</p>
     *
     * @param config candidate configuration
     * @return prepared socket
     *
     * @throws Exception if transport initialization fails
     */
    public static DatagramSocket validateTransport(AppConfig config) throws Exception {


        String localIp = String.valueOf(config.getSystem().getTransport().getLocalIp());

        int localPort = config.getSystem().getTransport().getLocalPort();
        if (localPort <= 0){
            localPort = 52381;
        }

        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(localIp),localPort));

        return socket;
    }

    /**
     * Replaces the active transport socket.     *
     * <p>The previous socket is closed after the new socket has been successfully installed.</p>
     *
     * @param newSocket new transport socket
     * @return operation result
     */
    public ConfigurationUpdateResults updateTransportSocket(DatagramSocket newSocket) {

        ConfigurationUpdateResults results = new ConfigurationUpdateResults();

        try {
            if (newSocket == null) {
                throw new IllegalArgumentException("New socket is null.");
            }

            DatagramSocket oldSocket = this.socket;

            this.socket = newSocket;

            if (oldSocket != null && !oldSocket.isClosed()) {
                oldSocket.close();
            }

        } catch (Exception e) {
            results.getErrors().put(
                    AppConfigManager.ConfigurationSection.TRANSPORT,
                    e.getMessage()
            );
        }

        return results;
    }

    private void shutdownTransport() {

        this.running = false;

        if (this.socket != null && !this.socket.isClosed()) {
            this.socket.close();
        }

        if (this.receiverThread != null) {

            try {
                this.receiverThread.join(1000);
            } catch (InterruptedException ignored) {
            }

            this.receiverThread = null;
        }
        initialized = false;

        System.out.println("PTZ Manager Transport SOCKET CLOSED");
    }

    /**
     * Stops all managed camera activity and shuts down transport resources.
     *
     * <p>Each configured camera is first instructed to move to its configured park position, tally lamp off and disable autoFollow mode
     * before UDP resources are released.</p>
     */
    public void shutdown(){

        running = false;
        for (PTZCamera camera : cameras.values()){
            try{
                camera.parkCamera();
                camera.tally(0);
                camera.autoFollow(0);
            }catch (Exception e){
                LOGGER.warning("Unable to stop camera: " + camera.getCameraId());
            }
        }

        shutdownTransport();
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
    }

    @Data
    public class CommunicationState{
        private int consecutiveErrors;
        private long lastSuccesfullCommunication;
        private long lastErrorTimestamp;
    }

}