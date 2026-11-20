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

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerThemeExtension
import com.zerostudio.idea.widget.editor.symbolinput.v2.testing.DrawerTestHostActivity
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SymbolDrawerThemeObserverTest {

    private fun theme(seed: Int) = SymbolDrawerTheme(
        primary = seed,
        onPrimary = seed + 1,
        surface = seed + 2,
        onSurface = seed + 3,
        secondaryText = seed + 4,
        outline = seed + 5,
        drawerBackground = seed + 6,
        selectedCellBackground = seed + 7
    )

    private fun provider(extensionId: String, value: SymbolDrawerTheme) =
        object : SymbolDrawerThemeExtension {
            override val id = extensionId
            override val title = extensionId
            override fun resolve(
                context: android.content.Context,
                settings: SymbolDrawerSettings
            ): SymbolDrawerTheme = value
        }

    @Test
    fun observerPublishesOnlyChangedSnapshotsAndCloseIsIdempotent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            val observed = mutableListOf<SymbolDrawerTheme?>()
            lateinit var drawer: SymbolDrawerView
            lateinit var observer: AutoCloseable
            val expected = theme(100)

            scenario.onActivity { activity ->
                drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer)
                observer = drawer.observeTheme { observed += it }

                drawer.extensions.registerTheme(provider("observer.theme", expected))
                assertTrue(drawer.setThemeExtension("observer.theme"))
                assertEquals(expected, drawer.themeSnapshot)
                drawer.refreshHostTheme()
            }

            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(listOf(null, expected), observed)
                observer.close()
                observer.close()
                drawer.refreshHostTheme()
                assertTrue(drawer.setThemeExtension(null))
                assertNull(drawer.themeSnapshot)
                assertEquals(listOf(null, expected), observed)
            }
        }
    }

    @Test
    fun replacingAndUnregisteringSelectedThemeUpdatesTheLiveSnapshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(DrawerTestHostActivity::class.java).use { scenario ->
            lateinit var drawer: SymbolDrawerView
            lateinit var newHandle: AutoCloseable
            val firstTheme = theme(200)
            val replacementTheme = theme(300)

            scenario.onActivity { activity ->
                drawer = SymbolDrawerView(activity)
                activity.content.addView(drawer)
                val oldHandle = drawer.extensions.registerTheme(
                    provider("replace.theme", firstTheme)
                )
                assertTrue(drawer.setThemeExtension("replace.theme"))

                newHandle = drawer.extensions.registerTheme(
                    provider("replace.theme", replacementTheme)
                )
                // A stale registration handle must not remove a same-ID replacement.
                oldHandle.close()
            }

            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(replacementTheme, drawer.themeSnapshot)
                newHandle.close()
            }

            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertNull(drawer.themeSnapshot)
            }
        }
    }
}
