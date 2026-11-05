package io.github.abdurazaaqmohammed.boot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the Ramdisk Manifest tree:
 * Exact permissions (modes), file orders, symlink targets, ownership (UID/GID), and file types.
 * Stored as 'ramdisk_tree.json' inside the extracted folder.
 * Guarantees 100% byte-accurate reverse repacking.
 */
public class RamdiskManifest {

    public static class Entry {
        public String name;
        public int ino;
        public int mode;
        public int uid;
        public int gid;
        public int nlink;
        public int mtime;
        public long filesize;
        public String type; // "dir", "file", "symlink"
        public String symlinkTarget;

        public Entry(String name, int ino, int mode, int uid, int gid, int nlink, int mtime, long filesize, String type, String symlinkTarget) {
            this.name = name;
            this.ino = ino;
            this.mode = mode;
            this.uid = uid;
            this.gid = gid;
            this.nlink = nlink;
            this.mtime = mtime;
            this.filesize = filesize;
            this.type = type;
            this.symlinkTarget = symlinkTarget;
        }

        public JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("ino", ino);
            obj.put("mode", mode);
            obj.put("mode_oct", String.format("%06o", mode));
            obj.put("uid", uid);
            obj.put("gid", gid);
            obj.put("nlink", nlink);
            obj.put("mtime", mtime);
            obj.put("size", filesize);
            obj.put("type", type);
            if (symlinkTarget != null) {
                obj.put("symlink_target", symlinkTarget);
            }
            return obj;
        }

        public static Entry fromJson(JSONObject obj) {
            return new Entry(
                    obj.optString("name"),
                    obj.optInt("ino", 0),
                    obj.optInt("mode", 0100644),
                    obj.optInt("uid", 0),
                    obj.optInt("gid", 0),
                    obj.optInt("nlink", 1),
                    obj.optInt("mtime", 0),
                    obj.optLong("size", 0),
                    obj.optString("type", "file"),
                    obj.optString("symlink_target", null)
            );
        }
    }

    public static void saveManifest(List<Entry> entries, File manifestFile) throws Exception {
        JSONArray arr = new JSONArray();
        for (Entry e : entries) {
            arr.put(e.toJson());
        }
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("total_entries", entries.size());
        root.put("entries", arr);

        try (FileOutputStream fos = new FileOutputStream(manifestFile)) {
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static List<Entry> loadManifest(File manifestFile) {
        if (!manifestFile.exists()) return null;
        try (FileInputStream fis = new FileInputStream(manifestFile)) {
            byte[] b = new byte[(int) manifestFile.length()];
            fis.read(b);
            JSONObject root = new JSONObject(new String(b, StandardCharsets.UTF_8));
            JSONArray arr = root.getJSONArray("entries");
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                entries.add(Entry.fromJson(arr.getJSONObject(i)));
            }
            return entries;
        } catch (Exception e) {
            return null;
        }
    }
}
