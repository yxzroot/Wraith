package wraith.platform;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** All OS-specific filesystem and desktop rules are centralized here. */
public final class PlatformPaths {
    private final Os os;
    private final Path home;
    private final Map<String, String> environment;

    public PlatformPaths(Os os, Path home, Map<String, String> environment) {
        this.os = os;
        this.home = home.toAbsolutePath().normalize();
        this.environment = Map.copyOf(environment);
    }

    public static PlatformPaths system() { return new PlatformPaths(Os.current(), Path.of(System.getProperty("user.home")), System.getenv()); }
    public Os os() { return os; }
    public Path home() { return home; }

    public Path configDirectory() {
        return switch (os) {
            case WINDOWS -> environmentPath("APPDATA", home.resolve("AppData").resolve("Roaming")).resolve("Wraith");
            case LINUX -> environmentPath("XDG_CONFIG_HOME", home.resolve(".config")).resolve("Wraith");
            case MAC -> home.resolve("Library").resolve("Application Support").resolve("Wraith");
        };
    }

    public Path logDirectory() { return configDirectory(); }

    public Path cacheDirectory() {
        return switch (os) {
            case WINDOWS -> environmentPath("LOCALAPPDATA", home.resolve("AppData").resolve("Local")).resolve("Wraith").resolve("cache");
            case LINUX -> environmentPath("XDG_CACHE_HOME", home.resolve(".cache")).resolve("Wraith");
            case MAC -> home.resolve("Library").resolve("Caches").resolve("Wraith");
        };
    }

    public Path programsDirectory() {
        return switch (os) {
            case WINDOWS -> environmentPath("LOCALAPPDATA", home.resolve("AppData").resolve("Local")).resolve("Wraith").resolve("programs");
            case LINUX -> environmentPath("XDG_DATA_HOME", home.resolve(".local").resolve("share")).resolve("Wraith").resolve("programs");
            case MAC -> home.resolve("Library").resolve("Application Support").resolve("Wraith").resolve("programs");
        };
    }

    public boolean isTilingSession() {
        if (os != Os.LINUX) return false;
        String desktop = environment.getOrDefault("XDG_CURRENT_DESKTOP", "").toLowerCase();
        String session = environment.getOrDefault("XDG_SESSION_TYPE", "").toLowerCase();
        return environment.containsKey("HYPRLAND_INSTANCE_SIGNATURE") || desktop.contains("hypr") || desktop.contains("sway")
                || desktop.contains("i3") || session.contains("wayland") && (desktop.contains("wlroots") || desktop.contains("river"));
    }

    public String windowClass() { return "wraith"; }
    public String platformKey() { return os == Os.WINDOWS ? "windows" : os == Os.LINUX ? "linux" : "mac"; }

    public List<MinecraftLocation> defaultMinecraftLocations() {
        List<MinecraftLocation> locations = new ArrayList<>();
        switch (os) {
            case WINDOWS -> {
                Path appData = environmentPath("APPDATA", home.resolve("AppData").resolve("Roaming"));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.VANILLA, appData.resolve(".minecraft")));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.LUNAR, home.resolve(".lunarclient")));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.PRISM, appData.resolve("PrismLauncher").resolve("instances")));
            }
            case LINUX -> {
                locations.add(new MinecraftLocation(MinecraftLocation.Type.VANILLA, home.resolve(".minecraft")));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.FLATPAK_MINECRAFT, home.resolve(".var").resolve("app").resolve("com.mojang.Minecraft").resolve(".minecraft")));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.LUNAR, home.resolve(".lunarclient")));
                Path data = environmentPath("XDG_DATA_HOME", home.resolve(".local").resolve("share"));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.PRISM, data.resolve("PrismLauncher").resolve("instances")));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.FLATPAK_PRISM, home.resolve(".var").resolve("app").resolve("org.prismlauncher.PrismLauncher").resolve("data").resolve("PrismLauncher").resolve("instances")));
                locations.add(new MinecraftLocation(MinecraftLocation.Type.MULTIMC, data.resolve("multimc").resolve("instances")));
            }
            case MAC -> locations.add(new MinecraftLocation(MinecraftLocation.Type.VANILLA, home.resolve("Library").resolve("Application Support").resolve("minecraft")));
        }
        return List.copyOf(locations);
    }

    public boolean applyExecutable(Path file) {
        if (os != Os.LINUX) return true;
        try {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public List<List<String>> minecraftLauncherCommands(String launcherType) {
        if (os == Os.WINDOWS) {
            Path local = environmentPath("LOCALAPPDATA", home.resolve("AppData").resolve("Local"));
            Path launcher = local.resolve("Programs").resolve("Minecraft Launcher").resolve("MinecraftLauncher.exe");
            return Files.isRegularFile(launcher) ? List.of(List.of(launcher.toString())) : List.of();
        }
        if (os == Os.LINUX) return "PRISM".equals(launcherType)
                ? List.of(List.of("prismlauncher"), List.of("flatpak", "run", "org.prismlauncher.PrismLauncher"))
                : List.of(List.of("minecraft-launcher"), List.of("flatpak", "run", "com.mojang.Minecraft"));
        return List.of();
    }

    private Path environmentPath(String name, Path fallback) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? fallback : Path.of(value);
    }
}
