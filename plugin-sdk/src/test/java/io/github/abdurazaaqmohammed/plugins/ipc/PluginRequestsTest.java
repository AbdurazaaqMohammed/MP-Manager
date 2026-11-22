package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure JUnit4 tests for the wire bounds and result helpers. */
public class PluginRequestsTest {

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }

    @Test
    public void payloadBounds() {
        // Payloads are Bundles on the wire; the byte bound applies to the
        // marshalled size the host measures.
        assertTrue(PluginRequests.isPayloadSizeOk((byte[]) null));
        assertTrue(PluginRequests.isPayloadSizeOk(new byte[PluginRequests.MAX_HOOK_PAYLOAD_BYTES]));
        assertFalse(PluginRequests.isPayloadSizeOk(new byte[PluginRequests.MAX_HOOK_PAYLOAD_BYTES + 1]));
    }

    @Test
    public void primitivePayloadValues() {
        assertTrue(PluginRequests.isPrimitiveValueOk("notes.txt"));
        assertTrue(PluginRequests.isPrimitiveValueOk("text/plain"));
        assertTrue(PluginRequests.isPrimitiveValueOk(42));
        assertTrue(PluginRequests.isPrimitiveValueOk(1.5d));
        assertTrue(PluginRequests.isPrimitiveValueOk(Boolean.TRUE));
        assertFalse(PluginRequests.isPrimitiveValueOk(null));
        assertFalse(PluginRequests.isPrimitiveValueOk(new Object()));
        assertFalse(PluginRequests.isPrimitiveValueOk(new int[]{1}));
    }

    @Test
    public void wireBoundsMatchContract() {
        assertEquals(16384, PluginRequests.MAX_HOOK_PAYLOAD_BYTES);
        assertEquals(2048, PluginRequests.MAX_META_VALUE_CHARS);
        assertEquals(32, PluginRequests.MAX_DECLARED_HOOKS);
        assertEquals(128, PluginRequests.MAX_ID_CHARS);
        assertEquals(128, PluginRequests.MAX_LABEL_CHARS);
    }

    @Test
    public void metaValueBounds() {
        assertTrue(PluginRequests.isMetaValueOk(null));
        assertTrue(PluginRequests.isMetaValueOk(""));
        assertTrue(PluginRequests.isMetaValueOk(repeat("h", PluginRequests.MAX_META_VALUE_CHARS)));
        assertFalse(PluginRequests.isMetaValueOk(
                repeat("h", PluginRequests.MAX_META_VALUE_CHARS + 1)));
    }

    @Test
    public void idBounds() {
        assertTrue(PluginRequests.isIdOk("sample.hooks"));
        assertTrue(PluginRequests.isIdOk(repeat("i", PluginRequests.MAX_ID_CHARS)));
        assertFalse(PluginRequests.isIdOk(null));
        assertFalse(PluginRequests.isIdOk(""));
        assertFalse(PluginRequests.isIdOk("a/b"));
        assertFalse(PluginRequests.isIdOk("a@b"));
        assertFalse(PluginRequests.isIdOk("a\\b"));
        assertFalse(PluginRequests.isIdOk("a\nb"));
        assertFalse(PluginRequests.isIdOk(repeat("i", PluginRequests.MAX_ID_CHARS + 1)));
    }

    @Test
    public void labelBounds() {
        assertTrue(PluginRequests.isLabelOk(null));
        assertTrue(PluginRequests.isLabelOk(repeat("l", PluginRequests.MAX_LABEL_CHARS)));
        assertFalse(PluginRequests.isLabelOk(repeat("l", PluginRequests.MAX_LABEL_CHARS + 1)));
    }

    @Test
    public void resultCodeHelpers() {
        assertTrue(PluginRequests.isResultOk(-1));
        assertFalse(PluginRequests.isResultOk(0));
        assertFalse(PluginRequests.isResultOk(1));
    }
}
