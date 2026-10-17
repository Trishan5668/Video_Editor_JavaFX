import java.util.HashMap;
import java.util.Map;

public class AnimationService {
    private final Map<String, KeyframeTrack> tracksByParameter = new HashMap<>();

    public KeyframeTrack getOrCreateTrack(String parameterName) {
        return tracksByParameter.computeIfAbsent(parameterName, KeyframeTrack::new);
    }

    public Map<String, KeyframeTrack> getTracksByParameter() {
        return Map.copyOf(tracksByParameter);
    }

    public double sampleParameterAtFrame(String parameterName, long frame) {
        KeyframeTrack track = tracksByParameter.get(parameterName);
        if (track == null) {
            return 0.0;
        }
        return track.sampleAtFrame(frame);
    }
}
