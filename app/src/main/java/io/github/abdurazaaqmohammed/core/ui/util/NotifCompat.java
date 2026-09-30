package io.github.abdurazaaqmohammed.core.ui.util;

import android.content.Context;

import androidx.core.app.NotificationCompat;

/**
 * Notification builder pre-tinted with the current theme accent.
 * Channel creation is the caller's responsibility; colorization is the
 * caller's decision too.
 */
public final class NotifCompat {

    private NotifCompat() {
    }

    public static NotificationCompat.Builder build(Context c, String channelId) {
        return new NotificationCompat.Builder(c, channelId)
                .setColor(ThemeAttrs.accent(c));
    }
}
