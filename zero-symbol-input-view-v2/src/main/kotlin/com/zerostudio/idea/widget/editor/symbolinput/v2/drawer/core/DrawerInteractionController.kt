/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.core

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Owns continuous drawer interaction independently of any feature layout.
 *
 * A feature supplies its existing height geometry and a callback that renders
 * the resulting height. The controller owns gesture-axis arbitration,
 * velocity tracking, drag state, settling and animator lifecycle. It does not
 * create or measure views, so adopting it never imposes a different layout.
 */
class DrawerInteractionController(
    context: Context,
    private val density: Float = context.resources.displayMetrics.density,
    private val onHeightChanged: (heightPx: Int, isDragging: Boolean, isAnimating: Boolean) -> Unit,
    private val onDisallowInterceptChanged: (Boolean) -> Unit = {}
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null
    private var animator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var gestureAxis = AXIS_UNDECIDED
    private var dragging = false
    private var attached = false
    private var minimumHeight = 0
    private var maximumHeight = 0
    private var height = 0
    private var animationDurationMs = DEFAULT_ANIMATION_DURATION_MS
    private var flingVelocityDpPerSecond = DEFAULT_FLING_VELOCITY_DP_PER_SECOND

    val currentHeightPx: Int get() = height
    val isDragging: Boolean get() = dragging
    val isAnimating: Boolean get() = animator?.isRunning == true
    val isGestureAxisLocked: Boolean get() = gestureAxis != AXIS_UNDECIDED
    val isHorizontalGestureLocked: Boolean get() = gestureAxis == AXIS_HORIZONTAL

    /** Updates feature-owned geometry without changing the current expansion fraction. */
    fun setBounds(
        minimumHeightPx: Int,
        maximumHeightPx: Int,
        currentHeightPx: Int = height,
        animationDurationMs: Long = this.animationDurationMs,
        flingVelocityDpPerSecond: Float = this.flingVelocityDpPerSecond
    ) {
        require(minimumHeightPx >= 0)
        require(maximumHeightPx >= minimumHeightPx)
        require(animationDurationMs >= 0L)
        require(flingVelocityDpPerSecond.isFinite() && flingVelocityDpPerSecond >= 0f)
        this.minimumHeight = minimumHeightPx
        this.maximumHeight = maximumHeightPx
        this.animationDurationMs = animationDurationMs
        this.flingVelocityDpPerSecond = flingVelocityDpPerSecond
        setHeight(currentHeightPx, notify = false)
    }

    /** Synchronizes a feature's initial/restored height with the interaction owner. */
    fun synchronizeHeight(heightPx: Int) {
        cancelAnimation()
        setHeight(heightPx, notify = true)
    }

    fun onAttached() {
        attached = true
    }

    fun onDetached() {
        attached = false
        cancelAnimation()
        resetGesture()
    }

    /**
     * Returns true only after a vertical gesture has been accepted by the
     * drawer. Horizontal gestures are permanently locked to the child pager.
     */
    fun onInterceptTouchEvent(
        event: MotionEvent,
        childCanScrollUp: (x: Float, y: Float) -> Boolean
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.rawY
                lastY = event.rawY
                dragging = false
                gestureAxis = AXIS_UNDECIDED
                // Do not lock ancestors before the gesture axis is known.
                // Let the child pager/scroll container participate in the
                // initial DOWN event; arbitration happens after touch slop.
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                val dyFromDown = event.rawY - downY
                if (gestureAxis == AXIS_UNDECIDED && maxOf(abs(dx), abs(dyFromDown)) > touchSlop) {
                    if (abs(dx) > abs(dyFromDown) * AXIS_LOCK_RATIO) {
                        gestureAxis = AXIS_HORIZONTAL
                        velocityTracker?.recycle()
                        velocityTracker = null
                        onDisallowInterceptChanged(true)
                    } else if (abs(dyFromDown) > abs(dx) * AXIS_LOCK_RATIO) {
                        gestureAxis = AXIS_VERTICAL
                    }
                }

                if (gestureAxis == AXIS_VERTICAL && !dragging) {
                    // Re-evaluate a vertical gesture on every move. A nested
                    // scrolling child may consume the first downward movement
                    // and reach its top later in the same finger gesture.
                    // lastY is advanced on child-owned moves, so interception
                    // never replays distance already consumed by that child.
                    val deltaY = event.rawY - lastY
                    val childAtTop = deltaY > 0f && childCanScrollUp(event.x, event.y)
                    if (shouldInterceptVerticalDrag(
                            deltaY, height, minimumHeight, maximumHeight, childAtTop
                        )
                    ) {
                        dragging = true
                        cancelAnimation()
                        onDisallowInterceptChanged(true)
                        // The MOVE that crosses touch slop is the first event
                        // transferred from the child to this drawer. Consume its
                        // displacement here and advance lastY before returning;
                        // ViewGroup may deliver this same MOVE to onTouchEvent
                        // immediately after interception, where it must be a no-op.
                        val dragDelta = lastY - event.rawY
                        lastY = event.rawY
                        if (dragDelta != 0f) {
                            setHeight(
                                (height + dragDelta.roundToInt()).coerceIn(minimumHeight, maximumHeight)
                            )
                        }
                        return true
                    }
                    lastY = event.rawY
                } else if (!dragging) {
                    // Preserve the latest child-owned position while the axis
                    // is still undecided (or has been locked horizontally).
                    lastY = event.rawY
                }
                return dragging
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return false
    }

    /** Consumes a gesture previously accepted by [onInterceptTouchEvent]. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val delta = lastY - event.rawY
                lastY = event.rawY
                setHeight((height + delta.roundToInt()).coerceIn(minimumHeight, maximumHeight))
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = -(velocityTracker?.yVelocity ?: 0f)
                    val threshold = maxOf(
                        minimumFlingVelocity.toFloat(),
                        flingVelocityDpPerSecond * density
                    )
                    val targetExpanded = if (abs(velocityY) >= threshold) {
                        velocityY > 0f
                    } else {
                        expansionFraction() >= 0.5f
                    }
                    settleTo(if (targetExpanded) maximumHeight else minimumHeight, animated = true)
                }
                resetGesture()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val target = if (expansionFraction() >= 0.5f) maximumHeight else minimumHeight
                    settleTo(target, animated = true)
                }
                resetGesture()
                return true
            }
        }
        return true
    }

    /** Programmatic feature requests use the same animator and state owner as gestures. */
    fun expand(animated: Boolean = true) = settleTo(maximumHeight, animated)
    fun collapse(animated: Boolean = true) = settleTo(minimumHeight, animated)

    fun settleToHeight(heightPx: Int, animated: Boolean = true) =
        settleTo(heightPx.coerceIn(minimumHeight, maximumHeight), animated)

    fun cancelAnimation() {
        val wasRunning = animator?.isRunning == true
        animator?.cancel()
        animator = null
        if (wasRunning) onHeightChanged(height, dragging, false)
    }

    fun resetGesture() {
        val wasDragging = dragging
        dragging = false
        gestureAxis = AXIS_UNDECIDED
        onDisallowInterceptChanged(false)
        velocityTracker?.recycle()
        velocityTracker = null
        if (wasDragging) onHeightChanged(height, false, isAnimating)
    }

    private fun settleTo(targetHeight: Int, animated: Boolean) {
        val target = targetHeight.coerceIn(minimumHeight, maximumHeight)
        cancelAnimation()
        if (!animated || !attached || height == target || animationDurationMs == 0L) {
            setHeight(target)
            return
        }
        val startFraction = expansionFraction()
        val targetFraction = if (maximumHeight <= minimumHeight) 0f else
            ((target - minimumHeight).toFloat() / (maximumHeight - minimumHeight)).coerceIn(0f, 1f)
        animator = ValueAnimator.ofFloat(startFraction, targetFraction).apply {
            duration = animationDurationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { value ->
                val fraction = value.animatedValue as Float
                val animatedHeight = (
                    minimumHeight + (maximumHeight - minimumHeight) * fraction
                ).roundToInt()
                setHeight(animatedHeight)
            }
            start()
        }
        onHeightChanged(height, dragging, true)
    }

    private fun setHeight(value: Int, notify: Boolean = true) {
        height = value.coerceIn(minimumHeight, maximumHeight)
        if (notify) onHeightChanged(height, dragging, isAnimating)
    }

    private fun expansionFraction(): Float {
        val range = maximumHeight - minimumHeight
        return if (range <= 0) 0f else
            ((height - minimumHeight).toFloat() / range).coerceIn(0f, 1f)
    }

    companion object {
        private const val AXIS_UNDECIDED = 0
        private const val AXIS_HORIZONTAL = 1
        private const val AXIS_VERTICAL = 2
        private const val AXIS_LOCK_RATIO = 1.15f
        private const val DEFAULT_ANIMATION_DURATION_MS = 220L
        private const val DEFAULT_FLING_VELOCITY_DP_PER_SECOND = 900f

        /** Shared policy for nested vertical content and continuous drawer movement. */
        @JvmStatic
        fun shouldInterceptVerticalDrag(
            fingerDeltaY: Float,
            currentHeightPx: Int,
            collapsedHeightPx: Int,
            expandedHeightPx: Int,
            childCanScrollUp: Boolean
        ): Boolean {
            if (expandedHeightPx <= collapsedHeightPx || fingerDeltaY == 0f) return false
            return if (fingerDeltaY < 0f) {
                currentHeightPx < expandedHeightPx
            } else {
                currentHeightPx > collapsedHeightPx && !childCanScrollUp
            }
        }
    }
}
