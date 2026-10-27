package android.zero.studio.widget.editor.symbolinput

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 旧版底部栏配置迁移工具。
 *
 * 将 MP Manager 旧版 `pref_bottom_bar_buttons`（JSONArray，含
 * `label/action/data1/data2/longAction/longData1/longData2` 字段）
 * 一次性迁移到本组件的分组存储（`advanced_symbol_prefs`/`symbol_json_data`）。
 *
 * 旧动作名称映射到自定义动作编号（>= 100，由宿主 App 的
 * `AdvancedSymbolInputView.onCustomActionListener` 执行）：
 * - Search -> 100
 * - Regex find and replace -> 101（shortText=find 正则，secondText=替换文本）
 * - Replace line（从剪贴板替换当前行）-> 102
 * 其余动作映射到组件内置动作编号。
 *
 * @author android_zero（迁移桥接代码由 MP Manager 集成时添加）
 */
object BottomBarMigration {

    /** 继续上一次搜索（对应旧版 Search 按钮） */
    const val ACTION_SEARCH = 100

    /** 查找替换（shortText=查找串，secondText=替换串，options: r=正则，c=区分大小写） */
    const val ACTION_FIND_REPLACE = 101

    /** 用剪贴板内容替换当前行 */
    const val ACTION_REPLACE_LINE_CLIPBOARD = 102

    /** 旧版配置存储键（默认 SharedPreferences） */
    const val LEGACY_KEY = "pref_bottom_bar_buttons"

    /** 迁移出的旧按钮所在分组名 */
    const val GROUP_NAME = "Custom"

    private const val FLAG_KEY = "bottom_bar_migrated_to_symbol_input"

    /**
     * 若存在旧版配置且尚未迁移，则迁移到新的分组存储。
     * 返回 true 表示数据发生了写入，宿主应刷新控件。
     */
    @JvmStatic
    fun migrateIfNeeded(context: Context): Boolean {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (defaultPrefs.getBoolean(FLAG_KEY, false)) return false

        val raw = defaultPrefs.getString(LEGACY_KEY, null)
        // 无旧数据（含默认空值）：直接打标记，避免每次启动重复解析。
        if (raw.isNullOrEmpty() || raw == "[]") {
            defaultPrefs.edit().putBoolean(FLAG_KEY, true).apply()
            return false
        }

        val migrated = try {
            buildGroupsFromLegacy(raw)
        } catch (e: Exception) {
            null
        }

        defaultPrefs.edit().putBoolean(FLAG_KEY, true).apply()
        if (migrated.isNullOrEmpty()) return false

        // 合并写入：保留可能已存在的新华分组，把迁移结果放到第一个分组。
        val existing = try {
            SymbolDataManager.loadData(context)
        } catch (e: Exception) {
            mutableListOf()
        }
        val merged = ArrayList<SymbolGroup>()
        merged.addAll(migrated)
        existing.forEach { old ->
            if (merged.none { it.name == old.name }) merged.add(old)
        }
        SymbolDataManager.saveData(context, merged)
        return true
    }

    private fun buildGroupsFromLegacy(raw: String): MutableList<SymbolGroup>? {
        val array: JSONArray = try {
            JSONArray(raw)
        } catch (e: Exception) {
            // 兼容最早期的 "Search,Copy,Cut,Paste" 逗号串格式
            val rebuilt = JSONArray()
            raw.split(",").forEach { name ->
                rebuilt.put(JSONObject().put("action", name.trim()).put("label", name.trim()))
            }
            rebuilt
        }

        val items = ArrayList<SymbolItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val label = obj.optString("label").ifEmpty { obj.optString("action") }
            val action = obj.optString("action")
            if (action.isEmpty() || action == "None") continue

            val short = toItem(action, obj.optString("data1"), obj.optString("data2")) ?: continue
            short.display = label

            val longActionRaw = obj.optString("longAction")
            val longItem: SymbolItem? =
                if (longActionRaw.isEmpty() || longActionRaw == "None") null
                else toItem(longActionRaw, obj.optString("longData1"), obj.optString("longData2"))

            items.add(
                SymbolItem(
                    shortAction = short.shortAction,
                    display = label,
                    shortText = short.shortText,
                    secondText = short.secondText,
                    longAction = longItem?.shortAction,
                    longText = longItem?.shortText,
                    longSecondText = longItem?.secondText,
                    options = short.options,
                    longOptions = longItem?.options
                )
            )
        }
        if (items.isEmpty()) return null

