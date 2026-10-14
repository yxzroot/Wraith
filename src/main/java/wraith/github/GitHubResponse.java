package wraith.github;

import java.time.Instant;
import java.util.Optional;

/** Raw read-only API response; implementation details stay behind GitHubApi. */
public record GitHubResponse(int status, String body, String etag, String linkHeader, int rateRemaining, Instant rateReset) {
    public boolean successful() { return status >= 200 && status < 300; }
    public boolean notModified() { return status == 304; }
    public Optional<String> nextLink() {
        if (linkHeader == null) return Optional.empty();
        for (String section : linkHeader.split(",")) if (section.contains("rel=\"next\"")) {
            int start = section.indexOf('<'), end = section.indexOf('>');
            if (start >= 0 && end > start) return Optional.of(section.substring(start + 1, end));
        }
        return Optional.empty();
    }
}
