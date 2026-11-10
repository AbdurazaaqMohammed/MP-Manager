package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.utils.UiPrefs;

/** The same row metrics for local, provider and network listings. */
public final class FileRowStyle {
    private final float nameSize;
    private final int maxLines;
    private final int iconPx;

    public FileRowStyle(Context context) {
        int scale = UiPrefs.getScale(context);
        nameSize = UiPrefs.nameSize(scale);
        maxLines = UiPrefs.getMaxLines(context);
        iconPx = UiPrefs.iconDp(context, scale);
    }

    public void apply(TextView name, ImageView icon) {
        float px = nameSize * name.getResources().getDisplayMetrics().scaledDensity;
        if (Math.abs(name.getTextSize() - px) > 0.5f) name.setTextSize(nameSize);
        if (name.getMaxLines() != maxLines) name.setMaxLines(maxLines);
        if (name.getEllipsize() != TextUtils.TruncateAt.END) name.setEllipsize(TextUtils.TruncateAt.END);
        ViewGroup.LayoutParams params = icon.getLayoutParams();
        if (params != null && (params.width != iconPx || params.height != iconPx)) {
            params.width = params.height = iconPx;
            icon.setLayoutParams(params);
        }
    }
}
