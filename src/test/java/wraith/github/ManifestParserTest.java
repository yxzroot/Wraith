package wraith.github;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import wraith.platform.Os;
class ManifestParserTest {
    @Test void parsesManifestAndSelectsPerOsAssets() { ManifestParser.Result result = ManifestParser.parse("{\"displayName\":\"Phantom\",\"install\":{\"type\":\"file\",\"asset\":{\"windows\":\"*.exe\",\"linux\":\"*.AppImage\"},\"executable\":true},\"launch\":{\"instructions\":\"Run it\",\"run\":{\"linux\":\"bin/app\",\"args\":[\"--safe\"]}}}"); assertTrue(result.valid()); List<GitHubAsset> assets = List.of(new GitHubAsset("phantom.AppImage", URI.create("https://github.com/a"), 2, "")); assertEquals("phantom.AppImage", AssetSelector.select(result.manifest(), assets, Os.LINUX).orElseThrow().name()); assertTrue(result.manifest().install().executable()); }
    @Test void containsBrokenManifest() { assertFalse(ManifestParser.parse("{").valid()); }
}
