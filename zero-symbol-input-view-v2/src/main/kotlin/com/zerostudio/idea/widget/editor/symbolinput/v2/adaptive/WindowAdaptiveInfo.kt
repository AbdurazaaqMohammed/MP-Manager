/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

/**
 * Window classes are based on the current application window, not the physical
 * device. This keeps split-screen, freeform and desktop windows responsive.
 */
enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

enum class WindowPosture { UNKNOWN, FLAT, HALF_OPENED }

data class WindowInsetsDp(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    init {
        require(listOf(left, top, right, bottom).all { it >= 0f })
    }
}

data class FoldingFeatureInfo(
    val posture: WindowPosture = WindowPosture.UNKNOWN,
    val isSeparating: Boolean = false,
    val boundsLeftDp: Float = 0f,
    val boundsTopDp: Float = 0f,
    val boundsRightDp: Float = 0f,
    val boundsBottomDp: Float = 0f
) {
    init {
        require(boundsLeftDp >= 0f && boundsTopDp >= 0f)
        require(boundsRightDp >= boundsLeftDp && boundsBottomDp >= boundsTopDp)
    }

    val hasBounds: Boolean
        get() = boundsRightDp > boundsLeftDp && boundsBottomDp > boundsTopDp
}

/**
 * Immutable snapshot of the usable app window. Hosts can construct it from
 * View bounds, WindowMetrics or AndroidX WindowManager without coupling this
 * core API to a particular window-management dependency.
 */
data class WindowAdaptiveInfo(
    val widthDp: Float,
    val heightDp: Float,
    val density: Float = 1f,
    val insets: WindowInsetsDp = WindowInsetsDp(),
    val foldingFeature: FoldingFeatureInfo = FoldingFeatureInfo()
) {
    init {
        require(widthDp >= 0f && heightDp >= 0f)
        require(density > 0f)
    }

    val availableWidthDp: Float
        get() = (widthDp - insets.left - insets.right).coerceAtLeast(0f)

    val availableHeightDp: Float
        get() = (heightDp - insets.top - insets.bottom).coerceAtLeast(0f)

    val sizeClass: WindowSizeClass
        get() = when {
            availableWidthDp < MEDIUM_WIDTH_DP -> WindowSizeClass.COMPACT
            availableWidthDp < EXPANDED_WIDTH_DP -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }

    companion object {
        const val MEDIUM_WIDTH_DP = 600f
        const val EXPANDED_WIDTH_DP = 840f

        fun fromPixels(
            widthPx: Int,
            heightPx: Int,
            density: Float,
            insets: WindowInsetsDp = WindowInsetsDp(),
            foldingFeature: FoldingFeatureInfo = FoldingFeatureInfo()
        ): WindowAdaptiveInfo {
            require(density > 0f)
            return WindowAdaptiveInfo(
                widthDp = widthPx.coerceAtLeast(0) / density,
                heightDp = heightPx.coerceAtLeast(0) / density,
                density = density,
                insets = insets,
                foldingFeature = foldingFeature
            )
        }
    }
}
