/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.extension

import android.content.Context
import android.view.View
import android.view.MotionEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.RegistryBackedSymbolActionHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionHandler
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionId
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionRegistry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup as LegacySymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings

/**
 * Stable identifiers for built-in drawer features consumed by the original UI.
 *
 * These IDs are configuration keys, not extension contribution IDs. Keep their
 * values stable across releases so hosts can persist visibility overrides.
 */
object SymbolDrawerBuiltInFeatures {
    const val SEARCH = "builtin.search"
    const val FAVORITES = "builtin.favorites"
    const val RECENT = "builtin.recent"
    const val SETTINGS = "builtin.settings"
    const val MANAGER = "builtin.manager"
}

/**
 * Process/session scoped extension point registry for a V2 drawer instance.
 *
 * Registrations are runtime objects and are deliberately not serialized. Persist
 * stable IDs and configuration in the host application, then register extensions
 * again when the editor/session is recreated. Closing a registration handle
 * unregisters that contribution; built-in features are hidden, never removed.
 */
class SymbolDrawerExtensionRegistry {
    private data class Registered<T>(val token: Any, val value: T)
    private val lock = Any()
    // Keep observer delivery ordered even when independent threads mutate the
    // registry concurrently. Snapshots are captured only after entering this
    // dispatch lock, so a delayed notification cannot publish stale state.
    private val notificationLock = Any()
    private val actions = LinkedHashMap<String, Registered<SymbolDrawerActionExtension>>()
    private val indicators = LinkedHashMap<String, Registered<SymbolGroupIndicatorExtension>>()
    private val itemViews = LinkedHashMap<String, Registered<SymbolDrawerItemViewExtension>>()
    private val groupProviders = LinkedHashMap<String, Registered<SymbolDrawerLegacyGroupProvider>>()
    private val lifecycle = LinkedHashMap<String, Registered<SymbolDrawerLifecycleExtension>>()
    private val settingsTabs = LinkedHashMap<String, Registered<SymbolDrawerSettingsTabExtension>>()
    private val settingsItems = LinkedHashMap<String, Registered<SymbolDrawerSettingExtension>>()
    private val themes = LinkedHashMap<String, Registered<SymbolDrawerThemeExtension>>()
    private val builtInVisibility = LinkedHashMap<String, Boolean>()
    private val listeners = CopyOnWriteArrayList<(SymbolDrawerExtensionSnapshot) -> Unit>()
    @Volatile private var revision: Long = 0L

    /** Optional host diagnostic sink. Exceptions thrown by this callback are isolated. */
    @Volatile var onExtensionErrorListener: ((extensionId: String, error: Throwable) -> Unit)? = null

    internal fun reportError(extensionId: String, error: Throwable) {
        runCatching { onExtensionErrorListener?.invoke(extensionId, error) }
    }

    /** Monotonic registry revision, useful for UI diffing and cache invalidation. */
    fun revision(): Long = revision

    /** Observe live contribution changes. The returned handle removes the listener. */
    fun observe(
        emitCurrent: Boolean = true,
        listener: (SymbolDrawerExtensionSnapshot) -> Unit
    ): AutoCloseable {
        listeners += listener
        if (emitCurrent) {
            // An observer is an external extension callback. A faulty observer
            // must not make registration or drawer attachment fail.
            runCatching { listener(snapshot()) }
        }
        return AutoCloseable { listeners.remove(listener) }
    }

