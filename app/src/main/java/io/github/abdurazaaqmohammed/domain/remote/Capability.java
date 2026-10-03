package io.github.abdurazaaqmohammed.domain.remote;

/**
 * Operations a backend may or may not implement.
 *
 * <p>The UI asks {@link RemoteFileSystem#supports(Capability)} before offering a
 * menu item, instead of letting the user discover the gap by hitting an
 * exception.
 */
public enum Capability {
    /** Directory tree navigation, e.g. entering a folder. */
    DIRECTORIES(true),
    /** Creating a directory / bucket prefix. */
    MKDIR(true),
    /** Renaming an entry (server-side move; not all backends offer it). */
    RENAME(true),
    /** Appending to an existing file rather than replacing it. */
    APPEND(false),
    /** Resolving symlinks on the server. */
    SYMLINKS(false),
    /** Setting mtime/atime on the server. */
    SET_TIMESTAMPS(false),
    /** Server-side copy. */
    SERVER_SIDE_COPY(false);

    private final boolean common;

    Capability(boolean common) {
        this.common = common;
    }

    /** True when nearly every backend provides it; used to order the UI. */
    public boolean isCommon() {
        return common;
    }
}