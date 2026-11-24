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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionJsonCodec
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*

/** Versioned typed-action import/export codec shared by backups and V2 local persistence. */
object SymbolGroupBundleCodec {
    const val CURRENT_VERSION = 1
    const val MAX_GROUPS = 500
    const val MAX_SYMBOLS_PER_GROUP = 5_000
    const val MAX_TOTAL_SYMBOLS = 50_000
    const val MAX_BUNDLE_CHARS = 16 * 1024 * 1024
    const val MAX_ACTION_TEXT_CHARS = 1024 * 1024

    fun encode(groups: List<SymbolGroup>): String {
        validateGroups(groups)
        // Backups are a lossless interchange format, unlike the compact V1
        // runtime storage format. Always use the typed envelope so group IDs,
        // symbol IDs, icons, V2 actions and legacy action metadata survive.
        val envelope = JsonObject().apply {
            addProperty("format", "zero-symbol-drawer")
            addProperty("version", CURRENT_VERSION)
            add("groups", JsonArray().also { array ->
                groups.forEach { group ->
                    array.add(JsonObject().apply {
                        addProperty("id", group.id)
                        addProperty("title", group.title)
                        add("symbols", JsonArray().also { symbols ->
                            group.symbols.forEach { symbol ->
                                symbols.add(JsonObject().apply {
                                    addProperty("id", symbol.id)
                                    addProperty("label", symbol.label)
                                    add("shortAction", SymbolActionJsonCodec.encode(symbol.shortAction))
                                    symbol.longAction?.let {
                                        add("longAction", SymbolActionJsonCodec.encode(it))
                                    }
                                    symbol.icon?.let { addProperty("icon", it) }
                                    symbol.legacyShortActionId?.let { addProperty("legacyShortActionId", it) }
                                    symbol.legacyShortText?.let { addProperty("legacyShortText", it) }
                                    symbol.legacyLongActionId?.let { addProperty("legacyLongActionId", it) }
                                    symbol.legacyLongText?.let { addProperty("legacyLongText", it) }
                                })
                            }
                        })
                    })
                }
            })
        }
        return envelope.toString().also {
            require(it.length <= MAX_BUNDLE_CHARS) { "Backup is too large" }
        }
    }

