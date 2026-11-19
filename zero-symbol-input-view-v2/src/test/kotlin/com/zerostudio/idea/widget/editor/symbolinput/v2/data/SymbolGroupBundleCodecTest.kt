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

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolGroupBundleCodecTest {

    @Test fun roundTripsNamespacedExtensionIdsAndV2ActionKinds() {
        val group = listOf(
            SymbolGroup(
                id = "custom/group:one",
                title = "Actions",
                symbols = listOf(
                    SymbolEntry(
                        id = 99L,
                        label = "Custom",
                        shortAction = SymbolAction.Extension("com.example/editor:paste", "payload"),
                        longAction = SymbolAction.Composite(
                            listOf(
                                SymbolAction.TransformSelection(SelectionTransform.TOGGLE_CASE),
                                SymbolAction.SurroundSelection("(", ")")
                            )
                        ),
                        icon = "custom-icon"
                    )
                )
            )
        )
        assertEquals(group, SymbolGroupBundleCodec.decode(SymbolGroupBundleCodec.encode(group)))
    }
    @Test fun roundTripsGroupsSymbolsIconsAndTypedActions() {
        val groups = listOf(
            SymbolGroup(
                "kotlin",
                "Kotlin",
                listOf(
                    SymbolEntry(
                        7L,
                        "fun",
                        SymbolAction.InsertText("fun "),
                        SymbolAction.MoveCursor(CursorTarget.LINE_END),
                        icon = "ƒ"
                    )
                )
            )
        )
        val restored = SymbolGroupBundleCodec.decode(SymbolGroupBundleCodec.encode(groups))
        assertEquals(groups.single().title, restored.single().title)
        assertEquals(groups.single().symbols.single().label, restored.single().symbols.single().label)
        assertEquals(groups.single().symbols.single().shortAction, restored.single().symbols.single().shortAction)
        assertEquals(groups.single().symbols.single().longAction, restored.single().symbols.single().longAction)
    }


    @Test fun bundlePreservesV2ActionsAndLegacyMetadataWhenCompactV1CannotRepresentThem() {
        val source = listOf(
            SymbolGroup("modern", "Modern", listOf(
                SymbolEntry(
                    id = 42L,
                    label = "toggle",
                    shortAction = SymbolAction.TransformSelection(SelectionTransform.TOGGLE_CASE),
                    longAction = SymbolAction.Composite(listOf(
                        SymbolAction.InsertText("a"),
                        SymbolAction.SurroundSelection("[", "]")
                    )),
                    icon = "T",
                    legacyShortActionId = 33,
                    legacyShortText = "kept"
                )
            ))
        )
        val json = SymbolGroupBundleCodec.encode(source)
        assertTrue(json.contains("\"format\":\"zero-symbol-drawer\""))
        assertEquals(source, SymbolGroupBundleCodec.decode(json))
    }

    @Test fun storageAcceptsWhitespaceOnlyLabelsForControlKeys() {
        val groups = listOf(
            SymbolGroup(
                "controls",
                "Controls",
                listOf(
                    SymbolEntry(1L, "\n", SymbolAction.InsertText("\n")),
                    SymbolEntry(2L, "\t", SymbolAction.InsertText("\t"))
                )
            )
        )

        val encoded = SymbolGroupStorageCodec.encode(groups)
        assertEquals(groups.map { it.symbols.map(SymbolEntry::label) }, SymbolGroupStorageCodec.decode(encoded).map { it.symbols.map(SymbolEntry::label) })
    }

    @Test fun defaultGroupsCanBePersistedWithoutCrashing() {
        val defaults = SymbolDrawerDefaults.createGroups()

        val encoded = SymbolGroupStorageCodec.encode(defaults)
        val restored = SymbolGroupStorageCodec.decode(encoded)

        assertEquals(defaults.single().title, restored.single().title)
        assertEquals(defaults.single().symbols.map { it.label }, restored.single().symbols.map { it.label })
        assertEquals(defaults.single().symbols.map { it.shortAction to it.longAction }, restored.single().symbols.map { it.shortAction to it.longAction })
        assertTrue(restored.single().symbols.any { it.label == "\n" })
        assertTrue(restored.single().symbols.any { it.label == "\t" })
    }

    @Test fun legacyExportUsesCurrentTypedActionInsteadOfStalePayloadMetadata() {
        val groups = listOf(SymbolGroup(
            "legacy", "Legacy", listOf(SymbolEntry(
                id = 1L,
                label = "replace",
                shortAction = SymbolAction.EditorCommand("line.replace", "new"),
                legacyShortActionId = 36,
                legacyShortText = "old"
            ))
        ))
        val restored = LegacySymbolDataCodec.decode(LegacySymbolDataCodec.encode(groups))
        assertEquals(
            SymbolAction.EditorCommand("line.replace", "new"),
            restored.single().symbols.single().shortAction
        )
    }

    @Test fun legacyExportRejectsDeleteKindsThatV1CannotRepresent() {
        val groups = listOf(SymbolGroup(
            "legacy", "Legacy", listOf(SymbolEntry(
                id = 1L,
                label = "delete to line start",
                shortAction = SymbolAction.Delete(DeleteKind.TO_LINE_START)
            ))
        ))
        val failure = runCatching { LegacySymbolDataCodec.encode(groups) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test fun emptyBackupIsValid() {
        assertTrue(SymbolGroupBundleCodec.decode(SymbolGroupBundleCodec.encode(emptyList())).isEmpty())
    }

    @Test fun storageWritesVersionedV2SchemaAndReadsItBack() {
        val groups = listOf(
            SymbolGroup("kotlin", "Kotlin", listOf(
                SymbolEntry(1L, "fun", SymbolAction.InsertText("fun "))
            ))
        )
        val encoded = SymbolGroupStorageCodec.encode(groups)
        assertTrue(encoded.contains("\"format\":\"zero-symbol-drawer\""))
        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.contains("\"shortAction\":"))
        assertEquals(groups, SymbolGroupStorageCodec.decode(encoded))
    }

    @Test fun storagePersistsActionsThatCannotBeRepresentedByV1NumericIds() {
        val groups = listOf(
            SymbolGroup("modern", "Modern", listOf(
                SymbolEntry(
                    id = 9L,
                    label = "wrap",
                    shortAction = SymbolAction.SurroundSelection("[", "]"),
                    longAction = SymbolAction.Composite(listOf(
                        SymbolAction.InsertText("before"),
                        SymbolAction.Extension("my.editor.action", "{\"mode\":1}")
                    )),
                    icon = "W"
                ),
                SymbolEntry(
                    id = 10L,
                    label = "toggle",
                    shortAction = SymbolAction.TransformSelection(SelectionTransform.TOGGLE_CASE)
                )
            ))
        )
        val encoded = SymbolGroupStorageCodec.encode(groups)
        assertEquals(groups, SymbolGroupStorageCodec.decode(encoded))
    }

    @Test fun storageStillReadsLegacyV1CompactSchema() {
        val legacyV1 = """[{"n":"Legacy","d":[{"a":0,"b":"x","c":"x"}]}]"""
        val restored = SymbolGroupStorageCodec.decode(legacyV1)
        assertEquals("Legacy", restored.single().title)
        assertEquals(SymbolAction.InsertText("x"), restored.single().symbols.single().shortAction)
    }

    @Test fun storageReadsPreviousUnversionedV2Array() {
        val legacy = """[{"id":"g","title":"Group","symbols":[{"id":1,"label":"x","shortAction":{"kind":"insertText","template":"x"}}]}]"""
        val decoded = SymbolGroupStorageCodec.decode(legacy)
        assertEquals("g", decoded.single().id)
        assertEquals(SymbolAction.InsertText("x"), decoded.single().symbols.single().shortAction)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnrelatedJsonAsLegacyGroups() {
        LegacySymbolDataCodec.decode("""[{"name":"not-a-v1-group","items":[]}]""")
    }


    @Test fun unknownLegacyActionIdsSurviveStrictV1ImportAsExtensionActions() {
        val json = """[{"n":"custom","d":[{"a":987,"b":"custom short","c":"short payload","d":988,"e":"long payload"}]}]"""
        val symbol = LegacySymbolDataCodec.decode(json).single().symbols.single()
        assertEquals(SymbolAction.Extension("legacy.action.987", "short payload"), symbol.shortAction)
        assertEquals(SymbolAction.Extension("legacy.action.988", "long payload"), symbol.longAction)
        assertEquals(987, symbol.legacyShortActionId)
        assertEquals(988, symbol.legacyLongActionId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLegacyGroupsWithWrongActionIdType() {
        LegacySymbolDataCodec.decode("""[{"n":"g","d":[{"a":2.5,"b":"x"}]}]""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownBackupVersion() {
        SymbolGroupBundleCodec.decode("""{"format":"zero-symbol-drawer","version":99,"groups":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateGroupIds() {
        val group = SymbolGroup("same", "A", emptyList())
        SymbolGroupBundleCodec.encode(listOf(group, group.copy(title = "B")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateSymbolIdsWithinGroup() {
        val first = SymbolEntry(1L, "a", SymbolAction.InsertText("a"))
        val second = SymbolEntry(1L, "b", SymbolAction.InsertText("b"))
        SymbolGroupBundleCodec.encode(listOf(SymbolGroup("g", "Group", listOf(first, second))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedActionPayloadsBeforePersistence() {
        val oversized = "x".repeat(SymbolGroupBundleCodec.MAX_ACTION_TEXT_CHARS + 1)
        val group = SymbolGroup(
            "g",
            "Group",
            listOf(SymbolEntry(1L, "large", SymbolAction.InsertText(oversized)))
        )
        SymbolGroupBundleCodec.encode(listOf(group))
    }


    @Test(expected = IllegalArgumentException::class)
    fun rejectsActionTreesBeyondTheAggregateNodeBudgetBeforePersistence() {
        val leaves = List(64) { SymbolAction.OpenManager }
        val branch = SymbolAction.Composite(leaves)
        val action = SymbolAction.Composite(List(5) { branch })
        val group = SymbolGroup("g", "Group", listOf(SymbolEntry(1L, "large tree", action)))
        SymbolGroupBundleCodec.encode(listOf(group))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCompositeActionsBeyondSupportedDepth() {
        var action: SymbolAction = SymbolAction.InsertText("x")
        repeat(10) { action = SymbolAction.Composite(listOf(action)) }
        val group = SymbolGroup("g", "Group", listOf(SymbolEntry(1L, "nested", action)))
        SymbolGroupBundleCodec.encode(listOf(group))
    }

    @Test fun legacyActionIdsAndPayloadsSurviveVersionedV2StorageRoundTrip() {
        val source = listOf(
            SymbolGroup(
                "legacy-actions",
                "Legacy actions",
                listOf(
                    SymbolEntry(
                        id = 1L,
                        label = "custom",
                        shortAction = LegacySymbolActionAdapter.convert(LegacySymbolAction(0, "val x = %DOLLAR%S%DOLLAR%T%DOLLAR%E")),
                        longAction = LegacySymbolActionAdapter.convert(LegacySymbolAction(36, "replacement")),
                        legacyShortActionId = 0,
                        legacyShortText = "val x = %DOLLAR%S%DOLLAR%T%DOLLAR%E",
                        legacyLongActionId = 36,
                        legacyLongText = "replacement"
                    ),
                    SymbolEntry(
                        id = 2L,
                        label = "duplicate",
                        shortAction = LegacySymbolActionAdapter.convert(LegacySymbolAction(33)),
                        legacyShortActionId = 33
                    )
                )
            )
        )
        val json = SymbolGroupStorageCodec.encode(source)
        assertTrue(json.contains("\"legacyShortActionId\":0"))
        assertTrue(json.contains("\"legacyShortText\":\"val x = %DOLLAR%S%DOLLAR%T%DOLLAR%E\""))
        assertTrue(json.contains("\"legacyLongActionId\":36"))
        assertTrue(json.contains("\"legacyLongText\":\"replacement\""))
        assertTrue(json.contains("\"legacyShortActionId\":33"))
        val restored = SymbolGroupStorageCodec.decode(json)
        assertEquals(source, restored)
    }

    @Test fun importsTheSuppliedV1BackupWithBracketSelectionActionId26() {
        val json = """[
          {"n":"backup","d":[
            {"a":11,"b":"注释","d":11},{"a":18,"b":"←","d":16},
            {"a":20,"b":"↑","d":23},{"a":19,"b":"→","d":17},
            {"a":0,"b":"\"","c":"\""},{"a":0,"b":"'","c":"'"},
            {"a":0,"b":".","c":"."},{"a":0,"b":",","c":","},
            {"a":0,"b":"/","c":"/"},{"a":0,"b":"//","c":"//"},
            {"a":21,"b":"↓","d":24},{"a":0,"b":":","c":":"},
            {"a":0,"b":";","c":";"},{"a":0,"b":"#","c":"#"},
            {"a":0,"b":"+","c":"+"},{"a":0,"b":"-","c":"-"},
            {"a":0,"b":"*","c":"*"},{"a":0,"b":"=","c":"="},
            {"a":0,"b":"|","c":"|"},{"a":0,"b":"_","c":"_"},
            {"a":0,"b":"(","c":"("},{"a":0,"b":")","c":")"},
            {"a":0,"b":"(\"","c":"(\""},{"a":0,"b":"\")","c":"\")"},
            {"a":0,"b":"{","c":"{"},{"a":0,"b":"}","c":"}"},
            {"a":0,"b":"()","c":"()","d":0,"e":"(%DOLLAR%T)"},
            {"a":0,"b":"[]","c":"[]","d":0,"e":"[%DOLLAR%T]"},
            {"a":0,"b":"{}","c":"{%DOLLAR%S%DOLLAR%T%DOLLAR%E}","d":0,"e":"{%DOLLAR%T}"},
            {"a":0,"b":"<","c":"<"},{"a":0,"b":">","c":">"},
            {"a":0,"b":"\\","c":"\\"},{"a":0,"b":"$","c":"$"},
            {"a":0,"b":"&","c":"&"},{"a":0,"b":"/*","c":"/**"},
            {"a":0,"b":"*/","c":"*/"}
          ]},
          {"n":"特殊","d":[
            {"a":22,"b":"…"},{"a":26,"b":"选中括号内容","d":25},
            {"a":0,"b":"return ","c":"return "},
            {"a":0,"b":"插入字符串","c":"const-string v0, \"\""},
            {"a":0,"b":"赋值1/0","c":"const v0,1","d":0,"e":"const v0,0"},
            {"a":0,"b":"const-wide","c":"const-wide v0,0x"},
            {"a":9,"b":"Tab"},{"a":4,"b":"清空行"},{"a":3,"b":"删除行"},
            {"a":29,"b":"剪切","d":2},{"a":28,"b":"复制","d":1},
            {"a":30,"b":"粘贴"},{"a":16,"b":"到行首"},{"a":17,"b":"到行尾"},
            {"a":23,"b":"到开头"},{"a":24,"b":"到结尾"},
            {"a":0,"b":"腾讯gradle镜像源","c":"mirrors.cloud.tencent.com/gradle"},
            {"a":32,"b":"格式化","d":33},{"a":7,"b":"A/a","d":8},{"a":22,"b":"…"}
          ]}
        ]""".replace("%DOLLAR%", "$")
        val groups = SymbolGroupBundleCodec.decode(json)
        assertEquals(listOf("backup", "特殊"), groups.map { it.title })
        assertEquals(2, groups.size)
        assertTrue(groups[0].symbols.size >= 30)
        assertEquals(20, groups[1].symbols.size)
        val bracket = groups[1].symbols.first { it.label == "选中括号内容" }
        assertEquals(26, bracket.legacyShortActionId)
        assertEquals(SymbolAction.Extension("legacy.action.26"), bracket.shortAction)
        assertEquals(25, bracket.legacyLongActionId)
        assertEquals(SymbolAction.EditorCommand("selection.selectAll"), bracket.longAction)
        val cut = groups[1].symbols.first { it.label == "剪切" }
        val copy = groups[1].symbols.first { it.label == "复制" }
        // V1 numeric IDs 1/2 had no executor dispatch branch. Preserve them as
        // unresolved legacy extensions instead of inventing clipboard behavior.
        assertEquals(SymbolAction.Extension("legacy.action.2"), cut.longAction)
        assertEquals(SymbolAction.Extension("legacy.action.1"), copy.longAction)
        assertEquals("const-string v0, \"\"", groups[1].symbols[3].legacyShortText)
    }

}
