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

import com.zerostudio.idea.widget.editor.symbolinput.v2.manager.LegacyActionPickerResolver
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the MP Manager find/replace (V1 action 101) typed
 * envelope used by the symbol manager form. The helper, codec and catalog are
 * all Android-free, so the full manager persistence contract is covered here
 * without an instrumented device.
 */
class MpManagerFindReplaceEnvelopeTest {

    @Test fun encodesTheTypedEnvelopeInTheCodecWireShape() {
        assertEquals(
            """{"mpManager":true,"text":"find","secondText":"repl","options":"rc"}""",
            MpManagerFindReplaceEnvelope.encodePayload("find", "repl", "rc")
        )
    }

    @Test fun encodesMissingTextsAsJsonNull() {
        val payload = MpManagerFindReplaceEnvelope.encodePayload(null, "", "rc")
        val json = com.google.gson.JsonParser.parseString(payload).asJsonObject
        assertTrue(json.get("mpManager").asBoolean)
        assertTrue(json.get("text").isJsonNull)
        assertTrue(json.get("secondText").isJsonNull)
        assertEquals("rc", json.get("options").asString)
    }

    @Test fun decodesAValidEnvelope() {
        val payload = MpManagerFindReplaceEnvelope.encodePayload("needle(.*)", "replace", "rca")
        val decoded = MpManagerFindReplaceEnvelope.decodePayload(payload)
        assertEquals(MpManagerFindReplaceEnvelope.Payload("needle(.*)", "replace", "rca"), decoded)
    }

    @Test fun decodesTheCodecImportEnvelope() {
        // LegacySymbolDataCodec builds exactly this envelope when importing V1
        // action 101 items, including JSON nulls for absent fields.
        val decoded = MpManagerFindReplaceEnvelope.decodePayload(
            """{"mpManager":true,"text":null,"secondText":null,"options":null}"""
        )
        assertEquals(MpManagerFindReplaceEnvelope.Payload(null, null, null), decoded)
    }

    @Test fun rejectsPayloadsThatAreNotMpManagerEnvelopes() {
        assertNull(MpManagerFindReplaceEnvelope.decodePayload(null))
        assertNull(MpManagerFindReplaceEnvelope.decodePayload(""))
        assertNull(MpManagerFindReplaceEnvelope.decodePayload("   "))
        assertNull(MpManagerFindReplaceEnvelope.decodePayload("plain find text"))
        assertNull(MpManagerFindReplaceEnvelope.decodePayload("[1,2]"))
        assertNull(MpManagerFindReplaceEnvelope.decodePayload("""{"text":"x"}"""))
        assertNull(MpManagerFindReplaceEnvelope.decodePayload("""{"mpManager":false,"text":"x"}"""))
        assertNull(MpManagerFindReplaceEnvelope.decodePayload("""{"mpManager":"yes"}"""))
    }

    @Test fun ignoresNonStringEnvelopeValues() {
        assertNull(
            MpManagerFindReplaceEnvelope.decodePayload(
                """{"mpManager":true,"text":42,"secondText":[],"options":{}}"""
            )?.findText
        )
        val decoded = MpManagerFindReplaceEnvelope.decodePayload(
            """{"mpManager":true,"text":42,"secondText":[],"options":{}}"""
        )
        assertEquals(MpManagerFindReplaceEnvelope.Payload(null, null, null), decoded)
    }

    @Test fun buildsOptionsFromIndependentFlags() {
        assertEquals("rc", MpManagerFindReplaceEnvelope.encodeOptions(true, true, false))
        assertEquals("rca", MpManagerFindReplaceEnvelope.encodeOptions(true, true, true))
        assertEquals("a", MpManagerFindReplaceEnvelope.encodeOptions(false, false, true))
        // An explicit empty options string means "no flags" on the V1 wire;
        // only an absent field defaults to rc at execution time.
        assertEquals("", MpManagerFindReplaceEnvelope.encodeOptions(false, false, false))
        assertTrue(MpManagerFindReplaceEnvelope.hasFlag("rc", MpManagerFindReplaceEnvelope.FLAG_REGEX))
        assertFalse(MpManagerFindReplaceEnvelope.hasFlag("rc", MpManagerFindReplaceEnvelope.FLAG_REPLACE_ALL))
        assertFalse(MpManagerFindReplaceEnvelope.hasFlag(null, MpManagerFindReplaceEnvelope.FLAG_REGEX))
    }

    @Test fun nullOptionsDefaultToTheHostBehaviour() {
        assertEquals("rc", MpManagerFindReplaceEnvelope.optionsOrDefault(null))
        assertEquals("", MpManagerFindReplaceEnvelope.optionsOrDefault(""))
        assertEquals("rca", MpManagerFindReplaceEnvelope.optionsOrDefault("rca"))
    }

