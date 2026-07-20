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
package io.github.ratul.topactivity.services;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Rect;
import android.util.LruCache;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import io.github.ratul.topactivity.extensions.GenericExtensions;
import io.github.ratul.topactivity.repository.DataRepository;

@SuppressLint("AccessibilityPolicy")
public class AccessibilityMonitoringService extends AccessibilityService {

    private static AccessibilityMonitoringService instance;

    private final LruCache<String, Boolean> activityCache = new LruCache<>(500);

    public static AccessibilityMonitoringService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!DataRepository.getInstance().getAppState().isRunning()) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleWindowStateChange(event);
        }
    }

    private void handleWindowStateChange(AccessibilityEvent event) {
        CharSequence pkgSeq = event.getPackageName();
        CharSequence clsSeq = event.getClassName();
        if (pkgSeq == null || clsSeq == null) return;

        String pkg = pkgSeq.toString();
        String cls = clsSeq.toString();
        String cacheKey = pkg + "/" + cls;

        Boolean isCachedActivity = activityCache.get(cacheKey);
        if (isCachedActivity != null) {
            if (isCachedActivity) DataRepository.getInstance().updateData(pkg, cls);
            return;
        }

        boolean isActivity = GenericExtensions.isActivity(getPackageManager(), pkg, cls);
        activityCache.put(cacheKey, isActivity);

        if (isActivity) DataRepository.getInstance().updateData(pkg, cls);
    }

    public static final class FoundView {
        public final ViewInfo viewInfo;
        public final int[] path;
        public final ViewNodeSnapshot rootSnapshot;

        public FoundView(ViewInfo viewInfo, int[] path, ViewNodeSnapshot rootSnapshot) {
            this.viewInfo = viewInfo;
            this.path = path;
            this.rootSnapshot = rootSnapshot;
        }
    }

    public FoundView findViewAt(int x, int y) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        int[] pathHolder = new int[1];
        findViewAtRecursive(root, x, y, new int[0], pathHolder);
        if (finalPath == null) {
            recycleSubtree(root);
            return null;
        }
        int[] path = finalPath;
        finalPath = null;

        ViewNodeSnapshot rootSnapshot = snapshot(root, new int[0]);

        recycleSubtree(root);
        ViewNodeSnapshot found = navigateToPath(rootSnapshot, path);
        if (found == null) return null;
        ViewInfo info = getViewInfo(found, path);
        return new FoundView(info, path, rootSnapshot);
    }

    private static int[] finalPath;

    private void findViewAtRecursive(AccessibilityNodeInfo node, int x, int y, int[] prefix, int[] pathLen) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        if (!bounds.contains(x, y)) {
            return;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            int[] childPath = new int[prefix.length + 1];
            System.arraycopy(prefix, 0, childPath, 0, prefix.length);
            childPath[prefix.length] = i;
            findViewAtRecursive(child, x, y, childPath, pathLen);
            if (finalPath != null) {
                return;
            }
        }

        finalPath = prefix;
        pathLen[0] = prefix.length;
    }

    private static ViewNodeSnapshot snapshot(AccessibilityNodeInfo node, int[] path) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        int[] location = new int[2];
        location[0] = bounds.left;
        location[1] = bounds.top;

        AccessibilityNodeInfo parent = node.getParent();
        String parentClass = parent != null && parent.getClassName() != null
                ? parent.getClassName().toString() : "null";

        String viewIdResourceName = node.getViewIdResourceName();
        CharSequence pkgNameSeq = node.getPackageName();

        ViewNodeSnapshot snap = new ViewNodeSnapshot(
                bounds,
                location,
                node.getClassName() != null ? node.getClassName().toString() : "Unknown",
                viewIdResourceName != null ? viewIdResourceName : "No ID",
                node.getText() != null ? node.getText().toString() : "",
                node.getContentDescription() != null ? node.getContentDescription().toString() : "",
                parentClass,
                pkgNameSeq != null ? pkgNameSeq.toString() : "",
                node.isVisibleToUser(),
                node.isEnabled(),
                node.isFocusable(),
                node.isClickable(),
                node.isLongClickable(),
                node.isFocused(),
                node.isSelected(),
                node.isChecked(),
                node.getChildCount(),
                path
        );

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            int[] childPath = new int[path.length + 1];
            System.arraycopy(path, 0, childPath, 0, path.length);
            childPath[path.length] = i;
            snap.children.add(snapshot(child, childPath));
        }
        return snap;
    }

    public static ViewNodeSnapshot navigateToPath(ViewNodeSnapshot root, int[] path) {
        ViewNodeSnapshot current = root;
        for (int index : path) {
            if (current == null) return null;
            if (index < 0 || index >= current.children.size()) return null;
            current = current.children.get(index);
        }
        return current;
    }

    public static List<int[]> getAllPaths(ViewNodeSnapshot root) {
        List<int[]> result = new ArrayList<>();
        collectPathsRecursive(root, result);
        return result;
    }

    private static void collectPathsRecursive(ViewNodeSnapshot node, List<int[]> out) {
        if (node.bounds.width() > 0 && node.bounds.height() > 0) {
            out.add(node.path);
        }
        for (ViewNodeSnapshot child : node.children) {
            collectPathsRecursive(child, out);
        }
    }

    public static void recycleSubtree(AccessibilityNodeInfo node) {
        if (node == null) return;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                recycleSubtree(child);
                child.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        super.onServiceConnected();
    }

    @Override
    public void onRebind(Intent intent) {
        instance = this;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return true;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static ViewInfo getViewInfo(ViewNodeSnapshot node, int[] path) {
        return new ViewInfo(
                node.className,
                node.resourceId,
                node.text,
                node.contentDescription,
                new Rect(node.bounds),
                new int[]{node.location[0], node.location[1]},
                node.bounds.width(),
                node.bounds.height(),
                node.visible,
                node.enabled,
                node.focusable,
                node.clickable,
                node.longClickable,
                node.focused,
                node.selected,
                node.checked,
                node.parentClass,
                node.childCount,
                node.packageName,
                null,
                path
        );
    }
}
