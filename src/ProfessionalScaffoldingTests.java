import java.util.List;

public class ProfessionalScaffoldingTests {

    public static void main(String[] args) {
        testSnapshotsAndRollback();
        testProxyWorkflowResolution();
        testMultiCamEditingCuts();
        testPluginAndSuiteRegistration();
        testPrecisionRazorTrim();
        testTimelinePlacementPersistence();
        testRepeatedTimelineMutations();
        testKeyframeInterpolationSampling();
        testMetadataAndCollaboration();
        testArchiverCollection();
        testVfxAndColorAudioServices();
        System.out.println("All professional scaffolding tests passed.");
    }

    private static void testSnapshotsAndRollback() {
        ProfessionalProject project = new ProfessionalProject("Demo");
        Sequence sequence = new Sequence("seq-1", "Main", new FrameRate(30));
        project.addSequence(sequence);

        Clip clipA = new Clip("a.mp4", 0, 10);
        sequence.getTimelineModel().addClip(clipA);
        ProjectSnapshot snap = project.createSnapshot("before-change");

        sequence.getTimelineModel().addClip(new Clip("b.mp4", 0, 4));
        assertTrue(sequence.getTimelineModel().getClips().size() == 2, "Expected 2 clips before rollback");

        boolean rolledBack = project.rollbackToSnapshot(snap.getSnapshotId());
        assertTrue(rolledBack, "Snapshot rollback should succeed");
        assertTrue(sequence.getTimelineModel().getClips().size() == 1, "Rollback should restore 1 clip");
    }

    private static void testProxyWorkflowResolution() {
        ProxyMediaManager manager = new ProxyMediaManager();
        manager.registerRawAsset("asset-1", "raw_8k.mov");
        manager.registerProxyAsset("asset-1", "proxy_1080p.mp4");

        assertTrue("proxy_1080p.mp4".equals(manager.resolvePathForEditing("asset-1")), "Proxy path should be used for editing by default");
        manager.setPreferProxyForEditing(false);
        assertTrue("raw_8k.mov".equals(manager.resolvePathForEditing("asset-1")), "Raw path should be used after toggle");
        assertTrue("raw_8k.mov".equals(manager.resolvePathForFinalExport("asset-1")), "Raw should be used for final export");
    }

    private static void testMultiCamEditingCuts() {
        MultiCamEditor multiCamEditor = new MultiCamEditor();
        multiCamEditor.addSource(new MultiCamSource("camA", "a", 0));
        multiCamEditor.addSource(new MultiCamSource("camB", "b", 0));
        multiCamEditor.addCut(0, "camA");
        multiCamEditor.addCut(120, "camB");

        assertTrue("camA".equals(multiCamEditor.resolveSourceAtFrame(60)), "CamA should be active at frame 60");
        assertTrue("camB".equals(multiCamEditor.resolveSourceAtFrame(180)), "CamB should be active at frame 180");
    }

    private static void testPluginAndSuiteRegistration() {
        ProfessionalProject project = new ProfessionalProject("Plugin Test");
        project.registerPlugin(new ProfessionalFeaturePlugin("pro.core", "Professional Core"));
        assertTrue(project.getPlugins().size() == 1, "Plugin should be registered");
        assertTrue(project.getProfessionalEditingSuite().getAudioPostService() != null, "Audio post service should exist");
    }

    private static void testPrecisionRazorTrim() {
        Sequence sequence = new Sequence("seq-2", "Trim", new FrameRate(30));
        sequence.getTimelineModel().addClip(new Clip("cut.mp4", 0, 10).withTimelineStart(5));
        sequence.getPrecisionTrimService().applyTrim(
                sequence.getTimelineModel(),
                0,
                0,
                TrimMode.RAZOR,
                150,
                sequence.getFrameRate()
        );
        assertTrue(sequence.getTimelineModel().getClips().size() == 2, "Razor trim should split clip into two");
        assertTrue(sequence.getTimelineModel().getClips().get(0).getTimelineStartSeconds() == 5.0,
                "Left split should preserve original timeline start");
        assertTrue(sequence.getTimelineModel().getClips().get(1).getTimelineStartSeconds() == 10.0,
                "Right split should advance to the cut point on the timeline");
    }

    private static void testTimelinePlacementPersistence() {
        TimelineModel timeline = new TimelineModel();
        timeline.addClip(new Clip("b.mp4", 0, 3).withTimelineStart(8));
        timeline.addClip(new Clip("a.mp4", 0, 4).withTimelineStart(2));

        assertTrue("a.mp4".equals(timeline.getClips().get(0).getSourcePath()), "Earlier timeline clip should sort first");
        assertTrue(timeline.computeTrackEndSeconds(0) == 11.0, "Track end should use persisted timeline placement");
    }

