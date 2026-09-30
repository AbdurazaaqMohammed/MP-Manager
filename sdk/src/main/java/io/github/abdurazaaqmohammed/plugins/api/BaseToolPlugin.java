package io.github.abdurazaaqmohammed.plugins.api;

import android.content.Context;

/**
 * Convenience base so each tool in plugins.tools.* only fills metadata
 * plus createView(). Keeps id/title/category boilerplate in one place.
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
    @Override public String title(Context context) { return title; }
    @Override public String subtitle(Context context) { return subtitle; }
    @Override public String category(Context context) { return category; }
    @Override public int iconRes(Context context) { return 0; }
}
