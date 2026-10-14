package wraith.github;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.time.Instant;

/** Small explicit rate-limit read used only from the Settings button. */
public final class RateLimitChecker {
    private final GitHubApi api; private final URI endpoint; private final java.util.function.Supplier<String> token;
    public RateLimitChecker(GitHubApi api, URI base, java.util.function.Supplier<String> token) { this.api = api; endpoint = base.resolve("rate_limit"); this.token = token; }
    public String check() throws Exception {
        GitHubResponse response = api.get(endpoint, "", token.get()); if (!response.successful()) return "GitHub returned HTTP " + response.status() + ".";
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject(); JsonObject core = root.has("resources") ? root.getAsJsonObject("resources").getAsJsonObject("core") : root;
        long remaining = core.get("remaining").getAsLong(); long reset = core.get("reset").getAsLong(); return remaining + " requests remaining · resets " + Instant.ofEpochSecond(reset);
    }
}
