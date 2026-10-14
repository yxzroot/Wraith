package wraith.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/** HTTPS-only API client. The normal constructor can only contact api.github.com. */
public final class HttpGitHubApi implements GitHubApi {
    private final HttpClient client;
    private final URI apiBase;
    private final boolean testEndpoint;

    public HttpGitHubApi() { this(URI.create("https://api.github.com/"), false); }
    public HttpGitHubApi(URI apiBase, boolean testEndpoint) {
        this.apiBase = apiBase; this.testEndpoint = testEndpoint;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public URI api(String path) { return apiBase.resolve(path.startsWith("/") ? path.substring(1) : path); }

    @Override public GitHubResponse get(URI uri, String etag, String token) throws IOException, InterruptedException {
        requireApiHost(uri);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "Wraith/1");
        if (etag != null && !etag.isBlank()) request.header("If-None-Match", etag);
        if (token != null && !token.isBlank() && "api.github.com".equalsIgnoreCase(uri.getHost())) request.header("Authorization", "Bearer " + token);
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        int remaining = parseInt(response.headers().firstValue("X-RateLimit-Remaining").orElse("-1"));
        Instant reset = parseReset(response.headers().firstValue("X-RateLimit-Reset").orElse(""));
        return new GitHubResponse(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(""),
                response.headers().firstValue("Link").orElse(""), remaining, reset);
    }

    private void requireApiHost(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !testEndpoint) throw new IOException("GitHub API requires HTTPS");
        if (!testEndpoint && !"api.github.com".equalsIgnoreCase(uri.getHost())) throw new IOException("Only api.github.com is allowed");
    }
    private static int parseInt(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return -1; } }
    private static Instant parseReset(String value) { try { return Instant.ofEpochSecond(Long.parseLong(value)); } catch (NumberFormatException ignored) { return null; } }
}
