/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation

import android.view.View
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent
import org.junit.Assert.*
import org.junit.Test

class DrawerNavigationContractTest {
    private fun page(id: String, title: String = id) = DrawerPage(id, title, object : DrawerPanelContent {
        override fun createView(): View = error("Navigation must not instantiate page views")
    })

    @Test fun pageRejectsBlankIdAndEmptyTitle() {
        assertThrows(IllegalArgumentException::class.java) { page(" ") }
        assertThrows(IllegalArgumentException::class.java) { page("valid", "") }
    }

    @Test fun submittedPageListsAndSnapshotsAreDefensiveCopies() {
        val source = mutableListOf(page("one"), page("two"))
        val controller = DrawerNavigationController()
        controller.submitPages(source)
        source.clear()
        assertEquals(listOf("one", "two"), controller.pages().map { it.id })
        val snapshot = controller.snapshot()
        snapshot.pageIds.toMutableList().clear()
        assertEquals(listOf("one", "two"), controller.snapshot().pageIds)
    }

    @Test fun selectionCanBePreservedOrResetWhenPageDisappears() {
        val controller = DrawerNavigationController()
        controller.submitPages(listOf(page("one"), page("two")))
        assertTrue(controller.selectPage("two"))
        controller.submitPages(listOf(page("three"), page("two")), preserveSelection = true)
        assertEquals("two", controller.selectedPage()?.id)
        controller.submitPages(listOf(page("three"), page("four")), preserveSelection = false)
        assertEquals("three", controller.selectedPage()?.id)
        assertEquals(0, controller.selectedIndex())
    }

    @Test fun selectingCurrentPageDoesNotDispatchDuplicateChange() {
        val controller = DrawerNavigationController()
        controller.submitPages(listOf(page("one")))
        var calls = 0
        controller.addListener { _, _ -> calls++ }
        assertTrue(controller.selectPage("one"))
        assertEquals(1, calls)
    }

    @Test fun listenerRemovalIsIdempotentAndStopsFutureNotifications() {
        val controller = DrawerNavigationController()
        var calls = 0
        val listener = DrawerNavigationController.Listener { _, _ -> calls++ }
        controller.addListener(listener)
        controller.removeListener(listener)
        controller.removeListener(listener)
        controller.submitPages(listOf(page("one")))
        assertEquals(1, calls)
    }

    @Test fun listenerCanRemoveItselfDuringDispatch() {
        val controller = DrawerNavigationController()
        var calls = 0
        lateinit var listener: DrawerNavigationController.Listener
        listener = DrawerNavigationController.Listener { _, _ ->
            calls++
            controller.removeListener(listener)
        }
        controller.addListener(listener)
        controller.submitPages(listOf(page("one")))
        controller.submitPages(listOf(page("two")))
        assertEquals(1, calls)
    }

    @Test fun invalidPageReplacementDoesNotDestroyCurrentSelection() {
        val controller = DrawerNavigationController()
        controller.submitPages(listOf(page("one"), page("two")))
        controller.selectPage("two")
        assertThrows(IllegalArgumentException::class.java) {
            controller.submitPages(listOf(page("duplicate"), page("duplicate")))
        }
        assertEquals(listOf("one", "two"), controller.pages().map { it.id })
        assertEquals("two", controller.selectedPage()?.id)
    }

    @Test fun snapshotTracksIdsAndSelectedIndexAcrossEmptyState() {
        val controller = DrawerNavigationController()
        assertEquals(-1, controller.selectedIndex())
        assertEquals(DrawerNavigationSnapshot(null, emptyList()), controller.snapshot())
        controller.submitPages(emptyList())
        assertNull(controller.selectedPage())
        assertEquals(-1, controller.selectedIndex())
    }
}
