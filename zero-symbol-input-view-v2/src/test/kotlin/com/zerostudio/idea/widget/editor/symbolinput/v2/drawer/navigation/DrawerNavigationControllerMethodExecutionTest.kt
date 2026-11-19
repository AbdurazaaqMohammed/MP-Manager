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

import android.view.View
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Directly exercises every public state/query/listener method on the navigation controller. */
class DrawerNavigationControllerMethodExecutionTest {
    private fun page(id: String) = DrawerPage(
        id = id,
        title = "Page $id",
        content = object : DrawerPanelContent {
            override fun createView(): View = error("Navigation must not create page views")
        }
    )

    @Test
    fun publicQueriesAndSelectionOverloadsReturnConsistentSnapshots() {
        val controller = DrawerNavigationController()
        assertTrue(controller.pages().isEmpty())
        assertNull(controller.selectedPage())
        assertEquals(-1, controller.selectedIndex())
        assertEquals(DrawerNavigationSnapshot(null, emptyList()), controller.snapshot())

        controller.submitPages(listOf(page("one"), page("two"), page("three")))
        assertEquals(listOf("one", "two", "three"), controller.pages().map { it.id })
        assertEquals(DrawerNavigationSnapshot("one", listOf("one", "two", "three")), controller.snapshot())
        assertEquals("one", controller.selectedPage()?.id)
        assertEquals(0, controller.selectedIndex())

        assertTrue(controller.selectPage(2))
        assertEquals("three", controller.selectedPage()?.id)
        assertEquals(2, controller.selectedIndex())
        assertTrue(controller.selectPage("two"))
        assertEquals("two", controller.snapshot().selectedPageId)
        assertEquals(1, controller.selectedIndex())

        assertTrue(controller.selectPage("two"))
        assertFalse(controller.selectPage(-1))
        assertFalse(controller.selectPage(3))
        assertFalse(controller.selectPage("missing"))
        assertEquals("two", controller.selectedPage()?.id)
    }

    @Test
    fun selectionCanBePreservedOrResetWhenPagesAreReplaced() {
        val controller = DrawerNavigationController()
        controller.submitPages(listOf(page("one"), page("two")))
        controller.selectPage("two")

        controller.submitPages(listOf(page("two"), page("three")), preserveSelection = true)
        assertEquals("two", controller.selectedPage()?.id)

        controller.submitPages(listOf(page("three"), page("four")), preserveSelection = false)
        assertEquals("three", controller.selectedPage()?.id)

        controller.submitPages(emptyList())
        assertNull(controller.selectedPage())
        assertEquals(-1, controller.selectedIndex())
        assertEquals(DrawerNavigationSnapshot(null, emptyList()), controller.snapshot())
    }

    @Test
    fun listenerReceivesImmediateAndSubsequentSnapshotsAndCanBeRemoved() {
        val controller = DrawerNavigationController()
        val events = mutableListOf<Pair<String?, Int>>()
        val listener = DrawerNavigationController.Listener { snapshot, index ->
            events += snapshot.selectedPageId to index
        }

        controller.addListener(listener)
        assertEquals(listOf(null to -1), events)

        controller.submitPages(listOf(page("one"), page("two")))
        controller.selectPage(1)
        controller.removeListener(listener)
        controller.selectPage(0)
        assertEquals(listOf(null to -1, "one" to 0, "two" to 1), events)
    }

    @Test
    fun duplicatePageSubmissionIsRejectedWithoutReplacingExistingState() {
        val controller = DrawerNavigationController()
        controller.submitPages(listOf(page("stable"), page("other")))
        controller.selectPage("other")
        val before = controller.snapshot()

        assertTrue(runCatching {
            controller.submitPages(listOf(page("duplicate"), page("duplicate")))
        }.isFailure)

        assertEquals(before, controller.snapshot())
        assertEquals(listOf("stable", "other"), controller.pages().map { it.id })
    }
}
