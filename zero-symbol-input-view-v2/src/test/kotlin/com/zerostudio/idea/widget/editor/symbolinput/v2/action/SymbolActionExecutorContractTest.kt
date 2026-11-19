/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.action

import com.zerostudio.idea.widget.editor.symbolinput.v2.model.*
import org.junit.Assert.*
import org.junit.Test

class SymbolActionExecutorContractTest {
    private class Host : SymbolActionHost {
        val calls = mutableListOf<String>()
        var supported = true
        var thrown: Throwable? = null
        var afterInsert: (() -> Unit)? = null
        private fun result(call: String): Boolean {
            calls += call
            thrown?.let { throw it }
            return supported
        }
        override fun insertText(template: String): Boolean {
            val success = result("insert:$template")
            afterInsert?.invoke()
            return success
        }
        override fun replaceSelection(text: String) = result("replace:$text")
        override fun surroundSelection(prefix: String, suffix: String) = result("surround:$prefix:$suffix")
        override fun moveCursor(target: CursorTarget) = result("cursor:$target")
        override fun delete(kind: DeleteKind) = result("delete:$kind")
        override fun transformSelection(transform: SelectionTransform) = result("transform:$transform")
        override fun executeEditorCommand(commandId: String, payload: String?) = result("command:$commandId:$payload")
        override fun performClipboard(operation: ClipboardOperation) = result("clipboard:$operation")
        override fun openManager() = result("manager")
        override fun executeExtension(extensionId: String, payload: String?) = result("extension:$extensionId:$payload")
    }

    private val executor = SymbolActionExecutor()

    @Test fun dispatchesEveryLeafActionToTheMatchingHostMethod() {
        val cases = listOf(
            SymbolAction.InsertText("x") to "insert:x",
            SymbolAction.ReplaceSelection("y") to "replace:y",
            SymbolAction.SurroundSelection("[", "]") to "surround:[:]",
            SymbolAction.MoveCursor(CursorTarget.DOCUMENT_END) to "cursor:DOCUMENT_END",
            SymbolAction.Delete(DeleteKind.CURRENT_LINE) to "delete:CURRENT_LINE",
            SymbolAction.TransformSelection(SelectionTransform.TOGGLE_CASE) to "transform:TOGGLE_CASE",
            SymbolAction.EditorCommand("editor.format", "json") to "command:editor.format:json",
            SymbolAction.Clipboard(ClipboardOperation.PASTE) to "clipboard:PASTE",
            SymbolAction.OpenManager to "manager",
            SymbolAction.Extension("plugin.action", "data") to "extension:plugin.action:data"
        )
        cases.forEach { (action, expected) ->
            val host = Host()
            assertEquals(expected, com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Executed, executor.execute(action, host))
            assertEquals(listOf(expected), host.calls)
        }
    }

