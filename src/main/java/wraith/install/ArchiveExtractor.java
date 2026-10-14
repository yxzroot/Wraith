package wraith.install;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** ZIP/TAR.GZ extraction rejects path traversal and symlink entries. */
public final class ArchiveExtractor {
    public void extract(Path archive, Path target) throws IOException {
        String name = archive.getFileName().toString().toLowerCase(); Files.createDirectories(target);
        if (name.endsWith(".zip")) zip(archive, target); else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) tarGz(archive, target); else throw new IOException("Only .zip and .tar.gz releases can be extracted.");
    }
    private void zip(Path archive, Path target) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) { for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) { Path out = safe(target, entry.getName()); if (entry.isDirectory()) Files.createDirectories(out); else { Files.createDirectories(out.getParent()); Files.copy(zip, out); } } }
    }
    private void tarGz(Path archive, Path target) throws IOException {
        try (InputStream input = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive)))) { byte[] header = new byte[512]; while (readFully(input, header) == 512) { if (empty(header)) break; String name = value(header, 0, 100); String prefix = value(header, 345, 155); if (!prefix.isBlank()) name = prefix + "/" + name; long size = octal(header, 124, 12); byte type = header[156]; Path out = safe(target, name); if (type == '2' || type == '1') throw new IOException("Archive symlinks are not allowed."); if (type == '5') Files.createDirectories(out); else { Files.createDirectories(out.getParent()); try (java.io.OutputStream output = Files.newOutputStream(out)) { copyN(input, output, size); } } long padding = (512 - size % 512) % 512; while (padding > 0) { long skipped = input.skip(padding); if (skipped <= 0) throw new IOException("Truncated tar archive."); padding -= skipped; } } }
    }
    private static Path safe(Path root, String entry) throws IOException { Path output = root.resolve(entry).normalize(); if (!output.startsWith(root) || entry.startsWith("/") || entry.startsWith("\\")) throw new IOException("Archive entry escapes the install folder."); return output; }
    private static int readFully(InputStream input, byte[] buffer) throws IOException { int total = 0; for (int read; total < buffer.length && (read = input.read(buffer, total, buffer.length - total)) >= 0;) total += read; return total; }
    private static boolean empty(byte[] data) { for (byte item : data) if (item != 0) return false; return true; }
    private static String value(byte[] data, int start, int length) { int end = start; while (end < start + length && data[end] != 0) end++; return new String(data, start, end - start, java.nio.charset.StandardCharsets.US_ASCII).trim(); }
    private static long octal(byte[] data, int start, int length) { String value = value(data, start, length); try { return value.isBlank() ? 0 : Long.parseLong(value.replaceAll("[^0-7]", ""), 8); } catch (Exception ignored) { return 0; } }
    private static void copyN(InputStream input, java.io.OutputStream output, long remaining) throws IOException { byte[] buffer = new byte[16 * 1024]; while (remaining > 0) { int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining)); if (read < 0) throw new IOException("Truncated tar archive."); output.write(buffer, 0, read); remaining -= read; } }
}
