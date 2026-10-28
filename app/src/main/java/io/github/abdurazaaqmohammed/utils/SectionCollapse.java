package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Turns a settings {@link MaterialCardView} whose first content child is the
 * section title into a collapsible section: the title becomes a tappable
 * header row with a rotating chevron, and the collapsed state persists in
 * DefaultSharedPreferences so it is remembered next time settings open.
 *
 * <p>SPDX-License-Identifier: GPL-3.0-only
 */
public final class SectionCollapse {

    private static final String PREF_PREFIX = "section_collapsed_";
    private static final String TAG_APPLIED = "section_collapse_applied";

    private SectionCollapse() {
    }

    public static void applyToSection(View root, int cardId, String key) {
        if (root == null) return;
        View card = root.findViewById(cardId);
        if (!(card instanceof MaterialCardView)) return;
        Context context = card.getContext();
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        applyToCard((MaterialCardView) card, prefs, key);
    }

    public static void applyToCard(MaterialCardView card, SharedPreferences prefs, String key) {
        if (card == null || prefs == null) return;
        if (card.getChildCount() == 0) return;
        View contentView = card.getChildAt(0);
        if (!(contentView instanceof LinearLayout)) return;
        LinearLayout content = (LinearLayout) contentView;
        if (TAG_APPLIED.equals(content.getTag())) return; // already converted
        if (content.getChildCount() < 2) return; // title + at least one row
        View title = content.getChildAt(0);
        if (!(title instanceof TextView)) return;

        Context context = card.getContext();
        content.setTag(TAG_APPLIED);
        content.removeViewAt(0);

        // Zero the title's own bottom margin: the header row owns spacing now.
        ViewGroup.LayoutParams titleLp = title.getLayoutParams();
        if (titleLp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) titleLp).bottomMargin = 0;
        }
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setFocusable(true);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, ripple, true)) {
            header.setBackgroundResource(ripple.resourceId);
        }
        float density = context.getResources().getDisplayMetrics().density;
        int vPad = (int) (4 * density);
        header.setPadding(0, vPad, 0, vPad);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.baseline_arrow_drop_down_24);
        chevron.setContentDescription(context.getString(R.string.sidebar_collapse));
        int iconSize = (int) (24 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        int startMargin = (int) (8 * density);
        iconParams.setMarginStart(startMargin);
        chevron.setLayoutParams(iconParams);
        // Follow light/dark theme instead of the drawable's hardcoded tint.
        TypedValue onSurface = new TypedValue();
        if (context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, onSurface, true)) {
            try {
                chevron.setImageTintList(ColorStateList.valueOf(onSurface.data));
            } catch (Exception ignored) {
            }
        }

        header.addView(title);
        header.addView(chevron);
        content.addView(header, 0);

        boolean collapsed = prefs.getBoolean(PREF_PREFIX + key, false);
        setCollapsed(content, chevron, collapsed, false);

        header.setOnClickListener(v -> {
            boolean now = !isCollapsed(content);
            prefs.edit().putBoolean(PREF_PREFIX + key, now).apply();
            setCollapsed(content, chevron, now, true);
        });
    }

    private static boolean isCollapsed(LinearLayout content) {
        // Header is child 0; collapsed when the first body child is gone.
        return content.getChildCount() > 1
                && content.getChildAt(1).getVisibility() == View.GONE;
    }

    private static void setCollapsed(LinearLayout content, ImageView chevron,
                                     boolean collapsed, boolean animate) {
        for (int i = 1; i < content.getChildCount(); i++) {
            content.getChildAt(i).setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        float target = collapsed ? -90f : 0f;
        if (animate) {
            chevron.animate().rotation(target).setDuration(150).start();
        } else {
            chevron.setRotation(target);
        }
        Context context = chevron.getContext();
        try {
            chevron.setContentDescription(context.getString(
                    collapsed ? R.string.sidebar_expand : R.string.sidebar_collapse));
        } catch (Exception ignored) {
        }
    }
}
