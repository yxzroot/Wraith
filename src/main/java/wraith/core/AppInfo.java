package wraith.core;

import java.io.InputStream;
import java.util.Properties;

public record AppInfo(String name, String vendor, String version) {
    public static AppInfo load() {
        Properties p = new Properties();
        try (InputStream input = AppInfo.class.getResourceAsStream("/build-info.properties")) { if (input != null) p.load(input); }
        catch (Exception ignored) { }
        return new AppInfo(p.getProperty("app.name", "Wraith"), p.getProperty("app.vendor", "yxzroot"), p.getProperty("app.version", "dev"));
    }
}
