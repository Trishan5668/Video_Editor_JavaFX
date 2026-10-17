public class MultiCamCut {
    private final long timelineFrame;
    private final String sourceId;

    public MultiCamCut(long timelineFrame, String sourceId) {
        this.timelineFrame = timelineFrame;
        this.sourceId = sourceId;
    }

    public long getTimelineFrame() {
        return timelineFrame;
    }

    public String getSourceId() {
        return sourceId;
    }
}
