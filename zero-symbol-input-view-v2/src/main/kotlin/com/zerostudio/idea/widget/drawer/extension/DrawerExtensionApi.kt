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

typealias SymbolDrawerExtensionRegistry = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerExtensionRegistry
typealias SymbolDrawerExtensionSnapshot = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerExtensionSnapshot
typealias SymbolDrawerActionExtension = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerActionExtension
typealias SymbolGroupIndicatorExtension = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolGroupIndicatorExtension
typealias SymbolGroupIndicatorState = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolGroupIndicatorState
typealias SymbolGroupIndicatorComponent = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolGroupIndicatorComponent
typealias SymbolDrawerSettingsTabExtension = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerSettingsTabExtension
typealias SymbolDrawerSettingExtension = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerSettingExtension
typealias SymbolDrawerSettingsScope = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerSettingsScope
typealias SymbolDrawerItemViewExtension = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerItemViewExtension
typealias SymbolDrawerItemViewState = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerItemViewState
typealias SymbolDrawerLegacyGroupProvider = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerLegacyGroupProvider
typealias SymbolDrawerLifecycleExtension = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerLifecycleExtension
typealias LegacySymbolGroup = com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup
typealias LegacySymbolItem = com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem
typealias SymbolDrawerSettings = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings
typealias SymbolDrawerSettingsRepository = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettingsRepository
typealias SymbolActionHost = com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionHost
typealias SymbolActionHandler<H> = com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionHandler<H>

/**
 * Stable IDs for built-in features whose original UI consumers are wired to
 * the extension registry. These constants are part of the public API contract.
 */
object SymbolDrawerBuiltInFeatures {
    const val SEARCH = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerBuiltInFeatures.SEARCH
    const val FAVORITES = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerBuiltInFeatures.FAVORITES
    const val RECENT = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerBuiltInFeatures.RECENT
    const val SETTINGS = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerBuiltInFeatures.SETTINGS
    const val MANAGER = com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerBuiltInFeatures.MANAGER
}
