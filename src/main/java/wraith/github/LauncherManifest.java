package wraith.github;

import java.util.List;
import java.util.Map;

public record LauncherManifest(String displayName, String description, String icon, Install install, Launch launch) {
    public record Install(String type, Object asset, boolean executable) { }
    public record Launch(String instructions, Run run) { }
    public record Run(Object windows, Object linux, List<String> args) { }
}
