/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.drawer.action

/**
 * Stable action identity shared by built-in, legacy V1 and extension actions.
 * Numeric V1 IDs remain numeric at the registration boundary and are mapped
 * into the dedicated legacy namespace only for runtime lookup.
 */
data class SymbolActionId(val namespace: String, val value: String) {
    init {
        require(namespace.matches(ID_PART)) { "Invalid action namespace" }
        require(value.matches(ID_PART)) { "Invalid action ID" }
    }

    companion object {
        private val ID_PART = Regex("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,255}")

        @JvmStatic fun legacy(numericId: Int) =
            SymbolActionId("legacy", numericId.toString())

        @JvmStatic fun extension(extensionId: String) =
            SymbolActionId("extension", extensionId)
    }
}

/** Runtime action callback. C is supplied by the host and is editor-neutral. */
fun interface SymbolActionHandler<C> {
    fun execute(context: C, payload: String?): Boolean
}

/**
 * Context-scoped action handler registry.
 *
 * Registrations are process-local executable callbacks and are never persisted.
 * Persist SymbolActionId and payload; re-register callbacks when the owning
 * host/session is recreated. Same-ID replacement is token-safe: closing an old
 * registration cannot unregister its replacement.
 */
class SymbolActionRegistry<C> {
    private data class Entry<C>(
        val token: Any,
        val handler: SymbolActionHandler<C>
    )

    private val lock = Any()
    private val entries = LinkedHashMap<SymbolActionId, Entry<C>>()

    /** Register or replace a handler. Closing the handle removes only this registration. */
    fun register(
        id: SymbolActionId,
        handler: SymbolActionHandler<C>
    ): AutoCloseable {
        requireNotNull(handler) { "Action handler cannot be null" }
        val token = Any()
        synchronized(lock) {
            entries[id] = Entry(token, handler)
        }
        var closed = false
        return AutoCloseable {
            synchronized(lock) {
                if (!closed) {
                    closed = true
                    if (entries[id]?.token === token) entries.remove(id)
                }
            }
        }
    }

    /** Convenience overload preserving the original V1 numeric action ID. */
    fun register(
        legacyNumericId: Int,
        handler: SymbolActionHandler<C>
    ): AutoCloseable = register(SymbolActionId.legacy(legacyNumericId), handler)

    fun get(id: SymbolActionId): SymbolActionHandler<C>? =
        synchronized(lock) { entries[id]?.handler }

    fun get(legacyNumericId: Int): SymbolActionHandler<C>? =
        get(SymbolActionId.legacy(legacyNumericId))

    /** null means unregistered; false means a registered handler declined. */
    fun execute(id: SymbolActionId, context: C, payload: String? = null): Boolean? =
        get(id)?.execute(context, payload)

    fun execute(legacyNumericId: Int, context: C, payload: String? = null): Boolean? =
        execute(SymbolActionId.legacy(legacyNumericId), context, payload)

    fun unregister(id: SymbolActionId): Boolean = synchronized(lock) {
        entries.remove(id) != null
    }

    fun clear() = synchronized(lock) { entries.clear() }

    val size: Int
        get() = synchronized(lock) { entries.size }
}
