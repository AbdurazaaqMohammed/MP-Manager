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

package com.zerostudio.idea.widget.editor.symbolinput.v2.action

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*

/**
 * Versioned, bounded action codec. Missing schemaVersion is accepted as V1 for
 * compatibility with early V2 snapshots; all newly written actions declare V1.
 * Decoding is bounded independently of the executor so imported data cannot
 * construct an arbitrarily large action tree before execution validation.
 */
object SymbolActionJsonCodec {
    fun encode(action: SymbolAction): JsonObject {
        val json = JsonObject().apply { addProperty("schemaVersion", CURRENT_SCHEMA_VERSION) }
        when (action) {
            is SymbolAction.InsertText -> { json.addProperty("kind", "insertText"); json.addProperty("template", action.template) }
            is SymbolAction.ReplaceSelection -> { json.addProperty("kind", "replaceSelection"); json.addProperty("text", action.text) }
            is SymbolAction.SurroundSelection -> {
                json.addProperty("kind", "surroundSelection")
                json.addProperty("prefix", action.prefix)
                json.addProperty("suffix", action.suffix)
            }
            is SymbolAction.MoveCursor -> { json.addProperty("kind", "moveCursor"); json.addProperty("target", action.target.name) }
            is SymbolAction.Delete -> { json.addProperty("kind", "delete"); json.addProperty("deleteKind", action.kind.name) }
            is SymbolAction.TransformSelection -> { json.addProperty("kind", "transformSelection"); json.addProperty("transform", action.transform.name) }
            is SymbolAction.EditorCommand -> {
                json.addProperty("kind", "editorCommand")
                json.addProperty("commandId", action.commandId)
                action.payload?.let { json.addProperty("payload", it) }
            }
            is SymbolAction.Clipboard -> { json.addProperty("kind", "clipboard"); json.addProperty("operation", action.operation.name) }
            SymbolAction.OpenManager -> json.addProperty("kind", "openManager")
            is SymbolAction.Composite -> {
                json.addProperty("kind", "composite")
                json.add("actions", JsonArray().also { array -> action.actions.forEach { array.add(encode(it)) } })
            }
            is SymbolAction.Extension -> {
                json.addProperty("kind", "extension")
                json.addProperty("extensionId", action.extensionId)
                action.payload?.let { json.addProperty("payload", it) }
            }
        }
        return json
    }

    fun decode(json: JsonObject, depth: Int = 0): SymbolAction {
        require(depth in 0..MAX_NESTING_DEPTH) { "Action JSON nesting limit exceeded" }
        return decodeInternal(json, depth, DecodeBudget())
    }

    private fun decodeInternal(
        json: JsonObject,
        depth: Int,
        budget: DecodeBudget
    ): SymbolAction {
        require(depth <= MAX_NESTING_DEPTH) { "Action JSON nesting limit exceeded" }
        budget.consumeNode()
        val version = json.get("schemaVersion")?.let { raw ->
            require(raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber) {
                "Invalid action schemaVersion"
            }
            raw.asInt.also {
                require(raw.asDouble == it.toDouble()) { "Action schemaVersion must be an integer" }
            }
        } ?: LEGACY_SCHEMA_VERSION
        require(version == CURRENT_SCHEMA_VERSION) {
            "Unsupported action schema version: $version"
        }
        fun required(name: String): String {
            val value = json.get(name)
                ?: throw IllegalArgumentException("Missing action field: $name")
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "Invalid action field: $name must be a string"
            }
            return value.asString
        }
        fun optional(name: String): String? {
            val value = json.get(name) ?: return null
            if (value.isJsonNull) return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "Invalid action field: $name must be a string"
            }
            return value.asString
        }
        fun bounded(name: String): String = required(name).also {
            require(it.length <= MAX_TEXT_LENGTH) { "Action field $name exceeds the size limit" }
        }
        fun boundedOptional(name: String): String? = optional(name)?.also {
            require(it.length <= MAX_TEXT_LENGTH) { "Action field $name exceeds the size limit" }
        }
        fun <T : Enum<T>> enumValueOf(name: String, values: Array<T>): T =
            values.firstOrNull { it.name == required(name) }
                ?: throw IllegalArgumentException("Invalid action enum: $name")

        return when (required("kind")) {
            "insertText" -> SymbolAction.InsertText(bounded("template"))
            "replaceSelection" -> SymbolAction.ReplaceSelection(bounded("text"))
            "surroundSelection" -> {
                val prefix = bounded("prefix")
                val suffix = bounded("suffix")
                require(prefix.length.toLong() + suffix.length <= MAX_TEXT_LENGTH) {
                    "Surround template exceeds the size limit"
                }
                SymbolAction.SurroundSelection(prefix, suffix)
            }
            "moveCursor" -> SymbolAction.MoveCursor(enumValueOf("target", CursorTarget.values()))
            "delete" -> SymbolAction.Delete(enumValueOf("deleteKind", DeleteKind.values()))
            "transformSelection" -> SymbolAction.TransformSelection(enumValueOf("transform", SelectionTransform.values()))
            "editorCommand" -> {
                val id = required("commandId")
                require(COMMAND_ID.matches(id)) { "Invalid editor command ID" }
                SymbolAction.EditorCommand(id, boundedOptional("payload"))
            }
            "clipboard" -> SymbolAction.Clipboard(enumValueOf("operation", ClipboardOperation.values()))
            "openManager" -> SymbolAction.OpenManager
            "composite" -> {
                val array = json.getAsJsonArray("actions")
                    ?: throw IllegalArgumentException("Composite action has no actions")
                require(array.size() in 1..64) { "Invalid composite action size" }
                SymbolAction.Composite(array.map { element ->
                    require(element.isJsonObject) { "Composite action entries must be objects" }
                    decodeInternal(element.asJsonObject, depth + 1, budget)
                })
            }
            "extension" -> {
                val id = required("extensionId")
                require(EXTENSION_ID.matches(id)) { "Invalid extension ID" }
                SymbolAction.Extension(id, boundedOptional("payload"))
            }
            else -> throw IllegalArgumentException("Unknown action kind")
        }
    }

    private class DecodeBudget(var nodesRemaining: Int = MAX_ACTION_NODES) {
        fun consumeNode() {
            nodesRemaining--
            require(nodesRemaining >= 0) { "Action JSON contains too many nested actions" }
        }
    }

    private const val CURRENT_SCHEMA_VERSION = 1
    private const val LEGACY_SCHEMA_VERSION = 1
    private const val MAX_NESTING_DEPTH = 8
    internal const val MAX_ACTION_NODES = 256
    private const val MAX_TEXT_LENGTH = 1_048_576
    private val COMMAND_ID = Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,127}")
    private val EXTENSION_ID = Regex("[a-zA-Z0-9][a-zA-Z0-9_.:/-]{0,255}")
}
