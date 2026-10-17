import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CurveEditorService {
    private final List<CurvePoint> saturationCurve = new ArrayList<>();
    private final List<CurvePoint> hueCurve = new ArrayList<>();
    private final List<CurvePoint> luminanceCurve = new ArrayList<>();

    public void addSaturationPoint(double x, double y) {
        saturationCurve.add(new CurvePoint(x, y));
    }

    public void addHuePoint(double x, double y) {
        hueCurve.add(new CurvePoint(x, y));
    }

    public void addLuminancePoint(double x, double y) {
        luminanceCurve.add(new CurvePoint(x, y));
    }

    public List<CurvePoint> getSaturationCurve() {
        return Collections.unmodifiableList(saturationCurve);
    }

    public List<CurvePoint> getHueCurve() {
        return Collections.unmodifiableList(hueCurve);
    }

    public List<CurvePoint> getLuminanceCurve() {
        return Collections.unmodifiableList(luminanceCurve);
    }
}
