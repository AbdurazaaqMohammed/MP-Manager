package io.github.abdurazaaqmohammed.plugins.ext;

/**
 * Adds one on/off switch row to the app settings dialog ("Plugins" section).
 *
 * <p>The host persists the value in DefaultSharedPreferences under
 * {@link #key()} — namespace it as {@code "plugin.<pack>.<name>"}.
 */
public interface SettingToggle extends AppExtension {

    /** Stable id, e.g. "mypack.autosave". */
    String id();

    /** Row title. */
    String title();

    /** Row summary, or "" for none. */
    String summary();

    /** SharedPreferences key holding the boolean value. */
    String key();

    /** Value used before the user ever touches the switch. */
    boolean defaultValue();
}
