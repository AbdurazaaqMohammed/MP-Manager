package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;


/** Run with the normally-installed plugin sample and the isolated regression host. */
public class PluginHooksInstrumentedTest {
    private static final String SAMPLE = "com.example.mpextsample";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;

    @Before
    public void setUp() {
        context = instrumentation.getTargetContext();
        assertTrue("Refusing to modify a non-isolated app", context.getPackageName()
                .endsWith(".editorregression"));
        PluginHost.removeTrust(context, SAMPLE);
        HookRegistry.invalidate();
        ExternalActions.clearAllCaches();
    }

    @After
    public void tearDown() {
        PluginHost.removeTrust(context, SAMPLE);
    }

    @Test
    public void hookDiscoveryIsNativeAndHasPackageQualifiedIdentity() {
        List<ExternalActions.Entry> entries = HookRegistry.entries(context,
                PluginContracts.HOOK_TOOLBAR_ACTIONS);
        ExternalActions.Entry sample = findSample(entries);
        assertNotNull("Install samples/plugin-sample before running this test", sample);
        assertEquals(PluginContracts.ACTION_HOOK, sample.action);
        assertEquals(PluginContracts.HOOK_TOOLBAR_ACTIONS, sample.hookId);
        assertTrue(sample.id.startsWith("ext:" + SAMPLE + "/"));
        assertTrue(sample.id.endsWith("@toolbar.actions"));
        assertTrue(HookRegistry.entries(context, "not.a.host.slot").isEmpty());
    }

