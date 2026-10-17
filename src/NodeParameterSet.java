import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NodeParameterSet {
    private final Map<String, Double> numericParams = new HashMap<>();

    public void set(String key, double value) {
        if (key == null || key.isBlank()) {
            return;
        }
        numericParams.put(key, value);
    }

    public double getOrDefault(String key, double fallback) {
        return numericParams.getOrDefault(key, fallback);
    }

    public Map<String, Double> asMap() {
        return Collections.unmodifiableMap(numericParams);
    }
}
