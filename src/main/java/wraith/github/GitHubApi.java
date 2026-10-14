package wraith.github;

import java.io.IOException;
import java.net.URI;

/** API seam used by tests; production accepts only GitHub API endpoints. */
@FunctionalInterface
public interface GitHubApi {
    GitHubResponse get(URI uri, String etag, String token) throws IOException, InterruptedException;
}
