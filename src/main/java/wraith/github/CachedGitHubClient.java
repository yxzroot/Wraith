package wraith.github;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/** Applies cache, ETag, timeout failures, and rate-limit fallback around GitHubApi. */
public final class CachedGitHubClient {
    public static final long TTL_MILLIS = 10 * 60 * 1000L;
    private final GitHubApi api;
    private final GitHubCache cache;
    private final java.util.function.Supplier<String> token;
    public CachedGitHubClient(GitHubApi api, GitHubCache cache, java.util.function.Supplier<String> token) { this.api = api; this.cache = cache; this.token = token; }

    public Result get(URI uri, boolean force) {
        String key = uri.toString(); Optional<CachedResponse> cached = cache.read(key); long now = System.currentTimeMillis();
        if (!force && cached.isPresent() && now - cached.get().fetchedAt < TTL_MILLIS) return new Result(cached.get().body, 200, true, false, false, "", cached.get().nextLink);
        try {
            GitHubResponse response = api.get(uri, cached.map(value -> value.etag).orElse(""), token.get());
            if (response.notModified() && cached.isPresent()) {
                cache.write(key, new CachedResponse(cached.get().etag, cached.get().body, cached.get().nextLink, now));
                return new Result(cached.get().body, 200, true, false, false, "", cached.get().nextLink);
            }
            if (response.successful()) {
                String next = response.nextLink().orElse("");
                cache.write(key, new CachedResponse(response.etag(), response.body(), next, now));
                return new Result(response.body(), response.status(), false, false, false, "", next);
            }
            if (response.status() == 403 || response.status() == 429) {
                String message = "GitHub rate limit reached, showing cached data" + (response.rateReset() == null ? "." : ". Try again at " + response.rateReset() + ".");
                return new Result(cached.map(value -> value.body).orElse(""), response.status(), cached.isPresent(), true, false, message, "");
            }
            return new Result("", response.status(), false, false, false, "", "");
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Result(cached.map(value -> value.body).orElse(""), 0, cached.isPresent(), false, true, "Offline — showing cached data.", "");
        }
    }
    public record Result(String body, int status, boolean fromCache, boolean rateLimited, boolean offline, String notice, String nextLink) { }
}
