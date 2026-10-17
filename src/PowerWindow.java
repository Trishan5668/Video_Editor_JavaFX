public class PowerWindow {
    private final String windowId;
    private double centerX;
    private double centerY;
    private double width;
    private double height;

    public PowerWindow(String windowId, double centerX, double centerY, double width, double height) {
        this.windowId = windowId;
        this.centerX = centerX;
        this.centerY = centerY;
        this.width = width;
        this.height = height;
    }

    public String getWindowId() {
        return windowId;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
}
