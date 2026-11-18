package io.github.abdurazaaqmohammed.plugins.packs;

import androidx.annotation.Keep;

/**
 * Pins Material {@code R.attr} constants that first-party tool packs link
 * against from their own dex.
 *
 * <p>Packs are built with {@code compileOnly project(':sdk')} and
 * {@code compileOnly 'com.google.android.material:material:1.12.0'}, so their
 * APKs contain symbolic references to these host-side R fields but not the
 * fields themselves. Nothing in the app necessarily reads every one of them,
 * so AGP/R8 may drop them from {@code com.google.android.material.R$attr} and
 * a pack then dies with {@code NoSuchFieldError} while building its views.
 * Referencing them here keeps the fields (and their R class) in the host dex.
 */
@Keep
public final class PackMaterialAttrs {

    @Keep public static final int COLOR_PRIMARY =
            com.google.android.material.R.attr.colorPrimary;
    @Keep public static final int COLOR_ERROR =
            com.google.android.material.R.attr.colorError;
    @Keep public static final int COLOR_OUTLINE =
            com.google.android.material.R.attr.colorOutline;
    @Keep public static final int BORDERLESS_BUTTON_STYLE =
            com.google.android.material.R.attr.borderlessButtonStyle;
    @Keep public static final int COLOR_ON_PRIMARY_CONTAINER =
            com.google.android.material.R.attr.colorOnPrimaryContainer;

    private PackMaterialAttrs() {
    }
}
