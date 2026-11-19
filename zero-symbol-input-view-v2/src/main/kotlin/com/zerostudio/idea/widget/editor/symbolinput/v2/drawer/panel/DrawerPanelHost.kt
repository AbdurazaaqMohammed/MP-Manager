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

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Minimal replaceable content host. It deliberately knows nothing about
 * symbols, pages, repositories or action execution.
 */
class DrawerPanelHost @JvmOverloads constructor(
    context: Context,
    val slot: DrawerPanelSlot,
    private val contentDescriptionLabel: CharSequence? = null
) : FrameLayout(context) {
    private var panelContent: DrawerPanelContent? = null
    private var contentView: View? = null
    private var panelVisible = false

    init {
        contentDescription = contentDescriptionLabel
        clipChildren = false
        clipToPadding = false
    }

    fun setContent(content: DrawerPanelContent?) = setContent(content, context)

    fun setContent(content: DrawerPanelContent?, viewContext: Context) {
        if (panelContent === content && contentView != null) return
        replaceContent(content, viewContext)
    }

    fun refreshContentContext(viewContext: Context) {
        val current = panelContent ?: return
        replaceContent(current, viewContext)
    }

    private fun replaceContent(content: DrawerPanelContent?, viewContext: Context) {
        if (panelVisible) panelContent?.onPanelHidden()
        panelVisible = false
        panelContent?.onPanelDetached()
        contentView?.let { old ->
            if (old.parent === this) removeView(old)
        }
        panelContent = content
        contentView = content?.createView(viewContext)?.also { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            content.onPanelAttached()
        }
    }

    fun measureContentHeight(maxWidthPx: Int, maxHeightPx: Int): Int {
        val view = contentView ?: return 0
        // Measure container content under the sheet's bound so large lists do
        // not trigger an unbounded child traversal. A plain leaf View is the
        // exception: View.getDefaultSize() treats AT_MOST as fill and would make
        // a 96dp minimum-height Peek view report the entire maximum sheet height.
        val heightSpec = if (view is ViewGroup) {
            View.MeasureSpec.makeMeasureSpec(
                maxHeightPx.coerceAtLeast(0),
                View.MeasureSpec.AT_MOST
            )
        } else {
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidthPx.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            heightSpec
        )
        return view.measuredHeight.coerceIn(0, maxHeightPx.coerceAtLeast(0))
    }

    fun content(): DrawerPanelContent? = panelContent

    fun dispatchPanelVisible(visible: Boolean) {
        if (panelVisible == visible) return
        panelVisible = visible
        if (visible) panelContent?.onPanelVisible() else panelContent?.onPanelHidden()
    }

    override fun onDetachedFromWindow() {
        dispatchPanelVisible(false)
        super.onDetachedFromWindow()
    }
}
