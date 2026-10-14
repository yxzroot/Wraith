package wraith.install;
import static org.junit.jupiter.api.Assertions.*;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import wraith.detect.LauncherType;
import wraith.detect.LoaderType;
import wraith.detect.MinecraftInstall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class InstallSecurityTest {
    @TempDir Path temp;
    @Test void allowsOnlyApprovedGithubHosts() { assertTrue(GitHubHostAllowlist.allows(java.net.URI.create("https://github.com/a/b/releases/download/x"))); assertTrue(GitHubHostAllowlist.allows(java.net.URI.create("https://release-assets.githubusercontent.com/x"))); assertFalse(GitHubHostAllowlist.allows(java.net.URI.create("http://github.com/x"))); assertFalse(GitHubHostAllowlist.allows(java.net.URI.create("https://example.com/x"))); }
    @Test void blocksZipSlip() throws Exception { Path archive = temp.resolve("bad.zip"); try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) { zip.putNextEntry(new ZipEntry("../outside.txt")); zip.write(1); zip.closeEntry(); } assertThrows(Exception.class, () -> new ArchiveExtractor().extract(archive, temp.resolve("target"))); assertFalse(Files.exists(temp.resolve("outside.txt"))); }
    @Test void restoresOldModWhenFinalHashFails() throws Exception { Path mods = temp.resolve("mods"); Files.createDirectories(mods); Path old = jar(mods.resolve("Phantom.jar"), "old"); String oldHash = Hashing.sha256(old); Path source = jar(temp.resolve("new.jar"), "new"); AtomicInteger calls = new AtomicInteger(); MinecraftModInstaller installer = new MinecraftModInstaller(path -> calls.incrementAndGet() == 3 ? "wrong" : Hashing.sha256(path)); InstallResult result = installer.install(source, new MinecraftInstall(LauncherType.VANILLA, "Test", "1.8.9", LoaderType.FORGE, mods, temp)); assertFalse(result.success()); assertEquals(oldHash, Hashing.sha256(mods.resolve("Phantom.jar"))); }
    private static Path jar(Path path, String body) throws Exception { try (OutputStream output = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(output)) { zip.putNextEntry(new ZipEntry("data.txt")); zip.write(body.getBytes()); zip.closeEntry(); } return path; }
}