    @Test fun returnsUnsupportedForEveryHostCapabilityThatDeclines() {
        val actions = listOf(
            SymbolAction.InsertText("x"), SymbolAction.ReplaceSelection("x"),
            SymbolAction.SurroundSelection("(", ")"), SymbolAction.MoveCursor(CursorTarget.LEFT),
            SymbolAction.Delete(DeleteKind.BACKWARD),
            SymbolAction.TransformSelection(SelectionTransform.UPPERCASE),
            SymbolAction.EditorCommand("editor.run"), SymbolAction.Clipboard(ClipboardOperation.COPY),
            SymbolAction.OpenManager, SymbolAction.Extension("plugin.run")
        )
        actions.forEach { action ->
            val host = Host().apply { supported = false }
            assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Unsupported(action), executor.execute(action, host))
            assertEquals(1, host.calls.size)
        }
    }

    @Test fun convertsHostExceptionsIntoFailedResults() {
        val error = IllegalStateException("host unavailable")
        val result = executor.execute(SymbolAction.OpenManager, Host().apply { thrown = error })
        assertTrue(result is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Failed)
        assertSame(error, (result as com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Failed).cause)
    }

    @Test fun reportsFailureWithoutPartialWrapperWhenFirstCompositeActionFails() {
        val host = Host().apply { supported = false }
        val result = executor.execute(
            SymbolAction.Composite(listOf(SymbolAction.InsertText("first"), SymbolAction.InsertText("second"))),
            host
        )
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Unsupported(SymbolAction.InsertText("first")), result)
        assertEquals(listOf("insert:first"), host.calls)
    }

    @Test fun nestedCompositeExecutesDepthFirstInDeclaredOrder() {
        val host = Host()
        val action = SymbolAction.Composite(listOf(
            SymbolAction.InsertText("a"),
            SymbolAction.Composite(listOf(SymbolAction.ReplaceSelection("b"), SymbolAction.OpenManager)),
            SymbolAction.Extension("plugin.done")
        ))
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Executed, executor.execute(action, host))
        assertEquals(listOf("insert:a", "replace:b", "manager", "extension:plugin.done:null"), host.calls)
    }

    @Test fun rejectsEmptyCompositeAndActionCountOverflowBeforeHostCalls() {
        val host = Host()
        assertTrue(executor.execute(SymbolAction.Composite(emptyList()), host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(SymbolActionExecutor(maxCompositeActions = 1).execute(
            SymbolAction.Composite(listOf(SymbolAction.OpenManager, SymbolAction.OpenManager)), host
        ) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(host.calls.isEmpty())
    }

    @Test fun rejectsNestedCompositeBeyondConfiguredDepthBeforeExecution() {
        var action: SymbolAction = SymbolAction.OpenManager
        repeat(4) { action = SymbolAction.Composite(listOf(action)) }
        val host = Host()
        assertTrue(SymbolActionExecutor(maxNestingDepth = 2).execute(action, host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(host.calls.isEmpty())
    }

    @Test fun rejectsInvalidExtensionIdsAndCommandIds() {
        val host = Host()
        listOf("", "../x", "has space", "-bad").forEach { id ->
            assertTrue(executor.execute(SymbolAction.EditorCommand(id), host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
            assertTrue(executor.execute(SymbolAction.Extension(id), host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        }
        assertTrue(host.calls.isEmpty())
    }

    @Test fun acceptsMaximumConfiguredTextLengthAndRejectsOneCharacterOver() {
        val host = Host()
        val bounded = SymbolActionExecutor(maxTextLength = 4)
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Executed, bounded.execute(SymbolAction.InsertText("1234"), host))
        assertTrue(bounded.execute(SymbolAction.InsertText("12345"), host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(bounded.execute(SymbolAction.ReplaceSelection("12345"), host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(bounded.execute(SymbolAction.SurroundSelection("12", "345"), host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertEquals(1, host.calls.size)
    }

    @Test fun constructorRejectsOutOfRangeResourceLimits() {
        assertFails { SymbolActionExecutor(maxCompositeActions = 0) }
        assertFails { SymbolActionExecutor(maxCompositeActions = 257) }
        assertFails { SymbolActionExecutor(maxNestingDepth = 0) }
        assertFails { SymbolActionExecutor(maxNestingDepth = 17) }
        assertFails { SymbolActionExecutor(maxTextLength = 0) }
        assertFails { SymbolActionExecutor(maxTextLength = 4_194_305) }
    }


    @Test fun enforcesAnAggregateCompositeActionBudgetBeforeHostCalls() {
        val action = SymbolAction.Composite(listOf(
            SymbolAction.Composite(listOf(SymbolAction.OpenManager, SymbolAction.OpenManager)),
            SymbolAction.OpenManager
        ))
        val host = Host()
        val result = SymbolActionExecutor(maxTotalActions = 3).execute(action, host)
        assertTrue(result is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(host.calls.isEmpty())
    }

    @Test fun executionRechecksAggregateBudgetIfTheActionTreeChangesAfterValidation() {
        var expanded = false
        val nestedActions = object : AbstractList<SymbolAction>() {
            override val size: Int get() = if (expanded) 64 else 1
            override fun get(index: Int): SymbolAction {
                if (index !in 0 until size) throw IndexOutOfBoundsException()
                return SymbolAction.InsertText("nested")
            }
        }
        val action = SymbolAction.Composite(listOf(
            SymbolAction.InsertText("trigger"),
            SymbolAction.Composite(nestedActions)
        ))
        val host = Host().apply { afterInsert = { expanded = true } }

        val result = SymbolActionExecutor(maxTotalActions = 4).execute(action, host)

        assertTrue(result is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.PartialFailure)
        assertEquals(listOf("insert:trigger", "insert:nested"), host.calls)
    }

    @Test fun doesNotConvertFatalErrorsIntoOrdinaryActionFailures() {
        val fatal = AssertionError("fatal")
        val host = Host().apply { thrown = fatal }
        try {
            executor.execute(SymbolAction.OpenManager, host)
            fail("Fatal JVM errors must propagate")
        } catch (actual: AssertionError) {
            assertSame(fatal, actual)
        }
    }

    @Test fun rejectsSurroundActionsWhoseCombinedTextExceedsTheLimit() {
        val host = Host()
        val limitedExecutor = SymbolActionExecutor(maxTextLength = 7)
        val action = SymbolAction.SurroundSelection("1234", "5678")
        assertTrue(limitedExecutor.execute(action, host) is com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Invalid)
        assertTrue(host.calls.isEmpty())

        // Each field is individually below the codec limit, but their combined
        // size exceeds it and must be rejected before a SymbolAction is created.
        val json = com.google.gson.JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("kind", "surroundSelection")
            addProperty("prefix", "p".repeat(524_289))
            addProperty("suffix", "s".repeat(524_288))
        }
        assertTrue(runCatching { SymbolActionJsonCodec.decode(json) }.isFailure)
    }


    @Test fun cancellationBeforeExecutionPreventsHostCalls() {
        val host = Host()
        val result = executor.execute(SymbolAction.OpenManager, host, SymbolActionCancellation { true })
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Cancelled(0), result)
        assertTrue(host.calls.isEmpty())
    }

    @Test fun compositeCancellationStopsBeforeTheNextStepAndReportsCompletedLeaves() {
        val host = Host()
        var cancelled = false
        val token = SymbolActionCancellation { cancelled }
        val cancelAfterFirst = object : SymbolActionHost by host {
            override fun insertText(template: String): Boolean {
                val result = host.insertText(template)
                cancelled = true
                return result
            }
        }
        val result = executor.execute(
            SymbolAction.Composite(listOf(
                SymbolAction.InsertText("first"),
                SymbolAction.InsertText("second"),
                SymbolAction.OpenManager
            )),
            cancelAfterFirst,
            token
        )
        assertEquals(com.zerostudio.idea.widget.drawer.action.SymbolActionResult.Cancelled(1), result)
        assertEquals(listOf("insert:first"), host.calls)
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); fail("Expected IllegalArgumentException") }
        catch (_: IllegalArgumentException) { }
    }
}
