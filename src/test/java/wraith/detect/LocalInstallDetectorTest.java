package wraith.detect;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wraith.config.LocalLog;
import wraith.platform.Os;
import wraith.platform.PlatformPaths;
class LocalInstallDetectorTest {
    @TempDir Path temp;
    @Test void findsWindowsForge() throws Exception { Path app = temp.resolve("appdata"); Files.createDirectories(app.resolve(".minecraft/versions/1.8.9-forge11.15.1")); var paths = new PlatformPaths(Os.WINDOWS, temp.resolve("home"), Map.of("APPDATA", app.toString())); assertTrue(new LocalInstallDetector(paths, new LocalLog(temp.resolve("logs"))).scan().stream().anyMatch(MinecraftInstall::isForge189)); }
    @Test void findsLinuxPrismForge() throws Exception { Path data = temp.resolve("data"); Path instance = data.resolve("PrismLauncher/instances/Test"); Files.createDirectories(instance.resolve(".minecraft")); Files.writeString(instance.resolve("mmc-pack.json"), "{\"components\":[{\"uid\":\"net.minecraft\",\"version\":\"1.8.9\"},{\"uid\":\"net.minecraftforge\",\"version\":\"x\"}]}"); var paths = new PlatformPaths(Os.LINUX, temp.resolve("home"), Map.of("XDG_DATA_HOME", data.toString())); assertTrue(new LocalInstallDetector(paths, new LocalLog(temp.resolve("logs"))).scan().stream().anyMatch(MinecraftInstall::isForge189)); }
}