    fun decode(json: String): List<SymbolGroup> {
        require(json.length <= MAX_BUNDLE_CHARS) { "Backup is too large" }
        val root = JsonParser.parseString(json)
        tryDecodeMtManager(root)?.let { return it }
        if (root.isJsonArray) {
            val array = root.asJsonArray
            if (array.size() == 0 || array[0].asJsonObject.has("n") || array[0].asJsonObject.has("d")) {
                return LegacySymbolDataCodec.decode(json)
            }
            return SymbolGroupStorageCodec.decode(json)
        }
        require(root.isJsonObject) { "Symbol group backup must be an object or V1 group array" }
        val envelope = root.asJsonObject
        require(envelope.keySet() == setOf("format", "version", "groups")) {
            "Symbol group backup fields do not match the supported envelope"
        }
        require(envelope.get("format").isJsonPrimitive &&
            envelope.get("format").asString == "zero-symbol-drawer") {
            "Not a supported symbol drawer backup"
        }
        require(envelope.get("version").isJsonPrimitive &&
            envelope.get("version").asJsonPrimitive.isNumber &&
            envelope.get("version").asInt.toDouble() == envelope.get("version").asDouble) {
            "Invalid backup version"
        }
        val version = envelope.get("version").asInt
        require(version == CURRENT_VERSION) { "Unsupported backup version: $version" }
        val groupElement = envelope.get("groups")
        require(groupElement.isJsonArray) { "Missing or invalid groups array" }
        val groupArray = groupElement.asJsonArray
        require(groupArray.size() <= MAX_GROUPS) { "Too many groups in backup" }
        val result = groupArray.map { groupElement ->
            require(groupElement.isJsonObject) { "Group entry must be an object" }
            val group = groupElement.asJsonObject
            require(group.keySet() == setOf("id", "title", "symbols")) {
                "Group entry does not match the supported schema"
            }
            require(group.get("id").isJsonPrimitive && group.get("id").asJsonPrimitive.isString &&
                group.get("title").isJsonPrimitive && group.get("title").asJsonPrimitive.isString &&
                group.get("symbols").isJsonArray) { "Group ID, title or symbols are invalid" }
            val symbols = group.getAsJsonArray("symbols")
            require(symbols.size() <= MAX_SYMBOLS_PER_GROUP) { "Too many symbols in group" }
            SymbolGroup(
                id = group.get("id")?.asString ?: error("Group ID is missing"),
                title = group.get("title")?.asString ?: error("Group title is missing"),
                symbols = symbols.map { element ->
                    require(element.isJsonObject) { "Symbol entry must be an object" }
                    val symbol = element.asJsonObject
                    require(symbol.keySet().containsAll(setOf("id", "label", "shortAction")) &&
                        symbol.keySet().all { it in setOf(
                            "id", "label", "shortAction", "longAction", "icon",
                            "legacyShortActionId", "legacyShortText",
                            "legacyLongActionId", "legacyLongText"
                        ) }) {
                        "Symbol entry does not match the supported schema"
                    }
                    require(symbol.get("id").isJsonPrimitive && symbol.get("id").asJsonPrimitive.isNumber &&
                        symbol.get("label").isJsonPrimitive && symbol.get("label").asJsonPrimitive.isString &&
                        symbol.get("shortAction").isJsonObject) { "Symbol ID, label or short action is invalid" }
                    val short = symbol.getAsJsonObject("shortAction")
                    val long = symbol.get("longAction")?.takeUnless { it.isJsonNull }?.asJsonObject
                        ?.let(SymbolActionJsonCodec::decode)
                    SymbolEntry(
                        id = symbol.get("id")?.asLong ?: error("Symbol ID is missing"),
                        label = symbol.get("label")?.asString ?: error("Symbol label is missing"),
                        shortAction = SymbolActionJsonCodec.decode(short),
                        longAction = long,
                        icon = symbol.get("icon")?.takeUnless { it.isJsonNull }?.asString,
                        legacyShortActionId = symbol.get("legacyShortActionId")?.takeUnless { it.isJsonNull }?.asInt,
                        legacyShortText = symbol.get("legacyShortText")?.takeUnless { it.isJsonNull }?.asString,
                        legacyLongActionId = symbol.get("legacyLongActionId")?.takeUnless { it.isJsonNull }?.asInt,
                        legacyLongText = symbol.get("legacyLongText")?.takeUnless { it.isJsonNull }?.asString
                    )
                }
            )
        }
        validateGroups(result)
        return result
    }

    internal fun validateGroups(groups: List<SymbolGroup>) {
        require(groups.size <= MAX_GROUPS) { "Too many groups" }
        var totalSymbols = 0
        require(groups.all { it.id.isNotBlank() && it.id.length <= 256 && it.title.isNotBlank() && it.title.length <= 4096 }) {
            "Group IDs and titles must be non-blank and within size limits"
        }
        require(groups.map { it.id }.distinct().size == groups.size) { "Duplicate group IDs" }
        groups.forEach { group ->
            require(group.symbols.size <= MAX_SYMBOLS_PER_GROUP) { "Too many symbols in group ${group.id}" }
            totalSymbols += group.symbols.size
            require(group.symbols.all {
                it.id != 0L && it.label.isNotEmpty() && it.label.length <= 4096 &&
                    (it.icon?.length ?: 0) <= 256 &&
                    (it.legacyShortText?.length ?: 0) <= MAX_ACTION_TEXT_CHARS &&
                    (it.legacyLongText?.length ?: 0) <= MAX_ACTION_TEXT_CHARS
            }) {
                "Symbol IDs, labels or icons are invalid in group ${group.id}"
            }
            require(group.symbols.map { it.id }.distinct().size == group.symbols.size) { "Duplicate symbol IDs in group ${group.id}" }
            group.symbols.forEach { symbol ->
                validateAction(symbol.shortAction, 0, ActionBudget())
                symbol.longAction?.let { validateAction(it, 0, ActionBudget()) }
            }
        }
        require(totalSymbols <= MAX_TOTAL_SYMBOLS) { "Too many symbols in backup" }
    }

    private class ActionBudget(private var remaining: Int = SymbolActionJsonCodec.MAX_ACTION_NODES) {
        fun consume() {
            remaining--
            require(remaining >= 0) { "Action tree contains too many nested actions" }
        }
    }

