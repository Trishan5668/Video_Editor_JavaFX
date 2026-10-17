public class ChromaKeySettings {
    private double hueCenter;
    private double hueRange;
    private double edgeSoftness;
    private double spillSuppression;

    public double getHueCenter() {
        return hueCenter;
    }

    public void setHueCenter(double hueCenter) {
        this.hueCenter = hueCenter;
    }

    public double getHueRange() {
        return hueRange;
    }

    public void setHueRange(double hueRange) {
        this.hueRange = Math.max(0, hueRange);
    }

    public double getEdgeSoftness() {
        return edgeSoftness;
    }

    public void setEdgeSoftness(double edgeSoftness) {
        this.edgeSoftness = Math.max(0, edgeSoftness);
    }

    public double getSpillSuppression() {
        return spillSuppression;
    }

    public void setSpillSuppression(double spillSuppression) {
        this.spillSuppression = Math.max(0, spillSuppression);
    }
}
