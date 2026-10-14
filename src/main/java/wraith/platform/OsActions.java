package wraith.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/** Explicit, local desktop integrations without shell command construction. */
public final class OsActions {
    private final Os os;
    public OsActions(Os os) { this.os = os; }

    public void openFolder(Path path) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile());
            return;
        }
        new ProcessBuilder(os == Os.WINDOWS ? List.of("explorer", path.toAbsolutePath().toString())
                : List.of("xdg-open", path.toAbsolutePath().toString())).start();
    }

    public void openUrl(String url) throws IOException {
        URI uri = URI.create(url);
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
            return;
        }
        new ProcessBuilder(os == Os.WINDOWS ? List.of("explorer", uri.toString()) : List.of("xdg-open", uri.toString())).start();
    }
}
