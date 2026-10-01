package io.github.abdurazaaqmohammed.packs.device;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildFlashlight().
 *
 * <p>Note: the host activity owns the permission callback, so unlike the
 * built-in version this plugin cannot auto-retry after the camera grant;
 * the user taps the torch button again instead.
 */
public class FlashlightTool extends BaseToolPlugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private CameraManager cameraManager;
    private String torchCameraId;
    private boolean torchOn;
    private boolean screenLightOn;
    private AlertDialog screenLightDialog;
    private boolean sosRunning;
    private int sosStep;
    private Runnable sosTick;

    public FlashlightTool() {
        super("flashlight", "Flashlight", "Use torch and screen light", ToolCategories.DEVICE);
    }

    private void setTorch(Context context, boolean on) {
        if (Build.VERSION.SDK_INT < 23) {
            ToolViewFactory.toast(context, "Torch needs Android 6+");
            return;
        }
        if (cameraManager == null || torchCameraId == null) {
            ToolViewFactory.toast(context, "Flash not available");
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            try {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CAMERA}, 9001);
            } catch (Exception ignored) {
            }
            ToolViewFactory.toast(context, "Camera permission needed, then tap again");
            return;
        }
        setTorchSilent(context, on);
    }

    private void setTorchSilent(Context context, boolean on) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && cameraManager != null && torchCameraId != null) {
                cameraManager.setTorchMode(torchCameraId, on);
                torchOn = on;
            }
        } catch (Exception e) {
            ToolViewFactory.toast(context, "Torch failed");
        }
    }

    private void showScreenLightOverlay(Context context, MaterialButton screenBtn) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.WHITE);
        MaterialButton exit = new MaterialButton(context);
        exit.setText("Turn off screen light");
        exit.setBackgroundColor(Color.parseColor("#CC000000"));
        exit.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int m = ToolViewFactory.dp(context, 24);
        ep.setMargins(m, m, m, ToolViewFactory.dp(context, 48));
        root.addView(exit, ep);
        AlertDialog[] holder = new AlertDialog[1];
        Runnable close = () -> {
            try {
                holder[0].dismiss();
            } catch (Exception ignored) {
            }
            screenLightOn = false;
            screenLightDialog = null;
            if (screenBtn != null) screenBtn.setText("Screen light: OFF");
        };
        root.setOnClickListener(v -> close.run());
        exit.setOnClickListener(v -> close.run());
        AlertDialog d = new MaterialAlertDialogBuilder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).setView(root).create();
        holder[0] = d;
        screenLightDialog = d;
        d.setOnDismissListener(di -> {
            screenLightOn = false;
            screenLightDialog = null;
            if (screenBtn != null) screenBtn.setText("Screen light: OFF");
        });
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.screenBrightness = 1.0f;
            d.getWindow().setAttributes(lp);
            d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        torchOn = false;
        screenLightOn = false;
        sosRunning = false;
        sosStep = 0;
        sosTick = null;
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Flashlight");
        ToolViewFactory.addLabel(box, "Torch uses the camera flash. Screen light fills the whole display white at max brightness.");
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (cameraManager != null) {
                    for (String id : cameraManager.getCameraIdList()) {
                        torchCameraId = id;
                        try {
                            Boolean flash = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                            if (flash != null && flash) {
                                torchCameraId = id;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        MaterialButton torchBtn = ToolViewFactory.makeButton(box, "Torch: OFF");
        torchBtn.setOnClickListener(v -> {
            setTorch(context, !torchOn);
            torchBtn.setText(torchOn ? "Torch: ON" : "Torch: OFF");
        });
        MaterialButton screenBtn = ToolViewFactory.makeButton(box, "Screen light: OFF");
        View screenLightView = new View(context);
        screenLightView.setBackgroundColor(Color.WHITE);
        screenLightView.setVisibility(View.GONE);
        screenLightView.setMinimumHeight(ToolViewFactory.dp(context, 4));
        box.addView(screenLightView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 4)));
        screenBtn.setOnClickListener(v -> {
            if (screenLightOn) {
                try {
                    if (screenLightDialog != null && screenLightDialog.isShowing()) screenLightDialog.dismiss();
                } catch (Exception ignored) {
                }
                screenLightOn = false;
                screenLightDialog = null;
                screenBtn.setText("Screen light: OFF");
                return;
            }
            screenLightOn = true;
            screenBtn.setText("Screen light: ON (tap to turn off)");
            showScreenLightOverlay(context, screenBtn);
        });
        MaterialButton sosBtn = ToolViewFactory.makeButton(box, "SOS blink: OFF");
        sosBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sosRunning = !sosRunning;
                sosBtn.setText(sosRunning ? "SOS blink: ON" : "SOS blink: OFF");
                if (sosRunning) {
                    sosStep = 0;
                    if (sosTick == null) {
                        sosTick = new Runnable() {
                            public void run() {
                                if (!sosRunning) {
                                    return;
                                }
                                boolean on = (sosStep % 2 == 0);
                                setTorchSilent(context, on);
                                torchBtn.setText(torchOn ? "Torch: ON" : "Torch: OFF");
                                sosStep++;
                                handler.postDelayed(this, sosStep % 4 == 0 ? 600 : 250);
                            }
                        };
                    }
                    handler.post(sosTick);
                } else {
                    setTorchSilent(context, false);
                    torchBtn.setText("Torch: OFF");
                }
            }
        });
        return box;
    }

    @Override
    public void onDestroy() {
        sosRunning = false;
        handler.removeCallbacksAndMessages(null);
        try {
            if (Build.VERSION.SDK_INT >= 23 && cameraManager != null && torchCameraId != null && torchOn) {
                cameraManager.setTorchMode(torchCameraId, false);
                torchOn = false;
            }
        } catch (Exception ignored) {
        }
        try {
            if (screenLightDialog != null && screenLightDialog.isShowing()) screenLightDialog.dismiss();
        } catch (Exception ignored) {
        }
        screenLightDialog = null;
    }
}
