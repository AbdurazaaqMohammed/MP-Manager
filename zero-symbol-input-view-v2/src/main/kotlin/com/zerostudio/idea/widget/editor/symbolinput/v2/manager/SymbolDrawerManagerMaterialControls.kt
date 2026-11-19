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

package com.zerostudio.idea.widget.editor.symbolinput.v2.manager

import android.content.Context
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Material3 construction helpers for the symbol drawer manager screen.
 *
 * Every control resolves its colors, shapes and typography from the host
 * Material3 theme through the library's own style attributes. Nothing here
 * hardcodes palette colors or corner radii onto the controls, so light/dark
 * and dynamic color follow the app theme instead of the drawer palette.
 */

/**
 * Outlined Material text field ([TextInputLayout] wrapping a
 * [TextInputEditText]). Set [multiline] for payload editors that accept
 * multi-line text.
 */
internal fun managerOutlinedTextField(
    context: Context,
    hint: CharSequence?,
    multiline: Boolean = false
): TextInputLayout {
    val layout = TextInputLayout(context, null, R.attr.textInputOutlinedStyle)
    val field = TextInputEditText(layout.context)
    if (multiline) {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        field.minLines = 2
    }
    layout.hint = hint
    layout.addView(
        field,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )
    return layout
}

/**
 * Exposed dropdown menu field ([TextInputLayout] with the Material exposed
 * dropdown style wrapping a non-editable [MaterialAutoCompleteTextView]).
 * The returned layout's editText is the dropdown; callers attach an
 * ArrayAdapter and an OnItemClickListener. A click listener that opens the
 * popup is installed so the picker opens on tap like the replaced Spinner.
 */
internal fun managerExposedDropdown(context: Context): TextInputLayout {
    val layout = TextInputLayout(
        context,
        null,
        R.attr.textInputOutlinedExposedDropdownMenuStyle
    )
    val dropdown = MaterialAutoCompleteTextView(layout.context)
    // Not editable: the field is a pure selector, exactly like the Spinner it
    // replaces. MaterialAutoCompleteTextView maps TYPE_NULL to keyListener(null).
    dropdown.inputType = InputType.TYPE_NULL
    dropdown.setOnClickListener { dropdown.showDropDown() }
    layout.addView(
        dropdown,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )
    return layout
}

/** Text-styled Material button for bar actions (bottom undo/batch bars). */
internal fun managerTextButton(context: Context): MaterialButton =
    MaterialButton(ContextThemeWrapper(context, R.style.ThemeOverlay_Material3_Button_TextButton))

/** Outlined-styled Material button for header actions. */
internal fun managerOutlinedButton(context: Context): MaterialButton =
    MaterialButton(context, null, R.attr.materialButtonOutlinedStyle)
