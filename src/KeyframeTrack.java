import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class KeyframeTrack {
    private final String parameterName;
    private final List<Keyframe> keyframes = new ArrayList<>();

    public KeyframeTrack(String parameterName) {
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }

    public void addKeyframe(Keyframe keyframe) {
        if (keyframe == null) {
            return;
        }
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingLong(Keyframe::getFrame));
    }

    public List<Keyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }

    public double sampleAtFrame(long frame) {
        if (keyframes.isEmpty()) {
            return 0.0;
        }
        if (keyframes.size() == 1 || frame <= keyframes.get(0).getFrame()) {
            return keyframes.get(0).getValue();
        }

        Keyframe previous = keyframes.get(0);
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe next = keyframes.get(i);
            if (frame <= next.getFrame()) {
                return interpolate(previous, next, frame);
            }
            previous = next;
        }
        return keyframes.get(keyframes.size() - 1).getValue();
    }

    private double interpolate(Keyframe start, Keyframe end, long frame) {
        long span = Math.max(1, end.getFrame() - start.getFrame());
        double t = Math.max(0, Math.min(1, (frame - start.getFrame()) / (double) span));
        double eased = applyEasing(t, end.getEasingType());
        return start.getValue() + ((end.getValue() - start.getValue()) * eased);
    }

    private double applyEasing(double t, EasingType easingType) {
        return switch (easingType == null ? EasingType.LINEAR : easingType) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1 - Math.pow(1 - t, 2);
            case EASE_IN_OUT -> t < 0.5
                    ? 2 * t * t
                    : 1 - Math.pow(-2 * t + 2, 2) / 2;
        };
    }
}
