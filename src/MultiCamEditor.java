import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MultiCamEditor {
    private final List<MultiCamSource> sources = new ArrayList<>();
    private final List<MultiCamCut> cuts = new ArrayList<>();

    public void addSource(MultiCamSource source) {
        if (source != null) {
            sources.add(source);
        }
    }

    public List<MultiCamSource> getSources() {
        return Collections.unmodifiableList(sources);
    }

    public void addCut(long timelineFrame, String sourceId) {
        cuts.add(new MultiCamCut(Math.max(0, timelineFrame), sourceId));
        cuts.sort(Comparator.comparingLong(MultiCamCut::getTimelineFrame));
    }

    public List<MultiCamCut> getCuts() {
        return Collections.unmodifiableList(cuts);
    }

    public String resolveSourceAtFrame(long frame) {
        String active = null;
        for (MultiCamCut cut : cuts) {
            if (cut.getTimelineFrame() > frame) {
                break;
            }
            active = cut.getSourceId();
        }
        return active;
    }
}
