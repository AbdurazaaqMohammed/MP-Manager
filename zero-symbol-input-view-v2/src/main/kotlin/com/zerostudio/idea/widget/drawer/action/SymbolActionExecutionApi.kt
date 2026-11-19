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

package com.zerostudio.idea.widget.drawer.action

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.ClipboardOperation
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.CursorTarget
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SelectionTransform
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction

/**
 * Host adapter implemented by the application/editor integration. Methods are
 * invoked synchronously on the caller thread; UI/editor hosts should call execute
 * from their required thread and report unavailable capabilities with false.
 */
interface SymbolActionHost {
    fun insertText(template: String): Boolean
    fun replaceSelection(text: String): Boolean
    fun surroundSelection(prefix: String, suffix: String): Boolean
    fun moveCursor(target: CursorTarget): Boolean
    fun delete(kind: DeleteKind): Boolean
    fun transformSelection(transform: SelectionTransform): Boolean
    fun executeEditorCommand(commandId: String, payload: String?): Boolean
    fun performClipboard(operation: ClipboardOperation): Boolean
    fun openManager(): Boolean
    fun executeExtension(extensionId: String, payload: String?): Boolean
}

/** Cooperative cancellation checked between action steps; host calls remain synchronous. */
fun interface SymbolActionCancellation {
    fun isCancelled(): Boolean

    companion object {
        val NEVER = SymbolActionCancellation { false }
    }
}

sealed interface SymbolActionResult {
    data object Executed : SymbolActionResult
    data class Cancelled(val completedActions: Int) : SymbolActionResult
    data class Unsupported(val action: SymbolAction) : SymbolActionResult
    data class Invalid(val reason: String) : SymbolActionResult
    data class Failed(val cause: Throwable) : SymbolActionResult
    data class PartialFailure(val completedActions: Int, val cause: SymbolActionResult) : SymbolActionResult
}

