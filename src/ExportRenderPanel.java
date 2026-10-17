import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExportRenderPanel extends BorderPane {
    private final ProfessionalProject project;
    private final VideoEngine videoEngine;
    private final java.util.function.Consumer<String> statusSink;

    private final ComboBox<ExportFormat> presetCombo = new ComboBox<>();
    private final ComboBox<FrameRatePreset> frameRateCombo = new ComboBox<>();
    private final ComboBox<String> rawIntentCombo = new ComboBox<>();
    private final TextField outputPathField = new TextField("exports/pro_export.mov");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final ListView<RenderQueueItem> queueList = new ListView<>();
    private final ObservableList<RenderQueueItem> queueItems = FXCollections.observableArrayList();
    private final TextArea exportLog = new TextArea();
    private final Slider bitDepthSlider = new Slider(8, 16, 10);
    private final CheckBox useRawToggle = new CheckBox("Use RAW source media");
    private final ExportPanelController exportController;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pro-render-queue");
        t.setDaemon(true);
        return t;
    });

    public ExportRenderPanel(ProfessionalProject project, VideoEngine videoEngine, java.util.function.Consumer<String> statusSink) {
        this.project = project;
        this.videoEngine = videoEngine;
        this.statusSink = statusSink;
        this.exportController = new ExportPanelController();

        setPadding(new Insets(8));
        setStyle("-fx-background-color: #0f172a;");

        setTop(buildPresetControls());
        setCenter(buildQueueView());
        setBottom(buildProgressControls());

        queueList.setItems(queueItems);
    }

    private VBox buildPresetControls() {
        presetCombo.getItems().setAll(ExportFormat.values());
        presetCombo.setValue(ExportFormat.PRORES);

        frameRateCombo.getItems().setAll(FrameRatePreset.values());
        frameRateCombo.setValue(FrameRatePreset.FPS_24);

        rawIntentCombo.getItems().setAll("Color Accuracy", "VFX Roundtrip", "Archive Master", "Fast Turnaround");
        rawIntentCombo.setValue("Color Accuracy");

        bitDepthSlider.setMajorTickUnit(2);
        bitDepthSlider.setMinorTickCount(1);
        bitDepthSlider.setShowTickLabels(true);

        useRawToggle.setSelected(true);

        Button addQueue = new Button("Add to Queue");
        addQueue.setOnAction(e -> queueCurrentExportPreset());

        Button browseOutput = new Button("Browse");
        browseOutput.setOnAction(e -> {
            String output = exportController.chooseOutputPath(presetCombo.getValue());
            if (output != null) {
                outputPathField.setText(output);
            }
        });

        Button renderNow = new Button("Render Selected");
        renderNow.setOnAction(e -> renderSelectedQueueItem());

        Button renderAll = new Button("Render All");
        renderAll.setOnAction(e -> renderAllQueueItems());

        Button previewOutput = new Button("Preview Selected Output");
        previewOutput.setOnAction(e -> {
            RenderQueueItem selected = queueList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                emit("Select a render queue item to preview.");
                return;
            }
            PreviewEventBus.publishExportPreview(selected.getOutput());
        });

        HBox row1 = new HBox(8,
                new Label("Preset"), presetCombo,
                new Label("Output"), outputPathField,
                browseOutput,
                addQueue,
                renderNow,
                renderAll,
                previewOutput
        );
        HBox.setHgrow(outputPathField, Priority.ALWAYS);

        HBox row2 = new HBox(8,
                new Label("FPS"), frameRateCombo,
                new Label("Bit Depth"), bitDepthSlider,
                new Label("RAW Intent"), rawIntentCombo,
                useRawToggle
        );
        HBox.setHgrow(bitDepthSlider, Priority.ALWAYS);

        return new VBox(8,
                new Label("Export Dialog (RAW, 10-bit, ProRes, DNxHR, DCP, IMF)"),
                row1,
                row2
        );
    }

    private VBox buildQueueView() {
        Button remove = new Button("Remove Selected");
        remove.setOnAction(e -> {
            RenderQueueItem selected = queueList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                queueItems.remove(selected);
                log("Removed queue item: " + selected);
            }
        });

        Button clear = new Button("Clear Queue");
        clear.setOnAction(e -> {
            queueItems.clear();
            log("Queue cleared");
        });

        exportLog.setPrefRowCount(6);
        exportLog.setEditable(false);

        return new VBox(8,
                new Label("Render Queue"),
                queueList,
                new HBox(8, remove, clear),
                new Label("Render Log"),
                exportLog
        );
    }

    private VBox buildProgressControls() {
        Label progressLabel = new Label("0%");
        progressBar.progressProperty().addListener((obs, oldV, newV) ->
                progressLabel.setText((int) Math.round(newV.doubleValue() * 100) + "%"));

        Button simulate = new Button("Simulate Progress");
        simulate.setOnAction(e -> {
            progressBar.setProgress(Math.min(1.0, progressBar.getProgress() + 0.1));
            emit("Render progress: " + (int) (progressBar.getProgress() * 100) + "%");
        });

        return new VBox(8,
                new HBox(8, new Label("Progress"), progressBar, progressLabel, simulate),
                new Label("Queue supports background and batch rendering.")
        );
    }

    public void queueCurrentExportPreset() {
        ExportFormat format = presetCombo.getValue() == null ? ExportFormat.H264_MP4 : presetCombo.getValue();
        String output = resolveOutputPath(outputPathField.getText(), format);
        RenderQueueItem item = new RenderQueueItem(format.name(), output, "Queued");
        queueItems.add(item);
        exportController.updateQueueMetadata(item);
        emit("Added export job to queue: " + item);
        log("Queued -> " + item);
    }

    private String resolveOutputPath(String base, ExportFormat format) {
        String value = (base == null || base.isBlank()) ? "exports/pro_export" : base;
        String extension = switch (format) {
            case DCP, IMF -> ".mxf";
            case RAW_10_BIT -> ".mov";
            case DNXHR -> ".mxf";
            case PRORES -> ".mov";
            case H264_MP4 -> ".mp4";
        };
        String withExt = (value.endsWith(".mp4") || value.endsWith(".mov") || value.endsWith(".mxf"))
                ? value
                : value + extension;
        String portable = ProjectPathResolver.toRelativeProjectPath(ProjectPathResolver.resolvePath(withExt));
        try {
            if (Path.of(portable).isAbsolute()) {
                String fileName = Path.of(portable).getFileName() == null
                        ? ("render_" + System.currentTimeMillis() + extension)
                        : Path.of(portable).getFileName().toString();
                return ProjectPathResolver.normalizeSeparators(Path.of("exports", fileName).toString());
            }
        } catch (Exception ignored) {
            return ProjectPathResolver.normalizeSeparators(Path.of("exports", "render_" + System.currentTimeMillis() + extension).toString());
        }
        return portable;
    }

    private void renderSelectedQueueItem() {
        RenderQueueItem item = queueList.getSelectionModel().getSelectedItem();
        if (item == null) {
            emit("Select a queue item first");
            return;
        }
        submitRender(item);
    }

    private void renderAllQueueItems() {
        if (queueItems.isEmpty()) {
            emit("Queue is empty");
            return;
        }
        for (RenderQueueItem item : queueItems) {
            if (!"Done".equalsIgnoreCase(item.getStatus())) {
                submitRender(item);
            }
        }
    }

    private void submitRender(RenderQueueItem item) {
        item.setStatus("Rendering");
        queueList.refresh();
        progressBar.setProgress(0.05);
        log("Rendering -> " + item);

        renderExecutor.submit(() -> {
            try {
                ExportRequest request = buildRequest(item);
                runRenderWithProgress(project, request, item);
                item.setStatus("Done");
                Platform.runLater(() -> {
                    queueList.refresh();
                    progressBar.setProgress(1.0);
                    emit("Render completed: " + item.getOutput());
                    log("Done -> " + item.getOutput());
                    PreviewEventBus.publishExportPreview(item.getOutput());
                });
            } catch (UnsupportedOperationException unsupported) {
                item.setStatus("Scaffolded");
                Platform.runLater(() -> {
                    queueList.refresh();
                    progressBar.setProgress(1.0);
                    emit("Professional renderer scaffold ready but engine path is not fully implemented yet.");
                    log("Scaffolded backend for -> " + item.getOutput());
                    PreviewEventBus.publishRefresh("Render scaffold completed for " + item.getOutput());
                });
            } catch (Exception ex) {
                item.setStatus("Failed");
                Platform.runLater(() -> {
                    queueList.refresh();
                    progressBar.setProgress(0);
                    emit("Render failed: " + ex.getMessage());
                    log("Failed -> " + ex.getMessage());
                    PreviewEventBus.publishRefresh("Render failed");
                });
            }
        });
    }

    private ExportRequest buildRequest(RenderQueueItem item) {
        int bitDepth = (int) Math.round(bitDepthSlider.getValue());
        ExportFormat format = ExportFormat.valueOf(item.getPreset());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("preset", item.getPreset());
        metadata.put("fps", String.valueOf(frameRateCombo.getValue().fps));
        metadata.put("delivery", "Professional");
        metadata.put("rawIntent", rawIntentCombo.getValue());

        return new ExportRequest(
                item.getOutput(),
                format,
                useRawToggle.isSelected(),
                bitDepth,
                metadata
        );
    }

    private void runRenderWithProgress(ProfessionalProject project, ExportRequest request, RenderQueueItem item) throws Exception {
        for (int i = 1; i <= 8; i++) {
            final double progress = i / 10.0;
            Platform.runLater(() -> progressBar.setProgress(progress));
            Thread.sleep(120);
        }
        videoEngine.renderProfessionalProject(project, request);
    }

    private void log(String msg) {
        exportLog.appendText(msg + System.lineSeparator());
    }

    private void emit(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }

    private class ExportPanelController {
        /** Export dialog action: open native save chooser to select output path and extension per selected format. */
        String chooseOutputPath(ExportFormat format) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Export Output");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("MP4", "*.mp4"),
                    new FileChooser.ExtensionFilter("MOV", "*.mov"),
                    new FileChooser.ExtensionFilter("MXF", "*.mxf")
            );
            chooser.setInitialFileName(defaultFileName(format == null ? ExportFormat.PRORES : format));
            File exportsDir = ProjectPathResolver.exportsDir().toFile();
            if (exportsDir.exists() && exportsDir.isDirectory()) {
                chooser.setInitialDirectory(exportsDir);
            }
            Window owner = getScene() == null ? null : getScene().getWindow();
            File file = chooser.showSaveDialog(owner);
            if (file == null) {
                return null;
            }
            String portable = ProjectPathResolver.toRelativeProjectPath(file.toPath());
            try {
                if (Path.of(portable).isAbsolute()) {
                    String fileName = file.toPath().getFileName() == null ? defaultFileName(format == null ? ExportFormat.PRORES : format) : file.toPath().getFileName().toString();
                    return ProjectPathResolver.normalizeSeparators(Path.of("exports", fileName).toString());
                }
            } catch (Exception ignored) {
                return ProjectPathResolver.normalizeSeparators(Path.of("exports", defaultFileName(format == null ? ExportFormat.PRORES : format)).toString());
            }
            return portable;
        }

        /** Export queue action: persist chosen export controls in metadata tags for backend render pipeline consumption. */
        void updateQueueMetadata(RenderQueueItem item) {
            if (item == null) {
                return;
            }
            String assetId = "export-job-" + System.currentTimeMillis();
            project.getMetadataTagService().setTag(assetId, "export.preset", item.getPreset());
            project.getMetadataTagService().setTag(assetId, "export.rawIntent", rawIntentCombo.getValue());
            project.getMetadataTagService().setTag(assetId, "export.bitDepth", String.valueOf((int) Math.round(bitDepthSlider.getValue())));
            project.getMetadataTagService().setTag(assetId, "export.useRaw", String.valueOf(useRawToggle.isSelected()));
        }

        private String defaultFileName(ExportFormat format) {
            String suffix = switch (format) {
                case DNXHR, DCP, IMF -> ".mxf";
                case H264_MP4 -> ".mp4";
                default -> ".mov";
            };
            return "render_" + System.currentTimeMillis() + suffix;
        }
    }

    private enum FrameRatePreset {
        FPS_24(24),
        FPS_25(25),
        FPS_30(30),
        FPS_48(48),
        FPS_60(60);

        private final int fps;

        FrameRatePreset(int fps) {
            this.fps = fps;
        }

        @Override
        public String toString() {
            return fps + " fps";
        }
    }
}