/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.manager.InspectOverlayManager;
import io.github.ratul.topactivity.services.AccessibilityMonitoringService;
import io.github.ratul.topactivity.services.ViewInfo;
import io.github.ratul.topactivity.services.ViewNodeSnapshot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ViewInspectorDialog extends Dialog {

    private final ClipboardManager clipboardManager;
    private final InspectOverlayManager overlayManager;
    private ViewNodeSnapshot rootSnapshot;
    private int[] currentPath;
    private ViewInfo viewInfo;

    private int dragInitX = 0;
    private int dragInitY = 0;
    private int dragInitMarginX = 0;
    private int dragInitMarginY = 0;
    private int panelX = 0;
    private int panelY = 0;
    private boolean positionInitialized = false;

    public ViewInspectorDialog(Context context, AccessibilityMonitoringService.FoundView found,
                               InspectOverlayManager overlayManager) {
        super(context, R.style.inspectorDialogTheme); //, R.style.AppTheme
        this.overlayManager = overlayManager;
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        applyFoundView(found);
        overlayManager.setInfoDialog(this);
    }

    private void applyFoundView(AccessibilityMonitoringService.FoundView found) {
        this.viewInfo = found.viewInfo;
        this.currentPath = found.path;
        this.rootSnapshot = found.rootSnapshot;
    }

    public void updateFoundView(AccessibilityMonitoringService.FoundView found) {
        applyFoundView(found);
        overlayManager.setHighlight(viewInfo.getBounds());
        rebuildContent();
    }

    @Override
    public void show() {
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                attrs.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }


            attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            attrs.gravity = Gravity.TOP | Gravity.START;
            android.util.Pair<Integer, Integer> size = io.github.ratul.topactivity.extensions.GenericExtensions.getScreenSize(
                    (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE));
            if (!positionInitialized) {
                panelX = 0;
                panelY = (int) (size.second * 0.55f);
                positionInitialized = true;
            }
            attrs.x = panelX;
            attrs.y = panelY;
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = (int) (size.second * 0.42f);
            window.setAttributes(attrs);
            window.setDimAmount(0f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        }
        super.show();
        setContentView(createContentView(getContext()));
    }

    @Override
    public void onBackPressed() {
        overlayManager.clearHighlight();
        super.dismiss();
    }

    public void exitInspect() {
        overlayManager.clearHighlight();
        overlayManager.hide();
        overlayManager.setInfoDialog(null);
        super.dismiss();
    }

    public void hideWindow() {
        super.dismiss();
    }

    public void showWindow() {
        if (!isShowing()) {
            show();
        }
    }

    public boolean isShowing() {
        return getWindow() != null && getWindow().getDecorView().isAttachedToWindow();
    }

    @Override
    public void dismiss() {
        overlayManager.setInfoDialog(null);
        super.dismiss();
    }

    private View createContentView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.argb(225, 20, 20, 20));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView header = new TextView(context);
        header.setText(context.getString(R.string.view_info));
        header.setTextSize(16f);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 14, 0, 14);
        header.setBackgroundColor(Color.argb(255, 30, 30, 30));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        header.setOnTouchListener(new DragTouchListener());
        root.addView(header);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 16, 32, 16);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        for (InfoItem item : collectViewInfo(context)) {
            container.addView(createInfoRow(context, item.label, item.value, item.copyable));
        }

        container.addView(createChildrenSection(context));

        scrollView.addView(container);
        root.addView(scrollView);

        root.addView(createBottomBar(context));
        return root;
    }

    private View createBottomBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(Color.argb(255, 30, 30, 30));
        bar.setPadding(8, 12, 8, 12);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView parentBtn = createBarButton(context, "▲", "Parent");
        TextView prevBtn = createBarButton(context, "◀", "Prev");
        TextView nextBtn = createBarButton(context, "▶", "Next");
        TextView childBtn = createBarButton(context, "▼", "Child");

        parentBtn.setOnClickListener(v -> navigateToParent());
        prevBtn.setOnClickListener(v -> navigateToSibling(-1));
        nextBtn.setOnClickListener(v -> navigateToSibling(1));
        childBtn.setOnClickListener(v -> navigateToChild());

        updateBarState(parentBtn, prevBtn, nextBtn, childBtn);

        TextView doneBtn = createBarButton(context, "✕", "Done");

        bar.addView(parentBtn);
        bar.addView(prevBtn);
        bar.addView(nextBtn);
        bar.addView(childBtn);
        bar.addView(doneBtn);
        doneBtn.setOnClickListener(v -> exitInspect());
        return bar;
    }

    private View createChildrenSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(context);
        title.setText(context.getString(R.string.view_children));
        title.setTextSize(13f);
        title.setTextColor(Color.argb(180, 255, 255, 255));
        title.setPadding(0, 16, 0, 6);
        section.addView(title);

        ViewNodeSnapshot current = AccessibilityMonitoringService.navigateToPath(rootSnapshot, currentPath);
        if (current == null || current.children.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(context.getString(R.string.no_children));
            empty.setTextSize(13f);
            empty.setTextColor(Color.argb(150, 255, 255, 255));
            empty.setPadding(0, 4, 0, 8);
            section.addView(empty);
            return section;
        }

        for (ViewNodeSnapshot child : current.children) {
            TextView childRow = new TextView(context);
            String name = child.className != null ? child.className : "Unknown";
            int dot = name.lastIndexOf('.');
            if (dot >= 0) name = name.substring(dot + 1);
            String id = child.resourceId != null && !"No ID".equals(child.resourceId) ? "  (R.id." + child.resourceId.split(File.separator)[1] + ")" : "";
            childRow.setText(context.getString(R.string.child_el_tx, name, id));
            childRow.setTextSize(13f);
            childRow.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
            childRow.setPadding(12, 8, 8, 8);
            childRow.setBackgroundResource(android.R.color.transparent);
            final int[] childPath = child.path;
            childRow.setOnClickListener(v -> swapCurrent(childPath));
            section.addView(childRow);

            View divider = new View(context);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(Color.argb(40, 255, 255, 255));
            section.addView(divider);
        }
        return section;
    }

    private TextView createBarButton(Context context, String icon, String desc) {
        TextView btn = new TextView(context);
        btn.setText(icon + "\n" + desc);
        btn.setTextSize(14f);
        btn.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(16, 8, 16, 8);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        return btn;
    }

    private void updateBarState(TextView parentBtn, TextView prevBtn, TextView nextBtn, TextView childBtn) {
        boolean hasParent = currentPath.length > 0;
        boolean hasChild = viewInfo.getChildCount() > 0;

        int totalCount = 0;
        int currentIndex = -1;
        if (rootSnapshot != null) {
            List<int[]> all = AccessibilityMonitoringService.getAllPaths(rootSnapshot);
            totalCount = all.size();
            for (int i = 0; i < all.size(); i++) {
                if (pathsEqual(all.get(i), currentPath)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        boolean hasPrev = currentIndex > 0;
        boolean hasNext = currentIndex >= 0 && currentIndex < totalCount - 1;

        setEnabled(parentBtn, hasParent);
        setEnabled(prevBtn, hasPrev);
        setEnabled(nextBtn, hasNext);
        setEnabled(childBtn, hasChild);
    }

    private void setEnabled(TextView btn, boolean enabled) {
        btn.setEnabled(enabled);
        btn.setAlpha(enabled ? 1f : 0.35f);
    }

    private void navigateToParent() {
        if (currentPath.length == 0) return;
        int[] parentPath = new int[currentPath.length - 1];
        System.arraycopy(currentPath, 0, parentPath, 0, parentPath.length);
        swapCurrent(parentPath);
    }

    private void navigateToChild() {
        if (viewInfo.getChildCount() <= 0) return;
        int[] childPath = new int[currentPath.length + 1];
        System.arraycopy(currentPath, 0, childPath, 0, currentPath.length);
        childPath[currentPath.length] = 0;
        swapCurrent(childPath);
    }

    private void navigateToSibling(int direction) {
        if (rootSnapshot == null) return;
        List<int[]> all = AccessibilityMonitoringService.getAllPaths(rootSnapshot);
        int currentIndex = -1;
        for (int i = 0; i < all.size(); i++) {
            if (pathsEqual(all.get(i), currentPath)) {
                currentIndex = i;
                break;
            }
        }
        int targetIndex = currentIndex + direction;
        if (currentIndex < 0 || targetIndex < 0 || targetIndex >= all.size()) return;
        swapCurrent(all.get(targetIndex));
    }

    private void swapCurrent(int[] newPath) {
        ViewNodeSnapshot target = AccessibilityMonitoringService.navigateToPath(rootSnapshot, newPath);
        if (target == null) return;
        currentPath = newPath;
        viewInfo = AccessibilityMonitoringService.getViewInfo(target, newPath);
        if (overlayManager != null) overlayManager.setHighlight(viewInfo.getBounds());
        rebuildContent();
    }

    private void rebuildContent() {
        setContentView(createContentView(getContext()));
        Window window = getWindow();
        if (window != null) {
            window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private List<InfoItem> collectViewInfo(Context context) {
        List<InfoItem> info = new ArrayList<>();

        info.add(new InfoItem(context.getString(R.string.view_class), viewInfo.getClassName(), true));
        String pkgName = viewInfo.getPackageName();
        info.add(new InfoItem(context.getString(R.string.view_package), pkgName, true));

        String resourceIdName = viewInfo.getResourceId();
        if (resourceIdName.equals("No ID")) {
            info.add(new InfoItem(context.getString(R.string.view_id), resourceIdName, true));
            info.add(new InfoItem(context.getString(R.string.view_id_int), resourceIdName, true));
            info.add(new InfoItem(context.getString(R.string.view_id_hex), resourceIdName, true));
        } else {
            String viewIdResourceName = resourceIdName.split(File.separator)[1];
            info.add(new InfoItem(context.getString(R.string.view_id), "R.id." + viewIdResourceName, true));
            if (!TextUtils.isEmpty(pkgName)) {
                try {
                    Context packageContext = context.createPackageContext(pkgName, 0);
                    int viewIdInt = packageContext.getResources().getIdentifier(viewIdResourceName, "id", pkgName);
                    String hex = "0x" + Integer.toHexString(viewIdInt);
                    info.add(new InfoItem(context.getString(R.string.view_id_int), String.valueOf(viewIdInt), true));
                    info.add(new InfoItem(context.getString(R.string.view_id_hex), hex, true));
                } catch (Exception e) {
                    info.add(new InfoItem(context.getString(R.string.view_id_int), "-1", true));
                    info.add(new InfoItem(context.getString(R.string.view_id_hex), "0x-1", true));
                }
            }
        }

        info.add(new InfoItem(
                context.getString(R.string.view_bounds),
                "left=" + viewInfo.getBounds().left + ", top=" + viewInfo.getBounds().top
                        + ", right=" + viewInfo.getBounds().right + ", bottom=" + viewInfo.getBounds().bottom,
                true
        ));

        info.add(new InfoItem(
                context.getString(R.string.view_location),
                "x=" + viewInfo.getLocationOnScreen()[0] + ", y=" + viewInfo.getLocationOnScreen()[1],
                true
        ));

        info.add(new InfoItem(
                context.getString(R.string.view_size),
                viewInfo.getWidth() + " x " + viewInfo.getHeight(),
                true
        ));

        info.add(new InfoItem(
                context.getString(R.string.view_visibility),
                viewInfo.isVisible() ? "Visible" : "Not Visible",
                false
        ));

        info.add(new InfoItem(context.getString(R.string.view_enabled), String.valueOf(viewInfo.isEnabled()), false));
        info.add(new InfoItem(context.getString(R.string.view_focusable), String.valueOf(viewInfo.isFocusable()), false));
        info.add(new InfoItem(context.getString(R.string.view_clickable), String.valueOf(viewInfo.isClickable()), false));
        info.add(new InfoItem(context.getString(R.string.view_long_clickable), String.valueOf(viewInfo.isLongClickable()), false));
        info.add(new InfoItem(context.getString(R.string.view_focused), String.valueOf(viewInfo.isFocused()), false));
        info.add(new InfoItem(context.getString(R.string.view_selected), String.valueOf(viewInfo.isSelected()), false));

        if (!viewInfo.getContentDescription().isEmpty()) {
            info.add(new InfoItem(context.getString(R.string.view_content_desc), viewInfo.getContentDescription(), true));
        }

        if (!viewInfo.getText().isEmpty()) {
            info.add(new InfoItem(context.getString(R.string.view_text), viewInfo.getText(), true));
        }

        info.add(new InfoItem(context.getString(R.string.view_parent), viewInfo.getParentClass(), true));
        info.add(new InfoItem(context.getString(R.string.view_children_count), String.valueOf(viewInfo.getChildCount()), false));

        return info;
    }

    private View createInfoRow(Context context, String label, String value, boolean copyable) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 8, 0, 8);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(12f);
        labelView.setTextColor(Color.argb(180, 255, 255, 255));
        container.addView(labelView);

        LinearLayout valueContainer = new LinearLayout(context);
        valueContainer.setOrientation(LinearLayout.HORIZONTAL);
        valueContainer.setGravity(Gravity.CENTER_VERTICAL);
        valueContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView valueView = new TextView(context);
        valueView.setText(value.isEmpty() ? context.getString(R.string.unknown) : value);
        valueView.setTextSize(14f);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        final String viewParentLabel = context.getString(R.string.view_parent);
        if (label.equals(viewParentLabel) && currentPath.length > 0) {
            valueView.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
            container.setOnClickListener(v -> navigateToParent());
        } else {
            valueView.setTextColor(Color.WHITE);
            valueView.setTextIsSelectable(true);
        }
        valueContainer.addView(valueView);

        if (copyable) {
            TextView copyBtn = new TextView(context);
            copyBtn.setText(context.getString(android.R.string.copy));
            copyBtn.setTextSize(12f);
            copyBtn.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
            copyBtn.setPadding(16, 8, 8, 8);
            copyBtn.setOnClickListener(v -> {
                ClipData clip = ClipData.newPlainText(label, value);
                clipboardManager.setPrimaryClip(clip);
                View root = getWindow() != null ? getWindow().getDecorView().getRootView() : null;
                if (root != null) {
                    Snackbar.make(root, context.getString(R.string.copied_to_clipboard, label), Snackbar.LENGTH_SHORT).show();
                }
            });
            valueContainer.addView(copyBtn);
        }

        container.addView(valueContainer);

        View divider = new View(context);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        ));
        divider.setBackgroundColor(Color.argb(50, 255, 255, 255));
        container.addView(divider);

        return container;
    }

    @Override
    public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
        super.onWindowAttributesChanged(params);
    }

    private class DragTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Window window = getWindow();
            if (window == null) return false;
            WindowManager.LayoutParams params = window.getAttributes();
            int xCord = (int) event.getRawX();
            int yCord = (int) event.getRawY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragInitX = xCord;
                    dragInitY = yCord;
                    dragInitMarginX = params.x;
                    dragInitMarginY = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = dragInitMarginX + (xCord - dragInitX);
                    params.y = dragInitMarginY + (yCord - dragInitY);
                    panelX = params.x;
                    panelY = params.y;
                    window.setAttributes(params);
                    return true;
            }
            return false;
        }
    }

    private static boolean pathsEqual(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private static class InfoItem {
        final String label;
        final String value;
        final boolean copyable;

        InfoItem(String label, String value, boolean copyable) {
            this.label = label;
            this.value = value;
            this.copyable = copyable;
        }
    }

    public static void show(Context context, ViewInfo viewInfo) {
        new ViewInspectorDialog(context,
                new AccessibilityMonitoringService.FoundView(viewInfo, new int[0], null),
                null).show();
    }
}
