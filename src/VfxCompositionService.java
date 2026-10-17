import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VfxCompositionService {
    private final NodeGraph nodeGraph = new NodeGraph();
    private final List<VfxLayer> layers = new ArrayList<>();
    private final AnimationService animationService = new AnimationService();
    private final MotionAttachService motionAttachService = new MotionAttachService();
    private final ChromaKeySettings chromaKeySettings = new ChromaKeySettings();

    public NodeGraph getNodeGraph() {
        return nodeGraph;
    }

    public void addLayer(VfxLayer layer) {
        if (layer != null) {
            layers.add(layer);
        }
    }

    public List<VfxLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public List<VfxLayer> getEnabledLayersInRenderOrder() {
        List<VfxLayer> enabled = new ArrayList<>();
        for (VfxLayer layer : layers) {
            if (layer != null && layer.isEnabled()) {
                enabled.add(layer);
            }
        }
        enabled.sort((a, b) -> a.getLayerId().compareTo(b.getLayerId()));
        return enabled;
    }

    public AnimationService getAnimationService() {
        return animationService;
    }

    public MotionAttachService getMotionAttachService() {
        return motionAttachService;
    }

    public ChromaKeySettings getChromaKeySettings() {
        return chromaKeySettings;
    }
}
