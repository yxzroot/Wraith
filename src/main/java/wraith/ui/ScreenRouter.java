package wraith.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

final class ScreenRouter {
    private final StackPane host;
    private Node current;
    ScreenRouter(StackPane host) { this.host = host; }
    void show(Node next) {
        next.setOpacity(0); next.setTranslateY(10); host.getChildren().add(next);
        FadeTransition fade = new FadeTransition(Duration.millis(200), next); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(200), next); slide.setToY(0);
        ParallelTransition in = new ParallelTransition(fade, slide);
        Node old = current;
        in.setOnFinished(event -> { if (old != null) host.getChildren().remove(old); current = next; });
        in.play();
    }
}
