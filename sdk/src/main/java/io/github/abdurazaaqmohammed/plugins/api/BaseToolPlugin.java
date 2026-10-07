package io.github.abdurazaaqmohammed.plugins.api;

import android.content.Context;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

/**
 * Convenience base so each tool in plugins.tools.* only fills metadata
 * plus createView(). Keeps id/title/category boilerplate in one place.
 *
 * <p>Title and subtitle are resolved from the owning pack's strings.xml by
 * convention keys {@code title_<id>} and {@code subtitle_<id>}; the English
 * literals passed to the constructor are the fallback when the pack resource
 * table is unavailable.
 */
public abstract class BaseToolPlugin implements ToolPlugin {

    private final String id;
    private final String title;
    private final String subtitle;
    private final String category;

    protected BaseToolPlugin(String id, String title, String subtitle, String category) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.category = category;
    }

    @Override public String id() { return id; }
    @Override public String title(Context context) { return PackRes.str("title_" + id, title); }
    @Override public String subtitle(Context context) { return PackRes.str("subtitle_" + id, subtitle); }
    @Override public String category(Context context) { return category; }
    @Override public int iconRes(Context context) { return 0; }
}
