package wraith.github;

public final class CachedResponse {
    public String etag = "";
    public String body = "";
    public String nextLink = "";
    public long fetchedAt;
    public CachedResponse() { }
    public CachedResponse(String etag, String body, String nextLink, long fetchedAt) { this.etag = etag; this.body = body; this.nextLink = nextLink; this.fetchedAt = fetchedAt; }
}
