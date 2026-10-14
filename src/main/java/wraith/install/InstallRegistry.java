package wraith.install;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import wraith.config.JsonStore;
import wraith.config.LocalLog;

public final class InstallRegistry {
    public static final class Data { public List<InstalledProgram> installs = new ArrayList<>(); }
    private final JsonStore<Data> store;
    private Data data;
    public InstallRegistry(Path configDirectory, LocalLog log) { store = new JsonStore<>(configDirectory.resolve("installs.json"), Data.class, Data::new, log); data = store.load(); if (data.installs == null) data.installs = new ArrayList<>(); }
    public synchronized Optional<InstalledProgram> find(String owner, String repo) { return data.installs.stream().filter(record -> record.owner.equals(owner) && record.repo.equals(repo)).findFirst(); }
    public synchronized List<InstalledProgram> all() { return List.copyOf(data.installs); }
    public synchronized void put(InstalledProgram record) throws java.io.IOException { data.installs.removeIf(item -> item.owner.equals(record.owner) && item.repo.equals(record.repo)); data.installs.add(record); store.save(data); }
    public synchronized void remove(String owner, String repo) throws java.io.IOException { data.installs.removeIf(item -> item.owner.equals(owner) && item.repo.equals(repo)); store.save(data); }
}
