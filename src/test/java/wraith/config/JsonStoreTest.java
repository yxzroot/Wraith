package wraith.config;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class JsonStoreTest {
    @TempDir Path temp;
    @Test void savesAndRecoversFromCorruption() throws Exception { ConfigStore store = new ConfigStore(temp); WraithConfig config = new WraithConfig(); config.playerName = "Nova"; store.save(config); assertEquals("Nova", store.load().playerName); Files.writeString(store.path(), "bad json"); assertTrue(store.load().playerName.isBlank()); assertTrue(Files.exists(temp.resolve("config.json.bad"))); }
}
