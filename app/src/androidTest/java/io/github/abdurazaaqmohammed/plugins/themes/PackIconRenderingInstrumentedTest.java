package io.github.abdurazaaqmohammed.plugins.themes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.abdurazaaqmohammed.data.prefs.UiPrefs;
import io.github.abdurazaaqmohammed.ui.common.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.ui.main.FileIconArt;
import io.github.abdurazaaqmohammed.ui.main.FileIconSpec;

/**
 * Selective plugin icon override, end to end on a device: the installed v2
 * theme sample's {@code icons} column is ingested, its folder/APK PNGs become
 * the rendered icons through {@link FileIconSpec}, types without a key keep
 * the built-in rendering, and the Appearance 3D toggle keeps controlling the
 * built-in path (and its persisted value is never touched by a pack import).
 * Deselecting the pack restores the built-in icons.
 *
 * <p>Render discriminators (mirroring FileIconArt/FileIconSpec internals):
 * flat = {@link LayerDrawable}; 3D = {@link BitmapDrawable} holding a
 * FileIconArt cache bitmap (96..192 px); pack override = BitmapDrawable
 * holding exactly the {@link FileIconSpec#packBitmap} instance (the raw
 * staged PNG, 48 px for the sample).
 */
public class PackIconRenderingInstrumentedTest {
    private final android.app.Instrumentation instrumentation =
            InstrumentationRegistry.getInstrumentation();
    private Context context;
    private boolean previousUse3d;

    @Before
    public void setUp() {
        context = instrumentation.getTargetContext();
        assertTrue("Refusing a non-isolated package", context.getPackageName()
                .endsWith(".editorregression"));
        previousUse3d = UiPrefs.use3dIcons(context);
        UiPrefs.setUse3dIcons(context, false);
        ThemePacks.deactivate(context);
        instrumentation.runOnMainSync(() -> ThemeRegistry.reload(context));
    }

    @After
    public void tearDown() {
        ThemePacks.deactivate(context);
        instrumentation.runOnMainSync(() -> ThemeRegistry.reload(context));
        UiPrefs.setUse3dIcons(context, previousUse3d);
    }

    private ThemePacks.Snapshot importSample() {
        ThemePacks.Entry sample = null;
        for (ThemePacks.Entry entry : ThemePacks.discover(context)) {
            if ("com.example.mpthemesample".equals(entry.packageName)) sample = entry;
        }
        assertNotNull("Install samples/theme-sample before running this test", sample);
        assertTrue(ThemePacks.importAndSelect(context, sample));
        ThemePacks.Snapshot pack = ThemePacks.active(context);
        assertNotNull(pack);
        return pack;
    }

    private static Bitmap renderedBitmap(Drawable drawable) {
        assertTrue("Expected a BitmapDrawable, got " + drawable.getClass(),
                drawable instanceof BitmapDrawable);
        return ((BitmapDrawable) drawable).getBitmap();
    }

    @Test
    public void samplePackOverridesFolderAndApkIcons_withStagedPngs() {
        ThemePacks.Snapshot pack = importSample();
        // The sample ships folder, folder_dark and apk icon endpoints; both
        // keys must reach the private store and the shared bitmap cache.
        assertNotNull("Sample folder icon must be staged into the private store",
                pack.iconFiles.get("folder"));
        assertTrue(pack.iconFiles.get("folder").isFile());
        assertNotNull("Sample apk icon must be staged into the private store",
                pack.iconFiles.get("apk"));

        instrumentation.runOnMainSync(() -> {
            Bitmap folderPack = FileIconSpec.packBitmap(context, "folder");
            assertNotNull("packBitmap must decode the staged folder PNG", folderPack);
            assertTrue(folderPack.getWidth() > 0 && folderPack.getHeight() > 0);
            assertNotNull(FileIconSpec.packBitmap(context, "apk"));

            // Overridden specs render exactly the pack bitmap instance.
            assertSame("Overridden folder icon must render the pack bitmap",
                    folderPack, renderedBitmap(FileIconSpec.FOLDER.drawable(context)));
            assertSame("Overridden apk icon must render the pack bitmap",
                    FileIconSpec.packBitmap(context, "apk"),
                    renderedBitmap(FileIconSpec.APK.drawable(context)));
        });
    }

