import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudioPostService {
    private final List<AudioProcessor> processors = new ArrayList<>();
    private final MultichannelAudioManager multichannelAudioManager = new MultichannelAudioManager();
    private final FoleyLibraryService foleyLibraryService = new FoleyLibraryService();

    public void addProcessor(AudioProcessor processor) {
        if (processor != null) {
            processors.add(processor);
        }
    }

    public List<AudioProcessor> getProcessors() {
        return Collections.unmodifiableList(processors);
    }

    public MultichannelAudioManager getMultichannelAudioManager() {
        return multichannelAudioManager;
    }

    public FoleyLibraryService getFoleyLibraryService() {
        return foleyLibraryService;
    }
}
