package io.github.abdurazaaqmohammed.plugins.ext;

/**
 * Marker for every host extension a pack contributes (sidebar items, settings
 * rows, file-menu actions, editor actions, APK dialog actions).
 *
 * <p>A pack exposes extensions via {@code ToolPack.extensions()}; the host
 * collects them in {@link ExtensionRegistry} when the pack APK is loaded and
 * drops them again on uninstall. Each sub-interface declares its own
 * {@code id()}, which must be globally unique — prefix it with your pack id,
 * e.g. {@code "mypack.myaction"}.
 */
public interface AppExtension {
}
