/*
 * This file is part of zero-Symbol-input-view-V2.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */

package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.LargeScreenLayoutMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SideDockEdge
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings

/** Resolved physical edge for the symbol toolbar host. */
enum class SymbolDrawerDocking { BOTTOM, LEFT, RIGHT }

/**
 * Resolves the toolbar's docking edge from the current app-window width.
 * The decision intentionally uses window bounds, not device model or orientation.
 * Hosts remain responsible for applying the returned edge to their own layout.
 */
object SymbolDrawerDockingPolicy {
    const val DEFAULT_SIDE_WIDTH_DP = 72
    const val MIN_SIDE_WIDTH_DP = 56
    const val MAX_SIDE_WIDTH_DP = 88
    const val AUTO_SIDE_DOCK_MIN_WIDTH_DP = 600

    fun resolve(
        settings: SymbolDrawerSettings,
        window: WindowAdaptiveInfo
    ): SymbolDrawerDocking = when (settings.largeScreenLayoutMode) {
        LargeScreenLayoutMode.BOTTOM -> SymbolDrawerDocking.BOTTOM
        LargeScreenLayoutMode.SIDE -> settings.sideDockEdge.toDocking()
        LargeScreenLayoutMode.AUTO -> if (window.availableWidthDp >= AUTO_SIDE_DOCK_MIN_WIDTH_DP) {
            settings.sideDockEdge.toDocking()
        } else {
            SymbolDrawerDocking.BOTTOM
        }
    }

    fun sideWidthDp(baseWidthDp: Int = DEFAULT_SIDE_WIDTH_DP): Int =
        baseWidthDp.coerceIn(MIN_SIDE_WIDTH_DP, MAX_SIDE_WIDTH_DP)

    private fun SideDockEdge.toDocking() = when (this) {
        SideDockEdge.LEFT -> SymbolDrawerDocking.LEFT
        SideDockEdge.RIGHT -> SymbolDrawerDocking.RIGHT
    }
}
