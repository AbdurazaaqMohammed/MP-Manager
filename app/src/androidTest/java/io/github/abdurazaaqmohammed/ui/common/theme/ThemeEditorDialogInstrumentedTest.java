package io.github.abdurazaaqmohammed.ui.common.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.files.SettingsController;
import io.github.abdurazaaqmohammed.plugins.themes.LocalThemes;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePackContracts;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;

/**
 * Real-UI coverage of {@link ThemeEditorDialog} on the emulator: the editor
 * opens with a live draft (reached reflectively through the private
 * showEditor entry, mirroring what the manager's Edit action does), and the
 * per-file-type icon chooser subdialog:
 * <ul>
 *   <li>lists the localized label for every {@link ThemePackContracts#ICON_KINDS}
 *       entry (scrolled end to end),</li>
 *   <li>the Base/Light/Dark header toggle retargets every row between the
 *       base key and its {@code _light}/{@code _dark} variants,</li>
 *   <li>Clear tombstones exactly the visible variant while sibling variants
 *       survive (draft + refreshed summary),</li>
 *   <li>the color editor accepts an 8-digit {@code #AARRGGBB} hex and lands
 *       the fully transparent color on the draft.</li>
 * </ul>
 * Dialogs live in their own windows, so interaction goes through UiAutomation
 * accessibility windows (same pattern as ScreenOcrCaptureTest), not the
 * activity view hierarchy.
 */
public class ThemeEditorDialogInstrumentedTest {
    private final Instrumentation instrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final List<String> themeIds = new ArrayList<>();
    private Context context;
    private MainActivity activity;

    /** Localized label per contract icon kind (mirrors the editor's mapping). */
    private static final Map<String, Integer> KIND_LABELS = new LinkedHashMap<>();

    static {
        KIND_LABELS.put("folder", R.string.folder);
        KIND_LABELS.put("parent_folder", R.string.theme_editor_parent_folder);
        KIND_LABELS.put("apk", R.string.shortcut_icon_apk);
        KIND_LABELS.put("image", R.string.image);
        KIND_LABELS.put("video", R.string.shortcut_icon_video);
        KIND_LABELS.put("audio", R.string.shortcut_icon_audio);
        KIND_LABELS.put("arsc", R.string.shortcut_icon_resources);
        KIND_LABELS.put("dex", R.string.shortcut_icon_dex);
        KIND_LABELS.put("archive", R.string.shortcut_icon_archive);
        KIND_LABELS.put("sticker", R.string.shortcut_icon_sticker);
        KIND_LABELS.put("pdf", R.string.shortcut_icon_pdf);
        KIND_LABELS.put("text", R.string.shortcut_icon_text);
        KIND_LABELS.put("java", R.string.shortcut_icon_java);
        KIND_LABELS.put("kotlin", R.string.shortcut_icon_kotlin);
        KIND_LABELS.put("xml", R.string.shortcut_icon_xml);
        KIND_LABELS.put("json", R.string.shortcut_icon_json);
        KIND_LABELS.put("python", R.string.shortcut_icon_python);
        KIND_LABELS.put("smali", R.string.shortcut_icon_smali);
        KIND_LABELS.put("js", R.string.shortcut_icon_js);
        KIND_LABELS.put("elf", R.string.shortcut_icon_elf);
        KIND_LABELS.put("disk", R.string.shortcut_icon_disk);
        KIND_LABELS.put("database", R.string.shortcut_icon_database);
        KIND_LABELS.put("font", R.string.shortcut_icon_font);
        KIND_LABELS.put("binary", R.string.shortcut_icon_binary);
        KIND_LABELS.put("proto", R.string.shortcut_icon_data);
        KIND_LABELS.put("document", R.string.shortcut_icon_document);
        KIND_LABELS.put("sheet", R.string.shortcut_icon_sheet);
        KIND_LABELS.put("slide", R.string.shortcut_icon_slide);
        KIND_LABELS.put("script", R.string.shortcut_icon_script);
        KIND_LABELS.put("backup", R.string.shortcut_icon_backup);
        KIND_LABELS.put("certificate", R.string.shortcut_icon_certificate);
        KIND_LABELS.put("file", R.string.file);
    }

