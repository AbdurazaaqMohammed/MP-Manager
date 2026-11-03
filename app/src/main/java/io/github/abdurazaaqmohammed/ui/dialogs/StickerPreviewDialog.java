package io.github.abdurazaaqmohammed.ui.dialogs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.StickerUtil;

public class StickerPreviewDialog {

    private StickerPreviewDialog() {}

    @SuppressLint("SetJavaScriptEnabled")
    public static void show(Activity activity, File stickerFile) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        FrameLayout rootLayout = new FrameLayout(activity);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar progressBar = new ProgressBar(activity);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(pbLp);
        rootLayout.addView(progressBar);

        WebView webView = new WebView(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        int sizePx = (int) (280 * density);
        FrameLayout.LayoutParams wvLp = new FrameLayout.LayoutParams(sizePx, sizePx);
        wvLp.gravity = Gravity.CENTER;
        webView.setLayoutParams(wvLp);
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDomStorageEnabled(true);

        rootLayout.addView(webView);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.sticker_preview))
                .setView(rootLayout)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.open_as_archive, (d, which) -> {
                    if (activity instanceof io.github.abdurazaaqmohammed.MPManager.MainActivity main) {
                        main.loadZipFolderInPane(stickerFile, "", main.lastPaneSelected == 1, true);
                    }
                })
                .create();

        dialog.setOnDismissListener(d -> {
            try {
                webView.loadUrl("about:blank");
                webView.destroy();
            } catch (Throwable ignored) {}
        });

        dialog.show();

        new Thread(() -> {
            String json = StickerUtil.extractLottieJson(stickerFile);
            activity.runOnUiThread(() -> {
                if (json == null || dialog.isShowing() == false) return;
                progressBar.setVisibility(android.view.View.GONE);

                String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                        + "<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=no'>"
                        + "<script src='file:///android_asset/lottie.min.js'></script>"
                        + "<style>"
                        + "body { margin:0; padding:0; display:flex; justify-content:center; align-items:center; height:100vh; background:transparent; overflow:hidden; }"
                        + "#lottie { width:100%; height:100%; }"
                        + "</style></head><body>"
                        + "<div id='lottie'></div>"
                        + "<script>"
                        + "try {"
                        + "  var animData = " + json + ";"
                        + "  lottie.loadAnimation({"
                        + "    container: document.getElementById('lottie'),"
                        + "    renderer: 'svg',"
                        + "    loop: true,"
                        + "    autoplay: true,"
                        + "    animationData: animData"
                        + "  });"
                        + "} catch(e) { document.body.innerHTML = '<p style=\"color:red;\">'+e.message+'</p>'; }"
                        + "</script></body></html>";

                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
            });
        }).start();
    }
}
