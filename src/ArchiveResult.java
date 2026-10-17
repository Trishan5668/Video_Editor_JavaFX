import java.util.Collections;
import java.util.List;

public class ArchiveResult {
    private final String archivePath;
    private final List<String> copiedAssets;
    private final List<String> missingAssets;

    public ArchiveResult(String archivePath, List<String> copiedAssets) {
        this(archivePath, copiedAssets, Collections.emptyList());
    }

    public ArchiveResult(String archivePath, List<String> copiedAssets, List<String> missingAssets) {
        this.archivePath = archivePath;
        this.copiedAssets = copiedAssets == null ? Collections.emptyList() : List.copyOf(copiedAssets);
        this.missingAssets = missingAssets == null ? Collections.emptyList() : List.copyOf(missingAssets);
    }

    public String getArchivePath() {
        return archivePath;
    }

    public List<String> getCopiedAssets() {
        return copiedAssets;
    }

    public List<String> getMissingAssets() {
        return missingAssets;
    }
}
