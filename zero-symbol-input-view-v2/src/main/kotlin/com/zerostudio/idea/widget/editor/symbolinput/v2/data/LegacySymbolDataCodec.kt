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

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup as LegacySymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem as LegacySymbolItem
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*

/** Persists the exact V1 compact JSON contract: group {n,d}, item {a,b,c,d,e}. */
object LegacySymbolDataCodec {
    private val gson = Gson()

    fun encode(groups: List<SymbolGroup>): String {
        // Build the compact V1 wire shape explicitly. This avoids relying on
        // reflective Kotlin data-class serialization for nullable payload fields.
        val root = com.google.gson.JsonArray()
        groups.forEach { group ->
            val encodedGroup = com.google.gson.JsonObject().apply {
                addProperty("n", group.title)
                add("d", com.google.gson.JsonArray().apply {
                    group.symbols.forEach { symbol ->
                        val short = legacyOverrideIfUnchanged(
                            symbol.legacyShortActionId, symbol.legacyShortText, symbol.shortAction
                        ) ?: toLegacy(symbol.shortAction)
                        val long = symbol.longAction?.let { action ->
                            legacyOverrideIfUnchanged(
                                symbol.legacyLongActionId, symbol.legacyLongText, action
                            ) ?: toLegacy(action)
                        }
                        add(com.google.gson.JsonObject().apply {
                            addProperty("a", short.type)
                            addProperty("b", symbol.label)
                            writePayload(this, short, "c", "f", "h")
                            long?.let {
                                addProperty("d", it.type)
                                writePayload(this, it, "e", "g", "i")
                            }
                        })
                    }
                })
            }
            root.add(encodedGroup)
        }
        return root.toString()
    }

    fun decode(json: String): List<SymbolGroup> {
        validateLegacyShape(json)
        val type = object : TypeToken<MutableList<LegacySymbolGroup>>() {}.type
        val legacy: MutableList<LegacySymbolGroup> = gson.fromJson(json, type)
            ?: error("Symbol group JSON is empty")
        require(legacy.size <= SymbolGroupBundleCodec.MAX_GROUPS) { "Too many groups" }
        var total = 0
        // V1 did not persist symbol IDs. Allocate migration IDs across the
        // entire imported repository, not from 1 independently per group.
        // This satisfies V2's repository-wide identity invariant without
        // changing any V1 action IDs or action payloads.
        var nextSymbolId = 1L
        return legacy.mapIndexed { groupIndex, group ->
            require(group.name.isNotBlank()) { "Group name is empty" }
            require(group.items.size <= SymbolGroupBundleCodec.MAX_SYMBOLS_PER_GROUP) { "Too many symbols in group ${group.name}" }
            total += group.items.size
            val symbols = group.items.mapIndexed { itemIndex, item ->
                require(item.display.isNotEmpty()) { "Symbol display is empty" }
                SymbolEntry(
                    id = nextSymbolId++,
                    label = item.display,
                    shortAction = convertAction(item.shortAction, item.shortText, item.secondText, item.options),
                    longAction = item.longAction?.let { convertAction(it, item.longText, item.longSecondText, item.longOptions) },
                    legacyShortActionId = item.shortAction,
                    legacyShortText = item.shortText,
                    legacyLongActionId = item.longAction,
                    legacyLongText = item.longText
                )
            }
            SymbolGroup("legacy-$groupIndex-${group.name.hashCode()}", group.name, symbols)
        }.also {
            require(total <= SymbolGroupBundleCodec.MAX_TOTAL_SYMBOLS) { "Too many symbols" }
            SymbolGroupBundleCodec.validateGroups(it)
        }
    }

