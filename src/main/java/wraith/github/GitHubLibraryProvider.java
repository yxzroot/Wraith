package wraith.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import wraith.config.LocalLog;
import wraith.config.WraithConfig;
import wraith.core.ProgramEntry;
import wraith.core.ProgramStatus;
import wraith.install.InstallRegistry;
import wraith.install.InstalledProgram;

/** Uses only GitHub's public REST API; detail work is bounded to four parallel requests. */
public final class GitHubLibraryProvider implements LibraryProvider {
    private final URI apiBase;
    private final CachedGitHubClient client;
    private final WraithConfig config;
    private final LocalLog log;
    private final InstallRegistry installs;

    public GitHubLibraryProvider(URI apiBase, GitHubApi api, GitHubCache cache, WraithConfig config, LocalLog log, java.util.function.Supplier<String> token, InstallRegistry installs) {
        this.apiBase = apiBase; this.client = new CachedGitHubClient(api, cache, token); this.config = config; this.log = log; this.installs = installs;
    }

    @Override public LibraryResult load(boolean forceRefresh) {
        List<Repository> repositories = new ArrayList<>(); boolean offline = false; String notice = "";
        for (String owner : config.owners == null ? List.<String>of() : config.owners) {
            if (owner == null || owner.isBlank()) continue;
            Page page = repositories(owner, forceRefresh); repositories.addAll(page.repositories); offline |= page.offline; if (!page.notice.isBlank()) notice = page.notice;
        }
        ExecutorService queue = Executors.newFixedThreadPool(4, runnable -> { Thread thread = new Thread(runnable, "wraith-github-detail"); thread.setDaemon(true); return thread; });
        try {
            List<Future<ProgramEntry>> tasks = new ArrayList<>();
            for (Repository repo : repositories) tasks.add(queue.submit(() -> detail(repo, forceRefresh)));
            List<ProgramEntry> entries = new ArrayList<>();
            for (Future<ProgramEntry> task : tasks) try { ProgramEntry item = task.get(); if (item != null) entries.add(item); } catch (Exception error) { log.error("Could not read a GitHub repository", error instanceof Exception ? (Exception) error : new Exception(error)); }
            return new LibraryResult(entries.stream().sorted(Comparator.comparing(ProgramEntry::displayName, String.CASE_INSENSITIVE_ORDER)).toList(), offline, notice);
        } finally { queue.shutdownNow(); }
    }

    private Page repositories(String owner, boolean force) {
        List<Repository> entries = new ArrayList<>(); URI next = endpoint("users/" + encode(owner) + "/repos?per_page=100&sort=pushed"); boolean offline = false; String notice = "";
        while (next != null) {
            CachedGitHubClient.Result response = client.get(next, force); offline |= response.offline(); if (!response.notice().isBlank()) notice = response.notice();
            if (response.status() == 404 || response.body().isBlank()) break;
            try {
                JsonArray list = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement element : list) if (element.isJsonObject()) {
                    JsonObject repo = element.getAsJsonObject(); boolean fork = bool(repo, "fork"), archived = bool(repo, "archived");
                    if ((!config.includeForks && fork) || (!config.includeArchived && archived)) continue;
                    entries.add(new Repository(owner, string(repo, "name"), string(repo, "description"), string(repo, "language")));
                }
            } catch (Exception error) { log.error("Could not parse GitHub repository list", error instanceof Exception ? (Exception) error : new Exception(error)); }
            next = response.nextLink().isBlank() ? null : URI.create(response.nextLink());
        }
        return new Page(entries, offline, notice);
    }

    private ProgramEntry detail(Repository repo, boolean force) {
        CachedGitHubClient.Result releaseResponse = client.get(endpoint("repos/" + encode(repo.owner) + "/" + encode(repo.name) + "/releases/latest"), force);
        ReleaseInfo release = releaseResponse.status() == 404 ? ReleaseInfo.none() : parseRelease(releaseResponse.body());
        CachedGitHubClient.Result manifestResponse = client.get(endpoint("repos/" + encode(repo.owner) + "/" + encode(repo.name) + "/contents/launcher.json"), force);
        ManifestParser.Result manifest = manifestResponse.status() == 404 ? new ManifestParser.Result(null, "") : ManifestParser.parse(content(manifestResponse.body()));
        CachedGitHubClient.Result readmeResponse = client.get(endpoint("repos/" + encode(repo.owner) + "/" + encode(repo.name) + "/readme"), force);
        String readme = readmeResponse.status() == 404 ? "" : content(readmeResponse.body());
        boolean hasRelease = release.available();
        if (!config.showWithoutReleases && !hasRelease) return null;
        String display = manifest.valid() && !manifest.manifest().displayName().isBlank() ? manifest.manifest().displayName() : repo.name;
        String description = manifest.valid() && !manifest.manifest().description().isBlank() ? manifest.manifest().description() : repo.description;
        java.util.Optional<InstalledProgram> installed = installs.find(repo.owner, repo.name);
        ProgramStatus status = hasRelease ? (installed.isEmpty() ? ProgramStatus.INSTALL : wraith.install.VersionComparator.updateAvailable(installed.get().tag, release.tag()) ? ProgramStatus.UPDATE : ProgramStatus.INSTALLED) : ProgramStatus.SOURCE_ONLY;
        return new ProgramEntry(repo.owner, repo.name, display, description == null ? "" : description, repo.language.isBlank() ? "Unknown" : repo.language,
                hasRelease ? release.tag() : "—", status, hasRelease, readme, release, manifest.manifest(), manifest.error());
    }

    private ReleaseInfo parseRelease(String body) {
        if (body == null || body.isBlank()) return ReleaseInfo.none();
        try {
            JsonObject release = JsonParser.parseString(body).getAsJsonObject(); List<GitHubAsset> assets = new ArrayList<>();
            if (release.has("assets") && release.get("assets").isJsonArray()) for (JsonElement item : release.getAsJsonArray("assets")) if (item.isJsonObject()) {
                JsonObject asset = item.getAsJsonObject(); assets.add(new GitHubAsset(string(asset, "name"), URI.create(string(asset, "browser_download_url")), number(asset, "size"), string(asset, "digest")));
            }
            Instant date = null; try { if (!string(release, "published_at").isBlank()) date = Instant.parse(string(release, "published_at")); } catch (Exception ignored) { }
            return new ReleaseInfo(string(release, "tag_name"), string(release, "name"), string(release, "body"), date, List.copyOf(assets));
        } catch (Exception ignored) { return ReleaseInfo.none(); }
    }

    private URI endpoint(String path) { return apiBase.resolve(path); }
    private static String content(String response) {
        try { JsonObject object = JsonParser.parseString(response).getAsJsonObject(); String encoded = string(object, "content").replaceAll("\\s", ""); return encoded.isBlank() ? "" : new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8); }
        catch (Exception ignored) { return ""; }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String string(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonPrimitive() && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; }
    private static boolean bool(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean(); }
    private static long number(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : 0L; }
    private record Repository(String owner, String name, String description, String language) { }
    private record Page(List<Repository> repositories, boolean offline, String notice) { }
}
