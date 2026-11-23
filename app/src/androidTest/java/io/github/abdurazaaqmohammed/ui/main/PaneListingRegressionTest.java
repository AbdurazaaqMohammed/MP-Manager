package io.github.abdurazaaqmohammed.ui.main;

import static org.junit.Assert.*;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.privilege.internal.ShizukuFile;

/** Exercises the pane with realistic ShizukuFile listing results, without requiring Shizuku. */
public class PaneListingRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity main;
    private File dir;

    @Before public void setUp() throws Exception {
        Application app = (Application) instrumentation.getTargetContext().getApplicationContext();
        // Prevent the app's all-files-access Settings screen from obscuring the
        // activity between tests. This affects only the isolated test package.
        String permissionCommand = Build.VERSION.SDK_INT >= 30
                ? "appops set " + app.getPackageName() + " MANAGE_EXTERNAL_STORAGE allow"
                : "pm grant " + app.getPackageName() + " android.permission.WRITE_EXTERNAL_STORAGE";
        try (ParcelFileDescriptor.AutoCloseInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(permissionCommand))) {
            byte[] buffer = new byte[256];
            while (stream.read(buffer) != -1) { /* wait for the shell command */ }
        }
        dir = new File(app.getCacheDir(), "pane-listing-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        app.startActivity(new Intent(app, MainActivity.class).putExtra("locatePath", dir.getAbsolutePath())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main = (MainActivity) instrumentation.waitForMonitorWithTimeout(monitor, 120000);
        instrumentation.removeMonitor(monitor);
        assertNotNull(main);
        instrumentation.waitForIdleSync();
    }

    @After public void tearDown() {
        if (main != null) instrumentation.runOnMainSync(() -> main.finish());
    }

    private static class ListedFolder extends File {
        final File[] children;
        final CountDownLatch entered;
        final CountDownLatch release;
        ListedFolder(File parent, String name, File[] children, CountDownLatch entered, CountDownLatch release) {
            super(parent, name);
            this.children = children;
            this.entered = entered;
            this.release = release;
        }
        @Override public boolean isDirectory() { return true; }
        @Override public File[] listFiles(FileFilter filter) {
            if (entered != null) entered.countDown();
            if (release != null) {
                try { if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return children;
        }
    }

    private void assertPane(boolean pane1, File folder, String expected) {
        assertEquals(folder.getAbsolutePath(), (pane1 ? main.pane1Folder : main.pane2Folder).getAbsolutePath());
        RecyclerView pane = main.findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) pane.getAdapter();
        assertNotNull(adapter);
        File[] shown = adapter.getShownFiles();
        assertEquals(2, shown.length);
        assertEquals(expected, shown[1].getName());
        assertTrue(shown[1] instanceof ShizukuFile);
    }

    @Test public void shizukuEntriesBindOnFirstNavigationInEitherPane() throws Exception {
        for (boolean pane1 : new boolean[]{true, false}) {
            File target = new File(dir, pane1 ? "one" : "two");
            File[] listing = new ShizukuFile[]{new ShizukuFile(new File(target, "Telegram").getAbsolutePath(), true, 0)};
            ListedFolder folder = new ListedFolder(dir, target.getName(), listing, null, null);
            CountDownLatch bound = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> main.loadFolderInPaneAsync(folder, pane1, true, bound::countDown));
            assertTrue("first listing was not bound", bound.await(30, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> assertPane(pane1, folder, "Telegram"));
        }
    }

    @Test public void slowOldListingCannotReplaceNewNavigation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), bound = new CountDownLatch(1);
        AtomicBoolean oldBound = new AtomicBoolean(false);
        File firstPath = new File(dir, "slow");
        ListedFolder slow = new ListedFolder(dir, "slow", new ShizukuFile[]{
                new ShizukuFile(new File(firstPath, "old").getAbsolutePath(), true, 0)}, entered, release);
        File nextPath = new File(dir, "new");
        ListedFolder next = new ListedFolder(dir, "new", new ShizukuFile[]{
                new ShizukuFile(new File(nextPath, "new-child").getAbsolutePath(), true, 0)}, null, null);
        instrumentation.runOnMainSync(() -> main.loadFolderInPaneAsync(slow, true, true, () -> oldBound.set(true)));
        assertTrue(entered.await(20, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> main.loadFolderInPaneAsync(next, true, true, bound::countDown));
        release.countDown();
        assertTrue(bound.await(30, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            assertFalse(oldBound.get());
            assertPane(true, next, "new-child");
        });
    }
}
