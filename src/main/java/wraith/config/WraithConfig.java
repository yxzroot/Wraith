package wraith.config;

import java.util.ArrayList;
import java.util.List;

/** Local settings. The optional GitHub token is never written to the log. */
public final class WraithConfig {
    public String playerName = "";
    /** Shown once after the name screen; can be reopened from Help at any time. */
    public boolean tutorialCompleted;
    public String lastPage = "library";
    public Double windowX;
    public Double windowY;
    public boolean useSystemWindowFrame;
    public List<String> owners = new ArrayList<>(List.of("yxzroot"));
    public boolean includeForks;
    public boolean includeArchived;
    public boolean showWithoutReleases = true;
    public String githubToken = "";
}
