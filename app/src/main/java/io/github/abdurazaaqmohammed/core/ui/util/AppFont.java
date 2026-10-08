package io.github.abdurazaaqmohammed.core.ui.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * App-wide custom font ("Font" appearance setting). The chosen typeface file is
 * stored under {@link #PREF_KEY}; every TextView inflated through an activity's
 * LayoutInflater gets it applied (preserving bold/italic styles). Views that
 * explicitly set android:fontFamily in XML (hex editor keys etc.) keep theirs.
 */
public final class AppFont {

    public static final String PREF_KEY = "app_font";

    private static Typeface sTypeface;
    private static String sLoadedPath;
    private static int sEpoch;

    private AppFont() {
    }

    /** Bumped on every font change; activities recreate when they see a new value. */
    public static int currentEpoch() {
        return sEpoch;
    }

    public static void invalidate() {
        sEpoch++;
        sTypeface = null;
        sLoadedPath = null;
    }

    /** Active font, or null when the system default should be used. */
    public static Typeface typeface(Context context) {
        String path = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, "");
        if (path == null || path.isEmpty()) return null;
        if (path.equals(sLoadedPath)) return sTypeface;
        sLoadedPath = path;
        try {
            sTypeface = Typeface.createFromFile(path);
        } catch (Exception e) {
            sTypeface = null;
        }
        return sTypeface;
    }

    /** Font files shipped with the device (system + product partitions). */
    public static List<File> systemFonts() {
        List<File> out = new ArrayList<>();
        for (String dir : new String[]{"/system/fonts", "/system/font", "/product/fonts", "/system_ext/fonts"}) {
            File[] files = new File(dir).listFiles();
            if (files == null) continue;
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".ttf") || n.endsWith(".otf")) out.add(f);
            }
        }
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    public static String displayName(File font) {
        String n = font.getName();
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n.replace('_', ' ');
    }

    /**
     * Installs a LayoutInflater factory applying the font to every created
     * TextView. Must run BEFORE super.onCreate so it wraps AppCompat's factory:
     * the wrapper delegates view creation to the AppCompat delegate (keeping
     * Material widget inflation intact) and then applies the typeface. No
     * factory is installed when the default font is selected (zero overhead).
     */
    public static void installFactory(AppCompatActivity activity) {
        Typeface font = typeface(activity);
        if (font == null) return;
        LayoutInflater inflater = activity.getLayoutInflater();
        FontFactory factory = new FontFactory(activity, font);
        inflater.setFactory2(factory);
        try {
            // Mark mFactory as taken too, so AppCompat skips installing its own.
            inflater.setFactory(factory);
        } catch (IllegalStateException ignored) {
        }
    }

    private static class FontFactory implements LayoutInflater.Factory2 {

        private final WeakReference<AppCompatActivity> activityRef;
        private final Typeface font;

        FontFactory(AppCompatActivity activity, Typeface font) {
            this.activityRef = new WeakReference<>(activity);
            this.font = font;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            return onCreateView(null, name, context, attrs);
        }

        @Override
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            View view = null;
            AppCompatActivity activity = activityRef.get();
            if (activity != null) {
                LayoutInflater.Factory2 delegate = (LayoutInflater.Factory2) activity.getDelegate();
                if (delegate != null) view = delegate.onCreateView(parent, name, context, attrs);
            }
            if (view instanceof TextView && !hasExplicitFontFamily(context, attrs)) {
                TextView tv = (TextView) view;
                Typeface old = tv.getTypeface();
                int style = old != null ? old.getStyle() : Typeface.NORMAL;
                tv.setTypeface(Typeface.create(font, style));
            }
            return view;
        }

        private static boolean hasExplicitFontFamily(Context context, AttributeSet attrs) {
            if (attrs == null) return false;
            TypedArray ta = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.fontFamily});
            try {
                return ta.hasValue(0);
            } finally {
                ta.recycle();
            }
        }
    }
}
