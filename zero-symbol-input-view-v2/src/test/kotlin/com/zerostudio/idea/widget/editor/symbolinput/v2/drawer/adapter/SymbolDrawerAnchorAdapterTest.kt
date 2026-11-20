/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.adapter

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolDrawerAnchorAdapterTest {

    @Test
    fun preservesLegacyCollapsedAndExpandedHeightsAtDensityOne() {
        val config = DrawerConfig(columns = 4, collapsedRows = 2, cellHeightDp = 30)
        val anchors = SymbolDrawerAnchorAdapter.resolve(
            groups = listOf(group("nine", 9)),
            config = config,
            density = 1f
        )

        // Legacy formula: 2 rows = 70px; 3 rows = 102px; indicator adds 26px
        // only to the expanded anchor.
        assertEquals(70, anchors.collapsedHeightPx)
        assertEquals(102, anchors.expandedGridHeightPx)
        assertEquals(128, anchors.expandedHeightPx)
    }

    @Test
    fun collapsedAnchorUsesConfiguredRowsForEverySupportedLayout() {
        listOf(1, 3, 4, 7, 16).forEach { rows ->
            val config = DrawerConfig(columns = 5, collapsedRows = rows, cellHeightDp = 32)
            val anchors = SymbolDrawerAnchorAdapter.resolve(
                groups = listOf(group("many", 100)),
                config = config,
                density = 1f
            )
            assertEquals(
                com.zerostudio.idea.widget.editor.symbolinput.v2.state.DrawerGeometry
                    .gridHeightPx(rows, config, 1f),
                anchors.collapsedHeightPx
            )
        }
    }

    @Test
    fun preservesLegacyHeightsAcrossDensityScaling() {
        val config = DrawerConfig(columns = 4, collapsedRows = 2, cellHeightDp = 30)
        val anchors = SymbolDrawerAnchorAdapter.resolve(
            groups = listOf(group("nine", 9)),
            config = config,
            density = 2f
        )

        assertEquals(140, anchors.collapsedHeightPx)
        assertEquals(204, anchors.expandedGridHeightPx)
        assertEquals(256, anchors.expandedHeightPx)
    }

    @Test
    fun indicatorRemainsPartOfExpandedHeightWhenAllSymbolsFitCollapsedGrid() {
        val config = DrawerConfig(columns = 10, collapsedRows = 2, cellHeightDp = 28)
        val anchors = SymbolDrawerAnchorAdapter.resolve(
            groups = listOf(group("few", 3)),
            config = config,
            density = 3f
        )

        assertEquals(anchors.collapsedHeightPx, anchors.expandedGridHeightPx)
        assertEquals(anchors.collapsedHeightPx + 78, anchors.expandedHeightPx)
    }

    private fun group(id: String, count: Int) = SymbolGroup(
        id = id,
        title = id,
        symbols = (0 until count).map { index ->
            SymbolEntry(index.toLong() + 1L, "s$index", SymbolAction.InsertText("s$index"))
        }
    )
}
