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

import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import java.lang.reflect.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.zerostudio.idea.widget.editor.symbolinput.v2.state.DrawerGeometry
import com.google.android.material.tabs.TabLayout
import com.google.android.material.button.MaterialButton
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.manager.SymbolDrawerManagerScreen
import com.zerostudio.idea.widget.editor.symbolinput.v2.manager.CappedScrollView
import com.zerostudio.idea.widget.editor.symbolinput.v2.manager.resolveEditorFormMaxHeightPx
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SettingsLoadResult
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettingsRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettingsScreen
import com.zerostudio.idea.widget.editor.symbolinput.v2.testing.DrawerTestHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerUiContractTest {
    private class Groups(initial: List<SymbolGroup>) : SymbolGroupRepository {
        var value = initial
        override fun loadGroups() = value
        override fun saveGroups(groups: List<SymbolGroup>) { value = groups.toList() }
    }

    private class Settings : SymbolDrawerSettingsRepository {
        var value = SymbolDrawerSettings()
        override fun load() = SettingsLoadResult(value)
        override fun save(settings: SymbolDrawerSettings) { value = settings }
        override fun reset() { value = SymbolDrawerSettings() }
        override fun hasStoredSettings() = true
    }

    private fun sample() = listOf(
        SymbolGroup("one", "One", listOf(SymbolEntry(1L, "a", SymbolAction.InsertText("a")))),
        SymbolGroup("two", "Two", listOf(SymbolEntry(2L, "b", SymbolAction.InsertText("b"))))
    )


    @Test fun drawerSavedStateCreatorIsPublicForParcelReflection() {
        val savedStateClass = SymbolDrawerView.DrawerSavedState::class.java
        assertTrue(Modifier.isPublic(savedStateClass.modifiers))
        assertTrue(Modifier.isPublic(savedStateClass.getField("CREATOR").modifiers))
    }

    @Test fun advancedDrawerInflatesAndAcceptsNegativeSpacing() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, 480))
                val value = SymbolDrawerSettings(
                    cellHorizontalSpacingDp = -10,
                    cellVerticalSpacingDp = -10,
                    cellHeightDp = 10,
                    maxExpandedHeightDp = 110
                )
                drawer.applySettings(value)
                drawer.submitGroups(sample())
                assertEquals(-10, drawer.getSettings().cellHorizontalSpacingDp)
                assertEquals(-10, drawer.getSettings().cellVerticalSpacingDp)
            }
        }
    }


    @Test fun settingsColumnCountUpdatesTheVisibleSymbolGrid() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            lateinit var drawer: SymbolDrawerView
            var sevenColumns = -1
            var fourColumns = -1
            val checked = CountDownLatch(1)
            scenario.onActivity { activity ->
                drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, 480))
                drawer.setExpanded(true, false)
                drawer.submitGroups(sample()) {
                    drawer.applySettings(SymbolDrawerSettings(columns = 7))
                    drawer.measure(
                        View.MeasureSpec.makeMeasureSpec(
                            (480f * activity.resources.displayMetrics.density).toInt(),
                            View.MeasureSpec.EXACTLY
                        ),
                        View.MeasureSpec.makeMeasureSpec(
                            (480f * activity.resources.displayMetrics.density).toInt(),
                            View.MeasureSpec.EXACTLY
                        )
                    )
                    drawer.layout(0, 0, drawer.measuredWidth, drawer.measuredHeight)
                    drawer.post {
                        val firstGrid = find(drawer) {
                            it is RecyclerView && it.layoutManager is GridLayoutManager
                        } as RecyclerView
                        sevenColumns = (firstGrid.layoutManager as GridLayoutManager).spanCount

                        drawer.applySettings(SymbolDrawerSettings(columns = 4))
                        drawer.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                (480f * activity.resources.displayMetrics.density).toInt(),
                                View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                (480f * activity.resources.displayMetrics.density).toInt(),
                                View.MeasureSpec.EXACTLY
                            )
                        )
                        drawer.layout(0, 0, drawer.measuredWidth, drawer.measuredHeight)
                        drawer.post {
                            val updatedGrid = find(drawer) {
                                it is RecyclerView && it.layoutManager is GridLayoutManager
                            } as RecyclerView
                            fourColumns = (updatedGrid.layoutManager as GridLayoutManager).spanCount
                            checked.countDown()
                        }
                    }
                }
            }
            assertTrue("The symbol grid did not finish applying column settings", checked.await(5, TimeUnit.SECONDS))
            assertEquals(7, sevenColumns)
            assertEquals(4, fourColumns)
        }
    }

    @Test fun settingsEntryStaysPinnedBesideScrollableGroupTabs() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = SymbolDrawerView(activity)
                var opened = 0
                drawer.onOpenSettingsListener = { opened++ }
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, 480))
                drawer.submitGroups(sample())
                drawer.setExpanded(true, false)

                val width = (420f * activity.resources.displayMetrics.density).toInt()
                val height = (480f * activity.resources.displayMetrics.density).toInt()
                drawer.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                drawer.layout(0, 0, width, height)

                val settings = find(drawer) { it.contentDescription == "符号设置" }
                assertEquals(width, (settings.parent as View).right)
                assertTrue(settings.performClick())
                assertEquals(1, opened)
            }
        }
    }


    @Test fun laterUsageMutationsInvalidateOlderUndoSnapshots() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager = SymbolDrawerManagerScreen(activity, Groups(sample()))
                activity.content.addView(manager, FrameLayout.LayoutParams(-1, -1))

                val showUndo = manager.javaClass.getDeclaredMethod(
                    "showUndo",
                    String::class.java,
                    Function0::class.java
                ).apply { isAccessible = true }
                val pendingUndo = manager.javaClass.getDeclaredField("pendingUndo").apply {
                    isAccessible = true
                }
                val noOpUndo: () -> Unit = {}

                showUndo.invoke(manager, "测试撤销", noOpUndo)
                assertNotNull(pendingUndo.get(manager))
                manager.recordRecentlyUsed(2L)
                assertNull("A later toolbar dispatch must invalidate the older undo snapshot", pendingUndo.get(manager))

                showUndo.invoke(manager, "测试撤销", noOpUndo)
                assertNotNull(pendingUndo.get(manager))
                val toggleFavorite = manager.javaClass.getDeclaredMethod(
                    "toggleFavorite",
                    SymbolEntry::class.java
                ).apply { isAccessible = true }
                toggleFavorite.invoke(manager, sample().first().symbols.first())
                assertNull("A later favorite edit must invalidate the older undo snapshot", pendingUndo.get(manager))
            }
        }
    }

    @Test fun managerSearchEntryUsesMaterialButtonAndTogglesSearchField() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val manager = SymbolDrawerManagerScreen(activity, Groups(sample()))
                activity.content.addView(manager, FrameLayout.LayoutParams(-1, -1))

                val searchButton = find(manager) {
                    it.tag == "symbol_manager_search_button"
                } as? MaterialButton ?: error("Material search button was not created")
                val searchContainer = find(manager) {
                    it.tag == "symbol_manager_search_container"
                }
                assertEquals(View.GONE, searchContainer.visibility)
                assertTrue(searchButton.performClick())
                assertEquals(View.VISIBLE, searchContainer.visibility)
                assertEquals("收起", searchButton.text.toString())
                assertTrue(searchButton.performClick())
                assertEquals(View.GONE, searchContainer.visibility)
                assertEquals("搜索", searchButton.text.toString())
            }
        }
    }

    @Test fun pinnedSettingsEntryOpensBuiltInSettingsWhenNoHostCallbackIsInstalled() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, 480))
                drawer.submitGroups(sample())
                val width = (420f * activity.resources.displayMetrics.density).toInt()
                val height = (480f * activity.resources.displayMetrics.density).toInt()
                drawer.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                drawer.layout(0, 0, width, height)
                drawer.setExpanded(true, false)
                val settings = find(drawer) { it.contentDescription == "符号设置" }
                assertTrue(settings.performClick())
                assertTrue(drawer.isSettingsDialogShowing)
            }
        }
    }

    @Test fun drawerCanDetachAndReattachWithoutLosingStableUiState() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            lateinit var drawer: SymbolDrawerView
            val committed = CountDownLatch(1)
            scenario.onActivity { activity ->
                drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, 480))
                drawer.submitGroups(sample()) { committed.countDown() }
            }
            assertTrue("Group submission did not commit", committed.await(5, TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                drawer.selectGroup(1, false)
                drawer.setExpanded(true, false)

                activity.content.removeView(drawer)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, 480))

                assertEquals(1, drawer.getCurrentGroupIndex())
                assertTrue(drawer.isExpanded())
            }
        }
    }

    @Test fun symbolDrawerHierarchyStateRestoresSelectedGroupAndExpansion() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            lateinit var original: SymbolDrawerView
            val firstCommitted = CountDownLatch(1)
            scenario.onActivity { activity ->
                original = SymbolDrawerView(activity).apply { id = View.generateViewId() }
                activity.content.addView(original, FrameLayout.LayoutParams(-1, 480))
                original.submitGroups(sample()) { firstCommitted.countDown() }
            }
            assertTrue("Initial symbol groups did not commit", firstCommitted.await(5, TimeUnit.SECONDS))

            val savedState = SparseArray<Parcelable>()
            val restored = CountDownLatch(1)
            var restoredGroupIndex = -1
            var restoredExpanded = false
            scenario.onActivity { activity ->
                original.selectGroup(1, smoothScroll = false)
                original.setExpanded(true, animate = false)
                original.saveHierarchyState(savedState)
                activity.content.removeView(original)

                val replacement = SymbolDrawerView(activity).apply { id = original.id }
                replacement.submitGroups(sample()) {
                    replacement.restoreHierarchyState(savedState)
                    activity.content.addView(replacement, FrameLayout.LayoutParams(-1, 480))
                    val width = activity.resources.displayMetrics.widthPixels
                    val height = (480f * activity.resources.displayMetrics.density).toInt()
                    replacement.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    replacement.layout(0, 0, width, height)
                    restoredGroupIndex = replacement.getCurrentGroupIndex()
                    restoredExpanded = replacement.isExpanded()
                    restored.countDown()
                }
            }
            assertTrue("Restored symbol drawer state did not commit", restored.await(5, TimeUnit.SECONDS))
            assertEquals(1, restoredGroupIndex)
            assertTrue(restoredExpanded)
        }
    }

    @Test fun collapsedToolbarUsesTheConfiguredVisibleRowCount() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            val checked = CountDownLatch(1)
            var collapsedGridHeight = -1
            var expectedGridHeight = -1
            var collapsedCanScroll = true
            var expandedCanScroll = false
            scenario.onActivity { activity ->
                val drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, -1))
                val density = activity.resources.displayMetrics.density
                val settings = SymbolDrawerSettings(
                    columns = 10,
                    collapsedRows = 3,
                    cellHeightDp = 28,
                    minimumTouchTargetDp = 40
                )
                drawer.applySettings(settings)
                val symbols = (1L..200L).map { id ->
                    SymbolEntry(id, "s$id", SymbolAction.InsertText("s$id"))
                }
                drawer.submitGroups(listOf(SymbolGroup("many", "Many", symbols))) {
                    val width = (420f * density).toInt()
                    val height = (640f * density).toInt()
                    fun layoutDrawer() {
                        drawer.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                        )
                        drawer.layout(0, 0, width, drawer.measuredHeight)
                    }
                    drawer.setExpanded(false, false)
                    layoutDrawer()
                    val grid = find(drawer) {
                        it is RecyclerView && it.layoutManager is GridLayoutManager
                    } as RecyclerView
                    collapsedCanScroll = grid.canScrollVertically(1)
                    collapsedGridHeight = grid.measuredHeight
                    expectedGridHeight = DrawerGeometry.collapsedHeightPx(settings.toDrawerConfig(), density)
                    drawer.setExpanded(true, false)
                    layoutDrawer()
                    grid.post {
                        expandedCanScroll = grid.canScrollVertically(1)
                        checked.countDown()
                    }
                }
            }
            assertTrue("The expanded grid did not finish layout", checked.await(5, TimeUnit.SECONDS))
            assertEquals("Collapsed height must use the layout setting, not a hard-coded row count",
                expectedGridHeight, collapsedGridHeight)
            assertFalse("Collapsed toolbar must keep exactly its configured rows fixed", collapsedCanScroll)
            assertTrue("The expanded grid must allow scrolling when content exceeds its viewport", expandedCanScroll)
        }
    }

    @Test fun verticalTouchDragCanExpandTheBottomDrawer() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            var expandedAfterDrag = false
            scenario.onActivity { activity ->
                val drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, -1))
                val symbols = (1L..80L).map { id ->
                    SymbolEntry(id, "s$id", SymbolAction.InsertText("s$id"))
                }
                drawer.submitGroups(listOf(SymbolGroup("many", "Many", symbols))) {
                    val width = (420f * activity.resources.displayMetrics.density).toInt()
                    val height = (640f * activity.resources.displayMetrics.density).toInt()
                    drawer.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    drawer.layout(0, 0, width, drawer.measuredHeight)
                    drawer.setExpanded(false, false)
                    val downTime = android.os.SystemClock.uptimeMillis()
                    fun send(action: Int, y: Float, offset: Long): Boolean {
                        val event = MotionEvent.obtain(
                            downTime,
                            downTime + offset,
                            action,
                            width / 2f,
                            y,
                            0
                        )
                        return try {
                            drawer.dispatchTouchEvent(event)
                        } finally {
                            event.recycle()
                        }
                    }
                    val density = activity.resources.displayMetrics.density
                    send(MotionEvent.ACTION_DOWN, 30f * density, 0)
                    send(MotionEvent.ACTION_MOVE, -320f * density, 120)
                    expandedAfterDrag = drawer.isExpanded()
                    send(MotionEvent.ACTION_UP, -320f * density, 160)
                }
            }
            assertTrue(
                "A vertical drag must be intercepted by the drawer and expand it",
                expandedAfterDrag
            )
        }
    }

    @Test fun horizontalTouchSwipeTurnsOneGroupPageLikeABook() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            val changed = CountDownLatch(1)
            var pageCount = 0
            var selectedPage = -1
            scenario.onActivity { activity ->
                val drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer, FrameLayout.LayoutParams(-1, -1))
                drawer.onGroupChangedListener = { index, _ ->
                    selectedPage = index
                    if (index == 1) changed.countDown()
                }
                drawer.submitGroups(sample()) {
                    val width = (420f * activity.resources.displayMetrics.density).toInt()
                    val height = (480f * activity.resources.displayMetrics.density).toInt()
                    drawer.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    drawer.layout(0, 0, width, drawer.measuredHeight)
                    drawer.setExpanded(true, false)
                    drawer.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    drawer.layout(0, 0, width, drawer.measuredHeight)
                    val pager = find(drawer) { it is ViewPager2 } as ViewPager2
                    pageCount = pager.adapter?.itemCount ?: 0
                    check(pager.isUserInputEnabled) { "Horizontal group paging must remain enabled" }
                    val y = (pager.top + pager.height / 2f).coerceAtLeast(1f)
                    val downTime = android.os.SystemClock.uptimeMillis()
                    fun send(action: Int, x: Float, offset: Long) {
                        val event = MotionEvent.obtain(
                            downTime,
                            downTime + offset,
                            action,
                            x,
                            y,
                            0
                        )
                        try {
                            drawer.dispatchTouchEvent(event)
                        } finally {
                            event.recycle()
                        }
                    }
                    send(MotionEvent.ACTION_DOWN, width * 0.88f, 0)
                    send(MotionEvent.ACTION_MOVE, width * 0.62f, 70)
                    send(MotionEvent.ACTION_MOVE, width * 0.34f, 140)
                    send(MotionEvent.ACTION_MOVE, width * 0.12f, 210)
                    send(MotionEvent.ACTION_UP, width * 0.12f, 240)
                }
            }
            assertTrue(
                "A real horizontal touch swipe must advance to the next symbol group page",
                changed.await(5, TimeUnit.SECONDS)
            )
            assertEquals("Each symbol group must occupy exactly one pager page", 2, pageCount)
            assertEquals(1, selectedPage)
        }
    }

    @Test fun managerScreenInflatesDrawerNavigationAndBackupImportRoundTrips() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val repository = Groups(sample())
                val screen = SymbolDrawerManagerScreen(activity, repository)
                activity.content.addView(screen, FrameLayout.LayoutParams(-1, -1))
                val navigation = find(screen) { it is TabLayout } as TabLayout
                assertEquals(2, navigation.tabCount)
                assertEquals(listOf("One", "Two"), (0 until navigation.tabCount).map {
                    navigation.getTabAt(it)?.text?.toString()
                })
                val backup = screen.exportBundle()
                screen.importBundle(backup)
                assertEquals(sample(), repository.value)
            }
        }
    }

    @Test fun dragPreviewUsesAnIndependentNonTouchPopupAndDoesNotResizeManager() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val screen = SymbolDrawerManagerScreen(activity, Groups(sample()))
                activity.content.addView(screen, FrameLayout.LayoutParams(-1, -1))
                val width = (420f * activity.resources.displayMetrics.density).toInt()
                val height = (720f * activity.resources.displayMetrics.density).toInt()
                screen.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                screen.layout(0, 0, width, height)
                val childCountBefore = screen.childCount
                val measuredHeightBefore = screen.measuredHeight

                val showPreview = SymbolDrawerManagerScreen::class.java
                    .getDeclaredMethod("showDragPreview")
                    .apply { isAccessible = true }
                showPreview.invoke(screen)

                val popupField = SymbolDrawerManagerScreen::class.java
                    .getDeclaredField("previewPopup")
                    .apply { isAccessible = true }
                val popup = popupField.get(screen) as? PopupWindow
                assertNotNull("Drag preview must be hosted by its own PopupWindow", popup)
                assertTrue("Preview popup should be visible during a drag", popup!!.isShowing)
                assertFalse("Preview must not steal the active drag touch stream", popup.isTouchable)
                assertEquals("Preview must not add rows to the manager layout", childCountBefore, screen.childCount)
                assertEquals("Preview must not change the measured manager height", measuredHeightBefore, screen.measuredHeight)

                SymbolDrawerManagerScreen::class.java
                    .getDeclaredMethod("destroyDragPreview")
                    .apply { isAccessible = true }
                    .invoke(screen)
            }
        }
    }

    @Test fun inlinePreviewEditModeCanRestoreTheEntrySnapshot() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val repository = Groups(sample())
                val screen = SymbolDrawerManagerScreen(activity, repository)
                activity.content.addView(screen, FrameLayout.LayoutParams(-1, -1))
                val width = (420f * activity.resources.displayMetrics.density).toInt()
                val height = (720f * activity.resources.displayMetrics.density).toInt()
                screen.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                screen.layout(0, 0, width, height)

                val screenClass = SymbolDrawerManagerScreen::class.java
                screenClass.getDeclaredMethod("enterPreviewEditMode")
                    .apply { isAccessible = true }
                    .invoke(screen)

                val panel = screenClass.getDeclaredField("previewPanel")
                    .apply { isAccessible = true }.get(screen) as View
                val content = screenClass.getDeclaredField("previewContent")
                    .apply { isAccessible = true }.get(screen) as ViewGroup
                val preview = screenClass.getDeclaredField("previewDrawer")
                    .apply { isAccessible = true }.get(screen) as View
                val popup = screenClass.getDeclaredField("previewPopup")
                    .apply { isAccessible = true }.get(screen)

                assertEquals("Inline preview must be placed above the group pages", screen, panel.parent)
                assertEquals(View.VISIBLE, panel.visibility)
                assertEquals("Preview drawer must be inside the inline preview host", content, preview.parent)
                assertTrue("Persistent edit mode must not create a touch-stealing popup", popup == null)

                val manager = screenClass.getDeclaredField("manager")
                    .apply { isAccessible = true }.get(screen)
                val changed = sample().mapIndexed { index, group ->
                    if (index == 0) group.copy(title = "Changed") else group
                }
                manager.javaClass.getDeclaredMethod("replaceAll", List::class.java)
                    .apply { isAccessible = true }
                    .invoke(manager, changed)

                screenClass.getDeclaredMethod("restorePreviewEditMode")
                    .apply { isAccessible = true }
                    .invoke(screen)
                assertEquals("Restore must return the repository to the entry snapshot", sample(), repository.value)

                screenClass.getDeclaredMethod("finishPreviewEditMode")
                    .apply { isAccessible = true }
                    .invoke(screen)
                assertEquals(View.GONE, panel.visibility)
                assertTrue(screenClass.getDeclaredField("previewDrawer")
                    .apply { isAccessible = true }.get(screen) == null)
            }
        }
    }

    @Test fun managerSymbolCardsReflowAcrossWindowSizeClasses() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val screen = SymbolDrawerManagerScreen(activity, Groups(sample()))
                activity.content.addView(screen, FrameLayout.LayoutParams(-1, -1))
                val density = activity.resources.displayMetrics.density
                val wideWidth = (1000f * density).toInt()
                val height = (800f * density).toInt()
                screen.measure(
                    View.MeasureSpec.makeMeasureSpec(wideWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                screen.layout(0, 0, wideWidth, height)
                val list = find(screen) { it is RecyclerView } as RecyclerView
                assertEquals(2, (list.layoutManager as GridLayoutManager).spanCount)

                val compactWidth = (500f * density).toInt()
                screen.measure(
                    View.MeasureSpec.makeMeasureSpec(compactWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                screen.layout(0, 0, compactWidth, height)
                assertEquals(1, (list.layoutManager as GridLayoutManager).spanCount)

                // A landscape/split-height change must not alter the column
                // decision: the policy is based on the app window width.
                val landscapeHeight = (360f * density).toInt()
                screen.measure(
                    View.MeasureSpec.makeMeasureSpec(compactWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(landscapeHeight, View.MeasureSpec.EXACTLY)
                )
                screen.layout(0, 0, compactWidth, landscapeHeight)
                assertEquals(1, (list.layoutManager as GridLayoutManager).spanCount)
            }
        }
    }

    @Test fun settingsScreenInflatesAllSectionsAndReloadsCompactValues() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val settings = Settings()
                val screen = SymbolDrawerSettingsScreen(activity, settings, Groups(sample()))
                activity.content.addView(screen, FrameLayout.LayoutParams(-1, -1))
                val tabs = find(screen) { it is TabLayout } as TabLayout
                assertTrue(tabs.tabCount >= 5)
                tabs.getTabAt(2)?.select()
                val density = activity.resources.displayMetrics.density
                val expandedWidth = (1000f * density).toInt()
                val expandedHeight = (800f * density).toInt()
                screen.measure(
                    View.MeasureSpec.makeMeasureSpec(expandedWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(expandedHeight, View.MeasureSpec.EXACTLY)
                )
                screen.layout(0, 0, expandedWidth, expandedHeight)
                val reflowedTabs = find(screen) { it is TabLayout } as TabLayout
                assertEquals(2, reflowedTabs.selectedTabPosition)

                val compactWidth = (500f * density).toInt()
                screen.measure(
                    View.MeasureSpec.makeMeasureSpec(compactWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(expandedHeight, View.MeasureSpec.EXACTLY)
                )
                screen.layout(0, 0, compactWidth, expandedHeight)
                val compactTabs = find(screen) { it is TabLayout } as TabLayout
                assertEquals(2, compactTabs.selectedTabPosition)

                settings.save(SymbolDrawerSettings(cellHorizontalSpacingDp = -10, cellVerticalSpacingDp = -8))
                screen.reload()
                assertEquals(-10, screen.getSettings().cellHorizontalSpacingDp)
                assertEquals(-8, screen.getSettings().cellVerticalSpacingDp)
            }
        }
    }



    @Test fun settingsResizePreservesSelectedTabAndItsScrollPosition() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            lateinit var screen: SymbolDrawerSettingsScreen
            var previousScroll = 0
            scenario.onActivity { activity ->
                screen = SymbolDrawerSettingsScreen(activity, Settings(), Groups(sample()))
                val density = activity.resources.displayMetrics.density
                fun layoutAt(widthDp: Int) {
                    val width = (widthDp * density).toInt()
                    val height = (320f * density).toInt()
                    screen.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                    )
                    screen.layout(0, 0, width, height)
                }
                activity.content.addView(screen, FrameLayout.LayoutParams(-1, -1))
                layoutAt(1000)
                // onSizeChanged rebuilds the page tree during layout; run a
                // second measure/layout pass so the replacement ScrollViews
                // have their final viewport before asserting their scroll range.
                layoutAt(1000)
                val tabs = find(screen) { it is TabLayout } as TabLayout
                // The layout page contains enough controls to guarantee a real
                // scroll range at this compact height. The data-management page
                // is intentionally short and should not be forced to scroll.
                tabs.getTabAt(1)?.select()
                // Selecting a previously GONE page schedules a fresh measure/layout.
                // Run that pass before asking ScrollView to clamp its scroll offset;
                // otherwise its old zero-height measurement makes scrollTo a no-op.
                layoutAt(1000)
                val content = tabs.parent as ViewGroup
                val pageHost = content.getChildAt(2) as FrameLayout
                val layoutScroll = pageHost.getChildAt(1) as ScrollView
                val page = layoutScroll.getChildAt(0)
                val scrollRange = (page.measuredHeight - layoutScroll.height).coerceAtLeast(0)
                assertTrue(
                    "The selected layout page must have a real scroll range: page=${page.measuredHeight}, viewport=${layoutScroll.height}",
                    scrollRange > 0
                )
                layoutScroll.scrollTo(0, minOf((180f * density).toInt(), scrollRange))
                previousScroll = layoutScroll.scrollY
                assertTrue("The selected layout page must accept a non-zero scroll offset", previousScroll > 0)

                layoutAt(500)
                layoutAt(500)
            }
            scenario.onActivity {
                val tabs = find(screen) { it is TabLayout } as TabLayout
                assertEquals(1, tabs.selectedTabPosition)
                val content = tabs.parent as ViewGroup
                val pageHost = content.getChildAt(2) as FrameLayout
                val restoredScroll = pageHost.getChildAt(1) as ScrollView
                val restoredPage = restoredScroll.getChildAt(0)
                val restoredRange = (restoredPage.measuredHeight - restoredScroll.height).coerceAtLeast(0)
                // Width-class reflow may reduce the page's total height. Keep the
                // absolute offset when possible, otherwise clamp to the new range.
                assertEquals(minOf(previousScroll, restoredRange), restoredScroll.scrollY)
                assertTrue("Reflow must not reset a still-scrollable page to its top", restoredScroll.scrollY > 0)
            }
        }
    }

    @Test fun settingsTypographyReflowsWithWindowSizeClass() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val screen = SymbolDrawerSettingsScreen(activity, Settings(), Groups(sample()))
                val density = activity.resources.displayMetrics.density
                screen.layout(0, 0, (500f * density).toInt(), (800f * density).toInt())
                val compactTitle = find(screen) {
                    it is TextView && it.text == "符号抽屉设置"
                } as TextView
                val compactTextSize = compactTitle.textSize

                screen.layout(0, 0, (1000f * density).toInt(), (800f * density).toInt())
                val expandedTitle = find(screen) {
                    it is TextView && it.text == "符号抽屉设置"
                } as TextView
                assertTrue(expandedTitle.textSize > compactTextSize)
            }
        }
    }

    @Test fun symbolEditorFormScrollViewIsCappedToAvailableWindowHeight() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val cap = (420f * activity.resources.displayMetrics.density).toInt()
                val scroll = CappedScrollView(activity, cap)
                val content = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    repeat(30) {
                        addView(View(activity), LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (48f * activity.resources.displayMetrics.density).toInt()
                        ))
                    }
                }
                scroll.addView(content)
                scroll.measure(
                    View.MeasureSpec.makeMeasureSpec(
                        (600f * activity.resources.displayMetrics.density).toInt(),
                        View.MeasureSpec.AT_MOST
                    ),
                    View.MeasureSpec.makeMeasureSpec(
                        (900f * activity.resources.displayMetrics.density).toInt(),
                        View.MeasureSpec.AT_MOST
                    )
                )
                assertTrue("Long edit forms must not exceed the available height cap", scroll.measuredHeight <= cap)
                assertTrue("Long edit forms must remain scrollable", scroll.getChildAt(0).measuredHeight > scroll.measuredHeight)
            }
        }
    }


    @Test fun editorFormHeightNeverUsesAnOversizedMinimumInTinyWindows() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val windowHeight = (60f * density).toInt()
                val reservedChrome = (96f * density).toInt()
                val cap = resolveEditorFormMaxHeightPx(windowHeight, reservedChrome)
                assertEquals(1, cap)
                assertTrue("The editor form cap must never exceed the available window height", cap <= windowHeight)
            }
        }
    }

    @Test fun cappedEditorFormAlsoHonorsATighterParentMeasureConstraint() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val cap = (500f * density).toInt()
                val parentLimit = (160f * density).toInt()
                val scroll = CappedScrollView(activity, cap)
                val content = TextView(activity).apply {
                    text = (1..80).joinToString("\n") { "Scrollable editor field $it" }
                }
                scroll.addView(content, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (900f * density).toInt()
                ))
                scroll.measure(
                    View.MeasureSpec.makeMeasureSpec(
                        (400f * density).toInt(),
                        View.MeasureSpec.AT_MOST
                    ),
                    View.MeasureSpec.makeMeasureSpec(parentLimit, View.MeasureSpec.AT_MOST)
                )
                scroll.layout(0, 0, scroll.measuredWidth, scroll.measuredHeight)
                assertTrue(
                    "The capped editor form must respect its tighter parent height",
                    scroll.measuredHeight <= parentLimit
                )
                assertTrue(
                    "The content must remain scrollable inside the capped form",
                    scroll.canScrollVertically(1)
                )
            }
        }
    }

    private fun find(root: View, predicate: (View) -> Boolean): View {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val result = runCatching { find(root.getChildAt(i), predicate) }.getOrNull()
                if (result != null) return result
            }
        }
        error("Required view not found")
    }
}
