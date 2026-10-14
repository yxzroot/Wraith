package wraith.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Small reusable atomic JSON store with corrupt-file recovery. */
public final class JsonStore<T> {
    private final Path file;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final LocalLog log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonStore(Path file, Class<T> type, Supplier<T> defaults, LocalLog log) {
        this.file = file; this.type = type; this.defaults = defaults; this.log = log;
    }

    public synchronized T load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) return defaults.get();
            T value = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
            if (value == null) throw new IOException("No JSON object");
            return value;
        } catch (Exception error) {
            try { if (Files.exists(file)) Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException backupError) { log.error("Could not back up corrupt " + file.getFileName(), backupError); }
            log.error("Corrupt " + file.getFileName() + "; using fresh local settings", error instanceof Exception ? (Exception) error : new IOException(error));
            return defaults.get();
        }
    }

    public synchronized void save(T value) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, gson.toJson(value), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public Path path() { return file; }
}
