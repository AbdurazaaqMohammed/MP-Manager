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
package com.zerostudio.idea.widget.drawer.action;

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.ClipboardOperation;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.CursorTarget;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SelectionTransform;
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction;

/** Compile-only fixture for the Java-facing, editor-neutral action API. */
final class JavaActionHostCompileFixture {
    static SymbolActionResult execute(SymbolAction action) {
        SymbolActionHost host = new SymbolActionHost() {
            @Override public boolean insertText(String template) { return true; }
            @Override public boolean replaceSelection(String text) { return true; }
            @Override public boolean surroundSelection(String prefix, String suffix) { return true; }
            @Override public boolean moveCursor(CursorTarget target) { return true; }
            @Override public boolean delete(DeleteKind kind) { return true; }
            @Override public boolean transformSelection(SelectionTransform transform) { return true; }
            @Override public boolean executeEditorCommand(String commandId, String payload) { return false; }
            @Override public boolean performClipboard(ClipboardOperation operation) { return true; }
            @Override public boolean openManager() { return true; }
            @Override public boolean executeExtension(String extensionId, String payload) { return false; }
        };
        SymbolActionExecutor executor = new SymbolActionExecutor();
        return executor.execute(action, host, () -> false);
    }
}
