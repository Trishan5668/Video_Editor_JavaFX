public class DynamicsProcessor implements AudioProcessor {
    private final String processorId;

    public DynamicsProcessor(String processorId) {
        this.processorId = processorId;
    }

    @Override
    public String getProcessorId() {
        return processorId;
    }

    @Override
    public void process(String inputBusId, String outputBusId) {
        // Placeholder for compressor/limiter/EQ/gate chain.
    }
}
