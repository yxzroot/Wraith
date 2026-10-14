package wraith.detect;
public enum LauncherType { VANILLA("Minecraft"), LUNAR("Lunar Client"), PRISM("Prism Launcher"), MULTIMC("MultiMC"), CUSTOM("Custom folder"); private final String label; LauncherType(String label) { this.label = label; } public String label() { return label; } }
