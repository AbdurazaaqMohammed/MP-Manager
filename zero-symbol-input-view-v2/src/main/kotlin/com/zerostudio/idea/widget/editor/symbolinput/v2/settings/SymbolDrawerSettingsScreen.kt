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

package com.zerostudio.idea.widget.editor.symbolinput.v2.settings

import com.zerostudio.idea.widget.editor.symbolinput.v2.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.roundToInt
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SharedPreferencesSymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolGroupBundleCodec
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.ui.HostThemeColors
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowAdaptiveInfo
import com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.AdaptiveContentLayoutResolver
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerExtensionRegistry
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerSettingsScope
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme
import com.zerostudio.idea.widget.editor.symbolinput.v2.SymbolDrawerView

/**
 * Settings-only surface. Symbol-group editing stays in the dedicated manager
 * dialog; this screen owns the two backup/import workflows.
 */
class SymbolDrawerSettingsScreen(
    context: Context,
    private val repository: SymbolDrawerSettingsRepository =
        SharedPreferencesSymbolDrawerSettingsRepository(context),
    private val groupRepository: SymbolGroupRepository =
        SharedPreferencesSymbolGroupRepository(context),
    /** Optional session registry for app-contributed settings tabs and rows. */
    private val extensionRegistry: SymbolDrawerExtensionRegistry? = null
) : LinearLayout(context) {

    var onSettingsChanged: ((SymbolDrawerSettings) -> Unit)? = null
    var onGroupsChanged: ((List<SymbolGroup>) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    private var current = repository.load().settings.copy(themeMode = ThemeMode.SYSTEM)
    private var activePage: LinearLayout? = null
    private var pendingImport: ImportKind = ImportKind.SETTINGS
    private var adaptiveSpacingScale: Float = 1f
    private var adaptiveTypographyScale: Float = 1f
    private var selectedTabIndex: Int = 0
    private var activeScrollView: ScrollView? = null
    private var spacingReflowInProgress = false
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private var extensionObserver: AutoCloseable? = null
    private var themeObserver: AutoCloseable? = null
    private var themeSource: SymbolDrawerView? = null
    private var drawerTheme: SymbolDrawerTheme? = null
    private val extensionViewDisposers = mutableListOf<() -> Unit>()
    private var extensionViewsDisposedOnDetach = false
    private var detachedScrollY = 0
    private val documentPicker = JsonDocumentPicker(
        context,
        onImported = ::handleImportedJson,
        onError = { onError?.invoke(it); toast(context.getString(R.string.symbol_drawer_file_error, it.message ?: context.getString(R.string.symbol_drawer_unknown_error))) }
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(surfaceColor())
        setPadding(dp(12), dp(12), dp(12), dp(8))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        render()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        val density = resources.displayMetrics.density
        val window = WindowAdaptiveInfo(w / density, h / density, density)
        val spec = AdaptiveContentLayoutResolver.resolve(window)
        val nextSpacingScale = when (window.sizeClass) {
            com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowSizeClass.COMPACT -> 0.96f
            com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowSizeClass.MEDIUM -> 1f
            com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowSizeClass.EXPANDED -> 1.08f
        }
        val nextTypographyScale = when (window.sizeClass) {
            com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowSizeClass.COMPACT -> 0.98f
            com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowSizeClass.MEDIUM -> 1f
            com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive.WindowSizeClass.EXPANDED -> 1.04f
        }
        val metricsChanged = nextSpacingScale != adaptiveSpacingScale ||
            nextTypographyScale != adaptiveTypographyScale
        adaptiveSpacingScale = nextSpacingScale
        adaptiveTypographyScale = nextTypographyScale
        val horizontal = dp(spec.horizontalPaddingDp.toInt())
        setPadding(horizontal, dp(12), horizontal, dp(8))
        gravity = Gravity.CENTER_HORIZONTAL
        val params = content.layoutParams as? LayoutParams ?: return
        val targetWidth = minOf((w - horizontal * 2).coerceAtLeast(0), dp(spec.maxContentWidthDp.toInt()))
        if (params.width != targetWidth) {
            params.width = targetWidth
            content.layoutParams = params
        }
        if (metricsChanged && !spacingReflowInProgress && content.childCount > 0) {
            spacingReflowInProgress = true
            val previousScroll = activeScrollView?.scrollY ?: 0
            render()
            activeScrollView?.post { activeScrollView?.scrollTo(0, previousScroll) }
            spacingReflowInProgress = false
        }
    }

    /** Bind the settings surface to the owning drawer without changing its constructor ABI. */
    fun bindThemeSource(drawer: SymbolDrawerView) {
        if (themeSource === drawer) return
        themeObserver?.close()
        themeObserver = null
        themeSource = drawer
        drawerTheme = drawer.themeSnapshot
        if (isAttachedToWindow) observeDrawerTheme()
    }

    private fun observeDrawerTheme() {
        if (themeObserver != null) return
        themeObserver = themeSource?.observeTheme(emitCurrent = false) { theme ->
            drawerTheme = theme
            post { if (isAttachedToWindow) render() }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (extensionViewsDisposedOnDetach) {
            extensionViewsDisposedOnDetach = false
            val restoreScroll = detachedScrollY
            post {
                if (isAttachedToWindow) {
                    render()
                    activeScrollView?.post { activeScrollView?.scrollTo(0, restoreScroll) }
                }
            }
        }
        observeDrawerTheme()
        if (extensionObserver == null) {
            extensionObserver = extensionRegistry?.observe(emitCurrent = false) {
                post { if (isAttachedToWindow) render() }
            }
        }
    }

    override fun onDetachedFromWindow() {
        detachedScrollY = activeScrollView?.scrollY ?: detachedScrollY
        extensionObserver?.close()
        extensionObserver = null
        themeObserver?.close()
        themeObserver = null
        disposeExtensionViews()
        extensionViewsDisposedOnDetach = true
        super.onDetachedFromWindow()
    }

    private fun disposeExtensionViews() {
        val pending = extensionViewDisposers.toList()
        extensionViewDisposers.clear()
        pending.forEach { dispose -> runCatching(dispose) }
    }

    private fun addExtensionView(
        page: LinearLayout,
        view: View,
        extensionId: String,
        dispose: () -> Unit
    ) {
        val safeDispose: () -> Unit = {
            runCatching(dispose).onFailure { extensionRegistry?.reportError(extensionId, it) }
            Unit
        }
        runCatching { page.addView(view, matchWrap()) }
            .onSuccess { extensionViewDisposers += safeDispose }
            .onFailure { error ->
                extensionRegistry?.reportError(extensionId, error)
                safeDispose()
                page.addView(TextView(context).apply {
                    text = context.getString(R.string.symbol_drawer_124) + extensionId
                    setTextColor(android.graphics.Color.RED)
                }, matchWrap())
            }
    }

    fun reload() {
        current = repository.load().settings.copy(themeMode = ThemeMode.SYSTEM)
        render()
        onSettingsChanged?.invoke(current)
    }

    fun getSettings(): SymbolDrawerSettings = current

    private fun render() {
        disposeExtensionViews()
        content.removeAllViews()
        content.setBackgroundColor(surfaceColor())
        activePage = null
        activeScrollView = null

        content.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(spaceDp(18), spaceDp(16), spaceDp(18), spaceDp(16))
            background = surfaceBackground(surfaceColor(), 0.10f, 22)
            addView(TextView(context).apply {
                text = context.getString(R.string.symbol_drawer_185)
                textSize = textSp(23f)
                setTextColor(primaryColor())
                setTypeface(typeface, Typeface.BOLD)
            }, matchWrap())
            addView(TextView(context).apply {
                text = context.getString(R.string.symbol_drawer_131)
                textSize = textSp(13f)
                setTextColor(onSurfaceColor())
                alpha = 0.72f
                setPadding(0, dp(5), 0, 0)
            }, matchWrap())
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val extensionSnapshot = extensionRegistry?.snapshot()
        val extensionTabs = extensionSnapshot?.settingsTabs.orEmpty()
        val extensionSettings = extensionSnapshot?.settingsItems.orEmpty()
        val tabs = TabLayout(context).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
            setSelectedTabIndicatorColor(primaryColor())
            setTabTextColors(onSurfaceColor(), primaryColor())
            setBackgroundColor(surfaceColor())
        }
        content.addView(tabs, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        val pageHost = FrameLayout(context)
        content.addView(pageHost, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val builders: List<Pair<String, () -> Unit>> = listOf(
            context.getString(R.string.symbol_drawer_074) to {
                section(context.getString(R.string.symbol_drawer_026))
                addChoice(context.getString(R.string.symbol_drawer_027), listOf(ThemeMode.SYSTEM), ThemeMode.SYSTEM) {
                    update { copy(themeMode = ThemeMode.SYSTEM) }
                }
                addChoice(context.getString(R.string.symbol_drawer_041), GroupIndicatorStyle.values().toList(), current.indicatorStyle) {
                    update { copy(indicatorStyle = it) }
                }
                addSwitch(context.getString(R.string.symbol_drawer_152), current.showGroupTitles) { update { copy(showGroupTitles = it) } }
                addSwitch(context.getString(R.string.symbol_drawer_155), current.showSymbolLabels) { update { copy(showSymbolLabels = it) } }
                addSwitch(context.getString(R.string.symbol_drawer_184), current.showFullSymbolLabels) { update { copy(showFullSymbolLabels = it) } }
                addHint(context.getString(R.string.symbol_drawer_108))
                addSlider(context.getString(R.string.symbol_drawer_186), current.symbolLabelTextSizeSp, 8, 32) { update { copy(symbolLabelTextSizeSp = it) } }
                addSwitch(context.getString(R.string.symbol_drawer_153), current.showActionHints) { update { copy(showActionHints = it) } }
            },
            context.getString(R.string.symbol_drawer_103) to {
                section(context.getString(R.string.symbol_drawer_082))
                addSlider(context.getString(R.string.symbol_drawer_166), current.columns, 1, 30) { update { copy(columns = it) } }
                addSlider(context.getString(R.string.symbol_drawer_129), current.collapsedRows, 1, 16) { update { copy(collapsedRows = it) } }
                addSlider(context.getString(R.string.symbol_drawer_054), current.cellHeightDp, 6, 128) { update { copy(cellHeightDp = it) } }
                addSlider(context.getString(R.string.symbol_drawer_163), current.cellHorizontalSpacingDp, -24, 48) { update { copy(cellHorizontalSpacingDp = it) } }
                addSlider(context.getString(R.string.symbol_drawer_198), current.cellVerticalSpacingDp, -24, 48) { update { copy(cellVerticalSpacingDp = it) } }
                addSlider(context.getString(R.string.symbol_drawer_042), current.indicatorHeightDp, 0, 96) { update { copy(indicatorHeightDp = it) } }
                addSlider(context.getString(R.string.symbol_drawer_157), current.maxExpandedHeightDp, 80, 1800) { update { copy(maxExpandedHeightDp = it) } }
                addSlider(context.getString(R.string.symbol_drawer_158), current.minimumTouchTargetDp, 20, 112) { update { copy(minimumTouchTargetDp = it) } }
            },
            context.getString(R.string.symbol_drawer_104) to {
                section(context.getString(R.string.symbol_drawer_106))
                addHint(
                    context.getString(R.string.symbol_drawer_084) +
                        context.getString(R.string.symbol_drawer_029)
                )
            },
            context.getString(R.string.symbol_drawer_028) to {
                section(context.getString(R.string.symbol_drawer_122))
                addSlider(context.getString(R.string.symbol_drawer_225), current.longPressTimeoutMs, 250, 1000) { update { copy(longPressTimeoutMs = it) } }
                addSlider(context.getString(R.string.symbol_drawer_083), current.flingVelocityDpPerSecond, 100, 6000) { update { copy(flingVelocityDpPerSecond = it) } }
                addSwitch(context.getString(R.string.symbol_drawer_123), current.autoCollapseAfterAction) { update { copy(autoCollapseAfterAction = it) } }
                addSwitch(context.getString(R.string.symbol_drawer_208), current.rememberExpandedState) { update { copy(rememberExpandedState = it) } }
                addHint(context.getString(R.string.symbol_drawer_107))
                section(context.getString(R.string.symbol_drawer_051))
                addSlider(context.getString(R.string.symbol_drawer_052), current.animationDurationMs, 0, 1200) { update { copy(animationDurationMs = it) } }
                addSwitch(context.getString(R.string.symbol_drawer_037), current.reduceMotion) { update { copy(reduceMotion = it) } }
                addChoice(context.getString(R.string.symbol_drawer_207), HapticMode.values().toList(), current.hapticMode) { update { copy(hapticMode = it) } }
            },
            context.getString(R.string.symbol_drawer_119) to {
                section(context.getString(R.string.symbol_drawer_118))
                addSlider(context.getString(R.string.symbol_drawer_227), current.pageCacheLimit, 1, 9) { update { copy(pageCacheLimit = it) } }
                addSwitch(context.getString(R.string.symbol_drawer_228), current.prefetchAdjacentGroups) { update { copy(prefetchAdjacentGroups = it) } }
                section(context.getString(R.string.symbol_drawer_132))
                addSwitch(context.getString(R.string.symbol_drawer_133), current.searchIncludesActionPayload) {
                    update { copy(searchIncludesActionPayload = it) }
                }
            },
            context.getString(R.string.symbol_drawer_144) to {
                addDataManagementCard(
                    context.getString(R.string.symbol_drawer_209),
                    context.getString(R.string.symbol_drawer_065),
                    listOf(
                        context.getString(R.string.symbol_drawer_073) to ::copySettingsBackup,
                        context.getString(R.string.symbol_drawer_081) to ::exportSettingsFile,
                        context.getString(R.string.symbol_drawer_195) to ::promptImportSettings,
                        context.getString(R.string.symbol_drawer_031) to { pendingImport = ImportKind.SETTINGS; documentPicker.openJson() }
                    )
                )
                addDataManagementCard(
                    context.getString(R.string.symbol_drawer_189),
                    context.getString(R.string.symbol_drawer_053),
                    listOf(
                        context.getString(R.string.symbol_drawer_072) to ::copyGroupsBackup,
                        context.getString(R.string.symbol_drawer_080) to ::exportGroupsFile,
                        context.getString(R.string.symbol_drawer_194) to ::promptImportGroups,
                        context.getString(R.string.symbol_drawer_030) to { pendingImport = ImportKind.GROUPS; documentPicker.openJson() }
                    )
                )
                addDataSafetyNotice()
                activePage?.addView(actionButton(context.getString(R.string.symbol_drawer_121)) {
                    runCatching {
                        val defaults = SymbolDrawerSettings()
                        repository.save(defaults)
                        current = defaults
                        render()
                        onSettingsChanged?.invoke(current)
                        toast(context.getString(R.string.symbol_drawer_212))
                    }.onFailure(::reportFailure)
                }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50)).apply {
                    topMargin = spaceDp(14)
                })
            }
        )

        val totalTabCount = builders.size + extensionTabs.size
        selectedTabIndex = selectedTabIndex.coerceIn(0, (totalTabCount - 1).coerceAtLeast(0))
        val builtInTabIds = listOf("appearance", "layout", "tablet", "interaction", "performance-search", "data")
        val settingsScope = object : SymbolDrawerSettingsScope {
            override val current: SymbolDrawerSettings get() = this@SymbolDrawerSettingsScreen.current
            override fun update(transform: (SymbolDrawerSettings) -> SymbolDrawerSettings) {
                this@SymbolDrawerSettingsScreen.update { transform(this) }
            }
        }

        builders.forEachIndexed { index, entry ->
            tabs.addTab(tabs.newTab().setText(entry.first), index == selectedTabIndex)
            val page = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(spaceDp(16), spaceDp(8), spaceDp(16), spaceDp(28))
            }
            val scroll = ScrollView(context).apply {
                isFillViewport = true
                addView(page, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                visibility = if (index == selectedTabIndex) View.VISIBLE else View.GONE
            }
            pageHost.addView(scroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            if (index == selectedTabIndex) activeScrollView = scroll
            activePage = page
            entry.second.invoke()
            extensionSettings.asSequence()
                .filter { it.tabId == builtInTabIds.getOrNull(index) }
                .forEach { extension ->
                    runCatching { extension.createView(context, settingsScope) }
                        .onSuccess { view ->
                            addExtensionView(page, view, extension.id) { extension.onViewDisposed(view) }
                        }
                        .onFailure { error ->
                            extensionRegistry?.reportError(extension.id, error)
                            page.addView(TextView(context).apply {
                                text = context.getString(R.string.symbol_drawer_ext_settings_error, extension.title, error.message ?: context.getString(R.string.symbol_drawer_unknown_error))
                                setTextColor(android.graphics.Color.RED)
                            }, matchWrap())
                        }
                }
        }
        extensionTabs.forEachIndexed { extensionIndex, extension ->
            val index = builders.size + extensionIndex
            tabs.addTab(tabs.newTab().setText(extension.title), index == selectedTabIndex)
            val page = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(spaceDp(16), spaceDp(8), spaceDp(16), spaceDp(28))
            }
            val scroll = ScrollView(context).apply {
                isFillViewport = true
                addView(page, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                visibility = if (index == selectedTabIndex) View.VISIBLE else View.GONE
            }
            pageHost.addView(scroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            if (index == selectedTabIndex) activeScrollView = scroll
            runCatching { extension.createView(context, settingsScope) }
                .onSuccess { view ->
                    addExtensionView(page, view, extension.id) { extension.onViewDisposed(view) }
                }
                .onFailure { error ->
                    extensionRegistry?.reportError(extension.id, error)
                    page.addView(TextView(context).apply {
                        text = context.getString(R.string.symbol_drawer_ext_page_error, extension.title, error.message ?: "Unknown error")
                        setTextColor(android.graphics.Color.RED)
                    }, matchWrap())
                }
            extensionSettings.asSequence()
                .filter { it.tabId == extension.id }
                .forEach { setting ->
                    runCatching { setting.createView(context, settingsScope) }
                        .onSuccess { view ->
                            addExtensionView(page, view, setting.id) { setting.onViewDisposed(view) }
                        }
                        .onFailure { error -> page.addView(TextView(context).apply {
                            text = context.getString(R.string.symbol_drawer_setting_error, setting.title, error.message ?: "Unknown error")
                            setTextColor(android.graphics.Color.RED)
                        }, matchWrap()) }
                }
        }
        activePage = null
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTabIndex = tab.position
                activeScrollView = pageHost.getChildAt(tab.position) as? ScrollView
                for (i in 0 until pageHost.childCount) {
                    pageHost.getChildAt(i).visibility = if (i == tab.position) View.VISIBLE else View.GONE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    /**
     * Adds the backup safety notice at most once to the active settings page.
     * This also protects against repeated extension-driven page composition
     * accidentally invoking the data-page builder more than once.
     */
    private fun addDataSafetyNotice() {
        val page = activePage ?: return
        val notice = context.getString(R.string.symbol_drawer_143)
        val tag = "zero.symbol.drawer.data-safety-notice"
        val existing = mutableListOf<View>()
        fun collect(parent: ViewGroup) {
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                if (child.tag == tag || (child is TextView && child.text?.toString() == notice)) {
                    existing += child
                }
                if (child is ViewGroup) collect(child)
            }
        }
        collect(page)
        if (existing.isNotEmpty()) {
            existing.drop(1).forEach { duplicate ->
                (duplicate.parent as? ViewGroup)?.removeView(duplicate)
            }
            return
        }
        page.addView(TextView(context).apply {
            this.tag = tag
            text = notice
            textSize = textSp(12f)
            setTextColor(onSurfaceColor())
            alpha = 0.72f
            setPadding(spaceDp(4), spaceDp(12), spaceDp(4), spaceDp(8))
        }, matchWrap())
    }

    private fun addDataManagementCard(
        title: String,
        description: String,
        actions: List<Pair<String, () -> Unit>>
    ) {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(spaceDp(14), spaceDp(14), spaceDp(14), spaceDp(12))
            background = surfaceBackground(surfaceColor(), 0.10f, 18)
        }
        card.addView(TextView(context).apply {
            text = title
            textSize = textSp(16f)
            setTextColor(onSurfaceColor())
            setTypeface(typeface, Typeface.BOLD)
        }, matchWrap())
        card.addView(TextView(context).apply {
            text = description
            textSize = textSp(12f)
            setTextColor(onSurfaceColor())
            alpha = 0.72f
            setPadding(0, spaceDp(5), 0, spaceDp(12))
        }, matchWrap())
        actions.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            pair.forEachIndexed { index, action ->
                row.addView(actionButton(action.first, action.second), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index == 0) marginEnd = spaceDp(5) else marginStart = spaceDp(5)
                })
            }
            card.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = spaceDp(8)
            })
        }
        activePage?.addView(card, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = spaceDp(12)
        })
    }

    private fun actionButton(title: String, action: () -> Unit) =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = title
            textSize = textSp(12f)
            isAllCaps = false
            // Shape, stroke, tint and text colors come from the host Material3
            // theme; no hardcoded colors or corner radii are set here.
            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            setOnClickListener { runCatching(action).onFailure(::reportFailure) }
        }

    private fun section(title: String) {
        activePage?.addView(TextView(context).apply {
            text = title
            textSize = textSp(17f)
            setTextColor(onSurfaceColor())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(spaceDp(2), spaceDp(22), spaceDp(2), spaceDp(10))
        }, matchWrap())
    }

    private fun addHint(message: String) {
        activePage?.addView(TextView(context).apply {
            text = message
            textSize = textSp(12f)
            setTextColor(onSurfaceColor())
            alpha = 0.72f
            setPadding(spaceDp(6), spaceDp(2), spaceDp(6), spaceDp(12))
        }, matchWrap())
    }

    private fun addSwitch(title: String, checked: Boolean, changed: (Boolean) -> Unit) {
        activePage?.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(spaceDp(14), spaceDp(5), spaceDp(10), spaceDp(5))
            background = surfaceBackground(surfaceColor(), 0.08f, 14)
            addView(TextView(context).apply {
                text = title
                textSize = textSp(14f)
                setTextColor(onSurfaceColor())
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(MaterialSwitch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, value -> changed(value) }
            }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = spaceDp(8)
        })
    }

    private fun <T : Enum<T>> addChoice(title: String, values: List<T>, selected: T, changed: (T) -> Unit) {
        val initialIndex = values.indexOf(selected).coerceAtLeast(0)
        val labels = values.map { value ->
            when (value) {
                ThemeMode.SYSTEM -> context.getString(R.string.symbol_drawer_217)
                LargeScreenLayoutMode.AUTO -> context.getString(R.string.symbol_drawer_205)
                LargeScreenLayoutMode.BOTTOM -> context.getString(R.string.symbol_drawer_063)
                LargeScreenLayoutMode.SIDE -> context.getString(R.string.symbol_drawer_062)
                SideDockEdge.LEFT -> context.getString(R.string.symbol_drawer_086)
                SideDockEdge.RIGHT -> context.getString(R.string.symbol_drawer_060)
                GroupIndicatorStyle.TEXT, GroupIndicatorStyle.TEXT_TABS -> context.getString(R.string.symbol_drawer_146)
                GroupIndicatorStyle.PILL -> context.getString(R.string.symbol_drawer_203)
                GroupIndicatorStyle.DOTS -> context.getString(R.string.symbol_drawer_043)
                GroupIndicatorStyle.UNDERLINE -> context.getString(R.string.symbol_drawer_105)
                GroupIndicatorStyle.GOOEY_METABALL -> context.getString(R.string.symbol_drawer_169)
                GroupIndicatorStyle.ELASTIC_WORM -> context.getString(R.string.symbol_drawer_111)
                GroupIndicatorStyle.SPRING_BLOB -> context.getString(R.string.symbol_drawer_112)
                GroupIndicatorStyle.LIQUID_MERGE -> context.getString(R.string.symbol_drawer_170)
                GroupIndicatorStyle.VERNIER_TICKS -> context.getString(R.string.symbol_drawer_197)
                GroupIndicatorStyle.MECHANICAL_RULER -> context.getString(R.string.symbol_drawer_160)
                GroupIndicatorStyle.WHEEL_ROLLER -> context.getString(R.string.symbol_drawer_002)
                GroupIndicatorStyle.CAMERA_DIAL -> context.getString(R.string.symbol_drawer_175)
                GroupIndicatorStyle.RADIAL_RING -> context.getString(R.string.symbol_drawer_174)
                GroupIndicatorStyle.RADAR_SWEEP -> context.getString(R.string.symbol_drawer_226)
                GroupIndicatorStyle.HUD_ARC -> context.getString(R.string.symbol_drawer_003)
                GroupIndicatorStyle.ORBIT_DOTS -> context.getString(R.string.symbol_drawer_218)
                GroupIndicatorStyle.ELASTIC_WAVE -> context.getString(R.string.symbol_drawer_110)
                GroupIndicatorStyle.SINE_WAVE -> context.getString(R.string.symbol_drawer_164)
                GroupIndicatorStyle.BOUNCE_TRACK -> context.getString(R.string.symbol_drawer_114)
                GroupIndicatorStyle.SPRING_SLIDER -> context.getString(R.string.symbol_drawer_113)
                GroupIndicatorStyle.MORPHING_BAR -> context.getString(R.string.symbol_drawer_116)
                GroupIndicatorStyle.SEGMENTED_TRACK -> context.getString(R.string.symbol_drawer_038)
                GroupIndicatorStyle.PROGRESS_LINE -> context.getString(R.string.symbol_drawer_219)
                GroupIndicatorStyle.DOUBLE_TRACK -> context.getString(R.string.symbol_drawer_055)
                GroupIndicatorStyle.MINIMAL_DASHES -> context.getString(R.string.symbol_drawer_161)
                GroupIndicatorStyle.SQUARE_TICKS -> context.getString(R.string.symbol_drawer_149)
                GroupIndicatorStyle.DIAMOND_MARKERS -> context.getString(R.string.symbol_drawer_206)
                GroupIndicatorStyle.TRIANGLE_POINTER -> context.getString(R.string.symbol_drawer_023)
                GroupIndicatorStyle.HEXAGON_NODES -> context.getString(R.string.symbol_drawer_036)
                GroupIndicatorStyle.GLOWING_NODES -> context.getString(R.string.symbol_drawer_056)
                GroupIndicatorStyle.PULSE_DOTS -> context.getString(R.string.symbol_drawer_204)
                GroupIndicatorStyle.STACKED_CARDS -> context.getString(R.string.symbol_drawer_059)
                GroupIndicatorStyle.MINI_PREVIEW -> context.getString(R.string.symbol_drawer_117)
                GroupIndicatorStyle.THUMBNAIL_STRIP -> context.getString(R.string.symbol_drawer_202)
                GroupIndicatorStyle.NUMBER_BADGES -> context.getString(R.string.symbol_drawer_141)
                GroupIndicatorStyle.VERTICAL_RAIL -> context.getString(R.string.symbol_drawer_064)
                GroupIndicatorStyle.ARC_NODES -> context.getString(R.string.symbol_drawer_109)
                GroupIndicatorStyle.FLUID_TRAIL -> context.getString(R.string.symbol_drawer_168)
                GroupIndicatorStyle.DIGITAL_COUNTER -> context.getString(R.string.symbol_drawer_142)
                GroupIndicatorStyle.STEPPER -> context.getString(R.string.symbol_drawer_165)
                else -> value.name.lowercase().replace('_', ' ')
            }
        }
        var lastPosition = initialIndex
        val field = MaterialAutoCompleteTextView(context).apply {
            // Select-only exposed dropdown: the dropdown-menu end icon delegate
            // wires the tap gesture, so free-text filtering is not allowed.
            // Mirrors the previous Spinner interaction.
            inputType = InputType.TYPE_NULL
            setAdapter(
                ArrayAdapter(
                    context,
                    com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                    labels
                )
            )
            setText(labels[initialIndex], false)
            setOnItemClickListener { _, _, position, _ ->
                if (position != lastPosition) {
                    lastPosition = position
                    values.getOrNull(position)?.let(changed)
                }
            }
        }
        val fieldLayout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputStyle).apply {
            hint = title
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            // The field is added before the end icon mode so the dropdown-menu
            // delegate can attach its gesture listeners to the EditText.
            addView(field, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        activePage?.addView(fieldLayout, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = spaceDp(8)
        })
    }

    private fun addSlider(title: String, value: Int, minimum: Int, maximum: Int, changed: (Int) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(spaceDp(10), spaceDp(6), spaceDp(10), spaceDp(6))
            background = surfaceBackground(surfaceColor(), 0.05f, 12)
        }
        val valueLabel = label("$title：$value")
        row.addView(valueLabel, matchWrap())
        val initialValue = value.toFloat().coerceIn(minimum.toFloat(), maximum.toFloat())
        row.addView(Slider(context).apply {
            // Negative minima (spacing sliders) and integer step granularity
            // reproduce the previous SeekBar behavior exactly.
            valueFrom = minimum.toFloat()
            valueTo = maximum.toFloat()
            setStepSize(1f)
            // Wide ranges (for example 80..1800) would draw a tick per step;
            // hide ticks while keeping the 1-unit snapping.
            setTickVisible(false)
            setLabelBehavior(LabelFormatter.LABEL_GONE)
            // Qualify explicitly: the function parameter `value` (Int) shadows
            // the Slider's Float property inside this apply block.
            this.value = initialValue
            var trackingTouch = false
            addOnChangeListener { _, raw, fromUser ->
                val next = raw.roundToInt().coerceIn(minimum, maximum)
                valueLabel.text = "$title：$next"
                // Keyboard and accessibility adjustments do not dispatch
                // onStart/onStopTrackingTouch. Persist those user changes
                // immediately, while touch drags still commit once on release.
                if (fromUser && !trackingTouch) changed(next)
            }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    trackingTouch = true
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    trackingTouch = false
                    changed(slider.value.roundToInt().coerceIn(minimum, maximum))
                }
            })
        }, matchWrap())
        activePage?.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = spaceDp(8)
        })
    }

    private fun copySettingsBackup() = copyJson(
        "Zero Symbol Drawer settings", SymbolDrawerSettingsCodec.encode(current), context.getString(R.string.symbol_drawer_210)
    )

    private fun exportSettingsFile() = documentPicker.saveJson(
        "symbol-drawer-settings.json", SymbolDrawerSettingsCodec.encode(current)
    )

    private fun copyGroupsBackup() = copyJson(
        "Zero Symbol Drawer groups", SymbolGroupBundleCodec.encode(groupRepository.loadGroups()), context.getString(R.string.symbol_drawer_188)
    )

    private fun exportGroupsFile() = documentPicker.saveJson(
        "symbol-drawer-groups.json", SymbolGroupBundleCodec.encode(groupRepository.loadGroups())
    )

    private fun copyJson(label: String, json: String, message: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, json))
        toast(message)
    }

    private fun promptImportSettings() {
        promptJson(context.getString(R.string.symbol_drawer_079), context.getString(R.string.symbol_drawer_196)) { json ->
            importSettings(json)
        }
    }

    private fun promptImportGroups() {
        promptJson(context.getString(R.string.symbol_drawer_078), context.getString(R.string.symbol_drawer_193)) { json ->
            confirmGroupImport(json)
        }
    }

    private fun promptJson(title: String, message: String, imported: (String) -> Unit) {
        val clipText = try {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (cm != null && cm.hasPrimaryClip() && (cm.primaryClip?.itemCount ?: 0) > 0) {
                cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
            } else ""
        } catch (e: Exception) { "" }

        val field = TextInputLayout(context, null, com.google.android.material.R.attr.textInputStyle).apply {
            hint = context.getString(R.string.symbol_drawer_192)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            val editText = TextInputEditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 6
                gravity = Gravity.TOP
                if (clipText.isNotBlank()) {
                    setText(clipText)
                    setSelection(clipText.length)
                }
            }
            addView(
                editText,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(field)
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .setPositiveButton(context.getString(R.string.symbol_drawer_199)) { _, _ ->
                val text = field.editText?.text?.toString().orEmpty()
                runCatching { imported(text) }.onFailure(::reportFailure)
            }
            .showAdaptiveDialog()
    }

    private fun handleImportedJson(json: String) {
        when (pendingImport) {
            ImportKind.SETTINGS -> importSettings(json)
            ImportKind.GROUPS -> confirmGroupImport(json)
        }
    }

    private fun importSettings(json: String) {
        val next = SymbolDrawerSettingsCodec.decodeStrict(json)
        repository.save(next)
        current = next.copy(themeMode = ThemeMode.SYSTEM)
        render()
        onSettingsChanged?.invoke(current)
        toast(context.getString(R.string.symbol_drawer_211))
    }

    private fun confirmGroupImport(json: String) {
        // Decode and validate before asking for confirmation. Invalid files never
        // reach the repository and cannot partially overwrite the current data.
        val imported = SymbolGroupBundleCodec.decode(json)
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.symbol_drawer_180))
            .setMessage(context.getString(R.string.symbol_drawer_099, imported.size))
            .setNegativeButton(context.getString(R.string.symbol_drawer_057), null)
            .setPositiveButton(context.getString(R.string.symbol_drawer_077)) { _, _ ->
                runCatching {
                    groupRepository.saveGroups(imported)
                    onGroupsChanged?.invoke(imported)
                    toast(context.getString(R.string.symbol_drawer_190))
                }.onFailure(::reportFailure)
            }
            .setNeutralButton("Append") { _, _ ->
                runCatching {
                    val existing = groupRepository.loadGroups()
                    val combined = existing + imported
                    groupRepository.saveGroups(combined)
                    onGroupsChanged?.invoke(combined)
                    toast(context.getString(R.string.symbol_drawer_190))
                }.onFailure(::reportFailure)
            }
            .showAdaptiveDialog()
    }

    private fun MaterialAlertDialogBuilder.showAdaptiveDialog(): AlertDialog {
        val dialog = create()
        dialog.setOnShowListener {
            val density = resources.displayMetrics.density
            val root = this@SymbolDrawerSettingsScreen.rootView
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

    private fun update(transform: SymbolDrawerSettings.() -> SymbolDrawerSettings) {
        runCatching {
            val next = current.transform().copy(themeMode = ThemeMode.SYSTEM)
            // SharedPreferences commit() is synchronous; return only after the
            // local persistent snapshot has been written successfully.
            repository.save(next)
            current = next
            onSettingsChanged?.invoke(next)
        }.onFailure(::reportFailure)
    }

    private fun label(value: String) = TextView(context).apply {
        text = value
        textSize = textSp(14f)
        setTextColor(onSurfaceColor())
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun reportFailure(error: Throwable) {
        onError?.invoke(error)
        toast(error.message ?: context.getString(R.string.symbol_drawer_137))
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private fun surfaceColor() = drawerTheme?.surface ?: HostThemeColors.surface(context)
    private fun onSurfaceColor() = drawerTheme?.onSurface ?: HostThemeColors.onSurface(context)
    private fun primaryColor() = drawerTheme?.primary ?: HostThemeColors.primary(context)
    private fun outlineColor() = drawerTheme?.outline ?: HostThemeColors.outline(context)

    private fun surfaceBackground(fill: Int, strokeAlpha: Float, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), (HostThemeColors.outline(context) and 0x00ffffff) or
            ((strokeAlpha.coerceIn(0f, 1f) * 255).toInt() shl 24))
    }

    private fun matchWrap() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun spaceDp(value: Int) = (value * resources.displayMetrics.density * adaptiveSpacingScale).toInt()
    private fun textSp(value: Float) = value * adaptiveTypographyScale

    private enum class ImportKind { SETTINGS, GROUPS }
}
