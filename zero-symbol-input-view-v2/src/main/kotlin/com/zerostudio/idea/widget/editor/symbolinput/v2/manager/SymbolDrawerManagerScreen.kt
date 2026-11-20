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

package com.zerostudio.idea.widget.editor.symbolinput.v2.manager

import com.zerostudio.idea.widget.editor.symbolinput.v2.R

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SharedPreferencesSymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolGroupBundleCodec
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolDataCodec
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolGroupManager
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolDataMigration
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolDrawerDefaults
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.ui.HostThemeColors
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolEntry
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.LegacySymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolActionAdapter
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolActionCatalog
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.MpManagerFindReplaceEnvelope
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.BuiltinSymbolActionCatalog
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.BuiltinSymbolActionOption
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolAction
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SharedPreferencesSymbolDrawerSettingsRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowAdaptiveInfo
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.AdaptiveContentLayoutResolver
import com.zerostudio.idea.widget.editor.symbolinput.v2.SymbolDrawerView
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerActionExtension
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerExtensionRegistry
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerBuiltInFeatures
import com.google.android.material.tabs.TabLayout
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerPage
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.navigation.DrawerPageHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.panel.DrawerPanelContent

private const val SETTINGS_PREFERENCE_KEY = "settings_json_v1"

private class SymbolHolder(val root: LinearLayout, val title: TextView, val detail: TextView, val dragHandle: TextView) : RecyclerView.ViewHolder(root)

private data class SymbolActionOption(
    val label: String,
    val legacyId: Int? = null,
    val builtin: BuiltinSymbolActionOption? = null,
    val keepCurrent: Boolean = false,
    val none: Boolean = false,
    val extension: SymbolDrawerActionExtension? = null,
    val legacyPayloadHint: String? = null
) {
    val hasPayload: Boolean
        get() = extension != null || (builtin?.hasPayload ?: (legacyId?.let(LegacySymbolActionCatalog::hasTextPayload) == true))

    val payloadHint: String?
        get() = extension?.payloadHint ?: builtin?.payloadHint ?: legacyPayloadHint
}

/**
 * Replacement text plus the independent regex/match-case/replace-all option
 * flags of the MP Manager V1 find/replace action (101). One instance exists
 * per action slot so the short and long editors stay fully independent.
 */
private class FindReplaceEditor(
    val container: LinearLayout,
    val replacementField: TextInputEditText,
    private val regexToggle: MaterialCheckBox,
    private val matchCaseToggle: MaterialCheckBox,
    private val replaceAllToggle: MaterialCheckBox
) {
    fun setVisible(visible: Boolean) {
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Prefills from a typed envelope; null falls back to the V1 host defaults (regex + match case). */
    fun bind(payload: MpManagerFindReplaceEnvelope.Payload?) {
        replacementField.setText(payload?.replacementText.orEmpty())
        val options = MpManagerFindReplaceEnvelope.optionsOrDefault(payload?.options)
        regexToggle.isChecked = MpManagerFindReplaceEnvelope.hasFlag(options, MpManagerFindReplaceEnvelope.FLAG_REGEX)
        matchCaseToggle.isChecked = MpManagerFindReplaceEnvelope.hasFlag(options, MpManagerFindReplaceEnvelope.FLAG_MATCH_CASE)
        replaceAllToggle.isChecked = MpManagerFindReplaceEnvelope.hasFlag(options, MpManagerFindReplaceEnvelope.FLAG_REPLACE_ALL)
    }

    fun currentOptions(): String = MpManagerFindReplaceEnvelope.encodeOptions(
        regexToggle.isChecked,
        matchCaseToggle.isChecked,
        replaceAllToggle.isChecked
    )
}

/**
 * Caps an editor form to the available app-window height. AlertDialog can then
 * keep its normal wrap-content behavior for short forms while long forms
 * become independently scrollable instead of extending beyond split-screen
 * or compact landscape bounds.
 */
internal class CappedScrollView(
    context: Context,
    private val maxHeightPx: Int
) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentLimit = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = minOf(maxHeightPx.coerceAtLeast(1), parentLimit.takeIf { it > 0 } ?: maxHeightPx)
        val cappedHeight = View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, cappedHeight)
    }
}

/**
 * Keeps the editor form strictly inside the app window even when the window is
 * shorter than the normal dialog chrome reservation (compact landscape and
 * heavily constrained split-screen windows).
 */
internal fun resolveEditorFormMaxHeightPx(
    windowHeightPx: Int,
    reservedHeightPx: Int
): Int {
    require(windowHeightPx >= 0) { "windowHeightPx must not be negative" }
    require(reservedHeightPx >= 0) { "reservedHeightPx must not be negative" }
    return (windowHeightPx - reservedHeightPx).coerceAtLeast(1)
}

/**
 * First-party manager screen for groups and symbols. The screen is intentionally
 * a View rather than an Activity so the host controls navigation and window policy.
 */