    private static void testRepeatedTimelineMutations() {
        Sequence sequence = new Sequence("seq-stress", "Stress", new FrameRate(30));
        TimelineModel timeline = sequence.getTimelineModel();
        timeline.addClip(new Clip("stress.mp4", 0, 120).withTimelineStart(0));

        for (int i = 1; i <= 100; i++) {
            double splitAt = i;
            int clipIndex = findClipIndexContaining(timeline, splitAt);
            assertTrue(clipIndex >= 0, "Expected a clip covering split second " + splitAt);
            timeline.splitClipAtSourceTimeInTrack(0, clipIndex, splitAt);
        }

        assertTrue(timeline.getClips().size() == 101, "Stress split should produce 101 clips");
        assertTrue(Math.abs(timeline.computeTrackEndSeconds(0) - 120.0) < 0.0001, "Stress split should preserve timeline span");

        for (int i = timeline.getClips().size() - 1; i >= 51; i--) {
            timeline.removeClipFromTrack(0, i);
        }
        assertTrue(timeline.getClips().size() == 51, "Delete sweep should leave 51 clips");
        assertTrue(timeline.getClips().get(0).getTimelineStartSeconds() == 0.0, "First clip should still begin at timeline start");
    }

    private static void testKeyframeInterpolationSampling() {
        AnimationService animation = new AnimationService();
        KeyframeTrack opacity = animation.getOrCreateTrack("opacity");
        opacity.addKeyframe(new Keyframe(0, 0.0, EasingType.LINEAR));
        opacity.addKeyframe(new Keyframe(30, 1.0, EasingType.EASE_IN_OUT));

        double midpoint = animation.sampleParameterAtFrame("opacity", 15);
        assertTrue(Math.abs(midpoint - 0.5) < 0.0001, "Midpoint interpolation should be frame accurate");
    }

    private static void testMetadataAndCollaboration() {
        ProfessionalProject project = new ProfessionalProject("Collab Test");
        project.registerAsset(new ProjectAsset("asset-1", "clip.mov", MediaType.VIDEO));
        project.getMetadataTagService().setTag("asset-1", "scene", "12A");
        assertTrue("12A".equals(project.getMetadataTagService().getTags("asset-1").get("scene")), "Metadata tag should be stored");

        boolean lockOk = project.getCollaborationManager().lockBin("bin-main", "alice");
        boolean lockFail = project.getCollaborationManager().lockBin("bin-main", "bob");
        assertTrue(lockOk && !lockFail, "Bin locking should prevent conflicts");
    }

    private static void testArchiverCollection() {
        ProfessionalProject project = new ProfessionalProject("Archive Test");
        try {
            ProjectPathResolver.ensureProjectStructure();
            java.nio.file.Files.writeString(ProjectPathResolver.resolvePath("assets/test-archive-a.mov"), "video");
            java.nio.file.Files.writeString(ProjectPathResolver.resolvePath("assets/test-archive-b.wav"), "audio");
        } catch (Exception ex) {
            throw new AssertionError("Failed to create archive test fixtures", ex);
        }
        project.registerAsset(new ProjectAsset("asset-1", "assets/test-archive-a.mov", MediaType.VIDEO));
        project.registerAsset(new ProjectAsset("asset-2", "assets/test-archive-b.wav", MediaType.AUDIO));
        ArchiveResult result = project.getProjectArchiver().archiveProject(project, "archive/folder");
        assertTrue(result.getCopiedAssets().size() == 2, "Archiver should collect all assets");
    }

    private static void testVfxAndColorAudioServices() {
        ProfessionalEditingSuite suite = new ProfessionalEditingSuite();

        suite.getColorGradingService().getScopeMonitorService().enableScope(ColorScopeType.WAVEFORM);
        assertTrue(suite.getColorGradingService().getScopeMonitorService().getEnabledScopes().contains(ColorScopeType.WAVEFORM),
                "Waveform scope should be enabled");

        VfxCompositionService vfx = suite.getVfxCompositionService();
        vfx.addLayer(new VfxLayer("layer-1", "subject-1"));
        vfx.getAnimationService().getOrCreateTrack("opacity").addKeyframe(new Keyframe(0, 0.0, EasingType.EASE_IN));
        vfx.getAnimationService().getOrCreateTrack("opacity").addKeyframe(new Keyframe(30, 1.0, EasingType.EASE_OUT));
        assertTrue(vfx.getLayers().size() == 1, "One VFX layer expected");

        AudioPostService audio = suite.getAudioPostService();
        audio.addProcessor(new AudioDuckingProcessor("duck-1"));
        audio.addProcessor(new DynamicsProcessor("dyn-1"));
        audio.getMultichannelAudioManager().configureBus("master", "5.1");
        audio.getFoleyLibraryService().addEffect("foley/door.wav");

        assertTrue(audio.getProcessors().size() == 2, "Two audio processors expected");
        assertTrue("5.1".equals(audio.getMultichannelAudioManager().getChannelLayout("master")), "Multichannel layout should be stored");
        assertTrue(audio.getFoleyLibraryService().getEffects().contains("foley/door.wav"), "Foley effect should be present");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int findClipIndexContaining(TimelineModel timeline, double timelineSecond) {
        List<Clip> clips = timeline.getClips();
        for (int i = 0; i < clips.size(); i++) {
            Clip clip = clips.get(i);
            double start = clip.getTimelineStartSeconds();
            double end = start + clip.getDurationSeconds();
            if (timelineSecond > start && timelineSecond < end) {
                return i;
            }
        }
        return -1;
    }
}
