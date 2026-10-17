import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AudioPostPanel extends BorderPane {
    private final ProfessionalProject project;
    private final Consumer<String> statusSink;

    private final ObservableList<String> routingRows = FXCollections.observableArrayList();
    private final ListView<String> processorChainList = new ListView<>();
    private final ListView<String> foleyLibraryList = new ListView<>();
    private final AudioPanelController audioController;
    private final Map<String, StripState> stripStateByName = new HashMap<>();

    public AudioPostPanel(ProfessionalProject project, Consumer<String> statusSink) {
        this.project = project;
        this.statusSink = statusSink;
        this.audioController = new AudioPanelController();
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #0f172a;");

        SplitPane split = new SplitPane(buildMixerPanel(), buildRoutingAndLibrary());
        split.setDividerPositions(0.60);
        setCenter(split);

        refreshProcessorList();
        refreshFoleyList();
    }

    private VBox buildMixerPanel() {
        HBox stripRow = new HBox(10,
                createStrip("DX 1"),
                createStrip("DX 2"),
                createStrip("Music"),
                createStrip("FX"),
                createStrip("Master")
        );

        Button addDucking = new Button("Add Ducking");
        addDucking.setOnAction(e -> {
            AudioDuckingProcessor ducking = new AudioDuckingProcessor("duck-" + System.nanoTime());
            project.getProfessionalEditingSuite().getAudioPostService().addProcessor(ducking);
            refreshProcessorList();
            emit("Audio ducking processor added: " + ducking.getProcessorId());
        });

        Button addDynamics = new Button("Add Dynamics");
        addDynamics.setOnAction(e -> {
            DynamicsProcessor dynamics = new DynamicsProcessor("dyn-" + System.nanoTime());
            project.getProfessionalEditingSuite().getAudioPostService().addProcessor(dynamics);
            refreshProcessorList();
            emit("Dynamics processor added: " + dynamics.getProcessorId());
        });

        Button processPreview = new Button("Process Preview");
        processPreview.setOnAction(e -> {
            for (AudioProcessor processor : project.getProfessionalEditingSuite().getAudioPostService().getProcessors()) {
                processor.process("dialogue", "master");
            }
            PreviewEventBus.publishRefresh("Audio processor chain previewed");
            emit("Audio chain processed for preview route dialogue -> master");
        });

        VBox chainBox = new VBox(6,
                new Label("Processor Chain"),
                processorChainList,
                new HBox(8, addDucking, addDynamics, processPreview)
        );
        chainBox.setPadding(new Insets(8));
        chainBox.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");

        VBox mixer = new VBox(10,
                new Label("Mixer / Track Strips"),
                stripRow,
                chainBox
        );
        return mixer;
    }

    private VBox createStrip(String name) {
        stripStateByName.putIfAbsent(name, new StripState(name));

        Slider fader = new Slider(-60, 12, -6);
        fader.setOrientation(Orientation.VERTICAL);
        fader.setPrefHeight(220);

        Slider eqLow = new Slider(-12, 12, 0);
        Slider eqMid = new Slider(-12, 12, 0);
        Slider eqHigh = new Slider(-12, 12, 0);

        CheckBox comp = new CheckBox("Comp");
        CheckBox gate = new CheckBox("Gate");

        ComboBox<String> inputBus = new ComboBox<>();
        inputBus.getItems().addAll("Mic", "Boom", "Music", "SFX", "Aux");
        inputBus.getSelectionModel().select(0);

        Slider duckingAmount = new Slider(0, 1, 0.35);
        duckingAmount.valueProperty().addListener((obs, oldV, newV) -> {
            audioController.updateStripDucking(name, newV.doubleValue());
            emit(name + " ducking amount " + String.format("%.2f", newV.doubleValue()));
        });

        fader.valueProperty().addListener((obs, oldV, newV) -> {
            audioController.updateStripFader(name, newV.doubleValue());
            emit(name + " fader: " + String.format("%.1f dB", newV.doubleValue()));
        });
        eqLow.valueProperty().addListener((obs, oldV, newV) -> {
            audioController.updateStripEq(name, "low", newV.doubleValue());
            emit(name + " EQ Low " + String.format("%.1f", newV.doubleValue()));
        });
        eqMid.valueProperty().addListener((obs, oldV, newV) -> {
            audioController.updateStripEq(name, "mid", newV.doubleValue());
            emit(name + " EQ Mid " + String.format("%.1f", newV.doubleValue()));
        });
        eqHigh.valueProperty().addListener((obs, oldV, newV) -> {
            audioController.updateStripEq(name, "high", newV.doubleValue());
            emit(name + " EQ High " + String.format("%.1f", newV.doubleValue()));
        });

        comp.selectedProperty().addListener((obs, oldV, newV) -> audioController.toggleDynamics(name, newV, gate.isSelected()));
        gate.selectedProperty().addListener((obs, oldV, newV) -> audioController.toggleDynamics(name, comp.isSelected(), newV));
        inputBus.valueProperty().addListener((obs, oldV, newV) -> audioController.updateInputBus(name, newV));

        VBox strip = new VBox(6,
                new Label(name),
                fader,
                new Label("Input"), inputBus,
                new Label("EQ L"), eqLow,
                new Label("EQ M"), eqMid,
                new Label("EQ H"), eqHigh,
                comp,
                gate,
                new Label("Auto Duck"),
                duckingAmount
        );
        strip.setAlignment(Pos.TOP_CENTER);
        strip.setPadding(new Insets(8));
        strip.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        strip.setPrefWidth(118);
        return strip;
    }

    private BorderPane buildRoutingAndLibrary() {
        BorderPane right = new BorderPane();
        right.setTop(buildRoutingMatrix());
        right.setCenter(buildFoleyLibrary());
        return right;
    }

    private VBox buildRoutingMatrix() {
        Label title = new Label("Multichannel Routing Matrix");
        TableView<String> matrix = new TableView<>(routingRows);
        matrix.getColumns().add(new TableColumn<>("Bus / Channel Route"));

        TextField bus = new TextField("master");
        TextField layout = new TextField("5.1");
        Button apply = new Button("Apply Layout");
        apply.setOnAction(e -> {
            project.getProfessionalEditingSuite().getAudioPostService().getMultichannelAudioManager().configureBus(bus.getText(), layout.getText());
            refreshRoutingRows();
            PreviewEventBus.publishRefresh("Audio routing updated: " + bus.getText() + " -> " + layout.getText());
            emit("Routing updated: " + bus.getText() + " -> " + layout.getText());
        });

        Button atmos = new Button("Set Atmos 7.1.4");
        atmos.setOnAction(e -> {
            bus.setText("master-atmos");
            layout.setText("7.1.4");
            project.getProfessionalEditingSuite().getAudioPostService().getMultichannelAudioManager().configureBus("master-atmos", "7.1.4");
            refreshRoutingRows();
            PreviewEventBus.publishRefresh("Audio routing updated: master-atmos -> 7.1.4");
            emit("Atmos routing enabled: 7.1.4");
        });

        GridPane controls = new GridPane();
        controls.setHgap(6);
        controls.setVgap(6);
        controls.addRow(0, new Label("Bus"), bus, new Label("Layout"), layout);
        controls.addRow(1, apply, atmos);

        VBox box = new VBox(8, title, matrix, controls);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        refreshRoutingRows();
        return box;
    }

    private VBox buildFoleyLibrary() {
        TextField search = new TextField();
        search.setPromptText("Search Foley / SFX");
        search.textProperty().addListener((obs, oldV, newV) -> filterFoley(newV));

        Button add = new Button("Add Foley Asset");
        add.setOnAction(e -> audioController.importFoleyFromDisk());

        Button addAmbience = new Button("Add Ambience");
        addAmbience.setOnAction(e -> audioController.importAmbienceFromDisk());

        Button audition = new Button("Audition Selected");
        audition.setOnAction(e -> {
            String selected = foleyLibraryList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                emit("Select a Foley item first.");
                return;
            }
            emit("Auditioning " + selected + " (preview integration point)");
        });

        VBox box = new VBox(8,
                new Label("Foley / SFX Library"),
                search,
                foleyLibraryList,
                new HBox(8, add, addAmbience, audition),
                new Label("Tip: drag entries from this list to audio tracks in Timeline tab.")
        );
        VBox.setVgrow(foleyLibraryList, Priority.ALWAYS);
        box.setPadding(new Insets(8));
        return box;
    }

    private void refreshProcessorList() {
        processorChainList.getItems().clear();
        for (AudioProcessor processor : project.getProfessionalEditingSuite().getAudioPostService().getProcessors()) {
            processorChainList.getItems().add(processor.getProcessorId() + "  (" + processor.getClass().getSimpleName() + ")");
        }
    }

    private void refreshRoutingRows() {
        routingRows.clear();
        String[] buses = {"master", "master-atmos", "dialogue", "music", "sfx"};
        for (String bus : buses) {
            String layout = project.getProfessionalEditingSuite().getAudioPostService().getMultichannelAudioManager().getChannelLayout(bus);
            routingRows.add(bus + " -> " + layout);
        }
    }

    private void refreshFoleyList() {
        foleyLibraryList.getItems().setAll(project.getProfessionalEditingSuite().getAudioPostService().getFoleyLibraryService().getEffects());
    }

    private void filterFoley(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        foleyLibraryList.getItems().clear();
        for (String effect : project.getProfessionalEditingSuite().getAudioPostService().getFoleyLibraryService().getEffects()) {
            if (q.isEmpty() || effect.toLowerCase().contains(q)) {
                foleyLibraryList.getItems().add(effect);
            }
        }
    }

    private void emit(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }

    private class AudioPanelController {
        /** Mixer action: update strip fader in backend-bound strip state and trigger processor preview update. */
        void updateStripFader(String stripName, double valueDb) {
            StripState state = stripStateByName.computeIfAbsent(stripName, StripState::new);
            state.faderDb = valueDb;
            processStrip(state);
        }

        /** Mixer action: update EQ low/mid/high value in backend-bound strip state and trigger preview update. */
        void updateStripEq(String stripName, String band, double value) {
            StripState state = stripStateByName.computeIfAbsent(stripName, StripState::new);
            switch (band.toLowerCase()) {
                case "low" -> state.eqLow = value;
                case "mid" -> state.eqMid = value;
                case "high" -> state.eqHigh = value;
                default -> {
                }
            }
            processStrip(state);
        }

        /** Dynamics action: enable/disable comp+gate state and trigger processor execution. */
        void toggleDynamics(String stripName, boolean compEnabled, boolean gateEnabled) {
            StripState state = stripStateByName.computeIfAbsent(stripName, StripState::new);
            state.compEnabled = compEnabled;
            state.gateEnabled = gateEnabled;
            if (compEnabled || gateEnabled) {
                project.getProfessionalEditingSuite().getAudioPostService().addProcessor(new DynamicsProcessor("dyn-" + stripName.toLowerCase()));
            }
            processStrip(state);
        }

        /** Routing action: update strip input bus and sync bus layout in multichannel backend manager. */
        void updateInputBus(String stripName, String inputBus) {
            StripState state = stripStateByName.computeIfAbsent(stripName, StripState::new);
            state.inputBus = inputBus == null ? "Mic" : inputBus;
            String busId = stripName.toLowerCase().replace(" ", "-");
            project.getProfessionalEditingSuite().getAudioPostService().getMultichannelAudioManager().configureBus(busId, "stereo");
            refreshRoutingRows();
        }

        /** Ducking action: update sidechain amount and dispatch existing ducking processors on preview route. */
        void updateStripDucking(String stripName, double amount) {
            StripState state = stripStateByName.computeIfAbsent(stripName, StripState::new);
            state.ducking = amount;
            AudioDuckingProcessor duck = new AudioDuckingProcessor("duck-" + stripName.toLowerCase());
            duck.setThresholdDb(-24 + (amount * 18));
            duck.setRatio(1.5 + amount * 6);
            project.getProfessionalEditingSuite().getAudioPostService().addProcessor(duck);
            processStrip(state);
        }

        /** Foley action: use native chooser and import one or multiple local SFX assets. */
        void importFoleyFromDisk() {
            List<File> files = chooseAudioFiles("Import Foley / SFX");
            if (files.isEmpty()) {
                return;
            }
            int imported = 0;
            for (File file : files) {
                if (!isAudio(file)) {
                    continue;
                }
                String stored = importAudioToAssets(file);
                if (stored == null) {
                    continue;
                }
                project.getProfessionalEditingSuite().getAudioPostService().getFoleyLibraryService().addEffect(stored);
                imported++;
            }
            refreshFoleyList();
            PreviewEventBus.publishRefresh("Foley library updated");
            emit(imported > 0 ? "Imported " + imported + " Foley/SFX asset(s)." : "No supported audio files selected.");
        }

        /** Foley action: import ambience loops from disk into Foley asset manager library. */
        void importAmbienceFromDisk() {
            List<File> files = chooseAudioFiles("Import Ambience");
            if (files.isEmpty()) {
                return;
            }
            int imported = 0;
            for (File file : files) {
                if (!isAudio(file)) {
                    continue;
                }
                String stored = importAudioToAssets(file);
                if (stored == null) {
                    continue;
                }
                project.getProfessionalEditingSuite().getAudioPostService().getFoleyLibraryService().addEffect(stored);
                imported++;
            }
            refreshFoleyList();
            PreviewEventBus.publishRefresh("Ambience library updated");
            emit(imported > 0 ? "Imported ambience file(s): " + imported : "No ambience files imported.");
        }

        private void processStrip(StripState state) {
            if (state == null) {
                return;
            }
            String outputBus = "master";
            String inputBus = state.inputBus == null ? "Mic" : state.inputBus;
            for (AudioProcessor processor : project.getProfessionalEditingSuite().getAudioPostService().getProcessors()) {
                processor.process(inputBus.toLowerCase(), outputBus);
            }
        }

        private boolean isAudio(File file) {
            if (file == null) {
                return false;
            }
            String n = file.getName().toLowerCase();
            return n.endsWith(".wav") || n.endsWith(".mp3") || n.endsWith(".aac") || n.endsWith(".flac");
        }

        private List<File> chooseAudioFiles(String title) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aac", "*.flac"));
            File assetsDir = ProjectPathResolver.assetsDir().toFile();
            if (assetsDir.exists() && assetsDir.isDirectory()) {
                chooser.setInitialDirectory(assetsDir);
            }
            Window owner = getScene() == null ? null : getScene().getWindow();
            List<File> files = chooser.showOpenMultipleDialog(owner);
            return files == null ? java.util.List.of() : files;
        }

        private String importAudioToAssets(File file) {
            try {
                return ProjectPathResolver.importIntoAssets(file);
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Import Error");
                alert.setHeaderText("Unable to import audio file");
                alert.setContentText(ex.getMessage() == null ? "Unknown audio import error." : ex.getMessage());
                alert.showAndWait();
                return null;
            }
        }
    }

    private static class StripState {
        final String stripName;
        String inputBus = "Mic";
        double faderDb = -6;
        double eqLow;
        double eqMid;
        double eqHigh;
        double ducking = 0.35;
        boolean compEnabled;
        boolean gateEnabled;

        StripState(String stripName) {
            this.stripName = stripName;
        }
    }
}