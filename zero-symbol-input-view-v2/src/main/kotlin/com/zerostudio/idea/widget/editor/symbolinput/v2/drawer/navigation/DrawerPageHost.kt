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

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent

/**
 * Generic page content host. It owns view attachment and visibility lifecycle,
 * while selection remains in DrawerNavigationController.
 */
class DrawerPageHost @JvmOverloads constructor(
    context: Context,
    val navigationController: DrawerNavigationController = DrawerNavigationController()
) : FrameLayout(context) {
    private data class CachedPageView(val content: DrawerPanelContent, val view: View)
    private val pageViews = LinkedHashMap<String, CachedPageView>()
    private var contentContext: Context = context
    private var visiblePageId: String? = null
    // Keep the exact content instance: a page may be replaced while retaining
    // its stable ID, and ID-only comparisons would leave the old view mounted.
    private var visiblePageContent: DrawerPanelContent? = null
    private var hostAttached = false
    private var panelVisible = false
    private var disposed = false
    private val navigationListener = DrawerNavigationController.Listener { snapshot, _ ->
        reconcilePageCache()
        render(snapshot.selectedPageId)
    }

    init {
        clipChildren = false
        clipToPadding = false
        navigationController.addListener(navigationListener)
    }

    fun setPages(pages: List<DrawerPage>, preserveSelection: Boolean = true) {
        check(!disposed) { "DrawerPageHost has been disposed" }
        // Validate before invoking lifecycle callbacks or evicting cached views.
        // A rejected update must leave the currently rendered page untouched.
        require(pages.map { it.id }.distinct().size == pages.size) {
            "Drawer page IDs must be unique"
        }
        navigationController.submitPages(pages, preserveSelection)
    }

    fun setContentContext(value: Context, recreateCachedViews: Boolean = false) {
        check(!disposed) { "DrawerPageHost has been disposed" }
        contentContext = value
        if (!recreateCachedViews || pageViews.isEmpty()) return
        val selectedId = visiblePageId
        if (hostAttached && panelVisible) visiblePageContent?.onPanelHidden()
        pageViews.values.forEach { cached ->
            cached.content.onPanelDetached()
            if (cached.view.parent === this) removeView(cached.view)
        }
        pageViews.clear()
        visiblePageId = null
        visiblePageContent = null
        render(selectedId)
    }

    fun selectPage(id: String): Boolean {
        check(!disposed) { "DrawerPageHost has been disposed" }
        return navigationController.selectPage(id)
    }

    fun selectPage(index: Int): Boolean {
        check(!disposed) { "DrawerPageHost has been disposed" }
        return navigationController.selectPage(index)
    }
    fun currentPage(): DrawerPage? = navigationController.selectedPage()

    fun measureCurrentContentHeight(maxWidthPx: Int, maxHeightPx: Int): Int {
        val selectedId = navigationController.snapshot().selectedPageId ?: return 0
        val view = pageViews[selectedId]?.view ?: return 0
        view.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidthPx.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeightPx.coerceAtLeast(0), View.MeasureSpec.AT_MOST)
        )
        return view.measuredHeight
    }

    /**
     * Reconcile cached views on every controller submission, not only calls
     * routed through setPages(). navigationController is intentionally public,
     * so hosts may submit replacement providers directly.
     */
    private fun reconcilePageCache() {
        val currentPages = navigationController.pages().associateBy { it.id }
        pageViews.toList().forEach { (id, cached) ->
            val current = currentPages[id]
            if (current == null || current.content !== cached.content) {
                if (id == visiblePageId) {
                    if (hostAttached && panelVisible) cached.content.onPanelHidden()
                    visiblePageContent = null
                }
                cached.content.onPanelDetached()
                if (cached.view.parent === this) removeView(cached.view)
                pageViews.remove(id)
            }
        }
    }

    private fun render(pageId: String?) {
        val page = navigationController.pages().firstOrNull { it.id == pageId }
        if (pageId == visiblePageId && page?.content === visiblePageContent) return

        val previousContent = visiblePageContent
        if (hostAttached && panelVisible) previousContent?.onPanelHidden()
        if (visiblePageId != null) {
            pageViews[visiblePageId]?.view?.let { old ->
                if (old.parent === this) removeView(old)
            }
        }

        // If the selected page keeps its ID but swaps its content object,
        // invalidate the old cached view and its attachment lifecycle.
        if (pageId == visiblePageId && previousContent != null &&
            page?.content !== previousContent) {
            previousContent.onPanelDetached()
            pageViews.remove(visiblePageId)
        }

        visiblePageId = pageId
        visiblePageContent = page?.content
        page ?: return
        val cached = pageViews[page.id]
        val entry = cached ?: CachedPageView(page.content, page.content.createView(contentContext))
            .also { pageViews[page.id] = it }
        val view = entry.view
        (view.parent as? ViewGroup)?.removeView(view)
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (cached == null) page.content.onPanelAttached()
        if (hostAttached && panelVisible) page.content.onPanelVisible()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hostAttached = true
        if (panelVisible) dispatchCurrentPageVisible(true)
    }

    override fun onDetachedFromWindow() {
        if (hostAttached && panelVisible) dispatchCurrentPageVisible(false)
        hostAttached = false
        super.onDetachedFromWindow()
    }

    fun setPanelVisible(visible: Boolean) {
        if (disposed || panelVisible == visible) return
        panelVisible = visible
        if (hostAttached) dispatchCurrentPageVisible(visible)
    }

    private fun dispatchCurrentPageVisible(visible: Boolean) {
        val page = navigationController.pages().firstOrNull { it.id == visiblePageId } ?: return
        if (visible) page.content.onPanelVisible() else page.content.onPanelHidden()
    }

    private fun pagesOrCurrent(id: String): DrawerPage? =
        navigationController.pages().firstOrNull { it.id == id }

    fun dispose() {
        if (disposed) return
        disposed = true
        navigationController.removeListener(navigationListener)
        pageViews.forEach { (id, cached) ->
            if (hostAttached && panelVisible && id == visiblePageId) {
                cached.content.onPanelHidden()
            }
            cached.content.onPanelDetached()
        }
        // Mark the panel hidden before detachment can arrive later. This makes
        // dispose() idempotent and prevents a second onPanelHidden callback.
        panelVisible = false
        pageViews.values.forEach { cached ->
            if (cached.view.parent === this) removeView(cached.view)
        }
        pageViews.clear()
        visiblePageId = null
        visiblePageContent = null
    }
}
