package wraith.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import wraith.detect.MinecraftInstall;
import wraith.platform.PlatformPaths;

public final class MinecraftLauncherStarter {
    private final PlatformPaths paths;
    public MinecraftLauncherStarter(PlatformPaths paths) { this.paths = paths; }
    public Optional<List<String>> command(MinecraftInstall install) { return paths.minecraftLauncherCommands(install.launcherType().name()).stream().filter(this::exists).findFirst(); }
    public void start(MinecraftInstall install) throws IOException { new ProcessBuilder(command(install).orElseThrow(() -> new IOException("Minecraft Launcher was not found."))).start(); }
    private boolean exists(List<String> command) { String binary = command.get(0); if (binary.contains(java.io.File.separator) || binary.endsWith(".exe")) return Files.isExecutable(Path.of(binary)); for (String folder : System.getenv().getOrDefault("PATH", "").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) if (Files.isExecutable(Path.of(folder, binary))) return true; return false; }
}
