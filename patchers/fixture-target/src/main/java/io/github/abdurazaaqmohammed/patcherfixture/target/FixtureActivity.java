package io.github.abdurazaaqmohammed.patcherfixture.target;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public final class FixtureActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        TextView text = new TextView(this);
        String value = FixtureValue.value();
        text.setText(value); text.setTextSize(24); setContentView(text);
        Log.i("MP-Patcher-Fixture", "VALUE=" + value);
        text.postDelayed(() -> { text.setText(FixtureValue.value()); Log.i("MP-Patcher-Fixture", "VALUE=" + FixtureValue.value()); }, 8000);
    }
}
