package io.github.abdurazaaqmohammed.ui.editor;

import io.github.abdurazaaqmohammed.util.misc.StickerUtil;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/** Previews animated WhatsApp Lottie sticker archives using native {@link LottieAnimationView}. */
public class StickerPreviewDialog {

    private StickerPreviewDialog() {}

    public static void show(Activity activity, File stickerFile) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || stickerFile == null) return;

        float density = activity.getResources().getDisplayMetrics().density;
        int sizePx = (int) (280 * density);
        int pad = (int) (16 * density);

        FrameLayout rootLayout = new FrameLayout(activity);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.setPadding(pad, pad, pad, 0);

        ProgressBar progressBar = new ProgressBar(activity);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        progressBar.setLayoutParams(pbLp);
        rootLayout.addView(progressBar);

        LottieAnimationView lottieView = new LottieAnimationView(activity);
        FrameLayout.LayoutParams lvLp = new FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER);
        lottieView.setLayoutParams(lvLp);
        lottieView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lottieView.setRepeatCount(LottieDrawable.INFINITE);
        lottieView.setVisibility(View.INVISIBLE);
        rootLayout.addView(lottieView);

        TextView errorView = new TextView(activity);
        errorView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        errorView.setText(R.string.shortcut_image_failed);
        errorView.setGravity(Gravity.CENTER);
        errorView.setVisibility(View.GONE);
        rootLayout.addView(errorView);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.sticker_preview))
                .setView(rootLayout)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.open_as_archive, (d, which) -> {
                    if (activity instanceof MainActivity main) {
                        main.loadZipFolderInPane(stickerFile, "", main.lastPaneSelected == 1, true);
                    }
                })
                .create();

        dialog.setOnDismissListener(d -> {
            lottieView.cancelAnimation();
            lottieView.clearAnimation();
        });

        dialog.show();

        AppExecutors.io().execute(() -> {
            String json = StickerUtil.extractLottieJson(stickerFile);
            LottieComposition composition = null;
            if (json != null) {
                LottieResult<LottieComposition> result =
                        LottieCompositionFactory.fromJsonStringSync(json, stickerFile.getAbsolutePath());
                composition = result != null ? result.getValue() : null;
            }
            LottieComposition ready = composition;
            AppExecutors.runOnUiIfAlive(activity, () -> {
                if (!dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                if (ready == null) {
                    lottieView.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    return;
                }
                lottieView.setComposition(ready);
                lottieView.setVisibility(View.VISIBLE);
                lottieView.playAnimation();
            });
        });
    }
}
