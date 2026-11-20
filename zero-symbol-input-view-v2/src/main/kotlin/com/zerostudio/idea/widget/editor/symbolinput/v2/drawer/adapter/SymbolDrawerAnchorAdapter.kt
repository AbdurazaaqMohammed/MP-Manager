/*
 * This file is part of zero-Symbol-input-view-V2.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.adapter

import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorResolver
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorStrategies
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorInput
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.state.DrawerGeometry

/**
 * Bridges the symbol drawer's established grid measurements to the generic
 * Drawer anchor API. No dimensions are reinterpreted or rounded a second time.
 *
 * The symbol toolbar historically measures its collapsed/expanded grid in
 * pixels, then adds the group-indicator height only to the expanded position.
 * Keep that exact contract here so adopting the generic anchor resolver cannot
 * introduce a one-row, density-rounding, or indicator-height regression.
 */
data class SymbolDrawerAnchors(
    val collapsedHeightPx: Int,
    val expandedGridHeightPx: Int,
    val expandedHeightPx: Int
)

object SymbolDrawerAnchorAdapter {
    @JvmStatic
    fun resolve(
        groups: List<SymbolGroup>,
        config: DrawerConfig,
        density: Float
    ): SymbolDrawerAnchors {
        require(density.isFinite() && density > 0f) { "density must be finite and positive" }

        val collapsed = DrawerGeometry.collapsedHeightPx(config, density)
        val expandedGrid = DrawerGeometry.expandedHeightPx(groups, config, density)
        val indicator = (config.indicatorHeightDp * density).let { kotlin.math.round(it).toInt() }
        val expanded = maxOf(collapsed, expandedGrid) + indicator

        val anchors = DrawerAnchorResolver.resolve(
            DrawerAnchorStrategies.existingHeight(),
            DrawerAnchorInput(
                minHeightPx = collapsed,
                maxHeightPx = expanded,
                viewportHeightPx = expanded,
                contentHeightPx = expanded,
                existingExpandedHeightPx = expanded,
                density = density
            )
        )
        return SymbolDrawerAnchors(
            collapsedHeightPx = anchors.first().heightPx,
            expandedGridHeightPx = expandedGrid,
            expandedHeightPx = anchors.last().heightPx
        )
    }
}
