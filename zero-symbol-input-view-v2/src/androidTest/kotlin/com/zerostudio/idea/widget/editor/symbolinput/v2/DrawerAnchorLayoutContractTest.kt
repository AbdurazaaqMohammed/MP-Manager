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
package com.zerostudio.idea.widget.editor.symbolinput.v2

import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.DrawerContainer
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config.DrawerPresentationMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchor
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorState
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorStrategies
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.style.DrawerDimensions
import com.zerostudio.idea.widget.editor.symbolinput.v2.testing.DrawerTestHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerAnchorLayoutContractTest {
    private fun attachedDrawer(activity: DrawerTestHostActivity): DrawerContainer {
        val drawer = DrawerContainer(activity)
        activity.content.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        val width = activity.resources.displayMetrics.widthPixels
        val height = activity.resources.displayMetrics.heightPixels
        drawer.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        drawer.layout(0, 0, width, height)
        return drawer
    }

    @Test fun fullScreenThreeStageUsesMeasuredHeightAndThreeDistinctStops() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = attachedDrawer(activity)
                drawer.controller.setPresentationMode(DrawerPresentationMode.FULL_SCREEN)
                drawer.controller.setAnchorStrategy(DrawerAnchorStrategies.threeStage(0.5f, false))
                drawer.refreshAnchors()
                val anchors = requireNotNull(drawer.controller.currentState()).anchors
                assertEquals(listOf("peek", "half", "expanded"), anchors.map { it.id })
                assertTrue(anchors[0].heightPx < anchors[1].heightPx)
                assertTrue(anchors[1].heightPx < anchors[2].heightPx)
                assertEquals(drawer.height, anchors.last().heightPx)
                drawer.controller.moveToAnchor("expanded", false)
                assertEquals(drawer.height, drawer.controller.currentState()?.currentHeightPx)
                drawer.controller.moveToAnchor("half", false)
                assertEquals(anchors[1].heightPx, drawer.controller.currentState()?.currentHeightPx)
            }
        }
    }

    @Test fun peekAnchorReservesTheMeasuredHeightOfItsContent() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = attachedDrawer(activity)
                drawer.setTitle("")
                drawer.controller.setPages(emptyList())
                drawer.controller.setExpandedContent(null)
                drawer.controller.setDragHandleEnabled(true)
                val density = activity.resources.displayMetrics.density
                val peekBodyHeight = Math.round(96f * density)
                drawer.setPeekContent(object : DrawerPanelContent {
                    override fun createView(): View = View(activity).apply {
                        minimumHeight = peekBodyHeight
                    }
                })
                drawer.controller.setAnchorStrategy(DrawerAnchorStrategies.threeStage(0.5f, false))
                drawer.refreshAnchors()

                val peek = requireNotNull(drawer.controller.currentState())
                    .anchors.first { it.id == "peek" }
                val minimumVisibleHeight = peekBodyHeight + Math.round(48f * density)
                assertTrue(
                    "The collapsed anchor must reserve the complete measured peek panel below the drag handle",
                    peek.heightPx >= minimumVisibleHeight
                )
            }
        }
    }

    @Test fun fullScreenPeekDoesNotReserveHeightForItsHiddenHandle() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = attachedDrawer(activity)
                drawer.setTitle("")
                drawer.setPeekContent(null)
                drawer.controller.setExpandedContent(null)
                drawer.controller.setPages(emptyList())
                drawer.controller.setPresentationMode(DrawerPresentationMode.FULL_SCREEN)
                drawer.controller.setAnchorStrategy(DrawerAnchorStrategies.threeStage(0.5f, false))
                drawer.refreshAnchors()

                val peek = requireNotNull(drawer.controller.currentState())
                    .anchors.first { it.id == "peek" }
                val expectedPeekHeight = Math.round(56f * activity.resources.displayMetrics.density)
                assertEquals(expectedPeekHeight, peek.heightPx)
                assertEquals(View.GONE, findView(drawer) { it.tag == "drawer_drag_handle" }.visibility)
            }
        }
    }

    @Test fun customFourAnchorsRemainDistinctInsideBottomSheetBounds() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = attachedDrawer(activity)
                val density = activity.resources.displayMetrics.density
                val maxHeight = minOf(drawer.height, (520f * density).toInt())
                drawer.controller.setDimensions(DrawerDimensions(
                    peekHeightDp = 64f, expandedMaxHeightDp = 520f
                ))
                drawer.controller.setAnchorStrategy(DrawerAnchorStrategies.custom(listOf(
                    DrawerAnchor("peek", (64f * density).toInt(), DrawerAnchorState.PEEK),
                    DrawerAnchor("compact", (maxHeight * 0.34f).toInt(), DrawerAnchorState.CUSTOM),
                    DrawerAnchor("half", (maxHeight * 0.62f).toInt(), DrawerAnchorState.HALF_EXPANDED),
                    DrawerAnchor("expanded", maxHeight, DrawerAnchorState.EXPANDED)
                )))
                drawer.refreshAnchors()
                val anchors = requireNotNull(drawer.controller.currentState()).anchors
                assertEquals(listOf("peek", "compact", "half", "expanded"), anchors.map { it.id })
                assertEquals(4, anchors.map { it.heightPx }.distinct().size)
                assertEquals(maxHeight, anchors.last().heightPx)
            }
        }
    }


    @Test fun collapsedHandleIsBottomAlignedAndReceivesDragGestures() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = attachedDrawer(activity)
                drawer.setTitle("")
                drawer.setPeekContent(null)
                drawer.controller.setExpandedContent(null)
                drawer.controller.setPages(emptyList())
                drawer.controller.setDragEnabled(true)
                drawer.controller.setDragHandleEnabled(true)
                drawer.controller.setAnchorStrategy(DrawerAnchorStrategies.threeStage(0.5f, false))
                drawer.refreshAnchors()
                drawer.controller.moveToAnchor("peek", false)

                val handle = findView(drawer) { it.tag == "drawer_drag_handle" }
                assertEquals(View.VISIBLE, handle.visibility)
                val drawerLocation = IntArray(2)
                val handleLocation = IntArray(2)
                drawer.getLocationOnScreen(drawerLocation)
                handle.getLocationOnScreen(handleLocation)
                val handleBottom = handleLocation[1] + handle.height - drawerLocation[1]
                val bottomGap = drawer.height - handleBottom
                val navigationInset = ViewCompat.getRootWindowInsets(drawer)
                    ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                    ?.bottom ?: 0
                val allowedGap = navigationInset +
                    (32f * activity.resources.displayMetrics.density).toInt()
                assertTrue("Collapsed drag handle should remain just above the navigation inset", bottomGap in 0..allowedGap)

                val initialHeight = requireNotNull(drawer.controller.currentState()).currentHeightPx
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
                handle.dispatchTouchEvent(down)
                down.recycle()
                val move = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_MOVE, 20f, -120f, 0)
                handle.dispatchTouchEvent(move)
                move.recycle()
                assertTrue("Dragging the visible handle must update drawer height",
                    requireNotNull(drawer.controller.currentState()).currentHeightPx > initialHeight)
                val up = MotionEvent.obtain(downTime, downTime + 120, MotionEvent.ACTION_UP, 20f, -120f, 0)
                handle.dispatchTouchEvent(up)
                up.recycle()
            }
        }
    }

    private fun findView(root: View, predicate: (View) -> Boolean): View {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                val child = runCatching { findView(root.getChildAt(index), predicate) }.getOrNull()
                if (child != null) return child
            }
        }
        error("Required drawer view not found")
    }

    @Test fun contentBasedAnchorTracksMeasuredPanelContentChanges() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = attachedDrawer(activity)
                drawer.controller.setPages(emptyList())
                fun panel(lines: Int) = object : DrawerPanelContent {
                    override fun createView(): View = TextView(activity).apply {
                        text = (1..lines).joinToString("\n") { "Content row $it" }
                    }
                }
                drawer.controller.setExpandedContent(panel(2))
                drawer.controller.setAnchorStrategy(DrawerAnchorStrategies.contentBased())
                drawer.refreshAnchors()
                val shortHeight = requireNotNull(drawer.controller.currentState()).anchors.last().heightPx
                drawer.controller.setExpandedContent(panel(24))
                drawer.refreshAnchors()
                val longHeight = requireNotNull(drawer.controller.currentState()).anchors.last().heightPx
                assertTrue("Longer content must produce a taller content anchor", longHeight > shortHeight)
            }
        }
    }
}
