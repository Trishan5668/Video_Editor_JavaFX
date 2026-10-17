import java.util.EnumSet;
import java.util.Set;

public class ScopeMonitorService {
    private final Set<ColorScopeType> enabledScopes = EnumSet.noneOf(ColorScopeType.class);

    public void enableScope(ColorScopeType scopeType) {
        if (scopeType != null) {
            enabledScopes.add(scopeType);
        }
    }

    public void disableScope(ColorScopeType scopeType) {
        if (scopeType != null) {
            enabledScopes.remove(scopeType);
        }
    }

    public Set<ColorScopeType> getEnabledScopes() {
        return Set.copyOf(enabledScopes);
    }
}
