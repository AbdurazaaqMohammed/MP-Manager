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

import android.content.Context
import com.zerostudio.idea.widget.editor.symbolinput.v2.R

/** A user-facing, editor-neutral action that can be persisted as EditorCommand. */
data class BuiltinSymbolActionOption(
    val commandId: String,
    val label: String,
    val payloadHint: String? = null
) {
    val hasPayload: Boolean get() = payloadHint != null
}

/** Stable V2 action palette; V1 numeric entries remain separately available. */
object BuiltinSymbolActionCatalog {
    /** English fallback retained for callers that use the original context-free API. */
    val options: List<BuiltinSymbolActionOption> = listOf(
        BuiltinSymbolActionOption("line.duplicate", "Line: Duplicate current line"),
        BuiltinSymbolActionOption("line.delete", "Line: Delete current line"),
        BuiltinSymbolActionOption("line.clear", "Line: Clear current line"),
        BuiltinSymbolActionOption("line.replace", "Line: Replace current line", "Enter the replacement line text"),
        BuiltinSymbolActionOption("line.insertAbove", "Line: Insert blank line above"),
        BuiltinSymbolActionOption("line.insertBelow", "Line: Insert blank line below"),
        BuiltinSymbolActionOption("line.joinNext", "Line: Join with next line"),
        BuiltinSymbolActionOption("line.moveUp", "Line: Move up"),
        BuiltinSymbolActionOption("line.moveDown", "Line: Move down"),
        BuiltinSymbolActionOption("line.select", "Line: Select current line"),
        BuiltinSymbolActionOption("line.uppercase", "Line: Convert to uppercase"),
        BuiltinSymbolActionOption("line.lowercase", "Line: Convert to lowercase"),
        BuiltinSymbolActionOption("line.toggleCase", "Line: Toggle case"),
        BuiltinSymbolActionOption("line.trimTrailingWhitespace", "Line: Remove trailing whitespace"),
        BuiltinSymbolActionOption("line.trimLeadingWhitespace", "Line: Remove leading whitespace"),
        BuiltinSymbolActionOption("line.duplicateAbove", "Line: Duplicate current line above"),
        BuiltinSymbolActionOption("line.toggleComment", "Line: Toggle line comment", "Comment marker; defaults to //"),
        BuiltinSymbolActionOption("editor.toggleLineComment", "Editor: Toggle line comment", "Comment marker; defaults to //"),
        BuiltinSymbolActionOption("line.indent", "Line: Indent", "Indent text; defaults to Tab"),
        BuiltinSymbolActionOption("line.unindent", "Line: Unindent"),
        BuiltinSymbolActionOption("selection.delete", "Selection: Delete"),
        BuiltinSymbolActionOption("selection.duplicate", "Selection: Duplicate after"),
        BuiltinSymbolActionOption("selection.uppercase", "Selection: Convert to uppercase"),
        BuiltinSymbolActionOption("selection.lowercase", "Selection: Convert to lowercase"),
        BuiltinSymbolActionOption("selection.toggleCase", "Selection: Toggle case"),
        BuiltinSymbolActionOption("selection.wrapParentheses", "Selection: Wrap in parentheses"),
        BuiltinSymbolActionOption("selection.wrapBrackets", "Selection: Wrap in square brackets"),
        BuiltinSymbolActionOption("selection.wrapBraces", "Selection: Wrap in braces"),
        BuiltinSymbolActionOption("selection.wrapQuotes", "Selection: Wrap in double quotes"),
        BuiltinSymbolActionOption("selection.wrapSingleQuotes", "Selection: Wrap in single quotes"),
        BuiltinSymbolActionOption("selection.wrapBackticks", "Selection: Wrap in backticks"),
        BuiltinSymbolActionOption("selection.wrapAngleBrackets", "Selection: Wrap in angle brackets"),
        BuiltinSymbolActionOption("selection.wrapAsterisks", "Selection: Wrap in asterisks"),
        BuiltinSymbolActionOption("selection.wrapUnderscores", "Selection: Wrap in underscores"),
        BuiltinSymbolActionOption("selection.wrapHtmlTag", "Selection: Wrap in HTML tag", "Tag name, for example span"),
        BuiltinSymbolActionOption("selection.indentLines", "Selection: Indent multiple lines", "Indent text; defaults to Tab"),
        BuiltinSymbolActionOption("selection.unindentLines", "Selection: Unindent multiple lines"),
        BuiltinSymbolActionOption("selection.reverseLines", "Selection: Reverse line order"),
        BuiltinSymbolActionOption("selection.sortLines", "Selection: Sort lines alphabetically"),
        BuiltinSymbolActionOption("selection.comment", "Selection: Toggle comments", "Comment marker; defaults to //"),
        BuiltinSymbolActionOption("cursor.wordLeft", "Cursor: Move to previous word"),
        BuiltinSymbolActionOption("cursor.wordRight", "Cursor: Move to next word"),
        BuiltinSymbolActionOption("cursor.deleteWordBackward", "Cursor: Delete previous word"),
        BuiltinSymbolActionOption("cursor.deleteWordForward", "Cursor: Delete next word"),
        BuiltinSymbolActionOption("cursor.selectWord", "Cursor: Select current word"),
        BuiltinSymbolActionOption("cursor.selectLine", "Cursor: Select current line"),
        BuiltinSymbolActionOption("cursor.lineStart", "Cursor: Move to line start"),
        BuiltinSymbolActionOption("cursor.lineEnd", "Cursor: Move to line end"),
        BuiltinSymbolActionOption("cursor.selectToLineStart", "Cursor: Select to line start"),
        BuiltinSymbolActionOption("cursor.selectToLineEnd", "Cursor: Select to line end"),
        BuiltinSymbolActionOption("cursor.selectToDocumentStart", "Cursor: Select to document start"),
        BuiltinSymbolActionOption("cursor.selectToDocumentEnd", "Cursor: Select to document end"),
        BuiltinSymbolActionOption("cursor.documentStart", "Cursor: Move to document start"),
        BuiltinSymbolActionOption("cursor.documentEnd", "Cursor: Move to document end"),
        BuiltinSymbolActionOption("document.selectAll", "Document: Select all"),
        BuiltinSymbolActionOption("document.trimTrailingWhitespace", "Document: Remove trailing whitespace"),
        BuiltinSymbolActionOption("document.trimLeadingWhitespace", "Document: Remove leading whitespace"),
        BuiltinSymbolActionOption("document.ensureFinalNewline", "Document: Ensure final newline", "Newline sequence; defaults to \\\\n"),
        BuiltinSymbolActionOption("document.removeBlankLines", "Document: Remove blank lines"),
        BuiltinSymbolActionOption("document.toggleComment", "Document: Toggle comments", "Comment marker; defaults to //"),
        BuiltinSymbolActionOption("undo", "Editor: Undo"),
        BuiltinSymbolActionOption("redo", "Editor: Redo"),
        BuiltinSymbolActionOption("indent", "Editor: Indent selection"),
        BuiltinSymbolActionOption("unindent", "Editor: Unindent selection"),
        BuiltinSymbolActionOption("format", "Editor: Format code"),
        BuiltinSymbolActionOption("insert_payload", "Editor: Insert payload text", "Text to insert")
    )

