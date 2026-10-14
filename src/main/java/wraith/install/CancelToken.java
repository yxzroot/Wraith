package wraith.install;
import java.util.concurrent.atomic.AtomicBoolean;
public final class CancelToken { private final AtomicBoolean cancelled = new AtomicBoolean(); public void cancel() { cancelled.set(true); } public boolean cancelled() { return cancelled.get(); } }
