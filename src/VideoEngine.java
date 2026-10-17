public interface VideoEngine {

    void loadVideo(String path);

    void play();

    void pause();

    void seek(double seconds);

    void renderTimeline(TimelineModel timeline, String outputPath) throws Exception;

    /**
     * Professional pipeline entry-point.
     * Current engines can keep using the classic timeline path and override this when ready.
     */
    default void renderProfessionalProject(ProfessionalProject project, ExportRequest request) throws Exception {
        throw new UnsupportedOperationException("Professional project rendering is not implemented for this engine yet.");
    }

    /**
     * Allows engines to opt into selecting a specific GPU/CPU rendering backend.
     */
    default void setRenderBackend(RenderBackendType backendType) {
        // Optional for engines that support backend selection.
    }

    // TODO: Replace Java implementation with JNI bridge for a native C++ engine.
    // TODO: Keep this interface stable so UI layer remains unchanged during migration.
}