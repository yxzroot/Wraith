package wraith.install;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/** Streaming, allowlisted release download. Redirects are checked one hop at a time. */
public final class SecureReleaseDownloader {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public Download download(URI source, Path destination, String expectedDigest, Consumer<InstallProgress> progress, CancelToken cancel) throws IOException, InterruptedException {
        URI current = source;
        for (int redirect = 0; redirect < 6; redirect++) {
            if (!GitHubHostAllowlist.allows(current)) throw new IOException("Release download is not hosted by an allowed GitHub host.");
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(current).GET().timeout(Duration.ofSeconds(15)).header("User-Agent", "Wraith/1").build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 300 && response.statusCode() < 400) { String next = response.headers().firstValue("Location").orElse(""); if (next.isBlank()) throw new IOException("GitHub returned an invalid download redirect."); current = current.resolve(next); continue; }
            if (response.statusCode() != 200) throw new IOException("GitHub download returned HTTP " + response.statusCode() + ".");
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1); long copied = 0;
            try (InputStream input = response.body(); java.io.OutputStream output = Files.newOutputStream(destination)) { byte[] buffer = new byte[16 * 1024]; for (int read; (read = input.read(buffer)) >= 0;) { if (cancel.cancelled()) throw new IOException("Download cancelled."); output.write(buffer, 0, read); copied += read; progress.accept(new InstallProgress(total <= 0 ? -1 : (double) copied / total, "Downloading release…")); } }
            String hash = Hashing.sha256(destination);
            if (expectedDigest != null && expectedDigest.toLowerCase().startsWith("sha256:") && !hash.equalsIgnoreCase(expectedDigest.substring(7))) throw new IOException("The release SHA-256 did not match GitHub's digest.");
            return new Download(destination, hash, copied);
        }
        throw new IOException("Too many GitHub download redirects.");
    }
    public record Download(Path file, String hash, long size) { }
}