    @Test fun recognizesOnlyTheCodecExtensionId() {
        assertTrue(
            MpManagerFindReplaceEnvelope.isFindReplaceExtension(
                SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, null)
            )
        )
        assertFalse(
            MpManagerFindReplaceEnvelope.isFindReplaceExtension(
                SymbolAction.Extension("legacy.action.100", null)
            )
        )
        assertFalse(
            MpManagerFindReplaceEnvelope.isFindReplaceExtension(
                SymbolAction.Extension("app.custom.action", null)
            )
        )
        assertFalse(MpManagerFindReplaceEnvelope.isFindReplaceExtension(SymbolAction.InsertText("x")))
        assertFalse(MpManagerFindReplaceEnvelope.isFindReplaceExtension(null))
    }

    @Test fun decodesTypedActionsAndFallsBackToRawPayloads() {
        val envelope = MpManagerFindReplaceEnvelope.encodePayload("find", "repl", "rc")
        assertEquals(
            MpManagerFindReplaceEnvelope.Payload("find", "repl", "rc"),
            MpManagerFindReplaceEnvelope.decodeAction(
                SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, envelope)
            )
        )
        // An extension without a valid envelope is surfaced as find text only,
        // matching how the host executes such payloads.
        assertEquals(
            MpManagerFindReplaceEnvelope.Payload("raw find", null, null),
            MpManagerFindReplaceEnvelope.decodeAction(
                SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, "raw find")
            )
        )
        assertNull(MpManagerFindReplaceEnvelope.decodeAction(SymbolAction.InsertText("x")))
    }

    @Test fun genericLegacyResolverCannotResolveTheTypedEnvelope() {
        val envelope = MpManagerFindReplaceEnvelope.encodePayload("find", "repl", "rc")
        val action = SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, envelope)
        // The V1 metadata payload (find text) never equals the envelope JSON,
        // so the generic comparison must fail and the manager resolves the
        // picker row explicitly through the catalog and extension ID.
        assertNull(LegacyActionPickerResolver.legacyActionIndex(action, 101, "find"))
        assertTrue(MpManagerFindReplaceEnvelope.isFindReplaceExtension(action))
        val index = LegacySymbolActionCatalog.values
            .indexOf(MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID)
        assertTrue("101 must be offered by the catalog", index >= 0)
        assertEquals("Find and replace", LegacySymbolActionCatalog.labels[index])
    }

    @Test fun managerEnvelopeExportsBackToV1FindReplaceFields() {
        val payload = MpManagerFindReplaceEnvelope.encodePayload("find", "repl", "rc")
        val groups = listOf(
            SymbolGroup(
                id = "manager",
                title = "G",
                symbols = listOf(
                    SymbolEntry(
                        id = 1L,
                        label = "FR",
                        shortAction = SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, payload),
                        longAction = null,
                        legacyShortActionId = MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID,
                        legacyShortText = "find"
                    )
                )
            )
        )
        assertEquals(
            """[{"n":"G","d":[{"a":101,"b":"FR","c":"find","f":"repl","h":"rc"}]}]""",
            LegacySymbolDataCodec.encode(groups)
        )
    }

    @Test fun managerEnvelopeReimportsIntoAnIdenticalTypedAction() {
        val payload = MpManagerFindReplaceEnvelope.encodePayload("find(.*)", "repl", "rca")
        val imported = LegacySymbolDataCodec.decode(
            """[{"n":"G","d":[{"a":101,"b":"FR","c":"find(.*)","f":"repl","h":"rca","d":101,"e":"lfind","g":"lrepl","i":"c"}]}]"""
        )
        val symbol = imported.single().symbols.single()
        // Short and long 101 decode to envelope extensions that the manager
        // can re-create character-for-character through the helper.
        assertEquals(
            SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, payload),
            symbol.shortAction
        )
        assertEquals(
            SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID,
                MpManagerFindReplaceEnvelope.encodePayload("lfind", "lrepl", "c")),
            symbol.longAction
        )
        assertEquals(MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID, symbol.legacyShortActionId)
        assertEquals("find(.*)", symbol.legacyShortText)
        assertEquals(MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID, symbol.legacyLongActionId)
        assertEquals("lfind", symbol.legacyLongText)
        assertEquals(imported, LegacySymbolDataCodec.decode(LegacySymbolDataCodec.encode(imported)))
    }

    @Test fun storageRoundTripKeepsEnvelopeAndLegacyMetadata() {
        val payload = MpManagerFindReplaceEnvelope.encodePayload(null, "repl", "")
        val groups = listOf(
            SymbolGroup(
                id = "manager",
                title = "G",
                symbols = listOf(
                    SymbolEntry(
                        id = 1L,
                        label = "FR",
                        shortAction = SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, payload),
                        longAction = SymbolAction.Extension(MpManagerFindReplaceEnvelope.EXTENSION_ID, payload),
                        legacyShortActionId = MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID,
                        legacyLongActionId = MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID
                    )
                )
            )
        )
        val stored = SymbolGroupStorageCodec.decode(SymbolGroupStorageCodec.encode(groups))
        assertEquals(groups, stored)
        val exported = LegacySymbolDataCodec.encode(stored)
        // Null find text stays absent (no c field), the replacement and the
        // explicit no-flag options string are still exported for both slots.
        assertEquals("""[{"n":"G","d":[{"a":101,"b":"FR","f":"repl","h":"","d":101,"g":"repl","i":""}]}]""", exported)
    }
}
