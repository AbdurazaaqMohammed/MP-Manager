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

import android.graphics.Rect;

public final class ViewInfo {
    private final String className;
    private final String resourceId;
    private final String text;
    private final String contentDescription;
    private final Rect bounds;
    private final int[] locationOnScreen;
    private final int width;
    private final int height;
    private final boolean isVisible;
    private final boolean isEnabled;
    private final boolean isFocusable;
    private final boolean isClickable;
    private final boolean isLongClickable;
    private final boolean isFocused;
    private final boolean isSelected;
    private final boolean isChecked;
    private final String parentClass;
    private final int childCount;
    private final String packageName;
    private final android.view.accessibility.AccessibilityNodeInfo parent;
    private final int[] path;

    public ViewInfo(String className, String resourceId, String text, String contentDescription,
                    Rect bounds, int[] locationOnScreen, int width, int height,
                    boolean isVisible, boolean isEnabled, boolean isFocusable, boolean isClickable,
                    boolean isLongClickable, boolean isFocused, boolean isSelected, boolean isChecked,
                    String parentClass, int childCount, String packageName,
                    android.view.accessibility.AccessibilityNodeInfo parent,
                    int[] path) {
        this.className = className;
        this.resourceId = resourceId;
        this.text = text;
        this.contentDescription = contentDescription;
        this.bounds = bounds;
        this.locationOnScreen = locationOnScreen;
        this.width = width;
        this.height = height;
        this.isVisible = isVisible;
        this.isEnabled = isEnabled;
        this.isFocusable = isFocusable;
        this.isClickable = isClickable;
        this.isLongClickable = isLongClickable;
        this.isFocused = isFocused;
        this.isSelected = isSelected;
        this.isChecked = isChecked;
        this.parentClass = parentClass;
        this.childCount = childCount;
        this.packageName = packageName;
        this.parent = parent;
        this.path = path;
    }

    public String getClassName() {
        return className;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getText() {
        return text;
    }

    public String getContentDescription() {
        return contentDescription;
    }

    public Rect getBounds() {
        return bounds;
    }

    public int[] getLocationOnScreen() {
        return locationOnScreen;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean isFocusable() {
        return isFocusable;
    }

    public boolean isClickable() {
        return isClickable;
    }

    public boolean isLongClickable() {
        return isLongClickable;
    }

    public boolean isFocused() {
        return isFocused;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public String getParentClass() {
        return parentClass;
    }

    public int getChildCount() {
        return childCount;
    }

    public String getPackageName() {
        return packageName;
    }

    public android.view.accessibility.AccessibilityNodeInfo getParent() {
        return parent;
    }

    public int[] getPath() {
        return path;
    }
}
