/*
 * This file is part of zero-Symbol-input-view-V2.
 * Copyright (C) 2026 android_zero
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */
package com.zerostudio.idea.widget.editor.symbolinput.v2.action

/**
 * Decorates the existing editor host with runtime action-registry dispatch.
 *
 * All non-extension operations remain delegated unchanged. Registered V2
 * extension handlers take precedence over the host's extension fallback.
 * Unknown V1 numeric actions are represented by LegacySymbolActionAdapter as
 * "legacy.action.<id>"; those first resolve through the registry's numeric
 * compatibility namespace and then fall back to the original host callback.
 *
 * A registered handler returning false is authoritative (declined); fallback
 * is used only when no handler is registered. This distinction prevents an
 * action from being executed twice.
 */
class RegistryBackedSymbolActionHost(
    private val delegate: SymbolActionHost,
    private val registry: SymbolActionRegistry<SymbolActionHost>,
    private val fallback: (String, String?) -> Boolean = { id, payload ->
        delegate.executeExtension(id, payload)
    }
) : SymbolActionHost by delegate {

    override fun executeExtension(extensionId: String, payload: String?): Boolean {
        val legacyId = extensionId
            .takeIf { it.startsWith(LEGACY_EXTENSION_PREFIX) }
            ?.removePrefix(LEGACY_EXTENSION_PREFIX)
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }

        if (legacyId != null) {
            val legacyResult = registry.execute(legacyId, delegate, payload)
            if (legacyResult != null) return legacyResult
        }

        val extensionResult = registry.execute(
            SymbolActionId.extension(extensionId),
            delegate,
            payload
        )
        return extensionResult ?: fallback(extensionId, payload)
    }

    private companion object {
        const val LEGACY_EXTENSION_PREFIX = "legacy.action."
    }
}
