package wraith.install;

/** Local install record. No remote identity beyond the public repository name. */
public final class InstalledProgram {
    public String owner = "";
    public String repo = "";
    public String tag = "";
    public String type = "";
    public String location = "";
    public String hash = "";
    public String installedAt = "";
    public String minecraftModsPath = "";
    public boolean trustedToLaunch;
}
