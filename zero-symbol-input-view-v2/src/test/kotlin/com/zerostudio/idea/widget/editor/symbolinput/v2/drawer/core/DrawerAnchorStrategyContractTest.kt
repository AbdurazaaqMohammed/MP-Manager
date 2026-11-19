/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core

import org.junit.Assert.*
import org.junit.Test

class DrawerAnchorStrategyContractTest {
    private val input = DrawerAnchorInput(
        minHeightPx = 80, maxHeightPx = 900, viewportHeightPx = 1000,
        contentHeightPx = 520, existingExpandedHeightPx = 700,
        density = 2f, viewportWidthPx = 400
    )

    @Test fun strategyFactoriesExposeStableIdsAndEquivalentPolicies() {
        assertEquals(ExistingHeightAnchorStrategy.ID, DrawerAnchorStrategies.existingHeight().id)
        assertEquals(ThreeStageAnchorStrategy.ID, DrawerAnchorStrategies.threeStage().id)
        assertEquals(ContentBasedAnchorStrategy.ID, DrawerAnchorStrategies.contentBased().id)
        assertEquals(CustomAnchorStrategy.ID, DrawerAnchorStrategies.custom(
            listOf(DrawerAnchor("only", 80, DrawerAnchorState.CUSTOM))
        ).id)
        assertEquals(
            ThreeStageAnchorStrategy(0.4f, false).resolve(input),
            DrawerAnchorStrategies.threeStage(0.4f, false).resolve(input)
        )
    }

    @Test fun existingHeightClampsExpandedToConfiguredBounds() {
        assertEquals(80, ExistingHeightAnchorStrategy().resolve(input.copy(existingExpandedHeightPx = 1)).last().heightPx)
        assertEquals(900, ExistingHeightAnchorStrategy().resolve(input.copy(existingExpandedHeightPx = 1200)).last().heightPx)
    }

    @Test fun contentHeightClampsToMinimumAndMaximum() {
        assertEquals(80, ContentBasedAnchorStrategy().resolve(input.copy(contentHeightPx = 0)).last().heightPx)
        assertEquals(900, ContentBasedAnchorStrategy().resolve(input.copy(contentHeightPx = 1500)).last().heightPx)
    }

    @Test fun customStrategyReturnsItsDeclaredAnchorsWithoutMutatingInput() {
        val source = mutableListOf(
            DrawerAnchor("second", 300, DrawerAnchorState.CUSTOM),
            DrawerAnchor("first", 100, DrawerAnchorState.PEEK)
        )
        val strategy = CustomAnchorStrategy(source)
        source.clear()
        assertEquals(listOf("second", "first"), strategy.resolve(input).map { it.id })
        assertEquals(listOf("second", "first"), strategy.copy().resolve(input).map { it.id })
        assertThrows(UnsupportedOperationException::class.java) {
            (strategy.anchors as MutableList<DrawerAnchor>).clear()
        }
    }

    @Test fun threeStageSupportsViewportAndMinMaxBoundaries() {
        val anchors = ThreeStageAnchorStrategy(useAutomaticPeekHeight = false).resolve(
            input.copy(minHeightPx = 400, maxHeightPx = 600, viewportHeightPx = 100)
        )
        assertEquals(listOf(400, 400, 600), anchors.map { it.heightPx })
    }

    @Test fun threeStageFractionMustBeWithinSupportedRange() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 0f, 0.099f, 0.901f, 1f).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { ThreeStageAnchorStrategy(value) }
        }
        assertEquals(0.1f, ThreeStageAnchorStrategy(0.1f).halfExpandedFraction)
        assertEquals(0.9f, ThreeStageAnchorStrategy(0.9f).halfExpandedFraction)
    }

    @Test fun anchorAndInputRejectInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) { DrawerAnchor(" ", 1, DrawerAnchorState.PEEK) }
        assertThrows(IllegalArgumentException::class.java) { DrawerAnchor("x", -1, DrawerAnchorState.PEEK) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(minHeightPx = -1) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(maxHeightPx = 1, minHeightPx = 2) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(viewportHeightPx = -1) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(contentHeightPx = -1) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(existingExpandedHeightPx = -1) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(density = 0f) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(density = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(density = Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { input.copy(viewportWidthPx = -1) }
    }

    @Test fun customStrategyRejectsEmptyAndDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) { CustomAnchorStrategy(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            CustomAnchorStrategy(listOf(
                DrawerAnchor("same", 1, DrawerAnchorState.PEEK),
                DrawerAnchor("same", 2, DrawerAnchorState.EXPANDED)
            ))
        }
    }
}
