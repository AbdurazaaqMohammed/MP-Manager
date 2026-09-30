package io.github.abdurazaaqmohammed.core.ui;

import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.core.ui.util.NotifCompat;
import io.github.abdurazaaqmohammed.core.ui.util.OverlayTheme;
import io.github.abdurazaaqmohammed.core.ui.util.ThemeAttrs;

/**
 * The single UI entry point for the whole project. Do not construct dialogs,
 * toasts or notifications outside core/ui — always go through this facade so
 * theming stays consistent.
 */
public final class UIKit {

    private UIKit() {
    }

    public static MaterialAlertDialogBuilder dialog(Context c) {
        return new MaterialAlertDialogBuilder(ThemeAttrs.themed(c));
    }

    public static void toast(Context c, CharSequence msg) {
        io.github.abdurazaaqmohammed.core.ui.util.AppToast.show(c, msg);
    }

    public static void toast(Context c, CharSequence msg, boolean longDuration) {
        io.github.abdurazaaqmohammed.core.ui.util.AppToast.show(c, msg, longDuration);
    }

    public static NotificationCompat.Builder notify(Context c, String channelId) {
        return NotifCompat.build(c, channelId);
    }

    public static OverlayTheme overlay(Context c) {
        return OverlayTheme.get(c);
    }
}
