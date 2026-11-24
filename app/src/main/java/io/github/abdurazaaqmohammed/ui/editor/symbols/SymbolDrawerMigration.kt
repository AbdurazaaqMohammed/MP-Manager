package io.github.abdurazaaqmohammed.ui.editor.symbols

import android.content.Context
import android.zero.studio.widget.editor.symbolinput.BottomBarMigration
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolDataMigration
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SharedPreferencesSymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SymbolDrawerDefaults
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.GroupIndicatorStyle
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SharedPreferencesSymbolDrawerSettingsRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings

/** Run before inflating the V2 view so defaults cannot preempt older app data. */
object SymbolDrawerMigration {
    @JvmStatic
    fun migrate(context: Context) {
        BottomBarMigration.migrateIfNeeded(context)
        val groupRepo = SharedPreferencesSymbolGroupRepository(context)
        LegacySymbolDataMigration.importIfV2Empty(context, groupRepo)

        val currentGroups = groupRepo.loadGroups()
        if (currentGroups.isEmpty()) {
            groupRepo.saveGroups(SymbolDrawerDefaults.createDefaultGroups(context))
        } else {
            val updatedGroups = ensureDefaultGroups(
                currentGroups = currentGroups,
                createCustomGroup = { SymbolDrawerDefaults.createCustomGroup(context) },
                createSymbolsGroup = { SymbolDrawerDefaults.createSymbolsGroup(context) }
            )
            if (updatedGroups !== currentGroups) {
                groupRepo.saveGroups(updatedGroups)
            }
        }
        val repository = SharedPreferencesSymbolDrawerSettingsRepository(context)
        if (repository.hasStoredSettings()) return
        val old = context.getSharedPreferences("advanced_symbol_prefs", Context.MODE_PRIVATE)
        if (old.all.keys.none { it.startsWith("symbol_") && it != "symbol_json_data" }) return
        val indicator = when (old.getInt("symbol_indicator_style", 0)) {
            1 -> GroupIndicatorStyle.DOTS
            2 -> GroupIndicatorStyle.TEXT_TABS
            3 -> GroupIndicatorStyle.UNDERLINE
            4 -> GroupIndicatorStyle.SQUARE_TICKS
            else -> GroupIndicatorStyle.TEXT_TABS
        }
        repository.save(SymbolDrawerSettings(
            columns = old.getInt("symbol_per_row", 10).coerceIn(1, 20),
            collapsedRows = old.getInt("symbol_collapsed_rows", 2).coerceIn(1, 10),
            symbolLabelTextSizeSp = old.getInt("symbol_text_size_sp", 18).coerceIn(8, 32),
            rememberExpandedState = old.getBoolean("symbol_remember_expanded", false),
            indicatorStyle = indicator,
            indicatorHeightDp = if (old.getInt("symbol_indicator_style", 0) == 2) 0 else 26,
            showFullSymbolLabels = old.getInt("symbol_max_label_chars", 0) == 0
        ))
        if (old.getBoolean("symbol_remember_expanded", false)) {
            context.getSharedPreferences("symbol_drawer_v2_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("remembered_expanded_state", old.getBoolean("symbol_last_expanded", false)).apply()
        }
    }

    internal fun ensureDefaultGroups(
        currentGroups: List<SymbolGroup>,
        createCustomGroup: () -> SymbolGroup = { SymbolDrawerDefaults.createCustomGroup() },
        createSymbolsGroup: () -> SymbolGroup = { SymbolDrawerDefaults.createSymbolsGroup() }
    ): List<SymbolGroup> {
        if (currentGroups.isEmpty()) return currentGroups

        val hasCustomByIdOrDefault = currentGroups.any {
            it.id.equals("custom", ignoreCase = true) || it.title.equals("Custom", ignoreCase = true)
        }
        val hasSymbolsByIdOrDefault = currentGroups.any {
            it.id.equals("symbols", ignoreCase = true) || it.title.equals("Symbols", ignoreCase = true)
        }

        val missing = mutableListOf<SymbolGroup>()
        if (!hasCustomByIdOrDefault) {
            val customGroup = createCustomGroup()
            if (currentGroups.none { it.title.equals(customGroup.title, ignoreCase = true) }) {
                missing.add(customGroup)
            }
        }
        if (!hasSymbolsByIdOrDefault) {
            val symbolsGroup = createSymbolsGroup()
            if (currentGroups.none { it.title.equals(symbolsGroup.title, ignoreCase = true) }) {
                missing.add(symbolsGroup)
            }
        }

        if (missing.isEmpty()) return currentGroups

        val usedSymbolIds = currentGroups.asSequence()
            .flatMap { it.symbols.asSequence() }
            .mapTo(mutableSetOf()) { it.id }
        val reservedIds = (usedSymbolIds + missing.asSequence().flatMap { it.symbols.asSequence() }.map { it.id }).toMutableSet()
        var candidate = 1L

        val sanitizedMissing = missing.map { group ->
            val sanitizedSymbols = group.symbols.map { symbol ->
                if (symbol.id != 0L && usedSymbolIds.add(symbol.id)) {
                    symbol
                } else {
                    while (candidate == 0L || candidate in reservedIds || candidate in usedSymbolIds) {
                        candidate++
                    }
                    val assigned = candidate++
                    usedSymbolIds += assigned
                    symbol.copy(id = assigned)
                }
            }
            group.copy(symbols = sanitizedSymbols)
        }
        return sanitizedMissing + currentGroups
    }
}
