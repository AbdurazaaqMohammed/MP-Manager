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
package com.zerostudio.idea.widget.drawer.extension

import android.content.Context
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings

/** Immutable theme token snapshot. Color values use Android ARGB encoding. */
data class SymbolDrawerTheme(
    val primary: Int,
    val onPrimary: Int,
    val surface: Int,
    val onSurface: Int,
    val secondaryText: Int,
    val outline: Int,
    val drawerBackground: Int = surface,
    val selectedCellBackground: Int = primary
)

/**
 * Java/Kotlin JVM extension point for supplying a drawer palette.
 *
 * The extension is registered with the owning drawer's extension registry.
 * Returning a new snapshot causes attached drawer surfaces to receive the
 * updated palette; null is represented by clearing the selected theme ID.
 */
interface SymbolDrawerThemeExtension {
    val id: String
    val title: String
    val priority: Int get() = 0
    fun resolve(context: Context, settings: SymbolDrawerSettings): SymbolDrawerTheme
}
