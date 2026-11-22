package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Exercises real explicit event delivery to the sample's separate process. */
public class PluginEventsInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private static final String SAMPLE = "com.example.mpextsample";

    @Before
    public void setUp() {
        context = instrumentation.getTargetContext();
        assertTrue(context.getPackageName().endsWith(".editorregression"));
        PluginHost.removeTrust(context, SAMPLE);
        String digest = PluginHost.certDigest(context, SAMPLE);
        assertNotNull("Install the plugin sample first", digest);
        PluginHost.setTrusted(context, SAMPLE, digest, "Sample", true);
    }

    @After
    public void tearDown() {
        PluginHost.removeTrust(context, SAMPLE);
    }

    @Test
    public void fileEventDeliversOnlyDisplayMetadataAfterConsent() throws Exception {
        String uniqueName = "plugin-event-" + SystemClock.uptimeMillis() + ".txt";
        PluginHost.setEventsEnabled(context, SAMPLE, true,
                java.util.Collections.singletonList(PluginContracts.EVENT_FILE_OPENED));
        assertTrue(PluginHost.eventAllowed(context, SAMPLE, PluginContracts.EVENT_FILE_OPENED));
        PluginEvents.fileOpened(context, "/do-not-disclose/" + uniqueName, "text/plain");
        String logs = "";
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            logs = shell("logcat -d -s MpSamplePlugin:I '*:S'");
            if (logs.contains(uniqueName)) break;
            SystemClock.sleep(100);
        }
        assertTrue("Sample receiver should see the explicit consented event: " + logs,
                logs.contains(uniqueName));
        assertFalse(logs.contains("/do-not-disclose/" + uniqueName));
        PluginHost.setEventsEnabled(context, SAMPLE, false);
        String disabledName = "disabled-" + uniqueName;
        PluginEvents.fileOpened(context, disabledName, "text/plain");
        SystemClock.sleep(500);
        assertFalse(shell("logcat -d -s MpSamplePlugin:I '*:S'").contains(disabledName));
    }

    private String shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = instrumentation.getUiAutomation()
                .executeShellCommand(command);
             InputStream in = new FileInputStream(descriptor.getFileDescriptor())) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
}
