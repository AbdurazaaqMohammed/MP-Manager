/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveContentLayoutResolverTest {
    @Test fun contentMetricsExpandWithTheCurrentWindowClass() {
        val compact = AdaptiveContentLayoutResolver.resolve(WindowAdaptiveInfo(400f, 800f))
        val medium = AdaptiveContentLayoutResolver.resolve(WindowAdaptiveInfo(700f, 900f))
        val expanded = AdaptiveContentLayoutResolver.resolve(WindowAdaptiveInfo(1000f, 800f))

        assertEquals(12f, compact.horizontalPaddingDp)
        assertEquals(24f, medium.horizontalPaddingDp)
        assertEquals(32f, expanded.horizontalPaddingDp)
        assertEquals(1, compact.cardColumns)
        assertEquals(1, medium.cardColumns)
        assertEquals(2, expanded.cardColumns)
    }

    @Test fun contentWidthAndDialogWidthAreBoundedByTheAvailableWindow() {
        val info = WindowAdaptiveInfo(1600f, 1000f, density = 2f)
        val spec = AdaptiveContentLayoutResolver.resolve(info)
        val dialogWidth = AdaptiveContentLayoutResolver.resolveDialogWidthPx(
            windowWidthPx = 3200,
            density = 2f,
            window = info
        )
        assertEquals(1040f, spec.maxContentWidthDp)
        assertTrue(dialogWidth <= 2080)
        assertTrue(dialogWidth > 0)
    }

    @Test fun narrowSplitWindowNeverProducesDialogWiderThanItsWindow() {
        val info = WindowAdaptiveInfo(520f, 800f, density = 2f)
        val width = AdaptiveContentLayoutResolver.resolveDialogWidthPx(
            windowWidthPx = 1040,
            density = 2f,
            window = info
        )
        assertTrue(width <= 1040)
        assertEquals(998, width)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDialogDensity() {
        AdaptiveContentLayoutResolver.resolveDialogWidthPx(100, 0f, WindowAdaptiveInfo(400f, 800f))
    }
}
