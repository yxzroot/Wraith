package wraith.install;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class Hashing {
    private Hashing() { }
    public static String sha256(Path file) throws IOException {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (InputStream in = Files.newInputStream(file)) { byte[] buffer = new byte[16 * 1024]; for (int count; (count = in.read(buffer)) >= 0;) digest.update(buffer, 0, count); } return hex(digest.digest()); }
        catch (Exception error) { throw new IOException("Could not calculate SHA-256", error); }
    }
    public static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte value : bytes) out.append(String.format("%02x", value)); return out.toString(); }
}
