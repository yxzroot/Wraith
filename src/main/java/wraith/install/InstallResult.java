package wraith.install;

import java.nio.file.Path;
public record InstallResult(boolean success, String message, Path location, String hash, boolean needsMinecraft) {
    public static InstallResult success(Path path, String hash) { return new InstallResult(true, "Installed.", path, hash, false); }
    public static InstallResult failure(String message) { return new InstallResult(false, message, null, "", false); }
    public static InstallResult minecraft() { return new InstallResult(true, "Choose a Minecraft profile.", null, "", true); }
}
