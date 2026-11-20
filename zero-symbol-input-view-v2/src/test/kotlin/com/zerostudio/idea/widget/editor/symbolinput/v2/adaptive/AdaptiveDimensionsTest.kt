/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveDimensionsTest {
    @Test fun scalesDecorativeDimensionsByWindowClass() {
        assertEquals(96f, AdaptiveDimensions(WindowAdaptiveInfo(400f, 800f)).dp(100f), 0.001f)
        assertEquals(100f, AdaptiveDimensions(WindowAdaptiveInfo(700f, 800f)).dp(100f), 0.001f)
        assertEquals(106f, AdaptiveDimensions(WindowAdaptiveInfo(1000f, 800f)).dp(100f), 0.001f)
    }

    @Test fun preservesMinimumTouchTarget() {
        val dimensions = AdaptiveDimensions(WindowAdaptiveInfo(400f, 800f))
        assertEquals(48f, dimensions.touchTargetDp(40f), 0.001f)
        assertEquals(48f, dimensions.touchTargetDp(48f), 0.001f)
    }

    @Test fun convertsScaledDimensionsToPixels() {
        val dimensions = AdaptiveDimensions(WindowAdaptiveInfo(1000f, 800f, density = 2f))
        assertEquals(212, dimensions.px(100f))
    }
}
