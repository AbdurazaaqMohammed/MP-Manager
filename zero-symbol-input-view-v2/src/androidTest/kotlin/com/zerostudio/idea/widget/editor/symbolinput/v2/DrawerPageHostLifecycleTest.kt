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
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerPage
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerPageHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import com.zerostudio.idea.widget.editor.symbolinput.v2.testing.DrawerTestHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerPageHostLifecycleTest {

    private class TrackedContent(
        private val activity: DrawerTestHostActivity,
        private val label: String,
        private val events: MutableList<String>
    ) : DrawerPanelContent {
        override fun createView(): View = TextView(activity).apply { text = label }
        override fun onPanelAttached() { events += "$label:attached" }
        override fun onPanelVisible() { events += "$label:visible" }
        override fun onPanelHidden() { events += "$label:hidden" }
        override fun onPanelDetached() { events += "$label:detached" }
    }

    @Test
    fun selectionReplacementAndDisposeDispatchBalancedLifecycleCallbacks() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val events = mutableListOf<String>()
                val host = DrawerPageHost(activity)
                activity.content.addView(host)
                val first = TrackedContent(activity, "first", events)
                val oldSecond = TrackedContent(activity, "old-second", events)
                val newSecond = TrackedContent(activity, "new-second", events)

                host.setPages(
                    listOf(
                        DrawerPage("first", "First", first),
                        DrawerPage("second", "Second", oldSecond)
                    )
                )
                host.setPanelVisible(true)
                assertTrue(host.selectPage("second"))

                host.setPages(
                    listOf(
                        DrawerPage("first", "First", first),
                        DrawerPage("second", "Second updated", newSecond)
                    )
                )
                assertEquals("second", host.currentPage()?.id)
                assertTrue(events.contains("old-second:hidden"))
                assertTrue(events.contains("old-second:detached"))
                assertTrue(events.contains("new-second:attached"))
                assertTrue(events.contains("new-second:visible"))

                host.dispose()
                host.dispose()
                assertEquals(1, events.count { it == "new-second:hidden" })
                assertEquals(1, events.count { it == "new-second:detached" })
                try {
                    host.selectPage("first")
                    throw AssertionError("Expected disposed host to reject selection")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("disposed"))
                }
            }
        }
    }

    @Test
    fun directControllerSubmissionInvalidatesReplacedNonSelectedPageView() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val events = mutableListOf<String>()
                val host = DrawerPageHost(activity)
                activity.content.addView(host)
                val oldFirst = TrackedContent(activity, "old-first", events)
                val currentSecond = TrackedContent(activity, "second", events)
                val newFirst = TrackedContent(activity, "new-first", events)
                host.setPages(listOf(
                    DrawerPage("first", "First", oldFirst),
                    DrawerPage("second", "Second", currentSecond)
                ))
                host.setPanelVisible(true)
                assertTrue(host.selectPage("second"))

                // Exercise the public controller path that bypasses host.setPages().
                host.navigationController.submitPages(listOf(
                    DrawerPage("first", "First updated", newFirst),
                    DrawerPage("second", "Second", currentSecond)
                ))
                assertTrue(host.selectPage("first"))
                assertTrue(events.contains("old-first:detached"))
                assertTrue(events.contains("new-first:attached"))
                assertTrue(events.contains("new-first:visible"))
                host.dispose()
            }
        }
    }

    @Test
    fun rejectedDuplicatePageUpdateDoesNotEvictCurrentPage() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val events = mutableListOf<String>()
                val host = DrawerPageHost(activity)
                activity.content.addView(host)
                val current = TrackedContent(activity, "current", events)
                host.setPages(listOf(DrawerPage("current", "Current", current)))
                host.setPanelVisible(true)

                try {
                    host.setPages(
                        listOf(
                            DrawerPage("duplicate", "One", current),
                            DrawerPage("duplicate", "Two", current)
                        )
                    )
                    throw AssertionError("Expected duplicate page IDs to be rejected")
                } catch (expected: IllegalArgumentException) {
                    assertTrue(expected.message.orEmpty().contains("unique"))
                }

                assertEquals("current", host.currentPage()?.id)
                assertEquals(1, events.count { it == "current:attached" })
                assertEquals(1, events.count { it == "current:visible" })
                assertFalse(events.contains("current:hidden"))
                host.dispose()
            }
        }
    }

    @Test
    fun disposedHostRejectsFurtherPageSelection() {
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = DrawerPageHost(activity)
                activity.content.addView(host)
                host.dispose()
                try {
                    host.selectPage("anything")
                    throw AssertionError("Expected disposed host to reject selection")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("disposed"))
                }
            }
        }
    }
}
