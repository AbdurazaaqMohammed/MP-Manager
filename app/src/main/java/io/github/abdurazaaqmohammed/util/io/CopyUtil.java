package io.github.abdurazaaqmohammed.util.io;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.text.ClipboardManager;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class CopyUtil {
    public static void copyToClipboard(Activity context, CharSequence text) {
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);
        Extensions.showMessage(context, context.getString(R.string.copied_to_clipboard, text));
    }

    public static void copyToClipboard(Dialog d, CharSequence text) {
        Context context = d.getContext();
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);
        Extensions.showMessage(d, context.getString(R.string.copied_to_clipboard, text));
    }

    public static void copyToClipboard(Context context, CharSequence text) {
        if (context instanceof Activity activity) {
            copyToClipboard(activity, text);
        } else {
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);
            android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard, text), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}