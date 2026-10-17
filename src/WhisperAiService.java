import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WhisperAiService {
    private final AiPythonRunner pythonRunner = new AiPythonRunner();

    public SubtitleGenerationResult generateSubtitles(
            String mediaPath,
            String sourceLanguage,
            String targetLanguage
    ) throws AiExecutionException {
        if (mediaPath == null || mediaPath.isBlank()) {
            throw new AiExecutionException("No media path provided for subtitle generation.");
        }

        Path input = ProjectPathResolver.resolvePath(mediaPath);
        if (!Files.exists(input)) {
            throw new AiExecutionException("Selected media does not exist: " + input);
        }

        ProjectPathResolver.ensureProjectStructure();
        String baseName = sanitizeBaseName(input.getFileName() == null ? "clip" : input.getFileName().toString());
        String outFileName = baseName + "_" + System.currentTimeMillis() + ".srt";
        Path outputSrt = ProjectPathResolver.subtitlesDir().resolve(outFileName);

        List<String> args = new ArrayList<>();
        args.add("--input");
        args.add(input.toString());
        args.add("--output");
        args.add(outputSrt.toString());
        args.add("--source-lang");
        args.add(normalizeLanguage(sourceLanguage));
        args.add("--target-lang");
        args.add(normalizeLanguage(targetLanguage));

        AiPythonRunner.AiRunResult run = pythonRunner.runScript("whisper_subtitles.py", args);
        if (run.exitCode() != 0 || run.fields().containsKey("ERROR")) {
            String details = run.fields().getOrDefault("ERROR", run.output());
            throw new AiExecutionException("Whisper subtitle generation failed: " + details);
        }

        String generated = run.fields().getOrDefault("OUTPUT", outputSrt.toString());
        Path generatedPath = ProjectPathResolver.resolvePath(generated);
        if (!Files.exists(generatedPath)) {
            throw new AiExecutionException("Whisper finished but subtitle file was not created.");
        }

        String language = run.fields().getOrDefault("LANGUAGE", normalizeLanguage(sourceLanguage));
        List<SubtitleCue> cues = parseSrt(generatedPath);
        return new SubtitleGenerationResult(ProjectPathResolver.toRelativeProjectPath(generatedPath), cues, language);
    }

    public String exportSubtitleTo(Path sourceSubtitle, Path destination) throws AiExecutionException {
        try {
            if (sourceSubtitle == null || destination == null) {
                throw new AiExecutionException("Invalid source or destination subtitle path.");
            }
            Path parent = destination.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(sourceSubtitle, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toString();
        } catch (IOException ex) {
            throw new AiExecutionException("Failed to export subtitle file.", ex);
        }
    }

    private List<SubtitleCue> parseSrt(Path srtPath) throws AiExecutionException {
        try {
            List<String> lines = Files.readAllLines(srtPath, StandardCharsets.UTF_8);
            List<SubtitleCue> cues = new ArrayList<>();
            int i = 0;
            while (i < lines.size()) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    i++;
                    continue;
                }

                if (line.matches("\\d+")) {
                    i++;
                    if (i >= lines.size()) {
                        break;
                    }
                }

                String timing = lines.get(i).trim();
                if (!timing.contains("-->")) {
                    i++;
                    continue;
                }
                String[] parts = timing.split("-->");
                double start = parseSrtTime(parts[0].trim());
                double end = parseSrtTime(parts[1].trim());
                i++;

                StringBuilder text = new StringBuilder();
                while (i < lines.size() && !lines.get(i).trim().isEmpty()) {
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    text.append(lines.get(i).trim());
                    i++;
                }

                if (text.length() > 0) {
                    cues.add(new SubtitleCue(start, end, text.toString()));
                }
            }
            return cues;
        } catch (Exception ex) {
            throw new AiExecutionException("Failed to parse SRT subtitles: " + srtPath, ex);
        }
    }

    private double parseSrtTime(String value) {
        String normalized = value.replace(',', '.');
        String[] hms = normalized.split(":");
        if (hms.length != 3) {
            return 0;
        }
        double h = safeDouble(hms[0]);
        double m = safeDouble(hms[1]);
        double s = safeDouble(hms[2]);
        return h * 3600 + m * 60 + s;
    }

    private double safeDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return 0;
        }
    }

    private String sanitizeBaseName(String value) {
        int dot = value.lastIndexOf('.');
        String base = dot > 0 ? value.substring(0, dot) : value;
        return base.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank() || "Original".equalsIgnoreCase(language)) {
            return "auto";
        }
        String v = language.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "english", "en" -> "en";
            case "hindi", "hi" -> "hi";
            case "spanish", "es" -> "es";
            case "french", "fr" -> "fr";
            case "german", "de" -> "de";
            case "japanese", "ja" -> "ja";
            default -> v;
        };
    }
}
