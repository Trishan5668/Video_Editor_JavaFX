public class AudioDuckingProcessor implements AudioProcessor {
    private final String processorId;
    private double thresholdDb = -18.0;
    private double ratio = 4.0;

    public AudioDuckingProcessor(String processorId) {
        this.processorId = processorId;
    }

    @Override
    public String getProcessorId() {
        return processorId;
    }

    @Override
    public void process(String inputBusId, String outputBusId) {
        // Placeholder for sidechain compression implementation.
    }

    public double getThresholdDb() {
        return thresholdDb;
    }

    public void setThresholdDb(double thresholdDb) {
        this.thresholdDb = thresholdDb;
    }

    public double getRatio() {
        return ratio;
    }

    public void setRatio(double ratio) {
        this.ratio = ratio;
    }
}
