import java.time.Instant;
import java.util.UUID;

public class ProjectSnapshot {
    private final String snapshotId;
    private final Instant createdAt;
    private final String label;
    private final TimelineModel timelineState;

    public ProjectSnapshot(String label, TimelineModel timelineState) {
        this.snapshotId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.label = label == null ? "Auto Snapshot" : label;
        this.timelineState = TimelineModelCloner.deepCopy(timelineState);
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLabel() {
        return label;
    }

    public TimelineModel getTimelineState() {
        return TimelineModelCloner.deepCopy(timelineState);
    }
}
