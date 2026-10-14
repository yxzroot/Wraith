package wraith.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/** Bounded local log. Callers must never include credentials in messages. */
public final class LocalLog {
    private final Path file;
    public LocalLog(Path directory) { file = directory.resolve("wraith.log"); }
    public synchronized void info(String text) { write("INFO", text, null); }
    public synchronized void warn(String text) { write("WARN", text, null); }
    public synchronized void error(String text, Exception error) { write("ERROR", text, error); }
    public Path file() { return file; }

    private void write(String level, String text, Exception error) {
        try {
            Files.createDirectories(file.getParent());
            String details = error == null ? "" : " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")";
            Files.writeString(file, Instant.now() + " [" + level + "] " + text + details + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() > 500) Files.write(file, lines.subList(lines.size() - 500, lines.size()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) { }
    }
}
