package io.github.abdurazaaqmohammed.ui.editor.symbols

import android.content.Context
import android.zero.studio.widget.editor.symbolinput.BottomBarMigration
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.LegacySymbolDataMigration
import com.zerostudio.idea.widget.editor.symbolinput.v2.data.SharedPreferencesSymbolGroupRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.GroupIndicatorStyle
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SharedPreferencesSymbolDrawerSettingsRepository
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings

/** Run before inflating the V2 view so defaults cannot preempt older app data. */
object SymbolDrawerMigration {
    @JvmStatic
    fun migrate(context: Context) {
        BottomBarMigration.migrateIfNeeded(context)
        LegacySymbolDataMigration.importIfV2Empty(context, SharedPreferencesSymbolGroupRepository(context))
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
}
