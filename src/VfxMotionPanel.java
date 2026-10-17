import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class VfxMotionPanel extends BorderPane {
    private final ProfessionalProject project;
    private final VideoEngine videoEngine;
    private final Consumer<String> statusSink;

    private final ListView<String> keyframeList = new ListView<>();
    private final ListView<String> layerList = new ListView<>();
    private final ListView<String> vfxNodeList = new ListView<>();
    private final ComboBox<String> targetClipSelector = new ComboBox<>();
    private final TextField backgroundReplaceField = new TextField();
    private final VfxPanelController vfxController;
    private final OpenCVAiService openCVAiService = new OpenCVAiService();

    public VfxMotionPanel(ProfessionalProject project, VideoEngine videoEngine, Consumer<String> statusSink) {
        this.project = project;
        this.videoEngine = videoEngine;
        this.statusSink = statusSink;
        this.vfxController = new VfxPanelController();
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #0f172a;");

        SplitPane split = new SplitPane(buildKeyframeAndTracking(), buildVfxNodeLayerEditor());
        split.setDividerPositions(0.52);
        setCenter(split);

        refreshKeyframeList();
        refreshLayersAndNodes();
        refreshTargetClipSelector();
    }

    private VBox buildKeyframeAndTracking() {
        ComboBox<EasingType> easing = new ComboBox<>();
        easing.getItems().addAll(EasingType.values());
        easing.getSelectionModel().select(EasingType.EASE_IN_OUT);

        ComboBox<String> parameterCombo = new ComboBox<>(FXCollections.observableArrayList(
                "positionX", "positionY", "scale", "rotation", "opacity"
        ));
        parameterCombo.getSelectionModel().select("positionX");

        Button addKey = new Button("Add Keyframe");
        addKey.setOnAction(e -> {
            EasingType easingType = easing.getValue();
            String parameter = parameterCombo.getValue();
            if (parameter == null) {
                return;
            }
            VfxCompositionService vfx = project.getProfessionalEditingSuite().getVfxCompositionService();
            vfx.getAnimationService().getOrCreateTrack(parameter)
                    .addKeyframe(new Keyframe(System.currentTimeMillis() % 300, Math.random(), easingType));
            refreshKeyframeList();
            emit("Keyframe added on " + parameter + " with easing: " + easingType);
        });

        Pane keyframeGraph = buildKeyframeGraphPreview();

        Button tracking = new Button("Track Subject");
        tracking.setOnAction(e -> vfxController.applyMotionTrackingToSelection());
        Button trackMotion = new Button("Track Motion");
        trackMotion.setOnAction(e -> vfxController.runOpenCvTracking(false));
        Button trackFaces = new Button("Track Faces");
        trackFaces.setOnAction(e -> vfxController.runOpenCvTracking(true));
        Button chooseBg = new Button("Choose BG");
        chooseBg.setOnAction(e -> chooseBackgroundReplacement());
        backgroundReplaceField.setPromptText("Background image/video (optional)");

        CheckBox overlayPreview = new CheckBox("Tracking Overlay Preview");
        overlayPreview.setSelected(true);
        overlayPreview.selectedProperty().addListener((obs, oldV, newV) ->
                emit("Tracking overlay preview " + (newV ? "enabled" : "disabled")));

        Slider threshold = new Slider(0, 1, 0.45);
        Slider spill = new Slider(0, 1, 0.3);
        Slider edgeSoftness = new Slider(0, 1, 0.15);

        threshold.valueProperty().addListener((o, a, b) -> {
            project.getProfessionalEditingSuite().getVfxCompositionService().getChromaKeySettings().setHueRange(b.doubleValue());
            PreviewEventBus.publishRefresh("VFX chroma threshold updated");
            emit("Chroma threshold: " + String.format("%.2f", b.doubleValue()));
        });
        spill.valueProperty().addListener((o, a, b) -> {
            project.getProfessionalEditingSuite().getVfxCompositionService().getChromaKeySettings().setSpillSuppression(b.doubleValue());
            PreviewEventBus.publishRefresh("VFX spill suppression updated");
            emit("Spill suppression: " + String.format("%.2f", b.doubleValue()));
        });
        edgeSoftness.valueProperty().addListener((o, a, b) -> {
            project.getProfessionalEditingSuite().getVfxCompositionService().getChromaKeySettings().setEdgeSoftness(b.doubleValue());
            PreviewEventBus.publishRefresh("VFX edge softness updated");
            emit("Edge softness: " + String.format("%.2f", b.doubleValue()));
        });

        Button enableGpu = new Button("Enable GPU VFX Preview");
        enableGpu.setOnAction(e -> {
            if (videoEngine != null) {
                videoEngine.setRenderBackend(RenderBackendType.OPENCL);
            }
            emit("GPU VFX preview enabled");
        });

        VBox box = new VBox(10,
                new Label("Keyframe Editor"),
                new HBox(8, new Label("Target Clip"), targetClipSelector),
                new HBox(8, new Label("Parameter"), parameterCombo, new Label("Easing"), easing, addKey),
                keyframeGraph,
                keyframeList,
                new Label("Motion Tracking"),
                new HBox(8, tracking, trackMotion, trackFaces, overlayPreview),
                new HBox(8, backgroundReplaceField, chooseBg),
                new Label("Chroma Key Controls"),
                new HBox(8, new Label("Threshold"), threshold),
                new HBox(8, new Label("Spill"), spill),
                new HBox(8, new Label("Edge"), edgeSoftness),
                enableGpu
        );
        VBox.setVgrow(keyframeList, Priority.ALWAYS);
        return box;
    }

    private Pane buildKeyframeGraphPreview() {
        Pane graph = new Pane();
        graph.setMinHeight(160);
        graph.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");

        Line axisX = new Line(14, 130, 330, 130);
        axisX.setStroke(Color.web("#475569"));
        Line axisY = new Line(14, 20, 14, 130);
        axisY.setStroke(Color.web("#475569"));
        graph.getChildren().addAll(axisX, axisY);

        Circle p1 = createDraggableKeyPoint(graph, 40, 120);
        Circle p2 = createDraggableKeyPoint(graph, 140, 86);
        Circle p3 = createDraggableKeyPoint(graph, 250, 40);
        Circle p4 = createDraggableKeyPoint(graph, 315, 66);
        graph.getChildren().addAll(p1, p2, p3, p4);
        return graph;
    }

    private Circle createDraggableKeyPoint(Pane graph, double x, double y) {
        Circle point = new Circle(x, y, 5, Color.web("#38bdf8"));
        point.setCursor(Cursor.HAND);
        point.setOnMouseDragged(e -> {
            point.setCenterX(Math.max(15, Math.min(325, e.getX())));
            point.setCenterY(Math.max(20, Math.min(130, e.getY())));
            emit("Bezier keyframe handle adjusted");
        });
        return point;
    }

    private VBox buildVfxNodeLayerEditor() {
        Button addLayer = new Button("Add VFX Layer");
        addLayer.setOnAction(e -> {
            VfxCompositionService vfx = project.getProfessionalEditingSuite().getVfxCompositionService();
            VfxLayer layer = new VfxLayer("layer-" + System.nanoTime(), "source-clip");
            vfx.addLayer(layer);
            refreshLayersAndNodes();
            PreviewEventBus.publishRefresh("VFX layer added");
            emit("VFX layer created: " + layer.getLayerId());
        });

        ComboBox<BlendMode> blendModeCombo = new ComboBox<>();
        blendModeCombo.getItems().setAll(BlendMode.values());
        blendModeCombo.getSelectionModel().select(BlendMode.NORMAL);
        blendModeCombo.valueProperty().addListener((obs, oldV, newV) -> applyBlendModeToSelectedLayer(newV));

        Button toggleLayer = new Button("Toggle Layer");
        toggleLayer.setOnAction(e -> toggleSelectedLayer());

        Button addNode = new Button("Add VFX Node");
        addNode.setOnAction(e -> {
            VfxCompositionService vfx = project.getProfessionalEditingSuite().getVfxCompositionService();
            EffectNode node = vfx.getNodeGraph().addNode(NodeType.EFFECT, "Particle/3D Node " + (vfx.getNodeGraph().getNodes().size() + 1));
            refreshLayersAndNodes();
            emit("VFX node added: " + node.getLabel());
        });

        Button addRoto = new Button("Add Rotoscope Node");
        addRoto.setOnAction(e -> {
            VfxCompositionService vfx = project.getProfessionalEditingSuite().getVfxCompositionService();
            vfx.getNodeGraph().addNode(NodeType.EFFECT, "Roto Node " + (vfx.getNodeGraph().getNodes().size() + 1));
            refreshLayersAndNodes();
            emit("Rotoscope node added");
        });

        Button addChromaNode = new Button("Add Chroma Node");
        addChromaNode.setOnAction(e -> {
            project.getProfessionalEditingSuite().getVfxCompositionService().getNodeGraph().addNode(NodeType.CHROMA_KEY, "Chroma Key");
            refreshLayersAndNodes();
            emit("Chroma key node inserted in VFX graph");
        });

        Region nodeCanvasHint = new Region();
        nodeCanvasHint.setPrefHeight(120);
        nodeCanvasHint.setStyle("-fx-background-color: #111827; -fx-border-color: #334155; -fx-border-radius: 8;");

        VBox box = new VBox(10,
                new Label("Node / Layer VFX Editor"),
                new Label("Layers"),
                layerList,
                new HBox(8, addLayer, toggleLayer, new Label("Blend"), blendModeCombo),
                new Label("Nodes"),
                vfxNodeList,
                new HBox(8, addNode, addRoto, addChromaNode),
                nodeCanvasHint,
                new Label("Supports: rotoscoping, particles, 3D graphics, compositing flow")
        );
        VBox.setVgrow(layerList, Priority.ALWAYS);
        VBox.setVgrow(vfxNodeList, Priority.ALWAYS);
        return box;
    }

    private void applyBlendModeToSelectedLayer(BlendMode mode) {
        int idx = layerList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || mode == null) {
            return;
        }
        List<VfxLayer> layers = project.getProfessionalEditingSuite().getVfxCompositionService().getLayers();
        if (idx >= layers.size()) {
            return;
        }
        layers.get(idx).setBlendMode(mode);
        refreshLayersAndNodes();
        PreviewEventBus.publishRefresh("VFX blend mode set: " + mode);
        emit("Layer blend mode set to " + mode);
    }

    private void toggleSelectedLayer() {
        int idx = layerList.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            emit("Select a layer first.");
            return;
        }
        List<VfxLayer> layers = project.getProfessionalEditingSuite().getVfxCompositionService().getLayers();
        if (idx >= layers.size()) {
            return;
        }
        VfxLayer layer = layers.get(idx);
        layer.setEnabled(!layer.isEnabled());
        refreshLayersAndNodes();
        PreviewEventBus.publishRefresh("VFX layer toggled");
        emit("Layer " + layer.getLayerId() + " " + (layer.isEnabled() ? "enabled" : "disabled"));
    }

    private void refreshKeyframeList() {
        keyframeList.getItems().clear();
        var tracks = project.getProfessionalEditingSuite().getVfxCompositionService().getAnimationService().getTracksByParameter();
        tracks.forEach((param, track) -> {
            for (Keyframe keyframe : track.getKeyframes()) {
                keyframeList.getItems().add(param + " @" + keyframe.getFrame() + " = "
                        + String.format("%.3f", keyframe.getValue()) + "  (" + keyframe.getEasingType() + ")");
            }
        });
    }

    private void refreshLayersAndNodes() {
        layerList.getItems().clear();
        for (VfxLayer layer : project.getProfessionalEditingSuite().getVfxCompositionService().getLayers()) {
            layerList.getItems().add(layer.getLayerId() + " [" + layer.getBlendMode() + "] "
                    + (layer.isEnabled() ? "ON" : "OFF"));
        }

        vfxNodeList.getItems().clear();
        for (EffectNode node : project.getProfessionalEditingSuite().getVfxCompositionService().getNodeGraph().getNodes()) {
            vfxNodeList.getItems().add(node.getType() + " • " + node.getLabel());
        }
    }

    private void refreshTargetClipSelector() {
        targetClipSelector.getItems().clear();
        Sequence active = project.getActiveSequence();
        if (active == null) {
            return;
        }
        for (Track track : active.getTimelineModel().getTracks()) {
            for (Clip clip : track.getClips()) {
                if (track.getType() == Track.TrackType.OVERLAY || track.getType() == Track.TrackType.TEXT || track.getType() == Track.TrackType.VIDEO) {
                    targetClipSelector.getItems().add(clip.getSourcePath());
                }
            }
        }
        if (!targetClipSelector.getItems().isEmpty()) {
            targetClipSelector.getSelectionModel().select(0);
        }
    }

    private class VfxPanelController {
        /** Motion tracking panel action: generate tracking data and attach transforms to selected overlay/text clip. */
        void applyMotionTrackingToSelection() {
            String target = targetClipSelector.getValue();
            if (target == null || target.isBlank()) {
                emit("Select an overlay/text clip target first.");
                return;
            }
            VfxCompositionService vfx = project.getProfessionalEditingSuite().getVfxCompositionService();
            String subjectId = "subject-" + System.currentTimeMillis();
            List<TrackPoint> points = new ArrayList<>();
            points.add(new TrackPoint(0, 0.42, 0.38));
            points.add(new TrackPoint(30, 0.49, 0.44));
            points.add(new TrackPoint(60, 0.62, 0.53));

            vfx.getMotionAttachService().setTrackingData(subjectId, points);
            vfx.getMotionAttachService().attach(subjectId, target);

            ProjectAsset asset = findOrCreateAsset(target);
            project.getMetadataTagService().setTag(asset.getAssetId(), "vfx.motion.subject", subjectId);
            project.getMetadataTagService().setTag(asset.getAssetId(), "vfx.motion.points", String.valueOf(points.size()));
            PreviewEventBus.publishRefresh("Motion tracking transform applied");
            emit("Motion tracking applied to selected target: " + target);
        }

        void runOpenCvTracking(boolean detectFaces) {
            String target = targetClipSelector.getValue();
            if (target == null || target.isBlank()) {
                emit("Select a clip in Target Clip first.");
                return;
            }
            try {
                MotionTrackingResult result = openCVAiService.trackMotion(
                        target,
                        detectFaces,
                        true,
                        backgroundReplaceField.getText()
                );

                VfxCompositionService vfx = project.getProfessionalEditingSuite().getVfxCompositionService();
                vfx.getMotionAttachService().setTrackingData(result.getSubjectId(), result.getTrackPoints());
                vfx.getMotionAttachService().attach(result.getSubjectId(), target);

                ProjectAsset asset = findOrCreateAsset(target);
                project.getMetadataTagService().setTag(asset.getAssetId(), "opencv.subject", result.getSubjectId());
                project.getMetadataTagService().setTag(asset.getAssetId(), "opencv.mask", result.getMaskOutputPath());
                project.getMetadataTagService().setTag(asset.getAssetId(), "opencv.points", String.valueOf(result.getTrackPoints().size()));
                if (backgroundReplaceField.getText() != null && !backgroundReplaceField.getText().isBlank()) {
                    project.getMetadataTagService().setTag(asset.getAssetId(), "opencv.bg", backgroundReplaceField.getText().trim());
                }

                PreviewEventBus.publishRefresh("OpenCV motion tracking applied");
                emit("OpenCV tracking complete: " + result.getTrackPoints().size() + " points, mask=" + result.getMaskOutputPath());
            } catch (AiExecutionException ex) {
                emit("Track Motion failed: " + ex.getMessage());
            }
        }

        private ProjectAsset findOrCreateAsset(String sourcePath) {
            for (ProjectAsset asset : project.getAssets().values()) {
                if (sourcePath.equalsIgnoreCase(asset.getSourcePath())) {
                    return asset;
                }
            }
            String assetId = "asset-" + System.currentTimeMillis();
            ProjectAsset asset = new ProjectAsset(assetId, sourcePath, MediaType.VIDEO);
            project.registerAsset(asset);
            return asset;
        }
    }

    private void chooseBackgroundReplacement() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Background Replacement Media");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Media", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp", "*.mp4", "*.mov", "*.mkv")
        );
        File assetsDir = ProjectPathResolver.assetsDir().toFile();
        if (assetsDir.exists() && assetsDir.isDirectory()) {
            chooser.setInitialDirectory(assetsDir);
        }
        Window owner = getScene() == null ? null : getScene().getWindow();
        File chosen = chooser.showOpenDialog(owner);
        if (chosen == null) {
            return;
        }
        backgroundReplaceField.setText(ProjectPathResolver.toRelativeProjectPath(chosen.toPath()));
    }

    private void emit(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }
}