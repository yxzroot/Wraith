package wraith.github;

@FunctionalInterface
public interface LibraryProvider {
    LibraryResult load(boolean forceRefresh) throws Exception;
}
