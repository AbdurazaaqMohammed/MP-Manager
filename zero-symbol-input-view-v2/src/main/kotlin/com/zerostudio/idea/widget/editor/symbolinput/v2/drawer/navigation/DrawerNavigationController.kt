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

/**
 * UI-agnostic page selection model. Any TabLayout, RecyclerView or custom
 * navigation view can observe and bind to this controller.
 */
class DrawerNavigationController {
    fun interface Listener {
        fun onNavigationChanged(snapshot: DrawerNavigationSnapshot, selectedIndex: Int)
    }

    private var pages: List<DrawerPage> = emptyList()
    private var selectedId: String? = null
    private val listeners = LinkedHashSet<Listener>()

    fun submitPages(value: List<DrawerPage>, preserveSelection: Boolean = true) {
        require(value.map { it.id }.distinct().size == value.size) {
            "Drawer page IDs must be unique"
        }
        val previous = selectedId
        pages = value.toList()
        selectedId = if (preserveSelection && previous != null && pages.any { it.id == previous }) {
            previous
        } else {
            pages.firstOrNull()?.id
        }
        dispatch()
    }

    fun selectPage(id: String): Boolean {
        if (pages.none { it.id == id }) return false
        if (selectedId == id) return true
        selectedId = id
        dispatch()
        return true
    }

    fun selectPage(index: Int): Boolean {
        val page = pages.getOrNull(index) ?: return false
        return selectPage(page.id)
    }

    fun pages(): List<DrawerPage> = pages.toList()
    fun selectedPage(): DrawerPage? = pages.firstOrNull { it.id == selectedId }
    fun selectedIndex(): Int = pages.indexOfFirst { it.id == selectedId }
    fun snapshot() = DrawerNavigationSnapshot(selectedId, pages.map { it.id })

    fun addListener(listener: Listener) {
        listeners += listener
        listener.onNavigationChanged(snapshot(), selectedIndex())
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    private fun dispatch() {
        val state = snapshot()
        val index = selectedIndex()
        listeners.toList().forEach { it.onNavigationChanged(state, index) }
    }
}
