package wraith.platform;

import java.nio.file.Path;

public record MinecraftLocation(Type type, Path path) {
    public enum Type { VANILLA, LUNAR, PRISM, MULTIMC, FLATPAK_MINECRAFT, FLATPAK_PRISM }
}