class SymbolDrawerManagerScreen(
    context: Context,
    private val repository: SymbolGroupRepository = SharedPreferencesSymbolGroupRepository(context),
    private val embeddedInSettings: Boolean = false,
    /** Optional session registry; when supplied, registered app actions appear in the action picker. */
    private val extensionRegistry: SymbolDrawerExtensionRegistry? = null,
    /** Optional live toolbar target; when supplied, manager imports/editing sync automatically. */
    private val liveDrawer: SymbolDrawerView? = null
) : LinearLayout(context) {

    var onGroupsChanged: ((List<SymbolGroup>) -> Unit)? = null
    private var drawerBindingHandle: AutoCloseable? = null
    private var builtInVisibilityObserver: AutoCloseable? = null
    private var themeObserver: AutoCloseable? = null
    private var drawerTheme: SymbolDrawerTheme? = null
    var onExportBundle: ((String) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    private val manager = SymbolGroupManager(repository.also {
        LegacySymbolDataMigration.importIfV2Empty(context, it)
    })
    private val settingsRepository = SharedPreferencesSymbolDrawerSettingsRepository(context)
    private var settings = settingsRepository.load().settings
    private val settingsPreferences = context.applicationContext.getSharedPreferences(
        "symbol_drawer_v2_settings",
        Context.MODE_PRIVATE
    )
    private var settingsListenerRegistered = false
    private val settingsPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SETTINGS_PREFERENCE_KEY) {
            settings = settingsRepository.load().settings
            refreshSymbols()
            previewDrawer?.applySettings(settings)
        }
    }
    private val groupTabs = TabLayout(context)
    private val groupPageHost = DrawerPageHost(context)
    private val groupPageContents = LinkedHashMap<String, GroupPageContent>()
    private var searchButton: MaterialButton? = null
    /** Search uses the same outlined Material text field pattern as the editors. */
    private val searchFieldLayout = managerOutlinedTextField(
        context,
        context.getString(R.string.symbol_drawer_135)
    )
    private val searchField: TextInputEditText
        get() = searchFieldLayout.editText as TextInputEditText
    private val searchContainer = LinearLayout(context)
    private val preferenceStore = context.getSharedPreferences("zero_symbol_manager_usage_v2", Context.MODE_PRIVATE)
    private val favoriteIds = linkedSetOf<Long>().apply {
        addAll(preferenceStore.getStringSet("favorites", emptySet()).orEmpty().mapNotNull(String::toLongOrNull))
    }
    private val recentIds = ArrayDeque<Long>().apply {
        preferenceStore.getString("recent", "").orEmpty().split(',').mapNotNull(String::toLongOrNull)
            .take(30).forEach(::addLast)
    }
    private var activeFilter = ManagerSymbolFilter.ALL
    private val emptyView = TextView(context)
    private var symbolList: RecyclerView? = null
    /** Inline preview editor lives above the group tabs, never inside the symbol list. */
    private var previewPanel: LinearLayout? = null
    private var previewContent: FrameLayout? = null
    private var previewPopup: PopupWindow? = null
    private var dragPreviewPanel: LinearLayout? = null
    private var previewDrawer: SymbolDrawerView? = null
    private var previewModeActive = false
    private var previewEditSnapshot: List<SymbolGroup>? = null
    private var previewEditSelectedGroupId: String? = null
    private var reorderTouchHelper: ItemTouchHelper? = null
    /** One immutable snapshot for the whole drag gesture; intermediate swaps are not separate undo steps. */
    private var reorderGestureSnapshot: List<SymbolGroup>? = null
    private var reorderGestureGroupId: String? = null
    /** In-memory order while ItemTouchHelper owns the gesture; persisted once on release. */
    private var reorderWorkingGroupId: String? = null
    private var reorderWorkingSymbols: MutableList<SymbolEntry>? = null
    private val symbolAdapter = SymbolAdapter(
        context = context,
        onClick = { symbol -> editSymbol(symbol) },
        themeProvider = { drawerTheme },
        onLongClick = { symbol, anchor -> showSymbolMenu(symbol, anchor) },
        onDragHandle = { holder -> reorderTouchHelper?.startDrag(holder) },
        isBatchMode = { batchMode },
        isSelected = { symbol -> symbol.id in selectedSymbolIds },
        onBatchToggle = { symbol -> toggleBatchSelection(symbol) }
    )
    private var selectedGroupId: String? = manager.groups.firstOrNull()?.id
    private val selectedSymbolIds = linkedSetOf<Long>()
    private var batchMode = false
    private val batchBar = LinearLayout(context)
    private val batchSummary = TextView(context)
    private val undoBar = LinearLayout(context)
    private val undoMessage = TextView(context)
    private val undoButton = managerTextButton(context)
    private var pendingUndo: (() -> Unit)? = null

    init {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = roundedSurface(surfaceColor(), 18)
        if (manager.groups.isEmpty()) {
            manager.replaceAll(SymbolDrawerDefaults.createGroups(context))
        }
        buildHeader()
        buildGroupControls()
        buildSearch()
        buildSymbolList()
        addView(
            groupPageHost,
            if (embeddedInSettings) LayoutParams(LayoutParams.MATCH_PARENT, dp(320))
            else LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        )
        buildUndoBar()
        refresh()
        liveDrawer?.let(::bindToDrawer)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        val density = resources.displayMetrics.density
        val window = WindowAdaptiveInfo(w / density, h / density, density)
        val spec = AdaptiveContentLayoutResolver.resolve(window)
        val horizontal = dp(spec.horizontalPaddingDp.toInt())
        val vertical = dp(if (spec.verticalSpacingDp >= 16f) 20 else 12)
        setPadding(horizontal, vertical, horizontal, vertical)

        // Reflow the symbol editor list as cards on expanded windows. The
        // compact/medium path remains the original single-column list.
        (symbolList?.layoutManager as? GridLayoutManager)?.let { grid ->
            if (grid.spanCount != spec.cardColumns) grid.spanCount = spec.cardColumns
        }
        groupTabs.layoutParams?.let { params ->
            val targetHeight = dp(if (spec.cardColumns > 1) 52 else 48)
            if (params.height != targetHeight) {
                params.height = targetHeight
                groupTabs.layoutParams = params
            }
        }
    }

    fun reload() {
        manager.reload()
        selectedGroupId = manager.groups.firstOrNull()?.id
        refresh()
    }

    /**
     * Connects manager transactions to the live toolbar instance.
     *
     * The manager remains the source of durable group data; each committed
     * manager refresh is submitted directly to the existing drawer adapter so
     * imports, edits, deletes and undo do not depend on Activity recreation or
     * a later host-resume callback.
     *
     * Close the returned handle when this manager surface is disposed.
     */
    fun bindToDrawer(drawer: SymbolDrawerView): AutoCloseable {
        drawerBindingHandle?.close()
        drawerBindingHandle = null
        val previousListener = onGroupsChanged
        val previousUsageListener = drawer.onSymbolUsedListener
        themeObserver?.close()
        drawerTheme = drawer.themeSnapshot
        themeObserver = drawer.observeTheme(emitCurrent = false) { theme ->
            post {
                if (isAttachedToWindow) applyDrawerTheme(theme)
            }
        }
        val listener: (List<SymbolGroup>) -> Unit = { committedGroups ->
            // The visible toolbar is the primary consumer. An unrelated observer
            // must never prevent the actual drawer from receiving imported data.
            drawer.submitGroups(committedGroups, preserveSelectedGroup = true)
            runCatching { previousListener?.invoke(committedGroups) }
                .onFailure(::reportError)
        }
        val usageListener: (SymbolEntry, Boolean) -> Unit = { symbol, _ ->
            recordRecentlyUsed(symbol.id)
        }
        onGroupsChanged = listener
        drawer.onSymbolUsedListener = usageListener
        drawer.setRepository(repository, reload = false)
        drawer.submitGroups(manager.groups, preserveSelectedGroup = true)
        lateinit var ownerHandle: AutoCloseable
        ownerHandle = AutoCloseable {
            if (onGroupsChanged === listener) onGroupsChanged = previousListener
            if (drawer.onSymbolUsedListener === usageListener) {
                drawer.onSymbolUsedListener = previousUsageListener
            }
            builtInVisibilityObserver?.close()
            builtInVisibilityObserver = null
            themeObserver?.close()
            themeObserver = null
            drawerTheme = null
            if (drawerBindingHandle === ownerHandle) drawerBindingHandle = null
        }
        drawerBindingHandle = ownerHandle
        observeBuiltInVisibility(drawer.extensions)
        return ownerHandle
    }

    /** Reapply live palette tokens without running a data refresh or invalidating undo. */
    private fun applyDrawerTheme(theme: SymbolDrawerTheme?) {
        drawerTheme = theme
        background = roundedSurface(surfaceColor(), 18)
        groupTabs.setSelectedTabIndicatorColor(primaryColor())
        groupTabs.setTabTextColors(secondaryTextColor(), primaryColor())
        refreshSymbols()
        // ListAdapter may calculate an empty diff when only the palette changed.
        // Force existing holders through the normal bind path so every visible
        // row receives the new theme tokens without replacing the list or its
        // selection/undo state.
        symbolAdapter.rebindTheme()
    }

    /**
     * Uses the same registry that owns the drawer extensions. This also works
     * when the manager is embedded without a live drawer instance.
     */
    private fun observeBuiltInVisibility(registry: SymbolDrawerExtensionRegistry?) {
        builtInVisibilityObserver?.close()
        builtInVisibilityObserver = null
        if (registry == null || !isAttachedToWindow) return
        builtInVisibilityObserver = registry.observe(emitCurrent = true) { snapshot ->
            post {
                if (!isAttachedToWindow) return@post
                val visibility = snapshot.builtInVisibility
                val searchVisible = visibility[SymbolDrawerBuiltInFeatures.SEARCH] ?: true
                val favoritesVisible = visibility[SymbolDrawerBuiltInFeatures.FAVORITES] ?: true
                val recentVisible = visibility[SymbolDrawerBuiltInFeatures.RECENT] ?: true
                searchButton?.visibility = if (searchVisible) View.VISIBLE else View.GONE
                if (!searchVisible) setSearchVisible(false)

                val activeFilterHidden =
                    (activeFilter == ManagerSymbolFilter.FAVORITES && !favoritesVisible) ||
                    (activeFilter == ManagerSymbolFilter.RECENT && !recentVisible)
                if (activeFilterHidden) {
                    activeFilter = ManagerSymbolFilter.ALL
                    refreshSymbols()
                }
            }
        }
    }

    private fun isBuiltInFeatureVisible(featureId: String): Boolean =
        liveDrawer?.isBuiltInFeatureVisible(featureId)
            ?: extensionRegistry?.isBuiltInVisible(featureId)
            ?: true

    fun exportBundle(): String = SymbolGroupBundleCodec.encode(manager.groups)

    /**
     * Exports the legacy compact JSON array used by the V1 symbol-toolbar
     * interchange format (group fields n/d; item fields a/b/c/d/e).
     *
     * This is intended for MT Manager/V1-compatible import-export workflows.
     * V2-only actions that cannot be represented by the legacy numeric action
     * catalog cause an IllegalArgumentException instead of being silently lost.
     */
    fun exportMtManagerJson(): String = LegacySymbolDataCodec.encode(manager.groups)

    /**
     * Imports a legacy compact symbol JSON file. Decoding is strict and validates
     * the complete payload before replacing current data. Existing import undo
     * behaviour is preserved.
     */
    fun importMtManagerJson(json: String) {
        val previous = manager.groups.toList()
        val previousUsage = snapshotUsageState()
        val previousSelection = selectedGroupId
        val imported = LegacySymbolDataCodec.decode(json)
        manager.replaceAll(imported)
        selectedGroupId = imported.firstOrNull()?.id
        refresh()
        showUndo(context.getString(R.string.symbol_drawer_096)) {
            manager.replaceAll(previous)
            restoreUsageState(previousUsage)
            selectedGroupId = previousSelection
        }
    }

    fun importBundle(json: String) {
        val previous = manager.groups.toList()
        val previousUsage = snapshotUsageState()
        val previousSelection = selectedGroupId
        val imported = SymbolGroupBundleCodec.decode(json)
        manager.replaceAll(imported)
        selectedGroupId = imported.firstOrNull()?.id
        refresh()
        showUndo(context.getString(R.string.symbol_drawer_097)) {
            manager.replaceAll(previous)
            restoreUsageState(previousUsage)
            selectedGroupId = previousSelection
        }
    }

    private fun buildHeader() {
        val row = horizontalRow().apply { gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL }
        val searchButton = managerOutlinedButton(context).apply {
            text = context.getString(R.string.symbol_drawer_132)
            contentDescription = context.getString(R.string.symbol_drawer_134)
            tag = "symbol_manager_search_button"
            isAllCaps = false
            textSize = 13f
            minWidth = dp(64)
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            icon = context.getDrawable(android.R.drawable.ic_menu_search)
            iconSize = dp(18)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener {
                setSearchVisible(searchContainer.visibility != View.VISIBLE)
            }
        }
        this.searchButton = searchButton
        row.addView(searchButton, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))
        val menuButton = managerOutlinedButton(context).apply {
            text = context.getString(R.string.symbol_drawer_156)
            isAllCaps = false
            textSize = 13f
            minWidth = dp(64)
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            icon = context.getDrawable(android.R.drawable.ic_menu_more)
            iconSize = dp(18)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { showManagerMenu(this) }
        }
        row.addView(menuButton, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))
        addView(row, matchWrap())
        buildDragPreviewPanel()
    }

    private fun showManagerMenu(anchor: View) {
        val menu = PopupMenu(context, anchor, android.view.Gravity.END)
        menu.menu.add(if (batchMode) context.getString(R.string.symbol_drawer_220) else context.getString(R.string.symbol_drawer_128))
            .setOnMenuItemClickListener { setBatchMode(!batchMode); true }
        menu.menu.add(context.getString(R.string.symbol_drawer_173)).setOnMenuItemClickListener { editSymbol(null); true }
        menu.menu.add(if (previewModeActive) context.getString(R.string.symbol_drawer_221) else context.getString(R.string.symbol_drawer_229))
            .setOnMenuItemClickListener {
                if (previewModeActive) finishPreviewEditMode() else enterPreviewEditMode()
                true
            }
        menu.menu.add(context.getString(R.string.symbol_drawer_172)).setOnMenuItemClickListener {
            promptGroupName(context.getString(R.string.symbol_drawer_147)) { title ->
                runCatching { selectedGroupId = manager.addGroup(title).id; refresh() }
                    .onFailure(::reportError)
            }
            true
        }
        val filters = menu.menu.addSubMenu(context.getString(R.string.symbol_drawer_191))
        buildList {
            add(ManagerSymbolFilter.ALL to context.getString(R.string.symbol_drawer_035))
            if (isBuiltInFeatureVisible(SymbolDrawerBuiltInFeatures.FAVORITES)) {
                add(ManagerSymbolFilter.FAVORITES to context.getString(R.string.symbol_drawer_138))
            }
            if (isBuiltInFeatureVisible(SymbolDrawerBuiltInFeatures.RECENT)) {
                add(ManagerSymbolFilter.RECENT to context.getString(R.string.symbol_drawer_159))
            }
        }.forEach { (filter, label) ->
            filters.add(if (activeFilter == filter) "✓ $label" else label)
                .setOnMenuItemClickListener { setActiveFilter(filter); true }
        }
        menu.show()
    }

    private fun setActiveFilter(filter: ManagerSymbolFilter) {
        activeFilter = filter
        refreshSymbols()
    }

    private fun buildGroupControls() {
        groupTabs.tabMode = TabLayout.MODE_SCROLLABLE
        groupTabs.isInlineLabel = true
        groupTabs.setSelectedTabIndicatorColor(primaryColor())
        groupTabs.setTabTextColors(secondaryTextColor(), primaryColor())
        groupTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val id = tab.tag as? String ?: return
                selectedGroupId = id
                groupPageHost.selectPage(groupPageId(id))
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        groupPageHost.navigationController.addListener { snapshot, _ ->
            snapshot.selectedPageId?.removePrefix(GROUP_PAGE_PREFIX)?.let { id ->
                if (selectedGroupId != id) {
                    selectedGroupId = id
                    refreshSymbols()
                }
            }
            updateGroupNavigationButtons()
        }
        addView(groupTabs, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
    }
    private fun showGroupTabMenu(anchor: View, groupId: String) {
        val group = manager.groups.firstOrNull { it.id == groupId } ?: return
        selectedGroupId = group.id
        val index = manager.groups.indexOfFirst { it.id == group.id }
        groupPageHost.selectPage(groupPageId(group.id))
        PopupMenu(context, anchor).apply {
            menu.add(context.getString(R.string.symbol_drawer_087)).apply { isEnabled = index > 0 }
                .setOnMenuItemClickListener { moveGroup(group.id, -1); true }
            menu.add(context.getString(R.string.symbol_drawer_061)).apply { isEnabled = index >= 0 && index < manager.groups.lastIndex }
                .setOnMenuItemClickListener { moveGroup(group.id, 1); true }
            menu.add(context.getString(R.string.symbol_drawer_171)).setOnMenuItemClickListener { showAddGroupDialog(); true }
            menu.add(context.getString(R.string.symbol_drawer_071)).setOnMenuItemClickListener {
                runCatching {
                    val duplicate = manager.duplicateGroup(group.id, index + 1)
                    selectedGroupId = duplicate.id
                    refresh()
                    toast(context.getString(R.string.symbol_drawer_094, duplicate.title))
                }.onFailure(::reportError)
                true
            }
            menu.add(context.getString(R.string.symbol_drawer_068)).apply { isEnabled = manager.groups.size > 1 }
                .setOnMenuItemClickListener { promptCopyGroupTo(group.id); true }
            menu.add(context.getString(R.string.symbol_drawer_222)).setOnMenuItemClickListener {
                promptGroupName(context.getString(R.string.symbol_drawer_223, group.title), group.title) {
                    manager.renameGroup(group.id, it)
                    refresh()
                }
                true
            }
            menu.add(context.getString(R.string.symbol_drawer_044)).setOnMenuItemClickListener { confirmDeleteGroup(group.id); true }
            show()
        }
    }

    private fun showAddGroupDialog() {
        promptGroupName(context.getString(R.string.symbol_drawer_147)) {
            val created = manager.addGroup(it)
            selectedGroupId = created.id
            refresh()
        }
    }

    private fun moveGroup(groupId: String, delta: Int) {
        val index = manager.groups.indexOfFirst { it.id == groupId }
        val target = index + delta
        if (index >= 0 && target in manager.groups.indices) {
            manager.moveGroup(index, target)
            refresh()
            groupPageHost.selectPage(groupPageId(groupId))
        }
    }

    private fun promptCopyGroupTo(sourceGroupId: String) {
        val source = manager.groups.firstOrNull { it.id == sourceGroupId } ?: return
        val targets = manager.groups.filter { it.id != sourceGroupId }
        if (targets.isEmpty()) return
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.symbol_drawer_067, source.title))
            .setItems(targets.map { it.title }.toTypedArray()) { _, which ->
                runCatching {
                    val target = targets[which]
                    val ids = source.symbols.map { it.id }.toSet()
                    if (ids.isNotEmpty()) manager.copySymbols(source.id, ids, target.id)
                    selectedGroupId = target.id
                    refresh()
                    toast(context.getString(R.string.symbol_drawer_095, target.title))
                }.onFailure(::reportError)
            }
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .showAdaptiveDialog()
    }

    private fun setSearchVisible(show: Boolean) {
        searchContainer.visibility = if (show) View.VISIBLE else View.GONE
        searchButton?.text = if (show) context.getString(R.string.symbol_drawer_139) else context.getString(R.string.symbol_drawer_132)
        searchButton?.contentDescription = if (show) context.getString(R.string.symbol_drawer_140) else context.getString(R.string.symbol_drawer_134)
        if (show) {
            searchField.requestFocus()
        } else {
            searchField.text?.clear()
            searchField.clearFocus()
        }
    }

    private fun buildSearch() {
        searchContainer.tag = "symbol_manager_search_container"
        searchContainer.orientation = VERTICAL
        searchContainer.visibility = View.GONE
        searchField.setSingleLine(true)
        searchField.textSize = 14f
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshSymbols()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        searchContainer.addView(searchFieldLayout, matchWrap())
        addView(searchContainer, matchWrap())
    }

    private data class UsageState(
        val favorites: Set<Long>,
        val recent: List<Long>
    )

    private fun snapshotUsageState() = UsageState(favoriteIds.toSet(), recentIds.toList())

    private fun restoreUsageState(state: UsageState) {
        favoriteIds.clear()
        favoriteIds.addAll(state.favorites)
        recentIds.clear()
        state.recent.take(30).forEach(recentIds::addLast)
        persistUsageState()
    }

    private fun pruneUsageState() {
        val liveIds = manager.groups.asSequence()
            .flatMap { it.symbols.asSequence() }
            .mapTo(mutableSetOf()) { it.id }
        val retainedFavorites = favoriteIds.filterTo(linkedSetOf()) { it in liveIds }
        val retainedRecent = recentIds.filter { it in liveIds }
        if (retainedFavorites != favoriteIds || retainedRecent != recentIds.toList()) {
            favoriteIds.clear()
            favoriteIds.addAll(retainedFavorites)
            recentIds.clear()
            retainedRecent.forEach(recentIds::addLast)
            persistUsageState()
        }
    }

    private fun persistUsageState() {
        preferenceStore.edit()
            .putStringSet("favorites", favoriteIds.map(Long::toString).toSet())
            .putString("recent", recentIds.joinToString(","))
            .apply()
    }

    private fun toggleFavorite(symbol: SymbolEntry) {
        // Usage-state edits are newer user mutations; an older undo snapshot
        // must not restore stale favorites over this change.
        invalidateUndo()
        if (!favoriteIds.add(symbol.id)) favoriteIds.remove(symbol.id)
        persistUsageState()
        refreshSymbols()
        toast(if (symbol.id in favoriteIds) context.getString(R.string.symbol_drawer_091) else context.getString(R.string.symbol_drawer_092))
    }

    /** Records a real toolbar dispatch; unknown/stale IDs are ignored safely. */
    fun recordRecentlyUsed(symbolId: Long) {
        val symbol = manager.groups.asSequence()
            .flatMap { it.symbols.asSequence() }
            .firstOrNull { it.id == symbolId } ?: return
        markRecentlyUsed(symbol)
    }

    private fun markRecentlyUsed(symbol: SymbolEntry) {
        // A later symbol dispatch changes durable usage state, so discard any
        // earlier undo snapshot that captured an obsolete favorites/recent set.
        invalidateUndo()
        recentIds.remove(symbol.id)
        recentIds.addFirst(symbol.id)
        while (recentIds.size > 30) recentIds.removeLast()
        persistUsageState()
        if (activeFilter == ManagerSymbolFilter.RECENT) refreshSymbols()
    }

    private fun buildSymbolList() {
        emptyView.text = context.getString(R.string.symbol_drawer_115)
        emptyView.textSize = 13f
        emptyView.setTextColor(secondaryTextColor())
        emptyView.alpha = 0.75f
        emptyView.setPadding(dp(8), dp(16), dp(8), dp(16))
        val list = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 1)
            adapter = symbolAdapter
            itemAnimator = null
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }
        symbolList = list
        if (embeddedInSettings) list.isNestedScrollingEnabled = false
        installSymbolReordering(list)
        buildBatchBar()
    }

    private fun buildBatchBar() {
        batchBar.orientation = HORIZONTAL
        batchBar.gravity = android.view.Gravity.CENTER_VERTICAL
        batchBar.visibility = View.GONE
        batchBar.setPadding(dp(4), dp(4), dp(4), dp(4))
        batchSummary.setTextColor(HostThemeColors.secondaryText(context))
        batchSummary.textSize = 12f
        batchBar.addView(batchSummary, LayoutParams(0, dp(40), 1f))
        batchBar.addView(actionButton(context.getString(R.string.symbol_drawer_066)) { batchCopy() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
        batchBar.addView(actionButton(context.getString(R.string.symbol_drawer_181)) { batchMove() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
        batchBar.addView(actionButton(context.getString(R.string.symbol_drawer_044)) { batchDelete() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
    }

    private fun setBatchMode(enabled: Boolean) {
        batchMode = enabled
        selectedSymbolIds.clear()
        batchBar.visibility = if (enabled) View.VISIBLE else View.GONE
        updateBatchSummary()
        symbolAdapter.notifyDataSetChanged()
    }

    private fun updateBatchSummary() {
        batchSummary.text = context.getString(R.string.symbol_drawer_102, selectedSymbolIds.size)
    }

    private fun toggleBatchSelection(symbol: SymbolEntry) {
        if (!batchMode) return
        if (!selectedSymbolIds.add(symbol.id)) selectedSymbolIds.remove(symbol.id)
        updateBatchSummary()
        symbolAdapter.notifyDataSetChanged()
    }

    private fun selectedBatch(): Pair<SymbolGroup, Set<Long>>? {
        val group = currentGroup() ?: return null
        val ids = selectedSymbolIds.toSet()
        if (ids.isEmpty()) {
            toast(context.getString(R.string.symbol_drawer_214))
            return null
        }
        return group to ids
    }

    private fun batchDelete() {
        val (group, ids) = selectedBatch() ?: return
        val before = manager.groups.toList()
        val previousUsage = snapshotUsageState()
        runCatching {
            manager.removeSymbols(group.id, ids)
            pruneUsageState()
            selectedSymbolIds.clear()
            updateBatchSummary()
            refresh()
            showUndo(context.getString(R.string.symbol_drawer_088, ids.size)) {
                manager.replaceAll(before)
                restoreUsageState(previousUsage)
            }
        }.onFailure(::reportError)
    }

    private fun batchCopy() {
        val (group, ids) = selectedBatch() ?: return
        val targets = manager.groups.filter { it.id != group.id }
        if (targets.isEmpty()) { toast(context.getString(R.string.symbol_drawer_213)); return }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.symbol_drawer_070))
            .setItems(targets.map { it.title }.toTypedArray()) { _, which ->
                val before = manager.groups.toList()
                runCatching {
                    val target = targets[which]
                    manager.copySymbols(group.id, ids, target.id)
                    selectedSymbolIds.clear()
                    refresh()
                    showUndo(context.getString(R.string.symbol_drawer_093, ids.size)) { manager.replaceAll(before) }
                }.onFailure(::reportError)
            }
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .showAdaptiveDialog()
    }

    private fun batchMove() {
        val (group, ids) = selectedBatch() ?: return
        val targets = manager.groups.filter { it.id != group.id }
        if (targets.isEmpty()) { toast(context.getString(R.string.symbol_drawer_213)); return }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.symbol_drawer_183))
            .setItems(targets.map { it.title }.toTypedArray()) { _, which ->
                val before = manager.groups.toList()
                runCatching {
                    val target = targets[which]
                    manager.moveSymbolsToGroup(group.id, ids, target.id)
                    selectedGroupId = target.id
                    selectedSymbolIds.clear()
                    refresh()
                    showUndo(context.getString(R.string.symbol_drawer_100, ids.size)) { manager.replaceAll(before) }
                }.onFailure(::reportError)
            }
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .showAdaptiveDialog()
    }

    /**
     * Reordering uses the same repository-backed manager transaction as the
     * explicit move commands. The preview is a fresh SymbolDrawerView instance,
     * not the Activity's live toolbar; it is detached and released as soon as
     * the drag gesture ends.
     */
    private fun installSymbolReordering(list: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (searchField.text?.isNotBlank() == true) return false
                val group = currentGroup() ?: return false
                if (reorderWorkingGroupId != group.id) return false
                val items = reorderWorkingSymbols ?: group.symbols.toMutableList().also {
                    reorderWorkingSymbols = it
                    reorderWorkingGroupId = group.id
                }
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION ||
                    from !in items.indices || to !in items.indices || from == to
                ) return false
                val moved = items.removeAt(from)
                items.add(to, moved)
                symbolAdapter.submitList(items.toList())
                updateDragPreview()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // Capture once per gesture, not once per onMove callback. ItemTouchHelper
                    // may cross many rows and invoke onMove repeatedly during one drag.
                    if (reorderGestureSnapshot == null) {
                        reorderGestureSnapshot = manager.groups.map { group ->
                            group.copy(symbols = group.symbols.toList())
                        }
                        reorderGestureGroupId = selectedGroupId
                        reorderWorkingGroupId = selectedGroupId
                        reorderWorkingSymbols = currentGroup()?.symbols?.toMutableList()
                    }
                    showDragPreview()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val before = reorderGestureSnapshot
                val previousGroupId = reorderGestureGroupId
                val changedGroupId = reorderWorkingGroupId
                val orderedIds = reorderWorkingSymbols?.map { it.id }
                reorderGestureSnapshot = null
                reorderGestureGroupId = null
                reorderWorkingGroupId = null
                reorderWorkingSymbols = null
                runCatching {
                    // ItemTouchHelper can report many swaps during one physical drag.
                    // Keep those swaps transient and write one repository transaction
                    // only after the gesture has completed.
                    if (before != null && changedGroupId != null && orderedIds != null) {
                        val previousOrder = before.firstOrNull { it.id == changedGroupId }
                            ?.symbols?.map { it.id }
                        if (previousOrder != null && previousOrder != orderedIds) {
                            manager.reorderSymbols(changedGroupId, orderedIds)
                        }
                    }
                    refresh()
                    if (before != null && before != manager.groups) {
                        showUndo(context.getString(R.string.symbol_drawer_101)) {
                            manager.replaceAll(before.map { it.copy(symbols = it.symbols.toList()) })
                            selectedGroupId = previousGroupId
                        }
                    }
                }.onFailure {
                    refresh()
                    reportError(it)
                }
                destroyDragPreview()
            }

            override fun isLongPressDragEnabled(): Boolean = false
        }
        reorderTouchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(list) }
    }

    private fun buildDragPreviewPanel() {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = roundedSurface(surfaceColor(), 14)
        }
        val controls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        controls.addView(TextView(context).apply {
            text = context.getString(R.string.symbol_drawer_085)
            textSize = 13f
            setTextColor(onSurfaceColor())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LayoutParams(0, dp(36), 1f))
        controls.addView(actionButton(context.getString(R.string.symbol_drawer_120)) { restorePreviewEditMode() },
            LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)))
        controls.addView(actionButton(context.getString(R.string.symbol_drawer_075)) { finishPreviewEditMode() },
            LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)))
        panel.addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        previewContent = FrameLayout(context).also {
            panel.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, dp(144)))
        }
        previewPanel = panel
        // This is deliberately outside DrawerPageHost and RecyclerView. Toggling
        // preview mode changes only the manager's available content height.
        addView(panel, matchWrap())
    }

    private fun createPreviewDrawer(): SymbolDrawerView = SymbolDrawerView(context).apply {
        setRepository(repository, reload = false)
        submitGroups(manager.groups, preserveSelectedGroup = true)
        manager.groups.indexOfFirst { it.id == selectedGroupId }
            .takeIf { it >= 0 }?.let { selectGroup(it, false) }
        onSymbolActionListener = null
        onSymbolUsedListener = null
        onOpenManagerListener = null
        onOpenSettingsListener = null
        onGroupChangedListener = null
    }

    private fun enterPreviewEditMode() {
        if (previewModeActive) return
        destroyDragPreview()
        previewEditSnapshot = manager.groups.map { it.copy(symbols = it.symbols.toList()) }
        previewEditSelectedGroupId = selectedGroupId
        previewModeActive = true
        previewPanel?.visibility = View.VISIBLE
        val preview = createPreviewDrawer()
        previewDrawer = preview
        previewContent?.addView(
            preview,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        preview.post { preview.setExpanded(true, false) }
        requestLayout()
    }

    private fun restorePreviewEditMode() {
        val snapshot = previewEditSnapshot ?: return
        runCatching {
            manager.replaceAll(snapshot.map { it.copy(symbols = it.symbols.toList()) })
            selectedGroupId = previewEditSelectedGroupId
            refresh()
            toast(context.getString(R.string.symbol_drawer_098))
        }.onFailure(::reportError)
    }

    private fun finishPreviewEditMode() {
        if (!previewModeActive) return
        previewModeActive = false
        previewEditSnapshot = null
        previewEditSelectedGroupId = null
        previewPanel?.visibility = View.GONE
        releasePreviewDrawer()
        requestLayout()
    }

    private fun releasePreviewDrawer() {
        val preview = previewDrawer ?: return
        preview.onSymbolActionListener = null
        preview.onSymbolUsedListener = null
        preview.onOpenManagerListener = null
        preview.onOpenSettingsListener = null
        preview.onGroupChangedListener = null
        (preview.parent as? ViewGroup)?.removeView(preview)
        previewDrawer = null
    }

    private fun showDragPreview() {
        if (previewModeActive || previewDrawer != null) return
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(4))
            background = roundedSurface(surfaceColor(), 14)
            addView(TextView(context).apply {
                text = context.getString(R.string.symbol_drawer_076)
                textSize = 11f
                setTextColor(secondaryTextColor())
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(22)))
        }
        dragPreviewPanel = panel
        val preview = createPreviewDrawer()
        previewDrawer = preview
        panel.addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, dp(140)))
        val popupWidth = dp(320).coerceAtMost(
            (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(180))
        )
        val popupHeight = dp(174)
        val popup = PopupWindow(panel, popupWidth, popupHeight, false).apply {
            // The preview is visual feedback only. It must never become the
            // touch target while ItemTouchHelper owns the symbol drag stream.
            isTouchable = false
            isFocusable = false
            isOutsideTouchable = false
            elevation = dp(12).toFloat()
        }
        previewPopup = popup

        // Anchor the preview to the symbol list instead of a fixed screen Y.
        // Its bottom edge overlaps roughly 5% of the list viewport, keeping
        // the preview higher without covering the active drag area.
        val rootLocation = IntArray(2)
        val listLocation = IntArray(2)
        rootView.getLocationOnScreen(rootLocation)
        val list = symbolList
        val overlapPx = ((list?.height ?: 0) * 0.05f).toInt()
        val listTopInRoot = if (list != null && list.height > 0) {
            list.getLocationOnScreen(listLocation)
            listLocation[1] - rootLocation[1]
        } else dp(96)
        val previewTop = (listTopInRoot - popupHeight + overlapPx).coerceAtLeast(0)
        popup.showAtLocation(rootView, android.view.Gravity.TOP or android.view.Gravity.END, dp(12), previewTop)
        preview.post { preview.setExpanded(true, false) }
    }

    private fun updateDragPreview() {
        previewDrawer?.let { preview ->
            val workingGroupId = reorderWorkingGroupId
            val workingSymbols = reorderWorkingSymbols
            val previewGroups = if (workingGroupId != null && workingSymbols != null) {
                manager.groups.map { group ->
                    if (group.id == workingGroupId) group.copy(symbols = workingSymbols.toList()) else group
                }
            } else manager.groups
            preview.submitGroups(previewGroups, preserveSelectedGroup = true)
            previewGroups.indexOfFirst { it.id == selectedGroupId }
                .takeIf { it >= 0 }?.let { preview.selectGroup(it, false) }
        }
    }

    private fun destroyDragPreview() {
        // A persistent inline preview belongs to preview-edit mode and must
        // survive ItemTouchHelper.clearView after each reorder operation.
        if (previewModeActive) return
        releasePreviewDrawer()
        previewPopup?.dismiss()
        previewPopup = null
        dragPreviewPanel = null
    }

    private fun buildUndoBar() {
        undoBar.orientation = HORIZONTAL
        undoBar.gravity = android.view.Gravity.CENTER_VERTICAL
        undoBar.setPadding(dp(8), dp(4), dp(4), dp(4))
        undoBar.visibility = View.GONE
        undoMessage.textSize = 13f
        undoBar.addView(undoMessage, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        undoButton.text = context.getString(R.string.symbol_drawer_136)
        undoButton.setOnClickListener {
            val restore = pendingUndo ?: return@setOnClickListener
            pendingUndo = null
            undoBar.visibility = View.GONE
            runCatching {
                restore()
                refresh()
            }.onFailure(::reportError)
        }
        undoBar.addView(undoButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(undoBar, matchWrap())
    }

    private fun refresh() {
        // Undo snapshots are valid only until another manager mutation or reload.
        // Clearing them here prevents an old delete/import snapshot from replacing
        // newer edits when the user later presses Undo.
        invalidateUndo()
        // Reconcile usage references after imports, reloads and restores too.
        pruneUsageState()
        val groups = manager.groups
        val selectedIndex = groups.indexOfFirst { it.id == selectedGroupId }.takeIf { it >= 0 } ?: 0
        selectedGroupId = groups.getOrNull(selectedIndex)?.id
        val activeIds = groups.mapTo(mutableSetOf()) { it.id }
        groupPageContents.keys.retainAll(activeIds)
        val pages = groups.map { group ->
            val content = groupPageContents.getOrPut(group.id) { GroupPageContent(group.id) }
            DrawerPage(groupPageId(group.id), group.title, content)
        }
        groupPageHost.setPages(pages, preserveSelection = true)
        if (selectedGroupId != null) groupPageHost.selectPage(groupPageId(selectedGroupId!!))
        updateGroupNavigationButtons()
        searchField.isEnabled = groups.isNotEmpty()
        refreshSymbols()
        // Keep the inline toolbar preview in lockstep with every committed
        // manager mutation, not only drag-reorder operations.
        updateDragPreview()
        runCatching { onGroupsChanged?.invoke(groups) }
            .onFailure(::reportError)
    }

    private fun groupPageId(groupId: String) = GROUP_PAGE_PREFIX + groupId

    private fun updateGroupNavigationButtons() {
        val existing = (0 until groupTabs.tabCount).mapNotNull { groupTabs.getTabAt(it)?.tag as? String }
        val next = manager.groups.map { it.id }
        if (existing != next) {
            groupTabs.removeAllTabs()
            manager.groups.forEach { group ->
                val tab = groupTabs.newTab()
                tab.text = group.title
                tab.tag = group.id
                groupTabs.addTab(tab, false)
            }
        } else {
            manager.groups.forEachIndexed { index, group -> groupTabs.getTabAt(index)?.text = group.title }
        }
        val index = manager.groups.indexOfFirst { it.id == selectedGroupId }
        if (index >= 0 && groupTabs.selectedTabPosition != index) groupTabs.getTabAt(index)?.select()
        val tabStrip = groupTabs.getChildAt(0) as? ViewGroup
        if (tabStrip != null) {
            for (tabIndex in 0 until minOf(groupTabs.tabCount, tabStrip.childCount)) {
                val tabView = tabStrip.getChildAt(tabIndex)
                val groupId = groupTabs.getTabAt(tabIndex)?.tag as? String ?: continue
                tabView.setOnLongClickListener {
                    showGroupTabMenu(it, groupId)
                    true
                }
            }
        }
    }

    private inner class GroupPageContent(
        private val groupId: String
    ) : DrawerPanelContent {
        private var page: LinearLayout? = null

        override fun createView(): View = LinearLayout(context).apply {
            orientation = VERTICAL
            page = this
        }

        override fun onPanelVisible() {
            selectedGroupId = groupId
            refreshSymbols()
            val container = page ?: return
            detachManagerContent()
            container.addView(batchBar, matchWrap())
            container.addView(emptyView, matchWrap())
            val list = symbolList ?: return
            // The page host already owns the embedded height. Let the list
            // consume only the remainder after the batch and empty-state rows,
            // otherwise a second fixed 320dp height makes the page overflow.
            container.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }

        override fun onPanelHidden() {
            detachManagerContent()
        }

        override fun onPanelDetached() {
            detachManagerContent()
            page = null
        }

        private fun detachManagerContent() {
            (batchBar.parent as? ViewGroup)?.removeView(batchBar)
            (emptyView.parent as? ViewGroup)?.removeView(emptyView)
            (symbolList?.parent as? ViewGroup)?.removeView(symbolList)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (drawerBindingHandle == null) liveDrawer?.let(::bindToDrawer)
        if (builtInVisibilityObserver == null) {
            observeBuiltInVisibility(liveDrawer?.extensions ?: extensionRegistry)
        }
        if (!settingsListenerRegistered) {
            settingsPreferences.registerOnSharedPreferenceChangeListener(settingsPreferenceListener)
            settingsListenerRegistered = true
        }
        // Settings may have changed while this manager was detached.
        settings = settingsRepository.load().settings
        refreshSymbols()
        previewDrawer?.applySettings(settings)
        groupPageHost.setPanelVisible(true)
        if (reorderTouchHelper == null) symbolList?.let(::installSymbolReordering)
    }

    override fun onDetachedFromWindow() {
        groupPageHost.setPanelVisible(false)
        if (settingsListenerRegistered) {
            settingsPreferences.unregisterOnSharedPreferenceChangeListener(settingsPreferenceListener)
            settingsListenerRegistered = false
        }
        drawerBindingHandle?.close()
        drawerBindingHandle = null
        builtInVisibilityObserver?.close()
        builtInVisibilityObserver = null
        themeObserver?.close()
        themeObserver = null
        drawerTheme = null
        if (previewModeActive) finishPreviewEditMode()
        destroyDragPreview()
        // A detached manager must not retain an in-flight gesture snapshot that
        // could be mistaken for a later drag after the view is reattached.
        reorderGestureSnapshot = null
        reorderGestureGroupId = null
        reorderWorkingGroupId = null
        reorderWorkingSymbols = null
        reorderTouchHelper?.attachToRecyclerView(null)
        reorderTouchHelper = null
        super.onDetachedFromWindow()
    }

    private fun refreshSymbols() {
        val group = currentGroup()
        val query = searchField.text?.toString()?.trim().orEmpty()
        val source = group?.symbols.orEmpty()
        val filtered = source.filter { symbol ->
            val matchesQuery = query.isEmpty() ||
                symbol.label.contains(query, ignoreCase = true) ||
                (settings.searchIncludesActionPayload && (
                    symbol.shortAction.toString().contains(query, ignoreCase = true) ||
                        symbol.longAction?.let { it.toString().contains(query, ignoreCase = true) } == true
                    ))
            val matchesFilter = when (activeFilter) {
                ManagerSymbolFilter.ALL -> true
                ManagerSymbolFilter.FAVORITES -> symbol.id in favoriteIds
                ManagerSymbolFilter.RECENT -> symbol.id in recentIds
            }
            matchesQuery && matchesFilter
        }
        val symbols = if (activeFilter == ManagerSymbolFilter.RECENT) {
            val order = recentIds.withIndex().associate { it.value to it.index }
            filtered.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        } else filtered
        symbolAdapter.submitList(symbols)
        emptyView.visibility = if (symbols.isEmpty()) View.VISIBLE else View.GONE
        selectedSymbolIds.retainAll(group?.symbols?.mapTo(mutableSetOf()) { it.id }.orEmpty())
        updateBatchSummary()
    }

    private fun currentGroup(): SymbolGroup? =
        selectedGroupId?.let { id -> manager.groups.firstOrNull { it.id == id } }

    private fun moveCurrentGroup(delta: Int) {
        val index = manager.groups.indexOfFirst { it.id == selectedGroupId }
        val target = index + delta
        if (index >= 0 && target in manager.groups.indices) {
            manager.moveGroup(index, target)
            refresh()
        }
    }

    private fun confirmDeleteGroup(groupId: String) {
        val group = manager.groups.firstOrNull { it.id == groupId } ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.symbol_drawer_046))
            .setMessage(context.getString(R.string.symbol_drawer_045, group.title))
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .setPositiveButton(context.getString(R.string.symbol_drawer_044)) { _, _ ->
                val previous = manager.groups.toList()
                val previousUsage = snapshotUsageState()
                val previousSelection = selectedGroupId
                runCatching {
                    manager.removeGroup(group.id)
                    pruneUsageState()
                    selectedGroupId = manager.groups.firstOrNull()?.id
                    refresh()
                    showUndo(context.getString(R.string.symbol_drawer_089, group.title)) {
                        manager.replaceAll(previous)
                        restoreUsageState(previousUsage)
                        selectedGroupId = previousSelection
                    }
                }.onFailure(::reportError)
            }
            .showAdaptiveDialog()
    }

    private fun promptGroupName(title: String, initial: String = "", done: (String) -> Unit) {
        val inputLayout = managerOutlinedTextField(
            context,
            context.getString(R.string.symbol_drawer_039)
        )
        val input = inputLayout.editText as TextInputEditText
        input.setSingleLine(true)
        input.setText(initial)
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(inputLayout)
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .setPositiveButton(context.getString(R.string.symbol_drawer_178)) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) runCatching { done(value) }.onFailure(::reportError)
                else toast(context.getString(R.string.symbol_drawer_040))
            }
            .showAdaptiveDialog()
    }

    private fun editSymbol(existing: SymbolEntry?) {
        val group = currentGroup() ?: return
        val labelLayout = managerOutlinedTextField(
            context,
            context.getString(R.string.symbol_drawer_154)
        )
        val label = labelLayout.editText as TextInputEditText
        label.setSingleLine(true)
        label.setText(existing?.label.orEmpty())
        val shortLegacyIndex = resolveLegacyPickerIndex(
            existing?.shortAction,
            existing?.legacyShortActionId,
            existing?.legacyShortText
        )
        val preserveShortAction = existing?.shortAction != null && shortLegacyIndex == null
        val longLegacyIndex = existing?.longAction?.let { action ->
            resolveLegacyPickerIndex(action, existing.legacyLongActionId, existing.legacyLongText)
        }
        val preserveLongAction = existing?.longAction != null && longLegacyIndex == null

        val legacyOptions = LegacySymbolActionCatalog.values.mapIndexed { index, id ->
            SymbolActionOption(
                "[V1] " + LegacySymbolActionCatalog.labels[index],
                legacyId = id,
                legacyPayloadHint = if (id == MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID)
                    context.getString(R.string.symbol_drawer_230) else null
            )
        }
        val builtinOptions = BuiltinSymbolActionCatalog.options(context).map { option ->
            SymbolActionOption("[V2] " + option.label, builtin = option)
        }
        val extensionOptions = extensionRegistry?.actions().orEmpty().map { extension ->
            SymbolActionOption(context.getString(R.string.symbol_drawer_004) + extension.title, extension = extension)
        }
        val shortOptions = buildList {
            if (preserveShortAction) add(SymbolActionOption(context.getString(R.string.symbol_drawer_034), keepCurrent = true))
            addAll(legacyOptions)
            addAll(builtinOptions)
            addAll(extensionOptions)
        }
        val shortOffset = if (preserveShortAction) 1 else 0
        val shortIndex = if (preserveShortAction) 0 else (shortLegacyIndex ?: 0) + shortOffset

        val longOptions = buildList {
            if (preserveLongAction) add(SymbolActionOption(context.getString(R.string.symbol_drawer_033), keepCurrent = true))
            add(SymbolActionOption(context.getString(R.string.symbol_drawer_151), none = true))
            addAll(legacyOptions)
            addAll(builtinOptions)
            addAll(extensionOptions)
        }
        val longOffset = if (preserveLongAction) 2 else 1
        val longIndex = when {
            preserveLongAction -> 0
            existing?.longAction == null -> 0
            else -> (longLegacyIndex ?: 0) + longOffset
        }

        // MP Manager V1 find/replace (101): the typed envelope is the source of
        // truth for the find text, replacement text and the independent r/c/a
        // option flags. Each action slot owns its own editor; a null payload
        // binds the V1 host defaults (regex + match case, no replace-all).
        val shortFindReplacePayload = findReplacePayload(existing?.shortAction)
        val longFindReplacePayload = existing?.longAction?.let(::findReplacePayload)
        val shortInitialLegacyId = shortLegacyIndex?.let { LegacySymbolActionCatalog.values.getOrNull(it) }
        val longInitialLegacyId = longLegacyIndex?.let { LegacySymbolActionCatalog.values.getOrNull(it) }
        val shortFindReplace = buildFindReplaceEditor().apply {
            bind(shortFindReplacePayload)
            setVisible(shortInitialLegacyId == MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID)
        }
        val longFindReplace = buildFindReplaceEditor().apply {
            bind(longFindReplacePayload)
            setVisible(longInitialLegacyId == MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID)
        }

        // Material exposed dropdowns replace the framework Spinners. The
        // keep-current (V2 edit) and none (long action) entries stay part of the
        // same option lists, so index-based selection semantics are unchanged.
        val shortActionLayout = managerExposedDropdown(context)
        val shortAction = shortActionLayout.editText as MaterialAutoCompleteTextView
        shortAction.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_list_item_1, shortOptions.map(SymbolActionOption::label))
        )
        var shortSelectedIndex = shortIndex.coerceIn(shortOptions.indices)
        shortAction.setText(shortOptions.getOrNull(shortSelectedIndex)?.label.orEmpty(), false)
        val longActionLayout = managerExposedDropdown(context)
        val longAction = longActionLayout.editText as MaterialAutoCompleteTextView
        longAction.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_list_item_1, longOptions.map(SymbolActionOption::label))
        )
        var longSelectedIndex = longIndex.coerceIn(longOptions.indices)
        longAction.setText(longOptions.getOrNull(longSelectedIndex)?.label.orEmpty(), false)
        val shortTextLayout = managerOutlinedTextField(
            context,
            shortOptions.getOrNull(shortIndex)?.payloadHint
                ?: context.getString(R.string.symbol_drawer_050),
            multiline = true
        )
        val shortText = shortTextLayout.editText as TextInputEditText
        shortText.setText(
            shortFindReplacePayload?.findText
                ?: existing?.legacyShortText?.takeIf { shortLegacyIndex != null }
                ?: (existing?.shortAction as? SymbolAction.InsertText)?.template
                ?: (existing?.shortAction as? SymbolAction.EditorCommand)?.payload.orEmpty()
        )
        shortText.isEnabled = shortOptions.getOrNull(shortIndex)?.hasPayload == true
        val longTextLayout = managerOutlinedTextField(
            context,
            longOptions.getOrNull(longIndex)?.payloadHint
                ?: context.getString(R.string.symbol_drawer_050),
            multiline = true
        )
        val longText = longTextLayout.editText as TextInputEditText
        longText.setText(
            longFindReplacePayload?.findText
                ?: existing?.legacyLongText?.takeIf { longLegacyIndex != null }
                ?: (existing?.longAction as? SymbolAction.InsertText)?.template
                ?: (existing?.longAction as? SymbolAction.EditorCommand)?.payload.orEmpty()
        )
        longText.isEnabled = longOptions.getOrNull(longIndex)?.hasPayload == true
        shortAction.setOnItemClickListener { _, _, position, _ ->
            shortSelectedIndex = position
            val option = shortOptions.getOrNull(position)
            shortText.isEnabled = option?.hasPayload == true
            shortTextLayout.hint = option?.payloadHint ?: context.getString(R.string.symbol_drawer_050)
            shortFindReplace.setVisible(option?.legacyId == MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID)
        }
        longAction.setOnItemClickListener { _, _, position, _ ->
            longSelectedIndex = position
            val option = longOptions.getOrNull(position)
            longText.isEnabled = option?.hasPayload == true
            longTextLayout.hint = option?.payloadHint ?: context.getString(R.string.symbol_drawer_050)
            longFindReplace.setVisible(option?.legacyId == MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID)
        }
        val form = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(labelLayout, matchWrap())
            addView(TextView(context).apply { text = context.getString(R.string.symbol_drawer_177) }, matchWrap())
            addView(shortActionLayout, matchWrap())
            addView(shortTextLayout, matchWrap())
            addView(shortFindReplace.container, matchWrap())
            addView(TextView(context).apply { text = context.getString(R.string.symbol_drawer_224) }, matchWrap())
            addView(longActionLayout, matchWrap())
            addView(longTextLayout, matchWrap())
            addView(longFindReplace.container, matchWrap())
            addView(TextView(context).apply {
                text = listOf(
                    context.getString(R.string.symbol_drawer_022),
                    context.getString(R.string.symbol_drawer_014),
                    context.getString(R.string.symbol_drawer_007),
                    context.getString(R.string.symbol_drawer_015),
                    context.getString(R.string.symbol_drawer_005),
                    context.getString(R.string.symbol_drawer_011),
                    context.getString(R.string.symbol_drawer_016),
                    context.getString(R.string.symbol_drawer_009),
                    context.getString(R.string.symbol_drawer_021),
                    context.getString(R.string.symbol_drawer_008),
                    context.getString(R.string.symbol_drawer_018),
                    context.getString(R.string.symbol_drawer_020),
                    context.getString(R.string.symbol_drawer_012),
                    context.getString(R.string.symbol_drawer_019),
                    context.getString(R.string.symbol_drawer_010),
                    context.getString(R.string.symbol_drawer_006),
                    context.getString(R.string.symbol_drawer_017),
                    context.getString(R.string.symbol_drawer_013)
                ).joinToString("\n")
                textSize = 12f
                alpha = 0.7f
            }, matchWrap())
        }
        val windowHeight = rootView.height.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        val formScroll = CappedScrollView(
            context,
            resolveEditorFormMaxHeightPx(windowHeight, dp(96))
        ).apply {
            isFillViewport = false
            addView(form, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) context.getString(R.string.symbol_drawer_148) else context.getString(R.string.symbol_drawer_201))
            .setView(formScroll)
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .setPositiveButton(context.getString(R.string.symbol_drawer_032)) { _, _ ->
                runCatching {
                    val display = label.text.toString()
                    require(display.isNotEmpty()) { context.getString(R.string.symbol_drawer_187) }
                    val shortOption = shortOptions.getOrNull(shortSelectedIndex)
                        ?: error(context.getString(R.string.symbol_drawer_215))
                    val keepShort = shortOption.keepCurrent
                    val shortId = if (keepShort) existing?.legacyShortActionId else shortOption.legacyId
                    val shortPayload = if (shortOption.hasPayload) {
                        shortText.text.toString().takeIf { it.isNotEmpty() }
                    } else null
                    val shortTypedAction: SymbolAction = when {
                        keepShort -> existing!!.shortAction
                        // MP Manager find/replace keeps the full typed envelope so
                        // the V1 export can restore find text (c), replacement (f)
                        // and option flags (h) losslessly.
                        shortOption.legacyId == MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID ->
                            SymbolAction.Extension(
                                MpManagerFindReplaceEnvelope.EXTENSION_ID,
                                MpManagerFindReplaceEnvelope.encodePayload(
                                    shortPayload,
                                    shortFindReplace.replacementField.text.toString()
                                        .takeIf { it.isNotEmpty() },
                                    shortFindReplace.currentOptions()
                                )
                            )
                        shortOption.builtin != null ->
                            SymbolAction.EditorCommand(shortOption.builtin.commandId, shortPayload)
                        shortOption.extension != null ->
                            SymbolAction.Extension(shortOption.extension.id, shortPayload)
                        shortOption.legacyId != null ->
                            LegacySymbolActionAdapter.convert(LegacySymbolAction(shortOption.legacyId, shortPayload))
                        else -> SymbolAction.InsertText("")
                    }
                    val shortLegacyText = when {
                        keepShort -> existing?.legacyShortText
                        shortId?.let(LegacySymbolActionCatalog::hasTextPayload) == true -> shortPayload
                        else -> null
                    }

                    val longOption = longOptions.getOrNull(longSelectedIndex)
                        ?: error(context.getString(R.string.symbol_drawer_216))
                    val keepLong = longOption.keepCurrent
                    val longId = if (keepLong) existing?.legacyLongActionId else longOption.legacyId
                    val longPayload = if (longOption.hasPayload) {
                        longText.text.toString().takeIf { it.isNotEmpty() }
                    } else null
                    val longTypedAction = when {
                        keepLong -> existing?.longAction
                        longOption.none -> null
                        // Long-press find/replace persists the same typed envelope
                        // shape, with its own independent fields and option flags.
                        longOption.legacyId == MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID ->
                            SymbolAction.Extension(
                                MpManagerFindReplaceEnvelope.EXTENSION_ID,
                                MpManagerFindReplaceEnvelope.encodePayload(
                                    longPayload,
                                    longFindReplace.replacementField.text.toString()
                                        .takeIf { it.isNotEmpty() },
                                    longFindReplace.currentOptions()
                                )
                            )
                        longOption.builtin != null ->
                            SymbolAction.EditorCommand(longOption.builtin.commandId, longPayload)
                        longOption.extension != null ->
                            SymbolAction.Extension(longOption.extension.id, longPayload)
                        longOption.legacyId != null ->
                            LegacySymbolActionAdapter.convert(LegacySymbolAction(longOption.legacyId, longPayload))
                        else -> null
                    }
                    val longLegacyText = when {
                        keepLong -> existing?.legacyLongText
                        longId?.let(LegacySymbolActionCatalog::hasTextPayload) == true -> longPayload
                        else -> null
                    }
                    val symbol = SymbolEntry(
                        id = existing?.id ?: 0L,
                        label = display,
                        shortAction = shortTypedAction,
                        longAction = longTypedAction,
                        legacyShortActionId = shortId,
                        legacyShortText = shortLegacyText,
                        legacyLongActionId = longId,
                        legacyLongText = longLegacyText,
                        icon = existing?.icon
                    )
                    if (existing == null) manager.addSymbol(group.id, symbol)
                    else manager.updateSymbol(group.id, symbol)
                    refresh()
                }.onFailure(::reportError)
            }
            .showAdaptiveDialog()
    }

    /**
     * Resolves the V1 picker row for the persisted typed action. The generic
     * legacy-payload comparison cannot resolve the MP Manager find/replace
     * entry (action 101 persists as the full JSON envelope extension, never as
     * the plain find-text payload), so legacy.action.101 extensions are matched
     * by their extension ID explicitly. Unknown actions still stay on the
     * keep-current path instead of being silently re-typed.
     */
    private fun resolveLegacyPickerIndex(
        action: SymbolAction?,
        legacyId: Int?,
        legacyPayload: String?
    ): Int? {
        LegacyActionPickerResolver.legacyActionIndex(action, legacyId, legacyPayload)?.let { return it }
        if (MpManagerFindReplaceEnvelope.isFindReplaceExtension(action)) {
            return LegacySymbolActionCatalog.values
                .indexOf(MpManagerFindReplaceEnvelope.LEGACY_ACTION_ID)
                .takeIf { it >= 0 }
        }
        return null
    }

    /**
     * Typed MP Manager find/replace payload of an existing action, used to
     * prefill the 101 editor. A legacy.action.101 extension without a valid
     * envelope (for example a plain find-text payload written by an older
     * build) is surfaced as find text only, matching how the host executes
     * such payloads.
     */
    private fun findReplacePayload(action: SymbolAction?): MpManagerFindReplaceEnvelope.Payload? {
        if (!MpManagerFindReplaceEnvelope.isFindReplaceExtension(action)) return null
        val extension = action as SymbolAction.Extension
        return MpManagerFindReplaceEnvelope.decodePayload(extension.payload)
            ?: MpManagerFindReplaceEnvelope.Payload(extension.payload, null, null)
    }

    /** Builds the replacement field plus the independent r/c/a option checkboxes. */
    private fun buildFindReplaceEditor(): FindReplaceEditor {
        val container = LinearLayout(context).apply { orientation = VERTICAL }
        val replacementLayout = managerOutlinedTextField(
            context,
            context.getString(R.string.symbol_drawer_231),
            multiline = true
        )
        val replacementField = replacementLayout.editText as TextInputEditText
        container.addView(replacementLayout, matchWrap())
        fun optionToggle(labelRes: Int) = MaterialCheckBox(context).apply {
            text = context.getString(labelRes)
            textSize = 13f
        }
        val regexToggle = optionToggle(R.string.symbol_drawer_232)
        val matchCaseToggle = optionToggle(R.string.symbol_drawer_233)
        val replaceAllToggle = optionToggle(R.string.symbol_drawer_234)
        container.addView(regexToggle, matchWrap())
        container.addView(matchCaseToggle, matchWrap())
        container.addView(replaceAllToggle, matchWrap())
        return FindReplaceEditor(container, replacementField, regexToggle, matchCaseToggle, replaceAllToggle)
    }

    private fun showSymbolMenu(symbol: SymbolEntry, anchor: View) {
        val menu = PopupMenu(context, anchor)
        menu.menu.add(if (symbol.id in favoriteIds) context.getString(R.string.symbol_drawer_058) else context.getString(R.string.symbol_drawer_048))
            .setOnMenuItemClickListener { toggleFavorite(symbol); true }
        menu.menu.add(context.getString(R.string.symbol_drawer_162)).setOnMenuItemClickListener {
            markRecentlyUsed(symbol)
            refreshSymbols()
            true
        }
        menu.menu.add(context.getString(R.string.symbol_drawer_200)).setOnMenuItemClickListener { editSymbol(symbol); true }
        menu.menu.add(context.getString(R.string.symbol_drawer_069)).setOnMenuItemClickListener {
            currentGroup()?.let { manager.copySymbol(it.id, symbol.id, it.id); refresh() }
            true
        }
        menu.menu.add(context.getString(R.string.symbol_drawer_182)).setOnMenuItemClickListener { promptMoveSymbol(symbol); true }
        menu.menu.add(context.getString(R.string.symbol_drawer_024)).setOnMenuItemClickListener { moveSymbol(symbol, -1); true }
        menu.menu.add(context.getString(R.string.symbol_drawer_025)).setOnMenuItemClickListener { moveSymbol(symbol, 1); true }
        menu.menu.add(context.getString(R.string.symbol_drawer_044)).setOnMenuItemClickListener {
            confirmDeleteSymbol(symbol)
            true
        }
        menu.show()
    }

    private fun confirmDeleteSymbol(symbol: SymbolEntry) {
        val group = currentGroup() ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.symbol_drawer_047))
            .setMessage(context.getString(R.string.symbol_drawer_179, symbol.label))
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .setPositiveButton(context.getString(R.string.symbol_drawer_044)) { _, _ ->
                val previous = manager.groups.toList()
                val previousUsage = snapshotUsageState()
                val previousSelection = selectedGroupId
                runCatching {
                    manager.removeSymbol(group.id, symbol.id)
                    pruneUsageState()
                    refresh()
                    showUndo(context.getString(R.string.symbol_drawer_090, symbol.label)) {
                        manager.replaceAll(previous)
                        restoreUsageState(previousUsage)
                        selectedGroupId = previousSelection
                    }
                }.onFailure(::reportError)
            }
            .showAdaptiveDialog()
    }

    private fun moveSymbol(symbol: SymbolEntry, delta: Int) {
        val group = currentGroup() ?: return
        val index = group.symbols.indexOfFirst { it.id == symbol.id }
        val target = index + delta
        if (index >= 0 && target in group.symbols.indices) {
            manager.moveSymbol(group.id, index, target)
            refresh()
        }
    }

    private fun promptMoveSymbol(symbol: SymbolEntry) {
        val source = currentGroup() ?: return
        val targets = manager.groups.filter { it.id != source.id }
        if (targets.isEmpty()) {
            toast(context.getString(R.string.symbol_drawer_167))
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.symbol_drawer_182))
            .setItems(targets.map { it.title }.toTypedArray()) { _, which ->
                runCatching { manager.moveSymbolToGroup(source.id, symbol.id, targets[which].id); refresh() }
                    .onFailure(::reportError)
            }
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .showAdaptiveDialog()
    }

    private fun MaterialAlertDialogBuilder.showAdaptiveDialog(): AlertDialog {
        val dialog = create()
        dialog.setOnShowListener {
            val density = resources.displayMetrics.density
            val root = this@SymbolDrawerManagerScreen.rootView
            val widthPx = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val heightPx = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val window = WindowAdaptiveInfo(widthPx / density, heightPx / density, density)
            val dialogWidth = AdaptiveContentLayoutResolver.resolveDialogWidthPx(
                windowWidthPx = widthPx,
                density = density,
                window = window
            )
            dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }

    private fun horizontalRow() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    private fun actionButton(title: String, action: () -> Unit) = managerTextButton(context).apply {
        text = title
        textSize = 12f
        isAllCaps = false
        minWidth = 0
        insetTop = 0
        insetBottom = 0
        setPadding(dp(8), dp(2), dp(8), dp(2))
        setOnClickListener { runCatching(action).onFailure(::reportError) }
    }

    private fun invalidateUndo() {
        pendingUndo = null
        undoBar.visibility = View.GONE
    }

    private fun showUndo(message: String, restore: () -> Unit) {
        pendingUndo = restore
        undoMessage.text = message
        undoBar.visibility = View.VISIBLE
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    private fun reportError(error: Throwable) {
        onError?.invoke(error)
        toast(error.message ?: context.getString(R.string.symbol_drawer_137))
    }

    private fun matchWrap() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private enum class ManagerSymbolFilter { ALL, FAVORITES, RECENT }

    private companion object {
        const val GROUP_PAGE_PREFIX = "symbol-group:"
    }

    private class SymbolAdapter(
        private val context: Context,
        private val onClick: (SymbolEntry) -> Unit,
        private val onLongClick: (SymbolEntry, View) -> Unit,
        private val onDragHandle: (RecyclerView.ViewHolder) -> Unit,
        private val themeProvider: () -> SymbolDrawerTheme?,
        private val isBatchMode: () -> Boolean,
        private val isSelected: (SymbolEntry) -> Boolean,
        private val onBatchToggle: (SymbolEntry) -> Unit
    ) : ListAdapter<SymbolEntry, SymbolHolder>(DIFF) {
        fun rebindTheme() {
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolHolder {
            val ctx = parent.context
            val title = TextView(ctx).apply {
                textSize = 15f
                setTextColor(themeProvider()?.onSurface ?: HostThemeColors.onSurface(ctx))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val detail = TextView(ctx).apply {
                textSize = 12f
                setTextColor(themeProvider()?.secondaryText ?: HostThemeColors.secondaryText(ctx))
                setPadding(0, dp(ctx, 4), 0, 0)
            }
            val textColumn = LinearLayout(ctx).apply {
                orientation = VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(detail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val root = LinearLayout(ctx).apply {
                orientation = HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = roundedSurface(ctx, themeProvider()?.surface ?: HostThemeColors.surface(ctx), dp(ctx, 12))
                addView(View(ctx).apply {
                    setBackgroundColor(themeProvider()?.primary ?: HostThemeColors.primary(ctx))
                }, LinearLayout.LayoutParams(dp(ctx, 3), ViewGroup.LayoutParams.MATCH_PARENT))
                addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val dragHandle = TextView(ctx).apply {
                    text = "⋮⋮"
                    textSize = 20f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(themeProvider()?.secondaryText ?: HostThemeColors.secondaryText(ctx))
                    contentDescription = context.getString(R.string.symbol_drawer_130)
                    isClickable = true
                    isFocusable = true
                }
                addView(dragHandle, LinearLayout.LayoutParams(dp(ctx, 40), ViewGroup.LayoutParams.MATCH_PARENT))
                isClickable = true
                isFocusable = true
                val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(ctx, 7)
                layoutParams = lp
            }
            val dragHandle = root.getChildAt(root.childCount - 1) as TextView
            return SymbolHolder(root, title, detail, dragHandle)
        }

        override fun onBindViewHolder(holder: SymbolHolder, position: Int) {
            val symbol = getItem(position)
            holder.title.text = if (symbol.icon.isNullOrEmpty()) symbol.label else "${symbol.icon}  ${symbol.label}"
            val shortName = symbol.legacyShortActionId?.let { actionName(context, it) } ?: symbol.shortAction.javaClass.simpleName
            val longName = symbol.legacyLongActionId?.let { actionName(context, it) } ?: symbol.longAction?.javaClass?.simpleName ?: context.getString(R.string.symbol_drawer_150)
            holder.detail.text = context.getString(R.string.symbol_drawer_176, shortName, longName)
            holder.root.contentDescription = context.getString(R.string.symbol_drawer_001, symbol.label, shortName, longName)
            val theme = themeProvider()
            val itemContext = holder.root.context
            holder.root.background = roundedSurface(itemContext, theme?.surface ?: HostThemeColors.surface(itemContext), dp(itemContext, 12))
            holder.title.setTextColor(theme?.onSurface ?: HostThemeColors.onSurface(itemContext))
            holder.detail.setTextColor(theme?.secondaryText ?: HostThemeColors.secondaryText(itemContext))
            holder.dragHandle.setTextColor(theme?.secondaryText ?: HostThemeColors.secondaryText(itemContext))
            holder.root.getChildAt(0).setBackgroundColor(theme?.primary ?: HostThemeColors.primary(itemContext))
            val selecting = isBatchMode()
            val selected = isSelected(symbol)
            holder.root.alpha = if (selecting && selected) 0.72f else 1f
            holder.title.text = (if (selecting) (if (selected) "☑ " else "□ ") else "") +
                (if (symbol.icon.isNullOrEmpty()) symbol.label else "${symbol.icon}  ${symbol.label}")
            holder.root.setOnClickListener {
                if (isBatchMode()) onBatchToggle(symbol) else onClick(symbol)
            }
            holder.root.setOnLongClickListener {
                if (isBatchMode()) onBatchToggle(symbol) else onLongClick(symbol, holder.root)
                true
            }
            holder.dragHandle.visibility = if (selecting) View.GONE else View.VISIBLE
            holder.dragHandle.isEnabled = true
            holder.dragHandle.alpha = if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) 0.45f else 1f
            // Keep ownership of the complete pointer stream. Returning false
            // for MOVE/UP after consuming DOWN lets the handle's normal touch
            // handling compete with ItemTouchHelper and can terminate an
            // upward drag before the finger is released.
            var dragPointerActive = false
            holder.dragHandle.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        dragPointerActive = true
                        onDragHandle(holder)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE,
                    android.view.MotionEvent.ACTION_POINTER_DOWN,
                    android.view.MotionEvent.ACTION_POINTER_UP -> dragPointerActive
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        val consumed = dragPointerActive
                        dragPointerActive = false
                        consumed
                    }
                    else -> dragPointerActive
                }
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<SymbolEntry>() {
                override fun areItemsTheSame(old: SymbolEntry, new: SymbolEntry) = old.id == new.id
                override fun areContentsTheSame(old: SymbolEntry, new: SymbolEntry) = old == new
            }
            private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
            private fun actionName(context: Context, id: Int): String {
                val index = LegacySymbolActionCatalog.values.indexOf(id)
                return LegacySymbolActionCatalog.labels(context).getOrNull(index) ?: context.getString(R.string.symbol_drawer_049, id)
            }
        }

        private fun roundedSurface(context: Context, color: Int, radius: Int) =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = radius.toFloat()
                setStroke(1, themeProvider()?.outline ?: HostThemeColors.outline(context))
            }
    }

    private fun roundedSurface(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), outlineColor())
    }
    private fun surfaceColor() = drawerTheme?.surface ?: HostThemeColors.surface(context)
    private fun onSurfaceColor() = drawerTheme?.onSurface ?: HostThemeColors.onSurface(context)
    private fun primaryColor() = drawerTheme?.primary ?: HostThemeColors.primary(context)
    private fun secondaryTextColor() = drawerTheme?.secondaryText ?: HostThemeColors.secondaryText(context)
    private fun outlineColor() = drawerTheme?.outline ?: HostThemeColors.outline(context)

}
