package android.zero.studio.widget.editor.symbolinput

import com.google.gson.annotations.SerializedName


// 符号分组数据模型
/**
 * 符号分组模型。
 *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
data class SymbolGroup(
    @SerializedName("n") var name: String = "",
    @SerializedName("d") var items: MutableList<SymbolItem> = mutableListOf()
)

// 具体符号属性数据模型
/**
 * 符号项模型。
 *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
data class SymbolItem(
    @SerializedName("a") var shortAction: Int = 0,
    @SerializedName("b") var display: String = "",
    @SerializedName("c") var shortText: String? = null,
    @SerializedName("d") var longAction: Int? = null,
    @SerializedName("e") var longText: String? = null,
    /** 短按动作的附加文本（如自定义正则替换动作的替换串），仅部分动作使用 */
    @SerializedName("f") var secondText: String? = null,
    /** 长按动作的附加文本 */
    @SerializedName("g") var longSecondText: String? = null,
    /** 动作选项标志（如查找替换的 r=正则、c=区分大小写），仅部分动作使用 */
    @SerializedName("h") var options: String? = null,
    /** 长按动作的选项标志 */
    @SerializedName("i") var longOptions: String? = null
)

/**
 * 符号输入 UI 配置模型。
 *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
data class SymbolUiSettings(
    val collapsedRows: Int = 2,
    val symbolsPerRow: Int = 10,
    val indicatorStyle: Int = 0,
    val rememberExpanded: Boolean = false,
    val uniformGroupHeight: Boolean = true,
    val symbolTextSizeSp: Int = 8,
    val showDragHandle: Boolean = true,
    val enableAdvancedActions: Boolean = true,
    val rememberLastPage: Boolean = true,
    /** 单元格标签超过该字符数后截断省略；0 表示不截断 */
    val maxLabelChars: Int = 0
)
