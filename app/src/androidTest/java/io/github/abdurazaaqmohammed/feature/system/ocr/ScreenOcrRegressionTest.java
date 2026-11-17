package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.system.ToolLauncher;
import io.github.abdurazaaqmohammed.feature.system.ToolRegistry;
import io.github.abdurazaaqmohammed.feature.system.ToolsHubActivity;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrResult;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrToolPlugin;
import io.github.abdurazaaqmohammed.plugins.packs.PackCatalog;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.abdurazaaqmohammed.plugins.packs.PackManager;
import io.github.abdurazaaqmohammed.ui.main.SidebarAdapter;

import static org.junit.Assert.*;

/** Only run against -PeditorRegressionBuild; never replace or modify the user's installed app. */
public class ScreenOcrRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private final List<Activity> activities = new ArrayList<>();

    @Before public void prepare() {
        context = instrumentation.getTargetContext();
        assertTrue(context.getPackageName().endsWith(".editorregression"));
    }

    @After public void cleanup() {
        OcrModels.select(context, OcrModels.find("en"));
        instrumentation.runOnMainSync(() -> {
            OcrSession.clear();
            for (Activity activity : activities) activity.finish();
        });
    }

    @Test public void sidebarAndHubShareEveryBuiltInToolAndShortcutDestination() {
        for (String section : new String[]{"tools", "plugins"}) {
            for (String id : ToolRegistry.sidebarIds(section)) {
                if (!"tools".equals(id)) assertNotNull("Missing hub entry " + id, ToolRegistry.findById(context, id));
                assertNotNull("Missing shortcut destination " + id, ToolLauncher.intentFor(context, id));
            }
        }
        assertEquals("wifi", ToolRegistry.canonicalId("wifimanager"));
        assertEquals("set_edit", ToolRegistry.canonicalId("setedit"));
        instrumentation.runOnMainSync(() -> {
            SidebarAdapter sidebar = new SidebarAdapter(context, null);
            sidebar.setToolOrder(List.of("ftp_client", "webdav_client", "smb_client", "documents"));
            sidebar.rebuild();
            assertEquals(1, Collections.frequency(sidebar.getToolOrder(), "network_client"));
            assertTrue(sidebar.getToolOrder().contains("screen_ocr"));
        });
    }

    @Test public void searchFiltersPacksAsWellAsBuiltInTools() {
        ToolsHubActivity hub = (ToolsHubActivity) instrumentation.startActivitySync(new Intent(context, ToolsHubActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activities.add(hub);
        instrumentation.runOnMainSync(() -> ((EditText) hub.findViewById(R.id.tools_search)).setText("no-match-ocr-regression"));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            assertEquals(View.VISIBLE, hub.findViewById(R.id.tools_empty).getVisibility());
            assertEquals(0, ((RecyclerView) hub.findViewById(R.id.tools_grid)).getAdapter().getItemCount());
            ((EditText) hub.findViewById(R.id.tools_search)).setText("copy text");
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertEquals(View.GONE, hub.findViewById(R.id.tools_empty).getVisibility()));
    }

    @Test public void allBuiltInCardsOfferShortcutOnLongPress() {
        ToolsHubActivity hub = (ToolsHubActivity) instrumentation.startActivitySync(new Intent(context, ToolsHubActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activities.add(hub);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            ((EditText) hub.findViewById(R.id.tools_search)).setText("copy text");
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            RecyclerView grid = hub.findViewById(R.id.tools_grid);
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(1);
            assertNotNull(holder);
            assertTrue(holder.itemView.performLongClick());
        });
        instrumentation.waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean visible = false;
        do {
            AccessibilityNodeInfo node = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (node != null && !node.findAccessibilityNodeInfosByText(
                    context.getString(R.string.plugin_create_shortcut)).isEmpty()) { visible = true; break; }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Long-press should display Create shortcut", visible);
    }

    @Test public void runtimeLoadsFromDownloadedApkAndReadsLightAndDarkTextOffline() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 24);
        installFixturePackAndModels();
        assertTrue("Pack did not expose the OCR engine", OcrSession.ensurePlugin(context) instanceof OcrToolPlugin);
        for (boolean dark : new boolean[]{false, true}) {
            Bitmap bitmap = textFixture(dark);
            instrumentation.runOnMainSync(() -> {
                OcrSession.setImage(bitmap);
                OcrSession.recognize(context, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()));
            });
            awaitRecognition();
            AtomicReference<OcrResult> result = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                assertNull("Recognition error", OcrSession.error());
                result.set(OcrSession.result());
            });
            assertNotNull(result.get());
            String text = result.get().text();
            assertTrue("Expected Hello screen, found " + text, text.contains("Hello screen"));
            assertTrue("Expected Copy text, found " + text, text.contains("Copy text"));
            assertTrue(result.get().lines.stream().anyMatch(line -> line.words().size() >= 2));
            Bundle timing = new Bundle();
            timing.putString("ocr_fixture", (dark ? "dark" : "light") + " " + result.get().elapsedMillis + "ms: " + text);
            instrumentation.sendStatus(0, timing);
        }
    }

    @Test public void arabicTextIsReturnedInLogicalOrderForCopying() throws Exception {
        installFixturePackAndModels();
        Assume.assumeTrue("Stage the pinned Arabic model for RTL checks",
                OcrModels.ready(context, OcrModels.find("arabic")));
        OcrModels.select(context, OcrModels.find("arabic"));
        Bitmap bitmap = Bitmap.createBitmap(900, 300, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(64);
        canvas.drawText("مرحبا بالعالم", 150, 120, paint);
        instrumentation.runOnMainSync(() -> {
            OcrSession.setImage(bitmap);
            OcrSession.recognize(context, new Rect(0, 0, 900, 300));
        });
        awaitRecognition();
        AtomicReference<String> text = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            assertNull(OcrSession.error());
            text.set(OcrSession.result().text());
        });
        Bundle result = new Bundle();
        result.putString("ocr_arabic", text.get());
        instrumentation.sendStatus(0, result);
        assertTrue("Unexpected Arabic output: " + text.get(), text.get().contains("مرحبا"));
        OcrModels.select(context, OcrModels.find("en"));
    }

    @Test public void resultUiDoesNotPersistTextAndCropCoordinatesStayInBitmapSpace() throws Exception {
        installFixturePackAndModels();
        Bitmap bitmap = textFixture(false);
        instrumentation.runOnMainSync(() -> OcrSession.setImage(bitmap));
        ScreenOcrActivity activity = (ScreenOcrActivity) instrumentation.startActivitySync(
                new Intent(context, ScreenOcrActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        activities.add(activity);
        instrumentation.runOnMainSync(() -> {
            OcrSelectionView view = findPreview(activity.findViewById(R.id.ocr_content));
            assertNotNull(view);
            assertEquals(new Rect(0, 0, 900, 500), view.region());
            OcrSession.recognize(context, new Rect(10, 10, 880, 450));
        });
        awaitRecognition();
        instrumentation.runOnMainSync(() -> {
            assertNull(OcrSession.error());
            assertTrue(OcrSession.result().lines.get(0).box().left() > 10);
            EditText text = activity.findViewById(R.id.ocr_result_text);
            assertNotNull(text);
            assertFalse(text.isSaveEnabled());
            assertTrue(text.getText().toString().contains("Hello screen"));
            OcrSelectionView preview = findPreview(activity.findViewById(R.id.ocr_content));
            OcrResult.Word word = OcrSession.result().lines.get(0).words().get(0);
            float scale = Math.min(preview.getWidth() / (float) bitmap.getWidth(), preview.getHeight() / (float) bitmap.getHeight());
            float x = (preview.getWidth() - bitmap.getWidth() * scale) / 2 + word.box().centerX() * scale;
            float y = (preview.getHeight() - bitmap.getHeight() * scale) / 2 + word.box().centerY() * scale;
            long time = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, x, y, 0);
            try {
                preview.dispatchTouchEvent(down);
                preview.dispatchTouchEvent(up);
            } finally { down.recycle(); up.recycle(); }
            assertEquals(word.text(), text.getText().toString());
        });
    }

    private void installFixturePackAndModels() throws Exception {
        File packApk = new File(context.getFilesDir(), "ocr-test-pack.apk");
        Assume.assumeTrue("Stage ocr-test-pack.apk before running inference tests", packApk.isFile());
        PackDescriptor pack = PackCatalog.byId(PackCatalog.load(context), "ocr");
        assertNotNull(pack);
        if (!PackManager.isInstalled(context, "ocr")) {
            assertNull(PackManager.installFromFile(context, pack, packApk));
        }
        assertTrue("Installing a picked APK must not delete it", packApk.isFile());
        Assume.assumeTrue("Stage both pinned models before running inference tests", OcrModels.ready(context, OcrModels.find("en")));
        OcrModels.select(context, OcrModels.find("en"));
    }

    private Bitmap textFixture(boolean dark) {
        Bitmap bitmap = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(dark ? Color.rgb(22, 25, 30) : Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(dark ? Color.WHITE : Color.BLACK);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(56);
        canvas.drawText("Hello screen", 50, 110, paint);
        paint.setTextSize(38);
        canvas.drawText("Copy text from any app", 50, 210, paint);
        canvas.drawText("Power users 12345", 50, 310, paint);
        return bitmap;
    }

    private void awaitRecognition() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 45000;
        AtomicReference<Boolean> busy = new AtomicReference<>(true);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> busy.set(OcrSession.busy()));
            if (!busy.get()) return;
            SystemClock.sleep(50);
        }
        fail("OCR recognition timed out");
    }

    private OcrSelectionView findPreview(View view) {
        if (view instanceof OcrSelectionView preview) return preview;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            OcrSelectionView found = findPreview(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
