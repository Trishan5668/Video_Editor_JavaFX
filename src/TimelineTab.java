import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;

public class TimelineTab extends BorderPane {
    public TimelineTab(TimelineEditingPanel timelineEditingPanel) {
        setPadding(new Insets(0));
        setStyle("-fx-background-color: #0f172a;");
        setCenter(timelineEditingPanel.getTimelineTabContent());
    }
}
