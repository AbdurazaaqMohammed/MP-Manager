/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.adaptive

import android.view.View

/**
 * Lifecycle-safe shared window observation for V2 widgets.
 *
 * A control can subscribe to the same controller instead of installing its own
 * layout/insets listeners. The controller owns one observer and fans out
 * immutable snapshots to registered listeners.
 *
 * Subscriptions are independent: registering the same callback more than once
 * creates distinct handles, and closing one handle never removes another.
 * Listener collection changes are synchronized; callbacks are always invoked
 * outside the lock so listeners may safely add/remove subscriptions reentrantly.
 */
class AdaptiveWindowController(
    hostView: View,
    insetsMode: WindowInsetsMode = WindowInsetsMode.SUBTRACT_SYSTEM_INSETS
) : AutoCloseable {
    private val lock = Any()
    private val listeners = LinkedHashMap<Long, (WindowAdaptiveInfo) -> Unit>()
    private var nextListenerId = 0L

    @Volatile
    private var closed = false

    @Volatile
    private var latestSnapshot: WindowAdaptiveInfo? = null

    @Volatile
    private var listenerFailure: Exception? = null

    /**
     * Optional diagnostics hook. A subscriber failure is recorded and reported
     * here without escaping the View layout/insets callback and crashing the host.
     */
    @Volatile
    var onListenerError: ((Exception) -> Unit)? = null

    val lastListenerFailure: Exception?
        get() = listenerFailure

    private val observer = AdaptiveWindowObserver(
        view = hostView,
        onChanged = ::dispatch,
        insetsMode = insetsMode
    )

    val current: WindowAdaptiveInfo?
        get() = latestSnapshot

    fun addListener(
        listener: (WindowAdaptiveInfo) -> Unit,
        emitCurrent: Boolean = true
    ): AutoCloseable {
        val id: Long
        val snapshot: WindowAdaptiveInfo?
        synchronized(lock) {
            check(!closed) { "AdaptiveWindowController is closed" }
            id = ++nextListenerId
            listeners[id] = listener
            snapshot = if (emitCurrent) latestSnapshot else null
        }

        var subscriptionClosed = false
        val subscription = AutoCloseable {
            synchronized(lock) {
                if (!subscriptionClosed) {
                    subscriptionClosed = true
                    listeners.remove(id)
                }
            }
        }
        try {
            snapshot?.let(listener)
        } catch (failure: Throwable) {
            subscription.close()
            throw failure
        }
        return subscription
    }

    fun updateFoldingFeature(value: FoldingFeatureInfo) {
        check(!closed) { "AdaptiveWindowController is closed" }
        val previous = latestSnapshot
        observer.updateFoldingFeature(value)
        // The observer intentionally waits for attachment before measuring.
        // Still expose host-supplied posture immediately in the last known
        // window snapshot, so consumers do not keep stale fold information
        // while the host view is temporarily detached.
        if (previous != null && latestSnapshot === previous &&
            previous.foldingFeature != value
        ) {
            dispatch(previous.copy(foldingFeature = value))
        }
    }

    fun refresh() {
        check(!closed) { "AdaptiveWindowController is closed" }
        observer.refresh()
    }

    private fun dispatch(snapshot: WindowAdaptiveInfo) {
        val callbacks = synchronized(lock) {
            if (closed) return
            latestSnapshot = snapshot
            listeners.values.toList()
        }
        callbacks.forEach { callback ->
            try {
                callback(snapshot)
            } catch (failure: Exception) {
                listenerFailure = failure
                // Listener code is external to the observer. Never let a faulty
                // subscriber escape through View.onLayoutChange on the main
                // thread; retain the failure for diagnostics and notify hosts.
                runCatching { onListenerError?.invoke(failure) }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            listeners.clear()
            latestSnapshot = null
        }
        // Detach the observer after making the controller inert. Any callback
        // racing with close will see closed=true and will be ignored.
        observer.close()
    }
}
