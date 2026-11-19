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

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Parcel
import android.os.Parcelable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.google.android.material.tabs.TabLayout
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerPresentationMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchor
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorInput
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorResolver
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorState
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorStrategy
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerController
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerState
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerNavigationConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerNavigationController
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerNavigationPlacement
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerPage
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerPageHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelSlot
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerColors
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerDimensions
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerMotion
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerShape
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerStyle
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerThemeMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerThemeResolver
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerTypography
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable bottom drawer container. The host content, Peek panel and Expanded panel
 * are independent; page navigation is optional and remains feature-neutral.
 */
class DrawerContainer @JvmOverloads constructor(
    context: Context,
    config: DrawerConfig = DrawerConfig.builder().build()
) : FrameLayout(context), NestedScrollingParent3 {

    private val scrim = View(context)
    private val sheet = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val handle = View(context)
    private val handleTouchTarget = FrameLayout(context)
    private val title = TextView(context)
    private val tabs = TabLayout(context)
    private val peekHost = DrawerPanelHost(context, DrawerPanelSlot.PEEK)
    private val expandedHost = DrawerPanelHost(context, DrawerPanelSlot.EXPANDED)
    private val pageHost = DrawerPageHost(context)
    private var currentConfig = config
    private var anchors: List<DrawerAnchor> = emptyList()
    private var state: DrawerState? = null
    private var animator: ValueAnimator? = null
    private var velocityTracker: VelocityTracker? = null
    private var downY = 0f
    private var dragStartHeight = 0
    private var dragStartAnchorId: String? = null
    private var restoredAnchorId: String? = null
    private var restoredHeightPx: Int? = null
    private var restoredPageId: String? = null
    private data class PendingAnchorRequest(
        val anchorId: String?,
        val expanded: Boolean?,
        val animated: Boolean
    )
    private var pendingAnchorRequest: PendingAnchorRequest? = null
    private var dragging = false
    private var peekContentVisible = false
    private var expandedContentVisible = false
    private var containerAttached = false
    private var hostContent: View? = null
    private var systemBarTopInset = 0
    private var systemBarBottomInset = 0
    private val stateListeners = LinkedHashSet<(DrawerState) -> Unit>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val nestedScrollHelper = NestedScrollingParentHelper(this)
    // Track touch and fling (non-touch) sessions independently. A drawer may receive
    // overlapping sessions while a scrolling child transitions from drag to fling.
    private val activeNestedScrollTypes = LinkedHashSet<Int>()
    private val controllerImpl = Controller()
    private val navigationTabListener = DrawerNavigationController.Listener { _, selectedIndex ->
        if (selectedIndex in 0 until tabs.tabCount && tabs.selectedTabPosition != selectedIndex) {
            tabs.getTabAt(selectedIndex)?.select()
        }
    }

    val controller: DrawerController get() = controllerImpl
    val navigationHost: DrawerPageHost get() = pageHost

    init {
        clipChildren = false
        clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            systemBarTopInset = bars.top
            systemBarBottomInset = bars.bottom
            applyVisualStyle()
            recalculateAnchors()
            insets
        }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scrim.setOnClickListener {
            if (currentConfig.dismissOnScrimTap) controller.collapse()
        }

        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, dp(640), Gravity.BOTTOM))
        handleTouchTarget.apply {
            tag = "drawer_drag_handle"
            minimumHeight = dp(48)
            contentDescription = "Drag drawer"
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            addView(handle, FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER))
            setOnClickListener {
                val current = state?.currentHeightPx ?: anchors.firstOrNull()?.heightPx ?: 0
                val next = anchors.firstOrNull { it.heightPx > current }
                if (next != null) moveTo(next, true) else controller.collapse()
            }
        }
        sheet.addView(handleTouchTarget, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, dp(48)
        ))
        sheet.addView(title, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(8) })
        sheet.addView(tabs, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ))
        sheet.addView(peekHost, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, 0, 1f
        ))
        sheet.addView(expandedHost, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, 0, 1f
        ))
        expandedHost.addView(pageHost, FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ))
        handleTouchTarget.setOnTouchListener(::onHandleTouch)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.tag as? String)?.let(pageHost::selectPage)
                applyNavigationTypography()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        pageHost.navigationController.addListener(navigationTabListener)
        controllerImpl.applyConfig(config)
        post { recalculateAnchors() }
    }

    fun setMainContent(view: View?) {
        hostContent?.let { if (it.parent === this) removeView(it) }
        hostContent = view
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
            addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        bringChildToFront(scrim)
        bringChildToFront(sheet)
    }

    fun setTitle(value: CharSequence?) {
        title.text = value ?: ""
        title.visibility = if (title.text.isNullOrEmpty()) GONE else VISIBLE
        recalculateAnchors()
    }

    private fun contentThemeContext() = DrawerThemeResolver.contextFor(context, currentConfig.style)

    fun setPeekContent(content: DrawerPanelContent?) {
        currentConfig = currentConfig.copy(peekContent = content)
        val changed = peekHost.content() !== content
        peekHost.setContent(content, contentThemeContext())
        peekHost.visibility = if (content == null) GONE else VISIBLE
        if (changed) refreshPanelVisibilityCallbacks()
        recalculateAnchors()
    }

    fun setExpandedContent(content: DrawerPanelContent?) {
        currentConfig = currentConfig.copy(expandedContent = content)
        pageHost.visibility = if (content == null && pageHost.navigationController.pages().isNotEmpty()) VISIBLE else GONE
        val changed = expandedHost.content() !== content
        expandedHost.setContent(content, contentThemeContext())
        if (changed) refreshPanelVisibilityCallbacks()
        recalculateAnchors()
    }

    fun setPages(pages: List<DrawerPage>, preserveSelection: Boolean = true) {
        currentConfig = currentConfig.copy(expandedContent = null)
        expandedHost.content()?.let { expandedHost.setContent(null) }
        pageHost.visibility = VISIBLE
        pageHost.setPages(pages, preserveSelection)
        applyRestoredPageSelection()
        rebuildTabs()
        recalculateAnchors()
    }

    fun expand(animated: Boolean = true) = controller.expand(animated)
    fun collapse(animated: Boolean = true) = controller.collapse(animated)
    /** Re-measure panel content and resolve anchors after asynchronous content-size changes. */
    fun refreshAnchors() = recalculateAnchors()

    fun handleBack(): Boolean {
        if (!currentConfig.dismissOnBack) return false
        val current = state ?: return false
        if (current.currentHeightPx <= (anchors.firstOrNull()?.heightPx ?: 0)) return false
        collapse()
        return true
    }

    private fun rebuildTabs() {
        tabs.removeAllTabs()
        val navigation = currentConfig.navigationConfig
        pageHost.navigationController.pages().forEach { page ->
            val label = TextView(context).apply {
                text = if (navigation.showTitles) page.title else ""
                gravity = Gravity.CENTER
                textSize = navigation.titleTextSizeSp
                minWidth = dp(navigation.itemMinWidthDp)
                setPadding(
                    dp(navigation.itemHorizontalPaddingDp),
                    dp(navigation.itemVerticalPaddingDp),
                    dp(navigation.itemHorizontalPaddingDp),
                    dp(navigation.itemVerticalPaddingDp)
                )
                if (navigation.showIcons && page.icon != null) {
                    setCompoundDrawablesWithIntrinsicBounds(page.icon, null, null, null)
                    compoundDrawablePadding = dp(6)
                }
                contentDescription = page.title
            }
            val tab = tabs.newTab().setCustomView(label).setTag(page.id)
            tabs.addTab(tab, false)
        }
        val selected = pageHost.navigationController.selectedIndex()
        if (selected >= 0 && selected < tabs.tabCount) tabs.getTabAt(selected)?.select()
        updateNavigationVisibility()
        applyNavigationTypography()
    }

    private fun applyNavigationTypography() {
        val navigation = currentConfig.navigationConfig
        for (index in 0 until tabs.tabCount) {
            val tab = tabs.getTabAt(index) ?: continue
            val label = tab.customView as? TextView ?: continue
            val selected = tab.isSelected
            label.textSize = if (selected) navigation.selectedTitleTextSizeSp else navigation.titleTextSizeSp
            label.setTypeface(
                label.typeface,
                if (selected && navigation.selectedTitleBold) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL
            )
            label.setTextColor(
                if (selected) resolvedColors().accent ?: Color.BLUE
                else resolvedColors().secondaryText ?: Color.GRAY
            )
        }
    }

    private fun updateNavigationVisibility() {
        val nav = currentConfig.navigationConfig
        if (tabs.parent === sheet) sheet.removeView(tabs)
        val tabIndex = if (nav.placement == DrawerNavigationPlacement.BOTTOM) sheet.childCount else 2
        sheet.addView(tabs, tabIndex.coerceIn(0, sheet.childCount),
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        tabs.visibility = if (nav.enabled && nav.placement != DrawerNavigationPlacement.HIDDEN &&
            pageHost.visibility == VISIBLE && pageHost.navigationController.pages().isNotEmpty()) VISIBLE else GONE
        tabs.isTabIndicatorFullWidth = nav.indicator.widthFraction >= 0.99f
        tabs.tabMode = if (nav.scrollable) TabLayout.MODE_SCROLLABLE else TabLayout.MODE_FIXED
        tabs.tabGravity = if (nav.scrollable) TabLayout.GRAVITY_START else TabLayout.GRAVITY_FILL
        val indicatorColor = nav.indicator.color ?: resolvedColors().accent ?: Color.BLUE
        tabs.setSelectedTabIndicator(GradientDrawable().apply {
            setColor(if (nav.indicator.shape == com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerIndicatorShape.NONE) {
                Color.TRANSPARENT
            } else indicatorColor)
            cornerRadius = if (nav.indicator.shape == com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerIndicatorShape.PILL) {
                dp(nav.indicator.heightDp).toFloat()
            } else 0f
        })
        tabs.setSelectedTabIndicatorHeight(
            if (nav.indicator.shape == com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerIndicatorShape.NONE) 0
            else dp(nav.indicator.heightDp)
        )
        tabs.setTabTextColors(
            resolvedColors().secondaryText ?: Color.GRAY,
            resolvedColors().accent ?: Color.BLUE
        )
    }

    private fun applyVisualStyle() {
        val style = currentConfig.style
        val colors = resolvedColors()
        val fullScreen = currentConfig.presentationMode == DrawerPresentationMode.FULL_SCREEN
        val topStart = if (fullScreen) 0f else
            (style.shape.topStartRadiusDp.takeIf { it > 0f } ?: style.dimensions.cornerRadiusDp)
        val topEnd = if (fullScreen) 0f else
            (style.shape.topEndRadiusDp.takeIf { it > 0f } ?: style.dimensions.cornerRadiusDp)
        val bottomEnd = if (fullScreen) 0f else style.shape.bottomEndRadiusDp
        val bottomStart = if (fullScreen) 0f else style.shape.bottomStartRadiusDp
        sheet.background = GradientDrawable().apply {
            setColor(colors.surface ?: Color.WHITE)
            cornerRadii = floatArrayOf(dp(topStart).toFloat(), dp(topStart).toFloat(),
                dp(topEnd).toFloat(), dp(topEnd).toFloat(),
                dp(bottomEnd).toFloat(), dp(bottomEnd).toFloat(),
                dp(bottomStart).toFloat(), dp(bottomStart).toFloat())
            if (!fullScreen && style.shape.outlineWidthDp > 0f) {
                setStroke(dp(style.shape.outlineWidthDp), colors.outline ?: (colors.primaryText ?: Color.GRAY))
            }
        }
        handle.background = GradientDrawable().apply {
            setColor(colors.secondaryText ?: Color.GRAY)
            cornerRadius = dp(style.dimensions.handleHeightDp).toFloat()
        }
        title.setTextColor(colors.primaryText ?: Color.BLACK)
        title.textSize = style.typography.titleSizeSp
        title.typeface = style.typography.fontFamily?.let {
            android.graphics.Typeface.create(it, style.typography.titleStyle)
        } ?: android.graphics.Typeface.create(title.typeface, style.typography.titleStyle)
        sheet.clipToOutline = style.clipContentToShape
        // The handle is a child of handleTouchTarget (FrameLayout), not
        // the vertical sheet (LinearLayout). Never cast a View's params to
        // the parent's sibling layout type: Android supplies FrameLayout.LayoutParams here.
        val handleParams = handle.layoutParams ?: FrameLayout.LayoutParams(
            dp(style.dimensions.handleWidthDp),
            dp(style.dimensions.handleHeightDp),
            Gravity.CENTER
        )
        handleParams.width = dp(style.dimensions.handleWidthDp)
        handleParams.height = dp(style.dimensions.handleHeightDp)
        handle.layoutParams = handleParams
        sheet.elevation = if (fullScreen) 0f else dp(style.elevationDp).toFloat()
        handleTouchTarget.visibility = if (!currentConfig.dragHandleEnabled) GONE else VISIBLE
        val topInset = if (currentConfig.enableEdgeToEdge) systemBarTopInset else 0
        val bottomInset = if (currentConfig.enableEdgeToEdge) systemBarBottomInset else 0
        val verticalPadding = dp(style.dimensions.verticalPaddingDp)
        sheet.setPadding(
            dp(style.dimensions.horizontalPaddingDp),
            verticalPadding + topInset,
            dp(style.dimensions.horizontalPaddingDp),
            verticalPadding + bottomInset
        )
        scrim.setBackgroundColor(colors.scrim ?: Color.BLACK)
        scrim.visibility = if (style.drawScrim) VISIBLE else GONE
        title.visibility = if (title.text.isNullOrEmpty()) GONE else VISIBLE
        updateNavigationVisibility()
        updateVisualState()
        requestLayout()
    }

    private fun refreshPanelVisibilityCallbacks() {
        peekContentVisible = false
        expandedContentVisible = false
        updateVisualState()
    }

    private fun resolvedColors(): DrawerColors = DrawerThemeResolver.resolve(context, currentConfig.style)

    private fun recalculateAnchors() {
        if (height <= 0 || width <= 0) return
        // A measurement change invalidates the previous animation's pixel target.
        animator?.cancel()
        animator = null
        val density = resources.displayMetrics.density
        val maxHeight = if (currentConfig.presentationMode == DrawerPresentationMode.FULL_SCREEN) {
            height
        } else {
            min(height, dp(currentConfig.style.dimensions.expandedMaxHeightDp))
        }
        val navigation = currentConfig.navigationConfig
        val hasNavigationTabs = navigation.enabled &&
            navigation.placement != DrawerNavigationPlacement.HIDDEN &&
            pageHost.visibility == VISIBLE &&
            pageHost.navigationController.pages().isNotEmpty()
        val verticalPadding = dp(currentConfig.style.dimensions.verticalPaddingDp)
        val topInset = if (currentConfig.enableEdgeToEdge) systemBarTopInset else 0
        val bottomInset = if (currentConfig.enableEdgeToEdge) systemBarBottomInset else 0
        val collapsedChromeHeight = topInset + bottomInset + verticalPadding * 2 +
            (if (currentConfig.dragHandleEnabled &&
                currentConfig.presentationMode != DrawerPresentationMode.FULL_SCREEN) dp(48) else 0) +
            (if (title.text.isNullOrEmpty()) 0 else dp(44)) +
            (if (hasNavigationTabs && navigation.showInPeekPanel) dp(48) else 0)
        // Measure Peek before resolving the collapsed anchor. A fixed 48dp
        // reservation can clip real compact panels such as the mini player.
        val contentWidth = width
        val peekBodyHeight = if (contentWidth > 0 && peekHost.content() != null) {
            peekHost.measureContentHeight(contentWidth, maxHeight)
        } else {
            0
        }
        val peekBodyMinimum = if (peekHost.content() != null) maxOf(dp(48), peekBodyHeight) else 0
        val minHeight = min(
            maxHeight,
            max(dp(currentConfig.style.dimensions.peekHeightDp),
                collapsedChromeHeight + peekBodyMinimum)
        )
        sheet.layoutParams = sheet.layoutParams.apply { this.height = maxHeight }
        // Use the measured container width for split-screen and embedded layouts.
        if (contentWidth <= 0) return
        val bodyHeight = max(
            peekBodyHeight,
            max(
                expandedHost.measureContentHeight(contentWidth, maxHeight),
                pageHost.measureCurrentContentHeight(contentWidth, maxHeight)
            )
        )
        val insetHeight = if (currentConfig.enableEdgeToEdge) {
            systemBarTopInset + systemBarBottomInset
        } else 0
        val headerHeight = insetHeight +
            (if (currentConfig.dragHandleEnabled &&
                currentConfig.presentationMode != DrawerPresentationMode.FULL_SCREEN) dp(48) else 0) +
            (if (title.text.isNullOrEmpty()) 0 else dp(44)) +
            (if (hasNavigationTabs) dp(48) else 0) +
            verticalPadding * 2
        val contentHeight = (bodyHeight + headerHeight).coerceAtMost(maxHeight)
        anchors = DrawerAnchorResolver.resolve(
            currentConfig.anchorStrategy,
            DrawerAnchorInput(
                minHeightPx = minHeight,
                maxHeightPx = maxHeight,
                viewportHeightPx = height,
                contentHeightPx = contentHeight,
                existingExpandedHeightPx = maxHeight,
                density = density,
                viewportWidthPx = width
            )
        )
        val previousHeight = state?.currentHeightPx ?: restoredHeightPx ?: minHeight
        val boundedHeight = previousHeight.coerceIn(anchors.first().heightPx, anchors.last().heightPx)
        val restoredAnchor = restoredAnchorId?.let { id -> anchors.firstOrNull { it.id == id } }
        val nearest = restoredAnchor
            ?: DrawerAnchorResolver.nearestAnchor(anchors, boundedHeight)
        // Restored state is always normalized to a stable anchor. During ordinary
        // relayout, preserve the current continuous height to avoid visual jumps.
        val resolvedHeight = if (restoredAnchor != null) restoredAnchor.heightPx else boundedHeight
        restoredAnchorId = null
        restoredHeightPx = null
        state = DrawerState(nearest.id, resolvedHeight, anchors, false, false)
        updateVisualState()
        state?.let { snapshot -> stateListeners.toList().forEach { it(snapshot) } }
        pendingAnchorRequest?.let { request ->
            pendingAnchorRequest = null
            val target = when {
                request.expanded == true -> anchors.lastOrNull()
                request.expanded == false -> anchors.firstOrNull()
                else -> anchors.firstOrNull { it.id == request.anchorId }
            }
            target?.let { moveTo(it, request.animated) }
        }
    }

    private fun updateState(anchor: DrawerAnchor, isDragging: Boolean, isAnimating: Boolean) {
        state = DrawerState(anchor.id, anchor.heightPx, anchors.ifEmpty { listOf(anchor) }, isDragging, isAnimating)
        updateVisualState()
        val snapshot = state ?: return
        stateListeners.toList().forEach { it(snapshot) }
    }

    private fun updateVisualState() {
        val current = state ?: return
        val sheetHeight = (sheet.layoutParams?.height ?: sheet.measuredHeight)
            .takeIf { it > 0 } ?: current.currentHeightPx
        sheet.translationY = (sheetHeight - current.currentHeightPx).toFloat()
        val minHeight = anchors.firstOrNull()?.heightPx ?: 1
        val maxHeight = anchors.lastOrNull()?.heightPx ?: minHeight
        val fraction = if (maxHeight == minHeight) 0f else
            ((current.currentHeightPx - minHeight).toFloat() / (maxHeight - minHeight)).coerceIn(0f, 1f)
        scrim.alpha = fraction * 0.48f
        scrim.isClickable = fraction > 0.01f && currentConfig.dismissOnScrimTap
        val expanded = current.currentHeightPx > minHeight
        handleTouchTarget.contentDescription = when {
            current.currentHeightPx >= maxHeight - 1 -> "Collapse drawer"
            current.currentHeightPx > minHeight -> "Expand drawer further or collapse drawer"
            else -> "Expand drawer"
        }
        val isFullScreen = currentConfig.presentationMode == DrawerPresentationMode.FULL_SCREEN
        val isAtFullScreenAnchor = current.currentHeightPx >= maxHeight - 1
        handleTouchTarget.visibility = if (!currentConfig.dragHandleEnabled || isFullScreen) {
            GONE
        } else {
            VISIBLE
        }
        val showPeek = !expanded && peekHost.content() != null
        val showExpanded = expanded && (expandedHost.content() != null || pageHost.navigationController.pages().isNotEmpty())
        peekHost.visibility = if (showPeek) VISIBLE else GONE
        expandedHost.visibility = if (showExpanded) VISIBLE else GONE
        val navigation = currentConfig.navigationConfig
        tabs.visibility = if (navigation.enabled &&
            navigation.placement != DrawerNavigationPlacement.HIDDEN &&
            pageHost.visibility == VISIBLE &&
            pageHost.navigationController.pages().isNotEmpty() &&
            (expanded || navigation.showInPeekPanel)) VISIBLE else GONE
        if (containerAttached && showPeek != peekContentVisible) {
            peekHost.dispatchPanelVisible(showPeek)
            peekContentVisible = showPeek
        }
        pageHost.setPanelVisible(showExpanded && pageHost.visibility == VISIBLE)
        if (containerAttached && showExpanded != expandedContentVisible) {
            expandedHost.dispatchPanelVisible(showExpanded)
            expandedContentVisible = showExpanded
        }
    }

    private fun moveTo(anchor: DrawerAnchor, animated: Boolean) {
        if (anchors.none { it.id == anchor.id }) return
        animator?.cancel()
        val start = state?.currentHeightPx ?: anchor.heightPx
        if (!animated || currentConfig.style.motion.settleDurationMs == 0L) {
            updateState(anchor, false, false)
            return
        }
        animator = ValueAnimator.ofInt(start, anchor.heightPx).apply {
            duration = currentConfig.style.motion.settleDurationMs
            addUpdateListener { value ->
                val heightPx = value.animatedValue as Int
                val nearest = DrawerAnchorResolver.nearestAnchor(anchors, heightPx)
                state = DrawerState(nearest.id, heightPx, anchors, false, true)
                updateVisualState()
                state?.let { snapshot -> stateListeners.toList().forEach { it(snapshot) } }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) updateState(anchor, false, false)
                    if (animator === this@apply) animator = null
                }
            })
            start()
        }
    }

    private fun onHandleTouch(view: View, event: MotionEvent): Boolean {
        if (!currentConfig.enableDrag || anchors.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                downY = event.rawY
                dragStartHeight = state?.currentHeightPx ?: anchors.firstOrNull()?.heightPx ?: dp(56f)
                dragStartAnchorId = state?.currentAnchorId
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.rawY - downY
                if (!dragging && abs(dy) > touchSlop) dragging = true
                if (dragging) {
                    val resistance = currentConfig.style.motion.dragResistance
                    val proposed = (dragStartHeight - dy * resistance).toInt()
                    val minHeight = anchors.firstOrNull()?.heightPx ?: 0
                    val maxHeight = anchors.lastOrNull()?.heightPx ?: minHeight
                    // Keep a small resisted overshoot during the gesture. It
                    // settles to a stable anchor on release.
                    val overscrollLimit = dp(64)
                    val resisted = when {
                        proposed < minHeight -> minHeight -
                            min(overscrollLimit, ((minHeight - proposed) * 0.18f).toInt())
                        proposed > maxHeight -> maxHeight +
                            min(overscrollLimit, ((proposed - maxHeight) * 0.18f).toInt())
                        else -> proposed
                    }
                    val clamped = resisted.coerceAtLeast(0)
                    // Follow the pointer continuously. The nearest anchor is only
                    // semantic metadata during drag and never quantizes the height.
                    val dragAnchor = DrawerAnchorResolver.nearestAnchor(anchors, clamped)
                    state = DrawerState(dragAnchor.id, clamped, anchors, true, false)
                    updateVisualState()
                    stateListeners.toList().forEach { it(state!!) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                if (!cancelled) velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = if (cancelled) 0f else velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                val currentHeight = state?.currentHeightPx ?: dragStartHeight
                val maximumVelocity = dp(currentConfig.style.motion.maximumFlingVelocityDp).toFloat()
                val boundedVelocity = velocityY.coerceIn(-maximumVelocity, maximumVelocity)
                val wasDragging = dragging
                val minimumFlingVelocity =
                    dp(currentConfig.style.motion.minimumFlingVelocityDp).toFloat()
                val target = when {
                    cancelled -> anchors.firstOrNull { it.id == dragStartAnchorId }
                        ?: DrawerAnchorResolver.nearestAnchor(anchors, dragStartHeight)
                    wasDragging && abs(boundedVelocity) >= minimumFlingVelocity ->
                        DrawerAnchorResolver.flingTarget(
                            anchors, currentHeight, -boundedVelocity, minimumFlingVelocity
                        )
                    else -> DrawerAnchorResolver.nearestAnchor(anchors, currentHeight)
                }
                if (!wasDragging && !cancelled) view.performClick()
                else moveTo(target, true)
                dragging = false
                dragStartAnchorId = null
                return true
            }
        }
        return true
    }

    private fun updateHeightFromNestedScroll(heightPx: Int) {
        if (anchors.isEmpty()) return
        // A real nested-scroll gesture takes ownership from any pending settle.
        animator?.cancel()
        animator = null
        val minHeight = anchors.first().heightPx
        val maxHeight = anchors.last().heightPx
        val bounded = heightPx.coerceIn(minHeight, maxHeight)
        val nearest = DrawerAnchorResolver.nearestAnchor(anchors, bounded)
        state = DrawerState(nearest.id, bounded, anchors, isDragging = true, isAnimating = false)
        updateVisualState()
        state?.let { snapshot -> stateListeners.toList().forEach { it(snapshot) } }
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int): Boolean =
        child === sheet && currentConfig.enableNestedScroll &&
            (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean =
        onStartNestedScroll(child, target, axes)

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        nestedScrollHelper.onNestedScrollAccepted(child, target, axes)
        activeNestedScrollTypes += ViewCompat.TYPE_TOUCH
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        nestedScrollHelper.onNestedScrollAccepted(child, target, axes, type)
        activeNestedScrollTypes += type
    }

    override fun onStopNestedScroll(target: View) {
        nestedScrollHelper.onStopNestedScroll(target)
        activeNestedScrollTypes -= ViewCompat.TYPE_TOUCH
        settleAfterNestedScroll()
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        nestedScrollHelper.onStopNestedScroll(target, type)
        activeNestedScrollTypes -= type
        settleAfterNestedScroll()
    }

    private fun settleAfterNestedScroll() {
        val current = state ?: return
        // A nested pre-fling may already have started a directional settle
        // animation. Do not replace that target with a nearest-anchor decision
        // based on the animation's transient starting position.
        if (activeNestedScrollTypes.isNotEmpty()) return
        if (current.isAnimating || !current.isDragging || anchors.isEmpty()) return
        moveTo(DrawerAnchorResolver.nearestAnchor(anchors, current.currentHeightPx), true)
    }

    override fun getNestedScrollAxes(): Int = nestedScrollHelper.nestedScrollAxes

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (!currentConfig.enableNestedScroll || anchors.isEmpty() || dy == 0) return
        val current = state?.currentHeightPx ?: anchors.first().heightPx
        val minHeight = anchors.first().heightPx
        val maxHeight = anchors.last().heightPx
        val next = when {
            dy > 0 && current < maxHeight -> (current + dy).coerceAtMost(maxHeight)
            dy < 0 && !target.canScrollVertically(-1) && current > minHeight ->
                (current + dy).coerceAtLeast(minHeight)
            else -> current
        }
        if (next != current) {
            consumed[1] += next - current
            updateHeightFromNestedScroll(next)
        }
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int
    ) {
        onNestedScroll(
            target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
            ViewCompat.TYPE_TOUCH, IntArray(2)
        )
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, type: Int
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, IntArray(2))
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, type: Int, consumed: IntArray
    ) {
        if (!currentConfig.enableNestedScroll || anchors.isEmpty() || dyUnconsumed == 0) return
        val current = state?.currentHeightPx ?: anchors.first().heightPx
        val minHeight = anchors.first().heightPx
        val maxHeight = anchors.last().heightPx
        // Nested-scroll deltas use content coordinates: positive expands the
        // drawer, negative collapses it, matching onNestedPreScroll semantics.
        val next = (current + dyUnconsumed).coerceIn(minHeight, maxHeight)
        val drawerDelta = next - current
        if (drawerDelta != 0) {
            consumed[1] += drawerDelta
            updateHeightFromNestedScroll(next)
        }
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        if (!currentConfig.enableNestedScroll || anchors.size < 2 || velocityY == 0f) return false
        val current = state?.currentHeightPx ?: anchors.first().heightPx
        val maximum = anchors.last().heightPx
        val minimum = anchors.first().heightPx
        val canExpand = velocityY > 0f && current < maximum
        val canCollapse = velocityY < 0f && current > minimum && !target.canScrollVertically(-1)
        if (!canExpand && !canCollapse) return false
        val maximumVelocity = dp(currentConfig.style.motion.maximumFlingVelocityDp).toFloat()
        val minimumVelocity = dp(currentConfig.style.motion.minimumFlingVelocityDp).toFloat()
        val bounded = velocityY.coerceIn(-maximumVelocity, maximumVelocity)
        val targetAnchor = DrawerAnchorResolver.flingTarget(
            anchors, current, bounded, minimumVelocity
        )
        if (targetAnchor.heightPx == current) return false
        moveTo(targetAnchor, true)
        return true
    }

    override fun onNestedFling(
        target: View, velocityX: Float, velocityY: Float, consumed: Boolean
    ): Boolean {
        if (consumed) return false
        return onNestedPreFling(target, velocityX, velocityY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateAnchors()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val stableAnchor = state?.let { current ->
            if (current.isDragging || current.isAnimating) {
                DrawerAnchorResolver.nearestAnchor(anchors, current.currentHeightPx)
            } else {
                anchors.firstOrNull { it.id == current.currentAnchorId }
                    ?: DrawerAnchorResolver.nearestAnchor(anchors, current.currentHeightPx)
            }
        }
        return SavedState(superState).also { saved ->
            saved.anchorId = stableAnchor?.id
            saved.heightPx = stableAnchor?.heightPx ?: anchors.firstOrNull()?.heightPx ?: 0
            saved.pageId = pageHost.navigationController.snapshot().selectedPageId
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        restoredAnchorId = state.anchorId
        restoredHeightPx = state.heightPx.coerceAtLeast(0)
        restoredPageId = state.pageId
        // Pages may already have been installed by the host before Android
        // dispatches restoration. Apply immediately when possible; otherwise
        // setPages() will retry after the page collection becomes available.
        applyRestoredPageSelection()
        if (width > 0 && height > 0) recalculateAnchors()
    }

    private fun applyRestoredPageSelection() {
        val pageId = restoredPageId ?: return
        if (pageHost.selectPage(pageId)) {
            restoredPageId = null
            rebuildTabs()
        }
    }

    internal class SavedState : BaseSavedState {
        var anchorId: String? = null
        var heightPx: Int = 0
        var pageId: String? = null

        constructor(superState: Parcelable?) : super(superState)

        private constructor(source: Parcel) : super(source) {
            anchorId = source.readString()
            heightPx = source.readInt()
            pageId = source.readString()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(anchorId)
            out.writeInt(heightPx)
            out.writeString(pageId)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        containerAttached = true
        ViewCompat.requestApplyInsets(this)
        updateVisualState()
    }

    override fun onDetachedFromWindow() {
        if (peekContentVisible) peekHost.dispatchPanelVisible(false)
        if (expandedContentVisible) expandedHost.dispatchPanelVisible(false)
        peekContentVisible = false
        expandedContentVisible = false
        containerAttached = false
        animator?.cancel()
        animator = null
        // Nested child sessions are owned by the current window attachment.
        // Drop stale type markers when the hierarchy is detached so a later
        // attachment cannot remain permanently "actively nested".
        activeNestedScrollTypes.clear()
        state?.let { current ->
            val nearest = anchors.takeIf { it.isNotEmpty() }?.let {
                DrawerAnchorResolver.nearestAnchor(it, current.currentHeightPx)
            }
            if (nearest != null) state = DrawerState(nearest.id, current.currentHeightPx.coerceIn(anchors.first().heightPx, anchors.last().heightPx), anchors, false, false)
        }
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    private fun requestAnchor(anchorId: String?, expanded: Boolean?, animated: Boolean) {
        val target = when {
            expanded == true -> anchors.lastOrNull()
            expanded == false -> anchors.firstOrNull()
            else -> anchors.firstOrNull { it.id == anchorId }
        }
        if (target == null) {
            pendingAnchorRequest = PendingAnchorRequest(anchorId, expanded, animated)
        } else {
            pendingAnchorRequest = null
            moveTo(target, animated)
        }
    }

    private fun syncInteractionConfig() {
        handleTouchTarget.isEnabled = currentConfig.enableDrag
        handleTouchTarget.isClickable = currentConfig.enableDrag
        sheet.importantForAccessibility = if (currentConfig.accessibilityEnabled) {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private inner class Controller : DrawerController {
        override fun expand(animated: Boolean) = requestAnchor(null, true, animated)
        override fun collapse(animated: Boolean) = requestAnchor(null, false, animated)
        override fun moveToAnchor(anchorId: String, animated: Boolean) =
            requestAnchor(anchorId, null, animated)
        override fun refreshAnchors() = this@DrawerContainer.refreshAnchors()
        override fun setAnchorStrategy(strategy: DrawerAnchorStrategy) {
            currentConfig = currentConfig.copy(anchorStrategy = strategy)
            recalculateAnchors()
        }
        override fun setPresentationMode(mode: DrawerPresentationMode) {
            currentConfig = currentConfig.copy(presentationMode = mode)
            applyVisualStyle()
            recalculateAnchors()
        }
        override fun applyConfig(config: DrawerConfig) {
            currentConfig = config
            syncInteractionConfig()
            val themedContext = contentThemeContext()
            if (peekHost.content() === config.peekContent) peekHost.refreshContentContext(themedContext)
            else peekHost.setContent(config.peekContent, themedContext)
            if (expandedHost.content() === config.expandedContent) expandedHost.refreshContentContext(themedContext)
            else expandedHost.setContent(config.expandedContent, themedContext)
            pageHost.setContentContext(themedContext, recreateCachedViews = true)
            refreshPanelVisibilityCallbacks()
            pageHost.visibility = if (config.expandedContent == null) VISIBLE else GONE
            applyVisualStyle()
            recalculateAnchors()
        }
        override fun setStyle(style: DrawerStyle) {
            val themeChanged = currentConfig.style.themeMode != style.themeMode
            currentConfig = currentConfig.copy(style = style)
            if (themeChanged) {
                val themedContext = contentThemeContext()
                peekHost.refreshContentContext(themedContext)
                expandedHost.refreshContentContext(themedContext)
                pageHost.setContentContext(themedContext, recreateCachedViews = true)
                refreshPanelVisibilityCallbacks()
            }
            applyVisualStyle()
            recalculateAnchors()
        }
        override fun setThemeMode(mode: DrawerThemeMode) = setStyle(currentConfig.style.copy(themeMode = mode))
        override fun setColors(colors: DrawerColors) = setStyle(currentConfig.style.copy(colors = colors))
        override fun setDimensions(dimensions: DrawerDimensions) = setStyle(currentConfig.style.copy(dimensions = dimensions))
        override fun setTypography(typography: DrawerTypography) = setStyle(currentConfig.style.copy(typography = typography))
        override fun setMotion(motion: DrawerMotion) = setStyle(currentConfig.style.copy(motion = motion))
        override fun setShape(shape: DrawerShape) = setStyle(currentConfig.style.copy(shape = shape))
        override fun setPeekContent(content: DrawerPanelContent?) =
            this@DrawerContainer.setPeekContent(content)
        override fun setExpandedContent(content: DrawerPanelContent?) {
            currentConfig = currentConfig.copy(expandedContent = content)
            this@DrawerContainer.setExpandedContent(content)
        }
        override fun setTitle(title: CharSequence?) = this@DrawerContainer.setTitle(title)
        override fun setPages(pages: List<DrawerPage>, preserveSelection: Boolean) =
            this@DrawerContainer.setPages(pages, preserveSelection)
        override fun selectPage(pageId: String): Boolean = pageHost.selectPage(pageId)
        override fun setNavigationConfig(config: DrawerNavigationConfig) {
            currentConfig = currentConfig.copy(navigationConfig = config)
            applyVisualStyle()
            recalculateAnchors()
        }
        override fun setDragEnabled(enabled: Boolean) {
            currentConfig = currentConfig.copy(enableDrag = enabled)
            syncInteractionConfig()
        }
        override fun setNestedScrollEnabled(enabled: Boolean) { currentConfig = currentConfig.copy(enableNestedScroll = enabled) }
        override fun setEdgeToEdgeEnabled(enabled: Boolean) {
            if (currentConfig.enableEdgeToEdge == enabled) return
            currentConfig = currentConfig.copy(enableEdgeToEdge = enabled)
            ViewCompat.requestApplyInsets(this@DrawerContainer)
            applyVisualStyle()
            recalculateAnchors()
        }
        override fun setDismissOnScrimTap(enabled: Boolean) { currentConfig = currentConfig.copy(dismissOnScrimTap = enabled); updateVisualState() }
        override fun setDismissOnBack(enabled: Boolean) { currentConfig = currentConfig.copy(dismissOnBack = enabled) }
        override fun setDragHandleEnabled(enabled: Boolean) {
            if (currentConfig.dragHandleEnabled == enabled) return
            currentConfig = currentConfig.copy(dragHandleEnabled = enabled)
            updateVisualState()
            if (!enabled) handleTouchTarget.visibility = GONE
            // The handle is part of the collapsed chrome. Re-resolve the peek
            // anchor whenever it is shown or hidden so the visible sheet edge
            // and the measured touch target cannot drift apart.
            recalculateAnchors()
        }
        override fun setAccessibilityEnabled(enabled: Boolean) {
            currentConfig = currentConfig.copy(accessibilityEnabled = enabled)
            syncInteractionConfig()
        }
        override fun currentState(): DrawerState? = state
        override fun addStateListener(listener: (DrawerState) -> Unit) { stateListeners += listener; state?.let(listener) }
        override fun removeStateListener(listener: (DrawerState) -> Unit) { stateListeners -= listener }
    }
}
