package wraith.github;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wraith.config.LocalLog;
import wraith.config.WraithConfig;
import wraith.install.InstallRegistry;

class HttpGitHubApiTest {
    @TempDir Path temp;
    private HttpServer server;
    private URI base;
    private final AtomicBoolean rateLimited = new AtomicBoolean();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        base = URI.create("http://localhost:" + server.getAddress().getPort() + "/");
        server.createContext("/etag", exchange -> {
            if (rateLimited.get()) { exchange.sendResponseHeaders(403, -1); return; }
            if ("\"one\"".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) { exchange.sendResponseHeaders(304, -1); return; }
            exchange.getResponseHeaders().add("ETag", "\"one\""); respond(exchange, 200, "{\"ok\":true}");
        });
        server.createContext("/users/test/repos", exchange -> {
            if ("page=2".equals(exchange.getRequestURI().getQuery())) respond(exchange, 200, "[{\"name\":\"two\",\"description\":\"second\",\"language\":\"Java\",\"fork\":false,\"archived\":false}]");
            else { exchange.getResponseHeaders().add("Link", "<" + base + "users/test/repos?page=2>; rel=\"next\""); respond(exchange, 200, "[{\"name\":\"one\",\"description\":\"first\",\"language\":\"Java\",\"fork\":false,\"archived\":false}]"); }
        });
        server.createContext("/repos/test/one/releases/latest", exchange -> exchange.sendResponseHeaders(404, -1));
        server.createContext("/repos/test/one/contents/launcher.json", exchange -> exchange.sendResponseHeaders(404, -1));
        server.createContext("/repos/test/one/readme", exchange -> exchange.sendResponseHeaders(404, -1));
        server.createContext("/repos/test/two/releases/latest", exchange -> exchange.sendResponseHeaders(404, -1));
        server.createContext("/repos/test/two/contents/launcher.json", exchange -> exchange.sendResponseHeaders(404, -1));
        server.createContext("/repos/test/two/readme", exchange -> exchange.sendResponseHeaders(404, -1));
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void handlesPaginationEtagRateLimitAndCachedOfflineFallback() throws Exception {
        HttpGitHubApi api = new HttpGitHubApi(base, true); GitHubCache cache = new GitHubCache(temp.resolve("cache")); CachedGitHubClient client = new CachedGitHubClient(api, cache, () -> "");
        CachedGitHubClient.Result first = client.get(base.resolve("etag"), true); assertFalse(first.fromCache());
        CachedGitHubClient.Result second = client.get(base.resolve("etag"), true); assertTrue(second.fromCache());
        rateLimited.set(true); CachedGitHubClient.Result limited = client.get(base.resolve("etag"), true); assertTrue(limited.rateLimited()); assertEquals("{\"ok\":true}", limited.body());
        CachedGitHubClient offline = new CachedGitHubClient((uri, etag, token) -> { throw new IOException("offline"); }, cache, () -> "");
        assertTrue(offline.get(base.resolve("etag"), true).offline());
    }

    @Test void followsRepoPaginationAndTreatsReleaseAndManifest404AsNormal() {
        WraithConfig config = new WraithConfig(); config.owners = java.util.List.of("test");
        InstallRegistry installs = new InstallRegistry(temp.resolve("config"), new LocalLog(temp.resolve("logs")));
        GitHubLibraryProvider provider = new GitHubLibraryProvider(base, new HttpGitHubApi(base, true), new GitHubCache(temp.resolve("cache")), config,
                new LocalLog(temp.resolve("logs")), () -> "", installs);
        LibraryResult result = provider.load(true);
        assertEquals(2, result.programs().size());
        assertTrue(result.programs().stream().allMatch(item -> !item.hasRelease()));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); }
}