    /**
     * Strictly validates the original V1 wire model before Gson conversion.
     * Gson's default lenient field handling must never turn unrelated JSON into
     * an empty or partially populated symbol-group collection.
     */
    private fun validateLegacyShape(json: String) {
        require(json.length <= SymbolGroupBundleCodec.MAX_BUNDLE_CHARS) { "Symbol group JSON is too large" }
        val root = com.google.gson.JsonParser.parseString(json)
        require(root.isJsonArray) { "Symbol group JSON must be an array" }
        require(root.asJsonArray.size() <= SymbolGroupBundleCodec.MAX_GROUPS) {
            "Too many groups in symbol group JSON"
        }
        var total = 0
        root.asJsonArray.forEachIndexed { groupIndex, groupElement ->
            require(groupElement.isJsonObject) { "Group #${groupIndex + 1} must be an object" }
            val group = groupElement.asJsonObject
            require(group.keySet() == setOf("n", "d")) { "Group #${groupIndex + 1} must contain only n and d fields" }
            val name = group.get("n")
            require(name.isJsonPrimitive && name.asJsonPrimitive.isString && name.asString.isNotBlank()) {
                "Group #${groupIndex + 1} has an invalid n (name) field"
            }
            val items = group.get("d")
            require(items.isJsonArray && items.asJsonArray.size() <= SymbolGroupBundleCodec.MAX_SYMBOLS_PER_GROUP) {
                "Group '${name.asString}' has an invalid d (items) field"
            }
            items.asJsonArray.forEachIndexed { itemIndex, itemElement ->
                require(itemElement.isJsonObject) { "Symbol #${itemIndex + 1} in '${name.asString}' must be an object" }
                val item = itemElement.asJsonObject
                require(item.keySet().containsAll(setOf("a", "b")) &&
                    item.keySet().all { it in setOf("a", "b", "c", "d", "e", "f", "g", "h", "i") }) {
                    "Symbol #${itemIndex + 1} in '${name.asString}' must use V1 fields a/b/c/d/e"
                }
                val shortId = item.get("a")
                val display = item.get("b")
                val parsedShortId = integerActionId(shortId)
                require(parsedShortId != null && parsedShortId >= 0) {
                    "Symbol #${itemIndex + 1} has an invalid short action ID"
                }
                require(display.isJsonPrimitive && display.asJsonPrimitive.isString &&
                    display.asString.isNotEmpty() && display.asString.length <= 4096) {
                    "Symbol #${itemIndex + 1} has an invalid display field"
                }
                listOf("c", "e", "f", "g", "h", "i").forEach { field ->
                    val value = item.get(field)
                    require(value == null || value.isJsonNull ||
                        (value.isJsonPrimitive && value.asJsonPrimitive.isString &&
                            value.asString.length <= SymbolGroupBundleCodec.MAX_ACTION_TEXT_CHARS)) {
                        "Symbol #${itemIndex + 1} has an invalid $field payload"
                    }
                }
                val longId = item.get("d")
                val parsedLongId = longId?.takeUnless { it.isJsonNull }?.let(::integerActionId)
                require(longId == null || longId.isJsonNull ||
                    (parsedLongId != null && parsedLongId >= 0)) {
                    "Symbol #${itemIndex + 1} has an invalid long action ID"
                }
            }
            total += items.asJsonArray.size()
            require(total <= SymbolGroupBundleCodec.MAX_TOTAL_SYMBOLS) { "Too many symbols in import" }
        }
    }

    private fun writePayload(target: com.google.gson.JsonObject, action: LegacySymbolAction,
                             textKey: String, secondKey: String, optionsKey: String) {
        val payload = action.payload
        val fields = if (action.type == 101 && payload != null)
            runCatching { com.google.gson.JsonParser.parseString(payload).asJsonObject }.getOrNull()
        else null
        if (fields?.get("mpManager")?.asBoolean == true) {
            listOf("text" to textKey, "secondText" to secondKey, "options" to optionsKey).forEach { (from, to) ->
                fields.get(from)?.takeUnless { it.isJsonNull }?.let { target.addProperty(to, it.asString) }
            }
        } else payload?.let { target.addProperty(textKey, it) }
    }

