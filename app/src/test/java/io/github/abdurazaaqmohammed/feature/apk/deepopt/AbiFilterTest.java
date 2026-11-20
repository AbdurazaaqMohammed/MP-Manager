package io.github.abdurazaaqmohammed.feature.apk.deepopt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for AbiFilter architecture discovery and pruning heuristics.
 */
public class AbiFilterTest {

    @Test
    public void testExtractAbi() {
        assertEquals("arm64-v8a", AbiFilter.extractAbi("lib/arm64-v8a/libfoo.so"));
        assertEquals("armeabi-v7a", AbiFilter.extractAbi("lib/armeabi-v7a/libbar.so"));
        assertEquals("x86_64", AbiFilter.extractAbi("lib/x86_64/libbaz.so"));
        assertNull(AbiFilter.extractAbi("assets/lib/something.so"));
        assertNull(AbiFilter.extractAbi("res/raw/sample.bin"));
        assertNull(AbiFilter.extractAbi("classes.dex"));
    }

    @Test
    public void testShouldPruneAbi() {
        Set<String> target = new HashSet<>();
        target.add(AbiFilter.ABI_ARM64_V8A);

        // Retain arm64-v8a
        assertFalse(AbiFilter.shouldPruneAbi("lib/arm64-v8a/libfoo.so", target));

        // Prune armeabi-v7a and x86
        assertTrue(AbiFilter.shouldPruneAbi("lib/armeabi-v7a/libfoo.so", target));
        assertTrue(AbiFilter.shouldPruneAbi("lib/x86/libfoo.so", target));
        assertTrue(AbiFilter.shouldPruneAbi("lib/x86_64/libfoo.so", target));

        // Non-lib files should never be pruned by AbiFilter
        assertFalse(AbiFilter.shouldPruneAbi("AndroidManifest.xml", target));
        assertFalse(AbiFilter.shouldPruneAbi("classes.dex", target));
    }

    @Test
    public void testEmptyOrNullTargetKeepsAll() {
        assertFalse(AbiFilter.shouldPruneAbi("lib/armeabi-v7a/libfoo.so", null));
        assertFalse(AbiFilter.shouldPruneAbi("lib/armeabi-v7a/libfoo.so", Collections.emptySet()));
    }
}
