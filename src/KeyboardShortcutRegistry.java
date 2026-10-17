import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public final class KeyboardShortcutRegistry {
    private KeyboardShortcutRegistry() {
    }

    public static void apply(Scene scene, ProfessionalWorkspaceView workspaceView) {
        if (scene == null || workspaceView == null) {
            return;
        }

        // Timeline / core editing
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN),
                () -> workspaceView.getTimelineEditingPanel().performBladeAtPlayhead()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                () -> workspaceView.getTimelineEditingPanel().toggleSnapping()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN),
                () -> workspaceView.getTimelineEditingPanel().toggleProxyMode()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN),
                () -> workspaceView.getTimelineEditingPanel().quickAddClip()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                () -> workspaceView.getTimelineEditingPanel().quickAddText()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                () -> workspaceView.getTimelineEditingPanel().quickAddOverlay()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN),
                () -> workspaceView.getTimelineEditingPanel().quickAddMusic()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DELETE),
                () -> workspaceView.getTimelineEditingPanel().deleteSelectedClips()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.BACK_SPACE),
                () -> workspaceView.getTimelineEditingPanel().deleteSelectedClips()
        );

        // Section navigation
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.ALT_DOWN),
                () -> workspaceView.activateTab(ProfessionalWorkspaceView.TAB_EDITOR)
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.ALT_DOWN),
                () -> workspaceView.activateTab(ProfessionalWorkspaceView.TAB_TIMELINE)
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.ALT_DOWN),
                () -> workspaceView.activateTab(ProfessionalWorkspaceView.TAB_COLOR)
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.ALT_DOWN),
                () -> workspaceView.activateTab(ProfessionalWorkspaceView.TAB_AUDIO)
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.ALT_DOWN),
                () -> workspaceView.activateTab(ProfessionalWorkspaceView.TAB_VFX)
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT6, KeyCombination.ALT_DOWN),
                () -> workspaceView.activateTab(ProfessionalWorkspaceView.TAB_COLLABORATION)
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.DIGIT7, KeyCombination.ALT_DOWN),
                () -> workspaceView.activateTab(ProfessionalWorkspaceView.TAB_EXPORT)
        );

        // Render queue
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                () -> workspaceView.getExportRenderPanel().queueCurrentExportPreset()
        );

        // Preview transport
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.SPACE),
                () -> {
                    if (workspaceView.hasVideoPreviewPanel()) {
                        workspaceView.getVideoPlayerPanel().playPreview();
                    } else {
                        workspaceView.setStatus("Preview panel unavailable in safe mode.");
                    }
                }
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.SPACE, KeyCombination.SHIFT_DOWN),
                () -> {
                    if (workspaceView.hasVideoPreviewPanel()) {
                        workspaceView.getVideoPlayerPanel().pausePreview();
                    } else {
                        workspaceView.setStatus("Preview panel unavailable in safe mode.");
                    }
                }
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.K),
                () -> {
                    if (workspaceView.hasVideoPreviewPanel()) {
                        workspaceView.getVideoPlayerPanel().stopPreview();
                    } else {
                        workspaceView.setStatus("Preview panel unavailable in safe mode.");
                    }
                }
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.PERIOD),
                () -> {
                    if (workspaceView.hasVideoPreviewPanel()) {
                        workspaceView.getVideoPlayerPanel().stepFrame();
                    } else {
                        workspaceView.setStatus("Preview panel unavailable in safe mode.");
                    }
                }
        );
    }
}
