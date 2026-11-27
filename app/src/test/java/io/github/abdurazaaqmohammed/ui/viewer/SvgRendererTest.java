package io.github.abdurazaaqmohammed.ui.viewer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.abdurazaaqmohammed.util.io.FileUtils;

/** Verifies SVG/SVGZ detection, extension registration, and bounded target dimension scaling. */
public class SvgRendererTest {

    @Test
    public void recognizesSvgAndSvgzExtensionsCaseInsensitively() {
        assertTrue(SvgRenderer.isSvgName("/icons/app_icon.svg"));
        assertTrue(SvgRenderer.isSvgName("/icons/LOGO.SVG"));
        assertTrue(SvgRenderer.isSvgName("vector.svgz"));
        assertTrue(FileUtils.isImageFile("sample.svg"));
        assertTrue(FileUtils.isImageFile("sample.svgz"));
        assertFalse(SvgRenderer.isSvgName("/icons/app_icon.png"));
        assertFalse(SvgRenderer.isSvgName(null));
    }

    @Test
    public void scalesSmallAndLargeSvgBoundsCorrectly() {
        // Small 24x24 icon upscaled cleanly to min viewer edge (1024)
        assertArrayEquals(new int[]{1024, 1024},
                SvgRenderer.computeTargetSize(24f, 24f, null, 1024, 2048));

        // Rectangular 48x24 upscaled proportionally to 1024x512
        assertArrayEquals(new int[]{1024, 512},
                SvgRenderer.computeTargetSize(48f, 24f, null, 1024, 2048));

        // Giant vector canvas downscaled to thumbnail cap (160)
        assertArrayEquals(new int[]{160, 80},
                SvgRenderer.computeTargetSize(4000f, 2000f, null, 160, 160));
    }

    @Test
    public void fallsBackToDefaultSizeWhenBoundsMissing() {
        assertArrayEquals(new int[]{160, 160},
                SvgRenderer.computeTargetSize(-1f, -1f, null, 160, 160));
    }
}
