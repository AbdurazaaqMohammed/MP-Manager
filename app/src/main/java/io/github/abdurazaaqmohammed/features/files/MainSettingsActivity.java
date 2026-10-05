package io.github.abdurazaaqmohammed.features.files;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

public class MainSettingsActivity extends BaseActivity {

    private SettingsController controller;
    private ScrollView settingsScroll;

    private Consumer<ActivityResult> pendingExternalSetting;
    private final ActivityResultLauncher<Intent> externalSettingLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Consumer<ActivityResult> cb = pendingExternalSetting;
                pendingExternalSetting = null;
                if (cb != null) {
                    try {
                        cb.accept(result);
                    } catch (Exception ignored) {
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_settings);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        settingsScroll = findViewById(R.id.settingsScroll);

        MainActivity host = resolveHost();
        controller = new SettingsController(this, host, this::launchExternalSetting);
        controller.attach(settingsScroll);
    }

    private MainActivity resolveHost() {
        MainActivity host = MainActivity.current();
        return host != null && !host.isFinishing() && !host.isDestroyed() ? host : null;
    }

    private void launchExternalSetting(Intent intent, Consumer<ActivityResult> callback) {
        try {
            pendingExternalSetting = callback;
            externalSettingLauncher.launch(intent);
        } catch (Exception ignored) {
            pendingExternalSetting = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (controller != null && settingsScroll != null) {
            controller.persist(settingsScroll);
        }
    }
}