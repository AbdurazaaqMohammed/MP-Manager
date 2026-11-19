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

package com.zerostudio.idea.widget.editor.symbolinput.v2.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.google.gson.JsonParser
import org.junit.Test

class SymbolDrawerSettingsTest {
    @Test fun settingsCodecRoundTripsAllValues() {
        val value = SymbolDrawerSettings(
            columns = 6,
            themeMode = ThemeMode.DARK,
            showActionHints = true,
            rememberExpandedState = false,
            showFullSymbolLabels = true,
            symbolLabelTextSizeSp = 24,
            reduceMotion = true,
            pageCacheLimit = 5
        )
        assertEquals(value, SymbolDrawerSettingsCodec.decode(SymbolDrawerSettingsCodec.encode(value)))
    }

    @Test fun missingFieldsUseCurrentDefaultsForStoredSettingsMigration() {
        val value = SymbolDrawerSettingsCodec.decode("""{"schemaVersion":1,"columns":7}""")
        assertEquals(7, value.columns)
        assertEquals(SymbolDrawerSettings().cellHeightDp, value.cellHeightDp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingFieldsInExternalSettingsBackup() {
        SymbolDrawerSettingsCodec.decodeStrict("""{"schemaVersion":1,"columns":7}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSettingsFields() {
        val json = SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings()).dropLast(1) + ""","unknown":true}"""
        SymbolDrawerSettingsCodec.decodeStrict(json)
    }

    @Test fun expandedLayoutDimensionsRoundTripAcrossNewBounds() {
        val value = SymbolDrawerSettings(columns = 30, collapsedRows = 16, cellHeightDp = 6, maxExpandedHeightDp = 80)
        assertEquals(value, SymbolDrawerSettingsCodec.decode(SymbolDrawerSettingsCodec.encode(value)))
    }

    @Test fun negativeCellSpacingIsSupportedAndPersisted() {
        val value = SymbolDrawerSettings(cellHorizontalSpacingDp = -24, cellVerticalSpacingDp = 48)
        val restored = SymbolDrawerSettingsCodec.decode(SymbolDrawerSettingsCodec.encode(value))
        assertEquals(-24, restored.cellHorizontalSpacingDp)
        assertEquals(48, restored.cellVerticalSpacingDp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCellSpacingOutsideExpandedFineAdjustmentLimit() {
        SymbolDrawerSettings(cellHorizontalSpacingDp = -25)
    }

    @Test fun everySettingsLayoutBoundaryConvertsToDrawerConfig() {
        val compact = SymbolDrawerSettings(
            cellHeightDp = 6,
            cellHorizontalSpacingDp = -24,
            cellVerticalSpacingDp = 48,
            maxExpandedHeightDp = 80
        )
        val config = compact.toDrawerConfig()
        assertEquals(6, config.cellHeightDp)
        assertEquals(-24, config.cellHorizontalSpacingDp)
        assertEquals(48, config.cellVerticalSpacingDp)
        assertEquals(80, config.maxExpandedHeightDp)
    }

    @Test fun drawerConfigHonorsReducedMotion() {
        val config = SymbolDrawerSettings(animationDurationMs = 350, reduceMotion = true).toDrawerConfig()
        assertEquals(0L, config.animationDurationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeColumns() {
        SymbolDrawerSettings(columns = 0)
    }

    @Test fun strictV1BackupMigratesNewFieldsToCurrentDefaults() {
        val root = JsonParser.parseString(SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings())).asJsonObject
        root.addProperty("schemaVersion", 1)
        root.remove("largeScreenLayoutMode")
        root.remove("sideDockEdge")
        root.remove("rememberExpandedState")
        root.remove("showFullSymbolLabels")
        root.remove("symbolLabelTextSizeSp")

        val restored = SymbolDrawerSettingsCodec.decodeStrict(root.toString())

        assertEquals(SymbolDrawerSettings.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(LargeScreenLayoutMode.AUTO, restored.largeScreenLayoutMode)
        assertEquals(SideDockEdge.RIGHT, restored.sideDockEdge)
    }

    @Test fun strictV2BackupRequiresTheExactCurrentFieldSet() {
        val root = JsonParser.parseString(SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings())).asJsonObject
        root.remove("columns")
        assertFails { SymbolDrawerSettingsCodec.decodeStrict(root.toString()) }

        root.addProperty("columns", SymbolDrawerSettings().columns)
        root.addProperty("futureSetting", true)
        assertFails { SymbolDrawerSettingsCodec.decodeStrict(root.toString()) }
    }

    @Test fun settingsDecoderRejectsWrongRootAndMalformedJson() {
        assertFails { SymbolDrawerSettingsCodec.decode("[]") }
        assertFails { SymbolDrawerSettingsCodec.decode("not-json") }
        assertFails { SymbolDrawerSettingsCodec.decode("null") }
    }

    @Test fun settingsDecoderRejectsFractionalAndWronglyTypedValues() {
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":2.5}""") }
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":2,"columns":3.5}""") }
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":2,"reduceMotion":"true"}""") }
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":2,"themeMode":1}""") }
    }

    @Test fun settingsDecoderRejectsUnsupportedVersionsAndUnknownEnums() {
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":0}""") }
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":${SymbolDrawerSettings.CURRENT_SCHEMA_VERSION + 1}}""") }
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":2,"themeMode":"AUTO"}""") }
        assertFails { SymbolDrawerSettingsCodec.decode("""{"schemaVersion":2,"sideDockEdge":"TOP"}""") }
    }

    @Test fun schemaVersionIsAlwaysUpgradedAfterPermissiveV1Decode() {
        val restored = SymbolDrawerSettingsCodec.decode("""{"schemaVersion":1,"columns":8}""")
        assertEquals(SymbolDrawerSettings.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(8, restored.columns)
    }

    @Test fun strictV2BackupMigratesNewLabelAndExpansionOptionsToDefaults() {
        val root = JsonParser.parseString(SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings())).asJsonObject
        root.addProperty("schemaVersion", 2)
        root.remove("rememberExpandedState")
        root.remove("showFullSymbolLabels")
        root.remove("symbolLabelTextSizeSp")
        val restored = SymbolDrawerSettingsCodec.decodeStrict(root.toString())
        assertTrue(restored.rememberExpandedState)
        assertEquals(false, restored.showFullSymbolLabels)
        assertEquals(18, restored.symbolLabelTextSizeSp)
    }

    @Test fun reducedMotionOnlyOverridesRuntimeAnimationDuration() {
        val normal = SymbolDrawerSettings(animationDurationMs = 425, reduceMotion = false).toDrawerConfig()
        val reduced = SymbolDrawerSettings(animationDurationMs = 425, reduceMotion = true).toDrawerConfig()
        assertEquals(425L, normal.animationDurationMs)
        assertEquals(0L, reduced.animationDurationMs)
        assertEquals(425, SymbolDrawerSettings(animationDurationMs = 425, reduceMotion = true).animationDurationMs)
    }

    @Test fun everyEnumSettingRoundTripsIndependently() {
        ThemeMode.values().forEach { mode ->
            assertEquals(mode, SymbolDrawerSettingsCodec.decode(
                SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(themeMode = mode))
            ).themeMode)
        }
        GroupIndicatorStyle.values().forEach { style ->
            assertEquals(style, SymbolDrawerSettingsCodec.decode(
                SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(indicatorStyle = style))
            ).indicatorStyle)
        }
        HapticMode.values().forEach { mode ->
            assertEquals(mode, SymbolDrawerSettingsCodec.decode(
                SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(hapticMode = mode))
            ).hapticMode)
        }
        LargeScreenLayoutMode.values().forEach { mode ->
            assertEquals(mode, SymbolDrawerSettingsCodec.decode(
                SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(largeScreenLayoutMode = mode))
            ).largeScreenLayoutMode)
        }
        SideDockEdge.values().forEach { edge ->
            assertEquals(edge, SymbolDrawerSettingsCodec.decode(
                SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(sideDockEdge = edge))
            ).sideDockEdge)
        }
    }

    @Test fun groupIndicatorCatalogContainsManyIndependentModesAndKeepsLegacyTextValue() {
        assertTrue(GroupIndicatorStyle.values().size >= 40)
        assertEquals(GroupIndicatorStyle.TEXT, SymbolDrawerSettingsCodec.decode(
            SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(indicatorStyle = GroupIndicatorStyle.TEXT))
        ).indicatorStyle)
        assertEquals(GroupIndicatorStyle.TEXT_TABS, SymbolDrawerSettingsCodec.decode(
            SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(indicatorStyle = GroupIndicatorStyle.TEXT_TABS))
        ).indicatorStyle)
        assertEquals(GroupIndicatorStyle.GOOEY_METABALL, SymbolDrawerSettingsCodec.decode(
            SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(indicatorStyle = GroupIndicatorStyle.GOOEY_METABALL))
        ).indicatorStyle)
        assertEquals(GroupIndicatorStyle.RADIAL_RING, SymbolDrawerSettingsCodec.decode(
            SymbolDrawerSettingsCodec.encode(SymbolDrawerSettings(indicatorStyle = GroupIndicatorStyle.RADIAL_RING))
        ).indicatorStyle)
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected invalid settings to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation boundary.
        }
    }
}
