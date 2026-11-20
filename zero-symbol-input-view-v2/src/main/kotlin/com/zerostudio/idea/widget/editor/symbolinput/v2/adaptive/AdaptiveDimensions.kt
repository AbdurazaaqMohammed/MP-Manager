/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import kotlin.math.roundToInt

/**
 * Small bounded scaling for decorative/layout dimensions. Text remains in sp
 * and interactive targets keep their minimum size; this is not a replacement
 * for Android density or font scaling.
 */
data class AdaptiveDimensionPolicy(
    val compactScale: Float = 0.96f,
    val mediumScale: Float = 1f,
    val expandedScale: Float = 1.06f,
    val minimumTouchTargetDp: Float = 48f,
    val maximumScaledDimensionDp: Float = 160f
) {
    init {
        require(compactScale in 0.75f..1.25f)
        require(mediumScale in 0.75f..1.25f)
        require(expandedScale in 0.75f..1.25f)
        require(minimumTouchTargetDp >= 48f)
        require(maximumScaledDimensionDp > 0f)
    }
}

class AdaptiveDimensions(
    private val info: WindowAdaptiveInfo,
    private val policy: AdaptiveDimensionPolicy = AdaptiveDimensionPolicy()
) {
    val scale: Float
        get() = when (info.sizeClass) {
            WindowSizeClass.COMPACT -> policy.compactScale
            WindowSizeClass.MEDIUM -> policy.mediumScale
            WindowSizeClass.EXPANDED -> policy.expandedScale
        }

    fun dp(baseDp: Float, scale: Boolean = true): Float {
        require(baseDp >= 0f)
        val value = if (scale) baseDp * this.scale else baseDp
        return value.coerceAtMost(policy.maximumScaledDimensionDp)
    }

    fun touchTargetDp(requestedDp: Float): Float {
        require(requestedDp >= 0f)
        return maxOf(policy.minimumTouchTargetDp, dp(requestedDp))
    }

    fun px(baseDp: Float, scale: Boolean = true): Int =
        (dp(baseDp, scale) * info.density).roundToInt().coerceAtLeast(0)
}
