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
        super("flashlight", R.string.flashlight_title, R.string.flashlight_sub,  ToolCategories.DEVICE);
    }

    private void setTorch(Context context, boolean on) {
        if (Build.VERSION.SDK_INT < 23) {
            ToolViewFactory.toast(context, context.getString(R.string.flashlight_torch_needs_android_6));
            return;
        }
        if (cameraManager == null || torchCameraId == null) {
            ToolViewFactory.toast(context, context.getString(R.string.flashlight_flash_not_available));
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            try {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CAMERA}, 9001);
            } catch (Exception ignored) {
            }
            ToolViewFactory.toast(context, context.getString(R.string.flashlight_camera_permission_needed_then_));
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
            ToolViewFactory.toast(context, context.getString(R.string.flashlight_torch_failed));
        }
    }

    private void showScreenLightOverlay(Context context, MaterialButton screenBtn) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.WHITE);
        MaterialButton exit = new MaterialButton(context);
        exit.setText(exit.getContext().getString(R.string.flashlight_turn_off_screen_light));
        exit.setBackgroundColor(Color.parseColor("#CC000000"));
        exit.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
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
            if (screenBtn != null) screenBtn.setText(screenBtn.getContext().getString(R.string.flashlight_screen_light_off));
        };
        root.setOnClickListener(v -> close.run());
        exit.setOnClickListener(v -> close.run());
        AlertDialog d = new MaterialAlertDialogBuilder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).setView(root).create();
        holder[0] = d;
        screenLightDialog = d;
        d.setOnDismissListener(di -> {
            screenLightOn = false;
            screenLightDialog = null;
            if (screenBtn != null) screenBtn.setText(screenBtn.getContext().getString(R.string.flashlight_screen_light_off));
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
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.flashlight_flashlight));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.flashlight_torch_uses_the_camera_flash_sc));
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
        MaterialButton torchBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.flashlight_torch_off));
        torchBtn.setOnClickListener(v -> {
            setTorch(context, !torchOn);
            torchBtn.setText(torchOn ? torchBtn.getContext().getString(R.string.flashlight_torch_on) : torchBtn.getContext().getString(R.string.flashlight_torch_off));
        });
        MaterialButton screenBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.flashlight_screen_light_off));
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
                screenBtn.setText(screenBtn.getContext().getString(R.string.flashlight_screen_light_off));
                return;
            }
            screenLightOn = true;
            screenBtn.setText(screenBtn.getContext().getString(R.string.flashlight_screen_light_on_tap_to_turn_of));
            showScreenLightOverlay(context, screenBtn);
        });
        MaterialButton sosBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.flashlight_sos_blink_off));
        sosBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sosRunning = !sosRunning;
                sosBtn.setText(sosRunning ? sosBtn.getContext().getString(R.string.flashlight_sos_blink_on) : sosBtn.getContext().getString(R.string.flashlight_sos_blink_off));
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
                                torchBtn.setText(torchOn ? torchBtn.getContext().getString(R.string.flashlight_torch_on) : torchBtn.getContext().getString(R.string.flashlight_torch_off));
                                sosStep++;
                                handler.postDelayed(this, sosStep % 4 == 0 ? 600 : 250);
                            }
                        };
                    }
                    handler.post(sosTick);
                } else {
                    setTorchSilent(context, false);
                    torchBtn.setText(torchBtn.getContext().getString(R.string.flashlight_torch_off));
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
