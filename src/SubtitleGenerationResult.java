import java.util.Collections;
import java.util.List;

public class SubtitleGenerationResult {
    private final String subtitlePath;
    private final List<SubtitleCue> cues;
    private final String detectedLanguage;

    public SubtitleGenerationResult(String subtitlePath, List<SubtitleCue> cues, String detectedLanguage) {
        this.subtitlePath = subtitlePath == null ? "" : subtitlePath;
        this.cues = cues == null ? List.of() : List.copyOf(cues);
        this.detectedLanguage = detectedLanguage == null ? "" : detectedLanguage;
    }

    public String getSubtitlePath() {
        return subtitlePath;
    }

    public List<SubtitleCue> getCues() {
        return Collections.unmodifiableList(cues);
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }
}
