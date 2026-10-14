package wraith.core;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class NameValidatorTest {
    @Test void validatesAllowedNames() { assertTrue(NameValidator.validate("Nova_42").valid()); assertFalse(NameValidator.validate("x").valid()); assertFalse(NameValidator.validate("not allowed").valid()); }
}
