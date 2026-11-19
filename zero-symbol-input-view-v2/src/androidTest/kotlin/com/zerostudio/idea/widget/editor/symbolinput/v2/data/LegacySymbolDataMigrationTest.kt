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
package com.zerostudio.idea.widget.editor.symbolinput.v2.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacySymbolDataMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val legacyPreferences by lazy {
        context.getSharedPreferences("advanced_symbol_prefs", Context.MODE_PRIVATE)
    }

    private class MemoryRepository(initial: List<SymbolGroup> = emptyList()) : SymbolGroupRepository {
        var groups = initial
        var migrationComplete = false

        override fun loadGroups() = groups
        override fun saveGroups(groups: List<SymbolGroup>) {
            this.groups = groups.toList()
        }
        override fun isLegacyMigrationComplete() = migrationComplete
        override fun markLegacyMigrationComplete() {
            migrationComplete = true
        }
    }

    @Before
    fun clearLegacyPreferences() {
        legacyPreferences.edit().clear().commit()
    }

    @After
    fun cleanLegacyPreferences() {
        legacyPreferences.edit().clear().commit()
    }

    @Test
    fun importsV1ActionsWithoutMutatingTheLegacyPreference() {
        val json = """[{"n":"Legacy","d":[{"a":0,"b":"x","c":"x","d":36,"e":"replacement"},{"a":987,"b":"custom","c":"short payload","d":988,"e":"long payload"}]}]"""
        legacyPreferences.edit().putString("symbol_json_data", json).commit()
        val repository = MemoryRepository()

        assertTrue(LegacySymbolDataMigration.importIfV2Empty(context, repository))
        assertTrue(repository.migrationComplete)
        val importedGroup = repository.groups.first { it.title == "Legacy" }
        assertEquals(2, importedGroup.symbols.size)
        assertEquals(SymbolAction.InsertText("x"), importedGroup.symbols[0].shortAction)
        assertEquals(
            SymbolAction.EditorCommand("line.replace", "replacement"),
            importedGroup.symbols[0].longAction
        )
        assertEquals(
            SymbolAction.Extension("legacy.action.987", "short payload"),
            importedGroup.symbols[1].shortAction
        )
        assertEquals(
            SymbolAction.Extension("legacy.action.988", "long payload"),
            importedGroup.symbols[1].longAction
        )
        assertEquals(json, legacyPreferences.getString("symbol_json_data", null))
    }

    @Test
    fun malformedLegacyDataIsNotMarkedMigratedOrPartiallyImported() {
        val json = """[{"n":"Legacy","d":[{"a":"not-an-action-id","b":"x"}]}]"""
        legacyPreferences.edit().putString("symbol_json_data", json).commit()
        val repository = MemoryRepository()

        assertFalse(LegacySymbolDataMigration.importIfV2Empty(context, repository))
        assertFalse(repository.migrationComplete)
        assertTrue(repository.groups.isEmpty())
        assertEquals(json, legacyPreferences.getString("symbol_json_data", null))
    }

    @Test
    fun existingV2DataWinsAndLegacyPreferenceRemainsUntouched() {
        val json = """[{"n":"Legacy","d":[{"a":0,"b":"legacy","c":"legacy"}]}]"""
        legacyPreferences.edit().putString("symbol_json_data", json).commit()
        val existing = listOf(
            SymbolGroup("v2", "V2", emptyList())
        )
        val repository = MemoryRepository(existing)

        assertFalse(LegacySymbolDataMigration.importIfV2Empty(context, repository))
        assertTrue(repository.migrationComplete)
        assertEquals(existing, repository.groups)
        assertEquals(json, legacyPreferences.getString("symbol_json_data", null))
    }
}
