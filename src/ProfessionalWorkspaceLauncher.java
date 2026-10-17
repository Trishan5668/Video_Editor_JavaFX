import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import java.util.UUID;

public final class ProfessionalWorkspaceLauncher {
    private ProfessionalWorkspaceLauncher() {
    }

    public static void openWorkspace(ProfessionalProject project, VideoEngine videoEngine) {
        ProfessionalProject resolvedProject = ensureProject(project);

        Stage stage = new Stage();
        stage.setTitle("Professional Workspace - " + resolvedProject.getName());

        ProfessionalWorkspaceView workspaceView = new ProfessionalWorkspaceView(resolvedProject, videoEngine);
        ScrollPane workspaceScroll = new ScrollPane(workspaceView);
        workspaceScroll.setFitToWidth(false);
        workspaceScroll.setFitToHeight(false);
        workspaceScroll.setPannable(true);
        workspaceScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        workspaceScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        Scene scene = new Scene(workspaceScroll, 1600, 920);
        KeyboardShortcutRegistry.apply(scene, workspaceView);

        stage.setScene(scene);
        stage.show();
    }

    private static ProfessionalProject ensureProject(ProfessionalProject project) {
        ProfessionalProject resolved = project == null ? new ProfessionalProject("Professional Session") : project;
        if (resolved.getActiveSequence() == null) {
            resolved.addSequence(new Sequence(
                    UUID.randomUUID().toString(),
                    "Main Sequence",
                    new FrameRate(30)
            ));
        }
        return resolved;
    }
}
