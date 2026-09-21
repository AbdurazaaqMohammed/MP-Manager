package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.ClipboardManager;

import androidx.appcompat.app.AppCompatActivity;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class CopyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String ip = intent.getStringExtra("io.github.abdurazaaqmohammed.MPManager.ip");
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(ip);
        Extensions.showMessage((AppCompatActivity) context, (R.string.copied));
    }
}