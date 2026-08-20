package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Context;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class CopyUtil {
    public static void copyToClipboard(Activity context, CharSequence text) {
        ((android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);
        Extensions.showMessage(context, context.getString(R.string.copied_to_clipboard, text));
    }
}