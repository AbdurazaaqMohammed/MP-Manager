package io.github.abdurazaaqmohammed.plugins.api;

import android.content.Context;

import androidx.annotation.StringRes;

/**
 * Convenience base so each tool in plugins.tools.* only fills metadata
 * plus createView(). Keeps id/title/category boilerplate in one place.
 *
 * <p>Two flavours of metadata are supported:
 * <ul>
 *   <li>plain {@link String} — the original form, for packs with nothing to
 *       translate;</li>
 *   <li>{@link StringRes} id — resolved through the {@link Context} passed to
 *       {@link #title(Context)}, so a pack ships its own
 *       {@code res/values-zh-rCN/strings.xml} and follows the host's locale.
 *       A value of {@code 0} means "no subtitle" and stays empty in every
 *       locale.</li>
 * </ul>
 * Mixing the two in one class is allowed as long as each constructor is used
 * consistently.
 */
public abstract class BaseToolPlugin implements ToolPlugin {

    private final String id;
    private final String title;
    private final String subtitle;
    private final String category;

    @StringRes
    private final int titleRes;
    @StringRes
    private final int subtitleRes;

    protected BaseToolPlugin(String id, String title, String subtitle, String category) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.category = category;
        this.titleRes = 0;
        this.subtitleRes = 0;
    }

    /**
     * @param titleRes    title string resource, or {@code 0} to keep {@code title}
     * @param subtitleRes subtitle string resource, or {@code 0} for no subtitle
     */
    protected BaseToolPlugin(String id, @StringRes int titleRes, @StringRes int subtitleRes,
                             String category) {
        this.id = id;
        this.title = null;
        this.subtitle = null;
        this.category = category;
        this.titleRes = titleRes;
        this.subtitleRes = subtitleRes;
    }

    @Override public String id() { return id; }

    @Override public String title(Context context) {
        if (titleRes != 0) {
            String resolved = context.getString(titleRes);
            // Fall back to the pack's English default if this build of the pack
            // was localized against a resource table it does not actually carry.
            if (resolved == null || resolved.isEmpty()) return id;
            return resolved;
        }
        return title;
    }

    @Override public String subtitle(Context context) {
        if (subtitleRes != 0) return context.getString(subtitleRes);
        return subtitle == null ? "" : subtitle;
    }

    @Override public String category(Context context) { return category; }
    @Override public int iconRes(Context context) { return 0; }
}
