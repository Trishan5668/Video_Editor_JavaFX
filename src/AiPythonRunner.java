import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiPythonRunner {
    public record AiRunResult(int exitCode, String output, Map<String, String> fields) {
    }

    public AiRunResult runScript(String scriptFileName, List<String> args) throws AiExecutionException {
        try {
            ProjectPathResolver.ensureProjectStructure();
            Path scriptPath = ProjectPathResolver.aiScriptsDir().resolve(scriptFileName).toAbsolutePath().normalize();
            if (!Files.exists(scriptPath)) {
                throw new AiExecutionException("AI script not found: " + scriptPath);
            }

            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(scriptPath.toString());
            if (args != null) {
                command.addAll(args);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            int exit = process.waitFor();
            Map<String, String> fields = parseFields(output.toString());
            return new AiRunResult(exit, output.toString(), fields);
        } catch (AiExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiExecutionException("Failed to execute AI script: " + scriptFileName, ex);
        }
    }

    private Map<String, String> parseFields(String output) {
        Map<String, String> fields = new HashMap<>();
        if (output == null || output.isBlank()) {
            return fields;
        }
        String[] lines = output.split("\\R");
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            int idx = line.indexOf(':');
            if (idx <= 0 || idx >= line.length() - 1) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if (!key.isBlank()) {
                fields.put(key, value);
            }
        }
        return fields;
    }
}
