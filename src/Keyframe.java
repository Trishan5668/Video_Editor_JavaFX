public class Keyframe {
    private final long frame;
    private final double value;
    private final EasingType easingType;

    public Keyframe(long frame, double value, EasingType easingType) {
        this.frame = Math.max(0, frame);
        this.value = value;
        this.easingType = easingType == null ? EasingType.LINEAR : easingType;
    }

    public long getFrame() {
        return frame;
    }

    public double getValue() {
        return value;
    }

    public EasingType getEasingType() {
        return easingType;
    }
}
