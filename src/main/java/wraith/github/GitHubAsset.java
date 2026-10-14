package wraith.github;

import java.net.URI;

public record GitHubAsset(String name, URI downloadUrl, long size, String digest) { }
