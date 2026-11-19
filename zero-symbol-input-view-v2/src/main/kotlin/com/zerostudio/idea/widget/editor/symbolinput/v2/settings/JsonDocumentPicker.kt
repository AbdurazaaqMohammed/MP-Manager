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

package com.zerostudio.idea.widget.editor.symbolinput.v2.settings

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lifecycle-bound Storage Access Framework picker for JSON import/export.
 * The host Activity remains in control of document access; no storage
 * permission or direct filesystem path is required.
 */
internal class JsonDocumentPicker(
    context: Context,
    private val onImported: (String) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val activity = context.findComponentActivity()
    private var pendingExport: String? = null
    private val openLauncher: ActivityResultLauncher<Array<String>>?
    private val saveLauncher: ActivityResultLauncher<String>?

    init {
        val host = activity
        if (host == null) {
            openLauncher = null
            saveLauncher = null
        } else {
            val suffix = nextId.incrementAndGet()
            val registry = host.activityResultRegistry
            // Use the registry overload without a LifecycleOwner: settings
            // dialogs are commonly opened after the Activity is already
            // RESUMED, when lifecycle-bound registration is no longer legal.
            openLauncher = registry.register(
                "zero-symbol-json-open-$suffix",
                ActivityResultContracts.OpenDocument()
            ) { uri -> if (uri != null) read(uri) }
            saveLauncher = registry.register(
                "zero-symbol-json-save-$suffix",
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri -> if (uri != null) write(uri) }
            host.lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        openLauncher?.unregister()
                        saveLauncher?.unregister()
                        source.lifecycle.removeObserver(this)
                    }
                }
            })
        }
    }

    fun openJson() {
        runCatching {
            val launcher = openLauncher
                ?: error("JSON file picker requires a ComponentActivity host")
            // Document providers often label JSON as application/octet-stream.
            // Select broadly, then validate the actual contents with the JSON codecs.
            launcher.launch(arrayOf("*/*"))
        }.onFailure { error -> runCatching { onError(error) } }
    }

    fun saveJson(fileName: String, json: String) {
        val launcher = saveLauncher
        if (launcher == null) {
            runCatching { onError(IllegalStateException("JSON file picker requires a ComponentActivity host")) }
            return
        }
        pendingExport = json
        runCatching { launcher.launch(fileName) }.onFailure { error ->
            pendingExport = null
            runCatching { onError(error) }
        }
    }

    private fun read(uri: Uri) {
        // Include decoding AND the import callback in the same failure boundary.
        // Schema errors (including unsupported legacy action IDs) must be reported
        // to the settings UI rather than escaping through ActivityResult delivery.
        runCatching {
            val input = activity?.contentResolver?.openInputStream(uri)
                ?: error("Unable to open selected JSON file")
            input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_IMPORT_BYTES) { "Selected JSON file exceeds the 16 MiB import limit" }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
            }.also(onImported)
        }.onFailure { error -> runCatching { onError(error) } }
    }

    private fun write(uri: Uri) {
        runCatching {
            val payload = pendingExport ?: error("No JSON export is pending")
            val output = activity?.contentResolver?.openOutputStream(uri, "wt")
                ?: error("Unable to create selected JSON file")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            pendingExport = null
        }.onSuccess { }.onFailure { error -> runCatching { onError(error) } }
    }

    private fun Context.findComponentActivity(): ComponentActivity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is ComponentActivity) return current
            current = current.baseContext
        }
        return current as? ComponentActivity
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
        val nextId = AtomicInteger()
    }
}
