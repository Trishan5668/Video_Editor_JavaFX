import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ColorGradingPanel extends BorderPane {
    private final ProfessionalProject project;
    private final VideoEngine videoEngine;
    private final Consumer<String> statusSink;

    private final Pane nodeGraphCanvas = new Pane();
    private final Map<String, Circle> nodeVisualById = new HashMap<>();
    private final ComboBox<String> scopeSelector = new ComboBox<>();
    private final ComboBox<String> gradedClipSelector = new ComboBox<>();
    private final ListView<String> powerWindowList = new ListView<>();
    private final Rectangle maskPreview = new Rectangle(280, 170, Color.web("#0f172a"));
    private final ColorPanelController colorController;

    public ColorGradingPanel(ProfessionalProject project, VideoEngine videoEngine, Consumer<String> statusSink) {
        this.project = project;
        this.videoEngine = videoEngine;
        this.statusSink = statusSink;
        this.colorController = new ColorPanelController();

        setPadding(new Insets(8));
        setStyle("-fx-background-color: #0f172a;");

        setCenter(buildCenter());
        setRight(buildScopesPanel());

        refreshNodeGraphView();
        refreshPowerWindows();
        refreshClipSelector();
    }

    private SplitPane buildCenter() {
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(
                buildNodeGraphEditor(),
                buildControlsTabs()
        );
        splitPane.setDividerPositions(0.56);
        return splitPane;
    }

    private VBox buildNodeGraphEditor() {
        Label title = new Label("Node Graph Editor");
        title.setStyle("-fx-text-fill: #e2e8f0; -fx-font-weight: bold;");

        nodeGraphCanvas.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        nodeGraphCanvas.setMinHeight(460);
        nodeGraphCanvas.setPrefHeight(460);

        Button addColorNode = new Button("Add Color Node");
        addColorNode.setOnAction(e -> {
            EffectNode node = project.getProfessionalEditingSuite().getColorGradingService().getNodeGraph()
                    .addNode(NodeType.COLOR_CORRECTION, "Color " + (nodeVisualById.size() + 1));
            ensureDefaultInputOutputNodes();
            autoConnectIfPossible(node);
            refreshNodeGraphView();
            emit("Added color node: " + node.getLabel());
        });

        Button addSecondaryNode = new Button("Add Secondary");
        addSecondaryNode.setOnAction(e -> {
            EffectNode node = project.getProfessionalEditingSuite().getColorGradingService().getNodeGraph()
                    .addNode(NodeType.SECONDARY_COLOR, "Secondary " + (nodeVisualById.size() + 1));
            autoConnectIfPossible(node);
            refreshNodeGraphView();
            emit("Added secondary correction node");
        });

        Button connectFlow = new Button("Auto Connect Chain");
        connectFlow.setOnAction(e -> {
            NodeGraph graph = project.getProfessionalEditingSuite().getColorGradingService().getNodeGraph();
            for (int i = 0; i < graph.getNodes().size() - 1; i++) {
                graph.connect(graph.getNodes().get(i).getNodeId(), graph.getNodes().get(i + 1).getNodeId());
            }
            refreshNodeGraphView();
            PreviewEventBus.publishRefresh("Color node graph chain updated");
            emit("Node chain auto-connected");
        });

        HBox actions = new HBox(8, addColorNode, addSecondaryNode, connectFlow);

        Button applyToClip = new Button("Apply Grade To Selected Clip");
        applyToClip.setOnAction(e -> colorController.applyCurrentGradeToSelectedClip());
        HBox clipRow = new HBox(8, new Label("Selected Clip"), gradedClipSelector, applyToClip);
        HBox.setHgrow(gradedClipSelector, Priority.ALWAYS);

        return new VBox(8, title, nodeGraphCanvas, actions, clipRow);
    }

    private TabPane buildControlsTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Wheels", buildColorWheelsPanel()),
                new Tab("Masks", buildMaskTrackingPanel()),
                new Tab("Curves", buildCurvesPanel())
        );
        return tabs;
    }

    private VBox buildColorWheelsPanel() {
        ComboBox<String> mode = new ComboBox<>();
        mode.getItems().addAll("Primary", "Secondary");
        mode.getSelectionModel().select(0);
        mode.valueProperty().addListener((obs, oldV, newV) -> emit("Color wheel mode: " + newV));

        GridPane wheels = new GridPane();
        wheels.setHgap(8);
        wheels.setVgap(10);

        wheels.add(createWheelRow("Lift", val -> setWheelValue("lift", val, mode.getValue())), 0, 0);
        wheels.add(createWheelRow("Gamma", val -> setWheelValue("gamma", val, mode.getValue())), 0, 1);
        wheels.add(createWheelRow("Gain", val -> setWheelValue("gain", val, mode.getValue())), 0, 2);
        wheels.add(createWheelRow("Offset", val -> setWheelValue("offset", val, mode.getValue())), 0, 3);

        return new VBox(10, new Label("Primary / Secondary Color Wheels"), mode, wheels);
    }

    private HBox createWheelRow(String label, java.util.function.DoubleConsumer consumer) {
        Slider slider = new Slider(-1, 1, 0);
        slider.valueProperty().addListener((obs, oldV, newV) -> consumer.accept(newV.doubleValue()));

        Circle puck = new Circle(14, Color.web("#1d4ed8"));
        puck.setStroke(Color.web("#93c5fd"));
        puck.setCursor(Cursor.HAND);

        final double[] dragAnchor = new double[2];
        puck.setOnMousePressed(e -> {
            dragAnchor[0] = e.getSceneX();
            dragAnchor[1] = e.getSceneY();
        });
        puck.setOnMouseDragged(e -> {
            double dx = (e.getSceneX() - dragAnchor[0]) / 120.0;
            slider.setValue(Math.max(-1, Math.min(1, slider.getValue() + dx)));
            dragAnchor[0] = e.getSceneX();
            dragAnchor[1] = e.getSceneY();
        });

        HBox row = new HBox(8, new Label(label), slider, puck);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private VBox buildMaskTrackingPanel() {
        maskPreview.setArcWidth(10);
        maskPreview.setArcHeight(10);

        Pane maskCanvas = new Pane(maskPreview);
        maskCanvas.setMinHeight(180);
        maskCanvas.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");

        Button addWindow = new Button("Add Power Window");
        addWindow.setOnAction(e -> {
            PowerWindow window = new PowerWindow("pw-" + System.currentTimeMillis(), 0.5, 0.5, 0.25, 0.2);
            project.getProfessionalEditingSuite().getColorGradingService().addPowerWindow(window);
            refreshPowerWindows();
            emit("Power window added: " + window.getWindowId());
        });

        Button trackForward = new Button("Track Forward");
        trackForward.setOnAction(e -> trackSelectedPowerWindow("forward"));

        Button trackBackward = new Button("Track Backward");
        trackBackward.setOnAction(e -> trackSelectedPowerWindow("backward"));

        Button invertMask = new Button("Invert Mask");
        invertMask.setOnAction(e -> emit("Mask inversion toggled (integration point active)"));

        return new VBox(8,
                new Label("Masking / Power Windows"),
                maskCanvas,
                powerWindowList,
                new HBox(8, addWindow, trackForward, trackBackward, invertMask),
                new Label("Feather/softness and spline points can be layered in this pane")
        );
    }

    private VBox buildCurvesPanel() {
        VBox curves = new VBox(8,
                buildCurveRow("Hue vs Hue", () -> project.getProfessionalEditingSuite().getColorGradingService().getCurveEditorService()
                        .addHuePoint(Math.random(), Math.random())),
                buildCurveRow("Hue vs Sat", () -> project.getProfessionalEditingSuite().getColorGradingService().getCurveEditorService()
                        .addSaturationPoint(Math.random(), Math.random())),
                buildCurveRow("Luma vs Sat", () -> project.getProfessionalEditingSuite().getColorGradingService().getCurveEditorService()
                        .addLuminancePoint(Math.random(), Math.random()))
        );

        Slider chromaWarp = new Slider(-1, 1, 0);
        chromaWarp.valueProperty().addListener((obs, oldV, newV) ->
                emit("Chroma warper adjusted: " + String.format("%.2f", newV.doubleValue())));

        return new VBox(10,
                new Label("Curves"), curves,
                new Label("Chroma Warper"), chromaWarp,
                new Label("Vector warp grid drag points can be mapped to backend LUT ops")
        );
    }

    private HBox buildCurveRow(String name, Runnable addPointAction) {
        Button addPoint = new Button("Add Point");
        addPoint.setOnAction(e -> {
            addPointAction.run();
            PreviewEventBus.publishRefresh("Color curve updated: " + name);
            emit(name + " point added");
        });

        Region curvePreview = new Region();
        curvePreview.setPrefSize(180, 34);
        curvePreview.setStyle("-fx-background-color: #1f2937; -fx-border-color: #334155;");
        HBox.setHgrow(curvePreview, Priority.ALWAYS);
        return new HBox(8, new Label(name), curvePreview, addPoint);
    }

    private VBox buildScopesPanel() {
        scopeSelector.getItems().addAll("Waveform", "Vectorscope", "Histogram");
        scopeSelector.getSelectionModel().select("Waveform");

        Pane scopeCanvas = new Pane();
        scopeCanvas.setMinSize(260, 220);
        scopeCanvas.setStyle("-fx-background-color: #0b1220; -fx-border-color: #1f2937; -fx-border-radius: 8;");

        Button enableScope = new Button("Enable Scope");
        enableScope.setOnAction(e -> {
            ColorScopeType type = mapScope(scopeSelector.getValue());
            project.getProfessionalEditingSuite().getColorGradingService().getScopeMonitorService().enableScope(type);
            drawScopeHint(scopeCanvas, type);
            PreviewEventBus.publishRefresh("Scope enabled: " + scopeSelector.getValue());
            emit(scopeSelector.getValue() + " enabled");
        });

        Button disableScope = new Button("Disable Scope");
        disableScope.setOnAction(e -> {
            ColorScopeType type = mapScope(scopeSelector.getValue());
            project.getProfessionalEditingSuite().getColorGradingService().getScopeMonitorService().disableScope(type);
            drawScopeHint(scopeCanvas, null);
            PreviewEventBus.publishRefresh("Scope disabled: " + scopeSelector.getValue());
            emit(scopeSelector.getValue() + " disabled");
        });

        Button gpuBoost = new Button("Use GPU Preview");
        gpuBoost.setOnAction(e -> {
            if (videoEngine != null) {
                videoEngine.setRenderBackend(RenderBackendType.OPENCL);
            }
            emit("GPU scope preview target enabled (OPENCL)");
        });

        VBox panel = new VBox(8,
                new Label("Scopes"),
                scopeSelector,
                scopeCanvas,
                new HBox(8, enableScope, disableScope),
                gpuBoost
        );
        panel.setPadding(new Insets(8));
        panel.setPrefWidth(300);
        panel.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        return panel;
    }

    private void ensureDefaultInputOutputNodes() {
        NodeGraph graph = project.getProfessionalEditingSuite().getColorGradingService().getNodeGraph();
        if (graph.getNodes().stream().noneMatch(n -> n.getType() == NodeType.OUTPUT)) {
            graph.addNode(NodeType.OUTPUT, "Output");
        }
        if (graph.getNodes().stream().noneMatch(n -> "Input".equalsIgnoreCase(n.getLabel()))) {
            graph.addNode(NodeType.EFFECT, "Input");
        }
    }

    private void autoConnectIfPossible(EffectNode newlyAdded) {
        if (newlyAdded == null) {
            return;
        }
        NodeGraph graph = project.getProfessionalEditingSuite().getColorGradingService().getNodeGraph();
        EffectNode output = graph.getNodes().stream().filter(n -> n.getType() == NodeType.OUTPUT).findFirst().orElse(null);
        if (output != null && !output.getNodeId().equals(newlyAdded.getNodeId())) {
            graph.connect(newlyAdded.getNodeId(), output.getNodeId());
        }
    }

    private void refreshNodeGraphView() {
        nodeGraphCanvas.getChildren().clear();
        nodeVisualById.clear();

        NodeGraph graph = project.getProfessionalEditingSuite().getColorGradingService().getNodeGraph();
        if (graph.getNodes().isEmpty()) {
            ensureDefaultInputOutputNodes();
        }

        double x = 70;
        double y = 80;
        for (EffectNode node : graph.getNodes()) {
            Circle visual = new Circle(22, nodeColor(node.getType()));
            visual.setLayoutX(x);
            visual.setLayoutY(y);
            visual.setCursor(Cursor.HAND);

            Label label = new Label(node.getLabel());
            label.setTextFill(Color.web("#e2e8f0"));
            label.setLayoutX(x - 30);
            label.setLayoutY(y + 28);

            final double[] anchor = new double[2];
            visual.setOnMousePressed(e -> {
                anchor[0] = e.getSceneX() - visual.getLayoutX();
                anchor[1] = e.getSceneY() - visual.getLayoutY();
            });
            visual.setOnMouseDragged(e -> {
                visual.setLayoutX(e.getSceneX() - anchor[0]);
                visual.setLayoutY(e.getSceneY() - anchor[1]);
                label.setLayoutX(visual.getLayoutX() - 30);
                label.setLayoutY(visual.getLayoutY() + 28);
                redrawConnections();
            });

            nodeVisualById.put(node.getNodeId(), visual);
            nodeGraphCanvas.getChildren().addAll(visual, label);

            x += 130;
            if (x > 600) {
                x = 70;
                y += 110;
            }
        }

        redrawConnections();
    }

    private void redrawConnections() {
        nodeGraphCanvas.getChildren().removeIf(node -> node instanceof Line);
        NodeGraph graph = project.getProfessionalEditingSuite().getColorGradingService().getNodeGraph();
        for (NodeConnection connection : graph.getConnections()) {
            Circle from = nodeVisualById.get(connection.getFromNodeId());
            Circle to = nodeVisualById.get(connection.getToNodeId());
            if (from == null || to == null) {
                continue;
            }
            Line line = new Line(from.getLayoutX(), from.getLayoutY(), to.getLayoutX(), to.getLayoutY());
            line.setStroke(Color.web("#94a3b8"));
            line.setStrokeWidth(2.2);
            nodeGraphCanvas.getChildren().add(0, line);
        }
    }

    private void refreshPowerWindows() {
        powerWindowList.getItems().clear();
        for (PowerWindow w : project.getProfessionalEditingSuite().getColorGradingService().getPowerWindows()) {
            powerWindowList.getItems().add(w.getWindowId() + "  [" + String.format("%.2f, %.2f", w.getCenterX(), w.getCenterY()) + "]");
        }
    }

    private void trackSelectedPowerWindow(String direction) {
        int index = powerWindowList.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            emit("Select a power window first.");
            return;
        }
        PowerWindow window = project.getProfessionalEditingSuite().getColorGradingService().getPowerWindows().get(index);
        String sourcePath = colorController.resolveSelectedClipPath();
        if (sourcePath == null) {
            emit("Select a timeline clip before tracking a power window.");
            return;
        }
        project.getProfessionalEditingSuite().getColorGradingService().getMotionTrackerService().trackWindow(window, sourcePath);
        PreviewEventBus.publishRefresh("Power window tracked on selected clip");
        emit("Tracking " + direction + " for " + window.getWindowId() + " -> "
                + project.getProfessionalEditingSuite().getColorGradingService().getMotionTrackerService().getTrackingDataPath(window.getWindowId()));
    }

    private void refreshClipSelector() {
        gradedClipSelector.getItems().clear();
        List<Clip> clips = colorController.collectTimelineClips();
        for (Clip clip : clips) {
            gradedClipSelector.getItems().add(clip.getSourcePath());
        }
        if (!gradedClipSelector.getItems().isEmpty()) {
            gradedClipSelector.getSelectionModel().select(0);
        }
    }

    private class ColorPanelController {
        /** Apply current node/wheel/curve grading state to selected clip metadata for backend color pipeline handoff. */
        void applyCurrentGradeToSelectedClip() {
            String source = resolveSelectedClipPath();
            if (source == null) {
                emit("Select a clip to apply grading.");
                return;
            }

            ProjectAsset asset = findOrCreateAssetBySource(source);
            if (asset == null) {
                emit("Unable to resolve asset for selected clip.");
                return;
            }

            ColorGradingService grading = project.getProfessionalEditingSuite().getColorGradingService();
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.nodeCount", String.valueOf(grading.getNodeGraph().getNodes().size()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.connectionCount", String.valueOf(grading.getNodeGraph().getConnections().size()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.primaryLift", String.format("%.3f", grading.getPrimaryWheels().getLift()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.primaryGamma", String.format("%.3f", grading.getPrimaryWheels().getGamma()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.primaryGain", String.format("%.3f", grading.getPrimaryWheels().getGain()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.curveHuePoints", String.valueOf(grading.getCurveEditorService().getHueCurve().size()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.curveSatPoints", String.valueOf(grading.getCurveEditorService().getSaturationCurve().size()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.curveLumaPoints", String.valueOf(grading.getCurveEditorService().getLuminanceCurve().size()));
            project.getMetadataTagService().setTag(asset.getAssetId(), "color.powerWindows", String.valueOf(grading.getPowerWindows().size()));
            emit("Color grading state bound to asset " + asset.getAssetId() + " for backend processing.");
        }

        String resolveSelectedClipPath() {
            String selected = gradedClipSelector.getValue();
            if (selected != null && !selected.isBlank()) {
                return selected;
            }
            List<Clip> clips = collectTimelineClips();
            return clips.isEmpty() ? null : clips.get(0).getSourcePath();
        }

        List<Clip> collectTimelineClips() {
            Sequence active = project.getActiveSequence();
            if (active == null) {
                return List.of();
            }
            List<Clip> clips = new ArrayList<>();
            for (Track track : active.getTimelineModel().getTracks()) {
                clips.addAll(track.getClips());
            }
            return clips;
        }

        private ProjectAsset findOrCreateAssetBySource(String sourcePath) {
            for (ProjectAsset asset : project.getAssets().values()) {
                if (sourcePath.equalsIgnoreCase(asset.getSourcePath())) {
                    return asset;
                }
            }
            String assetId = "asset-" + System.currentTimeMillis();
            MediaType mediaType = sourcePath.toLowerCase().endsWith(".wav") ? MediaType.AUDIO : MediaType.VIDEO;
            ProjectAsset asset = new ProjectAsset(assetId, sourcePath, mediaType);
            project.registerAsset(asset);
            return asset;
        }
    }

    private void setWheelValue(String channel, double value, String mode) {
        ColorWheelSettings target = "Secondary".equalsIgnoreCase(mode)
                ? project.getProfessionalEditingSuite().getColorGradingService().getSecondaryWheels()
                : project.getProfessionalEditingSuite().getColorGradingService().getPrimaryWheels();

        switch (channel.toLowerCase()) {
            case "lift" -> target.setLift(value);
            case "gamma" -> target.setGamma(value);
            case "gain" -> target.setGain(value);
            case "offset" -> target.setOffset(value);
            default -> {
            }
        }
        PreviewEventBus.publishRefresh("Color wheel adjusted: " + mode + " " + channel);
        emit(mode + " " + channel + " = " + String.format("%.2f", value));
    }

    private ColorScopeType mapScope(String selected) {
        if ("Vectorscope".equalsIgnoreCase(selected)) {
            return ColorScopeType.VECTORSCOPE;
        }
        if ("Histogram".equalsIgnoreCase(selected)) {
            return ColorScopeType.HISTOGRAM;
        }
        return ColorScopeType.WAVEFORM;
    }

    private void drawScopeHint(Pane scopeCanvas, ColorScopeType enabledType) {
        scopeCanvas.getChildren().clear();
        if (enabledType == null) {
            Label idle = new Label("No scope active");
            idle.setTextFill(Color.web("#64748b"));
            idle.setLayoutX(90);
            idle.setLayoutY(96);
            scopeCanvas.getChildren().add(idle);
            return;
        }

        switch (enabledType) {
            case WAVEFORM -> {
                for (int i = 0; i < 220; i += 6) {
                    Line l = new Line(i, 180 - (Math.sin(i * 0.07) * 40 + 80), i + 4, 180 - (Math.cos(i * 0.08) * 44 + 70));
                    l.setStroke(Color.web("#22d3ee"));
                    scopeCanvas.getChildren().add(l);
                }
            }
            case VECTORSCOPE -> {
                Circle c = new Circle(120, 110, 76);
                c.setFill(Color.TRANSPARENT);
                c.setStroke(Color.web("#818cf8"));
                scopeCanvas.getChildren().add(c);
                for (int d = 0; d < 360; d += 24) {
                    double x = 120 + Math.cos(Math.toRadians(d)) * 58;
                    double y = 110 + Math.sin(Math.toRadians(d)) * 58;
                    Circle p = new Circle(x, y, 3, Color.web("#a5b4fc"));
                    scopeCanvas.getChildren().add(p);
                }
            }
            case HISTOGRAM -> {
                for (int i = 0; i < 200; i += 7) {
                    double h = 20 + Math.abs(Math.sin(i * 0.13) * 120);
                    Rectangle bar = new Rectangle(i + 20, 180 - h, 5, h);
                    bar.setFill(Color.web("#34d399"));
                    scopeCanvas.getChildren().add(bar);
                }
            }
        }
    }

    private Color nodeColor(NodeType type) {
        return switch (type) {
            case COLOR_CORRECTION -> Color.web("#2563eb");
            case SECONDARY_COLOR -> Color.web("#0ea5e9");
            case CHROMA_KEY -> Color.web("#22c55e");
            case BLEND -> Color.web("#a855f7");
            case EFFECT -> Color.web("#f59e0b");
            case OUTPUT -> Color.web("#ef4444");
        };
    }

    private void emit(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }
}