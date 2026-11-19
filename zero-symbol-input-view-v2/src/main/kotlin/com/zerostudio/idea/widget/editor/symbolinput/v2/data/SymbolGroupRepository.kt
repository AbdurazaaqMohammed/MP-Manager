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
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup

/** Data boundary used by the drawer and the V2 manager UI. */
interface SymbolGroupRepository {
    fun loadGroups(): List<SymbolGroup>
    fun saveGroups(groups: List<SymbolGroup>)
    fun isLegacyMigrationComplete(): Boolean = loadGroups().isNotEmpty()
    fun markLegacyMigrationComplete() = Unit
}

/**
 * Optional live-change observation for repositories shared by a manager and a
 * visible drawer. Implementations must notify after a successful durable write.
 */
interface ObservableSymbolGroupRepository : SymbolGroupRepository {
    fun observeGroups(listener: () -> Unit): AutoCloseable
}

/**
 * V2-only SharedPreferences store. The explicit JSON codec gives actions a
 * stable discriminator-based schema and still reads the previous V2 draft schema.
 */
class SharedPreferencesSymbolGroupRepository(
    context: Context,
    preferencesName: String = "symbol_drawer_v2"
) : ObservableSymbolGroupRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun observeGroups(listener: () -> Unit): AutoCloseable {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_GROUPS) runCatching(listener)
        }
        preferences.registerOnSharedPreferenceChangeListener(observer)
        return AutoCloseable {
            preferences.unregisterOnSharedPreferenceChangeListener(observer)
        }
    }

    override fun loadGroups(): List<SymbolGroup> {
        val json = preferences.getString(KEY_GROUPS, null) ?: return emptyList()
        return runCatching {
            val groups = SymbolGroupStorageCodec.decode(json)
            // Canonicalize older V1/early-V2 storage on read. This persists the
            // repaired global IDs and current versioned envelope atomically.
            val canonical = SymbolGroupStorageCodec.encode(groups)
            if (canonical != json) {
                preferences.edit().putString(KEY_GROUPS, canonical).commit()
            }
            groups
        }.getOrElse { error ->
                // Preserve the only original copy before defaults or a later save
                // can replace malformed data.
                if (!preferences.contains(KEY_CORRUPT_GROUPS_BACKUP)) {
                    preferences.edit()
                        .putString(KEY_CORRUPT_GROUPS_BACKUP, json)
                        .putString(KEY_CORRUPT_GROUPS_REASON, error.message?.take(512) ?: "Invalid stored data")
                        .commit()
                }
                emptyList()
            }
    }

    override fun isLegacyMigrationComplete(): Boolean =
        preferences.getBoolean(KEY_LEGACY_MIGRATION_COMPLETE, false)

    override fun markLegacyMigrationComplete() {
        preferences.edit().putBoolean(KEY_LEGACY_MIGRATION_COMPLETE, true).apply()
    }

    override fun saveGroups(groups: List<SymbolGroup>) {
        val encoded = SymbolGroupStorageCodec.encode(groups)
        check(preferences.edit().putString(KEY_GROUPS, encoded).commit()) {
            "Unable to persist symbol groups"
        }
    }

    private companion object {
        const val KEY_GROUPS = "groups_json_v2"
        const val KEY_LEGACY_MIGRATION_COMPLETE = "legacy_migration_complete"
        const val KEY_CORRUPT_GROUPS_BACKUP = "groups_corrupt_backup_v2"
        const val KEY_CORRUPT_GROUPS_REASON = "groups_corrupt_reason_v2"
    }
}
