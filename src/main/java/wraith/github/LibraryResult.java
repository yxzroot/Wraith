package wraith.github;

import java.util.List;
import wraith.core.ProgramEntry;

public record LibraryResult(List<ProgramEntry> programs, boolean offline, String notice) { }
