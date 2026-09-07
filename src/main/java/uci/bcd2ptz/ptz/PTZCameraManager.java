package uci.bcd2ptz.ptz;

import uci.bcd2ptz.visca.ViscaConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PTZCameraManager {

    private final Map<String, PTZCamera> cameras =
            new ConcurrentHashMap<>();

    public PTZCamera getCamera(
            String cameraId) {

        PTZCamera camera =
                cameras.get(cameraId);

        if (camera == null) {

            throw new IllegalArgumentException(
                    "Nie znaleziono kamery: "
                            + cameraId
            );
        }

        return camera;
    }

    public PTZCamera addCamera(
            String cameraId,
            String ipAddress,
            int cameraPort,
            int localPort,
            ViscaConfig config) throws Exception {

        PTZCamera camera =
                new PTZCamera(cameraId, ipAddress, cameraPort, localPort, config);

        camera.setIpAddress(ipAddress);
        camera.setCameraPort(cameraPort);

        cameras.put(cameraId, camera);

        return camera;
    }

    public void removeCamera(
            String cameraId) {

        cameras.remove(cameraId);
    }

    public Map<String, PTZCamera> getCameras() {

        return cameras;
    }


    public boolean hasCamera(
            String cameraId) {

        return cameras.containsKey(cameraId);
    }
}