public class FrameRate {
    private final double fps;

    public FrameRate(double fps) {
        if (fps <= 0) {
            throw new IllegalArgumentException("FPS must be > 0");
        }
        this.fps = fps;
    }

    public double getFps() {
        return fps;
    }

    public long toFrames(double seconds) {
        return Math.max(0, Math.round(seconds * fps));
    }

    public double toSeconds(long frames) {
        return Math.max(0, frames) / fps;
    }
}
