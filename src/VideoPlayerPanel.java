import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dedicated professional preview monitor that encapsulates:
 * 1) preview surface, and
 * 2) docked playback controls.
 *
 * Controls are always anchored in the bottom region and wrapped in a horizontal
 * ScrollPane for small windows to avoid clipping outside visible bounds.
 */
public class VideoPlayerPanel extends BorderPane implements PreviewEventBus.Listener {

    private enum PlaybackState {
        IDLE,
        PLAYING,
        PAUSED,
        STOPPED
    }

    private final ProfessionalProject project;
    private final VideoEngine videoEngine;
    private final Consumer<String> statusSink;

    private final MediaView mediaView = new MediaView();
    private final Pane overlayLayer = new Pane();
    private final Slider timelineSlider = new Slider(0, 1, 0);
    private final Label timeLabel = new Label("00:00 / 00:00");
    private final Label activeInfoLabel = new Label("No clip selected");
    private final Label effectsInfoLabel = new Label("Color/VFX/Audio sync ready");
    private final ComboBox<String> resolutionModeCombo = new ComboBox<>();

    private TimelinePlaybackController playbackController;
    private final Map<String, MediaPlayer> overlayMediaPlayers = new HashMap<>();
    private final Map<String, StackPane> overlayNodes = new HashMap<>();
    private boolean internalSliderUpdate;
    private PlaybackState playbackState = PlaybackState.STOPPED;

