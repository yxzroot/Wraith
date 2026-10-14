package wraith;

import java.io.InputStream;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import wraith.config.ConfigStore;
import wraith.config.WraithConfig;
import wraith.core.AppContext;
import wraith.core.AppInfo;
import java.net.URI;
import wraith.github.GitHubCache;
import wraith.github.GitHubLibraryProvider;
import wraith.github.HttpGitHubApi;
import wraith.github.RateLimitChecker;
import wraith.platform.OsActions;
import wraith.platform.PlatformPaths;
import wraith.ui.WraithView;
import wraith.install.InstallRegistry;

/** Wraith is deliberately local-first; GitHub integration is introduced behind an interface in phase two. */
public final class WraithApp extends Application {
    @Override public void start(Stage stage) {
        PlatformPaths paths = PlatformPaths.system();
        ConfigStore store = new ConfigStore(paths.configDirectory());
        WraithConfig config = store.load();
        boolean tiling = paths.isTilingSession();
        boolean transparent = !tiling && !config.useSystemWindowFrame && Platform.isSupported(ConditionalFeature.TRANSPARENT_WINDOW);
        if (transparent) stage.initStyle(StageStyle.TRANSPARENT);
        else if (!config.useSystemWindowFrame) stage.initStyle(StageStyle.UNDECORATED);

        loadFonts();
        Scene scene = new Scene(new StackPane(), 900, 560, Color.TRANSPARENT);
        scene.getStylesheets().add(WraithApp.class.getResource("/wraith.css").toExternalForm());
        stage.setScene(scene); stage.setTitle("Wraith"); stage.setResizable(false);
        stage.setMinWidth(900); stage.setMaxWidth(900); stage.setMinHeight(560); stage.setMaxHeight(560);
        java.util.function.Supplier<String> token = () -> config.githubToken == null || config.githubToken.isBlank()
                ? System.getenv().getOrDefault("WRAITH_GITHUB_TOKEN", "") : config.githubToken;
        InstallRegistry installs = new InstallRegistry(paths.configDirectory(), store.log());
        HttpGitHubApi api = new HttpGitHubApi(); GitHubCache cache = new GitHubCache(paths.cacheDirectory());
        AppContext context = new AppContext(AppInfo.load(), paths, new OsActions(paths.os()), store, config,
                new GitHubLibraryProvider(URI.create("https://api.github.com/"), api, cache, config, store.log(), token, installs), installs, cache,
                new RateLimitChecker(api, URI.create("https://api.github.com/"), token));
        WraithView view = new WraithView(stage, context, !config.useSystemWindowFrame, tiling);
        if (config.windowX != null && config.windowY != null) { stage.setX(config.windowX); stage.setY(config.windowY); }
        stage.setOnCloseRequest(event -> { config.windowX = stage.getX(); config.windowY = stage.getY(); context.saveQuietly(); });
        stage.show(); view.showInitial();
    }

    private static void loadFonts() { load("/fonts/Inter-Regular.ttf"); load("/fonts/Inter-Medium.ttf"); load("/fonts/Inter-SemiBold.ttf"); }
    private static void load(String file) { try (InputStream input = WraithApp.class.getResourceAsStream(file)) { if (input != null) javafx.scene.text.Font.loadFont(input, 12); } catch (Exception ignored) { } }
    public static void main(String[] args) { System.setProperty("glass.gtk.application.id", "wraith"); launch(args); }
}
