package io.github.abdurazaaqmohammed.player;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;


import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.github.abdurazaaqmohammed.utils.AccessManager;

public class LocateActivity extends BaseActivity {

    // Directories most shared files (WhatsApp, Telegram, Downloads, ...) end up in.
    private static final String[] LIKELY_DIRS = {
            "Android/media/com.whatsapp/WhatsApp",
            "Android/media/com.whatsapp.w4b/WhatsApp Business",
            "Android/media/org.telegram.messenger/Telegram",
            "Android/media",
            "WhatsApp",
            "WhatsApp Business",
            "Telegram",
            "Download",
            "Documents",
            "Pictures",
            "DCIM",
            "Movies",
            "Music"
    };

    // Roots searched through the elevated backend when root/shizuku is on, because the app uid
    // cannot list them at all.
    private static final String[] LIKELY_ROOT_DIRS = {
            "/data/local/tmp", "/data/data", "/data/user/0", "/data/media/0/Android/data"
    };

    /** Root availability, probed once: each probe reads preferences and may ping the shell. */
    private Boolean rootAccess;
    private List<String> cachedRoots;

    /**
     * Runs the whole lookup without ever drawing a window: the activity is themed
     * {@code Theme.NoDisplay} in the manifest, so the user sees only the file manager opening
     * (or a toast when the file cannot be located) rather than a flashing intermediate screen.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(() -> {
            String path = null;
            Uri failedUri = null;
            try {
                for (Uri uri : collectUris(getIntent())) {
                    failedUri = uri;
                    path = resolvePath(uri);
                    if (path != null && !path.isEmpty()) break;
                }
            } catch (Exception e) {
                Log.e("LocateActivity", "resolve failed", e);
            }
            final String found = path;
            final Uri last = failedUri;
            if (found != null && !found.isEmpty()) {
                openManager(found);
                finish();
            } else {
                Log.e("LocateActivity", "locate failed for: " + last);
                // Toast, not a Snackbar: this activity has Theme.NoDisplay, so there is no
                // content view to anchor one to.
                runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        R.string.locate_failed, Toast.LENGTH_LONG).show());
                finish();
            }
        }).start();
    }

    /**
     * Opens the file manager on {@code path}. Posting the animation keeps the transition visible
     * even though this window is never drawn.
     */
    private void openManager(String path) {
        try {
            Intent open = new Intent(this, MainActivity.class);
            open.putExtra("locatePath", path);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(open);
        } catch (Exception e) {
            Log.e("LocateActivity", "open manager failed", e);
            runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                    R.string.locate_failed, Toast.LENGTH_LONG).show());
        }
        finish();
    }

    private ArrayList<Uri> collectUris(Intent intent) {
        ArrayList<Uri> uris = new ArrayList<>();
        try {
            if (intent == null) return uris;
            String action = intent.getAction();
            if (Intent.ACTION_SEND.equals(action)) {
                Uri uri = null;
                try {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                } catch (Exception ignored) {
                }
                if (uri == null) uri = intent.getData();
                if (uri != null) uris.add(uri);
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                try {
                    ArrayList<Uri> multi = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (multi != null) uris.addAll(multi);
                } catch (Exception ignored) {
                }
                if (uris.isEmpty() && intent.getData() != null) uris.add(intent.getData());
            } else if (intent.getData() != null) {
                uris.add(intent.getData());
            }
        } catch (Exception e) {
            Log.e("LocateActivity", "collect failed", e);
        }
        return uris;
    }

    /**
     * Resolves a content/file Uri to a real path on disk. Besides the standard Document/MediaStore
     * shortcuts this also falls back to looking the file up by its display name and size, which is
     * what makes WhatsApp (and similar providers that never expose {@code _data}) work.
     */
    private String resolvePath(Uri uri) {
        if (uri == null) return null;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String p = uri.getPath();
                if (p != null && new File(p).exists()) return p;
            }
        } catch (Exception ignored) {
        }
        // If the URI path looks like a root-only path (e.g., /data/data/...), try via root
        try {
            String uriPath = uri.getPath();
            if (uriPath != null && (uriPath.startsWith("/data/") || uriPath.startsWith("/data"))) {
                if (hasRootAccess()) {
                    String resolved = resolveRootPath(uriPath);
                    if (resolved != null) return resolved;
                }
            }
        } catch (Exception ignored) { }
        try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                String p = resolveDocumentUri(uri);
                if (p != null) return p;
            }
        } catch (Exception e) {
            Log.e("LocateActivity", "document resolve failed", e);
        }
        try {
            String p = queryData(uri);
            if (p != null) return p;
        } catch (Exception e) {
            Log.e("LocateActivity", "media resolve failed", e);
        }
        // No _data column (typical for WhatsApp/Files providers on modern Android): read the
        // display name/size and search the file system for a match.
        try {
            String name = queryDisplayName(uri);
            long size = querySize(uri);
            if (name != null && !name.isEmpty()) {
                String p = findFileByName(name, size, true);
                if (p != null) return p;
            }
        } catch (Exception e) {
            Log.e("LocateActivity", "name resolve failed", e);
        }
        try {
            String last = uri.getLastPathSegment();
            if (last != null) {
                int slash = last.lastIndexOf('/');
                if (slash >= 0) last = last.substring(slash + 1);
                // MediaStore still results differs when the size is unknown, but the disk walk
                // already covered every root in the same order, so skip it the second time.
                String p = findFileByName(last, -1, false);
                if (p != null) return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Resolves a path the app uid cannot see (anything under /data) through the shell. This is
     * the fast path for files shared straight out of a root browser: the absolute path is already
     * known, so it only has to be confirmed, not searched for.
     */
    private String resolveRootPath(String path) {
        if (!hasRootAccess()) return null;
        if (path == null || path.isEmpty()) return null;
        // One shell round-trip confirms the file; the local stat is tried first because it is
        // free, and listing the parent then covers paths that need a spelling correction.
        try {
            if (new File(path).exists()) return path;
        } catch (Exception ignored) { }
        try {
            if (AccessManager.exists(this, path)) return path;
        } catch (Exception ignored) { }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) return null;
        String parent = path.substring(0, lastSlash);
        String fileName = path.substring(lastSlash + 1);
        try {
            File[] listing = AccessManager.listWithStat(this, parent);
            if (listing != null) {
                for (File entry : listing) {
                    if (fileName.equals(entry.getName())) return path;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
    private String resolveDocumentUri(Uri uri) {
        String doc = DocumentsContract.getDocumentId(uri);
        String authority = uri.getAuthority();
        if (doc == null) return null;
        if ("com.android.externalstorage.documents".equals(authority)) {
            int colon = doc.indexOf(':');
            String volume = colon < 0 ? "primary" : doc.substring(0, colon);
            String rel = colon < 0 ? "" : doc.substring(colon + 1);
            File base = "primary".equalsIgnoreCase(volume)
                    ? Environment.getExternalStorageDirectory()
                    : new File("/storage/" + volume);
            File f = new File(base, rel);
            if (f.exists()) return f.getAbsolutePath();
        } else if ("com.android.providers.downloads.documents".equals(authority)) {
            if (doc.startsWith("raw:")) {
                String p = doc.substring(4);
                if (new File(p).exists()) return p;
            } else {
                try {
                    long id = Long.parseLong(doc);
                    Uri content = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), id);
                    String p = queryData(content);
                    if (p != null) return p;
                } catch (NumberFormatException ignored) {
                }
            }
        } else if ("com.android.providers.media.documents".equals(authority)) {
            int colon = doc.indexOf(':');
            if (colon > 0) {
                String type = doc.substring(0, colon);
                String id = doc.substring(colon + 1);
                Uri content = null;
                if ("image".equals(type)) {
                    content = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    content = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    content = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else if (("document".equals(type) || "download".equals(type))
                        && Build.VERSION.SDK_INT >= 29) {
                    content = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                }
                if (content != null) {
                    String p = queryData(ContentUris.withAppendedId(content, parseLong(id)));
                    if (p != null) return p;
                }
            }
        }
        return null;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private String queryData(Uri uri) {
        String[] projection = new String[]{MediaStore.MediaColumns.DATA};
        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                if (idx >= 0) {
                    String p = cursor.getString(idx);
                    if (p != null && !p.isEmpty() && new File(p).exists()) return p;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                // 0 means "unknown" for many providers; treating it as a real size would make the
                // size comparison never match and force the full walk.
                if (idx >= 0 && !cursor.isNull(idx)) {
                    long s = cursor.getLong(idx);
                    if (s > 0) return s;
                }
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    /**
     * Finds a file by name (preferring one whose size matches), checking MediaStore then disk.
     *
     * @param skipDiskScan set when the disk has already been walked for this resolution; a second
     *                     walk in the same order would not find anything the first one missed
     */
    private String findFileByName(String name, long size, boolean skipDiskScan) {
        if (name == null || name.isEmpty()) return null;
        String fromMedia = queryMediaStoreByName(name, size);
        if (fromMedia != null) return fromMedia;
        if (skipDiskScan) return null;
        for (String root : candidateRoots()) {
            File rootFile = new File(root);
            if (rootFile == null || !rootFile.isDirectory()) continue;
            File found = searchDir(rootFile, name, size, 0);
            if (found != null) return found.getAbsolutePath();
        }
        // If root is available, also search root-only paths like /data/data
        if (hasRootAccess()) {
            String found = searchRootPath(name, size);
            if (found != null) return found;
        }
        return null;
    }

    /** Cached because every call re-reads preferences and, in root mode, spawns a su listing. */
    private boolean hasRootAccess() {
        if (rootAccess == null) {
            try {
                rootAccess = AccessManager.fileOpsOn(this);
            } catch (Exception e) {
                rootAccess = Boolean.FALSE;
            }
        }
        return rootAccess;
    }

    private String queryMediaStoreByName(String name, long size) {
        Uri[] collections = {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        };
        if (Build.VERSION.SDK_INT >= 29) {
            collections = appendDownloadCollection(collections);
        }
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] args = {name};
        String[] projection = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE};
        for (Uri collection : collections) {
            try (Cursor cursor = getContentResolver().query(
                    collection, projection, selection, args, null)) {
                if (cursor == null) continue;
                int dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                int sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                String fallback = null;
                while (cursor.moveToNext()) {
                    if (dataIdx < 0) continue;
                    String p = cursor.getString(dataIdx);
                    if (p == null || p.isEmpty() || !new File(p).exists()) continue;
                    if (size < 0 || sizeIdx < 0 || cursor.getLong(sizeIdx) == size) return p;
                    if (fallback == null) fallback = p;
                }
                if (fallback != null) return fallback;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Uri[] appendDownloadCollection(Uri[] base) {
        Uri[] result = new Uri[base.length + 1];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        return result;
    }

    /**
     * Directories searched for a file whose name is all we know. Cached for the life of the
     * activity because building it lists the external directories and, in root mode, spawns a
     * {@code su} listing of /data. Ordered cheapest-first for the common share sources.
     */
    private List<String> candidateRoots() {
        if (cachedRoots != null) return cachedRoots;
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        File external = Environment.getExternalStorageDirectory();
        if (external != null) {
            for (String dir : LIKELY_DIRS) roots.add(new File(external, dir).getAbsolutePath());
        }
        for (File dir : getExternalFilesDirs(null)) {
            if (dir == null) continue;
            // /storage/XXXX-XXXX/Android/data/<pkg>/files -> 4 levels up is the volume root.
            File storage = dir;
            for (int i = 0; i < 4 && storage != null; i++) storage = storage.getParentFile();
            if (storage != null && !storage.getAbsolutePath().startsWith("/storage/Android")) {
                for (String rel : LIKELY_DIRS) roots.add(new File(storage, rel).getAbsolutePath());
            }
        }
        cachedRoots = new ArrayList<>(roots);
        return cachedRoots;
    }

    /**
     * Root-only paths (/data/data and friends) cannot be listed by the app uid, so they are
     * resolved through the shell with a single {@code su find} per base instead of a per-directory
     * {@code listFiles()} that would always return null there.
     */
    private String searchRootPath(String name, long size) {
        if (!hasRootAccess() || name == null || name.isEmpty()) return null;
        if (name.startsWith("/")) {
            if (new File(name).exists()) return name;
            try {
                if (AccessManager.exists(this, name)) return name;
            } catch (Exception ignored) { }
        }
        for (String base : LIKELY_ROOT_DIRS) {
            String hit = findUnderRoot(base, name, size);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * Looks for {@code name} beneath a root-only directory using the elevated backend.
     *
     * <p>{@code File.listFiles()} returns null for everything under /data without the app uid,
     * so this walks the listing the shell provides instead. Depth is bounded, and a size match
     * wins over the first name match the same way the app-uid search does.
     */
    private String findUnderRoot(String base, String name, long size) {
        try {
            File[] entries = AccessManager.listWithStat(this, base);
            if (entries == null) return null;
            for (File f : entries) {
                if (name.equals(f.getName())) {
                    long len = f.length();
                    if (size < 0 || len == size) return f.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Bounded recursive search so a huge tree cannot hang the lookup. */
    private File searchDir(File dir, String name, long size, int depth) {
        if (depth > 8) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        File nameMatch = null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = searchDir(child, name, size, depth + 1);
                if (found != null) return found;
            } else if (child.getName().equals(name)) {
                if (size < 0 || child.length() == size) return child;
                if (nameMatch == null) nameMatch = child;
            }
        }
        return nameMatch;
    }
}