    @Before
    public void setUp() throws Exception {
        context = instrumentation.getTargetContext();
        assertTrue("Refusing a non-isolated package", context.getPackageName()
                .endsWith(".editorregression"));
        AccessibilityServiceInfo info =
                instrumentation.getUiAutomation().getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        instrumentation.getUiAutomation().setServiceInfo(info);
        // Non-root startup so MainActivity never blocks on a su grant dialog.
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("working_mode", "non_root")
                .putString("su_command", "/system/bin/false")
                .putBoolean("startup_request_root", false)
                .putBoolean("root_file_ops", false).commit();
        // Grant all-files access up front: on a fresh install (every gradle
        // connected run reinstalls and resets the appops state) MainActivity
        // otherwise forwards to the app's blocking "All files access"
        // Settings screen, which pauses MainActivity so the editor dialog
        // never renders. Same isolation pattern as FileIconThemeCorrectnessTest.
        String permissionCommand = Build.VERSION.SDK_INT >= 30
                ? "appops set " + context.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow"
                : "pm grant " + context.getPackageName() + " android.permission.WRITE_EXTERNAL_STORAGE";
        try (ParcelFileDescriptor.AutoCloseInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(permissionCommand))) {
            byte[] buffer = new byte[256];
            while (stream.read(buffer) != -1) { /* wait for the shell command */ }
        }
        activity = (MainActivity) instrumentation.startActivitySync(
                new Intent(context, MainActivity.class)
                        // CLEAR_TASK guarantees a fresh onCreate even when a
                        // previous test's finished MainActivity lingers in
                        // the (singleTask) task: a reused instance fires
                        // onNewIntent instead and the launch wait would time
                        // out against a monitor that only sees onCreate.
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
    }

