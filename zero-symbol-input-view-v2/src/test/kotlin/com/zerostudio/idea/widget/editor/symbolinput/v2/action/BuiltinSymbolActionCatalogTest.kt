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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSymbolActionCatalogTest {
    @Test fun everyGenericActionIsAvailableAndEveryHostActionHasAHostContract() {
        val genericIds = GenericSymbolTextActions.supportedCommandIds()
        val catalogIds = BuiltinSymbolActionCatalog.options.map { it.commandId }
        val hostCommandIds = setOf("undo", "redo", "indent", "unindent", "format")
        val registry = GenericSymbolTextActions.createRegistry()

        assertEquals("Built-in action IDs must be unique", catalogIds.size, catalogIds.toSet().size)
        assertEquals(
            "Every catalog entry must resolve to a generic handler or an explicit host command",
            hostCommandIds,
            catalogIds.toSet() - genericIds
        )
        genericIds.forEach { id ->
            assertNotNull("Generic action '$id' must have a reverse-lookup handler",
                registry.get(SymbolActionId("builtin", id)))
        }
        assertEquals(61, genericIds.size)
        assertEquals(66, catalogIds.size)
    }
}
