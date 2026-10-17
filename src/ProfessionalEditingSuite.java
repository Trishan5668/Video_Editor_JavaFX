public class ProfessionalEditingSuite {
    private final ColorGradingService colorGradingService = new ColorGradingService();
    private final AudioPostService audioPostService = new AudioPostService();
    private final VfxCompositionService vfxCompositionService = new VfxCompositionService();

    public ColorGradingService getColorGradingService() {
        return colorGradingService;
    }

    public AudioPostService getAudioPostService() {
        return audioPostService;
    }

    public VfxCompositionService getVfxCompositionService() {
        return vfxCompositionService;
    }
}
