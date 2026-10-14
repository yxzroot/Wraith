package wraith.detect;
import java.nio.file.Path;
public record MinecraftInstall(LauncherType launcherType, String displayName, String gameVersion, LoaderType loader, Path modsFolder, Path root) { public boolean isForge189() { return loader == LoaderType.FORGE && gameVersion.startsWith("1.8.9"); } }
