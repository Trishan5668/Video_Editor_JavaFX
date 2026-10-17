public class MultiCamSource {
    private final String sourceId;
    private final String assetId;
    private final long syncOffsetFrames;

    public MultiCamSource(String sourceId, String assetId, long syncOffsetFrames) {
        this.sourceId = sourceId;
        this.assetId = assetId;
        this.syncOffsetFrames = syncOffsetFrames;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getAssetId() {
        return assetId;
    }

    public long getSyncOffsetFrames() {
        return syncOffsetFrames;
    }
}
