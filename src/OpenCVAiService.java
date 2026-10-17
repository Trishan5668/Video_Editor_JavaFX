import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OpenCVAiService {
    private final AiPythonRunner pythonRunner = new AiPythonRunner();

    public MotionTrackingResult trackMotion(
            String mediaPath,
            boolean detectFaces,
            boolean enableChromaKey,
            String backgroundReplacementPath
    ) throws AiExecutionException {
        if (mediaPath == null || mediaPath.isBlank()) {
            throw new AiExecutionException("No clip selected for motion tracking.");
        }
        Path input = ProjectPathResolver.resolvePath(mediaPath);
        if (!Files.exists(input)) {
            throw new AiExecutionException("Selected clip does not exist: " + input);
        }

        String base = baseName(input);
        Path maskOut = ProjectPathResolver.exportsDir().resolve(base + "_mask_" + System.currentTimeMillis() + ".mp4");

        List<String> args = new ArrayList<>();
        args.add("--input");
        args.add(input.toString());
        args.add("--detect");
        args.add(detectFaces ? "face" : "object");
        args.add("--chroma");
        args.add(enableChromaKey ? "1" : "0");
        args.add("--output-mask");
        args.add(maskOut.toString());
        if (backgroundReplacementPath != null && !backgroundReplacementPath.isBlank()) {
            args.add("--bg");
            args.add(ProjectPathResolver.resolvePath(backgroundReplacementPath).toString());
        }

        AiPythonRunner.AiRunResult run = pythonRunner.runScript("opencv_motion.py", args);
        if (run.exitCode() != 0 || run.fields().containsKey("ERROR")) {
            String details = run.fields().getOrDefault("ERROR", run.output());
            throw new AiExecutionException("OpenCV tracking failed: " + details);
        }

        String subject = run.fields().getOrDefault("SUBJECT", "subject-" + System.currentTimeMillis());
        List<TrackPoint> points = parseTrackPoints(run.fields().getOrDefault("POINTS", ""));
        String mask = run.fields().getOrDefault("MASK", ProjectPathResolver.toRelativeProjectPath(maskOut));
        return new MotionTrackingResult(subject, points, mask);
    }

    private List<TrackPoint> parseTrackPoints(String packed) {
        List<TrackPoint> points = new ArrayList<>();
        if (packed == null || packed.isBlank()) {
            return points;
        }
        String[] tuples = packed.split("\\|");
        for (String tuple : tuples) {
            String[] parts = tuple.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                long frame = Long.parseLong(parts[0].trim());
                double x = Double.parseDouble(parts[1].trim());
                double y = Double.parseDouble(parts[2].trim());
                points.add(new TrackPoint(frame, x, y));
            } catch (Exception ignored) {
            }
        }
        return points;
    }

    private String baseName(Path path) {
        String file = path.getFileName() == null ? "clip" : path.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = dot > 0 ? file.substring(0, dot) : file;
        return base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }
}
