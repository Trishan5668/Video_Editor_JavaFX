import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.media.Media;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class TimelineEditingPanel extends BorderPane {
    private static final double LANE_HEIGHT = 88;
    private static final double MIN_TIMELINE_SECONDS = 60;
    private static final double BASE_PIXELS_PER_SECOND = 42;
    private static final double MIN_CLIP_WIDTH = 58;

    private final ProfessionalProject project;
    private final VideoEngine videoEngine;
    private final Consumer<String> statusSink;

    private final Label zoomLabel = new Label("100%");
    private final Label snapStatusLabel = new Label("Snap: ON");
    private final Label proxyStatusLabel = new Label("Proxy: ON");
    private final Label playheadTimeLabel = new Label("00:00:00");

    private final ComboBox<TrimMode> trimModeCombo = new ComboBox<>();
    private final ComboBox<String> multiCamAngleSelector = new ComboBox<>();
    private final TabPane sequenceTabs = new TabPane();
    private final Slider zoomSlider = new Slider(0.4, 4.0, 1.0);
    private final Slider playheadSlider = new Slider(0, MIN_TIMELINE_SECONDS, 0);
    private final Slider autoCutSensitivitySlider = new Slider(5, 80, 27);
    private final ComboBox<String> subtitleSourceLangCombo = new ComboBox<>();
    private final ComboBox<String> subtitleTargetLangCombo = new ComboBox<>();
    private final Label selectedClipNameLabel = new Label("No clip selected");
    private final Label selectedClipTrackLabel = new Label("Track: --");
    private final Label selectedClipStartLabel = new Label("Start: --");
    private final Label selectedClipDurationLabel = new Label("Duration: --");
    private final Label selectedClipSourceRangeLabel = new Label("Source Range: --");

    private final VBox lanesContainer = new VBox(8);
    private final Pane rulerPane = new Pane();
    private final Line playheadLine = new Line();
    private final ScrollPane timelineScrollPane = new ScrollPane(lanesContainer);
    private final Slider timelineNavigatorSlider = new Slider(0, 1, 0);
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(120));
    private final BorderPane timelineTabContent;

    private final Map<Track.TrackType, Pane> lanePaneByType = new HashMap<>();
    private final Map<Clip, Track.TrackType> clipTrackType = new HashMap<>();
    private final TimelineActionController actionController;
    private final Consumer<Integer> tabActivator;

    private boolean snappingEnabled = true;
    private boolean proxyModeEnabled = true;
    private double pixelsPerSecond = BASE_PIXELS_PER_SECOND;
    private double lastTimelineContentWidth = MIN_TIMELINE_SECONDS * BASE_PIXELS_PER_SECOND;
    private String lastGeneratedSubtitlePath = "";
    private boolean timelineRebuildScheduled;

    private Clip selectedClip;
    private final Set<Clip> selectedClips = new LinkedHashSet<>();
    private final WhisperAiService whisperAiService = new WhisperAiService();
    private final PySceneDetectService pySceneDetectService = new PySceneDetectService();

    public TimelineEditingPanel(ProfessionalProject project, VideoEngine videoEngine, Consumer<String> statusSink) {
        this(project, videoEngine, statusSink, null);
    }

    public TimelineEditingPanel(ProfessionalProject project, VideoEngine videoEngine, Consumer<String> statusSink, Consumer<Integer> tabActivator) {
        this.project = project;
        this.videoEngine = videoEngine;
        this.statusSink = statusSink;
        this.actionController = new TimelineActionController();
        this.tabActivator = tabActivator;

        setPadding(new Insets(8));
        setStyle("-fx-background-color: #0f172a;");
        setFocusTraversable(true);

        timelineTabContent = buildTimelineArea();
        setTop(buildTopBar());
        setCenter(buildEditorWorkspace());

        refreshSequenceTabs();
        refreshMultiCamSources();
        rebuildTimelineLanes();
        updatePlayheadVisual();
        updateClipPropertyPanel();
    }

    private VBox buildTopBar() {
        HBox row1 = new HBox(8,
                createToolButton("Trim", "Trim selected clip with in/out handles", e -> openTrimHandlesDialog()),
                createToolButton("Cut", "Cut selected clip at playhead", e -> performBladeAtPlayhead()),
                createToolButton("Split", "Split selected clips at one or more points", e -> performSplitAtSelectedPoints()),
                createToolButton("Merge", "Merge selected video clips into one clip", e -> mergeSelectedTrack()),
                createToolButton("VFX", "Open VFX panel with keyframes, tracking, and chroma key", e -> openVfxPanel()),
                createToolButton("Add Clip", "Import video clip(s) into VIDEO track", e -> quickAddClip()),
                createToolButton("Overlay", "Import overlay media into OVERLAY track", e -> quickAddOverlay()),
                createToolButton("Add Text", "Insert text layer into TEXT track", e -> quickAddText()),
                createToolButton("Add Music", "Import audio into AUDIO track", e -> quickAddMusic()),
                createToolButton("Generate Subtitles", "Generate subtitles for selected media clip", e -> generateSubtitlesForSelection()),
                createToolButton("Export Subtitles", "Export latest generated subtitles as SRT", e -> exportLatestSubtitles()),
                createToolButton("Auto-Cut", "Detect scenes and split selected video clip", e -> autoCutSelectedClip()),
                createToolButton("Snap", "Toggle snapping to guides and clip edges", e -> toggleSnapping()),
                snapStatusLabel,
                createToolButton("Proxy/Raw", "Toggle proxy workflow for performance", e -> toggleProxyMode()),
                proxyStatusLabel
        );
        row1.setAlignment(Pos.CENTER_LEFT);

        trimModeCombo.getItems().setAll(TrimMode.values());
        trimModeCombo.setValue(TrimMode.RAZOR);

        playheadSlider.valueProperty().addListener((obs, oldV, newV) -> updatePlayheadVisual());
        playheadSlider.valueProperty().addListener((obs, oldV, newV) -> PreviewEventBus.publishScrub(newV.doubleValue()));
        zoomSlider.valueProperty().addListener((obs, oldV, newV) -> {
            pixelsPerSecond = BASE_PIXELS_PER_SECOND * newV.doubleValue();
            zoomLabel.setText(String.format("%d%%", Math.round(newV.doubleValue() * 100)));
            rebuildTimelineLanes();
            updatePlayheadVisual();
        });

        HBox row2 = new HBox(8,
                new Label("Edit Mode"), trimModeCombo,
                createToolButton("Apply Edit", "Apply selected trim mode at current playhead", e -> applyTrimToSelectedClip(trimModeCombo.getValue())),
                new Label("Sub Src"), subtitleSourceLangCombo,
                new Label("Sub Target"), subtitleTargetLangCombo,
                new Label("Auto-Cut Threshold"), autoCutSensitivitySlider,
                createToolButton("Open Timeline", "Jump to the dedicated timeline viewport tab", e -> openTimelineTab()),
                createToolButton("New Nested Sequence", "Create nested sequence tab", e -> createNestedSequence()),
                createToolButton("Sync Multi-cam", "Load demo multicam sources", e -> syncMultiCamDemo())
        );
        row2.setAlignment(Pos.CENTER_LEFT);

        row1.setStyle("-fx-background-color: #111827; -fx-background-radius: 8; -fx-padding: 6;");
        row2.setStyle("-fx-background-color: #111827; -fx-background-radius: 8; -fx-padding: 6;");

        subtitleSourceLangCombo.getItems().setAll("Original", "English", "Hindi", "Spanish", "French", "German", "Japanese");
        subtitleSourceLangCombo.setValue("Original");
        subtitleTargetLangCombo.getItems().setAll("Original", "English", "Hindi", "Spanish", "French", "German", "Japanese");
        subtitleTargetLangCombo.setValue("Original");
        autoCutSensitivitySlider.setMajorTickUnit(15);
        autoCutSensitivitySlider.setShowTickLabels(true);
        autoCutSensitivitySlider.setPrefWidth(130);

        return new VBox(8, row1, buildSequenceTabsRow(), row2);
    }

    private BorderPane buildEditorWorkspace() {
        VBox centerColumn = new VBox(10,
                buildEditorWorkspaceSummary(),
                buildClipPropertyPanel()
        );
        VBox.setVgrow(centerColumn.getChildren().get(centerColumn.getChildren().size() - 1), Priority.ALWAYS);

        HBox workspace = new HBox(10, centerColumn, buildRightInspector());
        HBox.setHgrow(centerColumn, Priority.ALWAYS);
        workspace.setAlignment(Pos.TOP_LEFT);

        BorderPane wrapper = new BorderPane(workspace);
        wrapper.setPadding(new Insets(8, 0, 0, 0));
        return wrapper;
    }

    private VBox buildEditorWorkspaceSummary() {
        Label title = new Label("Editor Workspace");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label body = new Label(
                "Tool operations, clip actions, subtitles, multi-cam, and sequence management live here. " +
                        "The actual tracks, scroll container, and ruler are isolated in the Timeline tab " +
                        "to keep viewport state stable during heavy edit bursts."
        );
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #cbd5e1;");

        Button openTimelineButton = createToolButton(
                "Open Timeline Workspace",
                "Switch to the lightweight dedicated timeline viewport",
                e -> openTimelineTab()
        );

        VBox card = new VBox(8, title, body, openTimelineButton);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        return card;
    }

    private VBox buildClipPropertyPanel() {
        selectedClipNameLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");
        selectedClipTrackLabel.setStyle("-fx-text-fill: #cbd5e1;");
        selectedClipStartLabel.setStyle("-fx-text-fill: #cbd5e1;");
        selectedClipDurationLabel.setStyle("-fx-text-fill: #cbd5e1;");
        selectedClipSourceRangeLabel.setStyle("-fx-text-fill: #cbd5e1;");

        HBox routingRow1 = new HBox(8,
                createToolButton("To Video", "Move selected clip to the VIDEO track", e -> movePrimarySelectionToTrack(Track.TrackType.VIDEO)),
                createToolButton("To Overlay", "Move selected clip to the OVERLAY track", e -> movePrimarySelectionToTrack(Track.TrackType.OVERLAY))
        );
        HBox routingRow2 = new HBox(8,
                createToolButton("To Text", "Move selected clip to the TEXT track", e -> movePrimarySelectionToTrack(Track.TrackType.TEXT)),
                createToolButton("To Audio", "Move selected clip to the AUDIO track", e -> movePrimarySelectionToTrack(Track.TrackType.AUDIO)),
                createToolButton("Jump Playhead", "Move playhead to the selected clip start", e -> jumpPlayheadToSelection())
        );

        VBox panel = new VBox(10,
                new Label("Clip Properties"),
                selectedClipNameLabel,
                selectedClipTrackLabel,
                selectedClipStartLabel,
                selectedClipDurationLabel,
                selectedClipSourceRangeLabel,
                routingRow1,
                routingRow2
        );
        panel.setPadding(new Insets(12));
        panel.setStyle("-fx-background-color: #111827; -fx-background-radius: 8; -fx-text-fill: #e2e8f0;");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private HBox buildSequenceTabsRow() {
        sequenceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sequenceTabs.setPrefHeight(42);
        sequenceTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == null) {
                return;
            }
            String sequenceId = (String) newTab.getUserData();
            if (sequenceId != null) {
                project.setActiveSequence(sequenceId);
                refreshMultiCamSources();
                rebuildTimelineLanes();
                emitStatus("Sequence selected: " + newTab.getText());
            }
        });

        Button addSequenceButton = createToolButton("+", "Create nested sequence", e -> createNestedSequence());
        HBox row = new HBox(6, new Label("Sequences"), sequenceTabs, addSequenceButton);
        HBox.setHgrow(sequenceTabs, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private BorderPane buildTimelineArea() {
        rulerPane.setPrefHeight(28);
        rulerPane.setStyle("-fx-background-color: #0b1220; -fx-background-radius: 8;");

        lanesContainer.setPadding(new Insets(8));
        timelineScrollPane.setFitToWidth(false);
        timelineScrollPane.setPannable(true);
        timelineScrollPane.setFocusTraversable(true);
        timelineScrollPane.setStyle("-fx-background: #0b1220; -fx-background-color: #0b1220;");

        timelineNavigatorSlider.setMaxWidth(Double.MAX_VALUE);
        timelineNavigatorSlider.setBlockIncrement(0.08);
        timelineNavigatorSlider.setMajorTickUnit(0.25);
        timelineNavigatorSlider.setShowTickMarks(true);
        timelineNavigatorSlider.setShowTickLabels(false);
        timelineNavigatorSlider.setTooltip(new Tooltip("Timeline Dragger: drag to quickly navigate the timeline"));
        timelineNavigatorSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (Math.abs(timelineScrollPane.getHvalue() - newV.doubleValue()) > 0.003) {
                timelineScrollPane.setHvalue(newV.doubleValue());
            }
        });
        timelineScrollPane.hvalueProperty().addListener((obs, oldV, newV) -> {
            if (Math.abs(timelineNavigatorSlider.getValue() - newV.doubleValue()) > 0.003) {
                timelineNavigatorSlider.setValue(newV.doubleValue());
            }
        });
        timelineScrollPane.viewportBoundsProperty().addListener((obs, oldV, newV) -> updateTimelineDraggerState());
        lanesContainer.widthProperty().addListener((obs, oldV, newV) -> updateTimelineDraggerState());

        timelineScrollPane.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                deleteSelectedClips();
                e.consume();
            }
        });

        playheadLine.setStroke(Color.web("#f59e0b"));
        playheadLine.setStrokeWidth(1.8);

        StackPane timelineStack = new StackPane(timelineScrollPane, playheadLine);
        StackPane.setAlignment(playheadLine, Pos.TOP_LEFT);

        HBox draggerRow = new HBox(8, new Label("Dragger"), timelineNavigatorSlider);
        draggerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(timelineNavigatorSlider, Priority.ALWAYS);

        BorderPane wrapper = new BorderPane();
        wrapper.setTop(rulerPane);
        wrapper.setCenter(new VBox(6, buildTimelineViewportToolbar(), timelineStack, draggerRow));
        wrapper.setPadding(new Insets(8, 8, 8, 0));
        return wrapper;
    }

    private HBox buildTimelineViewportToolbar() {
        HBox row = new HBox(8,
                new Label("Playhead"), playheadSlider, playheadTimeLabel,
                new Label("Zoom"), zoomSlider, zoomLabel
        );
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        HBox.setHgrow(playheadSlider, Priority.ALWAYS);
        HBox.setHgrow(zoomSlider, Priority.ALWAYS);
        return row;
    }

    public Node getTimelineTabContent() {
        return timelineTabContent;
    }

    private void openTimelineTab() {
        if (tabActivator != null) {
            tabActivator.accept(ProfessionalWorkspaceView.TAB_TIMELINE);
        }
        emitStatus("Timeline viewport opened.");
    }

    private void rebuildTimelineLanes() {
        if (timelineRebuildScheduled) {
            return;
        }
        timelineRebuildScheduled = true;

        // Capture previous scroll state (absolute pixels) so it can be preserved across content width changes.
        double prevContentWidth = Math.max(lastTimelineContentWidth + 82, lanesContainer.getWidth());
        double prevViewportWidth = timelineScrollPane.getViewportBounds() == null ? 0 : timelineScrollPane.getViewportBounds().getWidth();
        double prevHValue = timelineScrollPane.getHvalue();
        double prevScrollable = Math.max(0.0, prevContentWidth - prevViewportWidth);
        double prevScrollPixels = prevScrollable <= 0 ? 0.0 : prevHValue * prevScrollable;

        timelineRebuildScheduled = true;
        Platform.runLater(() -> {
            timelineRebuildScheduled = false;
            // Rebuild layout (recomputes lastTimelineContentWidth)
            rebuildTimelineLanesInternal();

            // After a layout pass, restore scroll position proportionally to previous absolute pixels.
            Platform.runLater(() -> {
                double newContentWidth = Math.max(lastTimelineContentWidth + 82, lanesContainer.getWidth());
                double newViewportWidth = timelineScrollPane.getViewportBounds() == null ? 0 : timelineScrollPane.getViewportBounds().getWidth();
                double newScrollable = Math.max(0.0, newContentWidth - newViewportWidth);

                double restoredHValue;
                if (newScrollable <= 0) {
                    restoredHValue = 0.0;
                } else {
                    restoredHValue = prevScrollPixels / newScrollable;
                }
                restoredHValue = Math.max(0.0, Math.min(1.0, restoredHValue));

                timelineScrollPane.setHvalue(restoredHValue);
                updateTimelineDraggerState();
            });
        });
    }

    private void rebuildTimelineLanesInternal() {
        sanitizeSelection();
        lanesContainer.getChildren().clear();
        lanePaneByType.clear();
        clipTrackType.clear();
        rebuildRuler();
        double timelineContentWidth = computeTimelineContentWidth();
        lastTimelineContentWidth = timelineContentWidth;
        lanesContainer.setMinWidth(timelineContentWidth + 82);
        lanesContainer.setPrefWidth(timelineContentWidth + 82);

        Sequence active = project.getActiveSequence();
        TimelineModel timeline = active == null ? new TimelineModel() : active.getTimelineModel();
        timeline.ensureTrack(Track.TrackType.OVERLAY);
        timeline.ensureTrack(Track.TrackType.TEXT);
        timeline.ensureTrack(Track.TrackType.AUDIO);

        for (Track track : timeline.getTracks()) {
            Pane laneContent = new Pane();
            laneContent.setMinHeight(LANE_HEIGHT);
            laneContent.setPrefHeight(LANE_HEIGHT);
            laneContent.setMinWidth(timelineContentWidth);
            laneContent.setPrefWidth(timelineContentWidth);
            laneContent.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 8;");

            renderTrackClips(track, laneContent);
            attachLaneDropBehavior(track.getType(), laneContent);

            Label trackLabel = new Label(track.getType().name());
            trackLabel.setMinWidth(74);
            trackLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: bold;");

            HBox row = new HBox(8, trackLabel, laneContent);
            HBox.setHgrow(laneContent, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);

            lanesContainer.getChildren().add(row);
            lanePaneByType.put(track.getType(), laneContent);
        }

        updatePlayheadVisual();
        updateClipPropertyPanel();
        Platform.runLater(this::updateTimelineDraggerState);
    }

    private void updateTimelineDraggerState() {
        // Use the most up-to-date content width: prefer the measured lanesContainer width when available
        double measuredWidth = lanesContainer.getWidth();
        double contentWidth = Math.max(lastTimelineContentWidth + 82, measuredWidth);

        double viewportWidth = timelineScrollPane.getViewportBounds() == null
                ? 0
                : timelineScrollPane.getViewportBounds().getWidth();

        double scrollable = Math.max(0.0, contentWidth - viewportWidth);
        boolean canScroll = scrollable > 1.0;

        timelineNavigatorSlider.setDisable(!canScroll);
        timelineNavigatorSlider.setOpacity(canScroll ? 1.0 : 0.5);

        // Keep navigator slider and scroll pane synchronized. Use small threshold to avoid feedback loops.
        double h = timelineScrollPane.getHvalue();
        if (Math.abs(timelineNavigatorSlider.getValue() - h) > 0.003) {
            timelineNavigatorSlider.setValue(h);
        }
    }

    private VBox buildRightInspector() {
        multiCamAngleSelector.setItems(FXCollections.observableArrayList());
        multiCamAngleSelector.setPromptText("Camera Angle");

        Button cutToAngleBtn = createToolButton("Cut To Angle", "Insert multicam cut at playhead", e -> {
            String selectedAngle = multiCamAngleSelector.getValue();
            Sequence activeSequence = project.getActiveSequence();
            if (selectedAngle == null || activeSequence == null) {
                emitStatus("Select a camera angle first.");
                return;
            }
            long frame = activeSequence.getFrameRate().toFrames(playheadSlider.getValue());
            activeSequence.getMultiCamEditor().addCut(frame, selectedAngle);
            emitStatus("Inserted multi-cam cut at " + frame + "f to " + selectedAngle);
        });

        TextField searchField = new TextField();
        searchField.setPromptText("Search clips / tags");
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            searchDebounce.setOnFinished(e -> highlightBySearch(newV));
            searchDebounce.playFromStart();
        });

        TitledPane nestedPane = new TitledPane("Nested Sequence Browser", new VBox(6,
                new Label("• Tabs represent nested sequences."),
                new Label("• Collapse this pane for focus editing."),
                new Label("• Double-click tab workflow can be extended later.")
        ));
        nestedPane.setExpanded(true);

        VBox panel = new VBox(10,
                new Label("Multi-cam"),
                multiCamAngleSelector,
                cutToAngleBtn,
                new Label("Search"),
                searchField,
                nestedPane
        );
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(290);
        panel.setStyle("-fx-background-color: #111827; -fx-background-radius: 8; -fx-text-fill: #e2e8f0;");
        return panel;
    }

    private void renderTrackClips(Track track, Pane lanePane) {
        List<Clip> clips = new ArrayList<>(track.getClips());
        clips.sort(Comparator.comparingDouble(Clip::getTimelineStartSeconds));
        for (int i = 0; i < clips.size(); i++) {
            Clip clip = clips.get(i);
            clipTrackType.put(clip, track.getType());

            double startSec = clip.getTimelineStartSeconds();
            double width = Math.max(MIN_CLIP_WIDTH, clip.getDurationSeconds() * pixelsPerSecond);

            StackPane clipNode = createClipNode(track.getType(), clip, i, width);
            clipNode.setLayoutX(startSec * pixelsPerSecond);
            clipNode.setLayoutY(8);

            attachClipDragBehavior(clipNode, clip, lanePane, width);
            lanePane.getChildren().add(clipNode);
        }
    }

    private StackPane createClipNode(Track.TrackType type, Clip clip, int index, double width) {
        Rectangle body = new Rectangle(width, LANE_HEIGHT - 16);
        body.setArcWidth(8);
        body.setArcHeight(8);
        body.setFill(colorForType(type));
        body.setStroke(selectedClips.contains(clip) ? Color.web("#fde047") : Color.web("#0b1220"));
        body.setStrokeWidth(selectedClips.contains(clip) ? 2.2 : 1.0);

        Label title = new Label(shortClipLabel(type, clip, index));
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold;");

        Label meta = new Label(String.format("%.1fs", clip.getDurationSeconds()));
        meta.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 11px;");

        VBox text = new VBox(2, title, meta);
        text.setPadding(new Insets(6));
        StackPane node = new StackPane(body, text);
        node.setUserData(clip);
        node.setCursor(Cursor.HAND);
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            timelineScrollPane.requestFocus();
            handleClipSelection(clip, e.isShiftDown(), e.isControlDown());
            rebuildTimelineLanes();
            PreviewEventBus.publishClipSelected(clip.getSourcePath());
            emitStatus("Selected " + selectedClips.size() + " clip(s): " + shortClipLabel(type, clip, index));
        });
        return node;
    }

    private void attachClipDragBehavior(StackPane clipNode, Clip clip, Pane lanePane, double width) {
        final double[] dragOffset = new double[1];
        clipNode.setOnMousePressed(e -> {
            timelineScrollPane.requestFocus();
            var localPoint = lanePane.sceneToLocal(e.getSceneX(), e.getSceneY());
            dragOffset[0] = localPoint.getX() - clipNode.getLayoutX();
        });
        clipNode.setOnMouseDragged(e -> {
            var localPoint = lanePane.sceneToLocal(e.getSceneX(), e.getSceneY());
            double x = localPoint.getX() - dragOffset[0];
            if (snappingEnabled) {
                x = snapXToGuides(x, width, lanePane, clipNode);
            }
            x = Math.max(0, Math.min(x, Math.max(0, lanePane.getWidth() - width)));
            clipNode.setLayoutX(x);
        });
        clipNode.setOnMouseReleased(e -> {
            double newSec = Math.max(0, clipNode.getLayoutX() / pixelsPerSecond);
            Track.TrackType currentType = clipTrackType.getOrDefault(clip, Track.TrackType.VIDEO);
            Track.TrackType targetType = resolveDropTargetTrackType(e.getSceneY(), currentType);
            Sequence active = project.getActiveSequence();
            if (active == null) {
                return;
            }
            TimelineModel timeline = active.getTimelineModel();
            int sourceTrackIndex = timeline.findTrackIndexOfClip(clip);
            int sourceClipIndex = timeline.indexOfInTrack(sourceTrackIndex, clip);
            if (sourceTrackIndex < 0 || sourceClipIndex < 0) {
                return;
            }
            Clip updated = clip.withTimelineStart(newSec);
            if (targetType != currentType) {
                timeline.removeClipFromTrack(sourceTrackIndex, sourceClipIndex);
                int targetTrackIndex = timeline.ensureTrack(targetType);
                timeline.addClipToTrack(targetTrackIndex, updated);
                clipTrackType.put(updated, targetType);
                setSingleSelection(updated);
            } else {
                timeline.replaceClipInTrack(sourceTrackIndex, sourceClipIndex, updated);
                clipTrackType.put(updated, currentType);
                setSingleSelection(updated);
            }
            reorderTrackByVisualPositions(clipTrackType.getOrDefault(updated, currentType));
            emitStatus("Clip moved to " + String.format("%.2fs", newSec)
                    + (snappingEnabled ? " (snap)" : ""));
            PreviewEventBus.publishTimelineChanged();
            PreviewEventBus.publishScrub(newSec);
            rebuildTimelineLanes();
        });
    }

    private void reorderTrackByVisualPositions(Track.TrackType trackType) {
        Sequence active = project.getActiveSequence();
        if (active == null || trackType == null) {
            return;
        }
        TimelineModel timeline = active.getTimelineModel();
        int trackIndex = timeline.findFirstTrackIndexByType(trackType);
        if (trackIndex < 0) {
            return;
        }

        List<Clip> sorted = new ArrayList<>(timeline.getClipsInTrack(trackIndex));
        sorted.sort(Comparator.comparingDouble(Clip::getTimelineStartSeconds));

        while (!timeline.getClipsInTrack(trackIndex).isEmpty()) {
            timeline.removeClipFromTrack(trackIndex, 0);
        }
        for (Clip clip : sorted) {
            timeline.addClipToTrack(trackIndex, clip);
        }
    }

    private void rebuildRuler() {
        rulerPane.getChildren().clear();
        double timelineSeconds = computeTimelineSpanSeconds();
        double width = timelineSeconds * pixelsPerSecond;
        rulerPane.setMinWidth(width);
        rulerPane.setPrefWidth(width);

        for (int sec = 0; sec <= (int) timelineSeconds; sec += 5) {
            double x = sec * pixelsPerSecond;
            Line tick = new Line(x, 0, x, sec % 10 == 0 ? 20 : 12);
            tick.setStroke(Color.web("#64748b"));
            rulerPane.getChildren().add(tick);

            if (sec % 10 == 0) {
                Label l = new Label(formatTime(sec));
                l.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");
                l.setLayoutX(x + 3);
                l.setLayoutY(8);
                rulerPane.getChildren().add(l);
            }
        }
    }

    private void updatePlayheadVisual() {
        double max = Math.max(MIN_TIMELINE_SECONDS, computeTimelineSpanSeconds());
        playheadSlider.setMax(max);
        if (playheadSlider.getValue() > max) {
            playheadSlider.setValue(max);
        }
        double x = playheadSlider.getValue() * pixelsPerSecond + 82;
        double height = Math.max(100, lanesContainer.getHeight() + 28);
        playheadLine.setStartX(x);
        playheadLine.setEndX(x);
        playheadLine.setStartY(0);
        playheadLine.setEndY(height);

        playheadTimeLabel.setText(formatTime((int) Math.round(playheadSlider.getValue())));
    }

    private void applyTrimToSelectedClip(TrimMode mode) {
        Sequence activeSequence = project.getActiveSequence();
        if (activeSequence == null || !hasSelectedClip()) {
            showWarning("No clip selected", "Select a clip to apply trim edit.");
            return;
        }

        selectedClip = getPrimarySelectedClip();

        int trackIndex = activeSequence.getTimelineModel().findTrackIndexOfClip(selectedClip);
        int clipIndex = activeSequence.getTimelineModel().indexOfInTrack(trackIndex, selectedClip);
        if (trackIndex < 0 || clipIndex < 0) {
            emitStatus("Clip not found in active timeline.");
            return;
        }

        TrimMode resolved = mode == null ? TrimMode.RAZOR : mode;
        long frame = activeSequence.getFrameRate().toFrames(playheadSlider.getValue());
        try {
            activeSequence.getPrecisionTrimService().applyTrim(
                    activeSequence.getTimelineModel(),
                    trackIndex,
                    clipIndex,
                    resolved,
                    frame,
                    activeSequence.getFrameRate()
            );
        } catch (Exception ex) {
            logAndShowError("Trim failed", ex);
            return;
        }

        emitStatus("Applied " + resolved + " edit at frame " + frame);
        clearSelection();
        PreviewEventBus.publishTimelineChanged();
        PreviewEventBus.publishRefresh("Timeline trim/cut/split applied");
        rebuildTimelineLanes();
    }

    public void performBladeAtPlayhead() {
        applyTrimToSelectedClip(TrimMode.RAZOR);
    }

    public void deleteSelectedClips() {
        Sequence active = project.getActiveSequence();
        sanitizeSelection();
        if (active == null || selectedClips.isEmpty()) {
            emitStatus("No selected clip to delete.");
            return;
        }
        TimelineModel model = active.getTimelineModel();
        int deleted = 0;
        for (Clip target : new ArrayList<>(selectedClips)) {
            int trackIndex = model.findTrackIndexOfClip(target);
            int clipIndex = model.indexOfInTrack(trackIndex, target);
            if (trackIndex >= 0 && clipIndex >= 0) {
                model.removeClipFromTrack(trackIndex, clipIndex);
                deleted++;
            }
        }
        clearSelection();
        if (deleted > 0) {
            PreviewEventBus.publishTimelineChanged();
            PreviewEventBus.publishRefresh("Deleted selected clip(s)");
            rebuildTimelineLanes();
            emitStatus("Deleted " + deleted + " clip(s).");
        }
        updateClipPropertyPanel();
    }

    private void mergeSelectedTrack() {
        if (selectedClips.size() < 2) {
            showWarning("Need multiple clips", "Select at least two clips to merge.");
            return;
        }
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return;
        }
        Clip firstSelected = getPrimarySelectedClip();
        int trackIndex = active.getTimelineModel().findTrackIndexOfClip(firstSelected);
        List<Clip> selectedInTrack = new ArrayList<>();
        for (Clip clip : active.getTimelineModel().getClipsInTrack(trackIndex)) {
            if (selectedClips.contains(clip)) {
                selectedInTrack.add(clip);
            }
        }
        if (selectedInTrack.size() < 2) {
            showWarning("Invalid merge selection", "Select at least two clips from the same track.");
            return;
        }

        Track.TrackType type = active.getTimelineModel().getTracks().get(trackIndex).getType();
        if (type != Track.TrackType.VIDEO || videoEngine == null) {
            emitStatus("Merge requires VIDEO track and available backend engine.");
            return;
        }
        try {
            TimelineModel temp = new TimelineModel();
            while (temp.getClips().size() > 0) {
                temp.removeClip(0);
            }
            for (Clip clip : selectedInTrack) {
                temp.addClip(clip);
            }

            String output = "exports/merge_" + System.currentTimeMillis() + ".mp4";
            videoEngine.renderTimeline(temp, output);

            List<Clip> original = new ArrayList<>(active.getTimelineModel().getClipsInTrack(trackIndex));
            while (!active.getTimelineModel().getClipsInTrack(trackIndex).isEmpty()) {
                active.getTimelineModel().removeClipFromTrack(trackIndex, 0);
            }
            double totalDuration = selectedInTrack.stream().mapToDouble(Clip::getDurationSeconds).sum();
            double earliestTimelineStart = selectedInTrack.stream()
                    .mapToDouble(Clip::getTimelineStartSeconds)
                    .min()
                    .orElse(0);
            Clip merged = new Clip(output, 0, Math.max(0.1, totalDuration), 1.0)
                    .withTimelineStart(earliestTimelineStart);

            boolean inserted = false;
            for (Clip clip : original) {
                if (selectedClips.contains(clip)) {
                    if (!inserted) {
                        active.getTimelineModel().addClipToTrack(trackIndex, merged);
                        inserted = true;
                    }
                } else {
                    active.getTimelineModel().addClipToTrack(trackIndex, clip);
                }
            }

            setSingleSelection(merged);
            registerAssetIfNeeded(output, MediaType.VIDEO);
            PreviewEventBus.publishTimelineChanged();
            PreviewEventBus.publishClipSelected(output);
            rebuildTimelineLanes();
            emitStatus("Merged " + selectedInTrack.size() + " clips via backend -> " + output);
        } catch (Exception ex) {
            logAndShowError("Merge failed", ex);
        }
    }

    private void generateSubtitlesForSelection() {
        if (!hasSelectedClip()) {
            showWarning("No clip selected", "Select a clip to generate subtitles.");
            return;
        }
        selectedClip = getPrimarySelectedClip();
        if (!isSubtitleEligibleMedia(selectedClip.getSourcePath())) {
            emitStatus("Subtitles currently support audio/video clips only.");
            return;
        }
        try {
            SubtitleGenerationResult result = whisperAiService.generateSubtitles(
                    selectedClip.getSourcePath(),
                    subtitleSourceLangCombo.getValue(),
                    subtitleTargetLangCombo.getValue()
            );
            lastGeneratedSubtitlePath = result.getSubtitlePath();
            overlaySubtitlesOnTimeline(result.getCues());
            registerAssetIfNeeded(result.getSubtitlePath(), MediaType.SEQUENCE);
            PreviewEventBus.publishTimelineChanged();
            rebuildTimelineLanes();
            emitStatus("Subtitles generated: " + result.getSubtitlePath() + " (" + result.getCues().size() + " cues)");
        } catch (AiExecutionException ex) {
            showImportError("Subtitle generation failed", ex.getMessage());
            emitStatus("Generate Subtitles failed");
        }
    }

    private void exportLatestSubtitles() {
        if (lastGeneratedSubtitlePath == null || lastGeneratedSubtitlePath.isBlank()) {
            emitStatus("No generated subtitles found. Run Generate Subtitles first.");
            return;
        }
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Subtitles (.srt)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SRT", "*.srt"));
            chooser.setInitialFileName(ProjectPathResolver.fileNameOf(lastGeneratedSubtitlePath));
            Window owner = getScene() == null ? null : getScene().getWindow();
            File saveTo = chooser.showSaveDialog(owner);
            if (saveTo == null) {
                return;
            }
            String exported = whisperAiService.exportSubtitleTo(
                    ProjectPathResolver.resolvePath(lastGeneratedSubtitlePath),
                    Path.of(saveTo.getAbsolutePath())
            );
            emitStatus("Subtitle exported: " + exported);
        } catch (AiExecutionException ex) {
            showImportError("Subtitle export failed", ex.getMessage());
            emitStatus("Subtitle export failed");
        }
    }

    private void autoCutSelectedClip() {
        if (!hasSelectedClip()) {
            showWarning("No clip selected", "Select a clip before running Auto-Cut.");
            return;
        }
        selectedClip = getPrimarySelectedClip();
        if (!isVideoFile(selectedClip.getSourcePath())) {
            emitStatus("Auto-Cut supports video clips only.");
            return;
        }
        Sequence active = project.getActiveSequence();
        if (active == null) {
            emitStatus("No active sequence.");
            return;
        }
        try {
            SceneDetectionResult result = pySceneDetectService.detectScenes(
                    selectedClip.getSourcePath(),
                    autoCutSensitivitySlider.getValue()
            );
            int splits = applySceneCutsToSelectedClip(active, result.getCutPointsSeconds());
            PreviewEventBus.publishTimelineChanged();
            rebuildTimelineLanes();
            emitStatus("Auto-Cut complete: " + splits + " splits applied.");
        } catch (AiExecutionException ex) {
            showImportError("Auto-Cut failed", ex.getMessage());
            emitStatus("Auto-Cut failed");
        }
    }

    private int applySceneCutsToSelectedClip(Sequence active, List<Double> cutPoints) {
        if (cutPoints == null || cutPoints.isEmpty() || selectedClip == null) {
            return 0;
        }
        TimelineModel model = active.getTimelineModel();
        int trackIndex = model.findTrackIndexOfClip(selectedClip);
        int clipIndex = model.indexOfInTrack(trackIndex, selectedClip);
        if (trackIndex < 0 || clipIndex < 0) {
            return 0;
        }

        double start = selectedClip.getStartSeconds();
        double end = selectedClip.getEndSeconds();
        List<Double> validCuts = new ArrayList<>();
        for (Double cut : cutPoints) {
            if (cut == null) {
                continue;
            }
            double c = cut.doubleValue();
            if (c > start + 0.03 && c < end - 0.03) {
                validCuts.add(c);
            }
        }
        validCuts.sort(Comparator.reverseOrder());

        int count = 0;
        for (Double cut : validCuts) {
            try {
                model.splitClipAtSourceTimeInTrack(trackIndex, clipIndex, cut);
                count++;
            } catch (Exception ignored) {
            }
        }
        clearSelection();
        return count;
    }

    private void overlaySubtitlesOnTimeline(List<SubtitleCue> cues) {
        if (cues == null || cues.isEmpty()) {
            return;
        }
        Sequence active = ensureActiveSequence();
        int textTrack = active.getTimelineModel().ensureTrack(Track.TrackType.TEXT);
        double timelineStart = selectedClip == null ? 0.0 : selectedClip.getTimelineStartSeconds();
        double clipDuration = selectedClip == null ? 0 : selectedClip.getDurationSeconds();

        for (SubtitleCue cue : cues) {
            if (cue == null) {
                continue;
            }
            double cueStart = Math.max(0, cue.getStartSeconds());
            double cueEnd = Math.max(cueStart + 0.05, cue.getEndSeconds());
            if (clipDuration > 0) {
                cueStart = Math.min(cueStart, clipDuration);
                cueEnd = Math.min(cueEnd, clipDuration);
            }
            if (cueEnd - cueStart < 0.05 || cue.getText().isBlank()) {
                continue;
            }
            Clip subtitleClip = new Clip("__SUBTITLE__", 0, cueEnd - cueStart, 1.0)
                    .withTimelineStart(timelineStart + cueStart)
                    .withText(cue.getText())
                    .withTextFontSize(32)
                    .withPosition(72, 420)
                    .withZIndex(200);
            active.getTimelineModel().addClipToTrack(textTrack, subtitleClip);
        }
    }

    private boolean isSubtitleEligibleMedia(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return false;
        }
        String p = sourcePath.toLowerCase();
        return isVideoFile(p) || p.endsWith(".wav") || p.endsWith(".mp3") || p.endsWith(".aac") || p.endsWith(".flac") || p.endsWith(".m4a");
    }

    private boolean isVideoFile(String sourcePath) {
        if (sourcePath == null) {
            return false;
        }
        String p = sourcePath.toLowerCase();
        return p.endsWith(".mp4") || p.endsWith(".mov") || p.endsWith(".mxf") || p.endsWith(".mkv") || p.endsWith(".avi");
    }

    public void quickAddClip() {
        actionController.importVideoClipFromDisk();
    }

    public void quickAddOverlay() {
        actionController.importOverlayFromDisk();
    }

    public void quickAddText() {
        actionController.insertTextOverlayFromDialog();
    }

    public void quickAddMusic() {
        actionController.importMusicFromDisk();
    }

    private void attachLaneDropBehavior(Track.TrackType type, Pane lanePane) {
        lanePane.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        lanePane.setOnDragDropped(event -> {
            boolean success = false;
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                success = actionController.importDroppedFiles(type, db, event);
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void addClipFromFile(File file, Track.TrackType type, double fallbackDuration, double volume) {
        if (file == null) {
            return;
        }
        String storedPath;
        try {
            storedPath = ProjectPathResolver.importIntoAssets(file);
        } catch (Exception ex) {
            showImportError("Unable to import media", ex.getMessage());
            emitStatus("Import failed: " + file.getName());
            return;
        }

        Sequence active = ensureActiveSequence();
        int track = active.getTimelineModel().ensureTrack(type);
        double duration = resolveMediaDurationSeconds(storedPath, type, fallbackDuration);
        double timelineStart = active.getTimelineModel().computeTrackEndSeconds(track);
        Clip clip = new Clip(storedPath, 0, duration, volume).withTimelineStart(timelineStart);
        if (type == Track.TrackType.OVERLAY) {
            clip = clip.withOpacity(0.9).withScale(1.0).withPosition(120, 70).withZIndex(100);
        }
        active.getTimelineModel().addClipToTrack(track, clip);
        setSingleSelection(clip);
        registerAssetIfNeeded(storedPath, mediaTypeForTrack(type));
        PreviewEventBus.publishTimelineChanged();
        PreviewEventBus.publishClipSelected(storedPath);
        rebuildTimelineLanes();
    }

    private void registerAssetIfNeeded(String sourcePath, MediaType mediaType) {
        boolean exists = project.getAssets().values().stream()
                .anyMatch(asset -> sourcePath.equalsIgnoreCase(asset.getSourcePath()));
        if (!exists) {
            String assetId = "asset-" + System.currentTimeMillis();
            project.registerAsset(new ProjectAsset(assetId, sourcePath, mediaType));
        }
    }

    private MediaType mediaTypeForTrack(Track.TrackType type) {
        return switch (type) {
            case AUDIO -> MediaType.AUDIO;
            case OVERLAY -> MediaType.IMAGE;
            default -> MediaType.VIDEO;
        };
    }

    private boolean isValidByTrackType(File file, Track.TrackType trackType) {
        if (file == null) {
            return false;
        }
        String p = file.getName().toLowerCase();
        return switch (trackType) {
            case VIDEO -> p.endsWith(".mp4") || p.endsWith(".mov") || p.endsWith(".mxf") || p.endsWith(".mkv");
            case OVERLAY -> p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg")
                    || p.endsWith(".webp") || p.endsWith(".bmp")
                    || p.endsWith(".mp4") || p.endsWith(".mov");
            case AUDIO -> p.endsWith(".wav") || p.endsWith(".mp3") || p.endsWith(".aac") || p.endsWith(".flac");
            case TEXT -> true;
        };
    }

    private class TimelineActionController {
        /** Add Clip button action: open native chooser and import selected video files into the VIDEO track. */
        void importVideoClipFromDisk() {
            List<File> files = chooseFiles(
                    "Import Video Clip(s)",
                    new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.mxf", "*.mkv"));
            if (files.isEmpty()) {
                return;
            }
            int imported = 0;
            for (File file : files) {
                if (!isValidByTrackType(file, Track.TrackType.VIDEO)) {
                    continue;
                }
                addClipFromFile(file, Track.TrackType.VIDEO, 30.0, 1.0);
                imported++;
            }
            emitStatus(imported > 0 ? "Imported " + imported + " video clip(s)." : "No supported video files selected.");
        }

        /** Overlay button action: open native chooser and import image/video overlays into OVERLAY track. */
        void importOverlayFromDisk() {
            List<File> files = chooseFiles(
                    "Import Overlay",
                    new FileChooser.ExtensionFilter("Overlay Media", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp", "*.mp4", "*.mov"));
            if (files.isEmpty()) {
                return;
            }
            int imported = 0;
            for (File file : files) {
                if (!isValidByTrackType(file, Track.TrackType.OVERLAY)) {
                    continue;
                }
                addClipFromFile(file, Track.TrackType.OVERLAY, 30.0, 1.0);
                imported++;
            }
            emitStatus(imported > 0 ? "Imported " + imported + " overlay clip(s)." : "No supported overlay files selected.");
        }

        /** Add Music button action: open native chooser and import audio files into AUDIO track. */
        void importMusicFromDisk() {
            List<File> files = chooseFiles(
                    "Import Music",
                    new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aac", "*.flac"));
            if (files.isEmpty()) {
                return;
            }
            int imported = 0;
            for (File file : files) {
                if (!isValidByTrackType(file, Track.TrackType.AUDIO)) {
                    continue;
                }
                addClipFromFile(file, Track.TrackType.AUDIO, 30.0, 0.8);
                imported++;
            }
            emitStatus(imported > 0 ? "Imported " + imported + " music file(s)." : "No supported audio files selected.");
        }

        /** Add Text button action: open text input dialog and insert editable text layer in TEXT track. */
        void insertTextOverlayFromDialog() {
            TextInputDialog dialog = new TextInputDialog("New Title");
            dialog.setTitle("Add Text Layer");
            dialog.setHeaderText("Insert Text Overlay");
            dialog.setContentText("Text:");
            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty() || result.get().isBlank()) {
                emitStatus("Text insertion cancelled.");
                return;
            }
            Sequence active = ensureActiveSequence();
            int track = active.getTimelineModel().ensureTrack(Track.TrackType.TEXT);
            double timelineStart = active.getTimelineModel().computeTrackEndSeconds(track);
            Clip clip = new Clip("__TEXT__", 0, 4.0, 1.0)
                    .withTimelineStart(timelineStart)
                    .withText(result.get().trim())
                    .withTextFontSize(44)
                    .withPosition(220, 120)
                    .withZIndex(150);
            active.getTimelineModel().addClipToTrack(track, clip);
            setSingleSelection(clip);
            PreviewEventBus.publishTimelineChanged();
            PreviewEventBus.publishClipSelected(clip.getSourcePath());
            rebuildTimelineLanes();
            emitStatus("Text layer inserted.");
        }

        /** Timeline drag/drop action: import dropped files to a lane's corresponding track type with snapping preserved. */
        boolean importDroppedFiles(Track.TrackType laneType, Dragboard dragboard, DragEvent event) {
            if (dragboard == null || !dragboard.hasFiles()) {
                return false;
            }
            int imported = 0;
            for (File file : dragboard.getFiles()) {
                if (!isValidByTrackType(file, laneType)) {
                    continue;
                }
                addClipFromFile(file, laneType, 30.0, laneType == Track.TrackType.AUDIO ? 0.8 : 1.0);
                imported++;
            }
            if (imported > 0) {
                emitStatus("Dropped " + imported + " file(s) into " + laneType + " track.");
            } else {
                emitStatus("Dropped files are not supported for " + laneType + " track.");
            }
            return imported > 0;
        }
    }

    private List<File> chooseFiles(String title, FileChooser.ExtensionFilter extensionFilter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(extensionFilter);
        File assetsDir = ProjectPathResolver.assetsDir().toFile();
        if (assetsDir.exists() && assetsDir.isDirectory()) {
            chooser.setInitialDirectory(assetsDir);
        }
        Window owner = getScene() == null ? null : getScene().getWindow();
        List<File> files = chooser.showOpenMultipleDialog(owner);
        return files == null ? List.of() : files;
    }

    private void showImportError(String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Import Error");
        alert.setHeaderText(header);
        alert.setContentText(details == null ? "Unknown media import error." : details);
        alert.showAndWait();
    }

    private Sequence ensureActiveSequence() {
        Sequence active = project.getActiveSequence();
        if (active != null) {
            return active;
        }
        Sequence created = new Sequence(UUID.randomUUID().toString(), "Main Sequence", new FrameRate(30));
        project.addSequence(created);
        refreshSequenceTabs();
        return created;
    }

    private void createNestedSequence() {
        Sequence parent = ensureActiveSequence();
        String newId = UUID.randomUUID().toString();
        Sequence nested = new Sequence(newId, "Nested " + (project.getSequences().size() + 1), new FrameRate(30));
        project.addSequence(nested);
        parent.embedSequence(newId);
        refreshSequenceTabs();
        emitStatus("Created nested sequence: " + nested.getName());
    }

    private void refreshSequenceTabs() {
        sequenceTabs.getTabs().clear();
        for (Sequence sequence : project.getSequences().values()) {
            Tab tab = new Tab(sequence.getName());
            tab.setUserData(sequence.getSequenceId());
            sequenceTabs.getTabs().add(tab);
            if (project.getActiveSequence() != null
                    && project.getActiveSequence().getSequenceId().equals(sequence.getSequenceId())) {
                sequenceTabs.getSelectionModel().select(tab);
            }
        }
    }

    private void syncMultiCamDemo() {
        Sequence active = ensureActiveSequence();
        active.getMultiCamEditor().addSource(new MultiCamSource("Cam-A", "camA", 0));
        active.getMultiCamEditor().addSource(new MultiCamSource("Cam-B", "camB", 4));
        active.getMultiCamEditor().addSource(new MultiCamSource("Cam-C", "camC", 8));
        refreshMultiCamSources();
        emitStatus("Multi-cam sources synced: 3 angles");
    }

    private void refreshMultiCamSources() {
        multiCamAngleSelector.getItems().clear();
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return;
        }
        active.getMultiCamEditor().getSources().forEach(source -> multiCamAngleSelector.getItems().add(source.getSourceId()));
        if (!multiCamAngleSelector.getItems().isEmpty()) {
            multiCamAngleSelector.getSelectionModel().select(0);
        }
    }

    private void highlightBySearch(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        for (Pane lane : lanePaneByType.values()) {
            for (Node node : lane.getChildren()) {
                if (!(node instanceof StackPane clipNode) || !(clipNode.getChildren().get(1) instanceof VBox labels)) {
                    continue;
                }
                String text = labels.getChildren().stream()
                        .filter(Label.class::isInstance)
                        .map(Label.class::cast)
                        .map(Label::getText)
                        .reduce("", (a, b) -> a + " " + b)
                        .toLowerCase();
                clipNode.setOpacity(q.isEmpty() || text.contains(q) ? 1.0 : 0.22);
            }
        }
    }

    public void toggleSnapping() {
        snappingEnabled = !snappingEnabled;
        snapStatusLabel.setText("Snap: " + (snappingEnabled ? "ON" : "OFF"));
        emitStatus("Timeline snapping " + (snappingEnabled ? "enabled" : "disabled"));
    }

    public void toggleProxyMode() {
        proxyModeEnabled = !proxyModeEnabled;
        proxyStatusLabel.setText("Proxy: " + (proxyModeEnabled ? "ON" : "OFF"));
        project.getProxyMediaManager().setPreferProxyForEditing(proxyModeEnabled);
        emitStatus(proxyModeEnabled ? "Proxy workflow enabled" : "Raw workflow enabled for editing");
    }

    private Button createToolButton(String text, String tooltipText, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.setOnAction(action);
        button.setStyle("-fx-background-color: #1f2937; -fx-text-fill: #e5e7eb; -fx-background-radius: 6;");
        if (tooltipText != null && !tooltipText.isBlank()) {
            button.setTooltip(new Tooltip(tooltipText));
        }
        return button;
    }

    private Button createToolButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        return createToolButton(text, text, action);
    }

    private double snapXToGuides(double x, double clipWidth, Pane lanePane, StackPane movingNode) {
        double snapped = x;
        double bestDistance = Double.MAX_VALUE;
        List<Double> guides = new ArrayList<>();

        int timelineSeconds = (int) Math.ceil(computeTimelineSpanSeconds());
        for (int sec = 0; sec <= timelineSeconds; sec += 1) {
            guides.add(sec * pixelsPerSecond);
        }
        for (int marker = 0; marker <= timelineSeconds; marker += 5) {
            guides.add(marker * pixelsPerSecond);
        }
        guides.add(playheadSlider.getValue() * pixelsPerSecond);

        if (lanePane != null) {
            for (Node node : lanePane.getChildren()) {
                if (node == movingNode) {
                    continue;
                }
                guides.add(node.getLayoutX());
                guides.add(node.getLayoutX() + node.prefWidth(-1));
            }
        }

        double thresholdPx = 10.0;
        for (Double guide : guides) {
            if (guide == null) {
                continue;
            }
            double leftDelta = Math.abs(x - guide);
            if (leftDelta < bestDistance && leftDelta <= thresholdPx) {
                bestDistance = leftDelta;
                snapped = guide;
            }
            double rightAlignedX = guide - clipWidth;
            double rightDelta = Math.abs(x - rightAlignedX);
            if (rightDelta < bestDistance && rightDelta <= thresholdPx) {
                bestDistance = rightDelta;
                snapped = rightAlignedX;
            }
        }
        return snapped;
    }

    private void handleClipSelection(Clip clip, boolean shift, boolean ctrl) {
        if (clip == null) {
            return;
        }
        sanitizeSelection();
        Clip current = resolveClipById(clip.getClipId());
        if (current == null) {
            return;
        }
        if (shift || ctrl) {
            if (containsSelectedClipId(current.getClipId()) && ctrl) {
                selectedClips.removeIf(existing -> existing != null && current.getClipId().equals(existing.getClipId()));
                if (selectedClip != null && current.getClipId().equals(selectedClip.getClipId())) {
                    selectedClip = selectedClips.stream().findFirst().orElse(null);
                }
            } else {
                selectedClips.add(current);
                selectedClip = current;
            }
        } else {
            setSingleSelection(current);
        }
    }

    private void setSingleSelection(Clip clip) {
        selectedClips.clear();
        selectedClip = clip;
        if (clip != null) {
            selectedClips.add(clip);
        }
        updateClipPropertyPanel();
    }

    private void clearSelection() {
        selectedClip = null;
        selectedClips.clear();
        updateClipPropertyPanel();
    }

    private boolean hasSelectedClip() {
        return getPrimarySelectedClip() != null;
    }

    private Clip getPrimarySelectedClip() {
        sanitizeSelection();
        if (selectedClip != null) {
            return selectedClip;
        }
        return selectedClips.stream().findFirst().orElse(null);
    }

    private void sanitizeSelection() {
        Sequence active = project.getActiveSequence();
        if (active == null) {
            clearSelection();
            return;
        }
        Map<String, Clip> clipsById = new HashMap<>();
        for (Track track : active.getTimelineModel().getTracks()) {
            for (Clip clip : track.getClips()) {
                clipsById.put(clip.getClipId(), clip);
            }
        }
        Set<Clip> remappedSelection = new LinkedHashSet<>();
        for (Clip selected : selectedClips) {
            if (selected == null) {
                continue;
            }
            Clip current = clipsById.get(selected.getClipId());
            if (current != null) {
                remappedSelection.add(current);
            }
        }
        selectedClips.clear();
        selectedClips.addAll(remappedSelection);
        if (selectedClip != null) {
            selectedClip = clipsById.get(selectedClip.getClipId());
        }
        if (selectedClip == null && !selectedClips.isEmpty()) {
            selectedClip = selectedClips.iterator().next();
        }
        updateClipPropertyPanel();
    }

    private boolean containsSelectedClipId(String clipId) {
        if (clipId == null || clipId.isBlank()) {
            return false;
        }
        for (Clip selected : selectedClips) {
            if (selected != null && clipId.equals(selected.getClipId())) {
                return true;
            }
        }
        return false;
    }

    private Clip resolveClipById(String clipId) {
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

    private void openTrimHandlesDialog() {
        Clip primary = getPrimarySelectedClip();
        if (primary == null) {
            showWarning("No clip selected", "Select a clip to trim.");
            return;
        }
        TextInputDialog inDialog = new TextInputDialog(String.format("%.2f", primary.getStartSeconds()));
        inDialog.setTitle("Trim - In Point");
        inDialog.setHeaderText("Set IN point (seconds)");
        inDialog.setContentText("In:");
        Optional<String> in = inDialog.showAndWait();
        if (in.isEmpty()) {
            return;
        }

        TextInputDialog outDialog = new TextInputDialog(String.format("%.2f", primary.getEndSeconds()));
        outDialog.setTitle("Trim - Out Point");
        outDialog.setHeaderText("Set OUT point (seconds)");
        outDialog.setContentText("Out:");
        Optional<String> out = outDialog.showAndWait();
        if (out.isEmpty()) {
            return;
        }

        try {
            double inSec = Double.parseDouble(in.get().trim());
            double outSec = Double.parseDouble(out.get().trim());
            if (outSec <= inSec) {
                showWarning("Invalid trim range", "Out point must be greater than in point.");
                return;
            }
            Sequence active = project.getActiveSequence();
            if (active == null) {
                return;
            }
            int trackIndex = active.getTimelineModel().findTrackIndexOfClip(primary);
            int clipIndex = active.getTimelineModel().indexOfInTrack(trackIndex, primary);
            if (trackIndex < 0 || clipIndex < 0) {
                showWarning("Trim failed", "Selected clip was not found in active timeline.");
                return;
            }
            Clip trimmed = primary.withRange(inSec, outSec);
            active.getTimelineModel().replaceClipInTrack(trackIndex, clipIndex, trimmed);
            setSingleSelection(trimmed);
            PreviewEventBus.publishTimelineChanged();
            PreviewEventBus.publishRefresh("Trim handles applied");
            rebuildTimelineLanes();
            emitStatus("Trim applied: " + String.format("%.2fs -> %.2fs", inSec, outSec));
        } catch (NumberFormatException nfe) {
            showWarning("Invalid number", "Please enter valid seconds for in/out points.");
        } catch (Exception ex) {
            logAndShowError("Trim failed", ex);
        }
    }

    private void performSplitAtSelectedPoints() {
        Clip primary = getPrimarySelectedClip();
        if (primary == null) {
            showWarning("No clip selected", "Select one or more clips before split.");
            return;
        }
        TextInputDialog splitDialog = new TextInputDialog(String.format("%.2f", (primary.getStartSeconds() + primary.getEndSeconds()) / 2.0));
        splitDialog.setTitle("Split Points");
        splitDialog.setHeaderText("Enter split point(s) in source seconds, comma-separated");
        splitDialog.setContentText("Points:");
        Optional<String> value = splitDialog.showAndWait();
        if (value.isEmpty() || value.get().isBlank()) {
            return;
        }

        List<Double> splitPoints = new ArrayList<>();
        for (String token : value.get().split(",")) {
            try {
                splitPoints.add(Double.parseDouble(token.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (splitPoints.isEmpty()) {
            showWarning("Invalid split points", "No valid split points were provided.");
            return;
        }

        splitPoints.sort(Comparator.reverseOrder());
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return;
        }
        int splits = 0;
        try {
            for (Clip target : new ArrayList<>(selectedClips)) {
                int trackIndex = active.getTimelineModel().findTrackIndexOfClip(target);
                int clipIndex = active.getTimelineModel().indexOfInTrack(trackIndex, target);
                if (trackIndex < 0 || clipIndex < 0) {
                    continue;
                }
                for (Double splitPoint : splitPoints) {
                    if (splitPoint > target.getStartSeconds() && splitPoint < target.getEndSeconds()) {
                        active.getTimelineModel().splitClipAtSourceTimeInTrack(trackIndex, clipIndex, splitPoint);
                        splits++;
                    }
                }
            }
            clearSelection();
            PreviewEventBus.publishTimelineChanged();
            PreviewEventBus.publishRefresh("Split points applied");
            rebuildTimelineLanes();
            emitStatus("Split completed: " + splits + " split(s).");
        } catch (Exception ex) {
            logAndShowError("Split failed", ex);
        }
    }

    private void openVfxPanel() {
        if (tabActivator != null) {
            tabActivator.accept(ProfessionalWorkspaceView.TAB_VFX);
        }
        emitStatus("VFX panel opened: keyframes, tracking, and chroma key controls ready.");
    }

    private Track.TrackType resolveDropTargetTrackType(double sceneY, Track.TrackType fallback) {
        for (Map.Entry<Track.TrackType, Pane> entry : lanePaneByType.entrySet()) {
            Pane pane = entry.getValue();
            if (pane == null || pane.getScene() == null) {
                continue;
            }
            var bounds = pane.localToScene(pane.getBoundsInLocal());
            if (bounds != null && sceneY >= bounds.getMinY() && sceneY <= bounds.getMaxY()) {
                return entry.getKey();
            }
        }
        return fallback;
    }

    private void moveClipToTrack(Clip clip, Track.TrackType fromType, Track.TrackType toType) {
        Sequence active = project.getActiveSequence();
        if (active == null || clip == null || fromType == null || toType == null || fromType == toType) {
            return;
        }
        TimelineModel timeline = active.getTimelineModel();
        int fromIndex = timeline.findFirstTrackIndexByType(fromType);
        int toIndex = timeline.ensureTrack(toType);
        if (fromIndex < 0 || toIndex < 0) {
            return;
        }
        int clipIndex = timeline.indexOfInTrack(fromIndex, clip);
        if (clipIndex < 0) {
            return;
        }
        timeline.removeClipFromTrack(fromIndex, clipIndex);
        timeline.addClipToTrack(toIndex, clip);
    }

    private void movePrimarySelectionToTrack(Track.TrackType toType) {
        Clip primary = getPrimarySelectedClip();
        if (primary == null) {
            showWarning("No clip selected", "Select a clip before changing its track.");
            return;
        }
        Track.TrackType fromType = resolveTrackTypeForClip(primary);
        if (fromType == null) {
            showWarning("Track not found", "The selected clip is no longer present in the active sequence.");
            return;
        }
        if (fromType == toType) {
            emitStatus("Selected clip is already on the " + toType + " track.");
            return;
        }
        moveClipToTrack(primary, fromType, toType);
        setSingleSelection(primary);
        PreviewEventBus.publishTimelineChanged();
        rebuildTimelineLanes();
        emitStatus("Moved selected clip to " + toType + " track.");
    }

    private Track.TrackType resolveTrackTypeForClip(Clip clip) {
        if (clip == null) {
            return null;
        }
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return null;
        }
        int trackIndex = active.getTimelineModel().findTrackIndexOfClip(clip);
        if (trackIndex < 0 || trackIndex >= active.getTimelineModel().getTracks().size()) {
            return null;
        }
        return active.getTimelineModel().getTracks().get(trackIndex).getType();
    }

    private void jumpPlayheadToSelection() {
        Clip primary = getPrimarySelectedClip();
        if (primary == null) {
            showWarning("No clip selected", "Select a clip before moving the playhead.");
            return;
        }
        playheadSlider.setValue(primary.getTimelineStartSeconds());
        PreviewEventBus.publishScrub(primary.getTimelineStartSeconds());
        emitStatus("Playhead moved to selected clip.");
    }

    private void updateClipPropertyPanel() {
        Clip primary = selectedClip != null ? selectedClip : selectedClips.stream().findFirst().orElse(null);
        if (primary == null) {
            selectedClipNameLabel.setText("No clip selected");
            selectedClipTrackLabel.setText("Track: --");
            selectedClipStartLabel.setText("Start: --");
            selectedClipDurationLabel.setText("Duration: --");
            selectedClipSourceRangeLabel.setText("Source Range: --");
            return;
        }
        Track.TrackType trackType = resolveTrackTypeForClip(primary);
        selectedClipNameLabel.setText(shortClipLabel(trackType == null ? Track.TrackType.VIDEO : trackType, primary, 0));
        selectedClipTrackLabel.setText("Track: " + (trackType == null ? "--" : trackType.name()));
        selectedClipStartLabel.setText(String.format("Start: %.2fs", primary.getTimelineStartSeconds()));
        selectedClipDurationLabel.setText(String.format("Duration: %.2fs", primary.getDurationSeconds()));
        selectedClipSourceRangeLabel.setText(String.format("Source Range: %.2fs - %.2fs", primary.getStartSeconds(), primary.getEndSeconds()));
    }

    private void showWarning(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Timeline Action");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
        emitStatus(message);
    }

    private void logAndShowError(String title, Exception ex) {
        String details = ex == null ? "Unknown error" : ex.getMessage();
        System.err.println(title + ": " + details);
        if (ex != null) {
            ex.printStackTrace();
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Editing Error");
        alert.setHeaderText(title);
        alert.setContentText(details == null || details.isBlank() ? "Please try again or check source media." : details);
        alert.showAndWait();
        emitStatus(title + ": " + details);
    }

    private String shortClipLabel(Track.TrackType type, Clip clip, int index) {
        String source = clip.getSourcePath();
        String shortSource = source == null ? "clip" : source.substring(Math.max(0, source.lastIndexOf('/') + 1)).replace("\\", "");
        if (shortSource.contains("\\")) {
            shortSource = shortSource.substring(shortSource.lastIndexOf('\\') + 1);
        }
        return type.name() + " " + (index + 1) + " • " + shortSource;
    }

    private Color colorForType(Track.TrackType type) {
        return switch (type) {
            case VIDEO -> Color.web("#334155");
            case OVERLAY -> Color.web("#7c3aed");
            case TEXT -> Color.web("#0ea5e9");
            case AUDIO -> Color.web("#16a34a");
        };
    }

    private String formatTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private double computeTimelineSpanSeconds() {
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return MIN_TIMELINE_SECONDS;
        }
        double maxEnd = 0;
        for (Track track : active.getTimelineModel().getTracks()) {
            double cursor = 0;
            for (Clip clip : track.getClips()) {
                double start = clip.getTimelineStartSeconds();
                double end = start + Math.max(0.05, clip.getDurationSeconds());
                maxEnd = Math.max(maxEnd, end);
                cursor = Math.max(cursor, end + 0.05);
            }
        }
        return Math.max(MIN_TIMELINE_SECONDS, Math.ceil(maxEnd + 10));
    }

    private double computeTimelineContentWidth() {
        return computeTimelineSpanSeconds() * pixelsPerSecond;
    }

    private double resolveMediaDurationSeconds(String storedPath, Track.TrackType type, double fallbackSeconds) {
        double fallback = Math.max(0.5, fallbackSeconds);
        if (type == Track.TrackType.TEXT) {
            return fallback;
        }
        try {
            String lower = storedPath == null ? "" : storedPath.toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
                return fallback;
            }
            Media media = new Media(ProjectPathResolver.resolvePath(storedPath).toUri().toString());
            Duration d = media.getDuration();
            if (d != null && !d.isUnknown() && d.toSeconds() > 0) {
                return d.toSeconds();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void emitStatus(String message) {
        if (statusSink != null) {
            statusSink.accept(message);
        }
    }
}
