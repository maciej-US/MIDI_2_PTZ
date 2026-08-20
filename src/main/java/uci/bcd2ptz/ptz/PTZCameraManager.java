package uci.bcd2ptz.ptz;

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
            int port,
            String viscaConfigFile) throws Exception {

        PTZCamera camera =
                new PTZCamera(cameraId, ipAddress, port, viscaConfigFile);

        camera.setIpAddress(ipAddress);
        camera.setPort(port);

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