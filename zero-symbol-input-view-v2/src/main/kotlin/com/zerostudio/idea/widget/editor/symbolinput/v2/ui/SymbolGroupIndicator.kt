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

package com.zerostudio.idea.widget.editor.symbolinput.v2.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.util.TypedValue
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.zerostudio.idea.widget.editor.symbolinput.v2.R
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.SymbolGroup
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.GroupIndicatorStyle
import com.zerostudio.idea.widget.editor.symbolinput.v2.settings.SymbolDrawerSettings
import com.zerostudio.idea.widget.editor.symbolinput.v2.extension.SymbolDrawerTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Group navigation shell. Group/page ownership remains in SymbolDrawerView and
 * ViewPager2; this class is a replaceable presentation surface only.
 */
internal class SymbolGroupIndicator(
    context: Context,
    private val onGroupSelected: (Int) -> Unit
) : FrameLayout(context) {
    var onSettingsClick: (() -> Unit)? = null
    private val renderer = IndicatorRenderer(context) { index -> onGroupSelected(index) }
    private val settingsButton = ImageButton(context).apply {
        val target = dp(48)
        minimumWidth = target
        minimumHeight = target
        contentDescription = context.getString(R.string.symbol_drawer_accessibility_settings)
        isClickable = true
        isFocusable = true
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val selectable = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, selectable, true) && selectable.resourceId != 0) {
            setBackgroundResource(selectable.resourceId)
        }
        setImageDrawable(MaterialSettingsDrawable(HostThemeColors.onSurface(context)))
        setOnClickListener { onSettingsClick?.invoke() }
    }
    init {
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(HostThemeColors.surface(context))
        addView(renderer, LayoutParams(0, LayoutParams.MATCH_PARENT).apply {
            width = LayoutParams.MATCH_PARENT
            marginEnd = dp(48)
        })
        addView(settingsButton, LayoutParams(dp(48), LayoutParams.MATCH_PARENT, android.view.Gravity.END))
    }
    fun submitGroups(groups: List<SymbolGroup>) = renderer.submitGroups(groups)
    /** Controls the built-in settings affordance independently of indicator styling. */
    fun setSettingsButtonVisible(visible: Boolean) {
        settingsButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun applySettings(settings: SymbolDrawerSettings) {
        renderer.applySettings(settings)
        settingsButton.setImageDrawable(MaterialSettingsDrawable(renderer.themeOnSurface()))
    }

    fun applyTheme(theme: SymbolDrawerTheme?) {
        renderer.applyTheme(theme)
        setBackgroundColor(theme?.drawerBackground ?: HostThemeColors.surface(context))
        settingsButton.setImageDrawable(MaterialSettingsDrawable(theme?.onSurface ?: HostThemeColors.onSurface(context)))
    }
    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(color)
        renderer.invalidate()
    }
    fun setSelectedIndex(index: Int) = renderer.setSelectedIndex(index)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer.invalidate()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}

/**
 * 40 independent drawing modes over one stable page-selection contract.
 * Non-text modes intentionally omit group titles; each node/dot/segment maps
 * one-to-one to a symbol group page.
 */
