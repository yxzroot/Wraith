package wraith.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import wraith.github.LauncherManifest;
import wraith.platform.PlatformPaths;

/** Starts only a manifest-declared local executable after UI confirmation. */
public final class ProgramRunner {
    private final PlatformPaths paths;
    public ProgramRunner(PlatformPaths paths) { this.paths = paths; }
    public void run(Path installFolder, LauncherManifest manifest) throws IOException {
        if (manifest == null || manifest.launch() == null || manifest.launch().run() == null) throw new IOException("This program has no runnable launch command.");
        Object raw = switch (paths.platformKey()) { case "windows" -> manifest.launch().run().windows(); case "linux" -> manifest.launch().run().linux(); default -> null; };
        if (!(raw instanceof String)) throw new IOException("This program is not available for " + paths.os() + ".");
        Path executable = installFolder.resolve((String) raw).normalize(); if (!executable.startsWith(installFolder) || !Files.isRegularFile(executable)) throw new IOException("The launch file is missing or outside the install folder.");
        List<String> command = new ArrayList<>(); command.add(executable.toString()); command.addAll(manifest.launch().run().args()); new ProcessBuilder(command).directory(installFolder.toFile()).start();
    }
}
