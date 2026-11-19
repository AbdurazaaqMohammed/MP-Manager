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
import com.zerostudio.idea.widget.editor.symbolinput.v2.R

/** Action IDs and ordering copied from V1 res/values/arrays.xml. */
object LegacySymbolActionCatalog {
    val values = intArrayOf(
        0, 3, 4, 7, 8, 9, 11, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 32, 33, 34, 35, 36, 37, 38, 39, 52, 53, 54, 55, 56,
        // MP Manager custom actions executed by the host app (SoraSymbolActionHost).
        // 100 opens the editor search panel, 101 runs find/replace with the
        // {mpManager:true,text,secondText,options} typed envelope payload and
        // 102 replaces the current line from the clipboard.
        100, 101, 102
    )
    // Legacy F1-F12 IDs remain decodable for existing data, but are not offered
    // as new choices: V1 only inserted literal "<F1>" text rather than dispatching
    // a function key. Historical clipboard IDs 1/2 are also import-only.
    val importableValues: Set<Int> = values.toSet() + setOf(1, 2) + (40..51)

    /**
     * V1 text payloads are used by insertion, replace-current-line and the
     * MP Manager find/replace actions. For 101 the text payload is the find
     * text (V1 field c/e); the replacement (f/g) and options (h/i) travel
     * inside the typed extension envelope.
     */
    fun hasTextPayload(actionId: Int): Boolean = actionId == 0 || actionId == 36 || actionId == 101

    val labels = listOf(
        "Insert text",
        "Delete line",
        "Clear line",
        "Convert to uppercase",
        "Convert to lowercase",
        "Indent (Tab)",
        "Toggle comment",
        "Go to line start",
        "Go to line end",
        "Move cursor left",
        "Move cursor right",
        "Move cursor up",
        "Move cursor down",
        "Open symbol toolbar manager",
        "Go to document start",
        "Go to document end",
        "Select all",
        "Copy",
        "Cut",
        "Paste",
        "Format code",
        "Copy line",
        "Cut line",
        "Duplicate line",
        "Replace line",
        "Enter key",
        "Tab key",
        "Shift + Tab",
        "Copy",
        "Cut",
        "Paste",
        "Undo",
        "Redo",
        "Search (editor)",
        "Find and replace",
        "Replace line from clipboard"
    )

    fun labels(context: Context): List<String> = listOf(
        context.getString(R.string.legacy_action_label_01),
        context.getString(R.string.legacy_action_label_02),
        context.getString(R.string.legacy_action_label_03),
        context.getString(R.string.legacy_action_label_04),
        context.getString(R.string.legacy_action_label_05),
        context.getString(R.string.legacy_action_label_06),
        context.getString(R.string.legacy_action_label_07),
        context.getString(R.string.legacy_action_label_08),
        context.getString(R.string.legacy_action_label_09),
        context.getString(R.string.legacy_action_label_10),
        context.getString(R.string.legacy_action_label_11),
        context.getString(R.string.legacy_action_label_12),
        context.getString(R.string.legacy_action_label_13),
        context.getString(R.string.legacy_action_label_14),
        context.getString(R.string.legacy_action_label_15),
        context.getString(R.string.legacy_action_label_16),
        context.getString(R.string.legacy_action_label_17),
        context.getString(R.string.legacy_action_label_18),
        context.getString(R.string.legacy_action_label_19),
        context.getString(R.string.legacy_action_label_20),
        context.getString(R.string.legacy_action_label_21),
        context.getString(R.string.legacy_action_label_22),
        context.getString(R.string.legacy_action_label_23),
        context.getString(R.string.legacy_action_label_24),
        context.getString(R.string.legacy_action_label_25),
        context.getString(R.string.legacy_action_label_26),
        context.getString(R.string.legacy_action_label_27),
        context.getString(R.string.legacy_action_label_28),
        context.getString(R.string.legacy_action_label_29),
        context.getString(R.string.legacy_action_label_30),
        context.getString(R.string.legacy_action_label_31),
        context.getString(R.string.legacy_action_label_32),
        context.getString(R.string.legacy_action_label_33),
        context.getString(R.string.legacy_action_label_34),
        context.getString(R.string.legacy_action_label_35),
        context.getString(R.string.legacy_action_label_36)
    )
    init { check(values.size == labels.size) }
}