    @Test
    public void hookIntentCarriesHostCapabilitiesAndOnlyPrimitivePalette() {
        ExternalActions.Entry sample = findSample(HookRegistry.entries(context,
                PluginContracts.HOOK_TOOLBAR_ACTIONS));
        assertNotNull(sample);
        Intent intent = ExternalActions.intent(context, sample, PluginContracts.ACTION_FILE_MENU);
        assertNotNull(intent.getComponent());
        assertEquals(SAMPLE, intent.getComponent().getPackageName());
        assertEquals(PluginContracts.ACTION_HOOK, intent.getAction());
        assertEquals(PluginContracts.HOOK_TOOLBAR_ACTIONS,
                intent.getStringExtra(PluginContracts.EXTRA_HOOK_ID));
        assertEquals(context.getPackageName(), intent.getStringExtra(PluginContracts.EXTRA_HOST_PACKAGE));
        assertEquals(PluginContracts.CONTRACT_VERSION,
                intent.getIntExtra(PluginContracts.EXTRA_CONTRACT_VERSION, -1));
        assertArrayEquals(PluginHooks.actionHooks(),
                intent.getStringArrayExtra(PluginContracts.EXTRA_HOST_HOOKS));
        Bundle palette = intent.getBundleExtra(PluginContracts.EXTRA_THEME_PALETTE);
        assertNotNull(palette);
        for (String token : palette.keySet()) {
            assertNotNull(PluginTheme.canonicalKey(token));
            assertTrue(palette.get(token) instanceof Integer);
        }
        assertNull(intent.getData());
        assertNull(intent.getClipData());
        assertEquals(0, intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
    }

    @Test
    public void automaticEventsRequireSeparateCertificateBoundConsent() {
        String digest = PluginHost.certDigest(context, SAMPLE);
        assertNotNull("Sample must be installed", digest);
        PluginHost.setTrusted(context, SAMPLE, digest, "Sample", true);
        assertTrue(PluginHost.isTrusted(context, SAMPLE));
        assertFalse(PluginHost.eventsEnabled(context, SAMPLE));
        boolean declaresEvents = false;
        for (PluginHost.ExternalPlugin receiver : PluginHost.queryEventReceivers(context, true)) {
            if (SAMPLE.equals(receiver.packageName)) {
                assertTrue(HookRegistry.declaredHooks(receiver).contains(PluginContracts.EVENT_FILE_OPENED));
                declaresEvents = true;
            }
        }
        assertTrue("Sample event manifest must declare its event interests", declaresEvents);
        PluginHost.setEventsEnabled(context, SAMPLE, true);
        assertTrue(PluginHost.eventsEnabled(context, SAMPLE));
        assertTrue(PluginHost.eventAllowed(context, SAMPLE, PluginContracts.EVENT_FILE_OPENED));
        PluginHost.setTrusted(context, SAMPLE, digest, "Sample", false);
        assertFalse(PluginHost.eventsEnabled(context, SAMPLE));
        assertNull(findSample(HookRegistry.entries(context, PluginContracts.HOOK_TOOLBAR_ACTIONS)));
        assertNotNull(findSample(HookRegistry.packageEntries(context, SAMPLE)));
        PluginHost.setTrusted(context, SAMPLE, "changed-certificate", "Sample", true);
        assertFalse(PluginHost.eventsEnabled(context, SAMPLE));
    }

    @Test
    public void anUpdateCannotExpandTheDisclosedEventList() {
        String digest = PluginHost.certDigest(context, SAMPLE);
        assertNotNull(digest);
        PluginHost.setTrusted(context, SAMPLE, digest, "Sample", true);
        PluginHost.setEventsEnabled(context, SAMPLE, true,
                java.util.Collections.singletonList(PluginContracts.EVENT_THEME_CHANGED));
        assertTrue(PluginHost.eventAllowed(context, SAMPLE, PluginContracts.EVENT_THEME_CHANGED));
        assertFalse(PluginHost.eventAllowed(context, SAMPLE, PluginContracts.EVENT_FILE_OPENED));
        assertFalse(PluginHost.eventAllowed(context, SAMPLE, PluginContracts.EVENT_APP_LAUNCH));
    }

    @Test
    public void settingMigrationPreservesPluginValueWithoutTouchingHostPreferences() {
        String digest = PluginHost.certDigest(context, SAMPLE);
        assertNotNull(digest);
        PluginHost.setTrusted(context, SAMPLE, digest, "Sample", true);
        ExternalActions.Entry setting = findSample(ExternalActions.settingEntries(context));
        assertNotNull(setting);
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "plugin_setting_migration_test", Context.MODE_PRIVATE);
        prefs.edit().clear().putBoolean("plugin.sample.demo_enabled", true)
                .putString("theme_plugin_id", "host-owned").commit();
        ExternalActions.migrateSetting(context, setting, prefs);
        assertTrue(prefs.getBoolean(ExternalActions.settingKey(setting), false));
        assertFalse(prefs.contains("plugin.sample.demo_enabled"));
        assertEquals("host-owned", prefs.getString("theme_plugin_id", null));
        prefs.edit().clear().commit();
    }

    @Test
    public void hidingAndUninstallCleanupDoNotAffectAnotherPackage() {
        ExternalActions.Entry sample = findSample(HookRegistry.entries(context,
                PluginContracts.HOOK_TOOLBAR_ACTIONS));
        assertNotNull(sample);
        HookOrder.setHidden(context, sample.hookId, sample.id, true);
        assertTrue(HookOrder.isHidden(context, sample.hookId, sample.id));
        assertNull(findSample(HookRegistry.entries(context, sample.hookId)));
        String other = "ext:com.example.other/action@toolbar.actions";
        HookOrder.setHidden(context, sample.hookId, other, true);
        PluginHost.removeTrust(context, SAMPLE);
        assertFalse(HookOrder.isHidden(context, sample.hookId, sample.id));
        assertTrue(HookOrder.isHidden(context, sample.hookId, other));
        HookOrder.removePackage(context, "com.example.other");
    }

    private static ExternalActions.Entry findSample(List<ExternalActions.Entry> entries) {
        for (ExternalActions.Entry entry : entries) {
            if (SAMPLE.equals(entry.plugin.packageName)) return entry;
        }
        return null;
    }
}
