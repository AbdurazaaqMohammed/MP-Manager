/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

enum class CanonicalLayoutType { LIST_DETAIL, FEED, SUPPORTING_PANE }
enum class PaneArrangement { SINGLE, SIDE_BY_SIDE, MAIN_WITH_SUPPORTING, FEED_GRID, STACKED }

/** Explicit, inspectable result; UI containers decide how to apply the arrangement. */
data class AdaptiveLayoutSpec(
    val layoutType: CanonicalLayoutType,
    val arrangement: PaneArrangement,
    val primaryPaneWeight: Float = 1f,
    val secondaryPaneWeight: Float = 0f,
    val feedColumnCount: Int = 1,
    val useHingeAsBoundary: Boolean = false
) {
    init {
        require(primaryPaneWeight > 0f)
        require(secondaryPaneWeight >= 0f)
        require(feedColumnCount >= 1)
    }
}

data class AdaptiveLayoutPolicy(
    val listDetailBreakpointDp: Float = WindowAdaptiveInfo.EXPANDED_WIDTH_DP,
    val supportingPaneBreakpointDp: Float = WindowAdaptiveInfo.MEDIUM_WIDTH_DP,
    val feedMinimumColumnWidthDp: Float = 280f,
    val feedMaximumColumns: Int = 4,
    val supportingPaneMainWeight: Float = 0.7f,
    val supportingPaneSecondaryWeight: Float = 0.3f
) {
    init {
        require(listDetailBreakpointDp > 0f)
        require(supportingPaneBreakpointDp > 0f)
        require(feedMinimumColumnWidthDp > 0f)
        require(feedMaximumColumns >= 1)
        require(supportingPaneMainWeight > 0f && supportingPaneSecondaryWeight > 0f)
    }
}

object AdaptiveLayoutResolver {
    fun resolve(
        info: WindowAdaptiveInfo,
        type: CanonicalLayoutType,
        policy: AdaptiveLayoutPolicy = AdaptiveLayoutPolicy()
    ): AdaptiveLayoutSpec {
        val width = info.availableWidthDp
        val separatedHinge = info.foldingFeature.isSeparating && info.foldingFeature.hasBounds
        return when (type) {
            CanonicalLayoutType.LIST_DETAIL -> {
                val dualPane = width >= policy.listDetailBreakpointDp || separatedHinge
                AdaptiveLayoutSpec(
                    type,
                    if (dualPane) PaneArrangement.SIDE_BY_SIDE else PaneArrangement.SINGLE,
                    primaryPaneWeight = if (dualPane) 0.4f else 1f,
                    secondaryPaneWeight = if (dualPane) 0.6f else 0f,
                    useHingeAsBoundary = separatedHinge
                )
            }
            CanonicalLayoutType.FEED -> {
                val columns = (width / policy.feedMinimumColumnWidthDp).toInt()
                    .coerceIn(1, policy.feedMaximumColumns)
                AdaptiveLayoutSpec(
                    type,
                    if (columns == 1) PaneArrangement.FEED_GRID else PaneArrangement.FEED_GRID,
                    feedColumnCount = columns,
                    useHingeAsBoundary = separatedHinge
                )
            }
            CanonicalLayoutType.SUPPORTING_PANE -> {
                val showSupporting = width >= policy.supportingPaneBreakpointDp || separatedHinge
                AdaptiveLayoutSpec(
                    type,
                    if (showSupporting) PaneArrangement.MAIN_WITH_SUPPORTING else PaneArrangement.STACKED,
                    primaryPaneWeight = if (showSupporting) policy.supportingPaneMainWeight else 1f,
                    secondaryPaneWeight = if (showSupporting) policy.supportingPaneSecondaryWeight else 0f,
                    useHingeAsBoundary = separatedHinge
                )
            }
        }
    }
}
