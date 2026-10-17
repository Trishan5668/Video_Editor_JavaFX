import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight event bus for decoupled preview synchronization between UI panels.
 */
public final class PreviewEventBus {
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private PreviewEventBus() {
    }

    public interface Listener {
        default void onTimelineChanged() {
        }

        default void onScrubRequested(double timelineSeconds) {
        }

        default void onClipSelected(String sourcePath) {
        }

        default void onRefreshRequested(String reason) {
        }

        default void onExportPreviewRequested(String outputPath) {
        }
    }

    public static void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static void publishTimelineChanged() {
        for (Listener listener : listeners) {
            listener.onTimelineChanged();
        }
    }

    public static void publishScrub(double timelineSeconds) {
        for (Listener listener : listeners) {
            listener.onScrubRequested(timelineSeconds);
        }
    }

    public static void publishClipSelected(String sourcePath) {
        for (Listener listener : listeners) {
            listener.onClipSelected(sourcePath);
        }
    }

    public static void publishRefresh(String reason) {
        for (Listener listener : listeners) {
            listener.onRefreshRequested(reason);
        }
    }

    public static void publishExportPreview(String outputPath) {
        for (Listener listener : listeners) {
            listener.onExportPreviewRequested(outputPath);
        }
    }
}