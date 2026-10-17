import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ProjectAsset {
    private final String assetId;
    private final String sourcePath;
    private final MediaType mediaType;
    private final Map<String, String> metadata;

    public ProjectAsset(String assetId, String sourcePath, MediaType mediaType) {
        this(assetId, sourcePath, mediaType, new HashMap<>());
    }

    public ProjectAsset(String assetId, String sourcePath, MediaType mediaType, Map<String, String> metadata) {
        this.assetId = assetId;
        this.sourcePath = sourcePath;
        this.mediaType = mediaType == null ? MediaType.VIDEO : mediaType;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    public String getAssetId() {
        return assetId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
}
