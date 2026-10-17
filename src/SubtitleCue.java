public class SubtitleCue {
    private final double startSeconds;
    private final double endSeconds;
    private final String text;

    public SubtitleCue(double startSeconds, double endSeconds, String text) {
        this.startSeconds = Math.max(0, startSeconds);
        this.endSeconds = Math.max(this.startSeconds, endSeconds);
        this.text = text == null ? "" : text;
    }

    public double getStartSeconds() {
        return startSeconds;
    }

    public double getEndSeconds() {
        return endSeconds;
    }

    public String getText() {
        return text;
    }
}
