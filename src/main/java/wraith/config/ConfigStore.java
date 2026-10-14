package wraith.config;

import java.nio.file.Path;

public final class ConfigStore {
    private final LocalLog log;
    private final JsonStore<WraithConfig> store;
    public ConfigStore(Path directory) {
        log = new LocalLog(directory);
        store = new JsonStore<>(directory.resolve("config.json"), WraithConfig.class, WraithConfig::new, log);
    }
    public WraithConfig load() { return store.load(); }
    public void save(WraithConfig config) throws java.io.IOException { store.save(config); }
    public LocalLog log() { return log; }
    public Path path() { return store.path(); }
}
