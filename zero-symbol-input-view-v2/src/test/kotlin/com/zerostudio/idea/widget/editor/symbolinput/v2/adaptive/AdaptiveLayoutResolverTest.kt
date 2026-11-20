/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutResolverTest {
    @Test fun listDetailUsesSinglePaneUntilExpandedWidth() {
        assertEquals(
            PaneArrangement.SINGLE,
            AdaptiveLayoutResolver.resolve(WindowAdaptiveInfo(700f, 900f), CanonicalLayoutType.LIST_DETAIL).arrangement
        )
        assertEquals(
            PaneArrangement.SIDE_BY_SIDE,
            AdaptiveLayoutResolver.resolve(WindowAdaptiveInfo(840f, 900f), CanonicalLayoutType.LIST_DETAIL).arrangement
        )
    }

    @Test fun feedColumnsFollowAvailableWidthAndRemainBounded() {
        assertEquals(1, AdaptiveLayoutResolver.resolve(WindowAdaptiveInfo(500f, 900f), CanonicalLayoutType.FEED).feedColumnCount)
        assertEquals(2, AdaptiveLayoutResolver.resolve(WindowAdaptiveInfo(600f, 900f), CanonicalLayoutType.FEED).feedColumnCount)
        assertEquals(4, AdaptiveLayoutResolver.resolve(WindowAdaptiveInfo(1800f, 900f), CanonicalLayoutType.FEED).feedColumnCount)
    }

    @Test fun supportingPaneIsStackedOnCompactAndSplitOnMedium() {
        val compact = AdaptiveLayoutResolver.resolve(WindowAdaptiveInfo(400f, 900f), CanonicalLayoutType.SUPPORTING_PANE)
        val medium = AdaptiveLayoutResolver.resolve(WindowAdaptiveInfo(700f, 900f), CanonicalLayoutType.SUPPORTING_PANE)
        assertEquals(PaneArrangement.STACKED, compact.arrangement)
        assertEquals(PaneArrangement.MAIN_WITH_SUPPORTING, medium.arrangement)
        assertEquals(0.7f, medium.primaryPaneWeight)
        assertEquals(0.3f, medium.secondaryPaneWeight)
    }

    @Test fun separatingFoldCanEnableDualPaneWithinNarrowWindow() {
        val fold = FoldingFeatureInfo(
            posture = WindowPosture.HALF_OPENED,
            isSeparating = true,
            boundsLeftDp = 390f,
            boundsRightDp = 410f,
            boundsBottomDp = 800f
        )
        val spec = AdaptiveLayoutResolver.resolve(
            WindowAdaptiveInfo(800f, 900f, foldingFeature = fold),
            CanonicalLayoutType.LIST_DETAIL
        )
        assertEquals(PaneArrangement.SIDE_BY_SIDE, spec.arrangement)
        assertTrue(spec.useHingeAsBoundary)
    }
}
