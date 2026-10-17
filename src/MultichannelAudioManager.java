import java.util.HashMap;
import java.util.Map;

public class MultichannelAudioManager {
    private final Map<String, String> channelLayoutByBus = new HashMap<>();

    public void configureBus(String busId, String channelLayout) {
        if (busId == null || busId.isBlank()) {
            return;
        }
        channelLayoutByBus.put(busId, channelLayout == null ? "stereo" : channelLayout);
    }

    public String getChannelLayout(String busId) {
        return channelLayoutByBus.getOrDefault(busId, "stereo");
    }
}