    private fun validateAction(action: SymbolAction, depth: Int, budget: ActionBudget) {
        require(depth <= 8) { "Action nesting limit exceeded" }
        budget.consume()
        when (action) {
            is SymbolAction.InsertText -> require(action.template.length <= MAX_ACTION_TEXT_CHARS) { "Insert action is too large" }
            is SymbolAction.ReplaceSelection -> require(action.text.length <= MAX_ACTION_TEXT_CHARS) { "Replace action is too large" }
            is SymbolAction.SurroundSelection -> require(action.prefix.length.toLong() + action.suffix.length <= MAX_ACTION_TEXT_CHARS) { "Surround action is too large" }
            is SymbolAction.EditorCommand -> {
                require(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,127}").matches(action.commandId)) { "Invalid editor command ID" }
                require((action.payload?.length ?: 0) <= MAX_ACTION_TEXT_CHARS) { "Editor command payload is too large" }
            }
            is SymbolAction.Extension -> {
                require(Regex("[a-zA-Z0-9][a-zA-Z0-9_.:/-]{0,255}").matches(action.extensionId)) { "Invalid extension ID" }
                require((action.payload?.length ?: 0) <= MAX_ACTION_TEXT_CHARS) { "Extension payload is too large" }
            }
            is SymbolAction.Composite -> {
                require(action.actions.size in 1..64) { "Invalid composite action size" }
                action.actions.forEach { validateAction(it, depth + 1, budget) }
            }
            is SymbolAction.MoveCursor, is SymbolAction.Delete, is SymbolAction.TransformSelection,
            is SymbolAction.Clipboard, SymbolAction.OpenManager -> Unit
        }
    }

