import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectArchiver {
    public ArchiveResult archiveProject(ProfessionalProject project, String archiveFolderPath) {
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null");
        }

        ProjectPathResolver.ensureProjectStructure();
        String archivePath = resolveArchivePath(archiveFolderPath);

        List<String> copiedAssets = new ArrayList<>();
        List<String> missingAssets = new ArrayList<>();
        for (ProjectAsset asset : project.getAssets().values()) {
            String relative = ProjectPathResolver.normalizeSeparators(asset.getSourcePath());
            if (ProjectPathResolver.exists(relative)) {
                copiedAssets.add(relative);
            } else {
                missingAssets.add(relative);
            }
        }

        // Keep archive references relative-only for portability.
        return new ArchiveResult(archivePath, copiedAssets, missingAssets);
    }

    private String resolveArchivePath(String archiveFolderPath) {
        String requested = (archiveFolderPath == null || archiveFolderPath.isBlank())
                ? "archives/project_archive"
                : archiveFolderPath;

        Path path = ProjectPathResolver.resolvePath(requested);
        String relative = ProjectPathResolver.toRelativeProjectPath(path);
        try {
            if (Path.of(relative).isAbsolute()) {
                String folderName = path.getFileName() == null ? "project_archive" : path.getFileName().toString();
                relative = ProjectPathResolver.normalizeSeparators(Path.of("archives", folderName).toString());
            }
        } catch (Exception ignored) {
            relative = "archives/project_archive";
        }

        try {
            Files.createDirectories(ProjectPathResolver.resolvePath(relative));
        } catch (Exception ignored) {
        }

        return ProjectPathResolver.normalizeSeparators(relative);
    }
}
