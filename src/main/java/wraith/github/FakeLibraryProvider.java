package wraith.github;

import java.util.List;
import wraith.core.ProgramEntry;
import wraith.core.ProgramStatus;

/** Phase-one fixture. UI remains independent from GitHub protocol details. */
public final class FakeLibraryProvider implements LibraryProvider {
    @Override public LibraryResult load(boolean forceRefresh) throws Exception {
        Thread.sleep(300);
        return new LibraryResult(List.of(
                new ProgramEntry("yxzroot", "phantom", "Phantom", "A refined Minecraft client with a compact, expressive HUD.", "Java", "v1.0.0", ProgramStatus.INSTALL, true,
                        "# Phantom\nA local Minecraft client.\n\n## How to launch\nOpen Minecraft with the Forge 1.8.9 profile."),
                new ProgramEntry("yxzroot", "wraith-tools", "Wraith Tools", "Small utilities designed around a fast local desktop workflow.", "Java", "v0.4.1", ProgramStatus.INSTALLED, true,
                        "# Wraith Tools\n\n## Usage\nOpen the installed program from Wraith."),
                new ProgramEntry("yxzroot", "nightfall", "Nightfall", "A source repository without a published binary release yet.", "Kotlin", "—", ProgramStatus.SOURCE_ONLY, false,
                        "# Nightfall\nNo launch instructions found.")
        ), false, "");
    }
}
