package wraith.install;

import java.net.URI;
import java.util.Set;

public final class GitHubHostAllowlist {
    private static final Set<String> HOSTS = Set.of("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com");
    private GitHubHostAllowlist() { }
    public static boolean allows(URI uri) { return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && HOSTS.contains(uri.getHost() == null ? "" : uri.getHost().toLowerCase()); }
}
