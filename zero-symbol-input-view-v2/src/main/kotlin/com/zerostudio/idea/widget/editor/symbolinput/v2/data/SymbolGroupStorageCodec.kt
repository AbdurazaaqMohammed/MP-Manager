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

import com.google.gson.JsonParser
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*

/** Versioned V2 storage writes; legacy V1 and early V2 snapshots remain readable. */
object SymbolGroupStorageCodec {
    fun encode(groups: List<SymbolGroup>): String {
        val symbolIds = groups.asSequence().flatMap { it.symbols.asSequence() }.map { it.id }.toList()
        require(symbolIds.distinct().size == symbolIds.size) {
            "Symbol IDs must be unique across the entire repository"
        }
        // The V2 store must preserve typed actions that have no V1 numeric-ID
        // equivalent (composites, surround-selection and app extensions).
        // decode() remains backward compatible with compact V1 arrays.
        return SymbolGroupBundleCodec.encode(groups).also {
            require(it.length <= SymbolGroupBundleCodec.MAX_BUNDLE_CHARS) {
                "Stored symbol groups exceed the size limit"
            }
        }
    }

    fun decode(json: String): List<SymbolGroup> {
        require(json.length <= SymbolGroupBundleCodec.MAX_BUNDLE_CHARS) { "Stored symbol groups exceed the size limit" }
        val root = JsonParser.parseString(json)
        if (root.isJsonObject) return normalizeRepositorySymbolIds(SymbolGroupBundleCodec.decode(json))
        val array = root.asJsonArray
        if (array.size() == 0) return emptyList()
        if (array[0].asJsonObject.has("n") || array[0].asJsonObject.has("d")) {
            return LegacySymbolDataCodec.decode(json)
        }
        return normalizeRepositorySymbolIds(decodePreviousV2Array(array))
    }

    /**
     * Early V2 builds assigned IDs independently inside each group. Keep the
     * first occurrence stable and deterministically reassign only collisions.
     * Action IDs/payloads and all non-colliding symbol IDs remain untouched.
     */
    private fun normalizeRepositorySymbolIds(groups: List<SymbolGroup>): List<SymbolGroup> {
        val reservedIds = groups.asSequence()
            .flatMap { it.symbols.asSequence() }
            .mapTo(mutableSetOf()) { it.id }
        val usedIds = mutableSetOf<Long>()
        var candidate = 1L
        return groups.map { group ->
            group.copy(symbols = group.symbols.map { symbol ->
                if (usedIds.add(symbol.id)) {
                    symbol
                } else {
                    // Do not steal an ID belonging to a later, otherwise-valid
                    // entry while walking the groups in stable display order.
                    while (candidate == 0L || candidate in reservedIds || candidate in usedIds) candidate++
                    symbol.copy(id = candidate).also {
                        usedIds += candidate
                        candidate++
                    }
                }
            })
        }
    }

    private fun decodePreviousV2Array(array: com.google.gson.JsonArray): List<SymbolGroup> {
        require(array.size() <= SymbolGroupBundleCodec.MAX_GROUPS) { "Too many stored groups" }
        val groups = array.mapIndexed { groupIndex, rawGroup ->
            val group = rawGroup.asJsonObject
            val symbols = group.getAsJsonArray("symbols") ?: com.google.gson.JsonArray()
            require(symbols.size() <= SymbolGroupBundleCodec.MAX_SYMBOLS_PER_GROUP) { "Too many symbols in group" }
            val converted = symbols.mapIndexed { index, rawSymbol ->
                val symbol = rawSymbol.asJsonObject
                val legacyShortId = symbol.get("shortType")?.takeUnless { it.isJsonNull }?.asInt
                val legacyShortText = symbol.get("shortPayload")?.takeUnless { it.isJsonNull }?.asString
                val legacyLongId = symbol.get("longType")?.takeUnless { it.isJsonNull }?.asInt
                val legacyLongText = symbol.get("longPayload")?.takeUnless { it.isJsonNull }?.asString
                val short = if (symbol.has("shortAction")) {
                    com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionJsonCodec.decode(symbol.getAsJsonObject("shortAction"))
                } else LegacySymbolActionAdapter.convert(LegacySymbolAction(
                    legacyShortId ?: error("Short action type is missing"),
                    legacyShortText
                ))
                val long = if (symbol.has("longAction") && !symbol.get("longAction").isJsonNull) {
                    com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionJsonCodec.decode(symbol.getAsJsonObject("longAction"))
                } else if (legacyLongId != null) {
                    LegacySymbolActionAdapter.convert(LegacySymbolAction(legacyLongId, legacyLongText))
                } else null
                SymbolEntry(
                    id = symbol.get("id")?.asLong ?: index.toLong() + 1L,
                    label = symbol.get("label")?.asString ?: error("Symbol label is missing"),
                    shortAction = short,
                    longAction = long,
                    icon = symbol.get("icon")?.takeUnless { it.isJsonNull }?.asString,
                    legacyShortActionId = legacyShortId,
                    legacyShortText = legacyShortText,
                    legacyLongActionId = legacyLongId,
                    legacyLongText = legacyLongText
                )
            }
            SymbolGroup(
                id = group.get("id")?.asString ?: "previous-v2-$groupIndex",
                title = group.get("title")?.asString ?: error("Group title is missing"),
                symbols = converted
            )
        }
        SymbolGroupBundleCodec.validateGroups(groups)
        return groups
    }
}
