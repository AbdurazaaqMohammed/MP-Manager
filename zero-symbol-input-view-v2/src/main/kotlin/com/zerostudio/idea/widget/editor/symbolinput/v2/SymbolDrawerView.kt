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

package com.zerostudio.idea.widget.editor.symbolinput.v2

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.zerostudio.idea.widget.editor.symbolinput.v2.R
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolDataMigration
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SharedPreferencesSymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolDataCodec
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.DrawerConfig
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolItem
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.HapticMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SharedPreferencesSymbolDrawerSettingsRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettingsScreen
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.ThemeMode
import com.zerostudio.idea.widget.editor.symbolinput.v2.state.DrawerGeometry
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.adapter.SymbolDrawerAnchorAdapter
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core.DrawerInteractionController
import com.zerostudio.idea.widget.editor.symbolinput.v2.ui.SymbolGroupIndicator
import com.zerostudio.idea.widget.editor.symbolinput.v2.ui.SymbolPageAdapter
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerExtensionRegistry
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolGroupIndicatorComponent
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolGroupIndicatorState
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolGroupIndicatorExtension
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerBuiltInFeatures
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionExecutor
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionCancellation
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionResult
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * A self-contained symbol drawer: vertical drag controls its height while
 * ViewPager2 owns horizontal group paging. The two axes are arbitrated before
 * the parent intercepts, so horizontal page swipes are not stolen by the drawer.
 *
 * Symbol actions are exposed as callbacks; this view has no dependency on a
 * particular editor implementation.
 */
class SymbolDrawerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private var config: DrawerConfig = DrawerConfig()
) : ViewGroup(context, attrs, defStyleAttr) {
    private companion object {
        const val SETTINGS_PREFERENCE_KEY = "settings_json_v1"
        const val EXPANSION_STATE_KEY = "remembered_expanded_state"
    }

    fun interface OnSymbolActionListener {
        fun onAction(symbol: SymbolEntry, isLongPress: Boolean)
    }

    /** Session-scoped public extension registry for custom actions, indicators, settings and themes. */
    val extensions: SymbolDrawerExtensionRegistry = SymbolDrawerExtensionRegistry()

    /** Wraps an editor-neutral host so registered SymbolAction.Extension handlers are dispatched. */
    fun bindActionHost(host: SymbolActionHost): SymbolActionHost = extensions.wrapActionHost(host)

    private val actionExecutor = SymbolActionExecutor()

    /**
     * Executes a typed action through the original V2 executor and this drawer's
     * session registry. Existing V1 numeric actions remain addressable through
     * the legacy.action.<id> compatibility route. The host remains application-
     * supplied and is never retained by this View.
     */
    fun executeAction(
        action: SymbolAction,
        host: SymbolActionHost,
        cancellation: SymbolActionCancellation = SymbolActionCancellation.NEVER
    ): SymbolActionResult = actionExecutor.execute(
        action,
        bindActionHost(host),
        cancellation
    )

    var onSymbolActionListener: OnSymbolActionListener? = null
    /** Called after a normal symbol action is dispatched; does not replace the host action callback. */
    var onSymbolUsedListener: ((SymbolEntry, Boolean) -> Unit)? = null
    var onOpenManagerListener: (() -> Unit)? = null
    /** Optional dedicated settings entry callback; falls back to the manager callback for V1 hosts. */
    var onOpenSettingsListener: (() -> Unit)? = null
    var onGroupChangedListener: ((Int, SymbolGroup?) -> Unit)? = null
    /** Receives isolated extension failures with the extension ID; host callbacks must not throw. */
    var onExtensionErrorListener: ((extensionId: String, error: Throwable) -> Unit)?
        get() = extensions.onExtensionErrorListener
        set(value) { extensions.onExtensionErrorListener = value }

    private val settingsRepository = SharedPreferencesSymbolDrawerSettingsRepository(context.applicationContext)
    private val settingsPreferences = context.applicationContext.getSharedPreferences(
        "symbol_drawer_v2_settings",
        Context.MODE_PRIVATE
    )
    private val settingsPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SETTINGS_PREFERENCE_KEY) {
            // Settings may be edited by a separate settings screen or reset/imported
            // elsewhere. Re-read the durable snapshot and update this live instance.
            applySettings(settingsRepository.load().settings)
        }
    }
    private var settingsListenerRegistered = false
    private val hasPersistedSettings = settingsRepository.hasStoredSettings()
    private var settings = settingsRepository.load().settings.copy(themeMode = ThemeMode.SYSTEM)
    private var settingsDialog: AlertDialog? = null
    internal val isSettingsDialogShowing: Boolean get() = settingsDialog?.isShowing == true
    private val indicator = SymbolGroupIndicator(context) { selectGroup(it, true) }.apply {
        onSettingsClick = { if (isBuiltInFeatureVisible(SymbolDrawerBuiltInFeatures.SETTINGS)) openSettings() }
    }
    private val indicatorHost = FrameLayout(context).apply {
        addView(indicator, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    private var customIndicatorId: String? = null
    private var activeCustomIndicatorExtension: SymbolGroupIndicatorExtension? = null
    private var customIndicatorComponent: SymbolGroupIndicatorComponent? = null
    private var activeThemeExtensionId: String? = null
    private var activeThemeExtension: com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerThemeExtension? = null
    private var activeTheme: SymbolDrawerTheme? = null
    private val themeObservers = CopyOnWriteArrayList<(SymbolDrawerTheme?) -> Unit>()
    private var publishedThemeSnapshot: SymbolDrawerTheme? = null

    /** Current custom theme tokens; null means the host/system palette is active. */
    val themeSnapshot: SymbolDrawerTheme? get() = activeTheme

    /** Observe resolved theme changes; closing the returned handle releases the observer. */
    fun observeTheme(
        emitCurrent: Boolean = true,
        observer: (SymbolDrawerTheme?) -> Unit
    ): AutoCloseable {
        themeObservers += observer
        if (emitCurrent) {
            runCatching { observer(activeTheme) }
                .onFailure { extensions.reportError("drawer.theme.observer", it) }
        }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                themeObservers.remove(observer)
            }
        }
    }
    private var extensionObserver: AutoCloseable? = null
    private val attachedLifecycleExtensions = LinkedHashMap<String, com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerLifecycleExtension>()
    private val pager = ViewPager2(context).apply {
        orientation = ViewPager2.ORIENTATION_HORIZONTAL
        offscreenPageLimit = 1
        isUserInputEnabled = true
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    private var legacyItemsBySymbolId: Map<Long, SymbolItem> = emptyMap()
    private var preserveLegacyMappingOnSubmit = false
    private var pageAdapter = createPageAdapter()
    private var suppressPageCallbacks = false
    private var adapterSwapGeneration = 0

    private var repository: SymbolGroupRepository =
        SharedPreferencesSymbolGroupRepository(context.applicationContext)
    private var repositoryObserver: AutoCloseable? = null
    private var groups: List<SymbolGroup> = emptyList()
    private var collapsedHeight = DrawerGeometry.collapsedHeightPx(config, resources.displayMetrics.density)
    private var expandedGridHeight = collapsedHeight
    private var expandedHeight = collapsedHeight
    private var drawerHeight = collapsedHeight
    private var pagerHeight = collapsedHeight
    private var expansion = 0f
    /**
     * The universal drawer core owns gesture arbitration, velocity tracking,
     * continuous drag state and settling; the feature retains its exact layout.
     */
    private val interactionController = DrawerInteractionController(
        context = context,
        onHeightChanged = { height, _, _ -> setExpansionFromHeight(height) },
        onDisallowInterceptChanged = ::setParentIntercept
    )

    init {
        indicator.setSettingsButtonVisible(isBuiltInFeatureVisible(SymbolDrawerBuiltInFeatures.SETTINGS))
        clipChildren = false
        clipToPadding = false
        addView(indicatorHost, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, collapsedHeight))
        pager.adapter = pageAdapter
        // Load the durable settings snapshot before the first visible layout.
        // Rebuild the adapter once below so grid geometry and interactions use
        // the same persisted values as the settings dialog.
        if (hasPersistedSettings) config = settings.toDrawerConfig()
        LegacySymbolDataMigration.importIfV2Empty(context, repository)
        if (repository.loadGroups().isEmpty()) {
            repository.saveGroups(com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolDrawerDefaults.createGroups(context))
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (suppressPageCallbacks) return
                setIndicatorSelectedIndex(position)
                onGroupChangedListener?.invoke(position, groups.getOrNull(position))
                val legacyGroup = legacyGroupAt(position)
                extensions.lifecycleExtensions().forEach { extension ->
                    runCatching { extension.onGroupSelected(position, legacyGroup) }.onFailure { extensions.reportError(extension.id, it) }
                }
            }
        })
        reloadGroups()
        if (hasPersistedSettings) applySettings(settings)
        updateVisualState(0f)
    }

    /**
     * Applies a persisted settings snapshot to the live drawer. Rebuilds only the
     * lightweight page adapter when grid geometry changes; group data is retained.
     */
    fun applySettings(value: SymbolDrawerSettings) {
        val previousSettings = settings
        val previousConfig = config
        settings = value.copy(themeMode = com.zerostudio.idea.widget.editor.symbolinput.v2.settings.ThemeMode.SYSTEM)
        if (!settings.rememberExpandedState) settingsPreferences.edit().remove(EXPANSION_STATE_KEY).apply()
        config = settings.toDrawerConfig()
        applyDrawerTheme()
        extensions.lifecycleExtensions().forEach { extension ->
            runCatching { extension.onSettingsChanged(settings) }.onFailure { extensions.reportError(extension.id, it) }
        }

        // Anchor geometry (especially the user-configured collapsed row count)
        // belongs to the drawer shell, not to symbol-cell presentation. Apply it
        // synchronously so a settings slider updates the visible drawer without
        // waiting for an asynchronous ListAdapter diff to finish.
        recalculateGeometry()
        applyIndicatorSettings(settings)
        pager.offscreenPageLimit = if (settings.prefetchAdjacentGroups) settings.pageCacheLimit else 1

        // Most settings (haptics, animation, auto-collapse, cache policy) do
        // not alter cell construction. Recreating ViewPager2's adapter for
        // those changes caused unnecessary page teardown/rebind work and could
        // make rapid settings adjustments appear frozen.
        val adapterPresentationChanged =
            previousSettings.showSymbolLabels != settings.showSymbolLabels ||
            previousSettings.showFullSymbolLabels != settings.showFullSymbolLabels ||
            previousSettings.symbolLabelTextSizeSp != settings.symbolLabelTextSizeSp ||
            previousSettings.showActionHints != settings.showActionHints ||
            previousSettings.longPressTimeoutMs != settings.longPressTimeoutMs
        val gridGeometryChanged =
            previousConfig.columns != config.columns ||
            previousConfig.cellHeightDp != config.cellHeightDp ||
            previousConfig.cellHorizontalSpacingDp != config.cellHorizontalSpacingDp ||
            previousConfig.cellVerticalSpacingDp != config.cellVerticalSpacingDp ||
            previousConfig.indicatorHeightDp != config.indicatorHeightDp ||
            previousConfig.maxExpandedHeightDp != config.maxExpandedHeightDp ||
            previousConfig.minimumTouchTargetDp != config.minimumTouchTargetDp
        if (!adapterPresentationChanged && !gridGeometryChanged) {
            // Motion thresholds and duration still update the live controller,
            // but they do not require recycling every symbol cell.
            recalculateGeometry()
            return
        }

        // Keep the current adapter attached until the replacement has a full
        // data snapshot, then restore the selected group atomically.
        val replacement = createPageAdapter()
        val generation = ++adapterSwapGeneration
        replacement.submitList(groups.toList()) {
            if (generation != adapterSwapGeneration) return@submitList

            val selectedGroupId = groups.getOrNull(pager.currentItem)?.id
            val restored = selectedGroupId?.let { id ->
                groups.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            } ?: pager.currentItem.coerceIn(0, (groups.size - 1).coerceAtLeast(0))

            suppressPageCallbacks = true
            pageAdapter = replacement
            pager.adapter = replacement
            if (groups.isNotEmpty()) pager.setCurrentItem(restored, false)
            suppressPageCallbacks = false

            setIndicatorSelectedIndex(restored)
            onGroupChangedListener?.invoke(restored, groups.getOrNull(restored))
            if (gridGeometryChanged) recalculateGeometry()
        }
    }

    private fun openSettings() {
        onOpenSettingsListener?.let {
            it.invoke()
            return
        }
        val screen = SymbolDrawerSettingsScreen(
            context,
            settingsRepository,
            repository,
            extensionRegistry = extensions
        ).apply {
            bindThemeSource(this@SymbolDrawerView)
            onSettingsChanged = { applySettings(it) }
            onGroupsChanged = {
                submitGroups(it)
                saveGroups()
            }
        }
        settingsDialog?.dismiss()
        settingsDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.symbol_drawer_185))
            .setView(screen)
            .setPositiveButton(context.getString(R.string.symbol_drawer_075), null)
            .create()
            .also { it.show() }
    }


    /**
     * Activates a registered custom group indicator. The component receives
     * group snapshots and selection callbacks, while ViewPager2 remains the
     * single owner of actual page navigation.
     *
     * Returns false when the ID is not registered or component creation fails.
     */
    fun setCustomGroupIndicator(extensionId: String): Boolean {
        val extension = extensions.indicators().firstOrNull { it.id == extensionId } ?: return false
        val component = runCatching {
            extension.create(
                context,
                SymbolGroupIndicatorState(legacyGroupsSnapshot(), pager.currentItem) { index -> selectGroup(index, true) }
            )
        }.onFailure { extensions.reportError(extension.id, it) }.getOrElse { return false }

        // Fully initialize the new component before disturbing the currently
        // visible indicator. A broken extension must not blank the original UI.
        val initialized = runCatching {
            component.submitGroups(legacyGroupsSnapshot())
            component.setSelectedIndex(pager.currentItem)
            component.applySettings(settings)
            component.applyTheme(activeTheme)
        }
        if (initialized.isFailure) {
            extensions.reportError(extension.id, initialized.exceptionOrNull()!!)
            runCatching { component.onDispose() }
                .onFailure { extensions.reportError(extension.id, it) }
            return false
        }

        val previousComponent = customIndicatorComponent
        val previousExtension = activeCustomIndicatorExtension
        val previousView = previousComponent?.view ?: indicator
        val replacement = runCatching {
            indicatorHost.removeView(previousView)
            indicatorHost.addView(
                component.view,
                FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
        }
        if (replacement.isFailure) {
            val error = replacement.exceptionOrNull()!!
            extensions.reportError(extension.id, error)
            runCatching { indicatorHost.removeView(component.view) }
            runCatching {
                if (previousView.parent == null) {
                    indicatorHost.addView(
                        previousView,
                        FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    )
                }
            }.onFailure { extensions.reportError(extension.id, it) }
            runCatching { component.onDispose() }
                .onFailure { extensions.reportError(extension.id, it) }
            return false
        }

        customIndicatorId = extensionId
        activeCustomIndicatorExtension = extension
        customIndicatorComponent = component
        previousComponent?.let { previous ->
            runCatching { previous.onDispose() }
                .onFailure { error ->
                    previousExtension?.let { extensions.reportError(it.id, error) }
                }
        }
        requestLayout()
        invalidate()
        return true
    }

    /** Restores the built-in indicator without changing the current group/page. */
    fun useBuiltInGroupIndicator() {
        val previousComponent = customIndicatorComponent
        val previousExtension = activeCustomIndicatorExtension
        val previousView = previousComponent?.view
        val restored = runCatching {
            indicatorHost.removeAllViews()
            indicatorHost.addView(
                indicator,
                FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
            indicator.applySettings(settings)
            indicator.submitGroups(groups)
            indicator.setSelectedIndex(pager.currentItem)
        }
        if (restored.isFailure) {
            previousExtension?.let { extensions.reportError(it.id, restored.exceptionOrNull()!!) }
            return
        }
        customIndicatorId = null
        activeCustomIndicatorExtension = null
        customIndicatorComponent = null
        previousComponent?.let { component ->
            runCatching { component.onDispose() }
                .onFailure { error -> previousExtension?.let { extensions.reportError(it.id, error) } }
        }
        requestLayout()
        invalidate()
    }

    private inline fun updateCustomIndicator(
        operation: String,
        update: (SymbolGroupIndicatorComponent) -> Unit
    ) {
        val component = customIndicatorComponent ?: return
        val extension = activeCustomIndicatorExtension ?: return
        runCatching { update(component) }.onFailure { error ->
            extensions.reportError(extension.id, IllegalStateException("Indicator callback failed: $operation", error))
            useBuiltInGroupIndicator()
        }
    }

    private fun setIndicatorSelectedIndex(index: Int) {
        if (customIndicatorComponent != null) {
            updateCustomIndicator("setSelectedIndex") { it.setSelectedIndex(index) }
        } else {
            indicator.setSelectedIndex(index)
        }
    }

    private fun submitIndicatorGroups(value: List<SymbolGroup>) {
        if (customIndicatorComponent != null) {
            val snapshot = legacyGroupsSnapshot()
            updateCustomIndicator("submitGroups") { it.submitGroups(snapshot) }
        } else {
            indicator.submitGroups(value)
        }
    }

    private fun applyIndicatorSettings(value: SymbolDrawerSettings) {
        if (customIndicatorComponent != null) {
            updateCustomIndicator("applySettings") { it.applySettings(value) }
        } else {
            indicator.applySettings(value)
        }
    }

    /** Selects a registered theme provider for the drawer shell and group indicator. */
    fun setThemeExtension(extensionId: String?): Boolean {
        if (extensionId == null) {
            activeThemeExtensionId = null
            activeThemeExtension = null
            activeTheme = null
            applyDrawerTheme()
            return true
        }
        val extension = extensions.themes().firstOrNull { it.id == extensionId } ?: return false
        val resolved = runCatching { extension.resolve(context, settings) }.onFailure { extensions.reportError(extension.id, it) }.getOrElse { return false }
        activeThemeExtensionId = extensionId
        activeThemeExtension = extension
        activeTheme = resolved
        applyDrawerTheme()
        return true
    }

    private fun applyDrawerTheme() {
        val selectedId = activeThemeExtensionId
        if (selectedId != null) {
            val extension = extensions.themes().firstOrNull { it.id == selectedId }
            if (extension == null) {
                activeThemeExtensionId = null
                activeThemeExtension = null
                activeTheme = null
            } else {
                activeThemeExtension = extension
                activeTheme = runCatching { extension.resolve(context, settings) }.onFailure { extensions.reportError(extension.id, it) }.getOrNull()
            }
        }
        val theme = activeTheme
        val background = theme?.drawerBackground
            ?: com.zerostudio.idea.widget.editor.symbolinput.v2.ui.HostThemeColors.surface(context)
        setBackgroundColor(background)
        indicatorHost.setBackgroundColor(background)
        indicator.applyTheme(theme)
        if (customIndicatorComponent != null) {
            updateCustomIndicator("applyTheme") { it.applyTheme(theme) }
        }
        // Rebind existing custom cells so their state receives the new theme
        // without replacing the page adapter or disturbing the current page.
        pageAdapter.refreshItemExtensions()
        // Theme refreshes can be triggered by host configuration callbacks as
        // well as extension registration. Publish only an actual token change so
        // bound manager/settings surfaces do not render redundantly.
        if (publishedThemeSnapshot != theme) {
            publishedThemeSnapshot = theme
            themeObservers.forEach { observer ->
                runCatching { observer(theme) }
                    .onFailure { extensions.reportError("drawer.theme.observer", it) }
            }
        }
    }

    /** Query runtime visibility for a built-in entry owned by this drawer session. */
    fun isBuiltInFeatureVisible(featureId: String, defaultVisible: Boolean = true): Boolean =
        extensions.isBuiltInVisible(featureId, defaultVisible)

    /**
     * Changes visibility of an existing built-in drawer feature at runtime.
     *
     * This is a presentation/availability switch only: the built-in implementation,
     * symbol data and persisted settings are left untouched. Registry observers are
     * notified so other built-in surfaces can update from the same source of truth.
     */
    fun setBuiltInFeatureVisible(featureId: String, visible: Boolean) {
        extensions.setBuiltInVisible(featureId, visible)
        if (featureId == SymbolDrawerBuiltInFeatures.SETTINGS) {
            indicator.setSettingsButtonVisible(visible)
        }
    }

    fun getSettings(): SymbolDrawerSettings = settings

    /** Refresh palette after the host changes its theme without recreating its Activity. */
    fun refreshHostTheme() {
        applyDrawerTheme()
        indicator.setSettingsButtonVisible(isBuiltInFeatureVisible(SymbolDrawerBuiltInFeatures.SETTINGS))
        applyIndicatorSettings(settings)
        applySettings(settings)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pageAdapter.refreshItemExtensions()
        if (!settingsListenerRegistered) {
            settingsPreferences.registerOnSharedPreferenceChangeListener(settingsPreferenceListener)
            settingsListenerRegistered = true
        }
        // Catch changes made while this view was detached.
        val latest = settingsRepository.load().settings
        if (latest != settings) applySettings(latest)
        indicator.setSettingsButtonVisible(isBuiltInFeatureVisible(SymbolDrawerBuiltInFeatures.SETTINGS))
        activeThemeExtensionId?.let { themeId ->
            if (extensions.themes().none { it.id == themeId }) setThemeExtension(null)
            else applyDrawerTheme()
        }
        customIndicatorId?.let { selectedId ->
            val registered = extensions.indicators().firstOrNull { it.id == selectedId }
            when {
                registered == null -> useBuiltInGroupIndicator()
                registered !== activeCustomIndicatorExtension -> setCustomGroupIndicator(selectedId)
            }
        }
        syncLifecycleExtensions(extensions.lifecycleExtensions())
        if (extensionObserver == null) {
            extensionObserver = extensions.observe(emitCurrent = false) { snapshot ->
                post {
                    if (!isAttachedToWindow) return@post
                    pageAdapter.refreshItemExtensions()
                    syncLifecycleExtensions(snapshot.lifecycle)
                    indicator.setSettingsButtonVisible(snapshot.builtInVisibility[SymbolDrawerBuiltInFeatures.SETTINGS] ?: true)
                    activeThemeExtensionId?.let { themeId ->
                        val updatedTheme = snapshot.themes.firstOrNull { it.id == themeId }
                        when {
                            updatedTheme == null -> setThemeExtension(null)
                            updatedTheme !== activeThemeExtension -> setThemeExtension(themeId)
                        }
                    }
                    val selectedId = customIndicatorId ?: return@post
                    val updated = snapshot.indicators.firstOrNull { it.id == selectedId }
                    when {
                        updated == null -> useBuiltInGroupIndicator()
                        updated !== activeCustomIndicatorExtension -> setCustomGroupIndicator(selectedId)
                    }
                }
            }
        }
        observeRepositoryChanges()
        interactionController.onAttached()
        recalculateGeometry()
        if (settings.rememberExpandedState) {
            val remembered = settingsPreferences.getBoolean(EXPANSION_STATE_KEY, false)
            post { animateTo(if (remembered) 1f else 0f, animated = false) }
        } else {
            settingsPreferences.edit().remove(EXPANSION_STATE_KEY).apply()
        }
    }

    override fun onDetachedFromWindow() {
        if (settings.rememberExpandedState) {
            settingsPreferences.edit()
                .putBoolean(EXPANSION_STATE_KEY, expansion >= 0.5f)
                .apply()
        }
        if (settingsListenerRegistered) {
            settingsPreferences.unregisterOnSharedPreferenceChangeListener(settingsPreferenceListener)
            settingsListenerRegistered = false
        }
        settingsDialog?.dismiss()
        settingsDialog = null
        attachedLifecycleExtensions.values.toList().asReversed().forEach { extension ->
            runCatching { extension.onDetached(this@SymbolDrawerView) }
                .onFailure { extensions.reportError(extension.id, it) }
        }
        attachedLifecycleExtensions.clear()
        extensionObserver?.close()
        extensionObserver = null
        repositoryObserver?.close()
        repositoryObserver = null
        interactionController.onDetached()
        super.onDetachedFromWindow()
    }

    private fun syncLifecycleExtensions(
        current: List<com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerLifecycleExtension>
    ) {
        // A contribution can be replaced under the same ID. Compare by object
        // identity, not equals(): data-class implementations may compare equal
        // even though their captured resources and callbacks are different.
        val wantedById = current.associateBy { it.id }
        attachedLifecycleExtensions.entries
            .filter { (id, attached) -> wantedById[id] !== attached }
            .toList()
            .asReversed()
            .forEach { (id, removed) ->
                attachedLifecycleExtensions.remove(id)
                runCatching { removed.onDetached(this) }
                    .onFailure { extensions.reportError(removed.id, it) }
            }
        current.forEach { extension ->
            if (attachedLifecycleExtensions[extension.id] !== extension) {
                attachedLifecycleExtensions[extension.id] = extension
                runCatching { extension.onAttached(this) }
                    .onFailure { extensions.reportError(extension.id, it) }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshHostTheme()
    }

    private fun createPageAdapter() = SymbolPageAdapter(
        config = config,
        settings = settings,
        extensions = extensions,
        legacyItemResolver = ::resolveLegacyItem,
        canScrollGridVertically = { expansion >= 0.999f },
        themeProvider = { activeTheme },
        onSymbolAction = { symbol, longPress ->
        val action = if (longPress) symbol.longAction else symbol.shortAction
        if (action != null) {
            if (action == SymbolAction.OpenManager) {
                if (isBuiltInFeatureVisible(SymbolDrawerBuiltInFeatures.MANAGER)) onOpenManagerListener?.invoke()
            } else {
                when (settings.hapticMode) {
                    HapticMode.OFF -> Unit
                    HapticMode.SELECTION -> if (!longPress) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    HapticMode.ALL -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                extensions.lifecycleExtensions().forEach { extension ->
                    runCatching { extension.onSymbolInvoked(resolveLegacyItem(symbol), longPress) }.onFailure { extensions.reportError(extension.id, it) }
                }
                onSymbolActionListener?.onAction(symbol, longPress)
                onSymbolUsedListener?.invoke(symbol, longPress)
                if (settings.autoCollapseAfterAction) setExpanded(false, true)
            }
        }
    }
    )

    /**
     * Submits original V1 records through the existing V2 repository/rendering
     * pipeline while retaining their exact legacy action IDs and text payloads
     * for public extension callbacks.
     */
    fun submitLegacyGroups(
        value: List<com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup>,
        preserveSelectedGroup: Boolean = true,
        onCommitted: (() -> Unit)? = null
    ) {
        val converted = LegacySymbolDataCodec.decode(
            com.google.gson.Gson().toJson(value)
        )
        legacyItemsBySymbolId = buildMap {
            var index = 0L
            value.forEach { group ->
                group.items.forEach { item -> put(++index, item.copy()) }
            }
        }
        preserveLegacyMappingOnSubmit = true
        try {
            submitGroups(converted, preserveSelectedGroup, onCommitted)
        } finally {
            preserveLegacyMappingOnSubmit = false
        }
    }

    /** Exact legacy record when submitted from the legacy API; safe V1 projection otherwise. */
    private fun resolveLegacyItem(entry: SymbolEntry): SymbolItem =
        legacyItemsBySymbolId[entry.id]?.copy() ?: runCatching {
            // Reuse the established V1 codec to project representable typed V2
            // actions into the exact compact legacy SymbolItem contract.
            val projection = SymbolGroup(
                id = "legacy-api-projection",
                title = "legacy-api-projection",
                symbols = listOf(entry)
            )
            val json = LegacySymbolDataCodec.encode(listOf(projection))
            val groups = com.google.gson.Gson().fromJson(
                json,
                Array<com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup>::class.java
            )
            groups.single().items.single()
        }.getOrElse {
            // New V2-only action types have no V1 representation. Preserve the
            // original V1 metadata if present and expose the label unchanged.
            SymbolItem(
                shortAction = entry.legacyShortActionId ?: 0,
                display = entry.label,
                shortText = entry.legacyShortText,
                longAction = entry.legacyLongActionId,
                longText = entry.legacyLongText
            )
        }

    private fun legacyGroupAt(index: Int): com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup? {
        val group = groups.getOrNull(index) ?: return null
        return com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup(
            name = group.title,
            items = group.symbols.map(::resolveLegacyItem).toMutableList()
        )
    }

    private fun legacyGroupsSnapshot(): List<com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup> =
        groups.indices.mapNotNull(::legacyGroupAt)

    /** Loads extension-provided legacy groups in deterministic priority/ID order. */
    suspend fun reloadLegacyGroupsFromExtensions(
        persist: Boolean = false
    ): List<com.zerostudio.idea.widget.editor.symbolinput.v2.model.legacy.SymbolGroup> {
        val providers = extensions.groupProviders()
        if (providers.isEmpty()) return emptyList()
        val supplied = buildList {
            providers.forEach { provider ->
                val result = runCatching { provider.loadGroups() }
                result.onSuccess(::addAll).onFailure { error ->
                    extensions.reportError(provider.id, error)
                }
            }
        }
        if (supplied.isEmpty()) return emptyList()
        submitLegacyGroups(supplied)
        if (persist) saveGroups()
        return supplied
    }

    private fun observeRepositoryChanges() {
        if (repositoryObserver != null) return
        val observable = repository as? com.zerostudio.idea.widget.editor.symbolinput.v2.data.ObservableSymbolGroupRepository
            ?: return
        repositoryObserver = observable.observeGroups {
            post {
                if (isAttachedToWindow && repository === observable) {
                    runCatching { reloadGroups() }
                        .onFailure { extensions.reportError("repository.groups", it) }
                }
            }
        }
    }

    fun setRepository(value: SymbolGroupRepository, reload: Boolean = true) {
        if (repository !== value) {
            repositoryObserver?.close()
            repositoryObserver = null
            repository = value
        }
        if (isAttachedToWindow) observeRepositoryChanges()
        if (reload) reloadGroups()
    }

    fun submitGroups(
        value: List<SymbolGroup>,
        preserveSelectedGroup: Boolean = true,
        onCommitted: (() -> Unit)? = null
    ) {
        if (!preserveLegacyMappingOnSubmit) legacyItemsBySymbolId = emptyMap()
        require(value.map { it.id }.distinct().size == value.size) {
            "Symbol group IDs must be unique"
        }
        value.forEach { group ->
            require(group.symbols.map { it.id }.distinct().size == group.symbols.size) {
                "Symbol IDs must be unique within group ${group.id}"
            }
        }
        val previousId = groups.getOrNull(pager.currentItem)?.id
        groups = value.map { it.copy(symbols = it.symbols.toList()) }
        val legacySnapshot = groups.mapIndexedNotNull { index, _ -> legacyGroupAt(index) }
        extensions.lifecycleExtensions().forEach { extension ->
            runCatching { extension.onGroupsChanged(legacySnapshot) }.onFailure { extensions.reportError(extension.id, it) }
        }
        val restored = if (preserveSelectedGroup && previousId != null) {
            groups.indexOfFirst { it.id == previousId }.takeIf { it >= 0 }
        } else null
        val target = restored ?: pager.currentItem.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
        submitIndicatorGroups(groups)
        pageAdapter.submitList(groups) {
            if (groups.isNotEmpty() && target in groups.indices) {
                pager.setCurrentItem(target, false)
            }
            onCommitted?.invoke()
        }
        recalculateGeometry()
    }

    fun reloadGroups() {
        submitGroups(repository.loadGroups())
    }

    fun saveGroups() {
        repository.saveGroups(groups)
    }

    fun getGroups(): List<SymbolGroup> = groups.toList()
    fun getCurrentGroupIndex(): Int = pager.currentItem
    fun isExpanded(): Boolean = expansion >= 0.5f

    fun selectGroup(index: Int, smoothScroll: Boolean = true) {
        if (index !in groups.indices) return
        pager.setCurrentItem(index, smoothScroll)
    }

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        animateTo(if (expanded) 1f else 0f, animate)
        if (settings.rememberExpandedState) {
            settingsPreferences.edit().putBoolean(EXPANSION_STATE_KEY, expanded).apply()
        }
    }

    fun onHostResume() {
        // Kept explicit for hosts that want to refresh after returning from a manager.
        reloadGroups()
    }

    override fun onSaveInstanceState(): Parcelable {
        if (settings.rememberExpandedState) {
            settingsPreferences.edit()
                .putBoolean(EXPANSION_STATE_KEY, expansion >= 0.5f)
                .apply()
        }
        return DrawerSavedState(super.onSaveInstanceState()).apply {
            selectedGroupId = groups.getOrNull(pager.currentItem)?.id
            expansion = this@SymbolDrawerView.expansion
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is DrawerSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        pageAdapter.submitList(groups) {
            val selectedIndex = state.selectedGroupId?.let { id ->
                groups.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            }
            if (selectedIndex != null) pager.setCurrentItem(selectedIndex, false)
            animateTo(state.expansion, animated = false)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        LayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams =
        LayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams): Boolean = params is LayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val indicatorHeight = indicatorHost.layoutParams.height.coerceAtLeast(0)
        indicatorHost.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(indicatorHeight, MeasureSpec.EXACTLY)
        )
        pager.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(pagerHeight, MeasureSpec.EXACTLY)
        )
        val desiredHeight = indicatorHost.measuredHeight + pager.measuredHeight
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, resolvedHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val indicatorHeight = indicatorHost.measuredHeight
        indicatorHost.layout(0, 0, width, indicatorHeight)
        pager.layout(0, indicatorHeight, width, indicatorHeight + pager.measuredHeight)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val intercepted = interactionController.onInterceptTouchEvent(event) { x, y ->
            canScrollableDescendantScrollVertically(-1, x, y)
        }
        if (interactionController.isHorizontalGestureLocked) {
            // A vertical grid inside a ViewPager2 page may have propagated
            // requestDisallowInterceptTouchEvent(true) to ViewPager2's internal
            // RecyclerView before the horizontal axis was known. Clear that
            // nested lock once a horizontal swipe is recognized so the pager
            // can take over and perform its normal page snap.
            // A symbol grid is itself a RecyclerView nested inside the
            // ViewPager2 RecyclerView. Its disallow-intercept request marks
            // BOTH ancestor ViewGroups as disallowing interception. Clear both
            // levels after the drawer has positively classified the gesture as
            // horizontal, otherwise the pager's RecyclerView never receives
            // the MOVE sequence needed to settle on the next group.
            (pager.getChildAt(0) as? ViewGroup)
                ?.requestDisallowInterceptTouchEvent(false)
            pager.requestDisallowInterceptTouchEvent(false)
        }
        return intercepted
    }

    /**
     * Finds a vertically scrollable descendant under the active pointer.
     * A downward gesture should scroll that content to its start before the
     * drawer itself begins collapsing.
     */
    private fun canScrollableDescendantScrollVertically(
        direction: Int,
        x: Float,
        y: Float
    ): Boolean {
        fun scan(view: View, localX: Float, localY: Float): Boolean {
            if (view !is ViewGroup) return view.canScrollVertically(direction)
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                if (child.visibility != View.VISIBLE || child.width <= 0 || child.height <= 0) continue
                val childX = localX + view.scrollX - child.left
                val childY = localY + view.scrollY - child.top
                if (childX >= 0f && childX < child.width &&
                    childY >= 0f && childY < child.height &&
                    scan(child, childX, childY)
                ) return true
            }
            return view.canScrollVertically(direction)
        }
        return scan(this, x, y)
    }

    /**
     * RecyclerView/ViewPager2 may request that ancestors do not intercept.
     * Ignore that request until this container has identified the gesture axis;
     * otherwise a child can accidentally disable the drawer's vertical drag.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Forward the child's request to ancestors without setting this
        // ViewGroup's own FLAG_DISALLOW_INTERCEPT. Setting that flag here
        // prevents SymbolDrawerView from intercepting the next vertical drag.
        if (interactionController.isGestureAxisLocked) {
            parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        interactionController.onTouchEvent(event)


    private fun setParentIntercept(disallowIntercept: Boolean) {
        // Only ask ancestors to leave this gesture alone. Never call
        // super.requestDisallowInterceptTouchEvent() on this ViewGroup: that
        // toggles its own child-interception lock and breaks drawer dragging.
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun recalculateGeometry() {
        val oldFraction = expansion
        // Resolve the symbol-specific geometry through the public Drawer anchor
        // contract. The adapter deliberately preserves the established pixel
        // formulas; this is a policy migration, not a visual/layout redesign.
        val resolved = SymbolDrawerAnchorAdapter.resolve(
            groups = groups,
            config = config,
            density = resources.displayMetrics.density
        )
        collapsedHeight = resolved.collapsedHeightPx
        expandedGridHeight = resolved.expandedGridHeightPx
        expandedHeight = resolved.expandedHeightPx
        val targetHeight = (collapsedHeight + (expandedHeight - collapsedHeight) * oldFraction).roundToInt()
        interactionController.setBounds(
            minimumHeightPx = collapsedHeight,
            maximumHeightPx = expandedHeight,
            currentHeightPx = targetHeight,
            animationDurationMs = config.animationDurationMs.toLong(),
            flingVelocityDpPerSecond = config.flingVelocityDpPerSecond.toFloat()
        )
        setExpansionFromHeight(targetHeight)
    }

    private fun setExpansionFromHeight(height: Int) {
        drawerHeight = height.coerceIn(collapsedHeight, expandedHeight)
        val previousExpansion = expansion
        expansion = (drawerHeight - collapsedHeight).toFloat() /
            (expandedHeight - collapsedHeight).coerceAtLeast(1)
        if (kotlin.math.abs(previousExpansion - expansion) > 0.0005f) {
            extensions.lifecycleExtensions().forEach { extension ->
                runCatching { extension.onExpansionChanged(expansion, expansion >= 0.999f) }.onFailure { extensions.reportError(extension.id, it) }
            }
        }
        pagerHeight = (
            collapsedHeight + (expandedGridHeight - collapsedHeight) * expansion
        ).roundToInt().coerceAtLeast(collapsedHeight)
        updateVisualState(expansion)
        requestLayout()
    }

    private fun updateVisualState(fraction: Float) {
        val reveal = DrawerGeometry.indicatorReveal(fraction)
        val indicatorHeight = (config.indicatorHeightDp * resources.displayMetrics.density * reveal).roundToInt()
        val params = indicatorHost.layoutParams
        if (params.height != indicatorHeight) {
            params.height = indicatorHeight
            indicatorHost.layoutParams = params
        }
        indicatorHost.alpha = reveal
        indicatorHost.translationY = -((1f - reveal) * config.indicatorHeightDp *
            resources.displayMetrics.density * 0.2f)
        indicatorHost.visibility = if (indicatorHeight == 0) View.INVISIBLE else View.VISIBLE
    }

    internal class DrawerSavedState : View.BaseSavedState {
        var selectedGroupId: String? = null
        var expansion: Float = 0f

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) {
            selectedGroupId = source.readString()
            expansion = source.readFloat().coerceIn(0f, 1f)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(selectedGroupId)
            out.writeFloat(expansion)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<DrawerSavedState> =
                object : Parcelable.Creator<DrawerSavedState> {
                    override fun createFromParcel(source: Parcel) = DrawerSavedState(source)
                    override fun newArray(size: Int): Array<DrawerSavedState?> = arrayOfNulls(size)
                }
        }
    }

    private fun animateTo(target: Float, animated: Boolean) {
        val destination = target.coerceIn(0f, 1f)
        val targetHeight = (collapsedHeight + (expandedHeight - collapsedHeight) * destination).roundToInt()
        interactionController.settleToHeight(targetHeight, animated)
    }
}


/** @deprecated Use [SymbolDrawerView]. */
@Deprecated("Use SymbolDrawerView")
typealias AdvancedSymbolDrawerView = SymbolDrawerView
