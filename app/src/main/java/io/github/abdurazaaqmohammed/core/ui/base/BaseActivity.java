package io.github.abdurazaaqmohammed.core.ui.base;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;

/**
 * Single place for activity-wide UI behaviour.
 * All feature activities should extend this instead of AppCompatActivity
 * so theme switching, dynamic colors and edge-to-edge change in one file.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Theme must be set before super.onCreate so inflation uses it.
        ThemeRegistry.applySaved(this);
        super.onCreate(savedInstanceState);
        View content = getWindow().getDecorView().findViewById(android.R.id.content);
        if (content != null){
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                androidx.core.graphics.Insets sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return androidx.core.view.WindowInsetsCompat.CONSUMED;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(content);
        }
    }

    /**
     * Named dpPx (not dp) to avoid clashing with the legacy private dp()
     * helpers still present in migrated activities.
     */
    protected int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
