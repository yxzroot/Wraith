package wraith.install;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import wraith.detect.MinecraftInstall;

/** Safe Phantom mod installation path used by mod-jar manifests. */
public final class MinecraftModInstaller {
    @FunctionalInterface public interface HashProvider { String hash(Path path) throws IOException; }
    private final HashProvider hashes;
    public MinecraftModInstaller() { this(Hashing::sha256); }
    public MinecraftModInstaller(HashProvider hashes) { this.hashes = hashes; }
    public InstallResult install(Path source, MinecraftInstall install) {
        Path mods = install.modsFolder().toAbsolutePath().normalize(); Path temp = null; Path backup = null; boolean movedOld = false;
        try {
            if (!validJar(source)) return InstallResult.failure("The downloaded release is not a valid JAR.");
            String expected = hashes.hash(source); Files.createDirectories(mods); if (!Files.isWritable(mods)) return InstallResult.failure(permission(mods));
            temp = Files.createTempFile(mods, ".phantom-", ".tmp"); Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING); if (!expected.equals(hashes.hash(temp))) return InstallResult.failure("The temporary mod copy could not be verified.");
            List<Path> old = phantomJars(mods); if (!old.isEmpty()) { Path root = mods.resolve(".phantom_backup"); deleteTree(root); backup = root.resolve(Long.toString(System.currentTimeMillis())); Files.createDirectories(backup); for (Path file : old) move(file, backup.resolve(file.getFileName())); movedOld = true; }
            Path target = mods.resolve("Phantom.jar"); move(temp, target); temp = null;
            if (!expected.equals(hashes.hash(target))) { Files.deleteIfExists(target); restore(backup, mods); return InstallResult.failure("The mod copy did not verify; the prior version was restored."); }
            return InstallResult.success(mods, expected);
        } catch (Exception error) { if (movedOld) restore(backup, mods); return InstallResult.failure(permission(mods)); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
    public InstallResult uninstall(MinecraftInstall install) { try { Files.deleteIfExists(install.modsFolder().resolve("Phantom.jar")); return InstallResult.success(install.modsFolder(), ""); } catch (IOException error) { return InstallResult.failure(permission(install.modsFolder())); } }
    private static List<Path> phantomJars(Path mods) throws IOException { try (java.util.stream.Stream<Path> files = Files.list(mods)) { return files.filter(Files::isRegularFile).filter(path -> { String n = path.getFileName().toString().toLowerCase(); return n.startsWith("phantom") && n.endsWith(".jar"); }).toList(); } }
    private static boolean validJar(Path file) { try (java.util.zip.ZipFile ignored = new java.util.zip.ZipFile(file.toFile())) { return true; } catch (Exception ignored) { return false; } }
    private static void restore(Path backup, Path mods) { if (backup == null || !Files.isDirectory(backup)) return; try (java.util.stream.Stream<Path> paths = Files.list(backup)) { paths.filter(Files::isRegularFile).forEach(path -> { try { move(path, mods.resolve(path.getFileName())); } catch (IOException ignored) { } }); } catch (IOException ignored) { } }
    private static void deleteTree(Path root) { try { if (Files.exists(root)) try (java.util.stream.Stream<Path> paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); } } catch (IOException ignored) { } }
    private static void move(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); } }
    private static String permission(Path mods) { return "The mods folder is not writable: " + mods + (mods.toString().contains(".var" + java.io.File.separator + "app") ? ". This appears to be a Flatpak sandbox path." : "."); }
}
