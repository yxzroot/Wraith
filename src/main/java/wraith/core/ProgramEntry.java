package wraith.core;

import wraith.github.LauncherManifest;
import wraith.github.ReleaseInfo;

/** UI-facing immutable library entry. GitHub mapping is added in phase two. */
public record ProgramEntry(String owner, String repo, String displayName, String description, String language,
                           String version, ProgramStatus status, boolean hasRelease, String readme, ReleaseInfo release,
                           LauncherManifest manifest, String manifestError) {
    public ProgramEntry(String owner, String repo, String displayName, String description, String language,
                        String version, ProgramStatus status, boolean hasRelease, String readme) {
        this(owner, repo, displayName, description, language, version, status, hasRelease, readme, null, null, "");
    }
    public String initial() { return displayName.isBlank() ? "?" : displayName.substring(0, 1).toUpperCase(); }
    public String githubUrl() { return "https://github.com/" + owner + "/" + repo; }
}