        // 旧版按钮组迁移为第一个分组，其后追加常用符号分组。
        val groups = ArrayList<SymbolGroup>()
        groups.add(SymbolGroup(GROUP_NAME, items))
        groups.add(SymbolGroup("Symbols", defaultSymbolItems()))
        return groups
    }

    /** 首次迁移时附带的常用符号分组。 */
    private fun defaultSymbolItems(): MutableList<SymbolItem> = mutableListOf(
        SymbolItem(0, "(", "(", 0, "()"),
        SymbolItem(0, ")", ")"),
        SymbolItem(0, "[", "[", 0, "[]"),
        SymbolItem(0, "]", "]"),
        SymbolItem(0, "{", "{", 0, "{}"),
        SymbolItem(0, "}", "}"),
        SymbolItem(0, "\"", "\""),
        SymbolItem(0, "'", "'"),
        SymbolItem(0, "//", "//"),
        SymbolItem(0, "/*", "/*"),
        SymbolItem(0, "*/", "*/"),
        SymbolItem(0, ";", ";"),
        SymbolItem(0, ":", ":"),
        SymbolItem(0, ",", ","),
        SymbolItem(0, ".", "."),
        SymbolItem(0, "=", "="),
        SymbolItem(0, "+", "+"),
        SymbolItem(0, "-", "-"),
        SymbolItem(0, "*", "*"),
        SymbolItem(0, "/", "/"),
        SymbolItem(0, "<", "<"),
        SymbolItem(0, ">", ">"),
        SymbolItem(0, "!", "!"),
        SymbolItem(0, "?", "?"),
        SymbolItem(0, "&", "&"),
        SymbolItem(0, "|", "|"),
        SymbolItem(0, "\\", "\\"),
        SymbolItem(0, "$", "$"),
        SymbolItem(0, "@", "@"),
        SymbolItem(0, "#", "#"),
        SymbolItem(18, "←", null, 16, null),
        SymbolItem(20, "↑", null, 23, null),
        SymbolItem(19, "→", null, 17, null),
        SymbolItem(21, "↓", null, 24, null),
        SymbolItem(37, "↵"),
        SymbolItem(38, "↹"),
        SymbolItem(55, "⤺"),
        SymbolItem(56, "⤻"),
        SymbolItem(22, "⚙")
    )

    /**
     * 旧动作名 -> 符号项（display 由调用方填充）。
     * shortText=data1，secondText=data2。返回 null 表示无法映射，跳过该按钮。
     */
    private fun toItem(action: String, data1: String?, data2: String?): SymbolItem? {
        return when (action) {
            "Search" -> SymbolItem(ACTION_SEARCH)
            "Insert text" -> SymbolItem(0, "", data1 ?: "")
            // 旧版为正则+区分大小写，迁移后保持默认勾选
            "Regex find and replace" -> SymbolItem(ACTION_FIND_REPLACE, "", data1 ?: "", null, null, data2, null, "rc")
            "Copy selection" -> SymbolItem(28)
            "Cut selection" -> SymbolItem(29)
            "Paste selection" -> SymbolItem(30)
            "Copy line" -> SymbolItem(33)
            "Cut line" -> SymbolItem(34)
            "Delete line" -> SymbolItem(3)
            "Empty line" -> SymbolItem(4)
            "Replace line" -> SymbolItem(ACTION_REPLACE_LINE_CLIPBOARD)
            else -> null
        }
    }
}
