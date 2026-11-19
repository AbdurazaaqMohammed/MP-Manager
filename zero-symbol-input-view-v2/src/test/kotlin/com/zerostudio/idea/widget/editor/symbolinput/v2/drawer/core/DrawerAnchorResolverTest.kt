/*
 * This file is part of zero-Symbol-input-view-V2.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerAnchorResolverTest {
    private val input = DrawerAnchorInput(
        minHeightPx = 120,
        maxHeightPx = 900,
        viewportHeightPx = 1000,
        contentHeightPx = 640,
        existingExpandedHeightPx = 720,
        viewportWidthPx = 400
    )

    @Test fun existingHeightStrategyPreservesCurrentTwoAnchorLayout() {
        val anchors = DrawerAnchorResolver.resolve(ExistingHeightAnchorStrategy(), input)
        assertEquals(listOf(120, 720), anchors.map { it.heightPx })
        assertEquals(listOf(DrawerAnchorState.PEEK, DrawerAnchorState.EXPANDED), anchors.map { it.state })
    }

    @Test fun threeStageStrategyCreatesIndependentThreeAnchors() {
        val anchors = DrawerAnchorResolver.resolve(ThreeStageAnchorStrategy(), input)
        assertEquals(listOf(120, 500, 900), anchors.map { it.heightPx })
        assertEquals(
            listOf(DrawerAnchorState.PEEK, DrawerAnchorState.HALF_EXPANDED, DrawerAnchorState.EXPANDED),
            anchors.map { it.state }
        )
    }

    @Test fun contentBasedStrategyUsesMeasuredContentHeight() {
        val anchors = DrawerAnchorResolver.resolve(ContentBasedAnchorStrategy(), input)
        assertEquals(listOf(120, 640), anchors.map { it.heightPx })
    }

    @Test fun customStrategyClampsAndSortsDeveloperAnchors() {
        val strategy = CustomAnchorStrategy(listOf(
            DrawerAnchor("high", 1200, DrawerAnchorState.EXPANDED),
            DrawerAnchor("low", 40, DrawerAnchorState.PEEK),
            DrawerAnchor("middle", 400, DrawerAnchorState.CUSTOM)
        ))
        val anchors = DrawerAnchorResolver.resolve(strategy, input)
        assertEquals(listOf(120, 400, 900), anchors.map { it.heightPx })
    }

    @Test fun duplicateHeightsAreCollapsed() {
        val anchors = DrawerAnchorResolver.resolve(
            ThreeStageAnchorStrategy(halfExpandedFraction = 0.1f, useAutomaticPeekHeight = false),
            input.copy(viewportHeightPx = 100, minHeightPx = 120, maxHeightPx = 120)
        )
        assertEquals(1, anchors.size)
        assertEquals(120, anchors.single().heightPx)
    }

    @Test fun flingMovesAtMostOneAnchorPerGesture() {
        val anchors = DrawerAnchorResolver.resolve(ThreeStageAnchorStrategy(), input)
        val target = DrawerAnchorResolver.flingTarget(anchors, 500, 1600f, 500f)
        assertEquals("expanded", target.id)
    }

    @Test fun flingChoosesFirstAnchorInTravelDirectionFromIntermediateHeight() {
        val anchors = DrawerAnchorResolver.resolve(
            ThreeStageAnchorStrategy(useAutomaticPeekHeight = false), input
        )
        assertEquals("half", DrawerAnchorResolver.flingTarget(anchors, 430, 1600f, 500f).id)
        assertEquals("expanded", DrawerAnchorResolver.flingTarget(anchors, 570, 1600f, 500f).id)
        assertEquals("half", DrawerAnchorResolver.flingTarget(anchors, 570, -1600f, 500f).id)
        assertEquals("peek", DrawerAnchorResolver.flingTarget(anchors, 430, -1600f, 500f).id)
    }

    @Test fun dragIntermediateHeightsAreNotQuantizedToAnchors() {
        val anchors = DrawerAnchorResolver.resolve(
            ThreeStageAnchorStrategy(useAutomaticPeekHeight = false), input
        )
        val intermediateHeights = listOf(180, 240, 360, 499, 500, 620, 780, 899)
        assertTrue(intermediateHeights.all { it in anchors.first().heightPx..anchors.last().heightPx })
        assertEquals("half", DrawerAnchorResolver.nearestAnchor(anchors, 620).id)
        assertEquals("expanded", DrawerAnchorResolver.nearestAnchor(anchors, 780).id)
    }

    @Test fun automaticPeekUsesMeasuredWidthWithoutCollapsingHalfState() {
        val portrait = DrawerAnchorResolver.resolve(ThreeStageAnchorStrategy(), input)
        assertEquals(120, portrait.first().heightPx)
        assertEquals(500, portrait[1].heightPx)

        val wide = DrawerAnchorResolver.resolve(
            ThreeStageAnchorStrategy(),
            input.copy(viewportHeightPx = 600, viewportWidthPx = 600, maxHeightPx = 600)
        )
        assertEquals(263, wide.first().heightPx)
        assertEquals(300, wide[1].heightPx)
    }

    @Test fun halfAndExpandedAreDerivedFromMeasuredViewportAndMaximum() {
        val measured = input.copy(viewportHeightPx = 1200, viewportWidthPx = 500, maxHeightPx = 1000)
        val anchors = DrawerAnchorResolver.resolve(
            ThreeStageAnchorStrategy(useAutomaticPeekHeight = false), measured
        )
        assertEquals(listOf(120, 600, 1000), anchors.map { it.heightPx })
    }

    @Test fun nearestAnchorChoosesClosestHeight() {
        val anchors = DrawerAnchorResolver.resolve(ThreeStageAnchorStrategy(), input)
        assertEquals("half", DrawerAnchorResolver.nearestAnchor(anchors, 530).id)
        assertTrue(anchors.zipWithNext().all { (a, b) -> a.heightPx <= b.heightPx })
    }

    @Test fun resolverRejectsDuplicateIdsFromThirdPartyStrategies() {
        val strategy = object : DrawerAnchorStrategy {
            override val id = "invalid-duplicate-ids"
            override fun resolve(input: DrawerAnchorInput) = listOf(
                DrawerAnchor("same", input.minHeightPx, DrawerAnchorState.PEEK),
                DrawerAnchor("same", input.maxHeightPx, DrawerAnchorState.EXPANDED)
            )
        }
        try {
            DrawerAnchorResolver.resolve(strategy, input)
            throw AssertionError("Expected duplicate anchor IDs to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("must be unique"))
        }
    }

    @Test fun nearestAnchorMidpointTieAlwaysChoosesMoreCollapsedAnchor() {
        val low = DrawerAnchor("peek", 200, DrawerAnchorState.PEEK)
        val high = DrawerAnchor("expanded", 400, DrawerAnchorState.EXPANDED)
        assertEquals("peek", DrawerAnchorResolver.nearestAnchor(listOf(high, low), 300).id)
        assertEquals("peek", DrawerAnchorResolver.nearestAnchor(listOf(low, high), 300).id)
    }

    @Test fun nearestAnchorUsesLongDistanceArithmeticForExtremeHeights() {
        val low = DrawerAnchor("low", 0, DrawerAnchorState.PEEK)
        val high = DrawerAnchor("high", Int.MAX_VALUE, DrawerAnchorState.EXPANDED)
        assertEquals("low", DrawerAnchorResolver.nearestAnchor(listOf(high, low), Int.MIN_VALUE).id)
    }

    @Test fun flingRejectsNonFiniteVelocityAndNegativeThreshold() {
        val anchors = DrawerAnchorResolver.resolve(ThreeStageAnchorStrategy(), input)
        try {
            DrawerAnchorResolver.flingTarget(anchors, 400, Float.NaN, 500f)
            throw AssertionError("Expected NaN velocity to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("finite"))
        }
        try {
            DrawerAnchorResolver.flingTarget(anchors, 400, 1200f, -1f)
            throw AssertionError("Expected negative threshold to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("non-negative"))
        }
    }

    @Test fun drawerStateRequiresCurrentAnchorToBelongToSnapshot() {
        val anchors = DrawerAnchorResolver.resolve(ThreeStageAnchorStrategy(), input)
        try {
            DrawerState("missing", 500, anchors)
            throw AssertionError("Expected missing current anchor to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("must exist"))
        }
    }

}
