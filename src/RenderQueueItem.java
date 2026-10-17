public class RenderQueueItem {
    private final String preset;
    private final String output;
    private String status;

    public RenderQueueItem(String preset, String output, String status) {
        this.preset = preset;
        this.output = output;
        this.status = status;
    }

    public String getPreset() {
        return preset;
    }

    public String getOutput() {
        return output;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return preset + " -> " + output + " [" + status + "]";
    }
}
