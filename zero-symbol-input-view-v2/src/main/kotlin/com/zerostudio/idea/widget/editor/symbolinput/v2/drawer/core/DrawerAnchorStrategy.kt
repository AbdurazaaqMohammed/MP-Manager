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

/**
 * Resolves a complete anchor set from measured layout inputs.
 *
 * Implementations must be deterministic and must not read Android Views directly.
 * This makes anchor calculation testable and lets applications supply custom policies.
 */
interface DrawerAnchorStrategy {
    val id: String
    fun resolve(input: DrawerAnchorInput): List<DrawerAnchor>
}

/**
 * Compatibility strategy matching the current V2 two-position height model:
 * the existing collapsed height and the existing expanded height.
 */
class ExistingHeightAnchorStrategy : DrawerAnchorStrategy {
    override val id: String = ID

    override fun resolve(input: DrawerAnchorInput): List<DrawerAnchor> = listOf(
        DrawerAnchor("peek", input.minHeightPx, DrawerAnchorState.PEEK),
        DrawerAnchor(
            "expanded",
            input.existingExpandedHeightPx.coerceIn(input.minHeightPx, input.maxHeightPx),
            DrawerAnchorState.EXPANDED
        )
    )

    companion object {
        const val ID = "existing-height"
    }
}

/**
 * Material-style three-state policy. Heights are visible sheet heights measured
 * from the parent's bottom edge. Expanded reaches the actual available maximum;
 * half-expanded is derived from the measured parent height.
 *
 * Automatic Peek follows the Material 16:9 keyline when width is measured and
 * is bounded below the half state so the three stable states remain ordered.
 */
data class ThreeStageAnchorStrategy(
    val halfExpandedFraction: Float = 0.5f,
    val useAutomaticPeekHeight: Boolean = true
) : DrawerAnchorStrategy {
    init {
        require(halfExpandedFraction in 0.1f..0.9f)
    }

    override val id: String = ID

    override fun resolve(input: DrawerAnchorInput): List<DrawerAnchor> {
        val expanded = input.maxHeightPx.coerceAtLeast(input.minHeightPx)
        val half = (input.viewportHeightPx * halfExpandedFraction)
            .toInt().coerceIn(input.minHeightPx, expanded)
        val keylinePeek = if (useAutomaticPeekHeight && input.viewportWidthPx > 0) {
            input.viewportHeightPx - (input.viewportWidthPx * 9f / 16f).toInt()
        } else {
            input.minHeightPx
        }
        // If the 16:9 keyline would place Collapsed above Half-Expanded,
        // retain the configured minimum peek instead of creating a meaningless
        // one-pixel gap between two otherwise distinct stable states.
        val peek = if (keylinePeek < half) {
            keylinePeek.coerceAtLeast(input.minHeightPx).coerceAtMost(expanded)
        } else {
            input.minHeightPx.coerceAtMost(expanded)
        }
        return listOf(
            DrawerAnchor("peek", peek, DrawerAnchorState.PEEK),
            DrawerAnchor("half", half, DrawerAnchorState.HALF_EXPANDED),
            DrawerAnchor("expanded", expanded, DrawerAnchorState.EXPANDED)
        )
    }

    companion object {
        const val ID = "three-stage"
    }
}

/**
 * Content-sized policy. The second anchor follows measured content height,
 * clamped to the available viewport and configured maximum.
 */
class ContentBasedAnchorStrategy : DrawerAnchorStrategy {
    override val id: String = ID

    override fun resolve(input: DrawerAnchorInput): List<DrawerAnchor> = listOf(
        DrawerAnchor("peek", input.minHeightPx, DrawerAnchorState.PEEK),
        DrawerAnchor(
            "content",
            input.contentHeightPx.coerceIn(input.minHeightPx, input.maxHeightPx),
            DrawerAnchorState.EXPANDED
        )
    )

    companion object {
        const val ID = "content-based"
    }
}

/** Application-defined anchor heights in pixels. Heights are clamped by the resolver. */
class CustomAnchorStrategy(anchors: List<DrawerAnchor>) : DrawerAnchorStrategy {
    /**
     * Immutable defensive snapshot. Neither mutations to the constructor list
     * nor casts of this property can change the strategy after construction.
     */
    val anchors: List<DrawerAnchor> =
        java.util.Collections.unmodifiableList(java.util.ArrayList(anchors))

    init {
        require(this.anchors.isNotEmpty()) { "Custom anchors must not be empty" }
        require(this.anchors.map(DrawerAnchor::id).distinct().size == this.anchors.size) {
            "Custom anchor IDs must be unique"
        }
    }

    override val id: String = ID

    override fun resolve(input: DrawerAnchorInput): List<DrawerAnchor> = anchors

    /** Retains data-class-style copy semantics while always taking a new snapshot. */
    fun copy(anchors: List<DrawerAnchor> = this.anchors): CustomAnchorStrategy =
        CustomAnchorStrategy(anchors)

    operator fun component1(): List<DrawerAnchor> = anchors

    override fun equals(other: Any?): Boolean =
        other is CustomAnchorStrategy && anchors == other.anchors

    override fun hashCode(): Int = anchors.hashCode()

    override fun toString(): String = "CustomAnchorStrategy(anchors=$anchors)"

    companion object {
        const val ID = "custom"
    }
}

/** Named factories make the four supported policies explicit at call sites. */
object DrawerAnchorStrategies {
    @JvmStatic fun existingHeight(): DrawerAnchorStrategy = ExistingHeightAnchorStrategy()
    @JvmStatic fun threeStage(
        halfExpandedFraction: Float = 0.5f,
        useAutomaticPeekHeight: Boolean = true
    ): DrawerAnchorStrategy = ThreeStageAnchorStrategy(halfExpandedFraction, useAutomaticPeekHeight)
    @JvmStatic fun contentBased(): DrawerAnchorStrategy = ContentBasedAnchorStrategy()
    @JvmStatic fun custom(anchors: List<DrawerAnchor>): DrawerAnchorStrategy = CustomAnchorStrategy(anchors)
}
