import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Entry point / launcher for Video Editor Pro.
 *
 * Responsibilities are intentionally minimal:
 * 1) create engine + project session,
 * 2) create the professional workspace root container,
 * 3) set scene on primary stage and show window,
 * 4) report startup errors with a user-friendly alert.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            ProjectPathResolver.ensureProjectStructure();
            VideoEngine engine = createVideoEngineSafely();
            ProfessionalProject project = createDefaultProject();
            project.saveProjectMetadata();

            // ProfessionalWorkspaceView contains Timeline/Color/Audio/VFX/Collaboration/Export
            // and the corrected VideoPlayerPanel (preview + docked controls in one modular unit).
            ProfessionalWorkspaceView workspace = new ProfessionalWorkspaceView(project, engine);
            workspace.setMinSize(1280, 720);
            workspace.setPrefSize(1440, 860);

            // ScrollPane is the new root container for the entire workspace.
            // IMPORTANT: setContent(workspace) ensures the UI is visible (not an empty scroll view).
            ScrollPane workspaceScroll = new ScrollPane();
            workspaceScroll.setContent(workspace);
            workspaceScroll.setFitToWidth(false);
            workspaceScroll.setFitToHeight(false);
            workspaceScroll.setPannable(true);
            workspaceScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            workspaceScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            workspaceScroll.setStyle("-fx-background: #0b1220; -fx-background-color: #0b1220;");

            BorderPane root = new BorderPane();
            root.setTop(buildMenuBar(primaryStage, workspace));
            root.setCenter(workspaceScroll);

            Scene scene = new Scene(root, 1280, 720);
            KeyboardShortcutRegistry.apply(scene, workspace);

            primaryStage.setTitle("Video Editor Pro");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1280);
            primaryStage.setMinHeight(720);
            primaryStage.setWidth(1280);
            primaryStage.setHeight(720);
            primaryStage.centerOnScreen();
            primaryStage.show();
        } catch (Throwable ex) {
            ex.printStackTrace();

            BorderPane fallbackRoot = new BorderPane();
            javafx.scene.control.Label fallbackLabel = new javafx.scene.control.Label(
                    "Video Editor Pro started in safe mode.\n" +
                            "Workspace initialization failed.\n" +
                            "Details: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
            );
            fallbackLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px;");
            fallbackRoot.setStyle("-fx-background-color: #0b1220;");
            fallbackRoot.setCenter(new javafx.scene.layout.StackPane(fallbackLabel));

            Scene fallbackScene = new Scene(fallbackRoot, 900, 520);
            primaryStage.setTitle("Video Editor Pro (Safe Mode)");
            primaryStage.setScene(fallbackScene);
            primaryStage.centerOnScreen();
            primaryStage.show();

            showStartupError(
                    "Workspace initialization failed.",
                    ex.getMessage() == null ? ex.toString() : ex.getMessage()
            );
        }
    }

    private VideoEngine createVideoEngineSafely() {
        // Resolve FFmpeg from bundled /bin folder for portability.
        String ffmpegPath = System.getProperty("videoeditor.ffmpeg", ProjectPathResolver.resolveBundledFfmpegExecutable());
        JavaFFmpegEngine engine = new JavaFFmpegEngine(ffmpegPath);
        // Prefer GPU acceleration for smooth preview when available.
        // Engines that don't support this backend can ignore or throw and then fall back to CPU.
        try {
            engine.setRenderBackend(RenderBackendType.OPENCL);
        } catch (Exception ignored) {
            engine.setRenderBackend(RenderBackendType.CPU);
        }
        return engine;
    }

    private ProfessionalProject createDefaultProject() {
        ProfessionalProject project = new ProfessionalProject("Video Editor Pro Session");

        Sequence main = new Sequence(
                UUID.randomUUID().toString(),
                "Main Sequence",
                new FrameRate(30)
        );
        project.addSequence(main);

        // Ensure tracks exist even if project starts blank.
        TimelineModel timeline = main.getTimelineModel();
        timeline.ensureTrack(Track.TrackType.OVERLAY);
        timeline.ensureTrack(Track.TrackType.TEXT);
        timeline.ensureTrack(Track.TrackType.AUDIO);

        // Optional demo media is only seeded if files are actually present.
        addDemoAssetIfExists(project, timeline, "assets/input.mp4", Track.TrackType.VIDEO, MediaType.VIDEO, 8.0, 1.0);
        addDemoAssetIfExists(project, timeline, "assets/overlay.png", Track.TrackType.OVERLAY, MediaType.IMAGE, 5.0, 1.0);
        addDemoAssetIfExists(project, timeline, "assets/music.wav", Track.TrackType.AUDIO, MediaType.AUDIO, 12.0, 0.7);

        // Safe default text layer that does not require external media.
        timeline.addClipToTrack(timeline.ensureTrack(Track.TrackType.TEXT),
                new Clip("__TEXT__", 0, 3.0, 1.0)
                        .withTimelineStart(0)
                        .withText("No media loaded")
                        .withTextFontSize(32)
                        .withPosition(80, 80)
                        .withZIndex(150));

        return project;
    }

    private void addDemoAssetIfExists(
            ProfessionalProject project,
            TimelineModel timeline,
            String source,
            Track.TrackType trackType,
            MediaType mediaType,
            double duration,
            double volume
    ) {
        Path path = ProjectPathResolver.resolvePath(source);
        if (!Files.exists(path)) {
            return;
        }
        String relativeSource = ProjectPathResolver.toRelativeProjectPath(path);
        int trackIndex = timeline.ensureTrack(trackType);
        double timelineStart = timeline.computeTrackEndSeconds(trackIndex);
        Clip clip = new Clip(relativeSource, 0, duration, volume).withTimelineStart(timelineStart);
        if (trackType == Track.TrackType.OVERLAY) {
            clip = clip.withOpacity(0.85).withScale(0.9).withZIndex(100);
        }
        timeline.addClipToTrack(trackIndex, clip);
        project.registerAsset(new ProjectAsset("asset-" + System.nanoTime(), relativeSource, mediaType));
    }

    private void showStartupError(String header, String details) {
        Runnable task = () -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Startup Error");
            alert.setHeaderText(header);
            alert.setContentText(details == null ? "Unknown startup error." : details);
            alert.showAndWait();
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    private MenuBar buildMenuBar(Stage stage, ProfessionalWorkspaceView workspace) {
        Menu file = new Menu("File");
        MenuItem newSeq = new MenuItem("New Sequence");
        newSeq.setOnAction(e -> workspace.getTimelineEditingPanel().quickAddClip());
        MenuItem archive = new MenuItem("Open Collaboration/Archive");
        archive.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_COLLABORATION));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> stage.close());
        file.getItems().addAll(newSeq, archive, new SeparatorMenuItem(), exit);

        Menu edit = new Menu("Edit");
        MenuItem trim = new MenuItem("Trim/Cut (Blade)");
        trim.setOnAction(e -> workspace.getTimelineEditingPanel().performBladeAtPlayhead());
        MenuItem snap = new MenuItem("Toggle Snapping");
        snap.setOnAction(e -> workspace.getTimelineEditingPanel().toggleSnapping());
        MenuItem proxy = new MenuItem("Toggle Proxy/Raw");
        proxy.setOnAction(e -> workspace.getTimelineEditingPanel().toggleProxyMode());
        edit.getItems().addAll(trim, snap, proxy);

        Menu panels = new Menu("Panels");
        MenuItem editor = new MenuItem("Editor");
        editor.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_EDITOR));
        MenuItem timeline = new MenuItem("Timeline");
        timeline.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_TIMELINE));
        MenuItem color = new MenuItem("Color Grading");
        color.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_COLOR));
        MenuItem audio = new MenuItem("Audio Mixer");
        audio.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_AUDIO));
        MenuItem vfx = new MenuItem("VFX / Motion");
        vfx.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_VFX));
        MenuItem collab = new MenuItem("Collaboration / Media");
        collab.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_COLLABORATION));
        MenuItem export = new MenuItem("Export");
        export.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_EXPORT));
        panels.getItems().addAll(editor, timeline, color, audio, vfx, collab, export);

        Menu delivery = new Menu("Delivery");
        MenuItem queueRender = new MenuItem("Queue Export Job");
        queueRender.setOnAction(e -> workspace.getExportRenderPanel().queueCurrentExportPreset());
        MenuItem openExportPanel = new MenuItem("Open Export Panel");
        openExportPanel.setOnAction(e -> workspace.activateTab(ProfessionalWorkspaceView.TAB_EXPORT));
        delivery.getItems().addAll(queueRender, openExportPanel);

        Menu preview = new Menu("Preview");
        MenuItem play = new MenuItem("Play");
        play.setOnAction(e -> {
            if (workspace.hasVideoPreviewPanel()) {
                workspace.getVideoPlayerPanel().playPreview();
            } else {
                workspace.setStatus("Preview panel unavailable in safe mode.");
            }
        });
        MenuItem pause = new MenuItem("Pause");
        pause.setOnAction(e -> {
            if (workspace.hasVideoPreviewPanel()) {
                workspace.getVideoPlayerPanel().pausePreview();
            } else {
                workspace.setStatus("Preview panel unavailable in safe mode.");
            }
        });
        MenuItem stop = new MenuItem("Stop");
        stop.setOnAction(e -> {
            if (workspace.hasVideoPreviewPanel()) {
                workspace.getVideoPlayerPanel().stopPreview();
            } else {
                workspace.setStatus("Preview panel unavailable in safe mode.");
            }
        });
        MenuItem frameStep = new MenuItem("Step Frame");
        frameStep.setOnAction(e -> {
            if (workspace.hasVideoPreviewPanel()) {
                workspace.getVideoPlayerPanel().stepFrame();
            } else {
                workspace.setStatus("Preview panel unavailable in safe mode.");
            }
        });
        MenuItem frameStepBack = new MenuItem("Step Frame Back");
        frameStepBack.setOnAction(e -> {
            if (workspace.hasVideoPreviewPanel()) {
                workspace.getVideoPlayerPanel().stepFrameBackward();
            } else {
                workspace.setStatus("Preview panel unavailable in safe mode.");
            }
        });
        preview.getItems().addAll(play, pause, stop, frameStepBack, frameStep);

        return new MenuBar(file, edit, panels, delivery, preview);
    }

    public static void main(String[] args) {
        // JavaFX framework launcher.
        launch(args);
    }
}
