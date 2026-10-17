import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Centralized project path resolver.
 *
 * All runtime paths are resolved relative to the project root (user.dir), so the
 * project folder can be moved/shared without breaking media links or binaries.
 */
public final class ProjectPathResolver {

    private ProjectPathResolver() {
    }

    public static Path projectRoot() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public static Path assetsDir() {
        return projectRoot().resolve("assets");
    }

    public static Path exportsDir() {
        return projectRoot().resolve("exports");
    }

    public static Path binDir() {
        return projectRoot().resolve("bin");
    }

    public static Path archivesDir() {
        return projectRoot().resolve("archives");
    }

    public static Path aiDir() {
        return projectRoot().resolve("ai");
    }

    public static Path aiScriptsDir() {
        return aiDir().resolve("scripts");
    }

    public static Path subtitlesDir() {
        return exportsDir().resolve("subtitles");
    }

    public static Path projectJsonPath() {
        return projectRoot().resolve("project.json");
    }

    public static void ensureProjectStructure() {
        try {
            Files.createDirectories(assetsDir());
            Files.createDirectories(exportsDir());
            Files.createDirectories(binDir());
            Files.createDirectories(archivesDir());
            Files.createDirectories(aiScriptsDir());
            Files.createDirectories(subtitlesDir());
        } catch (IOException ignored) {
        }
    }

    public static Path resolvePath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return projectRoot();
        }
        Path raw = Paths.get(storedPath);
        return raw.isAbsolute() ? raw.normalize() : projectRoot().resolve(raw).normalize();
    }

    public static boolean exists(String storedPath) {
        return Files.exists(resolvePath(storedPath));
    }

    public static String toRelativeProjectPath(String anyPath) {
        if (anyPath == null || anyPath.isBlank()) {
            return "";
        }
        return toRelativeProjectPath(Paths.get(anyPath));
    }

    public static String toRelativeProjectPath(Path anyPath) {
        if (anyPath == null) {
            return "";
        }
        Path absolute = anyPath.toAbsolutePath().normalize();
        Path root = projectRoot();
        try {
            if (absolute.startsWith(root)) {
                return normalizeSeparators(root.relativize(absolute).toString());
            }
        } catch (Exception ignored) {
        }
        return normalizeSeparators(absolute.toString());
    }

    public static String fileNameOf(String storedPath) {
        Path p = resolvePath(storedPath);
        Path fileName = p.getFileName();
        return fileName == null ? storedPath : fileName.toString();
    }

    public static String importIntoAssets(File sourceFile) throws IOException {
        if (sourceFile == null) {
            throw new IOException("No source file selected.");
        }
        ensureProjectStructure();

        Path source = sourceFile.toPath().toAbsolutePath().normalize();
        if (!Files.exists(source)) {
            throw new IOException("Selected file does not exist: " + sourceFile.getName());
        }

        Path assets = assetsDir();
        String originalName = source.getFileName().toString();
        Path destination = assets.resolve(originalName);

        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        String ext = dot > 0 ? originalName.substring(dot) : "";

        int suffix = 1;
        while (Files.exists(destination) && !Files.isSameFile(source, destination)) {
            destination = assets.resolve(base + "_" + suffix + ext);
            suffix++;
        }

        if (!Files.exists(destination) || !Files.isSameFile(source, destination)) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        return normalizeSeparators(projectRoot().relativize(destination).toString());
    }

    public static String resolveBundledFfmpegExecutable() {
        ensureProjectStructure();

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String exeName;
        if (os.contains("win")) {
            exeName = "ffmpeg.exe";
        } else if (os.contains("mac")) {
            exeName = "ffmpeg-macos";
        } else {
            exeName = "ffmpeg-linux";
        }

        Path preferred = binDir().resolve(exeName);
        if (Files.exists(preferred)) {
            return preferred.toString();
        }

        Path fallback = binDir().resolve(os.contains("win") ? "ffmpeg.exe" : "ffmpeg");
        return fallback.toString();
    }

    public static String normalizeSeparators(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }
}