    private fun tryDecodeMtManager(root: com.google.gson.JsonElement): List<SymbolGroup>? {
        val itemsArray: JsonArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                when {
                    obj.has("items") && obj.get("items").isJsonArray -> obj.getAsJsonArray("items")
                    obj.has("functions") && obj.get("functions").isJsonArray -> obj.getAsJsonArray("functions")
                    obj.has("list") && obj.get("list").isJsonArray -> obj.getAsJsonArray("list")
                    obj.has("buttons") && obj.get("buttons").isJsonArray -> obj.getAsJsonArray("buttons")
                    else -> null
                }
            }
            else -> null
        } ?: return null

        if (itemsArray.size() == 0) return null

        val first = itemsArray[0]
        if (!first.isJsonObject) return null
        val firstObj = first.asJsonObject
        if (firstObj.has("n") || (firstObj.has("d") && !firstObj.has("b")) || (firstObj.has("t") && firstObj.has("s"))) {
            return null
        }
        val isMtCandidate = (firstObj.has("b") && (firstObj.has("a") || firstObj.has("f") || firstObj.has("e"))) ||
            firstObj.has("name") || firstObj.has("title") || firstObj.has("label") ||
            firstObj.has("regex") || firstObj.has("search") || firstObj.has("find") ||
            firstObj.has("shortPress") || firstObj.has("action") || firstObj.has("type")

        if (!isMtCandidate) return null

        val symbols = mutableListOf<SymbolEntry>()
        for (i in 0 until itemsArray.size()) {
            val elem = itemsArray[i]
            if (!elem.isJsonObject) continue
            val item = elem.asJsonObject

            val name = when {
                item.has("b") && item.get("b").isJsonPrimitive -> item.get("b").asString
                item.has("name") && item.get("name").isJsonPrimitive -> item.get("name").asString
                item.has("title") && item.get("title").isJsonPrimitive -> item.get("title").asString
                item.has("label") && item.get("label").isJsonPrimitive -> item.get("label").asString
                item.has("id") && item.get("id").isJsonPrimitive -> item.get("id").asString
                else -> "Item ${i + 1}"
            }

            val action = parseMtAction(item) ?: continue
            val longAction = if (item.has("e") && item.get("e").isJsonPrimitive && !item.get("e").asString.isNullOrBlank() && item.has("f")) {
                SymbolAction.InsertText(item.get("e").asString)
            } else if (item.has("longPress") && item.get("longPress").isJsonObject) {
                parseMtAction(item.getAsJsonObject("longPress"))
            } else if (item.has("longClick") && item.get("longClick").isJsonObject) {
                parseMtAction(item.getAsJsonObject("longClick"))
            } else null

            symbols.add(
                SymbolEntry(
                    id = (i + 1).toLong(),
                    label = name,
                    shortAction = action,
                    longAction = longAction,
                    legacyShortActionId = if (action is SymbolAction.Extension) 101 else 0
                )
            )
        }

        if (symbols.isEmpty()) return null
        return listOf(SymbolGroup("mt_manager_import", "MT Manager", symbols))
    }

    private fun parseMtAction(item: JsonObject): SymbolAction? {
        if (item.has("f") && item.get("f").isJsonObject) {
            val f = item.getAsJsonObject("f")
            val search = if (f.has("a") && f.get("a").isJsonPrimitive) f.get("a").asString else ""
            val replace = if (f.has("b") && f.get("b").isJsonPrimitive) f.get("b").asString else ""
            val matchCase = if (f.has("c") && f.get("c").isJsonPrimitive) f.get("c").asBoolean else false
            val isRegex = if (f.has("d") && f.get("d").isJsonPrimitive) {
                if (f.get("d").asJsonPrimitive.isNumber) f.get("d").asInt == 1 else f.get("d").asBoolean
            } else true
            val replaceAll = if (f.has("e") && f.get("e").isJsonPrimitive) {
                if (f.get("e").asJsonPrimitive.isNumber) f.get("e").asInt == 1 else f.get("e").asBoolean
            } else true

            val options = StringBuilder().apply {
                if (isRegex) append('r')
                if (matchCase) append('c')
                if (replaceAll) append('a')
            }.toString()

            val payload = MpManagerFindReplaceEnvelope.encodePayload(search, replace, options)
            return SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, payload)
        }

        if (item.has("a") && item.get("a").isJsonPrimitive && item.get("a").asInt == 22) {
            return SymbolAction.OpenManager
        }

        if (item.has("e") && item.get("e").isJsonPrimitive && !item.get("e").asString.isNullOrBlank()) {
            return SymbolAction.InsertText(item.get("e").asString)
        }

        if (item.has("shortPress") && item.get("shortPress").isJsonObject) {
            val nested = parseMtAction(item.getAsJsonObject("shortPress"))
            if (nested != null) return nested
        }
        if (item.has("action") && item.get("action").isJsonObject) {
            val nested = parseMtAction(item.getAsJsonObject("action"))
            if (nested != null) return nested
        }

        val search = when {
            item.has("search") && item.get("search").isJsonPrimitive -> item.get("search").asString
            item.has("regex") && item.get("regex").isJsonPrimitive && !item.get("regex").asJsonPrimitive.isBoolean -> item.get("regex").asString
            item.has("find") && item.get("find").isJsonPrimitive -> item.get("find").asString
            item.has("pattern") && item.get("pattern").isJsonPrimitive -> item.get("pattern").asString
            else -> null
        }
        val replace = when {
            item.has("replace") && item.get("replace").isJsonPrimitive -> item.get("replace").asString
            item.has("replacement") && item.get("replacement").isJsonPrimitive -> item.get("replacement").asString
            item.has("replaceWith") && item.get("replaceWith").isJsonPrimitive -> item.get("replaceWith").asString
            item.has("secondText") && item.get("secondText").isJsonPrimitive -> item.get("secondText").asString
            else -> ""
        }
        if (search != null) {
            val isRegex = if (item.has("isRegex")) item.get("isRegex").asBoolean else if (item.has("regex") && item.get("regex").isJsonPrimitive && item.get("regex").asJsonPrimitive.isBoolean) item.get("regex").asBoolean else true
            val matchCase = if (item.has("matchCase")) item.get("matchCase").asBoolean else true
            val replaceAll = if (item.has("replaceAll")) item.get("replaceAll").asBoolean else true
            val options = StringBuilder().apply {
                if (isRegex) append('r')
                if (matchCase) append('c')
                if (replaceAll) append('a')
            }.toString()

            val payload = MpManagerFindReplaceEnvelope.encodePayload(search, replace, options)
            return SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, payload)
        }

        val insertText = when {
            item.has("insert") && item.get("insert").isJsonPrimitive -> item.get("insert").asString
            item.has("text") && item.get("text").isJsonPrimitive -> item.get("text").asString
            item.has("snippet") && item.get("snippet").isJsonPrimitive -> item.get("snippet").asString
            item.has("content") && item.get("content").isJsonPrimitive -> item.get("content").asString
            else -> null
        }
        if (insertText != null) {
            return SymbolAction.InsertText(insertText)
        }

        return null
    }
}
