import java.util.HashMap;
import java.util.Map;

public class ProxyMediaManager {
    private final Map<String, String> rawAssetPathById = new HashMap<>();
    private final Map<String, String> proxyAssetPathById = new HashMap<>();
    private boolean preferProxyForEditing = true;

    public void registerRawAsset(String assetId, String rawPath) {
        if (assetId == null || assetId.isBlank()) {
            return;
        }
        rawAssetPathById.put(assetId, rawPath);
    }

    public void registerProxyAsset(String assetId, String proxyPath) {
        if (assetId == null || assetId.isBlank()) {
            return;
        }
        proxyAssetPathById.put(assetId, proxyPath);
    }

    public String resolvePathForEditing(String assetId) {
        if (preferProxyForEditing && proxyAssetPathById.containsKey(assetId)) {
            return proxyAssetPathById.get(assetId);
        }
        return rawAssetPathById.get(assetId);
    }

    public String resolvePathForFinalExport(String assetId) {
        return rawAssetPathById.getOrDefault(assetId, proxyAssetPathById.get(assetId));
    }

    public void setPreferProxyForEditing(boolean preferProxyForEditing) {
        this.preferProxyForEditing = preferProxyForEditing;
    }

    /**
     * UI-facing getter used by preview/timeline controls.
     */
    public boolean isPreferProxyForEditing() {
        return preferProxyForEditing;
    }
}
