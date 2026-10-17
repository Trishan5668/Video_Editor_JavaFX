import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class ProfessionalWorkspaceView extends BorderPane {
    public static final int TAB_EDITOR = 0;
    public static final int TAB_TIMELINE = 1;
    public static final int TAB_COLOR = 2;
    public static final int TAB_AUDIO = 3;
    public static final int TAB_VFX = 4;
    public static final int TAB_COLLABORATION = 5;
    public static final int TAB_EXPORT = 6;

    private final ProfessionalProject project;
    private final VideoEngine videoEngine;
    private final Label statusLabel = new Label("Ready");

    private final TimelineEditingPanel timelineEditingPanel;
    private final ColorGradingPanel colorGradingPanel;
    private final AudioPostPanel audioPostPanel;
    private final VfxMotionPanel vfxMotionPanel;
    private final CollaborationMediaPanel collaborationMediaPanel;
    private final ExportRenderPanel exportRenderPanel;
    private final VideoPlayerPanel videoPlayerPanel;
    private final Node previewPaneNode;
    private final TabPane workspaceTabs = new TabPane();
    private final EditorTab editorTab;
    private final TimelineTab timelineTab;

    // Header controls for draggable tab support
    private final Map<Tab, Label> tabHeaderMap = new HashMap<>();
    private final Pane tabDropIndicatorLayer = new Pane();
    private Rectangle dropIndicator = null;
    private double dragStartX = 0;
    private Tab draggedTab = null;

    private final ComboBox<RenderBackendType> previewBackendCombo = new ComboBox<>();

    public ProfessionalWorkspaceView(ProfessionalProject project, VideoEngine videoEngine) {
        this.project = project;
        this.videoEngine = videoEngine;

        this.timelineEditingPanel = new TimelineEditingPanel(project, videoEngine, this::setStatus, this::activateTab);
        this.editorTab = new EditorTab(timelineEditingPanel);
        this.timelineTab = new TimelineTab(timelineEditingPanel);
        this.colorGradingPanel = new ColorGradingPanel(project, videoEngine, this::setStatus);
        this.audioPostPanel = new AudioPostPanel(project, this::setStatus);
        this.vfxMotionPanel = new VfxMotionPanel(project, videoEngine, this::setStatus);
        this.collaborationMediaPanel = new CollaborationMediaPanel(project, this::setStatus);
        this.exportRenderPanel = new ExportRenderPanel(project, videoEngine, this::setStatus);
        VideoPlayerPanel previewCandidate;
        Node previewCandidateNode;
        try {
            previewCandidate = new VideoPlayerPanel(project, videoEngine, this::setStatus);
            previewCandidateNode = previewCandidate;
        } catch (Throwable previewInitError) {
            previewCandidate = null;
            Label fallback = new Label("Preview unavailable (safe mode). Continue editing in tabs.");
            fallback.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 14px;");
            StackPane placeholder = new StackPane(fallback);
            placeholder.setMinHeight(260);
            placeholder.setStyle("-fx-background-color: #000000; -fx-background-radius: 8;");
            previewCandidateNode = placeholder;
            setStatus("Video preview initialization failed: " + previewInitError.getClass().getSimpleName());
        }
        this.videoPlayerPanel = previewCandidate;
        this.previewPaneNode = previewCandidateNode;

        setStyle("-fx-background-color: #0b1220;");
        setPadding(new Insets(8));

        setTop(buildWorkspaceHeader());
        setCenter(buildMainContent());
        setBottom(buildStatusBar());
    }

    private HBox buildWorkspaceHeader() {
        Label title = new Label("Professional Editing Workspace");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");

        previewBackendCombo.getItems().setAll(RenderBackendType.values());
        previewBackendCombo.setValue(RenderBackendType.CPU);
        previewBackendCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || videoEngine == null) {
                return;
            }
            videoEngine.setRenderBackend(newV);
            PreviewEventBus.publishRefresh("Preview backend changed: " + newV);
            setStatus("Preview backend set to " + newV + " (timeline/color/VFX)");
        });

        HBox header = new HBox(10,
                title,
                new Label("Preview Backend"),
                previewBackendCombo
        );
        HBox.setHgrow(title, Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 4, 10, 4));
        return header;
    }

    private SplitPane buildMainContent() {
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        workspaceTabs.getTabs().addAll(
                new Tab("Editor", editorTab),
                new Tab("Timeline", timelineTab),
                new Tab("Color", colorGradingPanel),
                new Tab("Audio", audioPostPanel),
                new Tab("VFX", vfxMotionPanel),
                new Tab("Collaboration", collaborationMediaPanel),
                new Tab("Export", exportRenderPanel)
        );

        // Apply any saved tab order from project settings
        applySavedTabOrder();

        // Replace default text headers with lightweight Label graphics so drag handlers can be attached
        for (Tab t : new ArrayList<>(workspaceTabs.getTabs())) {
            final String title = t.getText();
            Label header = new Label(title);
            header.setStyle("-fx-text-fill: #e2e8f0; -fx-padding: 6 12; -fx-font-size: 13px; -fx-font-weight: 500;");
            header.setMouseTransparent(false);
            t.setText(null);
            t.setGraphic(header);
            tabHeaderMap.put(t, header);

            // Hover and active visual states
            header.addEventHandler(MouseEvent.MOUSE_ENTERED, ev -> header.setStyle("-fx-text-fill: #e2e8f0; -fx-padding: 6 12; -fx-font-size: 13px; -fx-font-weight: 500; -fx-background-color: rgba(255,255,255,0.03); -fx-background-radius:6;"));
            header.addEventHandler(MouseEvent.MOUSE_EXITED, ev -> header.setStyle("-fx-text-fill: #e2e8f0; -fx-padding: 6 12; -fx-font-size: 13px; -fx-font-weight: 500;"));
            header.addEventHandler(MouseEvent.MOUSE_PRESSED, ev -> header.setStyle("-fx-text-fill: #e2e8f0; -fx-padding: 6 12; -fx-font-size: 13px; -fx-font-weight: 500; -fx-background-color: rgba(99,102,241,0.12); -fx-background-radius:6;"));
            header.addEventHandler(MouseEvent.MOUSE_RELEASED, ev -> header.setStyle("-fx-text-fill: #e2e8f0; -fx-padding: 6 12; -fx-font-size: 13px; -fx-font-weight: 500;"));

            // Drag handlers
            header.setOnMousePressed(e -> {
                dragStartX = e.getSceneX();
                draggedTab = t;
            });

            header.setOnMouseDragged(e -> handleHeaderDrag(e));

            header.setOnMouseReleased(e -> {
                handleHeaderRelease(e);
            });
        }

        // Wrap the TabPane in a StackPane so the drop indicator can float above it without changing layout
        StackPane tabsContainer = new StackPane(workspaceTabs);
        tabDropIndicatorLayer.setMouseTransparent(true);
        tabDropIndicatorLayer.setPickOnBounds(false);
        tabsContainer.getChildren().add(tabDropIndicatorLayer);

        VBox mediaSidebar = new VBox(8,
                new Label("Media Browser"),
                new Label("Assets: " + project.getAssets().size()),
                new Label("Sequences: " + project.getSequences().size()),
                new Label("Active: " + (project.getActiveSequence() == null ? "None" : project.getActiveSequence().getName()))
        );
        mediaSidebar.setPadding(new Insets(10));
        mediaSidebar.setStyle("-fx-background-color: #111827; -fx-text-fill: #e5e7eb; -fx-background-radius: 8;");

        SplitPane editorSplit = new SplitPane();
        editorSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        editorSplit.getItems().addAll(previewPaneNode, tabsContainer);
        editorSplit.setMinHeight(520);
        // Keep enough room for preview controls to remain visible in smaller windows.
        editorSplit.setDividerPositions(0.55);

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(mediaSidebar, editorSplit);
        splitPane.setDividerPositions(0.20);
        return splitPane;
    }

    private VBox buildStatusBar() {
        statusLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
        VBox container = new VBox(statusLabel);
        container.setPadding(new Insets(8, 10, 4, 10));
        return container;
    }

    public void setStatus(String status) {
        statusLabel.setText(status == null ? "Ready" : status);
    }

    public TimelineEditingPanel getTimelineEditingPanel() {
        return timelineEditingPanel;
    }

    public ExportRenderPanel getExportRenderPanel() {
        return exportRenderPanel;
    }

    public ColorGradingPanel getColorGradingPanel() {
        return colorGradingPanel;
    }

    public AudioPostPanel getAudioPostPanel() {
        return audioPostPanel;
    }

    public VfxMotionPanel getVfxMotionPanel() {
        return vfxMotionPanel;
    }

    public CollaborationMediaPanel getCollaborationMediaPanel() {
        return collaborationMediaPanel;
    }

    public void activateTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < workspaceTabs.getTabs().size()) {
            workspaceTabs.getSelectionModel().select(tabIndex);
        }
    }

    // --- Drag/reorder helper methods ---
    private void applySavedTabOrder() {
        try {
            Map<String, String> tags = project.getMetadataTagService().getTags("project-settings");
            if (tags == null) return;
            String order = tags.get("workspace.tabOrder");
            if (order == null || order.isBlank()) return;
            String[] names = order.split(",");
            List<Tab> newOrder = new ArrayList<>();
            for (String name : names) {
                for (Tab t : new ArrayList<>(workspaceTabs.getTabs())) {
                    if (name.equals(t.getText())) {
                        newOrder.add(t);
                        break;
                    }
                }
            }
            // Append any tabs not listed in saved order
            for (Tab t : new ArrayList<>(workspaceTabs.getTabs())) {
                if (!newOrder.contains(t)) newOrder.add(t);
            }
            if (newOrder.size() == workspaceTabs.getTabs().size()) {
                workspaceTabs.getTabs().setAll(newOrder);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleHeaderDrag(MouseEvent e) {
        if (draggedTab == null) return;
        // Determine target index based on current mouse X relative to header labels
        List<Tab> tabs = workspaceTabs.getTabs();
        List<Label> headers = new ArrayList<>();
        for (Tab t : tabs) {
            Label h = tabHeaderMap.get(t);
            if (h != null) headers.add(h);
        }
        double sceneX = e.getSceneX();
        int target = headers.size();
        for (int i = 0; i < headers.size(); i++) {
            Label h = headers.get(i);
            Bounds b = h.localToScene(h.getBoundsInLocal());
            double center = b.getMinX() + b.getWidth() / 2.0;
            if (sceneX < center) {
                target = i;
                break;
            }
        }
        // Show drop indicator at target position
        showDropIndicatorAtTarget(target, headers);
    }

    private void showDropIndicatorAtTarget(int targetIndex, List<Label> headers) {
        Platform.runLater(() -> {
            tabDropIndicatorLayer.getChildren().clear();
            dropIndicator = new Rectangle(2, 24, Color.web("#60a5fa"));
            dropIndicator.setArcWidth(2);
            dropIndicator.setArcHeight(2);
            if (targetIndex <= 0 && headers.size() > 0) {
                Bounds b = headers.get(0).localToScene(headers.get(0).getBoundsInLocal());
                Point2D local = tabDropIndicatorLayer.sceneToLocal(b.getMinX(), b.getMinY());
                dropIndicator.setLayoutX(local.getX());
            } else if (targetIndex >= headers.size() && headers.size() > 0) {
                Label last = headers.get(headers.size() - 1);
                Bounds b = last.localToScene(last.getBoundsInLocal());
                Point2D local = tabDropIndicatorLayer.sceneToLocal(b.getMinX() + b.getWidth(), b.getMinY());
                dropIndicator.setLayoutX(local.getX());
            } else if (headers.size() > 0) {
                Label h = headers.get(targetIndex);
                Bounds b = h.localToScene(h.getBoundsInLocal());
                Point2D local = tabDropIndicatorLayer.sceneToLocal(b.getMinX(), b.getMinY());
                dropIndicator.setLayoutX(local.getX());
            }
            dropIndicator.setLayoutY(6);
            tabDropIndicatorLayer.getChildren().add(dropIndicator);
        });
    }

    private void handleHeaderRelease(MouseEvent e) {
        if (draggedTab == null) return;
        List<Tab> tabs = workspaceTabs.getTabs();
        List<Label> headers = new ArrayList<>();
        for (Tab t : tabs) {
            Label h = tabHeaderMap.get(t);
            if (h != null) headers.add(h);
        }
        double sceneX = e.getSceneX();
        int target = headers.size();
        for (int i = 0; i < headers.size(); i++) {
            Label h = headers.get(i);
            Bounds b = h.localToScene(h.getBoundsInLocal());
            double center = b.getMinX() + b.getWidth() / 2.0;
            if (sceneX < center) {
                target = i;
                break;
            }
        }
        int from = tabs.indexOf(draggedTab);
        int to = target;
        if (to > from) to--;
        if (from >= 0 && to >= 0 && from != to && from < tabs.size()) {
            Tab selected = workspaceTabs.getSelectionModel().getSelectedItem();
            Tab moving = draggedTab;
            tabs.remove(from);
            tabs.add(to, moving);
            // Restore selection
            if (selected != null) {
                workspaceTabs.getSelectionModel().select(selected);
            }
            // Save order
            saveTabOrder();
        }
        // Clear state
        draggedTab = null;
        dragStartX = 0;
        Platform.runLater(() -> tabDropIndicatorLayer.getChildren().clear());
    }

    private void saveTabOrder() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Tab t : workspaceTabs.getTabs()) {
                if (sb.length() > 0) sb.append(',');
                // Use the text from the graphic label if text was cleared
                String name = t.getText();
                if (name == null || name.isBlank()) {
                    Label h = tabHeaderMap.get(t);
                    name = h == null ? "" : h.getText();
                }
                sb.append(name);
            }
            project.getMetadataTagService().setTag("project-settings", "workspace.tabOrder", sb.toString());
            project.saveProjectMetadata();
        } catch (Exception ignored) {
        }
    }

    public VideoPlayerPanel getVideoPlayerPanel() {
        return videoPlayerPanel;
    }

    public boolean hasVideoPreviewPanel() {
        return videoPlayerPanel != null;
    }
}
