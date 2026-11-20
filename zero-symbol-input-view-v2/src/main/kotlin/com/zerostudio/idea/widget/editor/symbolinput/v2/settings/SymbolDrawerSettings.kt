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

package com.zerostudio.idea.widget.editor.symbolinput.v2.settings

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DrawerConfig

enum class ThemeMode { SYSTEM, LIGHT, DARK }
/**
 * Presentation strategies for group-page navigation. These values change only
 * the indicator renderer; the ViewPager2 group model and page state stay intact.
 */
enum class GroupIndicatorStyle {
    TEXT_TABS, TEXT, PILL, DOTS, UNDERLINE,
    GOOEY_METABALL, ELASTIC_WORM, SPRING_BLOB, LIQUID_MERGE,
    VERNIER_TICKS, MECHANICAL_RULER, WHEEL_ROLLER, CAMERA_DIAL,
    RADIAL_RING, RADAR_SWEEP, HUD_ARC, ORBIT_DOTS,
    ELASTIC_WAVE, SINE_WAVE, BOUNCE_TRACK, SPRING_SLIDER,
    MORPHING_BAR, SEGMENTED_TRACK, PROGRESS_LINE, DOUBLE_TRACK,
    MINIMAL_DASHES, SQUARE_TICKS, DIAMOND_MARKERS, TRIANGLE_POINTER,
    HEXAGON_NODES, GLOWING_NODES, PULSE_DOTS, STACKED_CARDS,
    MINI_PREVIEW, THUMBNAIL_STRIP, NUMBER_BADGES, VERTICAL_RAIL,
    ARC_NODES, FLUID_TRAIL, DIGITAL_COUNTER, STEPPER
}
enum class HapticMode { OFF, SELECTION, ALL }

/** Large-window placement preference for the symbol toolbar. */
enum class LargeScreenLayoutMode { AUTO, BOTTOM, SIDE }

/** Preferred edge when the toolbar is docked vertically on a large window. */
enum class SideDockEdge { LEFT, RIGHT }

/** Immutable, validated settings snapshot persisted by the V2 settings repository. */
data class SymbolDrawerSettings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val columns: Int = 10,
    val collapsedRows: Int = 2,
    val cellHeightDp: Int = 28,
    val cellHorizontalSpacingDp: Int = 2,
    val cellVerticalSpacingDp: Int = -2,
    val indicatorHeightDp: Int = 26,
    val indicatorStyle: GroupIndicatorStyle = GroupIndicatorStyle.UNDERLINE,
    val showGroupTitles: Boolean = true,
    val showSymbolLabels: Boolean = true,
    val rememberExpandedState: Boolean = true,
    val showFullSymbolLabels: Boolean = false,
    val symbolLabelTextSizeSp: Int = 18,
    val showActionHints: Boolean = false,
    val maxExpandedHeightDp: Int = 480,
    val animationDurationMs: Int = 200,
    val reduceMotion: Boolean = false,
    val flingVelocityDpPerSecond: Int = 900,
    val longPressTimeoutMs: Int = 420,
    val hapticMode: HapticMode = HapticMode.SELECTION,
    val autoCollapseAfterAction: Boolean = false,
    val pageCacheLimit: Int = 3,
    val prefetchAdjacentGroups: Boolean = true,
    val minimumTouchTargetDp: Int = 40,
    val searchIncludesActionPayload: Boolean = false,
    val largeScreenLayoutMode: LargeScreenLayoutMode = LargeScreenLayoutMode.AUTO,
    val sideDockEdge: SideDockEdge = SideDockEdge.RIGHT
) {
    init {
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION)
        require(columns in 1..30)
        require(symbolLabelTextSizeSp in 8..32)
        require(collapsedRows in 1..16)
        require(cellHeightDp in 6..128)
        require(cellHorizontalSpacingDp in -24..48)
        require(cellVerticalSpacingDp in -24..48)
        require(indicatorHeightDp in 0..96)
        require(maxExpandedHeightDp in 80..1800)
        require(animationDurationMs in 0..1200)
        require(flingVelocityDpPerSecond in 100..6000)
        require(longPressTimeoutMs in 250..1000)
        require(pageCacheLimit in 1..9)
        require(minimumTouchTargetDp in 20..112)
    }

    fun toDrawerConfig() = DrawerConfig(
        columns = columns,
        collapsedRows = collapsedRows,
        cellHeightDp = cellHeightDp,
        cellHorizontalSpacingDp = cellHorizontalSpacingDp,
        cellVerticalSpacingDp = cellVerticalSpacingDp,
        indicatorHeightDp = indicatorHeightDp,
        animationDurationMs = if (reduceMotion) 0L else animationDurationMs.toLong(),
        flingVelocityDpPerSecond = flingVelocityDpPerSecond,
        maxExpandedHeightDp = maxExpandedHeightDp,
        minimumTouchTargetDp = minimumTouchTargetDp
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
    }
}
