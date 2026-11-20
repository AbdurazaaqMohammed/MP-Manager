/*
 * This file is part of zero-Symbol-input-view-V2.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */

package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import android.view.View
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings

/**
 * Connects live window changes and saved preferences to the docking layout API.
 *
 * The editor host receives a resolved immutable spec and applies it to its own
 * container. This keeps window observation out of SymbolDrawerView and avoids
 * silently replacing the host's insets or layout listeners.
 */
class SymbolDrawerDockingCoordinator(
    hostView: View,
    settings: SymbolDrawerSettings,
    private var bottomCollapsedHeightDp: Float,
    private var bottomExpandedHeightDp: Float,
    private val onLayoutResolved: (SymbolDrawerDockingLayoutSpec) -> Unit
) : AutoCloseable {

    private var currentSettings = settings
    private var currentWindow: WindowAdaptiveInfo? = null
    private var closed = false

    private val observer by lazy(LazyThreadSafetyMode.NONE) {
        AdaptiveWindowObserver(hostView, onChanged = { window ->
            currentWindow = window
            dispatch()
        })
    }

    init {
        require(bottomCollapsedHeightDp >= 0f)
        require(bottomExpandedHeightDp >= bottomCollapsedHeightDp)
        // Attach only after constructor invariants are validated.
        observer
    }

    /** Update after settings are edited or restored from persistent storage. */
    fun updateSettings(value: SymbolDrawerSettings) {
        if (closed || currentSettings == value) return
        currentSettings = value
        dispatch()
    }

    /** Update when the host's legacy bottom-drawer geometry changes. */
    fun updateBottomGeometry(collapsedHeightDp: Float, expandedHeightDp: Float) {
        require(collapsedHeightDp >= 0f)
        require(expandedHeightDp >= collapsedHeightDp)
        if (closed) return
        if (bottomCollapsedHeightDp == collapsedHeightDp &&
            bottomExpandedHeightDp == expandedHeightDp
        ) return
        bottomCollapsedHeightDp = collapsedHeightDp
        bottomExpandedHeightDp = expandedHeightDp
        dispatch()
    }

    /** Supply folding information from an optional WindowManager adapter. */
    fun updateFoldingFeature(value: FoldingFeatureInfo) {
        if (!closed) observer.updateFoldingFeature(value)
    }

    /** Re-evaluate against the latest host bounds. */
    fun refresh() {
        if (!closed) observer.refresh()
    }

    private fun dispatch() {
        val window = currentWindow ?: return
        if (closed) return
        onLayoutResolved(
            SymbolDrawerDockingLayoutResolver.resolve(
                settings = currentSettings,
                window = window,
                bottomCollapsedHeightDp = bottomCollapsedHeightDp,
                bottomExpandedHeightDp = bottomExpandedHeightDp
            )
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        observer.close()
        currentWindow = null
    }
}
