import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PySceneDetectService {
    private final AiPythonRunner pythonRunner = new AiPythonRunner();

    public SceneDetectionResult detectScenes(String mediaPath, double threshold) throws AiExecutionException {
        if (mediaPath == null || mediaPath.isBlank()) {
            throw new AiExecutionException("No media provided for Auto-Cut scene detection.");
        }
        Path input = ProjectPathResolver.resolvePath(mediaPath);
        if (!Files.exists(input)) {
            throw new AiExecutionException("Selected clip not found: " + input);
        }

        double safeThreshold = Math.max(5.0, Math.min(80.0, threshold));
        List<String> args = new ArrayList<>();
        args.add("--input");
        args.add(input.toString());
        args.add("--threshold");
        args.add(String.format(java.util.Locale.US, "%.2f", safeThreshold));

        AiPythonRunner.AiRunResult run = pythonRunner.runScript("scene_detect.py", args);
        if (run.exitCode() != 0 || run.fields().containsKey("ERROR")) {
            String details = run.fields().getOrDefault("ERROR", run.output());
            throw new AiExecutionException("PySceneDetect failed: " + details);
        }

        String cutsRaw = run.fields().getOrDefault("CUTS", "");
        List<Double> cuts = parseCuts(cutsRaw);
        return new SceneDetectionResult(cuts);
    }

    private List<Double> parseCuts(String value) {
        List<Double> cuts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return cuts;
        }
        for (String token : value.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                double v = Double.parseDouble(t);
                if (v > 0) {
                    cuts.add(v);
                }
            } catch (Exception ignored) {
            }
        }
        return cuts;
    }
}
