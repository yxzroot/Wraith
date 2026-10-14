package wraith.install;

public final class VersionComparator {
    private VersionComparator() { }
    public static boolean updateAvailable(String installed, String latest) {
        if (installed == null || installed.isBlank()) return true;
        if (installed.equals(latest)) return false;
        int[] left = semantic(installed), right = semantic(latest);
        if (left == null || right == null) return true;
        for (int i = 0; i < 3; i++) if (left[i] != right[i]) return right[i] > left[i];
        return !installed.equals(latest);
    }
    private static int[] semantic(String raw) {
        String value = raw.startsWith("v") || raw.startsWith("V") ? raw.substring(1) : raw;
        if (!value.matches("\\d+\\.\\d+\\.\\d+(?:[-+].*)?")) return null;
        String[] parts = value.split("[-+]", 2)[0].split("\\."); try { return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])}; } catch (Exception ignored) { return null; }
    }
}
