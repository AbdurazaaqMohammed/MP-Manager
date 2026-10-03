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
import android.widget.TextView;

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
        super("qrgen", R.string.qrgen_title, R.string.qrgen_sub,  ToolCategories.NETWORK);
    }

    private void shareFile(Context context, File f) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("image/png");
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(s, context.getString(R.string.qrgen_share)));
        } catch (Exception e) {
            ToolViewFactory.toast(context, context.getString(R.string.qrgen_share_failed));
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.qrgen_qr_generator));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.qrgen_text_url_or_wifi_config),
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
        MaterialButton genBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.qrgen_generate), 1f);
        MaterialButton saveBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.qrgen_save), 1f);
        MaterialButton shareBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.qrgen_share), 1f);
        genBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                ToolViewFactory.toast(context, context.getString(R.string.qrgen_enter_text_first));
                return;
            }
            try {
                qrGenBitmap = QrUtil.generate(text, 1024);
                qrGenView.setImageBitmap(qrGenBitmap);
                qrGenView.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                ToolViewFactory.toast(context, context.getString(R.string.qrgen_qr_failed) + e.getMessage());
            }
        });
        saveBtn.setOnClickListener(v -> {
            if (qrGenBitmap == null) {
                ToolViewFactory.toast(context, context.getString(R.string.qrgen_generate_first));
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
                    ToolViewFactory.toast(context, context.getString(R.string.qrgen_qr_image_saved));
                } catch (final Exception e) {
                    ToolViewFactory.toast(context, context.getString(R.string.qrgen_save_failed) + e.getMessage());
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
                ToolViewFactory.toast(context, context.getString(R.string.qrgen_enter_text_first));
                return;
            }
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            context.startActivity(Intent.createChooser(share, context.getString(R.string.qrgen_share)));
        });
        LinearLayout qrRow2 = ToolViewFactory.makeRow(box);
        MaterialButton qrOpenBtn = ToolViewFactory.makeRowButton(qrRow2, qrRow2.getContext().getString(R.string.qrgen_open_image), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(qrRow2, qrRow2.getContext().getString(R.string.qrgen_copy_text), 1f);
        qrOpenBtn.setOnClickListener(v -> {
            if (qrGenFile != null && qrGenFile.exists()) {
                try {
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", qrGenFile);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "image/png");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(i, context.getString(R.string.qrgen_open_image)));
                } catch (Exception e) {
                    ToolViewFactory.toast(context, context.getString(R.string.qrgen_open_failed));
                }
            } else {
                ToolViewFactory.toast(context, context.getString(R.string.qrgen_generate_or_save_first));
            }
        });
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, context.getString(R.string.qrgen_qr), input.getText().toString()));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.qrgen_wi_fi_shortcut_wifi_t_wpa_s_my));
        return box;
    }

    @Override
    public void onDestroy() {
        qrGenView = null;
        qrGenBitmap = null;
        qrGenFile = null;
    }
}
