package wraith.detect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import wraith.config.LocalLog;
import wraith.platform.MinecraftLocation;
import wraith.platform.PlatformPaths;

/** Fault-tolerant local detection for the Minecraft folders supported by Wraith. */
public final class LocalInstallDetector implements InstallDetector {
    private static final Pattern VERSION = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");
    private final PlatformPaths paths; private final LocalLog log;
    public LocalInstallDetector(PlatformPaths paths, LocalLog log) { this.paths = paths; this.log = log; }
    @Override public List<MinecraftInstall> scan() {
        Map<Path, MinecraftInstall> out = new LinkedHashMap<>();
        for (MinecraftLocation location : paths.defaultMinecraftLocations()) try {
            switch (location.type()) {
                case VANILLA, FLATPAK_MINECRAFT -> vanilla(location.path(), out);
                case LUNAR -> lunar(location.path(), out);
                case PRISM, FLATPAK_PRISM -> instances(location.path(), LauncherType.PRISM, out);
                case MULTIMC -> instances(location.path(), LauncherType.MULTIMC, out);
            }
        } catch (Exception error) { log.error("Could not scan " + location.path(), error); }
        return out.values().stream().sorted(Comparator.comparing(MinecraftInstall::displayName)).toList();
    }
    public MinecraftInstall custom(Path selected) {
        Path root = selected.toAbsolutePath().normalize(); Path game = Files.isDirectory(root.resolve(".minecraft")) ? root.resolve(".minecraft") : root;
        String forge = directories(game.resolve("versions")).stream().map(path -> path.getFileName().toString()).filter(this::forge).map(this::version).findFirst().orElse("Unknown");
        return new MinecraftInstall(LauncherType.CUSTOM, "Custom: " + root.getFileName(), forge, forge.equals("Unknown") ? LoaderType.UNKNOWN : LoaderType.FORGE, game.resolve("mods"), root);
    }
    private void vanilla(Path root, Map<Path, MinecraftInstall> out) {
        if (!Files.isDirectory(root)) return; List<Path> versions = directories(root.resolve("versions")); boolean found = false;
        for (Path folder : versions) if (forge(folder.getFileName().toString())) { add(out, new MinecraftInstall(LauncherType.VANILLA, "Minecraft " + version(folder.getFileName().toString()) + " Forge", version(folder.getFileName().toString()), LoaderType.FORGE, root.resolve("mods"), root)); found = true; }
        if (!found) add(out, new MinecraftInstall(LauncherType.VANILLA, "Minecraft " + profileVersion(root), profileVersion(root), LoaderType.VANILLA, root.resolve("mods"), root));
    }
    private void lunar(Path lunar, Map<Path, MinecraftInstall> out) {
        Path profiles = lunar.resolve("profiles"); if (!Files.isDirectory(profiles)) return;
        for (Path profile : directories(profiles)) { Path mods = profile.resolve("mods"); for (Path loader : directories(mods)) { String name = loader.getFileName().toString(); add(out, new MinecraftInstall(LauncherType.LUNAR, "Lunar Client " + version(name), version(name), forge(name) ? LoaderType.FORGE : LoaderType.UNKNOWN, loader, profile)); } }
    }
    private void instances(Path root, LauncherType type, Map<Path, MinecraftInstall> out) {
        for (Path instance : directories(root)) {
            Info info = packInfo(instance); Path game = Files.isDirectory(instance.resolve(".minecraft")) ? instance.resolve(".minecraft") : instance.resolve("minecraft");
            String name = instanceName(instance); add(out, new MinecraftInstall(type, name, info.version, info.forge ? LoaderType.FORGE : LoaderType.UNKNOWN, game.resolve("mods"), instance));
        }
    }
    private Info packInfo(Path instance) {
        Path pack = instance.resolve("mmc-pack.json"); if (!Files.isRegularFile(pack)) return new Info("Unknown", false);
        try { JsonObject root = JsonParser.parseString(Files.readString(pack, StandardCharsets.UTF_8)).getAsJsonObject(); String version = "Unknown"; boolean forge = false;
            for (JsonElement item : root.getAsJsonArray("components")) if (item.isJsonObject()) { JsonObject c = item.getAsJsonObject(); String uid = text(c, "uid"); if (uid.equals("net.minecraft")) version = text(c, "version"); if (uid.contains("forge")) forge = true; } return new Info(version, forge);
        } catch (Exception error) { log.error("Broken " + pack, error); return new Info("Unknown", false); }
    }
    private String profileVersion(Path root) {
        Path profile = root.resolve("launcher_profiles.json"); if (!Files.isRegularFile(profile)) return "Unknown";
        try { JsonObject data = JsonParser.parseString(Files.readString(profile, StandardCharsets.UTF_8)).getAsJsonObject(); String selected = text(data, "selectedProfile"); JsonObject profiles = data.has("profiles") ? data.getAsJsonObject("profiles") : null; return profiles != null && profiles.has(selected) ? version(text(profiles.getAsJsonObject(selected), "lastVersionId")) : "Unknown"; }
        catch (Exception error) { log.error("Broken " + profile, error); return "Unknown"; }
    }
    private String instanceName(Path instance) { Path cfg = instance.resolve("instance.cfg"); try (Stream<String> lines = Files.exists(cfg) ? Files.lines(cfg) : Stream.empty()) { return lines.filter(line -> line.startsWith("name=")).map(line -> line.substring(5)).findFirst().orElse(instance.getFileName().toString()); } catch (Exception ignored) { return instance.getFileName().toString(); } }
    private void add(Map<Path, MinecraftInstall> out, MinecraftInstall value) { Path key = value.modsFolder().toAbsolutePath().normalize(); if (!out.containsKey(key) || value.isForge189()) out.put(key, value); }
    private boolean forge(String name) { return name.toLowerCase().contains("forge"); }
    private String version(String value) { Matcher match = VERSION.matcher(value); return match.find() ? match.group(1) : "Unknown"; }
    private static String text(JsonObject value, String name) { return value.has(name) && value.get(name).isJsonPrimitive() ? value.get(name).getAsString() : ""; }
    private static List<Path> directories(Path path) { try (Stream<Path> files = Files.list(path)) { return files.filter(Files::isDirectory).toList(); } catch (Exception ignored) { return List.of(); } }
    private record Info(String version, boolean forge) { }
}
