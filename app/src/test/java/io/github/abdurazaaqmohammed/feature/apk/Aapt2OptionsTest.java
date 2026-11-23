package io.github.abdurazaaqmohammed.feature.apk;

import org.junit.Test;
import static org.junit.Assert.*;

public class Aapt2OptionsTest {
    @Test public void normalizesCommaSeparatedOptions() {
        Aapt2Optimizer.Options options = new Aapt2Optimizer.Options(" xhdpi, xxhdpi ", "fr, b+en+US", "arm64-v8a, x86_64", "23", "34");
        assertEquals("xhdpi,xxhdpi", options.densities);
        assertEquals("fr,b+en+US", options.configurations);
        assertTrue(options.abis.contains("arm64-v8a"));
        assertTrue(options.abis.contains("x86_64"));
        assertEquals(Integer.valueOf(23), options.minSdk);
    }
    @Test public void blanksLeaveEverythingUnchanged() {
        Aapt2Optimizer.Options options = new Aapt2Optimizer.Options("", "", "", "", "");
        assertTrue(options.abis.isEmpty());
        assertNull(options.minSdk);
        assertNull(options.targetSdk);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsEmptyListMember() {
        new Aapt2Optimizer.Options("hdpi,", "", "", "", "");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidSdkRange() {
        new Aapt2Optimizer.Options("", "", "", "30", "23");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsShellSyntax() {
        new Aapt2Optimizer.Options("xhdpi;echo", "", "", "", "");
    }
}
