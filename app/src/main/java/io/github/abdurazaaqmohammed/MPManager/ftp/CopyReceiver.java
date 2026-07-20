package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import io.github.abdurazaaqmohammed.MPManager.R;

public class CopyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String ip = intent.getStringExtra("io.github.abdurazaaqmohammed.MPManager.ip");
        ((android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(ip);
        Toast.makeText(context, (R.string.copied), Toast.LENGTH_SHORT).show();
    }
}