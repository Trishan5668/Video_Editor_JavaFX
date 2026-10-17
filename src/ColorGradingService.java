import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ColorGradingService {
    private final NodeGraph nodeGraph = new NodeGraph();
    private final ColorWheelSettings primaryWheels = new ColorWheelSettings();
    private final ColorWheelSettings secondaryWheels = new ColorWheelSettings();
    private final CurveEditorService curveEditorService = new CurveEditorService();
    private final ScopeMonitorService scopeMonitorService = new ScopeMonitorService();
    private final MotionTrackerService motionTrackerService = new MotionTrackerService();
    private final List<PowerWindow> powerWindows = new ArrayList<>();

    public NodeGraph getNodeGraph() {
        return nodeGraph;
    }

    public ColorWheelSettings getPrimaryWheels() {
        return primaryWheels;
    }

    public ColorWheelSettings getSecondaryWheels() {
        return secondaryWheels;
    }

    public CurveEditorService getCurveEditorService() {
        return curveEditorService;
    }

    public ScopeMonitorService getScopeMonitorService() {
        return scopeMonitorService;
    }

    public MotionTrackerService getMotionTrackerService() {
        return motionTrackerService;
    }

    public void addPowerWindow(PowerWindow powerWindow) {
        if (powerWindow != null) {
            powerWindows.add(powerWindow);
        }
    }

    public List<PowerWindow> getPowerWindows() {
        return Collections.unmodifiableList(powerWindows);
    }
}