class SymbolActionExecutor(
    private val maxCompositeActions: Int = 64,
    private val maxNestingDepth: Int = 8,
    private val maxTextLength: Int = 1_048_576,
    private val maxTotalActions: Int = 256
) {
    init {
        require(maxCompositeActions in 1..256)
        require(maxNestingDepth in 1..16)
        require(maxTextLength in 1..4_194_304)
        require(maxTotalActions in 1..4_096)
    }

    fun execute(
        action: SymbolAction,
        host: SymbolActionHost,
        cancellation: SymbolActionCancellation = SymbolActionCancellation.NEVER
    ): SymbolActionResult {
        val validationError = validate(action, 0, ActionBudget(maxTotalActions))
        if (validationError != null) return SymbolActionResult.Invalid(validationError)
        return executeInternal(action, host, 0, cancellation, ExecutionProgress(), ActionBudget(maxTotalActions))
    }

    /** Validate the entire tree before invoking the host, preventing malformed
     * later steps from leaving a partially applied composite action. */
    private fun validate(action: SymbolAction, depth: Int, budget: ActionBudget): String? {
        if (depth > maxNestingDepth) return "Composite action nesting limit exceeded"
        if (!budget.consume()) return "Composite action total action limit exceeded"
        return when (action) {
            is SymbolAction.InsertText ->
                if (action.template.length > maxTextLength) "Insert template is too large" else null
            is SymbolAction.ReplaceSelection ->
                if (action.text.length > maxTextLength) "Replacement text is too large" else null
            is SymbolAction.SurroundSelection ->
                if (action.prefix.length.toLong() + action.suffix.length > maxTextLength) "Surround template is too large" else null
            is SymbolAction.EditorCommand -> when {
                !COMMAND_ID.matches(action.commandId) -> "Invalid editor command ID"
                (action.payload?.length ?: 0) > maxTextLength -> "Editor command payload is too large"
                else -> null
            }
            is SymbolAction.Extension -> when {
                !EXTENSION_ID.matches(action.extensionId) -> "Invalid extension ID"
                (action.payload?.length ?: 0) > maxTextLength -> "Extension payload is too large"
                else -> null
            }
            is SymbolAction.Composite -> when {
                action.actions.isEmpty() -> "Composite action cannot be empty"
                action.actions.size > maxCompositeActions -> "Composite action contains too many steps"
                else -> action.actions.firstNotNullOfOrNull { validate(it, depth + 1, budget) }
            }
            else -> null
        }
    }

    private fun executeInternal(
        action: SymbolAction,
        host: SymbolActionHost,
        depth: Int,
        cancellation: SymbolActionCancellation,
        progress: ExecutionProgress,
        budget: ActionBudget
    ): SymbolActionResult {
        if (depth > maxNestingDepth) return SymbolActionResult.Invalid("Composite action nesting limit exceeded")
        if (cancellation.isCancelled()) return SymbolActionResult.Cancelled(progress.completedActions)
        if (!budget.consume()) return SymbolActionResult.Invalid("Composite action total action limit exceeded")
        return try {
            val result = when (action) {
                is SymbolAction.InsertText -> {
                    if (action.template.length > maxTextLength) invalid("Insert template is too large")
                    else supported(host.insertText(action.template), action)
                }
                is SymbolAction.ReplaceSelection -> {
                    if (action.text.length > maxTextLength) invalid("Replacement text is too large")
                    else supported(host.replaceSelection(action.text), action)
                }
                is SymbolAction.SurroundSelection -> {
                    if (action.prefix.length.toLong() + action.suffix.length > maxTextLength) invalid("Surround template is too large")
                    else supported(host.surroundSelection(action.prefix, action.suffix), action)
                }
                is SymbolAction.MoveCursor -> supported(host.moveCursor(action.target), action)
                is SymbolAction.Delete -> supported(host.delete(action.kind), action)
                is SymbolAction.TransformSelection -> supported(host.transformSelection(action.transform), action)
                is SymbolAction.EditorCommand -> {
                    when {
                        !COMMAND_ID.matches(action.commandId) -> invalid("Invalid editor command ID")
                        (action.payload?.length ?: 0) > maxTextLength -> invalid("Editor command payload is too large")
                        else -> supported(host.executeEditorCommand(action.commandId, action.payload), action)
                    }
                }
                is SymbolAction.Clipboard -> supported(host.performClipboard(action.operation), action)
                SymbolAction.OpenManager -> supported(host.openManager(), action)
                is SymbolAction.Extension -> {
                    when {
                        !EXTENSION_ID.matches(action.extensionId) -> invalid("Invalid extension ID")
                        (action.payload?.length ?: 0) > maxTextLength -> invalid("Extension payload is too large")
                        else -> supported(host.executeExtension(action.extensionId, action.payload), action)
                    }
                }
                is SymbolAction.Composite -> executeComposite(action, host, depth, cancellation, progress, budget)
            }
            if (action !is SymbolAction.Composite && result == SymbolActionResult.Executed) {
                progress.completedActions++
            }
            result
        } catch (error: Exception) {
            SymbolActionResult.Failed(error)
        }
    }

    private fun executeComposite(
        composite: SymbolAction.Composite,
        host: SymbolActionHost,
        depth: Int,
        cancellation: SymbolActionCancellation,
        progress: ExecutionProgress,
        budget: ActionBudget
    ): SymbolActionResult {
        if (composite.actions.isEmpty()) return SymbolActionResult.Invalid("Composite action cannot be empty")
        if (composite.actions.size > maxCompositeActions) {
            return SymbolActionResult.Invalid("Composite action contains too many steps")
        }
        var completed = 0
        composite.actions.forEach { child ->
            val result = executeInternal(child, host, depth + 1, cancellation, progress, budget)
            if (result is SymbolActionResult.Cancelled) return result
            if (result != SymbolActionResult.Executed) {
                return if (completed == 0) result else SymbolActionResult.PartialFailure(completed, result)
            }
            completed++
        }
        return SymbolActionResult.Executed
    }

    private class ExecutionProgress(var completedActions: Int = 0)

    private class ActionBudget(private var remaining: Int) {
        fun consume(): Boolean {
            if (remaining <= 0) return false
            remaining--
            return true
        }
    }

    private fun supported(success: Boolean, action: SymbolAction) =
        if (success) SymbolActionResult.Executed else SymbolActionResult.Unsupported(action)

    private fun invalid(reason: String) = SymbolActionResult.Invalid(reason)

    private companion object {
        val COMMAND_ID = Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,127}")
        val EXTENSION_ID = Regex("[a-zA-Z0-9][a-zA-Z0-9_.:/-]{0,255}")
    }
}
