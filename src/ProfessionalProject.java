import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public class ProfessionalProject {
    private final String projectId;
    private String name;
    private final Map<String, ProjectAsset> assets = new HashMap<>();
    private final Map<String, Sequence> sequences = new HashMap<>();
    private final List<ProjectSnapshot> snapshots = new ArrayList<>();
    private final List<EditorPlugin> plugins = new ArrayList<>();

    private final ProxyMediaManager proxyMediaManager = new ProxyMediaManager();
    private final MetadataTagService metadataTagService = new MetadataTagService();
    private final CollaborationManager collaborationManager = new CollaborationManager();
    private final ProjectArchiver projectArchiver = new ProjectArchiver();
    private final ProfessionalEditingSuite professionalEditingSuite = new ProfessionalEditingSuite();

    private String activeSequenceId;

    public ProfessionalProject(String name) {
        this.projectId = UUID.randomUUID().toString();
        this.name = name == null || name.isBlank() ? "Untitled Project" : name;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
    }

    public void addSequence(Sequence sequence) {
        if (sequence == null) {
            return;
        }
        sequences.put(sequence.getSequenceId(), sequence);
        if (activeSequenceId == null) {
            activeSequenceId = sequence.getSequenceId();
        }
    }

    public Sequence getActiveSequence() {
        if (activeSequenceId == null) {
            return null;
        }
        return sequences.get(activeSequenceId);
    }

    public void setActiveSequence(String sequenceId) {
        if (sequenceId != null && sequences.containsKey(sequenceId)) {
            this.activeSequenceId = sequenceId;
        }
    }

    public Map<String, Sequence> getSequences() {
        return Collections.unmodifiableMap(sequences);
    }

    public void registerAsset(ProjectAsset asset) {
        if (asset == null || asset.getAssetId() == null) {
            return;
        }
        assets.put(asset.getAssetId(), asset);
        proxyMediaManager.registerRawAsset(asset.getAssetId(), asset.getSourcePath());
        metadataTagService.initializeAsset(asset.getAssetId());
        saveProjectMetadata();
    }

    public Map<String, ProjectAsset> getAssets() {
        return Collections.unmodifiableMap(assets);
    }

    public ProjectSnapshot createSnapshot(String label) {
        Sequence active = getActiveSequence();
        if (active == null) {
            throw new IllegalStateException("Cannot create snapshot without an active sequence");
        }
        ProjectSnapshot snapshot = new ProjectSnapshot(label, active.getTimelineModel());
        snapshots.add(snapshot);
        return snapshot;
    }

    public boolean rollbackToSnapshot(String snapshotId) {
        Sequence active = getActiveSequence();
        if (active == null || snapshotId == null) {
            return false;
        }
        for (ProjectSnapshot snapshot : snapshots) {
            if (snapshot.getSnapshotId().equals(snapshotId)) {
                active.getTimelineModel().resetFrom(snapshot.getTimelineState());
                return true;
            }
        }
        return false;
    }

    public List<ProjectSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
    }

    public void registerPlugin(EditorPlugin plugin) {
        if (plugin == null) {
            return;
        }
        plugins.add(plugin);
        plugin.initialize(this);
    }

    public List<EditorPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public ProxyMediaManager getProxyMediaManager() {
        return proxyMediaManager;
    }

    public MetadataTagService getMetadataTagService() {
        return metadataTagService;
    }

    public CollaborationManager getCollaborationManager() {
        return collaborationManager;
    }

    public ProjectArchiver getProjectArchiver() {
        return projectArchiver;
    }

    public ProfessionalEditingSuite getProfessionalEditingSuite() {
        return professionalEditingSuite;
    }

    public void saveProjectMetadata() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"projectId\": \"").append(escape(projectId)).append("\",\n");
        json.append("  \"name\": \"").append(escape(name)).append("\",\n");
        json.append("  \"assets\": [\n");

        List<ProjectAsset> assetList = new ArrayList<>(assets.values());
        for (int i = 0; i < assetList.size(); i++) {
            ProjectAsset asset = assetList.get(i);
            json.append("    {\"assetId\": \"").append(escape(asset.getAssetId())).append("\", ")
                    .append("\"sourcePath\": \"").append(escape(ProjectPathResolver.normalizeSeparators(asset.getSourcePath()))).append("\", ")
                    .append("\"mediaType\": \"").append(asset.getMediaType().name()).append("\"}");
            if (i < assetList.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ],\n");

        json.append("  \"activeSequenceId\": \"").append(escape(activeSequenceId)).append("\",\n");

        json.append("  \"sequences\": [\n");
        List<Sequence> sequenceList = new ArrayList<>(sequences.values());
        for (int i = 0; i < sequenceList.size(); i++) {
            Sequence seq = sequenceList.get(i);
            json.append("    {\"sequenceId\": \"").append(escape(seq.getSequenceId())).append("\", ")
                    .append("\"name\": \"").append(escape(seq.getName())).append("\", ")
                    .append("\"fps\": ").append(seq.getFrameRate().getFps()).append(", ")
                    .append("\"tracks\": [");

            List<Track> tracks = seq.getTimelineModel().getTracks();
            for (int t = 0; t < tracks.size(); t++) {
                Track track = tracks.get(t);
                json.append("{\"type\": \"").append(track.getType().name()).append("\", \"clips\": [");
                List<Clip> clips = track.getClips();
                for (int c = 0; c < clips.size(); c++) {
                    Clip clip = clips.get(c);
                    json.append("{\"clipId\": \"").append(escape(clip.getClipId())).append("\", ")
                            .append("\"sourcePath\": \"").append(escape(ProjectPathResolver.normalizeSeparators(clip.getSourcePath()))).append("\", ")
                            .append("\"start\": ").append(clip.getStartSeconds()).append(", ")
                            .append("\"end\": ").append(clip.getEndSeconds()).append(", ")
                            .append("\"timelineStart\": ").append(clip.getTimelineStartSeconds()).append(", ")
                            .append("\"positionX\": ").append(clip.getPositionX()).append(", ")
                            .append("\"positionY\": ").append(clip.getPositionY()).append(", ")
                            .append("\"scale\": ").append(clip.getScale()).append(", ")
                            .append("\"opacity\": ").append(clip.getOpacity()).append(", ")
                            .append("\"zIndex\": ").append(clip.getZIndex()).append(", ")
                            .append("\"text\": \"").append(escape(clip.getTextContent())).append("\", ")
                            .append("\"fontSize\": ").append(clip.getTextFontSize()).append("}");
                    if (c < clips.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]}");
                if (t < tracks.size() - 1) {
                    json.append(",");
                }
            }
            json.append("]}");
            if (i < sequenceList.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ],\n");

        json.append("  \"tags\": {\n");
        List<String> assetIds = new ArrayList<>(metadataTagService.getAllTagsSnapshot().keySet());
        for (int i = 0; i < assetIds.size(); i++) {
            String assetId = assetIds.get(i);
            json.append("    \"").append(escape(assetId)).append("\": {");
            Map<String, String> tags = metadataTagService.getAllTagsSnapshot().get(assetId);
            List<String> keys = new ArrayList<>(tags.keySet());
            for (int k = 0; k < keys.size(); k++) {
                String key = keys.get(k);
                json.append("\"").append(escape(key)).append("\": \"").append(escape(tags.get(key))).append("\"");
                if (k < keys.size() - 1) {
                    json.append(", ");
                }
            }
            json.append("}");
            if (i < assetIds.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  }\n");
        json.append("}\n");

        try {
            ProjectPathResolver.ensureProjectStructure();
            Files.writeString(ProjectPathResolver.projectJsonPath(), json.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