private class IndicatorRenderer(
    context: Context,
    private val onSelected: (Int) -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var groups: List<SymbolGroup> = emptyList()
    private var selected = 0
    private var settings = SymbolDrawerSettings()
    private var customTheme: SymbolDrawerTheme? = null
    private var animation = 1f
    private var animator: ValueAnimator? = null
    private val primary get() = customTheme?.primary ?: HostThemeColors.primary(context)
    private val onSurface get() = customTheme?.onSurface ?: HostThemeColors.onSurface(context)
    private val surface get() = customTheme?.surface ?: HostThemeColors.surface(context)

    fun themeOnSurface(): Int = onSurface

    fun applyTheme(theme: SymbolDrawerTheme?) {
        customTheme = theme
        invalidate()
    }
    private fun dp(v: Float) = v * density

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.symbol_drawer_accessibility_group_indicator)
    }
    fun submitGroups(value: List<SymbolGroup>) {
        groups = value.toList()
        selected = selected.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
        invalidate()
    }
    fun applySettings(value: SymbolDrawerSettings) {
        settings = value
        invalidate()
    }
    fun setSelectedIndex(index: Int) {
        if (groups.isEmpty()) return
        val next = index.coerceIn(groups.indices)
        if (next == selected) { invalidate(); return }
        selected = next
        animator?.cancel()
        if (!settings.reduceMotion && settings.animationDurationMs > 0) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = settings.animationDurationMs.toLong()
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animation = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            animation = 1f
            invalidate()
        }
        contentDescription = context.getString(R.string.symbol_drawer_accessibility_group_position, selected + 1, groups.size)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = groups.size
        if (count == 0) return
        val style = settings.indicatorStyle
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f
        val horizontalInset = dp(10f)
        val usable = (w - horizontalInset * 2).coerceAtLeast(1f)
        val slot = usable / count
        val x = horizontalInset + slot * (selected + .5f)
        val radius = min(dp(12f), h * .28f)
        paint.style = Paint.Style.FILL
        paint.color = onSurface
        paint.alpha = 90
        when (style) {
            GroupIndicatorStyle.TEXT_TABS, GroupIndicatorStyle.TEXT -> drawTextTabs(canvas, slot, horizontalInset, h)
            GroupIndicatorStyle.PILL -> drawPill(canvas, x, centerY, slot, h)
            GroupIndicatorStyle.DOTS, GroupIndicatorStyle.PULSE_DOTS,
            GroupIndicatorStyle.ORBIT_DOTS -> drawDots(canvas, slot, horizontalInset, centerY, radius, style)
            GroupIndicatorStyle.UNDERLINE -> drawUnderline(canvas, slot, horizontalInset, h)
            GroupIndicatorStyle.GOOEY_METABALL, GroupIndicatorStyle.LIQUID_MERGE,
            GroupIndicatorStyle.SPRING_BLOB, GroupIndicatorStyle.FLUID_TRAIL,
            GroupIndicatorStyle.ELASTIC_WORM -> drawLiquid(canvas, slot, horizontalInset, centerY, radius, style)
            GroupIndicatorStyle.VERNIER_TICKS, GroupIndicatorStyle.MECHANICAL_RULER,
            GroupIndicatorStyle.WHEEL_ROLLER, GroupIndicatorStyle.CAMERA_DIAL,
            GroupIndicatorStyle.MINIMAL_DASHES, GroupIndicatorStyle.SQUARE_TICKS ->
                drawTicks(canvas, slot, horizontalInset, centerY, style)
            GroupIndicatorStyle.RADIAL_RING, GroupIndicatorStyle.RADAR_SWEEP,
            GroupIndicatorStyle.HUD_ARC, GroupIndicatorStyle.ARC_NODES ->
                drawRadial(canvas, slot, horizontalInset, centerY, radius, style)
            GroupIndicatorStyle.ELASTIC_WAVE, GroupIndicatorStyle.SINE_WAVE,
            GroupIndicatorStyle.BOUNCE_TRACK, GroupIndicatorStyle.SPRING_SLIDER ->
                drawWave(canvas, slot, horizontalInset, centerY, style)
            GroupIndicatorStyle.MORPHING_BAR, GroupIndicatorStyle.SEGMENTED_TRACK,
            GroupIndicatorStyle.PROGRESS_LINE, GroupIndicatorStyle.DOUBLE_TRACK,
            GroupIndicatorStyle.VERTICAL_RAIL, GroupIndicatorStyle.STEPPER ->
                drawTrack(canvas, slot, horizontalInset, centerY, h, style)
            GroupIndicatorStyle.DIAMOND_MARKERS, GroupIndicatorStyle.TRIANGLE_POINTER,
            GroupIndicatorStyle.HEXAGON_NODES, GroupIndicatorStyle.GLOWING_NODES ->
                drawShapes(canvas, slot, horizontalInset, centerY, radius, style)
            GroupIndicatorStyle.STACKED_CARDS, GroupIndicatorStyle.MINI_PREVIEW,
            GroupIndicatorStyle.THUMBNAIL_STRIP, GroupIndicatorStyle.NUMBER_BADGES,
            GroupIndicatorStyle.DIGITAL_COUNTER ->
                drawCards(canvas, slot, horizontalInset, centerY, h, style)
        }
    }
    private fun drawTextTabs(c: Canvas, slot: Float, inset: Float, h: Float) {
        groups.forEachIndexed { i, group ->
            val cx = inset + slot * (i + .5f)
            paint.color = if (i == selected) primary else onSurface
            paint.alpha = if (i == selected) 255 else 175
            paint.textSize = dp(14f)
            paint.typeface = if (i == selected) android.graphics.Typeface.create("sans-serif-medium", 0) else android.graphics.Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            val title = if (settings.showGroupTitles) group.title else (i + 1).toString()
            val label = android.text.TextUtils.ellipsize(title, android.text.TextPaint().apply { textSize = paint.textSize }, slot - dp(8f), android.text.TextUtils.TruncateAt.END).toString()
            c.drawText(label, cx, h / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        }
        paint.color = primary
        paint.alpha = 255
        c.drawRoundRect(inset + slot * selected + dp(5f), h - dp(3f), inset + slot * (selected + 1) - dp(5f), h - dp(1f), dp(2f), dp(2f), paint)
    }
    private fun drawPill(c: Canvas, x: Float, cy: Float, slot: Float, h: Float) {
        paint.color = primary; paint.alpha = 45
        c.drawRoundRect(x - slot * .43f, cy - h * .34f, x + slot * .43f, cy + h * .34f, h, h, paint)
        paint.color = primary; paint.alpha = 255
        c.drawCircle(x, cy, dp(4f) * (.8f + .2f * animation), paint)
    }
    private fun drawDots(c: Canvas, slot: Float, inset: Float, cy: Float, r: Float, style: GroupIndicatorStyle) {
        groups.indices.forEach { i ->
            val cx = inset + slot * (i + .5f)
            val active = i == selected
            paint.color = if (active) primary else onSurface
            paint.alpha = if (active) 255 else 105
            val rr = if (active) r * (if (style == GroupIndicatorStyle.PULSE_DOTS) 1.15f else 1f) else r * .55f
            if (active && style != GroupIndicatorStyle.DOTS) c.drawRoundRect(cx - rr * 1.8f, cy - rr * .55f, cx + rr * 1.8f, cy + rr * .55f, rr, rr, paint)
            else c.drawCircle(cx, cy, rr, paint)
            if (style == GroupIndicatorStyle.ORBIT_DOTS && active) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1.2f)
                c.drawCircle(cx, cy, rr + dp(4f), paint); paint.style = Paint.Style.FILL
            }
        }
    }
    private fun drawUnderline(c: Canvas, slot: Float, inset: Float, h: Float) {
        groups.indices.forEach { i ->
            paint.color = onSurface; paint.alpha = 55
            val left = inset + slot * i + dp(4f)
            c.drawRoundRect(left, h / 2f - dp(1f), left + slot - dp(8f), h / 2f + dp(1f), dp(2f), dp(2f), paint)
        }
        paint.color = primary; paint.alpha = 255
        val left = inset + slot * selected + dp(4f)
        c.drawRoundRect(left, h / 2f - dp(2f), left + slot - dp(8f), h / 2f + dp(2f), dp(3f), dp(3f), paint)
    }
    private fun drawLiquid(c: Canvas, slot: Float, inset: Float, cy: Float, r: Float, style: GroupIndicatorStyle) {
        groups.indices.forEach { i ->
            val cx = inset + slot * (i + .5f)
            paint.color = onSurface; paint.alpha = 65
            c.drawCircle(cx, cy, r * .42f, paint)
        }
        val cx = inset + slot * (selected + .5f)
        val stretch = if (style == GroupIndicatorStyle.ELASTIC_WORM) 1.8f else 1.25f
        paint.color = primary; paint.alpha = 255
        c.drawRoundRect(cx - r * stretch, cy - r * .7f, cx + r * stretch, cy + r * .7f, r, r, paint)
        if (style == GroupIndicatorStyle.GOOEY_METABALL || style == GroupIndicatorStyle.LIQUID_MERGE) {
            if (selected > 0) c.drawCircle(cx - slot * .32f, cy, r * .48f, paint)
            if (selected < groups.lastIndex) c.drawCircle(cx + slot * .32f, cy, r * .48f, paint)
        }
    }
    private fun drawTicks(c: Canvas, slot: Float, inset: Float, cy: Float, style: GroupIndicatorStyle) {
        groups.indices.forEach { i ->
            val x = inset + slot * (i + .5f)
            val active = i == selected
            paint.color = if (active) primary else onSurface; paint.alpha = if (active) 255 else 120
            paint.strokeWidth = if (active) dp(2.5f) else dp(1f)
            val extent = if (active) dp(15f) else dp(if (style == GroupIndicatorStyle.CAMERA_DIAL) 7f else 10f)
            if (style == GroupIndicatorStyle.SQUARE_TICKS) c.drawRect(x - dp(1f), cy - extent, x + dp(1f), cy + extent, paint)
            else c.drawLine(x, cy - extent, x, cy + extent, paint)
            if (active && style == GroupIndicatorStyle.WHEEL_ROLLER) {
                paint.style = Paint.Style.STROKE; c.drawCircle(x, cy, dp(7f), paint); paint.style = Paint.Style.FILL
            }
        }
    }
    private fun drawRadial(c: Canvas, slot: Float, inset: Float, cy: Float, r: Float, style: GroupIndicatorStyle) {
        val cx = width / 2f
        val radius = min(width * .34f, height * .42f)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f); paint.color = onSurface; paint.alpha = 75
        c.drawCircle(cx, cy, radius, paint)
        val angle = -PI / 2.0 + (2.0 * PI * selected / groups.size)
        paint.color = primary; paint.alpha = 255
        if (style == GroupIndicatorStyle.RADIAL_RING || style == GroupIndicatorStyle.HUD_ARC) {
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            c.drawArc(rect, -90f, 360f / groups.size, false, paint)
        } else {
            c.drawLine(cx, cy, cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius, paint)
        }
        paint.style = Paint.Style.FILL
        groups.indices.forEach { i ->
            val a = -PI / 2.0 + (2.0 * PI * i / groups.size)
            val px = cx + cos(a).toFloat() * radius
            val py = cy + sin(a).toFloat() * radius
            paint.color = if (i == selected) primary else onSurface; paint.alpha = 220
            c.drawCircle(px, py, if (i == selected) dp(3.5f) else dp(2f), paint)
        }
    }
    private fun drawWave(c: Canvas, slot: Float, inset: Float, cy: Float, style: GroupIndicatorStyle) {
        val path = Path()
        groups.indices.forEach { i ->
            val x = inset + slot * (i + .5f)
            val y = cy + if (style == GroupIndicatorStyle.SINE_WAVE) sin(i * .9).toFloat() * dp(5f) else 0f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            paint.color = if (i == selected) primary else onSurface; paint.alpha = 220
            paint.style = Paint.Style.FILL
            c.drawCircle(x, y, if (i == selected) dp(5f) else dp(2.5f), paint)
        }
        paint.color = primary; paint.alpha = 155; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
        c.drawPath(path, paint); paint.style = Paint.Style.FILL
    }
    private fun drawTrack(c: Canvas, slot: Float, inset: Float, cy: Float, h: Float, style: GroupIndicatorStyle) {
        groups.indices.forEach { i ->
            val left = inset + slot * i + dp(2f)
            val right = inset + slot * (i + 1) - dp(2f)
            paint.color = if (i == selected) primary else onSurface
            paint.alpha = if (i == selected) 255 else 65
            if (style == GroupIndicatorStyle.VERTICAL_RAIL) {
                c.drawRoundRect(left + slot * .35f, cy - h * .3f, right - slot * .35f, cy + h * .3f, dp(3f), dp(3f), paint)
            } else {
                c.drawRoundRect(left, cy - dp(if (i == selected) 4f else 2f), right, cy + dp(if (i == selected) 4f else 2f), dp(4f), dp(4f), paint)
            }
        }
    }
    private fun drawShapes(c: Canvas, slot: Float, inset: Float, cy: Float, r: Float, style: GroupIndicatorStyle) {
        groups.indices.forEach { i ->
            val x = inset + slot * (i + .5f)
            paint.color = if (i == selected) primary else onSurface; paint.alpha = if (i == selected) 255 else 100
            val rr = if (i == selected) r * .8f else r * .4f
            when (style) {
                GroupIndicatorStyle.DIAMOND_MARKERS -> {
                    val p = Path().apply { moveTo(x, cy - rr); lineTo(x + rr, cy); lineTo(x, cy + rr); lineTo(x - rr, cy); close() }
                    c.drawPath(p, paint)
                }
                GroupIndicatorStyle.TRIANGLE_POINTER -> {
                    val p = Path().apply { moveTo(x, cy - rr); lineTo(x + rr, cy + rr); lineTo(x - rr, cy + rr); close() }
                    c.drawPath(p, paint)
                }
                GroupIndicatorStyle.HEXAGON_NODES -> c.drawRoundRect(x - rr, cy - rr, x + rr, cy + rr, rr * .3f, rr * .3f, paint)
                else -> c.drawCircle(x, cy, rr, paint)
            }
        }
    }
    private fun drawCards(c: Canvas, slot: Float, inset: Float, cy: Float, h: Float, style: GroupIndicatorStyle) {
        groups.indices.forEach { i ->
            val x = inset + slot * (i + .5f)
            val active = i == selected
            paint.color = if (active) primary else onSurface
            paint.alpha = if (active) 220 else 65
            val cardH = if (style == GroupIndicatorStyle.NUMBER_BADGES || style == GroupIndicatorStyle.DIGITAL_COUNTER) dp(22f) else h * .52f
            c.drawRoundRect(x - slot * .38f, cy - cardH / 2, x + slot * .38f, cy + cardH / 2, dp(5f), dp(5f), paint)
            if (style == GroupIndicatorStyle.NUMBER_BADGES || style == GroupIndicatorStyle.DIGITAL_COUNTER) {
                paint.color = if (active) (customTheme?.onPrimary ?: HostThemeColors.onPrimary(context)) else surface
                paint.textAlign = Paint.Align.CENTER; paint.textSize = dp(11f)
                c.drawText((i + 1).toString(), x, cy - (paint.ascent() + paint.descent()) / 2, paint)
            }
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (groups.isNotEmpty()) {
                    val inset = dp(10f)
                    val slot = (width - inset * 2).coerceAtLeast(1f) / groups.size
                    val index = ((event.x - inset) / slot).toInt().coerceIn(groups.indices)
                    onSelected(index)
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