    private fun convertAction(id: Int, text: String?, secondText: String?, options: String?): SymbolAction {
        if (id == 101) {
            val payload = com.google.gson.JsonObject().apply {
                addProperty("mpManager", true)
                addProperty("text", text)
                addProperty("secondText", secondText)
                addProperty("options", options)
            }
            return SymbolAction.Extension("legacy.action.101", payload.toString())
        }
        return LegacySymbolActionAdapter.convert(LegacySymbolAction(id, text))
    }

    private fun integerActionId(value: com.google.gson.JsonElement): Int? {
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return runCatching { value.asBigDecimal.intValueExact() }.getOrNull()
    }

    /**
     * Legacy metadata is a compatibility hint, not the source of truth. Retain
     * it only while decoding that ID/payload still produces the current action.
     * This prevents an edited typed action from being overwritten by stale V1 data.
     */
    private fun legacyOverrideIfUnchanged(
        id: Int?,
        payload: String?,
        action: SymbolAction
    ): LegacySymbolAction? {
        val legacy = id?.let { LegacySymbolAction(it, payload) } ?: return null
        return legacy.takeIf {
            runCatching { LegacySymbolActionAdapter.convert(it) == action }.getOrDefault(false)
        }
    }

    private fun toLegacy(action: SymbolAction): LegacySymbolAction = when (action) {
        is SymbolAction.InsertText -> LegacySymbolAction(0, action.template)
        is SymbolAction.Delete -> when (action.kind) {
            DeleteKind.CURRENT_LINE -> LegacySymbolAction(3)
            else -> error("V1 format cannot preserve delete kind ${action.kind}")
        }
        is SymbolAction.TransformSelection -> when (action.transform) {
            SelectionTransform.UPPERCASE -> LegacySymbolAction(7)
            SelectionTransform.LOWERCASE -> LegacySymbolAction(8)
            SelectionTransform.TOGGLE_CASE -> error("V1 has no toggle-case action")
        }
        is SymbolAction.MoveCursor -> LegacySymbolAction(when (action.target) {
            CursorTarget.LINE_START -> 16; CursorTarget.LINE_END -> 17
            CursorTarget.LEFT -> 18; CursorTarget.RIGHT -> 19
            CursorTarget.UP -> 20; CursorTarget.DOWN -> 21
            CursorTarget.DOCUMENT_START -> 23; CursorTarget.DOCUMENT_END -> 24
        })
        is SymbolAction.Clipboard -> LegacySymbolAction(when (action.operation) {
            ClipboardOperation.COPY -> 28; ClipboardOperation.CUT -> 29; ClipboardOperation.PASTE -> 30
        })
        SymbolAction.OpenManager -> LegacySymbolAction(22)
        is SymbolAction.EditorCommand -> when (action.commandId) {
            "line.clear" -> LegacySymbolAction(4)
            "line.copy" -> LegacySymbolAction(33)
            "line.cut" -> LegacySymbolAction(34)
            "editor.indent" -> LegacySymbolAction(9)
            "editor.toggleLineComment" -> LegacySymbolAction(11)
            "selection.selectAll" -> LegacySymbolAction(25)
            "editor.format" -> LegacySymbolAction(32)
            "line.duplicate" -> LegacySymbolAction(35)
            "line.replace" -> LegacySymbolAction(36, action.payload)
            "editor.unindent" -> LegacySymbolAction(39)
            "history.undo" -> LegacySymbolAction(55)
            "history.redo" -> LegacySymbolAction(56)
            else -> error("Action '${action.commandId}' is not part of the V1 action catalog")
        }
        is SymbolAction.Extension -> {
            val id = Regex("legacy\\.action\\.(\\d+)").matchEntire(action.extensionId)
                ?: error("Extension '${action.extensionId}' cannot be represented by V1 JSON")
            LegacySymbolAction(id.groupValues[1].toInt(), action.payload)
        }
        is SymbolAction.ReplaceSelection -> LegacySymbolAction(0, action.text)
        is SymbolAction.SurroundSelection -> error("Surround-selection is not part of the V1 action catalog")
        is SymbolAction.Composite -> error("Composite actions are not part of the V1 action catalog")
    }

}
