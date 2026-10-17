import java.util.Collections;
import java.util.List;

public class SceneDetectionResult {
    private final List<Double> cutPointsSeconds;

    public SceneDetectionResult(List<Double> cutPointsSeconds) {
        this.cutPointsSeconds = cutPointsSeconds == null ? List.of() : List.copyOf(cutPointsSeconds);
    }

    public List<Double> getCutPointsSeconds() {
        return Collections.unmodifiableList(cutPointsSeconds);
    }
}
