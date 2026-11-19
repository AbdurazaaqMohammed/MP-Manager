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

/**
 * One-way, non-destructive migration from the legacy compact JSON schema.
 * The old preference is read-only; the imported copy is saved to the V2 store.
 */
object LegacySymbolDataMigration {
    private const val LEGACY_PREFS = "advanced_symbol_prefs"
    private const val LEGACY_DATA_KEY = "symbol_json_data"

    fun importIfV2Empty(context: Context, repository: SymbolGroupRepository): Boolean {
        if (repository.isLegacyMigrationComplete()) return false
        if (repository.loadGroups().isNotEmpty()) {
            repository.markLegacyMigrationComplete()
            return false
        }
        val json = context.applicationContext
            .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_DATA_KEY, null)
            ?.takeIf { it.isNotBlank() } ?: run {
                repository.markLegacyMigrationComplete()
                return false
            }

        // Reuse the strict V1 codec so migration enforces the same schema,
        // identifier, action-ID, payload and size limits as explicit imports.
        // This avoids an unbounded Gson parse of untrusted legacy preferences.
        val imported = runCatching {
            LegacySymbolDataCodec.decode(json).filter { it.symbols.isNotEmpty() }
        }.getOrElse { return false }

        if (imported.isEmpty()) {
            repository.markLegacyMigrationComplete()
            return false
        }
        // Model validation and persistence are part of the same failure
        // boundary as decoding. Keep the original preference untouched and
        // leave migration pending when either operation rejects the snapshot.
        return runCatching {
            SymbolGroupBundleCodec.validateGroups(imported)
            repository.saveGroups(imported)
            repository.markLegacyMigrationComplete()
            true
        }.getOrDefault(false)
    }

}
