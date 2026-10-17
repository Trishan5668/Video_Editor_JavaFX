import java.util.HashMap;
import java.util.Map;

public class MotionTrackerService {
    private final Map<String, String> trackingDataPathByWindowId = new HashMap<>();

    public void trackWindow(PowerWindow window, String sourcePath) {
        if (window == null || sourcePath == null) {
            return;
        }
        // Placeholder for optical flow / AI tracking data generation output.
        trackingDataPathByWindowId.put(window.getWindowId(), sourcePath + ".tracking.json");
    }

    public String getTrackingDataPath(String windowId) {
        return trackingDataPathByWindowId.get(windowId);
    }
}
