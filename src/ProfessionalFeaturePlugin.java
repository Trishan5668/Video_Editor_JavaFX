public class ProfessionalFeaturePlugin implements EditorPlugin {
    private final String pluginId;
    private final String displayName;

    public ProfessionalFeaturePlugin(String pluginId, String displayName) {
        this.pluginId = pluginId;
        this.displayName = displayName;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void initialize(ProfessionalProject project) {
        // Marker plugin for future capability discovery.
    }
}
