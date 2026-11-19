package io.github.abdurazaaqmohammed.ui.editor.symbols;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.SeekBar;
import androidx.appcompat.app.AlertDialog;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.zerostudio.idea.widget.editor.symbolinput.v2.manager.SymbolDrawerManagerScreen;
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SharedPreferencesSymbolDrawerSettingsRepository;
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings;
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettingsScreen;
import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.ui.common.theme.BuiltInThemes;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Real configuration widgets under the app's Material3 light and dark themes. */
public class SymbolConfigurationMaterialTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Activity> activities = new ArrayList<>();
    private Context context;
    private String savedTheme;

    @Before public void prepare() {
        context = instrumentation.getTargetContext();
        assertTrue(context.getPackageName().endsWith(".editorregression"));
        savedTheme = ThemeRegistry.getCurrentId(context);
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> {
            for (Activity activity : activities) activity.finish();
            ThemeRegistry.setCurrentId(context, savedTheme);
        });
    }

    @Test public void settingsUseMaterialControlsInLightTheme() throws Exception {
        checkSettings(BuiltInThemes.LIGHT_ID);
    }

    @Test public void settingsUseMaterialControlsInDarkTheme() throws Exception {
        checkSettings(BuiltInThemes.DARK_ID);
    }

    private void checkSettings(String themeId) throws Exception {
        ThemeRegistry.setCurrentId(context, themeId);
        SymbolDrawerActivity activity = open(true);
        SymbolDrawerSettingsScreen screen = awaitType(activity, SymbolDrawerSettingsScreen.class);
        assertNotNull(awaitType(activity, MaterialToolbar.class));
        List<View> views = descendants(screen);
        assertTrue("Material switch present", contains(views, MaterialSwitch.class));
        assertTrue("Material slider present", contains(views, Slider.class));
        assertTrue("Exposed dropdown present", contains(views, MaterialAutoCompleteTextView.class));
        assertFalse("No framework switches", contains(views, Switch.class));
        assertFalse("No framework seek bars", contains(views, SeekBar.class));
        assertFalse("No legacy spinner", contains(views, Spinner.class));
        for (View view : views) {
            if (view instanceof Slider) {
                Slider slider = (Slider) view;
                assertEquals("Integer slider steps", 1f, slider.getStepSize(), 0f);
                assertTrue(slider.getValue() >= slider.getValueFrom());
                assertTrue(slider.getValue() <= slider.getValueTo());
            }
            if (view instanceof TextInputLayout) {
                assertEquals("Outlined fields", TextInputLayout.BOX_BACKGROUND_OUTLINE,
                        ((TextInputLayout) view).getBoxBackgroundMode());
            }
        }
        MaterialSwitch toggle = first(views, MaterialSwitch.class);
        boolean before = new SharedPreferencesSymbolDrawerSettingsRepository(context, "symbol_drawer_v2_settings", context.getSharedPreferences("symbol_drawer_v2_settings", Context.MODE_PRIVATE)).load().getSettings().getShowGroupTitles();
        instrumentation.runOnMainSync(toggle::performClick);
        assertEquals(!before, new SharedPreferencesSymbolDrawerSettingsRepository(context, "symbol_drawer_v2_settings", context.getSharedPreferences("symbol_drawer_v2_settings", Context.MODE_PRIVATE)).load().getSettings().getShowGroupTitles());
        instrumentation.runOnMainSync(toggle::performClick);
        screenshot("settings-" + themeId + ".png");
    }

    @Test public void symbolEditorUsesMaterialFieldsDropdownsChecksAndDialog() throws Exception {
        ThemeRegistry.setCurrentId(context, BuiltInThemes.DARK_ID);
        SymbolDrawerActivity activity = open(false);
        SymbolDrawerManagerScreen screen = awaitType(activity, SymbolDrawerManagerScreen.class);
        Method edit = SymbolDrawerManagerScreen.class.getDeclaredMethod("editSymbol",
                com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry.class);
        edit.setAccessible(true);
        instrumentation.runOnMainSync(() -> {
            try { edit.invoke(screen, new Object[]{null}); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        instrumentation.waitForIdleSync();
        List<View> all = new ArrayList<>();
        for (View root : WindowInspector.getGlobalWindowViews()) all.addAll(descendants(root));
        assertTrue("Outlined Material text fields", contains(all, TextInputLayout.class));
        assertTrue("Material action choices", contains(all, MaterialAutoCompleteTextView.class));
        assertTrue("Material regex options", contains(all, MaterialCheckBox.class));
        assertFalse("No framework action spinners", contains(all, Spinner.class));
        for (View view : all) {
            if (view instanceof TextInputLayout) assertEquals(TextInputLayout.BOX_BACKGROUND_OUTLINE,
                    ((TextInputLayout) view).getBoxBackgroundMode());
        }
        screenshot("symbol-editor-dark.png");
        instrumentation.runOnMainSync(() -> {
            for (View root : WindowInspector.getGlobalWindowViews()) {
                View button = root.findViewById(android.R.id.button2);
                if (button != null) button.performClick();
            }
        });
    }

    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap image = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null), name))) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } finally { image.recycle(); }
    }

    private SymbolDrawerActivity open(boolean settings) {
        SymbolDrawerActivity activity = (SymbolDrawerActivity) instrumentation.startActivitySync(
                new Intent(context, SymbolDrawerActivity.class)
                        .putExtra(SymbolDrawerActivity.EXTRA_SETTINGS, settings)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activities.add(activity);
        instrumentation.waitForIdleSync();
        return activity;
    }

    private <T> T awaitType(Activity activity, Class<T> type) throws Exception {
        long until = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < until) {
            AtomicReference<T> found = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> found.set(first(descendants(activity.getWindow().getDecorView()), type)));
            if (found.get() != null) return found.get();
            SystemClock.sleep(40);
        }
        throw new AssertionError("Missing " + type.getName());
    }

    private static List<View> descendants(View root) {
        List<View> result = new ArrayList<>();
        result.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(descendants(group.getChildAt(i)));
        }
        return result;
    }
    private static boolean contains(List<View> views, Class<?> type) { return first(views, type) != null; }
    private static <T> T first(List<View> views, Class<T> type) {
        for (View view : views) if (type.isInstance(view)) return type.cast(view);
        return null;
    }
}
