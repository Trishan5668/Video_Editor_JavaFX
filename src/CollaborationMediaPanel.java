import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

public class CollaborationMediaPanel extends BorderPane {
    private final ProfessionalProject project;
    private final Consumer<String> statusSink;

    private final ObservableList<String> lockRows = FXCollections.observableArrayList();
    private final ObservableList<String> metadataRows = FXCollections.observableArrayList();

    private final ListView<String> archiveSummaryList = new ListView<>();
    private final ListView<String> metadataSearchList = new ListView<>();
    private final CollaborationPanelController collaborationController;

    public CollaborationMediaPanel(ProfessionalProject project, Consumer<String> statusSink) {
        this.project = project;
        this.statusSink = statusSink;
        this.collaborationController = new CollaborationPanelController();
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #0f172a;");

        setLeft(buildSharedDashboard());
        setCenter(buildMetadataPanel());
        setRight(buildArchiveWizard());

        refreshLockRows("bin-main", "bin-color", "bin-audio", "bin-vfx");
    }

    private VBox buildSharedDashboard() {
        Label title = new Label("Shared Project Dashboard");
        title.setStyle("-fx-font-weight: bold;");

        TextField binId = new TextField("bin-main");
        TextField user = new TextField("editor-1");

        Button lock = new Button("Lock Bin");
        lock.setOnAction(e -> collaborationController.lockBin(binId.getText(), user.getText()));

        Button unlock = new Button("Unlock Bin");
        unlock.setOnAction(e -> collaborationController.unlockBin(binId.getText(), user.getText()));

        TableView<String> lockTable = new TableView<>(lockRows);
        lockTable.getColumns().add(new TableColumn<>("User Lock Indicators"));

        VBox box = new VBox(8,
                title,
                new Label("Bin"), binId,
                new Label("User"), user,
                new HBox(8, lock, unlock),
                lockTable,
                new Label("Timeline/bin lock indicators update live to avoid edit conflicts")
        );
        box.setPadding(new Insets(8));
        box.setPrefWidth(330);
        box.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        return box;
    }

    private VBox buildMetadataPanel() {
        TextField assetId = new TextField("asset-1");
        TextField key = new TextField("scene");
        TextField value = new TextField("A12");

        Button setTag = new Button("Set Tag");
        setTag.setOnAction(e -> collaborationController.setMetadataTag(assetId.getText(), key.getText(), value.getText()));

        Button registerAsset = new Button("Register Asset");
        registerAsset.setOnAction(e -> collaborationController.registerAsset(assetId.getText()));

        TextField search = new TextField();
        search.setPromptText("Search tags or assets");
        search.textProperty().addListener((obs, oldV, newV) -> runMetadataSearch(newV));

        ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList(
                "Asset Id", "Camera", "Take", "Scene", "Custom"
        ));
        sort.getSelectionModel().select(0);
        sort.valueProperty().addListener((obs, oldV, newV) -> runMetadataSearch(search.getText()));

        ListView<String> rows = new ListView<>(metadataRows);

