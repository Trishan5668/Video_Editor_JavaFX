public class VfxLayer {
    private final String layerId;
    private String sourceId;
    private BlendMode blendMode = BlendMode.NORMAL;
    private boolean enabled = true;

    public VfxLayer(String layerId, String sourceId) {
        this.layerId = layerId;
        this.sourceId = sourceId;
    }

    public String getLayerId() {
        return layerId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public BlendMode getBlendMode() {
        return blendMode;
    }

    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode == null ? BlendMode.NORMAL : blendMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
