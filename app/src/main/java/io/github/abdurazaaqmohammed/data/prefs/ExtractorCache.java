package io.github.abdurazaaqmohammed.data.prefs;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.ApkExtractor.AppInfo;
import io.github.abdurazaaqmohammed.utils.AppGson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class ExtractorCache {

    private static final int MAX_ENTRIES = 1000;

    private static final Gson GSON = AppGson.get();

    private ExtractorCache() {
    }

    public static final class Snapshot {
        public final int sortMode;
        public final List<AppInfo> apps = new ArrayList<>();

        Snapshot(int sortMode) {
            this.sortMode = sortMode;
        }
    }

    private static final class Row {
        @SerializedName("fp")
        String filePath;
        @SerializedName("n")
        String name;
        @SerializedName("p")
        String pkg;
        @SerializedName("e")
        boolean enabled;
        @SerializedName("s")
        boolean split;
        @SerializedName("fi")
        long firstInstall;
        @SerializedName("lu")
        long lastUpdate;
        @SerializedName("vc")
        int versionCode;
        @SerializedName("vn")
        String versionName;
        @SerializedName("ts")
        int targetSdk;
        @SerializedName("ms")
        int minSdk;
        @SerializedName("in")
        String installer;
        @SerializedName("dd")
        String dataDir;
        @SerializedName("ed")
        String extDataDir;
        @SerializedName("ad")
        String apkDir;
        @SerializedName("bs")
        long baseSize;
        @SerializedName("tz")
        long totalSize;
        @SerializedName("sc")
        int splitCount;
    }

    private static final class Dto {
        @SerializedName("sort")
        int sort;
        @SerializedName("rows")
        List<Row> rows;
    }

    private static File cacheFile(Context context, boolean system) {
        return new File(context.getFilesDir(), system ? "extractor_cache_system.json" : "extractor_cache_user.json");
    }

    public static Snapshot load(Context context, boolean system, String dateFormat) {
        File f;
        try {
            f = cacheFile(context, system);
            if (f == null || !f.isFile() || f.length() <= 0 || f.length() > 1024 * 1024) return null;
        } catch (Exception ignored) {
            return null;
        }
        try (FileReader reader = new FileReader(f)) {
            Dto dto = GSON.fromJson(reader, Dto.class);
            if (dto == null || dto.rows == null) return null;
            Snapshot out = new Snapshot(dto.sort);
            int n = Math.min(dto.rows.size(), MAX_ENTRIES);
            for (int i = 0; i < n; i++) {
                Row r = dto.rows.get(i);
                if (r == null || r.pkg == null || r.pkg.isEmpty() || r.name == null) continue;
                AppInfo ai = new AppInfo(r.filePath, r.name, null, r.pkg, r.enabled, r.split,
                        APKExtractorActivity.formatDate(r.firstInstall, dateFormat),
                        APKExtractorActivity.formatDate(r.lastUpdate, dateFormat),
                        r.versionCode, r.versionName == null ? "" : r.versionName);
                ai.firstInstall = r.firstInstall;
                ai.lastUpdate = r.lastUpdate;
                ai.targetSdk = r.targetSdk;
                ai.minSdk = r.minSdk;
                ai.installer = r.installer;
                ai.dataDir = r.dataDir;
                ai.extDataDir = r.extDataDir;
                ai.apkDir = r.apkDir;
                ai.baseSize = r.baseSize;
                ai.totalSize = r.totalSize;
                ai.splitCount = r.splitCount;
                out.apps.add(ai);
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void save(Context context, boolean system, int sortMode, List<AppInfo> apps) {
        if (context == null || apps == null) return;
        try {
            Dto dto = new Dto();
            dto.sort = sortMode;
            dto.rows = new ArrayList<>(Math.min(apps.size(), MAX_ENTRIES));
            int n = Math.min(apps.size(), MAX_ENTRIES);
            for (int i = 0; i < n; i++) {
                AppInfo ai = apps.get(i);
                if (ai == null || ai.packageName == null || ai.packageName.isEmpty()) continue;
                Row r = new Row();
                r.filePath = ai.filePath;
                r.name = ai.name;
                r.pkg = ai.packageName;
                r.enabled = ai.enabled;
                r.split = ai.isSplit;
                r.firstInstall = ai.firstInstall;
                r.lastUpdate = ai.lastUpdate;
                r.versionCode = ai.getVersionCode();
                r.versionName = ai.versionName;
                r.targetSdk = ai.targetSdk;
                r.minSdk = ai.minSdk;
                r.installer = ai.installer;
                r.dataDir = ai.dataDir;
                r.extDataDir = ai.extDataDir;
                r.apkDir = ai.apkDir;
                r.baseSize = ai.baseSize;
                r.totalSize = ai.totalSize;
                r.splitCount = ai.splitCount;
                dto.rows.add(r);
            }
            File dest = cacheFile(context, system);
            File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(dto, writer);
            }
            if (!tmp.renameTo(dest)) {
                try (FileReader in = new FileReader(tmp); FileWriter out = new FileWriter(dest)) {
                    char[] buf = new char[4096];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                }
                try {
                    tmp.delete();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
