import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MetadataTagService {
    private final Map<String, Map<String, String>> tagsByAssetId = new HashMap<>();

    public void initializeAsset(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return;
        }
        tagsByAssetId.computeIfAbsent(assetId, ignored -> new HashMap<>());
    }

    public void setTag(String assetId, String key, String value) {
        if (assetId == null || key == null || key.isBlank()) {
            return;
        }
        initializeAsset(assetId);
        tagsByAssetId.get(assetId).put(key, value == null ? "" : value);
    }

    public Map<String, String> getTags(String assetId) {
        Map<String, String> tags = tagsByAssetId.get(assetId);
        if (tags == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(tags);
    }

    public Map<String, Map<String, String>> getAllTagsSnapshot() {
        Map<String, Map<String, String>> snapshot = new HashMap<>();
        tagsByAssetId.forEach((assetId, tags) -> snapshot.put(assetId, new HashMap<>(tags)));
        return Collections.unmodifiableMap(snapshot);
    }
}
