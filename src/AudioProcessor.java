public interface AudioProcessor {
    String getProcessorId();
    void process(String inputBusId, String outputBusId);
}
