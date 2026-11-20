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

package com.zerostudio.idea.widget.editor.symbolinput.v2.ui

import com.zerostudio.idea.widget.editor.symbolinput.v2.R

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.core.view.ViewCompat
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowAdaptiveInfo
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.AdaptiveGridResolver
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerExtensionRegistry
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerItemViewExtension
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerItemViewState
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem

/**
 * Material Symbols Outlined "settings" vector, rendered directly from Kotlin.
 *
 * Keeping the path in source avoids platform-dependent menu preference glyphs
 * and guarantees the drawer uses the same gear silhouette on every API level.
 */
internal class MaterialSettingsDrawable(
    color: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }
    private val path = PathParser.createPathFromPathData(
        "M9.25,22l-0.4-3.2q-0.32-0.13-0.61-0.3T7.68,18.13L4.7,19.38L1.95,14.63l2.58-1.95Q4.5,12.5 4.5,12.34q0-0.16 0-0.34t0-0.34t0.03-0.34L1.95,9.38L4.7,4.63L7.68,5.88Q7.95,5.68 8.25,5.5t0.6-0.3L9.25,2h5.5l0.4,3.2q0.33,0.13 0.61,0.3t0.56,0.38L19.3,4.63l2.75,4.75l-2.57,1.95q0.02,0.18 0.02,0.34t0,0.34t0,0.34t-0.05,0.34l2.57,1.95l-2.75,4.75l-2.95-1.25q-0.27,0.2-0.57,0.38t-0.6,0.3L14.75,22H9.25ZM11,20h1.98l0.35-2.65q0.78-0.2 1.44-0.59t1.21-0.94l2.47,1.03l0.98-1.7l-2.15-1.63q0.13-0.35 0.17-0.74T17.5,12t-0.05-0.79q-0.05-0.39-0.17-0.74l2.15-1.63l-0.98-1.7L15.98,8.2q-0.55-0.57-1.21-0.96T13.33,6.65L13,4H11.03L10.68,6.65Q9.9,6.85 9.24,7.24T8.03,8.17L5.55,7.15L4.58,8.85l2.15,1.6Q6.6,10.83 6.55,11.2T6.5,12q0,0.4 0.05,0.77t0.17,0.75L4.58,15.15l0.98,1.7L8.03,15.8q0.55,0.58 1.21,0.96t1.44,0.59L11,20Zm1.05-4.5q1.45,0 2.47-1.03T15.55,12T14.53,9.52T12.05,8.5q-1.47,0-2.49,1.02T8.55,12t1.01,2.47t2.49,1.03ZM12,12Z"
    ) ?: Path()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val source = RectF(0f, 0f, 24f, 24f)
        val target = RectF(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat())
        val matrix = Matrix().apply {
            setRectToRect(source, target, Matrix.ScaleToFit.CENTER)
        }
        val transformed = Path().apply { path.transform(matrix, this) }
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawPath(transformed, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** ViewPager2 page adapter backed by recycled grids and DiffUtil. */
internal class SymbolPageAdapter(
    private val config: DrawerConfig,
    private val settings: SymbolDrawerSettings,
    private val onSymbolAction: (SymbolEntry, Boolean) -> Unit,
    private val extensions: SymbolDrawerExtensionRegistry,
    private val legacyItemResolver: (SymbolEntry) -> SymbolItem,
    private val canScrollGridVertically: () -> Boolean = { true },
    private val themeProvider: () -> com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme? = { null }
) : ListAdapter<SymbolGroup, SymbolPageAdapter.PageHolder>(GROUP_DIFF) {

    data class GridSpacingState(
        var horizontalSpacingDp: Int,
        var verticalSpacingDp: Int,
        var fullLabelMinimumCellWidthDp: Float = 0f
    )

    private val recycledPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(0, config.columns * 4)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val spacingState = GridSpacingState(
            config.cellHorizontalSpacingDp,
            config.cellVerticalSpacingDp
        )
        val grid = RecyclerView(parent.context).apply {
            // ViewPager2 validates the direct page child's LayoutParams when it
            // attaches it. RecyclerView's generated default is not guaranteed
            // to be MATCH_PARENT in both dimensions, so declare the page
            // contract explicitly instead of relying on its default params.
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(themeProvider()?.surface ?: HostThemeColors.surface(context))
            val adaptiveGridLayout = object : GridLayoutManager(context, config.columns) {
                override fun canScrollVertically(): Boolean = canScrollGridVertically()
            }
            layoutManager = adaptiveGridLayout
            isNestedScrollingEnabled = false
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            itemAnimator = null
            setRecycledViewPool(recycledPool)
            setPadding(
                dp(context, config.cellHorizontalSpacingDp),
                dp(context, 4),
                dp(context, config.cellHorizontalSpacingDp),
                dp(context, 4)
            )
            clipToPadding = false
            var appliedHorizontalPadding = Int.MIN_VALUE
            addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                val gridView = view as? RecyclerView ?: return@addOnLayoutChangeListener
                val availableWidth = (right - left - view.paddingLeft - view.paddingRight).coerceAtLeast(1)
                if (availableWidth <= 0) return@addOnLayoutChangeListener
                val density = resources.displayMetrics.density
                val info = WindowAdaptiveInfo(
                    availableWidth / density,
                    (bottom - top).coerceAtLeast(0) / density,
                    density
                )
                val gridSpec = AdaptiveGridResolver.resolve(
                    window = info,
                    preferredColumns = config.columns,
                    minimumCellWidthDp = maxOf(36f, settings.minimumTouchTargetDp.toFloat(), spacingState.fullLabelMinimumCellWidthDp),
                    baseHorizontalSpacingDp = config.cellHorizontalSpacingDp,
                    adaptive = true,
                    baseVerticalSpacingDp = config.cellVerticalSpacingDp,
                    lockColumnCount = !settings.showFullSymbolLabels
                )
                if (adaptiveGridLayout.spanCount != gridSpec.columnCount) {
                    adaptiveGridLayout.spanCount = gridSpec.columnCount
                }
                spacingState.horizontalSpacingDp = gridSpec.horizontalSpacingDp
                spacingState.verticalSpacingDp = gridSpec.verticalSpacingDp
                val horizontal = dp(context, gridSpec.horizontalSpacingDp.coerceAtLeast(0))
                if (horizontal != appliedHorizontalPadding) {
                    appliedHorizontalPadding = horizontal
                    view.setPadding(horizontal, view.paddingTop, horizontal, view.paddingBottom)
                }
                for (index in 0 until gridView.childCount) {
                    val child = gridView.getChildAt(index)
                    val params = child.layoutParams as? RecyclerView.LayoutParams ?: continue
                    val halfHorizontal = dp(context, gridSpec.horizontalSpacingDp / 2)
                    val halfVertical = dp(context, gridSpec.verticalSpacingDp / 2)
                    if (params.marginStart != halfHorizontal ||
                        params.marginEnd != halfHorizontal ||
                        params.topMargin != halfVertical ||
                        params.bottomMargin != halfVertical
                    ) {
                        params.marginStart = halfHorizontal
                        params.marginEnd = halfHorizontal
                        params.topMargin = halfVertical
                        params.bottomMargin = halfVertical
                        child.layoutParams = params
                    }
                }
            }
        }
        return PageHolder(grid, config, settings, onSymbolAction, spacingState, extensions, legacyItemResolver, themeProvider)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.bind(position, getItem(position).symbols)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(ITEM_EXTENSION_PAYLOAD)) {
            holder.refreshItemExtensions()
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onViewRecycled(holder: PageHolder) {
        holder.clear()
    }

    /** Rebind visible cells after runtime renderer registrations change. */
    fun refreshItemExtensions() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, ITEM_EXTENSION_PAYLOAD)
    }

    class PageHolder(
        private val grid: RecyclerView,
        private val config: DrawerConfig,
        private val settings: SymbolDrawerSettings,
        onSymbolAction: (SymbolEntry, Boolean) -> Unit,
        private val spacingState: GridSpacingState,
        extensions: SymbolDrawerExtensionRegistry,
        legacyItemResolver: (SymbolEntry) -> SymbolItem,
        private val themeProvider: () -> com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme?
    ) : RecyclerView.ViewHolder(grid) {
        private val symbolsAdapter = SymbolAdapter(config, settings, onSymbolAction, spacingState, extensions, legacyItemResolver, themeProvider)
        init { grid.adapter = symbolsAdapter }
        fun bind(groupIndex: Int, symbols: List<SymbolEntry>) {
            // Theme extensions can change while this page holder remains alive.
            // Re-apply the live snapshot on every bind rather than only when the
            // RecyclerView is first created.
            grid.setBackgroundColor(
                themeProvider()?.surface ?: HostThemeColors.surface(grid.context)
            )
            val groupChanged = symbolsAdapter.groupIndex != groupIndex
            symbolsAdapter.groupIndex = groupIndex
            symbolsAdapter.submitList(symbols.toList()) {
                if (groupChanged) symbolsAdapter.refreshItemExtensions()
            }
            if (!settings.showFullSymbolLabels || !settings.showSymbolLabels) {
                spacingState.fullLabelMinimumCellWidthDp = 0f
                return
            }
            val density = grid.resources.displayMetrics.density.coerceAtLeast(0.1f)
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = settings.symbolLabelTextSizeSp * 0.75f *
                    grid.resources.displayMetrics.scaledDensity
            }
            val longest = symbols.asSequence()
                .filter { it.shortAction != SymbolAction.OpenManager }
                .map { paint.measureText(it.label) }
                .maxOrNull() ?: 0f
            spacingState.fullLabelMinimumCellWidthDp = maxOf(
                settings.minimumTouchTargetDp.toFloat(),
                longest / density + 8f
            )
            grid.post {
                val width = (grid.width - grid.paddingLeft - grid.paddingRight).coerceAtLeast(1)
                if (width <= 1) return@post
                val minimumWidthPx = spacingState.fullLabelMinimumCellWidthDp * density
                val columns = (width / minimumWidthPx).toInt().coerceIn(1, config.columns)
                val manager = grid.layoutManager as? GridLayoutManager ?: return@post
                if (manager.spanCount != columns) manager.spanCount = columns
            }
        }
        fun refreshItemExtensions() {
            grid.setBackgroundColor(
                themeProvider()?.surface ?: HostThemeColors.surface(grid.context)
            )
            symbolsAdapter.refreshItemExtensions()
        }
        fun clear() = symbolsAdapter.submitList(emptyList())
    }

    private class SymbolAdapter(
        private val config: DrawerConfig,
        private val settings: SymbolDrawerSettings,
        private val onSymbolAction: (SymbolEntry, Boolean) -> Unit,
        private val spacingState: GridSpacingState,
        private val extensions: SymbolDrawerExtensionRegistry,
        private val legacyItemResolver: (SymbolEntry) -> SymbolItem,
        private val themeProvider: () -> com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme?
    ) : ListAdapter<SymbolEntry, SymbolHolder>(SYMBOL_DIFF) {
        var groupIndex: Int = -1
        fun refreshItemExtensions() {
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolHolder {
            val container = android.widget.FrameLayout(parent.context)
            val label = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                setTextColor(themeProvider()?.onSurface ?: HostThemeColors.onSurface(context))
                val selectableBackground = TypedValue()
                if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectableBackground, true) && selectableBackground.resourceId != 0) {
                    setBackgroundResource(selectableBackground.resourceId)
                }
                textSize = settings.symbolLabelTextSizeSp.toFloat()
                isSingleLine = true
                maxLines = 1
                includeFontPadding = false
                ellipsize = if (settings.showFullSymbolLabels) null else android.text.TextUtils.TruncateAt.END
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    if (settings.showFullSymbolLabels) 6 else 8,
                    settings.symbolLabelTextSizeSp.coerceAtLeast(8),
                    1,
                    TypedValue.COMPLEX_UNIT_SP
                )
                isClickable = true
                isFocusable = true
                minHeight = dp(context, maxOf(config.cellHeightDp, config.minimumTouchTargetDp))
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            container.addView(label)
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(parent.context, maxOf(config.cellHeightDp, config.minimumTouchTargetDp))
            ).apply {
                marginStart = dp(parent.context, spacingState.horizontalSpacingDp / 2)
                marginEnd = dp(parent.context, spacingState.horizontalSpacingDp / 2)
                topMargin = dp(parent.context, spacingState.verticalSpacingDp / 2)
                bottomMargin = dp(parent.context, spacingState.verticalSpacingDp / 2)
            }
            return SymbolHolder(container, label) { id, error -> extensions.reportError(id, error) }
        }

        override fun onViewAttachedToWindow(holder: SymbolHolder) {
            super.onViewAttachedToWindow(holder)
            holder.attach()
        }

        override fun onViewDetachedFromWindow(holder: SymbolHolder) {
            holder.detach()
            super.onViewDetachedFromWindow(holder)
        }

        override fun onViewRecycled(holder: SymbolHolder) {
            holder.recycle()
            super.onViewRecycled(holder)
        }

        override fun onBindViewHolder(holder: SymbolHolder, position: Int) {
            val item = getItem(position)
            val legacyItem = legacyItemResolver(item)
            val theme = themeProvider()
            val state = SymbolDrawerItemViewState(
                groupIndex = groupIndex,
                position = position,
                selected = false,
                pressed = false,
                enabled = true,
                theme = theme
            )
            // Existing ViewHolders are rebound on theme changes. Update native
            // cell colors here so a theme swap is visible without recycling.
            holder.root.setBackgroundColor(
                theme?.surface ?: HostThemeColors.surface(holder.root.context)
            )
            holder.label.setTextColor(
                theme?.onSurface ?: HostThemeColors.onSurface(holder.label.context)
            )
            var extension = extensions.itemViews().firstOrNull { candidate ->
                runCatching { candidate.accepts(legacyItem) }.onFailure {
                    extensions.reportError(candidate.id, it)
                }.getOrDefault(false)
            }
            var custom: View? = null
            if (extension != null) {
                if (holder.activeExtension !== extension) {
                    holder.recycle()
                    custom = runCatching {
                        extension!!.createView(holder.root.context, legacyItem, state)
                    }.onFailure { extensions.reportError(extension!!.id, it) }.getOrNull()
                    if (custom != null) {
                        // Mark ownership before attaching so a failed attach still
                        // runs the extension's recycle hook before falling back.
                        holder.activeExtension = extension
                        holder.customView = custom
                        holder.boundLegacyItem = legacyItem.copy()
                        val attached = runCatching {
                            holder.root.removeAllViews()
                            holder.root.addView(custom, android.widget.FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }.onFailure { extensions.reportError(extension!!.id, it) }.isSuccess
                        if (!attached) {
                            holder.recycle()
                            extension = null
                            custom = null
                        }
                    } else {
                        extension = null
                    }
                } else {
                    custom = holder.customView
                }
                if (extension != null && custom != null) {
                    holder.boundLegacyItem = legacyItem.copy()
                    val bound = runCatching { extension!!.bindView(custom!!, legacyItem, state) }
                        .onFailure { extensions.reportError(extension!!.id, it) }.isSuccess
                    if (!bound) {
                        holder.recycle()
                        extension = null
                        custom = null
                    }
                } else {
                    holder.recycle()
                    extension = null
                }
            }

            val target: View
            if (extension != null && custom != null) {
                target = custom!!
            } else {
                holder.recycle()
                holder.root.removeAllViews()
                holder.root.addView(holder.label, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                val isSettingsAction = item.shortAction == SymbolAction.OpenManager
                if (isSettingsAction) {
                    holder.label.text = ""
                    val icon = MaterialSettingsDrawable(theme?.onSurface ?: HostThemeColors.onSurface(holder.label.context))
                    val size = dp(holder.label.context, 22)
                    icon.setBounds(0, 0, size, size)
                    holder.label.setCompoundDrawables(null, icon, null, null)
                    holder.label.contentDescription = holder.label.context.getString(R.string.symbol_drawer_accessibility_settings)
                } else {
                    holder.label.setCompoundDrawables(null, null, null, null)
                    holder.label.text = if (settings.showSymbolLabels) item.label else item.icon?.takeIf { it.isNotBlank() } ?: item.label
                    holder.label.contentDescription = if (settings.showActionHints) {
                        "${item.label}, ${item.shortAction}"
                    } else item.label
                }
                target = holder.label
            }
            val boundExtension = extension
            target.isClickable = true
            target.isFocusable = true
            holder.interactionTarget = target
            val hasLongPress = item.longAction != null ||
                (boundExtension?.let { candidate ->
                    runCatching { candidate.wantsLongPress(legacyItem) }
                        .onFailure { extensions.reportError(candidate.id, it) }.getOrDefault(false)
                } == true)
            target.setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION && getItem(current).id == item.id) {
                    boundExtension?.let { extension ->
                        runCatching { extension.onClick(target, legacyItem, state) }
                            .onFailure { extensions.reportError(extension.id, it) }
                    }
                    onSymbolAction(getItem(current), false)
                }
            }
            holder.accessibilityTarget?.let { previous ->
                holder.accessibilityLongActionId?.let { ViewCompat.removeAccessibilityAction(previous, it) }
            }
            holder.accessibilityTarget = target
            holder.accessibilityLongActionId = null
            val dispatchLongAction = {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION && getItem(current).id == item.id) {
                    boundExtension?.let { extension ->
                        runCatching {
                            extension.onLongPress(target, legacyItem, state.copy(pressed = true))
                        }.onFailure { extensions.reportError(extension.id, it) }
                    }
                    onSymbolAction(getItem(current), true)
                }
            }
            if (hasLongPress) {
                holder.accessibilityLongActionId = ViewCompat.addAccessibilityAction(
                    target, target.context.getString(R.string.symbol_drawer_accessibility_long_press_action)
                ) { _, _ -> dispatchLongAction(); true }
            }
            holder.touchListener?.cancel()
            holder.touchListener = SymbolCellTouchListener(
                view = target,
                longPressTimeoutMs = settings.longPressTimeoutMs,
                hasLongAction = hasLongPress,
                onLongPress = dispatchLongAction,
                onCancelled = {
                    boundExtension?.let { extension ->
                        runCatching {
                            extension.onStateChanged(
                                target,
                                legacyItem,
                                state.copy(pressed = false, enabled = target.isEnabled)
                            )
                        }.onFailure { extensions.reportError(extension.id, it) }
                    }
                },
                onTouch = { event ->
                    if (boundExtension != null) {
                        val itemState = state.copy(
                            pressed = event.actionMasked != MotionEvent.ACTION_UP &&
                                event.actionMasked != MotionEvent.ACTION_CANCEL &&
                                event.actionMasked != MotionEvent.ACTION_POINTER_DOWN,
                            enabled = target.isEnabled
                        )
                        runCatching { boundExtension.onStateChanged(target, legacyItem, itemState) }
                            .onFailure { extensions.reportError(boundExtension.id, it) }
                        runCatching {
                            boundExtension.onTouch(
                                target, legacyItem,
                                itemState,
                                event
                            )
                        }.onFailure { extensions.reportError(boundExtension.id, it) }
                    }
                }
            )
            target.setOnTouchListener(holder.touchListener)
            // A payload rebind can replace the renderer while the holder is
            // already attached; attach the newly-created contribution exactly once.
            if (holder.root.isAttachedToWindow) holder.attach()
        }
    }

    /**
     * Uses the configured timeout rather than the platform-global long-press
     * timeout. The touch stream is owned by this listener so a completed long
     * press cannot also dispatch the short action on ACTION_UP.
     */
    private class SymbolCellTouchListener(
        private val view: View,
        private val longPressTimeoutMs: Int,
        private val hasLongAction: Boolean,
        private val onLongPress: () -> Unit,
        private val onCancelled: () -> Unit,
        private val onTouch: (MotionEvent) -> Unit
    ) : View.OnTouchListener {
        private val touchSlop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var cancelled = false
        private var longPressed = false
        private val longPressTask = Runnable {
            if (!cancelled && view.isPressed && hasLongAction) {
                // Clear the pressed state before dispatching the long action so
                // the TextView's native ACTION_UP handling cannot also click.
                longPressed = true
                view.isPressed = false
                onLongPress()
                onCancelled()
            }
        }

        private fun cancelGesture() {
            if (!cancelled) onCancelled()
            cancelled = true
            view.removeCallbacks(longPressTask)
            view.isPressed = false
        }

        fun cancel() {
            cancelGesture()
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            onTouch(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    cancelled = false
                    longPressed = false
                    if (hasLongAction) view.postDelayed(longPressTask, longPressTimeoutMs.toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!cancelled && (
                        kotlin.math.abs(event.x - downX) > touchSlop ||
                            kotlin.math.abs(event.y - downY) > touchSlop
                    )) {
                        cancelGesture()
                        // Keep the normal View/RecyclerView touch pipeline alive:
                        // scrolling and ViewPager2 swipes must be able to take over.
                        view.isPressed = false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPressTask)
                    if (cancelled || longPressed) view.isPressed = false
                    cancelled = true
                    // Do not synthesize a click here. Returning false lets the
                    // TextView perform its normal click exactly once for taps.
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelGesture()
                }
            }
            // Never steal the stream from RecyclerView/ViewPager2. The host view
            // remains responsible for taps; this listener only observes timing.
            return false
        }
    }

    private class SymbolHolder(
        val root: android.widget.FrameLayout,
        val label: TextView,
        private val reportExtensionError: (String, Throwable) -> Unit
    ) : RecyclerView.ViewHolder(root) {
        var activeExtension: SymbolDrawerItemViewExtension? = null
        var customView: View? = null
        var boundLegacyItem: SymbolItem? = null
        var touchListener: SymbolCellTouchListener? = null
        var accessibilityLongActionId: Int? = null
        var accessibilityTarget: View? = null
        var interactionTarget: View? = null
        private var attachedToWindow = false

        fun attach() {
            if (attachedToWindow) return
            val extension = activeExtension ?: return
            val view = customView ?: return
            val item = boundLegacyItem ?: return
            attachedToWindow = true
            runCatching { extension.onViewAttachedToWindow(view, item) }
                .onFailure { reportExtensionError(extension.id, it) }
        }

        fun detach() {
            touchListener?.cancel()
            if (!attachedToWindow) return
            attachedToWindow = false
            val extension = activeExtension ?: return
            val view = customView ?: return
            val item = boundLegacyItem ?: return
            runCatching { extension.onViewDetachedFromWindow(view, item) }
                .onFailure { reportExtensionError(extension.id, it) }
        }

        fun recycle() {
            detach()
            touchListener?.cancel()
            touchListener = null
            val extension = activeExtension
            val view = customView
            if (extension != null && view != null) {
                runCatching { extension.onViewRecycled(view, boundLegacyItem) }
                    .onFailure { reportExtensionError(extension.id, it) }
            }
            activeExtension = null
            customView = null
            boundLegacyItem = null
            accessibilityTarget?.let { target ->
                accessibilityLongActionId?.let { ViewCompat.removeAccessibilityAction(target, it) }
                target.setOnClickListener(null)
                target.setOnTouchListener(null)
            }
            accessibilityTarget = null
            accessibilityLongActionId = null
            interactionTarget = null
            root.setOnClickListener(null)
            root.setOnTouchListener(null)
            label.setOnClickListener(null)
            label.setOnTouchListener(null)
        }
    }

    private companion object {
        val ITEM_EXTENSION_PAYLOAD = Any()
        val GROUP_DIFF = object : DiffUtil.ItemCallback<SymbolGroup>() {
            override fun areItemsTheSame(old: SymbolGroup, new: SymbolGroup) = old.id == new.id
            override fun areContentsTheSame(old: SymbolGroup, new: SymbolGroup) = old == new
        }
        val SYMBOL_DIFF = object : DiffUtil.ItemCallback<SymbolEntry>() {
            override fun areItemsTheSame(old: SymbolEntry, new: SymbolEntry) = old.id == new.id
            override fun areContentsTheSame(old: SymbolEntry, new: SymbolEntry) = old == new
        }
        fun dp(context: Context, value: Int) =
            (value * context.resources.displayMetrics.density).toInt()

        // Colors always follow the host Activity theme.
    }
}