    fun options(context: Context): List<BuiltinSymbolActionOption> = listOf(
        BuiltinSymbolActionOption("line.duplicate", context.getString(R.string.builtin_action_line_duplicate_label)),
        BuiltinSymbolActionOption("line.delete", context.getString(R.string.builtin_action_line_delete_label)),
        BuiltinSymbolActionOption("line.clear", context.getString(R.string.builtin_action_line_clear_label)),
        BuiltinSymbolActionOption("line.replace", context.getString(R.string.builtin_action_line_replace_label), context.getString(R.string.builtin_action_line_replace_hint)),
        BuiltinSymbolActionOption("line.insertAbove", context.getString(R.string.builtin_action_line_insert_above_label)),
        BuiltinSymbolActionOption("line.insertBelow", context.getString(R.string.builtin_action_line_insert_below_label)),
        BuiltinSymbolActionOption("line.joinNext", context.getString(R.string.builtin_action_line_join_next_label)),
        BuiltinSymbolActionOption("line.moveUp", context.getString(R.string.builtin_action_line_move_up_label)),
        BuiltinSymbolActionOption("line.moveDown", context.getString(R.string.builtin_action_line_move_down_label)),
        BuiltinSymbolActionOption("line.select", context.getString(R.string.builtin_action_line_select_label)),
        BuiltinSymbolActionOption("line.uppercase", context.getString(R.string.builtin_action_line_uppercase_label)),
        BuiltinSymbolActionOption("line.lowercase", context.getString(R.string.builtin_action_line_lowercase_label)),
        BuiltinSymbolActionOption("line.toggleCase", context.getString(R.string.builtin_action_line_toggle_case_label)),
        BuiltinSymbolActionOption("line.trimTrailingWhitespace", context.getString(R.string.builtin_action_line_trim_trailing_whitespace_label)),
        BuiltinSymbolActionOption("line.trimLeadingWhitespace", context.getString(R.string.builtin_action_line_trim_leading_whitespace_label)),
        BuiltinSymbolActionOption("line.duplicateAbove", context.getString(R.string.builtin_action_line_duplicate_above_label)),
        BuiltinSymbolActionOption("line.toggleComment", context.getString(R.string.builtin_action_line_toggle_comment_label), context.getString(R.string.builtin_action_line_toggle_comment_hint)),
        BuiltinSymbolActionOption("editor.toggleLineComment", context.getString(R.string.builtin_action_editor_toggle_line_comment_label), context.getString(R.string.builtin_action_editor_toggle_line_comment_hint)),
        BuiltinSymbolActionOption("line.indent", context.getString(R.string.builtin_action_line_indent_label), context.getString(R.string.builtin_action_line_indent_hint)),
        BuiltinSymbolActionOption("line.unindent", context.getString(R.string.builtin_action_line_unindent_label)),
        BuiltinSymbolActionOption("selection.delete", context.getString(R.string.builtin_action_selection_delete_label)),
        BuiltinSymbolActionOption("selection.duplicate", context.getString(R.string.builtin_action_selection_duplicate_label)),
        BuiltinSymbolActionOption("selection.uppercase", context.getString(R.string.builtin_action_selection_uppercase_label)),
        BuiltinSymbolActionOption("selection.lowercase", context.getString(R.string.builtin_action_selection_lowercase_label)),
        BuiltinSymbolActionOption("selection.toggleCase", context.getString(R.string.builtin_action_selection_toggle_case_label)),
        BuiltinSymbolActionOption("selection.wrapParentheses", context.getString(R.string.builtin_action_selection_wrap_parentheses_label)),
        BuiltinSymbolActionOption("selection.wrapBrackets", context.getString(R.string.builtin_action_selection_wrap_brackets_label)),
        BuiltinSymbolActionOption("selection.wrapBraces", context.getString(R.string.builtin_action_selection_wrap_braces_label)),
        BuiltinSymbolActionOption("selection.wrapQuotes", context.getString(R.string.builtin_action_selection_wrap_quotes_label)),
        BuiltinSymbolActionOption("selection.wrapSingleQuotes", context.getString(R.string.builtin_action_selection_wrap_single_quotes_label)),
        BuiltinSymbolActionOption("selection.wrapBackticks", context.getString(R.string.builtin_action_selection_wrap_backticks_label)),
        BuiltinSymbolActionOption("selection.wrapAngleBrackets", context.getString(R.string.builtin_action_selection_wrap_angle_brackets_label)),
        BuiltinSymbolActionOption("selection.wrapAsterisks", context.getString(R.string.builtin_action_selection_wrap_asterisks_label)),
        BuiltinSymbolActionOption("selection.wrapUnderscores", context.getString(R.string.builtin_action_selection_wrap_underscores_label)),
        BuiltinSymbolActionOption("selection.wrapHtmlTag", context.getString(R.string.builtin_action_selection_wrap_html_tag_label), context.getString(R.string.builtin_action_selection_wrap_html_tag_hint)),
        BuiltinSymbolActionOption("selection.indentLines", context.getString(R.string.builtin_action_selection_indent_lines_label), context.getString(R.string.builtin_action_selection_indent_lines_hint)),
        BuiltinSymbolActionOption("selection.unindentLines", context.getString(R.string.builtin_action_selection_unindent_lines_label)),
        BuiltinSymbolActionOption("selection.reverseLines", context.getString(R.string.builtin_action_selection_reverse_lines_label)),
        BuiltinSymbolActionOption("selection.sortLines", context.getString(R.string.builtin_action_selection_sort_lines_label)),
        BuiltinSymbolActionOption("selection.comment", context.getString(R.string.builtin_action_selection_comment_label), context.getString(R.string.builtin_action_selection_comment_hint)),
        BuiltinSymbolActionOption("cursor.wordLeft", context.getString(R.string.builtin_action_cursor_word_left_label)),
        BuiltinSymbolActionOption("cursor.wordRight", context.getString(R.string.builtin_action_cursor_word_right_label)),
        BuiltinSymbolActionOption("cursor.deleteWordBackward", context.getString(R.string.builtin_action_cursor_delete_word_backward_label)),
        BuiltinSymbolActionOption("cursor.deleteWordForward", context.getString(R.string.builtin_action_cursor_delete_word_forward_label)),
        BuiltinSymbolActionOption("cursor.selectWord", context.getString(R.string.builtin_action_cursor_select_word_label)),
        BuiltinSymbolActionOption("cursor.selectLine", context.getString(R.string.builtin_action_cursor_select_line_label)),
        BuiltinSymbolActionOption("cursor.lineStart", context.getString(R.string.builtin_action_cursor_line_start_label)),
        BuiltinSymbolActionOption("cursor.lineEnd", context.getString(R.string.builtin_action_cursor_line_end_label)),
        BuiltinSymbolActionOption("cursor.selectToLineStart", context.getString(R.string.builtin_action_cursor_select_to_line_start_label)),
        BuiltinSymbolActionOption("cursor.selectToLineEnd", context.getString(R.string.builtin_action_cursor_select_to_line_end_label)),
        BuiltinSymbolActionOption("cursor.selectToDocumentStart", context.getString(R.string.builtin_action_cursor_select_to_document_start_label)),
        BuiltinSymbolActionOption("cursor.selectToDocumentEnd", context.getString(R.string.builtin_action_cursor_select_to_document_end_label)),
        BuiltinSymbolActionOption("cursor.documentStart", context.getString(R.string.builtin_action_cursor_document_start_label)),
        BuiltinSymbolActionOption("cursor.documentEnd", context.getString(R.string.builtin_action_cursor_document_end_label)),
        BuiltinSymbolActionOption("document.selectAll", context.getString(R.string.builtin_action_document_select_all_label)),
        BuiltinSymbolActionOption("document.trimTrailingWhitespace", context.getString(R.string.builtin_action_document_trim_trailing_whitespace_label)),
        BuiltinSymbolActionOption("document.trimLeadingWhitespace", context.getString(R.string.builtin_action_document_trim_leading_whitespace_label)),
        BuiltinSymbolActionOption("document.ensureFinalNewline", context.getString(R.string.builtin_action_document_ensure_final_newline_label), context.getString(R.string.builtin_action_document_ensure_final_newline_hint)),
        BuiltinSymbolActionOption("document.removeBlankLines", context.getString(R.string.builtin_action_document_remove_blank_lines_label)),
        BuiltinSymbolActionOption("document.toggleComment", context.getString(R.string.builtin_action_document_toggle_comment_label), context.getString(R.string.builtin_action_document_toggle_comment_hint)),
        BuiltinSymbolActionOption("undo", context.getString(R.string.builtin_action_undo_label)),
        BuiltinSymbolActionOption("redo", context.getString(R.string.builtin_action_redo_label)),
        BuiltinSymbolActionOption("indent", context.getString(R.string.builtin_action_indent_label)),
        BuiltinSymbolActionOption("unindent", context.getString(R.string.builtin_action_unindent_label)),
        BuiltinSymbolActionOption("format", context.getString(R.string.builtin_action_format_label)),
        BuiltinSymbolActionOption("insert_payload", context.getString(R.string.builtin_action_insert_payload_label), context.getString(R.string.builtin_action_insert_payload_hint))
    )

    private val byId = options.associateBy(BuiltinSymbolActionOption::commandId)
    fun find(commandId: String): BuiltinSymbolActionOption? = byId[commandId]
    fun find(context: Context, commandId: String): BuiltinSymbolActionOption? =
        options(context).firstOrNull { it.commandId == commandId }
}