    public VideoPlayerPanel(ProfessionalProject project, VideoEngine videoEngine, Consumer<String> statusSink) {
        this.project = project;
        this.videoEngine = videoEngine;
        this.statusSink = statusSink;

        setPadding(new Insets(8));
        setStyle("-fx-background-color: #0f172a; -fx-background-radius: 8;");

        setTop(buildHeader());
        setCenter(buildPreviewSurface());
        setBottom(buildDockedControls());

        initializePlaybackController();
        rebuildTimelineForPreview();
        updateState(PlaybackState.STOPPED);

        PreviewEventBus.addListener(this);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                PreviewEventBus.removeListener(this);
                if (playbackController != null) {
                    playbackController.dispose();
                }
            }
        });
    }

    private HBox buildHeader() {
        Label title = new Label("Video Preview Panel");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold;");

        resolutionModeCombo.getItems().addAll("Proxy", "Raw");
        resolutionModeCombo.setValue(project.getProxyMediaManager().isPreferProxyForEditing() ? "Proxy" : "Raw");
        resolutionModeCombo.valueProperty().addListener((obs, oldV, newV) -> applyResolutionMode(newV));

        Label resolutionLabel = new Label("Resolution");
        resolutionLabel.setStyle("-fx-text-fill: #e2e8f0;");
        effectsInfoLabel.setStyle("-fx-text-fill: #93c5fd;");

        HBox header = new HBox(10, title, resolutionLabel, resolutionModeCombo, effectsInfoLabel);
        HBox.setHgrow(effectsInfoLabel, Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 6, 0));
        return header;
    }

    private StackPane buildPreviewSurface() {
        mediaView.setPreserveRatio(true);
        mediaView.setFitWidth(960);
        mediaView.setFitHeight(440);

        activeInfoLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-background-color: rgba(15,23,42,0.75); -fx-padding: 6 10; -fx-background-radius: 6;");

        overlayLayer.setPickOnBounds(false);
        overlayLayer.setMouseTransparent(false);
        StackPane preview = new StackPane(mediaView, overlayLayer, activeInfoLabel);
        overlayLayer.prefWidthProperty().bind(preview.widthProperty());
        overlayLayer.prefHeightProperty().bind(preview.heightProperty());
        StackPane.setAlignment(activeInfoLabel, Pos.TOP_LEFT);
        preview.setMinHeight(320);
        preview.setPrefHeight(460);
        preview.setStyle("-fx-background-color: #000000; -fx-background-radius: 8;");
        VBox.setVgrow(preview, Priority.ALWAYS);
        return preview;
    }

    private VBox buildDockedControls() {
        Button rewind = new Button("⏪");
        rewind.setTooltip(new Tooltip("Seek backward 5 seconds"));
        rewind.setOnAction(e -> seekRelative(-5.0));

        Button play = new Button("▶ Play");
        play.setTooltip(new Tooltip("Start playback"));
        play.setOnAction(e -> handlePlayRequest());

        Button pause = new Button("⏸ Pause");
        pause.setTooltip(new Tooltip("Pause playback (only when playing)"));
        pause.setOnAction(e -> handlePauseRequest());

        Button stop = new Button("⏹ Stop");
        stop.setTooltip(new Tooltip("Stop playback and reset to start"));
        stop.setOnAction(e -> handleStopRequest());

        Button fastForward = new Button("⏩");
        fastForward.setTooltip(new Tooltip("Seek forward 5 seconds"));
        fastForward.setOnAction(e -> seekRelative(5.0));

        Button frameBack = new Button("Frame -1");
        frameBack.setTooltip(new Tooltip("Step one frame backward"));
        frameBack.setOnAction(e -> playbackController.seek(playbackController.getCurrentGlobalSeconds() - frameDurationSeconds()));

        Button frameStep = new Button("Frame +1");
        frameStep.setTooltip(new Tooltip("Step one frame forward"));
        frameStep.setOnAction(e -> playbackController.seek(playbackController.getCurrentGlobalSeconds() + frameDurationSeconds()));

        Button fullscreen = new Button("Fullscreen");
        fullscreen.setTooltip(new Tooltip("Toggle fullscreen preview"));
        fullscreen.setOnAction(e -> toggleFullscreen());

        timelineSlider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(timelineSlider, Priority.ALWAYS);
        timelineSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (internalSliderUpdate) {
                return;
            }
            if (timelineSlider.isValueChanging()) {
                playbackController.seek(newV.doubleValue());
            }
        });
        timelineSlider.setOnMouseReleased(e -> playbackController.seek(timelineSlider.getValue()));
        timelineSlider.setOnKeyReleased(e -> playbackController.seek(timelineSlider.getValue()));

        HBox controlsRow = new HBox(8,
                rewind,
                play,
                pause,
                stop,
                fastForward,
                frameBack,
                frameStep,
                fullscreen,
                timeLabel
        );
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        controlsRow.setPadding(new Insets(4, 0, 0, 0));

        ScrollPane controlsScroller = new ScrollPane(controlsRow);
        controlsScroller.setFitToHeight(true);
        controlsScroller.setFitToWidth(true);
        controlsScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        controlsScroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlsScroller.setPannable(true);
        controlsScroller.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox controls = new VBox(6, timelineSlider, controlsScroller);
        controls.setPadding(new Insets(8, 0, 0, 0));
        return controls;
    }

    private void initializePlaybackController() {
        playbackController = new TimelinePlaybackController(mediaView, new TimelinePlaybackController.Listener() {
            @Override
            public void onTimeUpdate(double globalSeconds, double totalSeconds) {
                Platform.runLater(() -> {
                    internalSliderUpdate = true;
                    timelineSlider.setMax(Math.max(1, totalSeconds));
                    timelineSlider.setValue(globalSeconds);
                    internalSliderUpdate = false;
                    timeLabel.setText(formatTime(globalSeconds) + " / " + formatTime(totalSeconds));
                });
            }

            @Override
            public void onClipChanged(TimelinePlaybackController.PlaybackClip previous,
                                      TimelinePlaybackController.PlaybackClip current,
                                      boolean autoTransition) {
                String text = current == null ? "No clip selected" : "Previewing: " + current.getClip().getSourcePath();
                Platform.runLater(() -> activeInfoLabel.setText(text));
                if (current != null) {
                    syncEngineLoadedSource(current.getClip().getSourcePath());
                }
            }

            @Override
            public void onPlaybackStateChanged(boolean playing) {
                updateState(playing ? PlaybackState.PLAYING : PlaybackState.PAUSED);
                emit(playing ? "Preview playback started" : "Preview playback paused/stopped");
            }

            @Override
            public void onError(String message) {
                emit(message);
            }

            @Override
            public void onActiveTrackClipsChanged(double globalSeconds,
                                                  List<TimelinePlaybackController.PlaybackClip> activeNonPrimaryTrackClips) {
                if (activeNonPrimaryTrackClips == null || activeNonPrimaryTrackClips.isEmpty()) {
                    effectsInfoLabel.setText("Color/VFX/Audio sync ready");
                    Platform.runLater(VideoPlayerPanel.this::clearActiveOverlays);
                    return;
                }
                StringBuilder sb = new StringBuilder("Active layers: ");
                for (int i = 0; i < activeNonPrimaryTrackClips.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(activeNonPrimaryTrackClips.get(i).getClip().getSourcePath());
                }
                effectsInfoLabel.setText(sb.toString());
                renderPreviewOverlays(globalSeconds, activeNonPrimaryTrackClips);
            }
        });
    }

    private void renderPreviewOverlays(double globalSeconds, List<TimelinePlaybackController.PlaybackClip> overlays) {
        Platform.runLater(() -> {
            List<TimelinePlaybackController.PlaybackClip> sorted = new ArrayList<>(overlays);
            sorted.sort(this::compareOverlayPriority);
            Set<String> activeIds = new HashSet<>();
            for (TimelinePlaybackController.PlaybackClip playbackClip : sorted) {
                if (playbackClip == null || playbackClip.getClip() == null) {
                    continue;
                }
                Clip clip = resolveOverlayClip(playbackClip.getClip());
                if (clip == null || clip.getOpacity() <= 0.001) {
                    continue;
                }
                String clipId = clip.getClipId();
                activeIds.add(clipId);
                StackPane wrapper = ensureOverlayNode(playbackClip, clip);
                if (wrapper == null) {
                    continue;
                }
                updateOverlayNode(wrapper, clip, playbackClip, globalSeconds);
                wrapper.toFront();
            }
            removeInactiveOverlayResources(activeIds);
        });
    }

    private StackPane ensureOverlayNode(TimelinePlaybackController.PlaybackClip playbackClip, Clip clip) {
        String clipId = clip.getClipId();
        String kind = overlayKind(clip);
        StackPane existing = overlayNodes.get(clipId);
        if (existing != null && kind.equals(existing.getProperties().get("overlayKind"))) {
            return existing;
        }
        removeOverlayResources(clipId);

        javafx.scene.Node content = createOverlayContent(playbackClip, clip, kind);
        if (content == null) {
            return null;
        }
        double baseWidth = overlayBaseWidth(kind);
        StackPane wrapper = new StackPane(content);
        wrapper.setLayoutX(clip.getPositionX());
        wrapper.setLayoutY(clip.getPositionY());
        wrapper.setCursor(Cursor.MOVE);
        Rectangle resizeHandle = new Rectangle(10, 10, Color.web("#f59e0b"));
        StackPane handleBox = new StackPane(resizeHandle);
        wrapper.getChildren().add(handleBox);
        attachOverlayDragResize(wrapper, handleBox, clipId, baseWidth);
        wrapper.setStyle("-fx-border-color: rgba(245,158,11,0.7); -fx-border-width: 1;");
        wrapper.getProperties().put("overlayKind", kind);
        wrapper.getProperties().put("baseWidth", baseWidth);
        overlayNodes.put(clipId, wrapper);
        overlayLayer.getChildren().add(wrapper);
        return wrapper;
    }

    private javafx.scene.Node createOverlayContent(TimelinePlaybackController.PlaybackClip playbackClip, Clip clip, String kind) {
        if ("text".equals(kind)) {
            Label label = new Label(clip.getTextContent() == null ? "" : clip.getTextContent());
            label.setTextFill(Color.WHITE);
            label.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-padding: 4 8; -fx-background-radius: 4;");
            return label;
        }
        if ("image".equals(kind)) {
            javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(ProjectPathResolver.resolvePath(clip.getSourcePath()).toUri().toString(), true)
            );
            imageView.setPreserveRatio(true);
            return imageView;
        }
        try {
            Media media = new Media(ProjectPathResolver.resolvePath(clip.getSourcePath()).toUri().toString());
            MediaPlayer overlayPlayer = new MediaPlayer(media);
            overlayPlayer.setMute(true);
            overlayPlayer.setOnError(() -> emit("Overlay playback error: " + overlayPlayer.getError()));
            MediaView overlayView = new MediaView(overlayPlayer);
            overlayView.setPreserveRatio(true);
            overlayMediaPlayers.put(clip.getClipId(), overlayPlayer);
            syncOverlayMediaPlayer(overlayPlayer, playbackClip, playbackController == null ? 0 : playbackController.getCurrentGlobalSeconds());
            return overlayView;
        } catch (Exception ex) {
            emit("Overlay load warning: " + ex.getMessage());
            return null;
        }
    }

    private void updateOverlayNode(StackPane wrapper,
                                   Clip clip,
                                   TimelinePlaybackController.PlaybackClip playbackClip,
                                   double globalSeconds) {
        double baseWidth = ((Number) wrapper.getProperties().getOrDefault("baseWidth", 260.0)).doubleValue();
        wrapper.setLayoutX(clip.getPositionX());
        wrapper.setLayoutY(clip.getPositionY());
        wrapper.setScaleX(1.0);
        wrapper.setScaleY(1.0);

        if (!wrapper.getChildren().isEmpty()) {
            javafx.scene.Node content = wrapper.getChildren().get(0);
            if (content instanceof Label label) {
                label.setText(clip.getTextContent() == null ? "" : clip.getTextContent());
                label.setFont(Font.font(Math.max(10, clip.getTextFontSize() * clip.getScale())));
                label.setOpacity(clip.getOpacity());
            } else if (content instanceof javafx.scene.image.ImageView imageView) {
                imageView.setFitWidth(baseWidth * Math.max(0.1, clip.getScale()));
                imageView.setOpacity(clip.getOpacity());
            } else if (content instanceof MediaView overlayView) {
                overlayView.setFitWidth(baseWidth * Math.max(0.1, clip.getScale()));
                overlayView.setOpacity(clip.getOpacity());
                syncOverlayMediaPlayer(overlayMediaPlayers.get(clip.getClipId()), playbackClip, globalSeconds);
            }
        }

        if (wrapper.getChildren().size() > 1 && wrapper.getChildren().get(1) instanceof StackPane handleBox) {
            handleBox.setTranslateX((baseWidth * Math.max(0.1, clip.getScale())) * 0.5 - 8);
            handleBox.setTranslateY(18);
        }
    }

    private void attachOverlayDragResize(StackPane wrapper, StackPane handle, String clipId, double baseWidth) {
        final double[] dragAnchor = new double[2];
        wrapper.setOnMousePressed(e -> {
            dragAnchor[0] = e.getSceneX() - wrapper.getLayoutX();
            dragAnchor[1] = e.getSceneY() - wrapper.getLayoutY();
            Clip clip = findClipById(clipId);
            if (clip != null) {
                PreviewEventBus.publishClipSelected(clip.getSourcePath());
            }
            e.consume();
        });
        wrapper.setOnMouseDragged(e -> {
            wrapper.setLayoutX(Math.max(0, e.getSceneX() - dragAnchor[0]));
            wrapper.setLayoutY(Math.max(0, e.getSceneY() - dragAnchor[1]));
            e.consume();
        });
        wrapper.setOnMouseReleased(e -> {
            Clip clip = findClipById(clipId);
            if (clip != null) {
                commitPreviewMediaClipUpdate(clipId, wrapper.getLayoutX(), wrapper.getLayoutY(), clip.getScale());
            }
            e.consume();
        });

        final double[] scaleAnchor = new double[2];
        final double[] startScale = new double[1];
        handle.setCursor(Cursor.SE_RESIZE);
        handle.setOnMousePressed(e -> {
            scaleAnchor[0] = e.getSceneX();
            scaleAnchor[1] = e.getSceneY();
            Clip clip = findClipById(clipId);
            startScale[0] = clip == null ? 1.0 : clip.getScale();
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - scaleAnchor[0];
            double dy = e.getSceneY() - scaleAnchor[1];
            double delta = Math.max(dx, dy) / Math.max(80.0, baseWidth);
            Clip clip = findClipById(clipId);
            double currentScale = clip == null ? 1.0 : clip.getScale();
            double newScale = Math.max(0.1, Math.min(4.0, startScale[0] + delta));
            wrapper.setScaleX(newScale / Math.max(0.1, currentScale));
            wrapper.setScaleY(newScale / Math.max(0.1, currentScale));
            e.consume();
        });
        handle.setOnMouseReleased(e -> {
            double dx = e.getSceneX() - scaleAnchor[0];
            double dy = e.getSceneY() - scaleAnchor[1];
            double delta = Math.max(dx, dy) / Math.max(80.0, baseWidth);
            double newScale = Math.max(0.1, Math.min(4.0, startScale[0] + delta));
            commitPreviewMediaClipUpdate(clipId, wrapper.getLayoutX(), wrapper.getLayoutY(), newScale);
            e.consume();
        });
    }

    private Clip resolveOverlayClip(Clip clip) {
        if (clip == null) {
            return null;
        }
        return findClipById(clip.getClipId());
    }

    private void commitPreviewMediaClipUpdate(String clipId, double x, double y, double scale) {
        Sequence active = project.getActiveSequence();
        if (active == null || clipId == null || clipId.isBlank()) {
            return;
        }
        TimelineModel model = active.getTimelineModel();
        Clip original = findClipById(clipId);
        if (original == null) {
            return;
        }
        int trackIndex = model.findTrackIndexOfClip(original);
        int clipIndex = model.indexOfInTrack(trackIndex, original);
        if (trackIndex < 0 || clipIndex < 0) {
            return;
        }
        Clip updated = original.withPosition(x, y).withScale(scale);
        model.replaceClipInTrack(trackIndex, clipIndex, updated);
        PreviewEventBus.publishTimelineChanged();
    }

    private void clearOverlayPlayers() {
        for (MediaPlayer player : new ArrayList<>(overlayMediaPlayers.values())) {
            if (player != null) {
                try {
                    player.stop();
                    player.dispose();
                } catch (Exception ignored) {
                }
            }
        }
        overlayMediaPlayers.clear();
    }

    private void clearActiveOverlays() {
        overlayLayer.getChildren().clear();
        overlayNodes.clear();
        clearOverlayPlayers();
    }

    private void removeInactiveOverlayResources(Set<String> activeIds) {
        List<String> staleIds = new ArrayList<>();
        for (String clipId : overlayNodes.keySet()) {
            if (activeIds == null || !activeIds.contains(clipId)) {
                staleIds.add(clipId);
            }
        }
        for (String clipId : staleIds) {
            removeOverlayResources(clipId);
        }
    }

    private void removeOverlayResources(String clipId) {
        StackPane wrapper = overlayNodes.remove(clipId);
        if (wrapper != null) {
            overlayLayer.getChildren().remove(wrapper);
        }
        MediaPlayer player = overlayMediaPlayers.remove(clipId);
        if (player != null) {
            try {
                player.stop();
                player.dispose();
            } catch (Exception ignored) {
            }
        }
    }

    private void syncOverlayMediaPlayer(MediaPlayer overlayPlayer,
                                        TimelinePlaybackController.PlaybackClip playbackClip,
                                        double globalSeconds) {
        if (overlayPlayer == null || playbackClip == null) {
            return;
        }
        double sourceSeek = playbackClip.getSourceStart() + (globalSeconds - playbackClip.getTimelineStart());
        double clampedSeek = Math.max(playbackClip.getSourceStart(), Math.min(playbackClip.getSourceEnd(), sourceSeek));
        Runnable syncTask = () -> {
            try {
                double drift = Math.abs(overlayPlayer.getCurrentTime().toSeconds() - clampedSeek);
                if (drift > 0.05) {
                    overlayPlayer.seek(javafx.util.Duration.seconds(clampedSeek));
                }
                if (playbackController != null && playbackController.isPlaying()) {
                    overlayPlayer.play();
                } else {
                    overlayPlayer.pause();
                }
            } catch (Exception ignored) {
            }
        };
        overlayPlayer.setOnReady(syncTask);
        MediaPlayer.Status status = overlayPlayer.getStatus();
        if (status != MediaPlayer.Status.UNKNOWN
                && status != MediaPlayer.Status.DISPOSED
                && status != MediaPlayer.Status.HALTED) {
            syncTask.run();
        }
    }

    private int compareOverlayPriority(TimelinePlaybackController.PlaybackClip left,
                                       TimelinePlaybackController.PlaybackClip right) {
        Clip a = resolveOverlayClip(left == null ? null : left.getClip());
        Clip b = resolveOverlayClip(right == null ? null : right.getClip());
        return Integer.compare(overlayPriority(left, a), overlayPriority(right, b));
    }

    private int overlayPriority(TimelinePlaybackController.PlaybackClip playbackClip, Clip clip) {
        if (clip == null || playbackClip == null) {
            return 0;
        }
        int priority = clip.getZIndex() + (playbackClip.getTrackIndex() * 1000);
        if ("text".equals(overlayKind(clip))) {
            priority += 100_000;
        }
        return priority;
    }

    private String overlayKind(Clip clip) {
        String source = clip == null || clip.getSourcePath() == null ? "" : clip.getSourcePath().toLowerCase(Locale.ROOT);
        if (source.startsWith("__text__") || source.startsWith("__subtitle__")) {
            return "text";
        }
        if (source.endsWith(".png") || source.endsWith(".jpg") || source.endsWith(".jpeg")
                || source.endsWith(".webp") || source.endsWith(".bmp")) {
            return "image";
        }
        return "video";
    }

    private double overlayBaseWidth(String kind) {
        return "image".equals(kind) ? 220 : 260;
    }

    private Clip findClipById(String clipId) {
        if (clipId == null || clipId.isBlank()) {
            return null;
        }
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return null;
        }
        for (Track track : active.getTimelineModel().getTracks()) {
            for (Clip clip : track.getClips()) {
                if (clipId.equals(clip.getClipId())) {
                    return clip;
                }
            }
        }
        return null;
    }

    private void handlePlayRequest() {
        if (!hasLoadedPlayableMedia()) {
            showNoMediaLoadedAlert();
            return;
        }
        if (playbackState == PlaybackState.PLAYING) {
            return;
        }

        try {
            videoEngine.play();
        } catch (Exception ex) {
            emit("Engine play warning: " + ex.getMessage());
        }

        playbackController.play();
        updateState(PlaybackState.PLAYING);
    }

    private void handlePauseRequest() {
        if (playbackState != PlaybackState.PLAYING) {
            return;
        }
        try {
            videoEngine.pause();
        } catch (Exception ex) {
            emit("Engine pause warning: " + ex.getMessage());
        }
        playbackController.pause();
        updateState(PlaybackState.PAUSED);
    }

    private void handleStopRequest() {
        try {
            videoEngine.pause();
            videoEngine.seek(0);
        } catch (Exception ex) {
            emit("Engine stop warning: " + ex.getMessage());
        }
        playbackController.stop();
        updateState(PlaybackState.STOPPED);
    }

    private void seekRelative(double deltaSeconds) {
        playbackController.seek(playbackController.getCurrentGlobalSeconds() + deltaSeconds);
    }

    private void rebuildTimelineForPreview() {
        Sequence active = project.getActiveSequence();
        if (active == null) {
            playbackController.setTimelineTracks(List.of());
            updateState(PlaybackState.IDLE);
            return;
        }

        List<List<TimelinePlaybackController.PlaybackClip>> tracks = new ArrayList<>();
        for (int trackIndex = 0; trackIndex < active.getTimelineModel().getTracks().size(); trackIndex++) {
            Track track = active.getTimelineModel().getTracks().get(trackIndex);
            List<TimelinePlaybackController.PlaybackClip> playbackClips = new ArrayList<>();
            for (Clip clip : track.getClips()) {
                double srcStart = clip.getStartSeconds();
                double srcEnd = clip.getEndSeconds();
                if (srcEnd <= srcStart) {
                    continue;
                }
                if (trackIndex == 0 && !isPlayableMediaSource(clip.getSourcePath())) {
                    continue;
                }
                if (track.getType() == Track.TrackType.AUDIO) {
                    continue;
                }
                playbackClips.add(new TimelinePlaybackController.PlaybackClip(
                        clip,
                        trackIndex,
                        clip.getTimelineStartSeconds(),
                        srcStart,
                        srcEnd
                ));
            }
            tracks.add(playbackClips);
        }
        playbackController.setTimelineTracks(tracks);
        updateState(hasLoadedPlayableMedia() ? PlaybackState.STOPPED : PlaybackState.IDLE);
    }

    private boolean hasLoadedPlayableMedia() {
        Sequence active = project.getActiveSequence();
        if (active == null || active.getTimelineModel() == null) {
            return false;
        }

        for (Track track : active.getTimelineModel().getTracks()) {
            for (Clip clip : track.getClips()) {
                if (isPlayableMediaSource(clip.getSourcePath()) && clip.getEndSeconds() > clip.getStartSeconds()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPlayableMediaSource(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank() || sourcePath.startsWith("__")) {
            return false;
        }
        File file = ProjectPathResolver.resolvePath(sourcePath).toFile();
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        String ext = sourcePath.toLowerCase(Locale.ROOT);
        return ext.endsWith(".mp4") || ext.endsWith(".m4v") || ext.endsWith(".mov")
                || ext.endsWith(".mkv") || ext.endsWith(".avi")
                || ext.endsWith(".mp3") || ext.endsWith(".m4a") || ext.endsWith(".wav") || ext.endsWith(".aiff");
    }

    private void showNoMediaLoadedAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Playback");
        alert.setHeaderText("No media loaded");
        alert.setContentText("Add a playable audio/video clip to the timeline before pressing Play.");
        alert.show();
        emit("No media loaded");
    }

    private void syncEngineLoadedSource(String sourcePath) {
        if (!isPlayableMediaSource(sourcePath)) {
            return;
        }
        try {
            videoEngine.loadVideo(sourcePath);
            videoEngine.seek(playbackController.getCurrentGlobalSeconds());
        } catch (Exception ex) {
            emit("Engine sync warning: " + ex.getMessage());
        }
    }

    private void applyResolutionMode(String mode) {
        boolean proxy = "Proxy".equalsIgnoreCase(mode);
        project.getProxyMediaManager().setPreferProxyForEditing(proxy);
        mediaView.setFitWidth(proxy ? 760 : 960);
        mediaView.setFitHeight(proxy ? 360 : 440);
        emit(proxy ? "Preview resolution set to Proxy" : "Preview resolution set to Raw");
    }

    private void toggleFullscreen() {
        if (getScene() == null || getScene().getWindow() == null) {
            return;
        }
        if (getScene().getWindow() instanceof Stage stage) {
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    private double frameDurationSeconds() {
        Sequence active = project.getActiveSequence();
        if (active == null || active.getFrameRate() == null || active.getFrameRate().getFps() <= 0) {
            return 1.0 / 30.0;
        }
        return 1.0 / active.getFrameRate().getFps();
    }

    private String formatTime(double seconds) {
        int total = (int) Math.max(0, Math.round(seconds));
        int mm = total / 60;
        int ss = total % 60;
        return String.format("%02d:%02d", mm, ss);
    }

    private void updateState(PlaybackState nextState) {
        playbackState = nextState == null ? PlaybackState.IDLE : nextState;
    }

    private void emit(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }

    /** External controller hook: start preview playback. */
    public void playPreview() {
        handlePlayRequest();
    }

    /** External controller hook: pause preview playback. */
    public void pausePreview() {
        handlePauseRequest();
    }

    /** External controller hook: stop preview playback and reset to start. */
    public void stopPreview() {
        handleStopRequest();
    }

    /** External controller hook: frame-step preview forward by one frame. */
    public void stepFrame() {
        playbackController.seek(playbackController.getCurrentGlobalSeconds() + frameDurationSeconds());
    }

    /** External controller hook: frame-step preview backward by one frame. */
    public void stepFrameBackward() {
        playbackController.seek(playbackController.getCurrentGlobalSeconds() - frameDurationSeconds());
    }

    @Override
    public void onTimelineChanged() {
        clearActiveOverlays();
        rebuildTimelineForPreview();
    }

    @Override
    public void onScrubRequested(double timelineSeconds) {
        playbackController.seek(timelineSeconds);
    }

    @Override
    public void onClipSelected(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return;
        }
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return;
        }
        for (Track track : active.getTimelineModel().getTracks()) {
            for (Clip clip : track.getClips()) {
                if (sourcePath.equalsIgnoreCase(clip.getSourcePath()) && isPlayableMediaSource(clip.getSourcePath())) {
                    playbackController.seek(clip.getTimelineStartSeconds());
                    return;
                }
            }
        }
    }

    @Override
    public void onRefreshRequested(String reason) {
        effectsInfoLabel.setText("Live update: " + reason);
    }

    @Override
    public void onExportPreviewRequested(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        File file = ProjectPathResolver.resolvePath(outputPath).toFile();
        if (!file.exists()) {
            emit("Export preview file not found yet: " + outputPath);
            return;
        }
        String relative = ProjectPathResolver.toRelativeProjectPath(file.toPath());
        Clip previewClip = new Clip(relative, 0, 30.0, 1.0);
        playbackController.setTimeline(List.of(new TimelinePlaybackController.PlaybackClip(previewClip, 0, 0, 0, 30.0)));
        playbackController.seek(0);
        updateState(PlaybackState.STOPPED);
        emit("Export preview loaded: " + outputPath);
    }
}
