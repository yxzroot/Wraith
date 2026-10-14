package wraith.install;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import wraith.core.ProgramEntry;
import wraith.github.AssetSelector;
import wraith.github.GitHubAsset;
import wraith.github.LauncherManifest;
import wraith.platform.PlatformPaths;

/** Transactional installer for normal file/archive programs. mod-jar is delegated to Minecraft flow. */
public final class ProgramInstaller {
    private final PlatformPaths paths; private final InstallRegistry registry; private final SecureReleaseDownloader downloader; private final ArchiveExtractor extractor;
    public ProgramInstaller(PlatformPaths paths, InstallRegistry registry) { this(paths, registry, new SecureReleaseDownloader(), new ArchiveExtractor()); }
    public ProgramInstaller(PlatformPaths paths, InstallRegistry registry, SecureReleaseDownloader downloader, ArchiveExtractor extractor) { this.paths = paths; this.registry = registry; this.downloader = downloader; this.extractor = extractor; }

    public InstallResult install(ProgramEntry entry, Consumer<InstallProgress> progress, CancelToken cancel) {
        try {
            LauncherManifest manifest = entry.manifest(); if (manifest == null || manifest.install() == null) return InstallResult.failure("This repository has no valid launcher.json.");
            Optional<GitHubAsset> asset = AssetSelector.select(manifest, entry.release() == null ? java.util.List.of() : entry.release().assets(), paths.os());
            if (asset.isEmpty()) return InstallResult.failure("Not available for " + paths.os() + ".");
            Path root = paths.programsDirectory().resolve(entry.repo()); Files.createDirectories(root);
            Path source = Files.createTempFile(root, ".download-", ".tmp");
            SecureReleaseDownloader.Download download;
            try { download = downloader.download(asset.get().downloadUrl(), source, asset.get().digest(), progress, cancel); }
            catch (Exception error) { Files.deleteIfExists(source); return InstallResult.failure(error.getMessage()); }
            if ("mod-jar".equals(manifest.install().type())) return new InstallResult(true, "Choose a Minecraft profile.", source, download.hash(), true);
            String tag = entry.release().tag(); String safeTag = tag.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeTag.isBlank()) return InstallResult.failure("The release tag cannot be used as a local folder name.");
            Path staging = root.resolve("." + safeTag + ".new-" + System.nanoTime()).normalize(); Path target = root.resolve(safeTag).normalize();
            if (!staging.startsWith(root) || !target.startsWith(root)) return InstallResult.failure("Invalid release install path.");
            try {
                Files.createDirectories(staging); progress.accept(new InstallProgress(.88, "Verifying installation…"));
                if ("archive".equals(manifest.install().type())) extractor.extract(source, staging);
                else if ("file".equals(manifest.install().type())) { Path output = staging.resolve(asset.get().name()).normalize(); if (!output.startsWith(staging)) throw new IOException("Invalid asset name."); Files.copy(source, output); if (manifest.install().executable()) paths.applyExecutable(output); }
                else throw new IOException("Unsupported install type: " + manifest.install().type());
                Files.deleteIfExists(source); if (Files.exists(target)) deleteTree(target); move(staging, target);
                Optional<InstalledProgram> old = registry.find(entry.owner(), entry.repo()); registry.put(record(entry, target, download.hash()));
                if (old.isPresent() && !old.get().location.equals(target.toString())) { Path previous = Path.of(old.get().location); if (previous.startsWith(paths.programsDirectory())) deleteTree(previous); }
                progress.accept(new InstallProgress(1, "Installed.")); return InstallResult.success(target, download.hash());
            } catch (Exception error) { deleteTree(staging); Files.deleteIfExists(source); return InstallResult.failure("Could not install " + entry.displayName() + ": " + error.getMessage()); }
        } catch (Exception error) { return InstallResult.failure("Could not prepare the install: " + error.getMessage()); }
    }
    public InstallResult uninstall(ProgramEntry entry) {
        try { Optional<InstalledProgram> record = registry.find(entry.owner(), entry.repo()); if (record.isEmpty()) return InstallResult.failure("This program is not installed."); Path location = Path.of(record.get().location); if (!location.startsWith(paths.programsDirectory())) return InstallResult.failure("Refusing to remove a folder outside Wraith programs."); deleteTree(location); registry.remove(entry.owner(), entry.repo()); return InstallResult.success(location, ""); }
        catch (Exception error) { return InstallResult.failure("Could not uninstall: " + error.getMessage()); }
    }
    private InstalledProgram record(ProgramEntry entry, Path target, String hash) { InstalledProgram record = new InstalledProgram(); record.owner = entry.owner(); record.repo = entry.repo(); record.tag = entry.release().tag(); record.type = entry.manifest().install().type(); record.location = target.toString(); record.hash = hash; record.installedAt = Instant.now().toString(); return record; }
    static void deleteTree(Path root) { try { if (Files.exists(root)) try (java.util.stream.Stream<Path> files = Files.walk(root)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); } } catch (Exception ignored) { } }
    private static void move(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); } }
}
