import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Sequence {
    private final String sequenceId;
    private String name;
    private final TimelineModel timelineModel;
    private final FrameRate frameRate;
    private final List<String> nestedSequenceIds = new ArrayList<>();
    private final MultiCamEditor multiCamEditor = new MultiCamEditor();
    private final PrecisionTrimService precisionTrimService = new PrecisionTrimService();

    public Sequence(String sequenceId, String name, FrameRate frameRate) {
        this(sequenceId, name, new TimelineModel(), frameRate);
    }

    public Sequence(String sequenceId, String name, TimelineModel timelineModel, FrameRate frameRate) {
        this.sequenceId = sequenceId;
        this.name = name == null ? "Untitled Sequence" : name;
        this.timelineModel = timelineModel == null ? new TimelineModel() : timelineModel;
        this.frameRate = frameRate == null ? new FrameRate(30.0) : frameRate;
    }

    public String getSequenceId() {
        return sequenceId;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName) {
        this.name = newName == null || newName.isBlank() ? this.name : newName;
    }

    public TimelineModel getTimelineModel() {
        return timelineModel;
    }

    public FrameRate getFrameRate() {
        return frameRate;
    }

    public void embedSequence(String nestedSequenceId) {
        if (nestedSequenceId == null || nestedSequenceId.isBlank()) {
            return;
        }
        if (!nestedSequenceIds.contains(nestedSequenceId)) {
            nestedSequenceIds.add(nestedSequenceId);
        }
    }

    public List<String> getNestedSequenceIds() {
        return Collections.unmodifiableList(nestedSequenceIds);
    }

    public MultiCamEditor getMultiCamEditor() {
        return multiCamEditor;
    }

    public PrecisionTrimService getPrecisionTrimService() {
        return precisionTrimService;
    }
}
