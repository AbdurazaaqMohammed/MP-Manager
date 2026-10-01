package io.github.abdurazaaqmohammed.packs.media;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RecCommon {

    private RecCommon() {
    }

    public static String T(Context context, String key, String fallback) {
        try {
            String lang = "";
            try {
                lang = Locale.getDefault().getLanguage();
            } catch (Exception ignored) {
            }
            String s = RecStrings.get(lang, key);
            if (s != null && !s.isEmpty()) return s;
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static SharedPreferences opts(Context context) {
        return context.getSharedPreferences("recorder", Context.MODE_PRIVATE);
    }

    public static int optInt(Context context, String key, int def) {
        try {
            return opts(context).getInt(key, def);
        } catch (Exception ignored) {
            return def;
        }
    }

    public static void putOpt(Context context, String key, int value) {
        try {
            opts(context).edit().putInt(key, value).apply();
        } catch (Exception ignored) {
        }
    }

    public static boolean optBool(Context context, String key, boolean def) {
        try {
            return opts(context).getBoolean(key, def);
        } catch (Exception ignored) {
            return def;
        }
    }

    public static void putOpt(Context context, String key, boolean value) {
        try {
            opts(context).edit().putBoolean(key, value).apply();
        } catch (Exception ignored) {
        }
    }

    public static File recordingsDir(Context context) {
        try {
            File d = new File(Environment.getExternalStorageDirectory(), "Recordings");
            d.mkdirs();
            if (d.isDirectory()) return d;
        } catch (Exception ignored) {
        }
        File c = new File(context.getCacheDir(), "recordings");
        try {
            c.mkdirs();
        } catch (Exception ignored) {
        }
        return c;
    }

    public static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    public static String fmtDur(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    public static String fmtDurH(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        if (h <= 0) return fmtDur(ms);
        return String.format(Locale.US, "%02d:%02d:%02d", h, (s % 3600) / 60, s % 60);
    }

    public static long mediaDuration(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            return Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    public static void saveAmps(File audio, List<Float> amps) {
        try {
            FileOutputStream os = new FileOutputStream(audio.getAbsolutePath() + ".amp");
            for (Float v : amps) os.write(Math.max(0, Math.min(255, Math.round(v * 255))));
            os.close();
        } catch (Exception ignored) {
        }
    }

    public static List<Float> loadAmps(File audio) {
        List<Float> out = new ArrayList<>();
        try {
            File f = new File(audio.getAbsolutePath() + ".amp");
            if (!f.exists()) return out;
            FileInputStream in = new FileInputStream(f);
            int b;
            while ((b = in.read()) >= 0) out.add(b / 255f);
            in.close();
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return new DecimalFormat("0.0").format(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return new DecimalFormat("0.0").format(mb) + " MB";
        }
        return new DecimalFormat("0.00").format(mb / 1024.0) + " GB";
    }

    public static void shareFile(Context context, File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType(mime);
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(s, T(context, "rec_share", "Share")));
        } catch (Exception e) {
            ToolViewFactory.toast(context, T(context, "rec_share_failed", "Share failed"));
        }
    }

    public static void openFile(Context context, File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent v = new Intent(Intent.ACTION_VIEW);
            v.setDataAndType(uri, mime);
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(v, T(context, "rec_open_with", "Open with")));
        } catch (Exception e) {
            ToolViewFactory.toast(context, T(context, "rec_no_app", "No app found"));
        }
    }
}
