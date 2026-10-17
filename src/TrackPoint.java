public class TrackPoint {
    private final long frame;
    private final double x;
    private final double y;

    public TrackPoint(long frame, double x, double y) {
        this.frame = Math.max(0, frame);
        this.x = x;
        this.y = y;
    }

    public long getFrame() {
        return frame;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
