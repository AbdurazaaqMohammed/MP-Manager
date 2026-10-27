package android.zero.studio.widget.editor.symbolinput

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.content.SharedPreferences
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * MT 风格的符号输入控件：
 * Root(ViewGroup)
 *  ├─ GroupIndicatorBar(HorizontalScrollView > LinearLayout > (LinearLayout > TextView))
 *  └─ SymbolPagerHost(ViewGroup > ViewPager > SymbolPageGridView)
 */
/**
 * 高级符号输入控件（纯代码构建）。
 *
 * ## 架构
 * - 顶层：`AdvancedSymbolInputView`（`ViewGroup`）
 * - 分组指示器：`GroupIndicatorBar`（`HorizontalScrollView > LinearLayout > TextView`）
 * - 分页容器：`SymbolPagerHost`（`ViewPager`）
 * - 页内网格：`SymbolPageGridView`（自定义网格测量与布局）
 *
 * ## 设计目标
 * 1. 避免 XML 多层嵌套，改为轻量级代码布局；
 * 2. 将“折叠/展开高度”与网格行高统一到同一计算模型；
 * 3. 通过设置项控制固定行数、每行符号数以及分页切换体验。
  *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
class AdvancedSymbolInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val indicatorBar = GroupIndicatorBar(context) { index -> pagerHost.setCurrentPage(index, true) }
    private val bottomIndicator = CompactPageIndicator(context) { index -> pagerHost.setCurrentPage(index, true) }
    private val pagerHost = SymbolPagerHost(context)
    private val pageAdapter = SymbolPagerAdapter()

    private var editor: CodeEditor? = null
    var onOpenManagerListener: (() -> Unit)? = null
    var followSystemIme: Boolean = false

    /**
     * 自定义动作回调（动作编号 >= 100），参数为 (动作编号, 文本, 附加文本, 选项标志)。
     * 由宿主 App 注入，用于实现组件内置动作之外的编辑操作。
     */
    var onCustomActionListener: ((Int, String?, String?, String?) -> Unit)? = null

    private val groups = mutableListOf<SymbolGroup>()
    private var uiSettings = SymbolUiSettings()

    private val gridCellHeightPx by lazy { dp(28) }
    private val gridHorizontalGapPx by lazy { dp(2) }
    private val gridVerticalGapPx by lazy { dp(2) }
    private val gridVerticalPaddingPx by lazy { dp(4) }
    private var collapsedHeightPx = calculateGridHeight(2)
    private var expandedHeightPx = calculateGridHeight(4)

    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private var initialY = 0f
    private var initialX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var heightAnimator: ValueAnimator? = null
    private var lastSavedPageIndex = -1

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (SymbolDataManager.shouldTriggerUiRefresh(key)) refreshData()
    }

    init {
        addView(indicatorBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(32)))
        addView(pagerHost, LayoutParams(LayoutParams.MATCH_PARENT, collapsedHeightPx))
        addView(bottomIndicator, LayoutParams(LayoutParams.MATCH_PARENT, dp(14)))
        pagerHost.bindAdapter(pageAdapter)
        pagerHost.onPageChanged = { page ->
            indicatorBar.setSelectedIndex(page)
            bottomIndicator.setSelectedIndex(page)
            if (uiSettings.rememberLastPage && lastSavedPageIndex != page) {
                lastSavedPageIndex = page
                SymbolDataManager.setLastPageIndex(context, page)
            }
            recalculateHeights()
        }
        refreshData()
        applyIndicatorReveal(0f)
    }

    /**
     * 与旧调用链兼容的占位方法。
     *
     * 当前版本由控件内部管理展开/折叠，不再依赖外部 `BottomSheetBehavior`。
     */
    fun setupWithBottomSheet(rootView: View, bottomSheet: View, followView: View? = null) = Unit

    /** 绑定编辑器实例，用于执行符号插入动作。 */
    fun bindEditor(editor: CodeEditor) {
        this.editor = editor
    }

    /** 页面恢复时按设置恢复抽屉展开状态。 */
    fun onHostResume() {
        val shouldExpand = uiSettings.rememberExpanded && SymbolDataManager.getLastExpanded(context)
        animateToHeight(if (shouldExpand) expandedHeightPx else collapsedHeightPx)
    }

    /**
     * 重新加载分组与样式配置，并刷新分页与指示器。
     */
    fun refreshData() {
        uiSettings = SymbolDataManager.getUiSettings(context)

        groups.clear()
        groups.addAll(SymbolDataManager.loadData(context).filter { it.items.isNotEmpty() })
        if (groups.isEmpty()) {
            val defaults = SymbolDefaults.createFallbackGroups()
            groups.addAll(defaults)
            SymbolDataManager.saveData(context, defaults)
        }

        indicatorBar.submitGroups(groups, uiSettings.indicatorStyle)
        bottomIndicator.submitCount(groups.size, uiSettings.indicatorStyle)
        pageAdapter.notifyDataSetChanged()

        if (groups.isNotEmpty()) {
            val target = if (uiSettings.rememberLastPage) {
                SymbolDataManager.getLastPageIndex(context).coerceIn(0, groups.lastIndex)
            } else 0
            pagerHost.setCurrentPage(target, false)
            indicatorBar.setSelectedIndex(target)
            bottomIndicator.setSelectedIndex(target)
            lastSavedPageIndex = target
        }

        recalculateHeights()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.getSharedPreferences("advanced_symbol_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDetachedFromWindow() {
        context.getSharedPreferences("advanced_symbol_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDetachedFromWindow()
    }

    /**
     * 计算网格尺寸。
     *
     * 关键点：
     * - `cols` 来自设置中的每行符号数量；
     * - 行高固定为 `itemCellHeightPx`；
     * - 总高度 = 上下内边距 + 行高总和 + 行间距总和。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val indicatorHeight = indicatorBar.layoutParams.height.coerceAtLeast(0)
        measureChild(indicatorBar, widthMeasureSpec, MeasureSpec.makeMeasureSpec(indicatorHeight, MeasureSpec.EXACTLY))

        val pagerHeight = pagerHost.layoutParams.height.coerceAtLeast(collapsedHeightPx)
        pagerHost.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(pagerHeight, MeasureSpec.EXACTLY)
        )

        val bottomHeight = bottomIndicator.layoutParams.height.coerceAtLeast(0)
        measureChild(bottomIndicator, widthMeasureSpec, MeasureSpec.makeMeasureSpec(bottomHeight, MeasureSpec.EXACTLY))

        setMeasuredDimension(width, indicatorBar.measuredHeight + pagerHost.measuredHeight + bottomIndicator.measuredHeight)
    }

    /**
     * 根据行列索引将符号项定位到对应单元格。
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val indicatorHeight = indicatorBar.measuredHeight
        indicatorBar.layout(0, 0, width, indicatorHeight)
        val pagerBottom = indicatorHeight + pagerHost.measuredHeight
        pagerHost.layout(0, indicatorHeight, width, pagerBottom)
        bottomIndicator.layout(0, pagerBottom, width, pagerBottom + bottomIndicator.measuredHeight)
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialY = ev.rawY
                lastY = ev.rawY
                initialX = ev.rawX
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - initialY
                val dx = ev.rawX - initialX
                if (!isDragging && expandedHeightPx > collapsedHeightPx && kotlin.math.abs(dy) > touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                heightAnimator?.cancel()
                initialY = event.rawY
                lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return super.onTouchEvent(event)
                val deltaY = event.rawY - lastY
                val nextHeight = (pagerHost.layoutParams.height - deltaY.toInt()).coerceIn(collapsedHeightPx, expandedHeightPx)
                updatePagerHeight(nextHeight)
                lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val midpoint = (collapsedHeightPx + expandedHeightPx) / 2
                    val target = if (pagerHost.layoutParams.height >= midpoint) expandedHeightPx else collapsedHeightPx
                    if (uiSettings.rememberExpanded) {
                        SymbolDataManager.setLastExpanded(context, target == expandedHeightPx)
                    }
                    animateToHeight(target)
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateToHeight(targetHeight: Int) {
        val currentHeight = pagerHost.layoutParams.height.coerceAtLeast(collapsedHeightPx)
        if (currentHeight == targetHeight) return
        heightAnimator?.cancel()
        heightAnimator = ValueAnimator.ofInt(currentHeight, targetHeight).apply {
            duration = 200
            addUpdateListener { updatePagerHeight(it.animatedValue as Int) }
            start()
        }
    }

    private fun updatePagerHeight(height: Int) {
        val clamped = height.coerceIn(collapsedHeightPx, expandedHeightPx)
        if (pagerHost.layoutParams.height != clamped) {
            pagerHost.layoutParams = pagerHost.layoutParams.apply { this.height = clamped }
            requestLayout()
        }
        val fraction = (clamped - collapsedHeightPx).toFloat() / (expandedHeightPx - collapsedHeightPx).coerceAtLeast(1)
        applyIndicatorReveal(fraction)
    }

    private fun applyIndicatorReveal(fraction: Float) {
        val reveal = ((fraction - 0.08f) / 0.47f).coerceIn(0f, 1f)
        indicatorBar.applyReveal(reveal, dp(32), uiSettings.indicatorStyle)
        bottomIndicator.applyReveal(reveal, uiSettings.indicatorStyle)
        requestLayout()
    }

    /**
     * 依据当前配置与页面数据重新计算折叠/展开高度。
     *
     * - 折叠高度：严格按 `collapsedRows` 计算；
     * - 展开高度：按当前页的真实行数计算；
     * - 两者都使用同一网格高度公式，避免出现“设置 2 行却显示 3 行”的漂移问题。
     */
    private fun recalculateHeights() {
        val collapsedRows = uiSettings.collapsedRows.coerceAtLeast(1)
        collapsedHeightPx = calculateGridHeight(collapsedRows)

        val availablePageWidth = (pagerHost.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels) - dp(8)
        val engine = FlowGridEngine(uiSettings, resources.displayMetrics)
        val maxPageRows = groups.maxOfOrNull { group ->
            engine.rowCount(group.items, availablePageWidth, dp(2)).coerceAtLeast(1)
        } ?: collapsedRows
        expandedHeightPx = calculateGridHeight(maxPageRows.coerceAtLeast(collapsedRows))

        updatePagerHeight(pagerHost.layoutParams.height.coerceIn(collapsedHeightPx, expandedHeightPx))
    }

    private fun calculateGridHeight(rows: Int): Int {
        val safeRows = rows.coerceAtLeast(1)
        return (safeRows * gridCellHeightPx) + ((safeRows - 1) * gridVerticalGapPx) + (gridVerticalPaddingPx * 2)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private inner class SymbolPagerAdapter : PagerAdapter() {
        override fun getCount(): Int = groups.size
        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val group = groups[position]
            val pageView = SymbolPageGridView(context) { item, isLong ->
                if (isLong) {
                    val action = item.longAction
                    if (action != null) {
                        editor?.let {
                            SymbolActionExecutor.execute(
                                it, action, item.longText, item.longSecondText, item.longOptions,
                                onOpenManagerListener, onCustomActionListener
                            )
                        }
                    }
                } else {
                    editor?.let {
                        SymbolActionExecutor.execute(
                            it, item.shortAction, item.shortText, item.secondText, item.options,
                            onOpenManagerListener, onCustomActionListener
                        )
                    }
                }
            }
            pageView.updateConfig(uiSettings)
            pageView.submit(group.items)
            container.addView(pageView)
            return pageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun getItemPosition(`object`: Any): Int = POSITION_NONE
    }
}

/**
 * 从当前主题解析颜色属性，找不到时回退到 fallback。
 */
private fun resolveThemeColor(context: Context, attr: Int, fallback: Int): Int {
    val value = TypedValue()
    if (context.theme.resolveAttribute(attr, value, true) && value.type != TypedValue.TYPE_NULL) {
        return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
    }
    return fallback
}

/**
 * 分组指示器组件。
 *
 * 使用横向滚动容器承载多个分组项，负责：
 * - 渲染分组标题；
 * - 选中态高亮与自动滚动居中；
 * - 展开过程中的显隐与位移动画。
  *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
private class GroupIndicatorBar(
    context: Context,
    private val onGroupClicked: (Int) -> Unit
) : ViewGroup(context) {

    private val scroll = HorizontalScrollView(context).apply {
        overScrollMode = OVER_SCROLL_NEVER
        isHorizontalScrollBarEnabled = false
    }
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    init {
        scroll.addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun submitGroups(groups: List<SymbolGroup>, style: Int) {
        row.removeAllViews()
        groups.forEachIndexed { index, group ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setOnClickListener { onGroupClicked(index) }
            }
            if (style == 3) {
                item.setPadding(dp(6), dp(8), dp(6), dp(8))
                val line = View(context).apply {
                    alpha = 0.55f
                }
                item.addView(line, LinearLayout.LayoutParams(dp(22), dp(3)).apply { gravity = Gravity.CENTER })
                row.addView(item, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            } else {
                item.setPadding(dp(8), dp(6), dp(8), dp(6))
                item.addView(TextView(context).apply {
                    text = group.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(resolveThemeColor(context, android.R.attr.textColorPrimary, Color.LTGRAY))
                })
                row.addView(item, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            }
        }
    }

    fun setSelectedIndex(index: Int) {
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            child.alpha = if (i == index) 1f else 0.6f
            if (child is LinearLayout && child.childCount > 0 && child.getChildAt(0) is View && child.getChildAt(0) !is TextView) {
                val line = child.getChildAt(0)
                val selected = i == index
                line.alpha = if (selected) 1f else 0.45f
                val onSurface = resolveThemeColor(context, android.R.attr.textColorPrimary, Color.LTGRAY)
                line.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(2).toFloat()
                    setColor(if (selected) onSurface else ColorUtils.setAlphaComponent(onSurface, 153))
                }
            }
        }
        row.getChildAt(index)?.let { child ->
            val center = child.left + child.width / 2
            scroll.smoothScrollTo((center - width / 2).coerceAtLeast(0), 0)
        }
    }

    fun applyReveal(fraction: Float, fullHeight: Int, style: Int) {
        if (style == 1 || style == 4 || style == 2) {
            visibility = View.GONE
            layoutParams = layoutParams.apply { height = 0 }
            return
        }
        alpha = fraction
        translationY = (1f - fraction) * -6f * resources.displayMetrics.density
        layoutParams = layoutParams.apply { height = (fullHeight * fraction).roundToInt() }
        visibility = if (fraction <= 0f) View.INVISIBLE else View.VISIBLE
    }

    /**
     * 计算网格尺寸。
     *
     * 关键点：
     * - `cols` 来自设置中的每行符号数量；
     * - 行高固定为 `itemCellHeightPx`；
     * - 总高度 = 上下内边距 + 行高总和 + 行间距总和。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        scroll.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    /**
     * 根据行列索引将符号项定位到对应单元格。
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        scroll.layout(0, 0, r - l, b - t)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}


private class CompactPageIndicator(
    context: Context,
    private val onItemClicked: (Int) -> Unit
) : ViewGroup(context) {

    private var style: Int = 2

    fun submitCount(count: Int, style: Int) {
        this.style = style
        removeAllViews()
        if (style != 1 && style != 4) return
        repeat(count) { index ->
            addView(View(context).apply {
                setOnClickListener { onItemClicked(index) }
            }, LayoutParams(dp(8), dp(8)))
        }
    }

    fun setSelectedIndex(index: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val selected = i == index
            val size = if (selected) dp(8) else dp(6)
            child.layoutParams = child.layoutParams.apply { width = size; height = size }
            child.alpha = if (selected) 1f else 0.55f
            val onSurface = resolveThemeColor(context, android.R.attr.textColorPrimary, Color.LTGRAY)
            val color = if (selected) onSurface else ColorUtils.setAlphaComponent(onSurface, 153)
            child.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = if (style == 1) size / 2f else dp(1).toFloat()
                setColor(color)
            }
        }
    }

    fun applyReveal(fraction: Float, style: Int) {
        val showCompact = style == 1 || style == 4
        visibility = if (showCompact) View.VISIBLE else View.GONE
        layoutParams = layoutParams.apply { height = if (showCompact) dp(14) else 0 }
        // Compact indicators should remain visible at drawer bottom for these styles,
        // instead of being tied to top-tab reveal progress.
        alpha = if (showCompact) 1f else 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            measureChild(c, MeasureSpec.makeMeasureSpec(dp(8), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(8), MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(width, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val total = childCount * dp(8) + (childCount - 1).coerceAtLeast(0) * dp(6)
        var left = ((r - l - total) / 2).coerceAtLeast(0)
        val top = ((b - t - dp(8)) / 2).coerceAtLeast(0)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
            left += c.measuredWidth + dp(6)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}

/**
 * 分页承载组件，职责为包装 `ViewPager` 并向外暴露最小化 API。
  *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
private class SymbolPagerHost(context: Context) : ViewGroup(context) {
    private val pager = ViewPager(context).apply { overScrollMode = OVER_SCROLL_NEVER }
    var onPageChanged: ((Int) -> Unit)? = null
    val currentPage: Int get() = pager.currentItem

    init {
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                onPageChanged?.invoke(position)
            }
        })
    }

    fun bindAdapter(adapter: PagerAdapter) {
        pager.adapter = adapter
    }

    fun setCurrentPage(page: Int, smooth: Boolean) {
        pager.setCurrentItem(page, smooth)
    }

    /**
     * 计算网格尺寸。
     *
     * 关键点：
     * - `cols` 来自设置中的每行符号数量；
     * - 行高固定为 `itemCellHeightPx`；
     * - 总高度 = 上下内边距 + 行高总和 + 行间距总和。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        pager.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    /**
     * 根据行列索引将符号项定位到对应单元格。
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        pager.layout(0, 0, r - l, b - t)
    }
}

/**
 * 流式布局计算器：按钮宽度按内容自适应，超出容器宽度或达到每行上限时换行。
 * 页面视图的测量/布局与抽屉展开高度估算共用同一套计算，保证两者一致。
 *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
private class FlowGridEngine(settings: SymbolUiSettings, metrics: DisplayMetrics) {
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, settings.symbolTextSizeSp.coerceAtLeast(12).toFloat(), metrics
        )
    }
    private val maxPerRow = settings.symbolsPerRow.coerceIn(1, 20)
    private val maxLabelChars = settings.maxLabelChars
    private val minChipWidth = (MIN_WIDTH_DP * metrics.density).roundToInt()
    private val padH = (PAD_H_DP * metrics.density).roundToInt()

    /** 应用标签截断设置后的实际显示文本。 */
    fun labelOf(item: SymbolItem): String {
        val label = item.display
        return if (maxLabelChars > 0 && label.length > maxLabelChars)
            label.take(maxLabelChars) + "…"
        else label
    }

    fun chipWidth(item: SymbolItem): Int {
        val textWidth = ceil(paint.measureText(labelOf(item))).toInt()
        return (textWidth + padH * 2).coerceAtLeast(minChipWidth)
    }

    fun widths(items: List<SymbolItem>): IntArray =
        IntArray(items.size) { chipWidth(items[it]) }

    fun rowCount(items: List<SymbolItem>, availableWidth: Int, gapX: Int): Int =
        flow(widths(items), availableWidth, gapX, null)

    /**
     * 依宽度序列流式换行。
     *
     * @param outLefts 每项的 x 偏移（长度需与 w 一致，可空）。
     * @param outTops 每项的 y 偏移（rowIndex * topStride，可空）。
     * @return 总行数。
     */
    fun flow(w: IntArray, availableWidth: Int, gapX: Int, outLefts: IntArray?, outTops: IntArray? = null, topStride: Int = 0): Int {
        var x = 0
        var inRow = 0
        var rows = 1
        val avail = availableWidth.coerceAtLeast(1)
        w.indices.forEach { i ->
            val cw = w[i].coerceAtMost(avail)
            if (inRow > 0 && (x + cw > avail || inRow >= maxPerRow)) {
                x = 0
                rows++
                inRow = 0
            }
            outLefts?.set(i, x)
            outTops?.set(i, (rows - 1) * topStride)
            x += cw + gapX
            inRow++
        }
        return rows
    }

    companion object {
        const val PAD_H_DP = 10
        const val MIN_WIDTH_DP = 40
    }
}

/**
 * 分页内符号流式面板：按钮宽度随内容自适应，逐行排布。
 *
 * - 行高固定为 `itemCellHeightPx`，按钮宽度按文本测量（含内边距，含最小宽度）；
 * - 超出容器宽度或达到“每行符号数”上限时换行；
 * - 点击与长按动作分发。
 *
 * @author android_zero
 * @github msmt2018/zero-Symbol-input-view
 */
private class SymbolPageGridView(
    context: Context,
    private val onItemTriggered: (SymbolItem, Boolean) -> Unit
) : ViewGroup(context) {

    private var settings = SymbolUiSettings()
    private var engine = FlowGridEngine(settings, resources.displayMetrics)
    private var items: List<SymbolItem> = emptyList()
    private val childLeft = ArrayList<Int>()
    private val childTop = ArrayList<Int>()

    /** 更新页面渲染配置（字体、截断、每行上限等）。 */
    fun updateConfig(settings: SymbolUiSettings) {
        this.settings = settings
        engine = FlowGridEngine(settings, resources.displayMetrics)
    }

    private val itemHorizontalGapPx: Int get() = dp(2)
    private val itemVerticalGapPx: Int get() = dp(2)
    private val itemCellHeightPx: Int get() = dp(28)

    /**
     * 提交当前页符号列表并重建子视图。
     *
     * @param items 当前分组下用于展示的符号项集合。
     */
    fun submit(items: List<SymbolItem>) {
        this.items = items
        removeAllViews()
        setPadding(dp(4), dp(4), dp(4), dp(4))
        items.forEach { item ->
            val tv = TextView(context).apply {
                text = engine.labelOf(item)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.symbolTextSizeSp.coerceAtLeast(12).toFloat())
                setPadding(dp(FlowGridEngine.PAD_H_DP), 0, dp(FlowGridEngine.PAD_H_DP), 0)
                val bg = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, bg, true)
                setBackgroundResource(bg.resourceId)
                setOnClickListener { onItemTriggered(item, false) }
                setOnLongClickListener {
                    if (item.longAction != null) {
                        onItemTriggered(item, true)
                        true
                    } else {
                        false
                    }
                }
            }
            addView(tv)
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val totalContentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val gapX = itemHorizontalGapPx
        val gapY = itemVerticalGapPx
        val cellHeight = itemCellHeightPx

        if (items.size != childCount) {
            setMeasuredDimension(width, paddingTop + paddingBottom + cellHeight)
            return
        }

        val lefts = IntArray(childCount)
        val tops = IntArray(childCount)
        val w = engine.widths(items)
        val rows = engine.flow(w, totalContentWidth, gapX, lefts, tops, cellHeight + gapY)

        childLeft.clear()
        childTop.clear()
        repeat(childCount) { i ->
            val cw = w[i].coerceAtMost(totalContentWidth.coerceAtLeast(1))
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY)
            )
            childLeft.add(lefts[i])
            childTop.add(tops[i])
        }

        val totalHeight = paddingTop + paddingBottom + rows * cellHeight + (rows - 1).coerceAtLeast(0) * gapY
        setMeasuredDimension(width, totalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        repeat(childCount) { i ->
            if (i >= childLeft.size) return
            val c = getChildAt(i)
            val left = paddingLeft + childLeft[i]
            val top = paddingTop + childTop[i]
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
