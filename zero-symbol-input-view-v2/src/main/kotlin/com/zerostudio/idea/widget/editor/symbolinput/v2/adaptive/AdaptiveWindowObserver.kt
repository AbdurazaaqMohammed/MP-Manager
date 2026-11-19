/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Observes the host View's actual laid-out window bounds. It deliberately does
 * not install an insets listener, so it cannot replace or steal the host's
 * existing inset handling. Hosts may push folding-feature updates separately.
 */
enum class WindowInsetsMode {
    /** Subtract system bars/cutouts from the observed view bounds. */
    SUBTRACT_SYSTEM_INSETS,
    /** Use view bounds as already-safe content bounds; do not subtract insets again. */
    VIEW_BOUNDS_ALREADY_EXCLUDE_INSETS
}

class AdaptiveWindowObserver(
    private val view: View,
    private val onChanged: (WindowAdaptiveInfo) -> Unit,
    private val insetsMode: WindowInsetsMode = WindowInsetsMode.SUBTRACT_SYSTEM_INSETS
) : View.OnLayoutChangeListener, View.OnAttachStateChangeListener, AutoCloseable {

    private var foldingFeature = FoldingFeatureInfo()
    private var lastSnapshot: WindowAdaptiveInfo? = null
    private var closed = false

    init {
        view.addOnLayoutChangeListener(this)
        view.addOnAttachStateChangeListener(this)
        if (ViewCompat.isAttachedToWindow(view)) dispatchIfChanged()
    }

    fun updateFoldingFeature(value: FoldingFeatureInfo) {
        if (closed || foldingFeature == value) return
        foldingFeature = value
        dispatchIfChanged(force = true)
    }

    /** Re-emits the current snapshot after host settings or layout constraints change. */
    fun refresh() {
        dispatchIfChanged(force = true)
    }

    override fun onLayoutChange(
        changedView: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        dispatchIfChanged()
    }

    override fun onViewAttachedToWindow(v: View) = dispatchIfChanged(force = true)

    override fun onViewDetachedFromWindow(v: View) {
        lastSnapshot = null
    }

    private fun dispatchIfChanged(force: Boolean = false) {
        if (closed || !ViewCompat.isAttachedToWindow(view)) return
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return

        val density = view.resources.displayMetrics.density
        val rootInsets = if (insetsMode == WindowInsetsMode.SUBTRACT_SYSTEM_INSETS) {
            ViewCompat.getRootWindowInsets(view)
        } else null
        val systemInsets = rootInsets?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val insets = if (systemInsets == null) WindowInsetsDp() else WindowInsetsDp(
            left = systemInsets.left / density,
            top = systemInsets.top / density,
            right = systemInsets.right / density,
            bottom = systemInsets.bottom / density
        )
        val snapshot = WindowAdaptiveInfo.fromPixels(
            widthPx = width,
            heightPx = height,
            density = density,
            insets = insets,
            foldingFeature = foldingFeature
        )
        if (force || snapshot != lastSnapshot) {
            lastSnapshot = snapshot
            onChanged(snapshot)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        view.removeOnLayoutChangeListener(this)
        view.removeOnAttachStateChangeListener(this)
        lastSnapshot = null
    }
}
