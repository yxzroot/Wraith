package wraith.github;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Optional;

/** ETag-aware local cache; it contains public GitHub response bodies only. */
public final class GitHubCache {
    private final Path directory;
    private final Gson gson = new Gson();
    public GitHubCache(Path directory) { this.directory = directory.resolve("github"); }
    public Optional<CachedResponse> read(String key) {
        try {
            Path file = file(key); if (!Files.isRegularFile(file)) return Optional.empty();
            CachedResponse value = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), CachedResponse.class);
            return value == null ? Optional.empty() : Optional.of(value);
        } catch (Exception ignored) { return Optional.empty(); }
    }
    public void write(String key, CachedResponse value) {
        try {
            Files.createDirectories(directory); Path target = file(key); Path temp = Files.createTempFile(directory, "response-", ".tmp");
            try { Files.writeString(temp, gson.toJson(value), StandardCharsets.UTF_8); Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temp); }
        } catch (Exception ignored) { }
    }
    public void clear() {
        try { if (Files.exists(directory)) try (java.util.stream.Stream<Path> files = Files.walk(directory)) { for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); } }
        catch (Exception ignored) { }
    }
    private Path file(String key) throws Exception { return directory.resolve(hex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))) + ".json"); }
    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte item : bytes) out.append(String.format("%02x", item)); return out.toString(); }
}
