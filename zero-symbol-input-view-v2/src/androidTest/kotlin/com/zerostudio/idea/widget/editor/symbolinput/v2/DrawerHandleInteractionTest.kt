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

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerAnchorStrategies
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.DrawerContainer
import com.zerostudio.idea.widget.editor.symbolinput.v2.testing.DrawerTestHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class DrawerHandleInteractionTest {

    @Test
    fun handleHitTargetTracksTheVisibleBottomSheetEdgeAndAcceptsVerticalDrag() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = DrawerContainer(activity)
                activity.content.addView(
                    drawer,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                val width = activity.resources.displayMetrics.widthPixels
                val height = activity.resources.displayMetrics.heightPixels
                drawer.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                drawer.layout(0, 0, width, height)

                val state = requireNotNull(drawer.controller.currentState())
                val sheet = drawer.getChildAt(drawer.childCount - 1) as LinearLayout
                val handleTarget = sheet.getChildAt(0)
                val visibleSheetTop = sheet.top + sheet.translationY
                assertEquals(
                    drawer.height - state.currentHeightPx,
                    visibleSheetTop.roundToInt()
                )
                assertTrue(handleTarget.isShown)
                assertTrue(handleTarget.isEnabled)
                assertTrue(handleTarget.height >= (48f * activity.resources.displayMetrics.density).toInt())
                assertTrue(
                    sheet.top + sheet.translationY + handleTarget.bottom <= drawer.height
                )

                val startHeight = state.currentHeightPx
                val downTime = SystemClock.uptimeMillis()
                fun send(action: Int, y: Float, timeOffset: Long) {
                    val event = MotionEvent.obtain(
                        downTime,
                        downTime + timeOffset,
                        action,
                        handleTarget.width / 2f,
                        y,
                        0
                    )
                    try {
                        handleTarget.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
                send(MotionEvent.ACTION_DOWN, 24f, 0)
                send(MotionEvent.ACTION_MOVE, -96f, 80)
                val draggedHeight = requireNotNull(drawer.controller.currentState()).currentHeightPx
                assertTrue("Dragging the visible handle upward must increase drawer height", draggedHeight > startHeight)
                send(MotionEvent.ACTION_CANCEL, -96f, 100)
            }
        }
    }


    @Test
    fun handleVisibilityFollowsConfigurationAndRemovesTheHitTargetWhenHidden() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = DrawerContainer(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, -1))
                val width = activity.resources.displayMetrics.widthPixels
                val height = activity.resources.displayMetrics.heightPixels
                drawer.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                drawer.layout(0, 0, width, height)

                val sheet = drawer.getChildAt(drawer.childCount - 1) as LinearLayout
                val handleTarget = sheet.getChildAt(0)
                assertEquals(View.VISIBLE, handleTarget.visibility)
                drawer.refreshAnchors()
                val visiblePeekHeight = requireNotNull(drawer.controller.currentState())
                    .anchors.first { it.id == "peek" }.heightPx

                drawer.controller.setDragHandleEnabled(false)
                assertEquals(View.GONE, handleTarget.visibility)
                assertFalse(handleTarget.isShown)
                val hiddenPeekHeight = requireNotNull(drawer.controller.currentState())
                    .anchors.first { it.id == "peek" }.heightPx
                assertTrue(
                    "Hiding the handle must remove its height from the collapsed anchor",
                    hiddenPeekHeight < visiblePeekHeight
                )
                drawer.controller.moveToAnchor("peek", false)
                assertEquals(hiddenPeekHeight, drawer.controller.currentState()?.currentHeightPx)

                drawer.controller.setDragHandleEnabled(true)
                assertEquals(View.VISIBLE, handleTarget.visibility)
                assertTrue(handleTarget.isShown)
                val restoredPeekHeight = requireNotNull(drawer.controller.currentState())
                    .anchors.first { it.id == "peek" }.heightPx
                assertTrue(
                    "Showing the handle must restore its measured collapsed chrome height",
                    restoredPeekHeight > hiddenPeekHeight
                )
                drawer.controller.moveToAnchor("peek", false)
                assertEquals(restoredPeekHeight, drawer.controller.currentState()?.currentHeightPx)
            }
        }
    }

    @Test
    fun expandedNestedContentScrollsToItsStartBeforeTheDrawerCollapses() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = DrawerContainer(activity)
                val scroll = NestedScrollView(activity).apply {
                    isNestedScrollingEnabled = true
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        repeat(36) { index ->
                            addView(TextView(activity).apply {
                                text = "Scrollable panel row $index"
                                minHeight = (48f * resources.displayMetrics.density).toInt()
                            }, LinearLayout.LayoutParams(-1, -2))
                        }
                    }, FrameLayout.LayoutParams(-1, -2))
                }
                drawer.controller.setPages(emptyList())
                drawer.controller.setNestedScrollEnabled(true)
                drawer.controller.setExpandedContent(object : DrawerPanelContent {
                    override fun createView(): View = scroll
                })
                drawer.controller.setAnchorStrategy(DrawerAnchorStrategies.threeStage(0.5f, false))
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, -1))

                val width = activity.resources.displayMetrics.widthPixels
                val height = activity.resources.displayMetrics.heightPixels
                fun layoutDrawer() {
                    drawer.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    drawer.layout(0, 0, width, height)
                }
                layoutDrawer()
                drawer.refreshAnchors()
                drawer.controller.moveToAnchor("expanded", false)
                layoutDrawer()

                assertTrue("Expanded panel must have a measured scroll viewport", scroll.height > 0)
                scroll.scrollTo(0, Int.MAX_VALUE)
                assertTrue("The nested panel must be scrolled away from its start", scroll.canScrollVertically(-1))

                val expandedHeight = requireNotNull(drawer.controller.currentState()).currentHeightPx
                assertTrue(
                    "The sheet must accept nested scrolling from its expanded panel",
                    ViewCompat.startNestedScroll(
                        scroll,
                        ViewCompat.SCROLL_AXIS_VERTICAL,
                        ViewCompat.TYPE_TOUCH
                    )
                )

                val consumed = IntArray(2)
                ViewCompat.dispatchNestedPreScroll(
                    scroll, 0, -(48f * activity.resources.displayMetrics.density).toInt(),
                    consumed, null, ViewCompat.TYPE_TOUCH
                )
                assertEquals("Scrollable content owns downward movement before reaching its start", 0, consumed[1])
                assertEquals(expandedHeight, drawer.controller.currentState()?.currentHeightPx)

                scroll.scrollTo(0, 0)
                assertFalse(scroll.canScrollVertically(-1))
                consumed.fill(0)
                ViewCompat.dispatchNestedPreScroll(
                    scroll, 0, -(48f * activity.resources.displayMetrics.density).toInt(),
                    consumed, null, ViewCompat.TYPE_TOUCH
                )
                assertTrue("The drawer may consume downward movement once content is at its start", consumed[1] < 0)
                assertTrue(
                    drawer.controller.currentState()!!.currentHeightPx < expandedHeight
                )
                ViewCompat.stopNestedScroll(scroll, ViewCompat.TYPE_TOUCH)
            }
        }
    }

    @Test
    fun nestedScrollingFromMainContentIsNotCapturedByTheDrawerSheet() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = DrawerContainer(activity)
                val mainScroll = NestedScrollView(activity).apply {
                    addView(TextView(activity).apply {
                        text = (1..30).joinToString("\n") { "Main content row $it" }
                    }, FrameLayout.LayoutParams(-1, -2))
                }
                drawer.controller.setNestedScrollEnabled(true)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, -1))
                drawer.setMainContent(mainScroll)

                val width = activity.resources.displayMetrics.widthPixels
                val height = activity.resources.displayMetrics.heightPixels
                drawer.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                drawer.layout(0, 0, width, height)
                val initialHeight = drawer.controller.currentState()?.currentHeightPx
                assertFalse(
                    "Main content is not a child of the sheet and must not start sheet nested scrolling",
                    ViewCompat.startNestedScroll(
                        mainScroll,
                        ViewCompat.SCROLL_AXIS_VERTICAL,
                        ViewCompat.TYPE_TOUCH
                    )
                )
                val consumed = IntArray(2)
                ViewCompat.dispatchNestedPreScroll(
                    mainScroll, 0, 120, consumed, null, ViewCompat.TYPE_TOUCH
                )
                assertEquals(0, consumed[1])
                assertEquals(initialHeight, drawer.controller.currentState()?.currentHeightPx)
            }
        }
    }

    @Test
    fun settingsEntryIsPinnedAtTheEndOfTheGroupIndicatorAndRemainsClickable() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = SymbolDrawerView(activity)
                var settingsOpened = 0
                drawer.onOpenSettingsListener = { settingsOpened++ }
                activity.content.addView(
                    drawer,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (220f * activity.resources.displayMetrics.density).toInt()
                    )
                )
                fun layoutAt(widthDp: Int) {
                    val width = (widthDp * activity.resources.displayMetrics.density).toInt()
                    val height = (220f * activity.resources.displayMetrics.density).toInt()
                    drawer.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    drawer.layout(0, 0, width, height)
                }
                fun findSettingsButton(view: View): ImageButton? {
                    if (view is ImageButton && view.contentDescription == "符号设置") return view
                    if (view is ViewGroup) {
                        for (index in 0 until view.childCount) {
                            findSettingsButton(view.getChildAt(index))?.let { return it }
                        }
                    }
                    return null
                }
                for (widthDp in listOf(400, 900)) {
                    layoutAt(widthDp)
                    val button = requireNotNull(findSettingsButton(drawer))
                    assertTrue(button.isClickable)
                    assertTrue(button.minimumWidth >= (48f * activity.resources.displayMetrics.density).toInt())
                    assertTrue(button.minimumHeight >= (48f * activity.resources.displayMetrics.density).toInt())
                    assertEquals(button.parent.let { (it as View).width }, button.right)
                    button.performClick()
                }
                assertEquals(2, settingsOpened)
            }
        }
    }
}
