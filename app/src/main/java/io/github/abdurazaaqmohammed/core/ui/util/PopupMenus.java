package io.github.abdurazaaqmohammed.core.ui.util;

import android.widget.PopupMenu;

import java.lang.reflect.Method;

/**
 * PopupMenu helpers. Extracted from MainActivity.forceShowIcons.
 */
public final class PopupMenus {

    private PopupMenus() {
    }

    public static void forceShowIcons(PopupMenu popupMenu) {
        try {
            java.lang.reflect.Field field = popupMenu.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popupMenu);
            Method setForceIcons = menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon",
                    boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
