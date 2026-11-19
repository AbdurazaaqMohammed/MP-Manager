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
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction

/**
 * Typed form helpers for MP Manager's V1 custom find/replace action.
 *
 * V1 action 101 carries three payloads on the legacy wire format:
 * text (field c/e) = find text, secondText (field f/g) = replacement text and
 * options (field h/i) = flag string with r=regex, c=match case, a=replace all.
 *
 * [LegacySymbolDataCodec] decodes it to
 * `SymbolAction.Extension("legacy.action.101", {"mpManager":true,"text":...,
 * "secondText":...,"options":...})` and expands that envelope back to the V1
 * c/f/h and e/g/i fields on export. This object is the shared contract for the
 * manager form so the editor and the codecs never drift apart. It is
 * deliberately free of Android dependencies so it can be regression-tested on
 * the JVM.
 */
object MpManagerFindReplaceEnvelope {
    /** V1 custom action ID for find/replace (host: BottomBarMigration.ACTION_FIND_REPLACE). */
    const val LEGACY_ACTION_ID = 101

    /** Typed extension ID produced by [LegacySymbolDataCodec] when decoding action 101. */
    const val EXTENSION_ID = "legacy.action.101"

    /** V1 host default when the options field is absent: regex + match case. */
    const val DEFAULT_OPTIONS = "rc"

    const val FLAG_REGEX = 'r'
    const val FLAG_MATCH_CASE = 'c'
    const val FLAG_REPLACE_ALL = 'a'

    private const val MARKER_KEY = "mpManager"
    private const val TEXT_KEY = "text"
    private const val SECOND_TEXT_KEY = "secondText"
    private const val OPTIONS_KEY = "options"

    /** Parsed view of the typed find/replace envelope. */
    data class Payload(
        val findText: String?,
        val replacementText: String?,
        val options: String?
    )

    /** True only for the exact extension ID the codec produces for action 101. */
    fun isFindReplaceExtension(action: SymbolAction?): Boolean =
        action is SymbolAction.Extension && action.extensionId == EXTENSION_ID

    /**
     * Builds the typed envelope payload. Null/empty find or replacement text is
     * stored as JSON null, matching the shape [LegacySymbolDataCodec] emits when
     * importing V1 items whose corresponding fields are absent. An empty
     * options string stays an empty string (V1 treats "no flags" differently
     * from an absent field, which the host defaults to [DEFAULT_OPTIONS]).
     */
    fun encodePayload(findText: String?, replacementText: String?, options: String?): String =
        JsonObject().apply {
            addProperty(MARKER_KEY, true)
            addProperty(TEXT_KEY, findText?.takeIf { it.isNotEmpty() })
            addProperty(SECOND_TEXT_KEY, replacementText?.takeIf { it.isNotEmpty() })
            addProperty(OPTIONS_KEY, options)
        }.toString()

    /**
     * Parses the typed envelope. Returns null for null/blank payloads and for
     * JSON that is not an object carrying the mpManager marker, so a plain
     * legacy payload can never be mistaken for envelope data.
     */
    fun decodePayload(payload: String?): Payload? {
        if (payload.isNullOrBlank()) return null
        val obj = runCatching { JsonParser.parseString(payload) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null
        val marked = obj.get(MARKER_KEY)
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: return null
        if (!marked) return null
        fun text(key: String): String? = obj.get(key)
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        return Payload(text(TEXT_KEY), text(SECOND_TEXT_KEY), text(OPTIONS_KEY))
    }

    /** Decodes a 101 extension action, or null when the action is something else. */
    fun decodeAction(action: SymbolAction?): Payload? =
        (action as? SymbolAction.Extension)
            ?.takeIf { it.extensionId == EXTENSION_ID }
            ?.let { decodePayload(it.payload) ?: Payload(it.payload, null, null) }

    /** Builds the V1 options string from independent checkbox states. */
    fun encodeOptions(regex: Boolean, matchCase: Boolean, replaceAll: Boolean): String =
        buildString {
            if (regex) append(FLAG_REGEX)
            if (matchCase) append(FLAG_MATCH_CASE)
            if (replaceAll) append(FLAG_REPLACE_ALL)
        }

    /** Reads one option flag; a null options field counts as unset. */
    fun hasFlag(options: String?, flag: Char): Boolean = (options?.indexOf(flag) ?: -1) >= 0

    /**
     * Option flags for the form checkboxes. A null options field (V1 items
     * saved without h/i) falls back to the host default "rc".
     */
    fun optionsOrDefault(options: String?): String = options ?: DEFAULT_OPTIONS
}
