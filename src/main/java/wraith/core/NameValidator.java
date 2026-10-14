package wraith.core;

import java.util.regex.Pattern;

public final class NameValidator {
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]{2,16}");
    private NameValidator() { }
    public static Result validate(String name) {
        if (name == null || name.isBlank()) return new Result(false, "Enter a name to continue.");
        if (name.length() < 2 || name.length() > 16) return new Result(false, "Use 2 to 16 characters.");
        return ALLOWED.matcher(name).matches() ? new Result(true, "") : new Result(false, "Use letters, numbers, _ or - only.");
    }
    public record Result(boolean valid, String message) { }
}
