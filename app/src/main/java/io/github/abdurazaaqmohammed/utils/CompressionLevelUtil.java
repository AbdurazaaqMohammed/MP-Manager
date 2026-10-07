package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.text.TextUtils;

import net.lingala.zip4j.model.enums.CompressionLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CompressionLevelUtil {

    private CompressionLevelUtil() {
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (CompressionLevel level : CompressionLevel.values()) names.add(level.name());
        return names;
    }

    public static List<String> labels(Context context) {
        List<String> labels = new ArrayList<>();
        for (String name : names()) labels.add(label(context, name));
        return labels;
    }

    public static String label(Context context, String name) {
        if (TextUtils.isEmpty(name)) return "";
        int id = context.getResources().getIdentifier(
                "compress_level_" + name.toLowerCase(Locale.ROOT), "string", context.getPackageName());
        return id == 0 ? name : context.getString(id);
    }

    public static String nameOf(Context context, String label) {
        if (TextUtils.isEmpty(label)) return null;
        for (String name : names()) if (label(context, name).equals(label)) return name;
        return null;
    }
}
