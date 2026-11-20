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

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySymbolDataCodecTest {
    @Test fun mpManagerReplacePayloadAndOptionsSurviveImportStorageAndExport() {
        val json = """[{"n":"Custom","d":[{"a":101,"b":"替换","c":"你好(.*)","d":101,"e":"foo","f":"世界$1","g":"bar","h":"rca","i":"c"}]}]"""
        val groups = LegacySymbolDataCodec.decode(json)
        val stored = SymbolGroupStorageCodec.decode(SymbolGroupStorageCodec.encode(groups))
        val symbol = stored.single().symbols.single()
        val short = symbol.shortAction as com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction.Extension
        val payload = com.google.gson.JsonParser.parseString(short.payload).asJsonObject
        assertEquals("你好(.*)", payload.get("text").asString)
        assertEquals("世界$1", payload.get("secondText").asString)
        assertEquals("rca", payload.get("options").asString)
        assertEquals(com.google.gson.JsonParser.parseString(json),
            com.google.gson.JsonParser.parseString(LegacySymbolDataCodec.encode(stored)))
        assertEquals(groups, LegacySymbolDataCodec.decode(LegacySymbolDataCodec.encode(stored)))
    }

    @Test
    fun decodeAllocatesSymbolIdsAcrossAllGroupsAndPreservesLegacyActions() {
        val json = """
            [
              {"n":"First","d":[{"a":0,"b":"A","c":"alpha"},{"a":3,"b":"B"}]},
              {"n":"Second","d":[{"a":7,"b":"C","c":"payload"}]}
            ]
        """.trimIndent()

        val groups = LegacySymbolDataCodec.decode(json)

        assertEquals(listOf(1L, 2L), groups[0].symbols.map { it.id })
        assertEquals(listOf(3L), groups[1].symbols.map { it.id })
        assertEquals(listOf(1L, 2L, 3L), groups.flatMap { it.symbols }.map { it.id })
        assertEquals(0, groups[0].symbols[0].legacyShortActionId)
        assertEquals("alpha", groups[0].symbols[0].legacyShortText)
        assertEquals(3, groups[0].symbols[1].legacyShortActionId)
        assertEquals(7, groups[1].symbols[0].legacyShortActionId)
        assertEquals("payload", groups[1].symbols[0].legacyShortText)
    }

    @Test fun historicalClipboardActionIdsRoundTripWithoutRenumbering() {
        val legacy = """[{"n":"剪贴板","d":[{"a":0,"b":"复制","d":1},{"a":0,"b":"剪切","d":2}]}]"""
        val decoded = LegacySymbolDataCodec.decode(legacy)
        val roundTrip = LegacySymbolDataCodec.decode(LegacySymbolDataCodec.encode(decoded))
        assertEquals(1, roundTrip[0].symbols[0].legacyLongActionId)
        assertEquals(2, roundTrip[0].symbols[1].legacyLongActionId)
    }

    @Test fun v1CompactExportPreservesGroupAndSymbolActions() {
        val json = """[{"n":"常用","d":[{"a":0,"b":"括号","c":"()","d":0,"e":"[]"}]}]"""
        val decoded = LegacySymbolDataCodec.decode(json)
        assertEquals(json, LegacySymbolDataCodec.encode(decoded))
    }

    @Test(expected = IllegalStateException::class)
    fun v1CompactExportRejectsV2OnlyCompositeAction() {
        val group = com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup(
            id = "custom",
            title = "Custom",
            symbols = listOf(
                com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry(
                    id = 1L,
                    label = "Composite",
                    shortAction = com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction.Composite(
                        listOf(com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction.InsertText("a"))
                    )
                )
            )
        )
        LegacySymbolDataCodec.encode(listOf(group))
    }

    @Test fun mpManagerCutLineKeepsItsClipboardSemantics() {
        val legacy = """[{"n":"行","d":[{"a":34,"b":"剪切行"}]}]"""
        val decoded = LegacySymbolDataCodec.decode(legacy)
        assertEquals(
            com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction.EditorCommand("line.cut"),
            decoded[0].symbols[0].shortAction
        )
        assertEquals(legacy, LegacySymbolDataCodec.encode(decoded))
    }
}
