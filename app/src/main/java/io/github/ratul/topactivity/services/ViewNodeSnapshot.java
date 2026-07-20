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

import java.util.ArrayList;
import java.util.List;

public class ViewNodeSnapshot {

    public final Rect bounds;
    public final int[] location;
    public final String className;
    public final String resourceId;
    public final String text;
    public final String contentDescription;
    public final String parentClass;
    public final String packageName;
    public final boolean visible;
    public final boolean enabled;
    public final boolean focusable;
    public final boolean clickable;
    public final boolean longClickable;
    public final boolean focused;
    public final boolean selected;
    public final boolean checked;
    public final int childCount;
    public final int[] path;
    public final List<ViewNodeSnapshot> children = new ArrayList<>();

    ViewNodeSnapshot(Rect bounds, int[] location, String className, String resourceId,
                     String text, String contentDescription, String parentClass, String packageName,
                     boolean visible, boolean enabled, boolean focusable, boolean clickable,
                     boolean longClickable, boolean focused, boolean selected, boolean checked,
                     int childCount, int[] path) {
        this.bounds = bounds;
        this.location = location;
        this.className = className;
        this.resourceId = resourceId;
        this.text = text;
        this.contentDescription = contentDescription;
        this.parentClass = parentClass;
        this.packageName = packageName;
        this.visible = visible;
        this.enabled = enabled;
        this.focusable = focusable;
        this.clickable = clickable;
        this.longClickable = longClickable;
        this.focused = focused;
        this.selected = selected;
        this.checked = checked;
        this.childCount = childCount;
        this.path = path;
    }
}
