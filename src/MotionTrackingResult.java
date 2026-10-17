import java.util.Collections;
import java.util.List;

public class MotionTrackingResult {
    private final String subjectId;
    private final List<TrackPoint> trackPoints;
    private final String maskOutputPath;

    public MotionTrackingResult(String subjectId, List<TrackPoint> trackPoints, String maskOutputPath) {
        this.subjectId = subjectId == null ? "" : subjectId;
        this.trackPoints = trackPoints == null ? List.of() : List.copyOf(trackPoints);
        this.maskOutputPath = maskOutputPath == null ? "" : maskOutputPath;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public List<TrackPoint> getTrackPoints() {
        return Collections.unmodifiableList(trackPoints);
    }

    public String getMaskOutputPath() {
        return maskOutputPath;
    }
}