    @After
    public void tearDown() {
        for (String id : themeIds) {
            LocalThemes.delete(context, id);
        }
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
        }
        instrumentation.waitForIdleSync();
    }

    // ---------- editor + chooser helpers ----------

    /** Opens the editor for {@code draft} exactly like the manager's Edit. */
    private void showEditor(LocalThemes.Draft draft) throws Exception {
        java.lang.reflect.Method show = ThemeEditorDialog.class.getDeclaredMethod(
                "showEditor", AppCompatActivity.class,
                SettingsController.ExternalSettingLauncher.class, Runnable.class,
                LocalThemes.Draft.class);
        show.setAccessible(true);
        instrumentation.runOnMainSync(() -> {
            try {
                show.invoke(null, activity, null, null, draft);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        instrumentation.waitForIdleSync();
        awaitText(context.getString(R.string.theme_editor_file_type_icons));
    }

    /** Opens the per-file-type chooser subdialog from the editor. */
    private void openFileTypeChooser() {
        // The file type icons row hosts the first "Choose" button in the
        // editor layout (before wallpaper scope and launcher rows).
        click(awaitText(context.getString(R.string.theme_editor_choose)));
        awaitText(context.getString(R.string.theme_editor_icon_variant_base));
        awaitText(context.getString(R.string.theme_editor_icon_variants_hint));
    }

    private void dismissChooserAndEditor() {
        click(awaitText(context.getString(R.string.theme_editor_done)));
        click(awaitText(context.getString(android.R.string.cancel)));
        instrumentation.waitForIdleSync();
    }

    private void dismissEditorOnly() {
        click(awaitText(context.getString(android.R.string.cancel)));
        instrumentation.waitForIdleSync();
    }

    /** Imports a saved local theme carrying only folder_light/folder_dark. */
    private LocalThemes.Draft importedVariantDraft() {
        String document = "{\"format\":1,\"type\":\"mp-manager-theme\","
                + "\"name\":\"UITheme\",\"images\":{"
                + "\"folder_light\":\"" + base64(pngBytes(0xFF00AA11)) + "\","
                + "\"folder_dark\":\"" + base64(pngBytes(0xFF00AA22)) + "\"}}";
        LocalThemes.ImportResult result;
        try (InputStream in = new ByteArrayInputStream(
                document.getBytes(StandardCharsets.UTF_8))) {
            result = LocalThemes.importTheme(context, in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertNotNull("Import failed: " + result.error, result.theme);
        assertFalse(result.rejected);
        themeIds.add(result.theme.id);
        LocalThemes.Draft draft = LocalThemes.draftFrom(context, result.theme.id);
        assertTrue(draft.hasIcon("folder_light"));
        assertTrue(draft.hasIcon("folder_dark"));
        assertFalse(draft.hasIcon("folder"));
        return draft;
    }

    /** The folder row must show the given variant state (Set/Not set). */
    private void awaitRowState(int stateRes) {
        String description = context.getString(
                R.string.theme_editor_icon_row_description,
                context.getString(R.string.folder), context.getString(stateRes));
        assertNotNull("Missing folder icon row '" + description + "'",
                awaitDescription(description));
    }

    /** Scrolls the chooser list until every kind label has been visible. */
    private Set<String> collectAllKindLabels() {
        Set<String> seen = new HashSet<>();
        long deadline = SystemClock.uptimeMillis() + 20000;
        do {
            AccessibilityNodeInfo root = activeRoot();
            for (Integer labelRes : KIND_LABELS.values()) {
                String label = context.getString(labelRes);
                if (findNode(root, label, false) != null) seen.add(label);
            }
            if (seen.size() == ThemePackContracts.ICON_KINDS.length) return seen;
            AccessibilityNodeInfo list = findListView(root);
            if (list == null || !list.performAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break;
            instrumentation.waitForIdleSync();
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        return seen;
    }

    // ---------- tests ----------

    @Test
    public void fileTypeChooser_listsLocalizedLabelForEveryKind() throws Exception {
        showEditor(LocalThemes.newDraft("Labels", false));
        openFileTypeChooser();

        // The chooser header carries the variant toggle + explanation hint.
        awaitText(context.getString(R.string.theme_editor_icon_variant_light));
        awaitText(context.getString(R.string.theme_editor_icon_variant_dark));
        awaitText(context.getString(R.string.theme_editor_icon_variants_hint));

        Set<String> seen = collectAllKindLabels();
        assertEquals("Every contract kind must show its localized label",
                ThemePackContracts.ICON_KINDS.length, seen.size());
        for (Map.Entry<String, Integer> kind : KIND_LABELS.entrySet()) {
            assertTrue("Missing label for kind " + kind.getKey(),
                    seen.contains(context.getString(kind.getValue())));
        }
        dismissChooserAndEditor();
    }

    @Test
    public void variantToggle_retargetsRowsBetweenBaseLightAndDark() throws Exception {
        LocalThemes.Draft draft = importedVariantDraft();
        showEditor(draft);
        openFileTypeChooser();

        String folder = context.getString(R.string.folder);
        // Base key has no artwork; only the variants do.
        awaitRowState(R.string.theme_editor_image_none);
        click(awaitText(context.getString(
                R.string.theme_editor_icon_variant_light)));
        awaitRowState(R.string.theme_editor_image_set);
        click(awaitText(context.getString(
                R.string.theme_editor_icon_variant_dark)));
        awaitRowState(R.string.theme_editor_image_set);
        click(awaitText(context.getString(
                R.string.theme_editor_icon_variant_base)));
        awaitRowState(R.string.theme_editor_image_none);
        dismissChooserAndEditor();
    }

    @Test
    public void clearOneVariant_tombstonesItAndRetainsSiblings() throws Exception {
        LocalThemes.Draft draft = importedVariantDraft();
        showEditor(draft);
        openFileTypeChooser();

        // In the Dark variant, Clear on the folder row must remove ONLY
        // folder_dark; the light variant (and the base key) stay intact.
        click(awaitText(context.getString(
                R.string.theme_editor_icon_variant_dark)));
        awaitRowState(R.string.theme_editor_image_set);
        String rowDescription = context.getString(
                R.string.theme_editor_icon_row_description,
                context.getString(R.string.folder),
                context.getString(R.string.theme_editor_image_set));
        AccessibilityNodeInfo clear = findNode(
                awaitDescription(rowDescription),
                context.getString(R.string.theme_editor_clear), false);
        assertNotNull("Folder row must expose a Clear button", clear);
        click(clear);

        assertFalse("Cleared variant must be tombstoned on the draft",
                draft.hasIcon("folder_dark"));
        assertTrue("Sibling variant must survive the clear",
                draft.hasIcon("folder_light"));
        awaitRowState(R.string.theme_editor_image_none);
        click(awaitText(context.getString(
                R.string.theme_editor_icon_variant_light)));
        awaitRowState(R.string.theme_editor_image_set);

        // Done refreshes the editor summary: no base kinds, 1 variant left.
        click(awaitText(context.getString(R.string.theme_editor_done)));
        awaitText(context.getString(R.string.theme_editor_icons_summary_variants,
                0, ThemePackContracts.ICON_KINDS.length, 1));
        dismissEditorOnly();
    }

    @Test
    public void colorEditor_acceptsTransparentAlphaHex() throws Exception {
        LocalThemes.Draft draft = LocalThemes.newDraft("Transparent", false);
        showEditor(draft);

        // First chip strip entry is colorPrimary ("Primary" caption).
        click(awaitText("Primary"));
        awaitText(context.getString(android.R.string.ok));

        AccessibilityNodeInfo hexField = awaitEditText();
        Bundle setText = new Bundle();
        setText.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                "#00FFFFFF");
        assertTrue(hexField.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT, setText));
        instrumentation.waitForIdleSync();

        click(awaitText(context.getString(android.R.string.ok)));
        instrumentation.waitForIdleSync();

        // The fully transparent color landed on the draft and the chip
        // caption/swatch now render its 8-digit hex.
        assertEquals(Integer.valueOf(0x00FFFFFF),
                draft.colors.get("colorPrimary"));
        assertNotNull(awaitText("#00FFFFFF"));
        dismissEditorOnly();
    }

    // ---------- UiAutomation helpers (ScreenOcrCaptureTest pattern) ----------

    private AccessibilityNodeInfo activeRoot() {
        return instrumentation.getUiAutomation().getRootInActiveWindow();
    }

    private AccessibilityNodeInfo awaitText(String text) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            // The focused (topmost) dialog wins so toggles like "Dark" that
            // also exist in the editor window never shadow the chooser's.
            AccessibilityNodeInfo focused = findNode(activeRoot(), text, false);
            if (focused != null) return focused;
            for (AccessibilityWindowInfo window :
                    instrumentation.getUiAutomation().getWindows()) {
                AccessibilityNodeInfo node = findNode(window.getRoot(),
                        text, false);
                if (node != null) return node;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing node with text '" + text + "'");
    }

    private AccessibilityNodeInfo awaitDescription(String description) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            AccessibilityNodeInfo focused = findNode(activeRoot(),
                    description, true);
            if (focused != null) return focused;
            for (AccessibilityWindowInfo window :
                    instrumentation.getUiAutomation().getWindows()) {
                AccessibilityNodeInfo node = findNode(window.getRoot(),
                        description, true);
                if (node != null) return node;
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing node described '" + description + "'");
    }

    private AccessibilityNodeInfo awaitEditText() {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            AccessibilityNodeInfo node = findByClass(activeRoot(),
                    "android.widget.EditText");
            if (node != null) return node;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing hex input field");
    }

    private AccessibilityNodeInfo findListView(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable() && String.valueOf(node.getClassName())
                .contains("ListView")) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo match = findListView(node.getChild(index));
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo findByClass(AccessibilityNodeInfo node,
                                              String className) {
        if (node == null) return null;
        if (className.equals(String.valueOf(node.getClassName()))) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo match = findByClass(node.getChild(index),
                    className);
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node,
                                           String value, boolean description) {
        if (node == null) return null;
        CharSequence current = description
                ? node.getContentDescription() : node.getText();
        if (value.equalsIgnoreCase(current == null ? "" : current.toString())) {
            return node;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo match = findNode(node.getChild(index),
                    value, description);
            if (match != null) return match;
        }
        return null;
    }

    private void click(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        assertNotNull("No clickable ancestor for the target node", node);
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        instrumentation.waitForIdleSync();
    }

    // ---------- fixtures ----------

    /** 16x16 solid PNG bytes. */
    private static byte[] pngBytes(int color) {
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private static String base64(byte[] bytes) {
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }
}