        VBox box = new VBox(8,
                new Label("Metadata Tagging / Search / Sort"),
                new HBox(6, new Label("Asset"), assetId, registerAsset),
                new HBox(6, key, value, setTag),
                new HBox(6, search, sort),
                rows,
                new Label("Filtered results"),
                metadataSearchList
        );
        HBox.setHgrow(search, Priority.ALWAYS);
        VBox.setVgrow(rows, Priority.ALWAYS);
        VBox.setVgrow(metadataSearchList, Priority.ALWAYS);
        box.setPadding(new Insets(8));
        return box;
    }

    private VBox buildArchiveWizard() {
        TableView<String> wizardSteps = new TableView<>();
        wizardSteps.getColumns().add(new TableColumn<>("Project Archiving / Export Wizard"));
        wizardSteps.getItems().addAll(
                "1. Consolidate media",
                "2. Verify links and checksums",
                "3. Freeze metadata + collaboration state",
                "4. Build archive manifest",
                "5. Package delivery preset"
        );

        TextField target = new TextField("archives/project_001");
        Button run = new Button("Create Archive");
        run.setOnAction(e -> collaborationController.createArchive(target.getText()));

        Button browse = new Button("Browse");
        browse.setOnAction(e -> {
            String dir = collaborationController.chooseArchiveDirectory();
            if (dir != null) {
                target.setText(dir);
            }
        });

        Button dryRun = new Button("Dry Run");
        dryRun.setOnAction(e -> collaborationController.archiveDryRun());

        VBox box = new VBox(8,
                new Label("Archive Wizard"),
                wizardSteps,
                new HBox(8, target, browse),
                new HBox(8, run, dryRun),
                archiveSummaryList
        );
        box.setPadding(new Insets(8));
        box.setPrefWidth(330);
        box.setStyle("-fx-background-color: #111827; -fx-background-radius: 8;");
        VBox.setVgrow(archiveSummaryList, Priority.ALWAYS);
        return box;
    }

    private void refreshLockRows(String... bins) {
        lockRows.clear();
        for (String bin : bins) {
            if (bin == null || bin.isBlank()) {
                continue;
            }
            String owner = project.getCollaborationManager().getBinOwner(bin);
            lockRows.add(bin + " -> " + (owner == null ? "Unlocked" : "Locked by " + owner));
        }
    }

    private void refreshMetadataRows(String assetId) {
        metadataRows.clear();
        if (assetId == null || assetId.isBlank()) {
            return;
        }
        Map<String, String> tags = project.getMetadataTagService().getTags(assetId);
        if (tags.isEmpty()) {
            metadataRows.add("No tags for " + assetId);
            return;
        }
        tags.forEach((k, v) -> metadataRows.add(assetId + " :: " + k + " = " + v));
    }

    private void runMetadataSearch(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        metadataSearchList.getItems().clear();

        for (ProjectAsset asset : project.getAssets().values()) {
            boolean matchesAsset = q.isEmpty()
                    || asset.getAssetId().toLowerCase().contains(q)
                    || asset.getSourcePath().toLowerCase().contains(q);

            if (matchesAsset) {
                metadataSearchList.getItems().add(asset.getAssetId() + " -> " + asset.getSourcePath());
            }

            Map<String, String> tags = project.getMetadataTagService().getTags(asset.getAssetId());
            tags.forEach((k, v) -> {
                String row = asset.getAssetId() + " :: " + k + " = " + v;
                if (q.isEmpty() || row.toLowerCase().contains(q)) {
                    metadataSearchList.getItems().add(row);
                }
            });
        }

        if (metadataSearchList.getItems().isEmpty()) {
            metadataSearchList.getItems().add("No results");
        }
    }

    private void emit(String msg) {
        if (statusSink != null) {
            statusSink.accept(msg);
        }
    }

    private class CollaborationPanelController {
        /** Collaboration action: lock bin with ownership checks and refresh lock indicators for dashboard. */
        void lockBin(String binId, String userId) {
            boolean ok = project.getCollaborationManager().lockBin(binId, userId);
            refreshLockRows("bin-main", "bin-color", "bin-audio", "bin-vfx", binId);
            emit(ok ? "Bin locked by " + userId : "Bin already locked by another user");
        }

        /** Collaboration action: unlock bin for current owner and refresh lock indicators. */
        void unlockBin(String binId, String userId) {
            project.getCollaborationManager().unlockBin(binId, userId);
            refreshLockRows("bin-main", "bin-color", "bin-audio", "bin-vfx", binId);
            emit("Unlock requested by " + userId);
        }

        /** Metadata action: write key/value tag to backend metadata database and refresh panel table. */
        void setMetadataTag(String assetId, String key, String value) {
            project.getMetadataTagService().setTag(assetId, key, value);
            refreshMetadataRows(assetId);
            emit("Metadata updated for " + assetId);
        }

        /** Media management action: register new asset in project database for collaboration/search. */
        void registerAsset(String assetIdInput) {
            String id = (assetIdInput == null || assetIdInput.isBlank()) ? "asset-" + System.currentTimeMillis() : assetIdInput;
            project.registerAsset(new ProjectAsset(id, "assets/" + id + ".mov", MediaType.VIDEO));
            refreshMetadataRows(id);
            emit("Asset registered: " + id);
        }

        /** Archiving action: run backend archive flow and render archive summary list. */
        void createArchive(String targetPath) {
            ArchiveResult result = project.getProjectArchiver().archiveProject(project, targetPath);
            archiveSummaryList.getItems().clear();
            archiveSummaryList.getItems().add("Archive: " + result.getArchivePath());
            archiveSummaryList.getItems().add("Assets copied: " + result.getCopiedAssets().size());
            archiveSummaryList.getItems().addAll(result.getCopiedAssets());
            if (!result.getMissingAssets().isEmpty()) {
                archiveSummaryList.getItems().add("Missing assets: " + result.getMissingAssets().size());
                archiveSummaryList.getItems().addAll(result.getMissingAssets());
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Archive Warning");
                alert.setHeaderText("Some files are missing");
                alert.setContentText("Archive created with missing asset references. Please restore missing files in assets/.");
                alert.showAndWait();
            }
            emit("Archive prepared at: " + result.getArchivePath() + " (" + result.getCopiedAssets().size() + " assets)");
        }

        /** Archiving action: perform validation-only dry run for archive wizard. */
        void archiveDryRun() {
            archiveSummaryList.getItems().setAll(
                    "Dry Run Complete",
                    "Assets discovered: " + project.getAssets().size(),
                    "Sequences discovered: " + project.getSequences().size(),
                    "No files copied"
            );
            emit("Archive dry run completed");
        }

        /** Archive wizard action: open native directory chooser for archive destination selection. */
        String chooseArchiveDirectory() {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Archive Destination");
            File archivesDir = ProjectPathResolver.archivesDir().toFile();
            if (archivesDir.exists() && archivesDir.isDirectory()) {
                chooser.setInitialDirectory(archivesDir);
            }
            Window owner = getScene() == null ? null : getScene().getWindow();
            File selected = chooser.showDialog(owner);
            return selected == null ? null : ProjectPathResolver.toRelativeProjectPath(selected.toPath());
        }
    }
}