    /** Immutable snapshot for UI hosts to render without holding the registry lock. */
    fun snapshot(): SymbolDrawerExtensionSnapshot = synchronized(lock) {
        SymbolDrawerExtensionSnapshot(
            revision = revision,
            actions = actions.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            indicators = indicators.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            itemViews = itemViews.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            groupProviders = groupProviders.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            lifecycle = lifecycle.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            settingsTabs = settingsTabs.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            settingsItems = settingsItems.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            themes = themes.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })),
            builtInVisibility = builtInVisibility.toMap()
        )
    }

    private fun notifyChanged() = synchronized(notificationLock) {
        val next = synchronized(lock) {
            revision += 1
            snapshot()
        }
        listeners.forEach { callback -> runCatching { callback(next) } }
    }

    /** Action registry used by the extension-aware SymbolActionHost decorator. */
    val actionHandlers = SymbolActionRegistry<SymbolActionHost>()

    fun registerAction(extension: SymbolDrawerActionExtension): AutoCloseable =
        register(actions, extension.id, extension) {
            actionHandlers.register(SymbolActionId.extension(extension.id), extension.handler)
        }

    /**
     * Registers a handler against the original V1 numeric action ID.
     *
     * Legacy SymbolItem.shortAction/longAction values are dispatched without
     * translating them into a second string-based action schema. The payload
     * is passed through unchanged from shortText/longText. This is the preferred
     * extension point when a symbol is authored with the legacy SymbolData model.
     */
    fun registerLegacyAction(
        actionId: Int,
        handler: SymbolActionHandler<SymbolActionHost>
    ): AutoCloseable {
        require(actionId >= 0) { "Legacy action ID must be non-negative" }
        val registration = actionHandlers.register(actionId, handler)
        notifyChanged()
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                registration.close()
                notifyChanged()
            }
        }
    }

    fun registerIndicator(extension: SymbolGroupIndicatorExtension): AutoCloseable =
        register(indicators, extension.id, extension)

    /** Registers a renderer consumed by the existing SymbolPageAdapter cell lifecycle. */
    fun registerItemView(extension: SymbolDrawerItemViewExtension): AutoCloseable =
        register(itemViews, extension.id, extension)

    fun registerGroupProvider(extension: SymbolDrawerLegacyGroupProvider): AutoCloseable =
        register(groupProviders, extension.id, extension)

    fun registerLifecycle(extension: SymbolDrawerLifecycleExtension): AutoCloseable =
        register(lifecycle, extension.id, extension)

    fun registerSettingsTab(extension: SymbolDrawerSettingsTabExtension): AutoCloseable =
        register(settingsTabs, extension.id, extension)

    fun registerSetting(extension: SymbolDrawerSettingExtension): AutoCloseable =
        register(settingsItems, extension.id, extension)

    fun registerTheme(extension: SymbolDrawerThemeExtension): AutoCloseable =
        register(themes, extension.id, extension)

    private fun <T> register(
        target: LinkedHashMap<String, Registered<T>>,
        id: String,
        value: T,
        afterInsert: (() -> AutoCloseable)? = null
    ): AutoCloseable {
        require(id.matches(EXTENSION_ID)) { "Invalid extension ID: $id" }
        val token = Any()
        // Keep replacement and its secondary registration in one critical
        // section. Otherwise concurrent registrations with the same ID can
        // leave the public contribution and its action handler pointing at
        // different instances.
        val secondaryHandle = synchronized(lock) {
            val previous = target[id]
            target[id] = Registered(token, value)
            try {
                afterInsert?.invoke()
            } catch (error: Throwable) {
                // Registration is transactional: if setup fails, restore the
                // previous contribution instead of silently deleting it.
                if (target[id]?.token === token) {
                    if (previous == null) target.remove(id)
                    else target[id] = previous
                }
                throw error
            }
        }
        notifyChanged()
        var closed = false
        return AutoCloseable {
            val shouldClose = synchronized(lock) {
                if (closed) false else {
                    closed = true
                    true
                }
            }
            if (shouldClose) {
                val removed = synchronized(lock) {
                    if (target[id]?.token === token) {
                        target.remove(id)
                        true
                    } else false
                }
                if (removed) notifyChanged()
                // A replaced registration still owns its secondary action handle.
                secondaryHandle?.close()
            }
        }
    }

    /** Returns an immutable, priority-sorted snapshot of custom action entries. */
    fun actions(): List<SymbolDrawerActionExtension> =
        synchronized(lock) { actions.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    fun indicators(): List<SymbolGroupIndicatorExtension> =
        synchronized(lock) { indicators.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    fun itemViews(): List<SymbolDrawerItemViewExtension> =
        synchronized(lock) { itemViews.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    fun groupProviders(): List<SymbolDrawerLegacyGroupProvider> =
        synchronized(lock) { groupProviders.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    fun lifecycleExtensions(): List<SymbolDrawerLifecycleExtension> =
        synchronized(lock) { lifecycle.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    fun settingsTabs(): List<SymbolDrawerSettingsTabExtension> =
        synchronized(lock) { settingsTabs.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    fun settingsItems(): List<SymbolDrawerSettingExtension> =
        synchronized(lock) { settingsItems.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    fun themes(): List<SymbolDrawerThemeExtension> =
        synchronized(lock) { themes.values.map { it.value }.sortedWith(compareBy({ it.priority }, { it.id })) }

    /**
     * Hides/shows a built-in feature by stable feature ID. This changes runtime
     * availability only; it never deletes code or rewrites symbol data.
     */
    fun setBuiltInVisible(featureId: String, visible: Boolean) {
        require(featureId.matches(EXTENSION_ID))
        val changed = synchronized(lock) { builtInVisibility.put(featureId, visible) != visible }
        if (changed) notifyChanged()
    }

    fun isBuiltInVisible(featureId: String, defaultVisible: Boolean = true): Boolean =
        synchronized(lock) { builtInVisibility[featureId] ?: defaultVisible }

    fun builtInVisibilitySnapshot(): Map<String, Boolean> =
        synchronized(lock) { builtInVisibility.toMap() }

    /** Decorates a host so SymbolAction.Extension resolves registered app handlers first. */
    fun wrapActionHost(delegate: SymbolActionHost): SymbolActionHost =
        RegistryBackedSymbolActionHost(delegate, actionHandlers)

    /** Removes all runtime contributions. Call when the owning drawer/session is disposed. */
    fun clear() {
        synchronized(lock) {
            actions.clear()
            indicators.clear()
            itemViews.clear()
            groupProviders.clear()
            lifecycle.clear()
            settingsTabs.clear()
            settingsItems.clear()
            themes.clear()
            builtInVisibility.clear()
            // Keep action handlers in the same critical section as their
            // owning contributions so a concurrent register cannot be
            // published and then accidentally erased by this clear().
            actionHandlers.clear()
        }
        notifyChanged()
    }

    private companion object {
        val EXTENSION_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,255}")
    }
}

data class SymbolDrawerActionExtension(
    val id: String,
    val title: String,
    val description: String = "",
    val payloadHint: String? = null,
    val priority: Int = 0,
    val handler: SymbolActionHandler<SymbolActionHost>
) {
    init { require(title.isNotBlank()) }
}

/** Supplies original V1 groups to the existing drawer through its V2 codec boundary. */
interface SymbolDrawerLegacyGroupProvider {
    val id: String
    val title: String
    val priority: Int get() = 0
    suspend fun loadGroups(): List<com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup>
}

/** Lifecycle and interaction callbacks for extensions attached to a real drawer instance. */
interface SymbolDrawerLifecycleExtension {
    val id: String
    val priority: Int get() = 0
    fun onAttached(drawer: com.zerostudio.idea.widget.editor.symbolinput.v2.SymbolDrawerView) = Unit
    fun onSettingsChanged(settings: SymbolDrawerSettings) = Unit
    fun onGroupsChanged(groups: List<com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup>) = Unit
    fun onGroupSelected(index: Int, group: com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup?) = Unit
    fun onExpansionChanged(fraction: Float, expanded: Boolean) = Unit
    fun onSymbolInvoked(item: SymbolItem, longPress: Boolean) = Unit
    fun onDetached(drawer: com.zerostudio.idea.widget.editor.symbolinput.v2.SymbolDrawerView) = Unit
}

/** Runtime state passed to custom symbol-cell renderers. */
data class SymbolDrawerItemViewState(
    val groupIndex: Int,
    val position: Int,
    val selected: Boolean = false,
    val pressed: Boolean = false,
    val enabled: Boolean = true,
    /** Active theme tokens from the owning drawer; null means host/system theme. */
    val theme: SymbolDrawerTheme? = null
)

/**
 * Replaces the content of a cell inside the existing SymbolPageAdapter.
 * The adapter still owns click/long-press dispatch, sizing, recycling and
 * accessibility; extensions own only their content View and binding.
 */
interface SymbolDrawerItemViewExtension {
    val id: String
    val title: String
    val description: String get() = ""
    val priority: Int get() = 0
    fun accepts(item: SymbolItem): Boolean = true
    /** Opt in to long-press callbacks even when the legacy item has no longAction. */
    fun wantsLongPress(item: SymbolItem): Boolean = item.longAction != null
    fun createView(context: Context, item: SymbolItem, state: SymbolDrawerItemViewState): View
    fun bindView(view: View, item: SymbolItem, state: SymbolDrawerItemViewState)
    /** Called after this cell enters the window while this renderer owns it. */
    fun onViewAttachedToWindow(view: View, item: SymbolItem) = Unit
    /** Called before this cell leaves the window or its renderer is replaced. */
    fun onViewDetachedFromWindow(view: View, item: SymbolItem) = Unit
    /** Called whenever pressed/enabled state changes during the original touch stream. */
    fun onStateChanged(view: View, item: SymbolItem, state: SymbolDrawerItemViewState) = Unit
    /** Receives the original touch stream without intercepting drawer scrolling or paging. Do not retain the MotionEvent. */
    fun onTouch(view: View, item: SymbolItem, state: SymbolDrawerItemViewState, event: MotionEvent) = Unit
    fun onClick(view: View, item: SymbolItem, state: SymbolDrawerItemViewState) = Unit
    fun onLongPress(view: View, item: SymbolItem, state: SymbolDrawerItemViewState) = Unit
    fun onViewRecycled(view: View, item: SymbolItem?) = Unit
}

/** Stable navigation contract supplied to custom indicator components. */
data class SymbolGroupIndicatorState(
    val groups: List<LegacySymbolGroup>,
    val selectedIndex: Int,
    val onSelectGroup: (Int) -> Unit
)

/** A custom indicator owns its View but not the underlying group/page state. */
interface SymbolGroupIndicatorComponent {
    val view: View
    fun submitGroups(groups: List<LegacySymbolGroup>)
    fun setSelectedIndex(index: Int)
    fun applySettings(settings: SymbolDrawerSettings)
    /** Optional theme tokens supplied by the owning drawer session. */
    fun applyTheme(theme: SymbolDrawerTheme?) = Unit
    /** Release component-owned animators/listeners when it is replaced or removed. */
    fun onDispose() = Unit
}

/** A custom indicator is automatically discoverable from the extension registry. */
interface SymbolGroupIndicatorExtension {
    val id: String
    val title: String
    val description: String get() = ""
    val priority: Int get() = 0
    fun create(context: Context, initialState: SymbolGroupIndicatorState): SymbolGroupIndicatorComponent
}

/** Host-owned settings surface contributed as a tab in the settings screen. */
interface SymbolDrawerSettingsTabExtension {
    val id: String
    val title: String
    val priority: Int get() = 0
    fun createView(context: Context, scope: SymbolDrawerSettingsScope): View
    /** Release listeners, observers and other resources owned by the contributed settings view. */
    fun onViewDisposed(view: View) = Unit
}

/** Host-owned setting row/card that can be placed in an existing built-in tab. */
interface SymbolDrawerSettingExtension {
    val id: String
    val tabId: String
    val title: String
    val priority: Int get() = 0
    fun createView(context: Context, scope: SymbolDrawerSettingsScope): View
    /** Release listeners, observers and other resources owned by the contributed setting view. */
    fun onViewDisposed(view: View) = Unit
}

/** Read/write boundary for host-defined settings contributions. */
interface SymbolDrawerSettingsScope {
    val current: SymbolDrawerSettings
    fun update(transform: (SymbolDrawerSettings) -> SymbolDrawerSettings)
}

/** Kotlin source compatibility aliases; JVM declarations live in the public drawer API. */
typealias SymbolDrawerTheme = com.zerostudio.idea.widget.drawer.extension.SymbolDrawerTheme
typealias SymbolDrawerThemeExtension = com.zerostudio.idea.widget.drawer.extension.SymbolDrawerThemeExtension

/** Immutable point-in-time view of every extension contribution. */
data class SymbolDrawerExtensionSnapshot(
    val revision: Long,
    val actions: List<SymbolDrawerActionExtension>,
    val indicators: List<SymbolGroupIndicatorExtension>,
    val itemViews: List<SymbolDrawerItemViewExtension>,
    val groupProviders: List<SymbolDrawerLegacyGroupProvider>,
    val lifecycle: List<SymbolDrawerLifecycleExtension>,
    val settingsTabs: List<SymbolDrawerSettingsTabExtension>,
    val settingsItems: List<SymbolDrawerSettingExtension>,
    val themes: List<SymbolDrawerThemeExtension>,
    val builtInVisibility: Map<String, Boolean>
)