    @Test
    public void builtInRenderingKeepsFlatAnd3DToggleForNonOverriddenKeys() {
        importSample();
        instrumentation.runOnMainSync(() -> {
            // Sample overrides folder/apk only: video stays built-in, and the
            // 3D toggle still switches the built-in rendering.
            UiPrefs.setUse3dIcons(context, false);
            Drawable videoFlat = FileIconSpec.VIDEO.drawable(context);
            assertTrue("Toggle off must render the classic flat tile",
                    videoFlat instanceof LayerDrawable);

            UiPrefs.setUse3dIcons(context, true);
            FileIconArt.clearCache();
            Bitmap video3d = renderedBitmap(FileIconSpec.VIDEO.drawable(context));
            assertTrue("3D artwork is host-rendered, not the 48px pack PNG",
                    Math.max(video3d.getWidth(), video3d.getHeight()) >= 96);
            assertTrue("3D artwork is host-rendered, not the 48px pack PNG",
                    Math.max(video3d.getWidth(), video3d.getHeight()) <= 192);

            UiPrefs.setUse3dIcons(context, false);
            FileIconArt.clearCache();
            assertTrue(FileIconSpec.VIDEO.drawable(context) instanceof LayerDrawable);
        });
    }

    @Test
    public void packOverrideWinsOverThe3DToggle_andImportNeverTouchesTheTogglePref() {
        importSample();
        // Snapshot right after the import: proves the import itself never
        // touched the 3D preference (the test's own toggles come later).
        boolean afterImport = UiPrefs.use3dIcons(context);
        assertEquals("Pack import must not change the 3D preference",
                previousUse3d, afterImport);
        try {
            instrumentation.runOnMainSync(() -> {
                UiPrefs.setUse3dIcons(context, true);
                FileIconArt.clearCache();
                // With the toggle ON, an overridden key still renders the raw
                // pack PNG (the pack wins), not the host 3D artwork.
                Bitmap folder = renderedBitmap(FileIconSpec.FOLDER.drawable(context));
                assertSame("Pack override must win over the 3D rendering",
                        FileIconSpec.packBitmap(context, "folder"), folder);
            });
        } finally {
            UiPrefs.setUse3dIcons(context, previousUse3d);
        }
    }

    @Test
    public void deselectingPackRestoresBuiltInIconsImmediately() {
        importSample();
        assertNotNull(FileIconSpec.packBitmap(context, "folder"));
        instrumentation.runOnMainSync(() ->
                assertTrue(FileIconSpec.FOLDER.drawable(context)
                        instanceof BitmapDrawable));
        ThemePacks.deactivate(context);
        instrumentation.runOnMainSync(() -> ThemeRegistry.reload(context));
        assertNull("No pack -> no override", FileIconSpec.packBitmap(context, "folder"));
        instrumentation.runOnMainSync(() -> {
            UiPrefs.setUse3dIcons(context, false);
            assertTrue("Deselect must restore the built-in flat rendering",
                    FileIconSpec.FOLDER.drawable(context) instanceof LayerDrawable);
        });
    }

    @Test
    public void parentRowKeepsBuiltInIconWhenPackHasNoParentFolderKey() {
        importSample();
        assertNull(FileIconSpec.packBitmap(context, "parent_folder"));
        instrumentation.runOnMainSync(() -> {
            UiPrefs.setUse3dIcons(context, false);
            assertTrue("Un-overridden parent row must stay built-in flat",
                    FileIconSpec.parentDrawable(context) instanceof LayerDrawable);
        });
    }
}
