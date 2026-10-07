package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Volume sizes offered when a zip is split into parts. The pref stores the byte
 * count rather than a label, so switching the interface language cannot turn a
 * saved choice into an unknown value.
 *
 * <p>zip4j only splits while creating an archive, so a split zip cannot be
 * added to afterwards.
 */
public final class SplitVolumeUtil {

    private static final long MB = 1024L * 1024L;
    private static final long GB = 1024L * MB;

    /** 0 means "one file", every other entry is a volume size in bytes. */
    private static final long[] SIZES = {0, 50 * MB, 100 * MB, 250 * MB, 500 * MB, GB, 2 * GB, 4 * GB};

    private SplitVolumeUtil() {
    }

    public static List<String> sizes() {
        List<String> sizes = new ArrayList<>();
        for (long size : SIZES) sizes.add(Long.toString(size));
        return sizes;
    }

    public static List<String> labels(Context context) {
        List<String> labels = new ArrayList<>();
        for (long size : SIZES) labels.add(label(context, Long.toString(size)));
        return labels;
    }

    public static String label(Context context, String size) {
        long bytes = toBytes(size);
        if (bytes <= 0) return context.getString(R.string.split_volume_none);
        if (bytes >= GB) return (bytes / GB) + " GB";
        return (bytes / MB) + " MB";
    }

    /** @return the volume size in bytes, 0 when splitting is off */
    public static long toBytes(String size) {
        if (TextUtils.isEmpty(size)) return 0;
        try {
            long bytes = Long.parseLong(size);
            // zip4j refuses anything at or below 64 KiB.
            return bytes > 65536 ? bytes : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}