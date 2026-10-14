package wraith.core;

public enum ProgramStatus {
    INSTALL("Install"), INSTALLED("Installed"), UPDATE("Update"), SOURCE_ONLY("Source only"), OFFLINE("Offline");
    private final String label;
    ProgramStatus(String label) { this.label = label; }
    public String label() { return label; }
}
