/*
 * This file is part of zero-Symbol-input-view-V2.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 */

package com.zerostudio.idea.widget.editor.symbolinput.v2.drawer.config

/**
 * Selects the host presentation contract independently from anchor geometry.
 *
 * BOTTOM_SHEET renders a floating surface anchored to the bottom edge.
 * FULL_SCREEN expands to the complete measured DrawerContainer viewport.
 */
enum class DrawerPresentationMode {
    BOTTOM_SHEET,
    FULL_SCREEN
}
