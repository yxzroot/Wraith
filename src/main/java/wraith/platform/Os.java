package wraith.platform;

import java.util.Locale;

public enum Os {
    WINDOWS, LINUX, MAC;

    public static Os detect(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (value.contains("win")) return WINDOWS;
        if (value.contains("mac") || value.contains("darwin")) return MAC;
        return LINUX;
    }

    public static Os current() { return detect(System.getProperty("os.name")); }
}
