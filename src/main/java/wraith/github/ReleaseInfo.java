package wraith.github;

import java.time.Instant;
import java.util.List;

public record ReleaseInfo(String tag, String name, String body, Instant publishedAt, List<GitHubAsset> assets) {
    public static ReleaseInfo none() { return new ReleaseInfo("", "", "", null, List.of()); }
    public boolean available() { return !tag.isBlank(); }
}
