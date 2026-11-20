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
package com.zerostudio.idea.widget.editor.symbolinput.v2.extension

import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionId
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SymbolDrawerExtensionRegistryTest {
    private class Host : SymbolActionHost {
        var last: Pair<String, String?>? = null
        override fun insertText(template: String) = true
        override fun replaceSelection(text: String) = true
        override fun surroundSelection(prefix: String, suffix: String) = true
        override fun moveCursor(target: com.zerostudio.idea.widget.editor.symbolinput.v2.model.CursorTarget) = true
        override fun delete(kind: com.zerostudio.idea.widget.editor.symbolinput.v2.model.DeleteKind) = true
        override fun transformSelection(transform: com.zerostudio.idea.widget.editor.symbolinput.v2.model.SelectionTransform) = true
        override fun executeEditorCommand(commandId: String, payload: String?) = true
        override fun performClipboard(operation: com.zerostudio.idea.widget.editor.symbolinput.v2.model.ClipboardOperation) = true
        override fun openManager() = true
        override fun executeExtension(extensionId: String, payload: String?): Boolean {
            last = extensionId to payload
            return false
        }
    }

    @Test fun registeredActionIsDispatchedAndHandleUnregistersIt() {
        val registry = SymbolDrawerExtensionRegistry()
        val host = Host()
        var called = false
        val handle = registry.registerAction(
            SymbolDrawerActionExtension(
                id = "sample.insert",
                title = "Sample",
                handler = { _, payload -> called = payload == "hello"; true }
            )
        )
        val wrapped = registry.wrapActionHost(host)
        assertTrue(wrapped.executeExtension("sample.insert", "hello"))
        assertTrue(called)
        handle.close()
        assertFalse(wrapped.executeExtension("sample.insert", "fallback"))
        assertEquals("sample.insert", host.last?.first)
    }

    @Test fun numericLegacyActionIdDispatchesWithUnchangedPayloadAndUnregisters() {
        val registry = SymbolDrawerExtensionRegistry()
        val host = Host()
        var received: String? = null
        val handle = registry.registerLegacyAction(73) { _, payload ->
            received = payload
            true
        }

        val wrapped = registry.wrapActionHost(host)
        assertTrue(wrapped.executeExtension("legacy.action.73", "custom-payload"))
        assertEquals("custom-payload", received)
        assertNull(host.last)

        handle.close()
        assertFalse(wrapped.executeExtension("legacy.action.73", "fallback"))
        assertEquals("legacy.action.73", host.last?.first)
        assertEquals("fallback", host.last?.second)
    }

    @Test fun customActionReplacesByIdWithoutOldHandleRemovingReplacement() {
        val registry = SymbolDrawerExtensionRegistry()
        var first = false
        var second = false
        val firstHandle = registry.registerAction(
            SymbolDrawerActionExtension("app.command", "First", handler = { _, _ -> first = true; true })
        )
        registry.registerAction(
            SymbolDrawerActionExtension("app.command", "Second", handler = { _, _ -> second = true; true })
        )
        firstHandle.close()
        registry.wrapActionHost(Host()).executeExtension("app.command", null)
        assertFalse(first)
        assertTrue(second)
    }

    @Test
    fun replacedLegacyActionSurvivesClosingPreviousHandle() {
        val registry = SymbolDrawerExtensionRegistry()
        var first = false
        var second = false
        val firstHandle = registry.registerLegacyAction(91) { _, _ -> first = true; true }
        registry.registerLegacyAction(91) { _, _ -> second = true; true }

        firstHandle.close()
        assertTrue(registry.wrapActionHost(Host()).executeExtension("legacy.action.91", null))
        assertFalse(first)
        assertTrue(second)
    }

    @Test
    fun legacyActionHandleCanBeClosedRepeatedly() {
        val registry = SymbolDrawerExtensionRegistry()
        var notifications = 0
        registry.observe(emitCurrent = false) { notifications++ }
        val handle = registry.registerLegacyAction(92) { _, _ -> true }
        assertEquals(1, notifications)

        handle.close()
        handle.close()
        assertEquals(2, notifications)
        assertNull(registry.actionHandlers.get(92))
    }

    @Test
    fun clearRemovesCurrentActionsAndAllowsFreshRegistration() {
        val registry = SymbolDrawerExtensionRegistry()
        var oldCalled = false
        var newCalled = false
        val oldHandle = registry.registerAction(
            SymbolDrawerActionExtension("clear.action", "Old", handler = { _, _ -> oldCalled = true; true })
        )

        registry.clear()
        assertTrue(registry.snapshot().actions.isEmpty())
        assertNull(registry.actionHandlers.get(SymbolActionId.extension("clear.action")))

        val newHandle = registry.registerAction(
            SymbolDrawerActionExtension("clear.action", "New", handler = { _, _ -> newCalled = true; true })
        )
        // Closing a handle from before clear must not unregister the new entry.
        oldHandle.close()
        assertTrue(registry.wrapActionHost(Host()).executeExtension("clear.action", null))
        assertFalse(oldCalled)
        assertTrue(newCalled)

        newHandle.close()
        assertNull(registry.actionHandlers.get(SymbolActionId.extension("clear.action")))
    }

    @Test
    fun builtInFeatureIdsAreStableAndDistinct() {
        val ids = listOf(
            SymbolDrawerBuiltInFeatures.SEARCH,
            SymbolDrawerBuiltInFeatures.FAVORITES,
            SymbolDrawerBuiltInFeatures.RECENT,
            SymbolDrawerBuiltInFeatures.SETTINGS,
            SymbolDrawerBuiltInFeatures.MANAGER
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf(
                "builtin.search",
                "builtin.favorites",
                "builtin.recent",
                "builtin.settings",
                "builtin.manager"
            ),
            ids
        )
    }

    @Test fun builtInVisibilityDefaultsToVisibleAndCanBeRestored() {
        val registry = SymbolDrawerExtensionRegistry()
        assertTrue(registry.isBuiltInVisible("builtin.search"))
        registry.setBuiltInVisible("builtin.search", false)
        assertFalse(registry.isBuiltInVisible("builtin.search"))
        registry.setBuiltInVisible("builtin.search", true)
        assertTrue(registry.isBuiltInVisible("builtin.search"))
    }


    @Test fun observerReceivesImmutableSnapshotsAndCanUnsubscribe() {
        val registry = SymbolDrawerExtensionRegistry()
        val revisions = mutableListOf<Long>()
        val actionCounts = mutableListOf<Int>()
        val handle = registry.observe { snapshot ->
            revisions += snapshot.revision
            actionCounts += snapshot.actions.size
        }
        val registration = registry.registerAction(
            SymbolDrawerActionExtension("observe.action", "Observed", handler = { _, _ -> true })
        )
        assertEquals(2, revisions.size)
        assertEquals(0, actionCounts.first())
        assertEquals(1, actionCounts.last())
        assertTrue(revisions.last() > revisions.first())
        registration.close()
        assertEquals(0, registry.snapshot().actions.size)
        val countAfterClose = revisions.size
        handle.close()
        registry.setBuiltInVisible("builtin.search", false)
        assertEquals(countAfterClose, revisions.size)
    }

    @Test
    fun throwingInitialObserverDoesNotBreakRegistryRegistration() {
        val registry = SymbolDrawerExtensionRegistry()
        registry.observe(emitCurrent = true) {
            error("observer failure")
        }
        val handle = registry.registerAction(
            SymbolDrawerActionExtension("safe.observer", "Safe", handler = { _, _ -> true })
        )
        assertEquals(1, registry.snapshot().actions.size)
        handle.close()
        assertTrue(registry.snapshot().actions.isEmpty())
    }

    @Test fun snapshotContainsBuiltInVisibilityAndAllContributionTypes() {
        val registry = SymbolDrawerExtensionRegistry()
        registry.setBuiltInVisible("builtin.manager", false)
        val snapshot = registry.snapshot()
        assertFalse(snapshot.builtInVisibility["builtin.manager"] ?: true)
        assertEquals(registry.revision(), snapshot.revision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidExtensionIdIsRejected() {
        SymbolDrawerExtensionRegistry().registerAction(
            SymbolDrawerActionExtension("bad id", "Bad", handler = { _, _ -> true })
        )
    }

    @Test
    fun itemViewRendererIsPublishedAndUnregisteredFromTheDrawerRegistry() {
        val registry = SymbolDrawerExtensionRegistry()
        val renderer = object : SymbolDrawerItemViewExtension {
            override val id = "sample.renderer"
            override val title = "Sample renderer"
            override fun createView(
                context: android.content.Context,
                item: com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem,
                state: SymbolDrawerItemViewState
            ): android.view.View = error("Creation is owned by SymbolPageAdapter")
            override fun bindView(
                view: android.view.View,
                item: com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem,
                state: SymbolDrawerItemViewState
            ) = Unit
        }
        val handle = registry.registerItemView(renderer)
        assertEquals("sample.renderer", registry.itemViews().single().id)
        assertEquals("sample.renderer", registry.snapshot().itemViews.single().id)
        handle.close()
        assertTrue(registry.itemViews().isEmpty())
    }

    @Test
    fun legacyProvidersAndLifecycleContributionsAreVisibleInSnapshots() {
        val registry = SymbolDrawerExtensionRegistry()
        val provider = object : SymbolDrawerLegacyGroupProvider {
            override val id = "sample.groups"
            override val title = "Sample groups"
            override suspend fun loadGroups() =
                listOf(com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup("sample"))
        }
        val lifecycle = object : SymbolDrawerLifecycleExtension {
            override val id = "sample.lifecycle"
        }
        val providerHandle = registry.registerGroupProvider(provider)
        val lifecycleHandle = registry.registerLifecycle(lifecycle)
        assertEquals("sample.groups", registry.snapshot().groupProviders.single().id)
        assertEquals("sample.lifecycle", registry.snapshot().lifecycle.single().id)
        providerHandle.close()
        lifecycleHandle.close()
        assertTrue(registry.snapshot().groupProviders.isEmpty())
        assertTrue(registry.snapshot().lifecycle.isEmpty())
    }
    @Test fun itemViewExtensionStateCallbackIsOptionalAndSourceCompatible() {
        val extension = object : SymbolDrawerItemViewExtension {
            override val id = "test.state"
            override val title = "State"
            override fun createView(
                context: android.content.Context,
                item: SymbolItem,
                state: SymbolDrawerItemViewState
            ): android.view.View = throw UnsupportedOperationException()
            override fun bindView(
                view: android.view.View,
                item: SymbolItem,
                state: SymbolDrawerItemViewState
            ) = Unit
        }
        assertEquals("test.state", extension.id)
        // All lifecycle/state hooks remain optional for existing third-party renderers.
        val viewType: (android.view.View, SymbolItem) -> Unit = { view, item ->
            extension.onViewAttachedToWindow(view, item)
            extension.onViewDetachedFromWindow(view, item)
            extension.onStateChanged(view, item, SymbolDrawerItemViewState(0, 0))
        }
        assertNotNull(viewType)
    }
    @Test fun extensionErrorListenerIsolatedFromFaultyHostCallback() {
        val registry = SymbolDrawerExtensionRegistry()
        var reported = 0
        registry.onExtensionErrorListener = { id, error ->
            assertEquals("test.extension", id)
            assertEquals("boom", error.message)
            reported++
        }
        registry.reportError("test.extension", IllegalStateException("boom"))
        assertEquals(1, reported)

        registry.onExtensionErrorListener = { _, _ -> error("host diagnostic callback failure") }
        registry.reportError("test.extension", IllegalStateException("isolated"))
    }
    @Test
    fun concurrentMutationsNeverDeliverOlderSnapshotsAfterNewerOnes() {
        val registry = SymbolDrawerExtensionRegistry()
        val firstCallbackEntered = CountDownLatch(1)
        val releaseFirstCallback = CountDownLatch(1)
        val revisions = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val actionCounts = java.util.Collections.synchronizedList(mutableListOf<Int>())

        registry.observe(emitCurrent = false) { snapshot ->
            revisions += snapshot.revision
            actionCounts += snapshot.actions.size
            if (snapshot.actions.size == 1) {
                firstCallbackEntered.countDown()
                check(releaseFirstCallback.await(5, TimeUnit.SECONDS))
            }
        }

        val firstMutation = Thread {
            registry.registerAction(
                SymbolDrawerActionExtension("concurrent.first", "First", handler = { _, _ -> true })
            )
        }
        firstMutation.start()
        assertTrue(firstCallbackEntered.await(5, TimeUnit.SECONDS))

        val secondMutation = Thread {
            registry.registerAction(
                SymbolDrawerActionExtension("concurrent.second", "Second", handler = { _, _ -> true })
            )
        }
        secondMutation.start()

        // Wait until the second contribution is committed while its notification
        // is still queued behind the first observer callback.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (registry.snapshot().actions.size < 2 && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertEquals(2, registry.snapshot().actions.size)
        releaseFirstCallback.countDown()
        firstMutation.join(5_000)
        secondMutation.join(5_000)

        assertFalse(firstMutation.isAlive)
        assertFalse(secondMutation.isAlive)
        assertEquals(2, actionCounts.last())
        assertTrue(revisions.zipWithNext().all { (before, after) -> after > before })
        assertTrue(actionCounts.zipWithNext().all { (before, after) -> after >= before })
    }

}
