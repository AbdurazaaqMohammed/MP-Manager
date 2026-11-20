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
package com.zerostudio.idea.widget.drawer.extension

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.drawer.action.SymbolActionId
import com.zerostudio.idea.widget.drawer.action.SymbolActionRegistry
import com.zerostudio.idea.widget.drawer.action.SymbolActionHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolDrawerPublicApiTest {
    @Test
    fun builtInFeatureIdsAreExposedFromPublicPackage() {
        assertEquals("builtin.search", SymbolDrawerBuiltInFeatures.SEARCH)
        assertEquals("builtin.favorites", SymbolDrawerBuiltInFeatures.FAVORITES)
        assertEquals("builtin.recent", SymbolDrawerBuiltInFeatures.RECENT)
        assertEquals("builtin.settings", SymbolDrawerBuiltInFeatures.SETTINGS)
        assertEquals("builtin.manager", SymbolDrawerBuiltInFeatures.MANAGER)
    }

    @Test
    fun publicActionApiTypesAreResolvable() {
        assertNotNull(SymbolAction::class)
        assertNotNull(SymbolActionId::class)
        assertNotNull(SymbolActionRegistry::class)
        assertNotNull(SymbolActionHandler::class)
    }

    @Test
    fun publicRegistryExecutesExtensionAndLegacyHandlers() {
        val registry = SymbolActionRegistry<Any>()
        val context = Any()
        var extensionPayload: String? = null
        var legacyPayload: String? = null

        registry.register(
            SymbolActionId.extension("sample.insert"),
            SymbolActionHandler { actualContext, payload ->
                assertTrue(actualContext === context)
                extensionPayload = payload
                true
            }
        )
        registry.register(
            73,
            SymbolActionHandler { actualContext, payload ->
                assertTrue(actualContext === context)
                legacyPayload = payload
                true
            }
        )

        assertEquals(true, registry.execute(
            SymbolActionId.extension("sample.insert"),
            context,
            "extension-payload"
        ))
        assertEquals(true, registry.execute(73, context, "legacy-payload"))
        assertEquals("extension-payload", extensionPayload)
        assertEquals("legacy-payload", legacyPayload)
    }

    @Test
    fun closingReplacedPublicRegistrationDoesNotRemoveNewHandler() {
        val registry = SymbolActionRegistry<Any>()
        val id = SymbolActionId.extension("sample.replace")
        val context = Any()
        val oldHandle = registry.register(id, SymbolActionHandler { _, _ -> false })
        val newHandle = registry.register(id, SymbolActionHandler { _, _ -> true })

        oldHandle.close()

        assertEquals(true, registry.execute(id, context))
        newHandle.close()
        assertEquals(null, registry.execute(id, context))
    }

    @Test
    fun publicRegistryAndExtensionTypesAreResolvable() {
        assertNotNull(SymbolDrawerExtensionRegistry::class)
        assertNotNull(SymbolDrawerActionExtension::class)
        assertNotNull(SymbolDrawerItemViewExtension::class)
        assertNotNull(SymbolGroupIndicatorExtension::class)
        assertNotNull(SymbolDrawerSettingsTabExtension::class)
        assertNotNull(SymbolDrawerSettingExtension::class)
        assertNotNull(SymbolDrawerThemeExtension::class)
        assertNotNull(SymbolDrawerLifecycleExtension::class)
        assertNotNull(SymbolDrawerLegacyGroupProvider::class)
        assertNotNull(SymbolDrawerSettingsScope::class)
        assertNotNull(SymbolDrawerTheme::class)
        assertNotNull(SymbolDrawerItemViewState::class)
        assertNotNull(LegacySymbolGroup::class)
        assertNotNull(LegacySymbolItem::class)
    }
}
