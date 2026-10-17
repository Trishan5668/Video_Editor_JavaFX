import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FoleyLibraryService {
    private final List<String> soundEffectPaths = new ArrayList<>();

    public void addEffect(String path) {
        if (path != null && !path.isBlank()) {
            soundEffectPaths.add(path);
        }
    }

    public List<String> getEffects() {
        return Collections.unmodifiableList(soundEffectPaths);
    }
}
