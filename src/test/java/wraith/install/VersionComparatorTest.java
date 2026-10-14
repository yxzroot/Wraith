package wraith.install;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VersionComparatorTest { @Test void comparesSemanticAndFallbackTags() { assertTrue(VersionComparator.updateAvailable("v1.2.0", "v1.3.0")); assertFalse(VersionComparator.updateAvailable("v1.3.0", "v1.2.0")); assertFalse(VersionComparator.updateAvailable("build-a", "build-a")); assertTrue(VersionComparator.updateAvailable("build-a", "build-b")); } }
