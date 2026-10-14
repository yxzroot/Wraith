package wraith.core;

import wraith.config.ConfigStore;
import wraith.config.WraithConfig;
import wraith.github.LibraryProvider;
import wraith.install.InstallRegistry;
import wraith.install.MinecraftModInstaller;
import wraith.install.ProgramInstaller;
import wraith.github.GitHubCache;
import wraith.github.RateLimitChecker;
import wraith.platform.OsActions;
import wraith.platform.PlatformPaths;

public final class AppContext {
    public final AppInfo info;
    public final PlatformPaths paths;
    public final OsActions actions;
    public final ConfigStore configStore;
    public final WraithConfig config;
    public LibraryProvider library;
    public final InstallRegistry installs;
    public final ProgramInstaller installer;
    public final MinecraftModInstaller minecraftInstaller;
    public final GitHubCache cache;
    public final RateLimitChecker rateLimit;

    public AppContext(AppInfo info, PlatformPaths paths, OsActions actions, ConfigStore configStore, WraithConfig config, LibraryProvider library, InstallRegistry installs, GitHubCache cache, RateLimitChecker rateLimit) {
        this.info = info; this.paths = paths; this.actions = actions; this.configStore = configStore; this.config = config; this.library = library; this.installs = installs; this.cache = cache; this.rateLimit = rateLimit;
        installer = new ProgramInstaller(paths, installs); minecraftInstaller = new MinecraftModInstaller();
    }

    public void saveQuietly() {
        try { configStore.save(config); }
        catch (Exception error) { configStore.log().error("Could not save local settings", error); }
    }
}
