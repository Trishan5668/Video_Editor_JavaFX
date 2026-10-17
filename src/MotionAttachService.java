import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MotionAttachService {
    private final Map<String, List<TrackPoint>> trackingDataBySubjectId = new HashMap<>();
    private final Map<String, String> attachmentBySubjectId = new HashMap<>();

    public void setTrackingData(String subjectId, List<TrackPoint> points) {
        if (subjectId == null || subjectId.isBlank() || points == null) {
            return;
        }
        trackingDataBySubjectId.put(subjectId, new ArrayList<>(points));
    }

    public void attach(String subjectId, String attachmentId) {
        if (subjectId == null || attachmentId == null) {
            return;
        }
        attachmentBySubjectId.put(subjectId, attachmentId);
    }

    public List<TrackPoint> getTrackingData(String subjectId) {
        List<TrackPoint> points = trackingDataBySubjectId.get(subjectId);
        return points == null ? Collections.emptyList() : Collections.unmodifiableList(points);
    }

    public String getAttachment(String subjectId) {
        return attachmentBySubjectId.get(subjectId);
    }
}
