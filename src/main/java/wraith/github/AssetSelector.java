package wraith.github;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import wraith.platform.Os;

public final class AssetSelector {
    private AssetSelector() { }
    public static Optional<GitHubAsset> select(LauncherManifest manifest, List<GitHubAsset> assets, Os os) {
        if (manifest == null || manifest.install() == null) return Optional.empty();
        Object raw = manifest.install().asset(); String glob;
        if (raw instanceof String value) glob = value;
        else if (raw instanceof Map<?, ?> map) { Object value = map.get(os == Os.WINDOWS ? "windows" : os == Os.LINUX ? "linux" : "mac"); glob = value instanceof String ? (String) value : ""; }
        else glob = "";
        if (glob.isBlank()) return Optional.empty();
        Pattern matcher = Pattern.compile(globToRegex(glob), Pattern.CASE_INSENSITIVE);
        return assets.stream().filter(asset -> matcher.matcher(asset.name()).matches()).findFirst();
    }
    private static String globToRegex(String glob) { return "^" + Pattern.quote(glob).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "$"; }
}
