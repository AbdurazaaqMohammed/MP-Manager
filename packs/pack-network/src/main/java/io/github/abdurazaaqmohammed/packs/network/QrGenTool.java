package io.github.abdurazaaqmohammed.packs.network;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.utils.QrUtil;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Extraction of ToolRunnerActivity.buildQrGen().
 *
 * <p>"Locate image file" became dependency-free "Open image".
 */
public class QrGenTool extends BaseToolPlugin {

    private ImageView qrGenView;
    private Bitmap qrGenBitmap;
    private File qrGenFile;

    public QrGenTool() {
        super("qrgen", "QR Generator", "Text, URL, Wi-Fi to QR", ToolCategories.NETWORK);
    }

    private void shareFile(Context context, File f) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("image/png");
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(s, "Share"));
        } catch (Exception e) {
            ToolViewFactory.toast(context, "Share failed");
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "QR Generator");
        EditText input = ToolViewFactory.makeInput(box, "Text, URL or WIFI config",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        qrGenView = new ImageView(context);
        qrGenView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 280));
        vp.gravity = Gravity.CENTER;
        int m8 = ToolViewFactory.dp(context, 8);
        vp.setMargins(0, m8, 0, m8);
        box.addView(qrGenView, vp);
        qrGenView.setVisibility(View.GONE);
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton genBtn = ToolViewFactory.makeRowButton(row, "Generate", 1f);
        MaterialButton saveBtn = ToolViewFactory.makeRowButton(row, "Save", 1f);
        MaterialButton shareBtn = ToolViewFactory.makeRowButton(row, "Share", 1f);
        genBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                ToolViewFactory.toast(context, "Enter text first");
                return;
            }
            try {
                qrGenBitmap = QrUtil.generate(text, 1024);
                qrGenView.setImageBitmap(qrGenBitmap);
                qrGenView.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                ToolViewFactory.toast(context, "QR failed: " + e.getMessage());
            }
        });
        saveBtn.setOnClickListener(v -> {
            if (qrGenBitmap == null) {
                ToolViewFactory.toast(context, "Generate first");
                return;
            }
            new Thread(() -> {
                try {
                    QrUtil.saveToGallery(context, qrGenBitmap, "qr_" + System.currentTimeMillis());
                    try {
                        File dir = new File(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES), "QRCodes");
                        dir.mkdirs();
                        File out = new File(dir, "qr_" + System.currentTimeMillis() + ".png");
                        FileOutputStream os = new FileOutputStream(out);
                        qrGenBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        os.close();
                        qrGenFile = out;
                    } catch (Exception ignored) {
                    }
                    ToolViewFactory.toast(context, "QR image saved");
                } catch (final Exception e) {
                    ToolViewFactory.toast(context, "Save failed: " + e.getMessage());
                }
            }).start();
        });
        shareBtn.setOnClickListener(v -> {
            if (qrGenBitmap != null) {
                try {
                    File dir = new File(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES), "QRCodes");
                    dir.mkdirs();
                    File out = new File(dir, "qr_" + System.currentTimeMillis() + ".png");
                    FileOutputStream os = new FileOutputStream(out);
                    qrGenBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                    os.close();
                    qrGenFile = out;
                    shareFile(context, out);
                    return;
                } catch (Exception ignored) {
                }
            }
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                ToolViewFactory.toast(context, "Enter text first");
                return;
            }
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            context.startActivity(Intent.createChooser(share, "Share"));
        });
        LinearLayout qrRow2 = ToolViewFactory.makeRow(box);
        MaterialButton qrOpenBtn = ToolViewFactory.makeRowButton(qrRow2, "Open image", 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(qrRow2, "Copy text", 1f);
        qrOpenBtn.setOnClickListener(v -> {
            if (qrGenFile != null && qrGenFile.exists()) {
                try {
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", qrGenFile);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "image/png");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(i, "Open image"));
                } catch (Exception e) {
                    ToolViewFactory.toast(context, "Open failed");
                }
            } else {
                ToolViewFactory.toast(context, "Generate or save first");
            }
        });
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, "qr", input.getText().toString()));
        ToolViewFactory.addLabel(box, "Wi-Fi shortcut: WIFI:T:WPA;S:MyNet;P:pass123;;");
        return box;
    }

    @Override
    public void onDestroy() {
        qrGenView = null;
        qrGenBitmap = null;
        qrGenFile = null;
    }
}
