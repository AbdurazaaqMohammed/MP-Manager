package io.github.abdurazaaqmohammed.core.ui.base;

import android.R;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
        View content = getWindow().getDecorView().findViewById(R.id.content);
        if (content != null){
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
            ViewCompat.requestApplyInsets(content);
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
