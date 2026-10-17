public interface EditorPlugin {
    String getPluginId();
    String getDisplayName();
    void initialize(ProfessionalProject project);
}
