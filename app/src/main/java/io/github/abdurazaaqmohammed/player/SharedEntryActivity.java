package io.github.abdurazaaqmohammed.player;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.InstallerConfig;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Single share/view entry point replacing the old Locate / Save / Image-edit /
 * Text-editor intent-filters.
 *
 * <p>Why one activity: four catch-all entries flooded the system share sheet
 * and each showed even when it could do nothing (e.g. "Locate" on a cloud
 * file with no local path). This activity appears once, figures out what the
 * shared content actually supports from its mime type, disables what cannot
 * work, and resolves the real on-disk path in the background so the UI opens
 * instantly.
 */
public class SharedEntryActivity extends BaseActivity {

    private static final Set<String> IMAGE_EXTS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif",
            "avif", "ico", "tif", "tiff", "dng", "psd"));

    private static final Set<String> TEXT_EXTS = new HashSet<>(Arrays.asList(
            "txt", "md", "markdown", "json", "xml", "html", "htm", "css", "csv",
            "log", "ini", "cfg", "conf", "prop", "properties", "yml", "yaml",
            "toml", "js", "ts", "jsx", "tsx", "java", "kt", "kts", "py", "c",
            "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "sh", "smali",
            "gradle", "srt", "vtt", "lrc", "tex", "axml"));

    /** Matches the media extensions the player itself queues as siblings. */
    private static final Set<String> MEDIA_EXTS = new HashSet<>(Arrays.asList(
            "mp3", "wav", "flac", "ogg", "oga", "m4a", "aac", "wma", "opus",
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "3g2", "ts", "flv",
            "wmv", "m4v"));

    private static final Set<String> ARCHIVE_EXTS = new HashSet<>(Arrays.asList(
            "zip", "jar", "apk", "apks", "xapk", "apkm", "epub", "cbz",
            "tar", "tgz", "gz", "bz2", "xz", "zst", "lz4", "7z", "rar"));

    private static final Set<String> ARCHIVE_MIMES = new HashSet<>(Arrays.asList(
            "application/zip", "application/x-zip-compressed",
            "application/java-archive", "application/vnd.android.package-archive",
            "application/x-7z-compressed", "application/vnd.rar",
            "application/x-rar-compressed", "application/x-tar",
            "application/gzip", "application/x-gzip", "application/x-bzip2",
            "application/x-xz", "application/zstd", "application/epub+zip"));

    private final List<Uri> uris = new ArrayList<>();
    private String sharedText;
    private String intentMime;
    /** Package of the app that shared the content, when it can be determined. */
    private String sourcePackage;

    private final List<ItemState> items = new ArrayList<>();
    private int selected = 0;

    private ExecutorService executor;
    private Future<?> resolveJob;
    private volatile boolean cancelled;

    private ImageView iconView;
    private TextView nameView;
    private TextView metaView;
    private TextView pathView;
    private TextView pathHintView;
    private LinearProgressIndicator progress;
    private LinearLayout fileList;
    private MaterialButton locateBtn;
    private MaterialButton textBtn;
    private MaterialButton imageBtn;
    private MaterialButton mediaBtn;
    private MaterialButton hexBtn;
    private MaterialButton archiveBtn;
    private MaterialButton saveBtn;
    private TextView locateHint;
    private TextView textHint;
    private TextView imageHint;
    private TextView mediaHint;
    private TextView hexHint;
    private TextView archiveHint;
    private TextView saveHint;

    private static final class ItemState {
        Uri uri;
        String name;
        String mime;
        long size = -1;
        SharedPathResolver.Result resolved;
        boolean resolving = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_entry);

        MaterialToolbar toolbar = findViewById(R.id.sharedEntryToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        iconView = findViewById(R.id.sharedEntryIcon);
        nameView = findViewById(R.id.sharedEntryName);
        metaView = findViewById(R.id.sharedEntryMeta);
        pathView = findViewById(R.id.sharedEntryPath);
        pathHintView = findViewById(R.id.sharedEntryPathHint);
        progress = findViewById(R.id.sharedEntryProgress);
        fileList = findViewById(R.id.sharedEntryFileList);
        locateBtn = findViewById(R.id.sharedEntryLocate);
        textBtn = findViewById(R.id.sharedEntryText);
        imageBtn = findViewById(R.id.sharedEntryImage);
        mediaBtn = findViewById(R.id.sharedEntryMedia);
        hexBtn = findViewById(R.id.sharedEntryHex);
        archiveBtn = findViewById(R.id.sharedEntryArchive);
        saveBtn = findViewById(R.id.sharedEntrySave);
        locateHint = findViewById(R.id.sharedEntryLocateHint);
        textHint = findViewById(R.id.sharedEntryTextHint);
        imageHint = findViewById(R.id.sharedEntryImageHint);
        mediaHint = findViewById(R.id.sharedEntryMediaHint);
        hexHint = findViewById(R.id.sharedEntryHexHint);
        archiveHint = findViewById(R.id.sharedEntryArchiveHint);
        saveHint = findViewById(R.id.sharedEntrySaveHint);

        parseIntent(getIntent());
        if (uris.isEmpty() && (sharedText == null || sharedText.isEmpty())) {
            finish();
            return;
        }
        sourcePackage = detectSourcePackage(getIntent());
        // Remember who handed us the file so the "app that shared this file"
        // installer-package mode can attribute an install back to them.
        InstallerConfig.setInitiatorPackage(this, sourcePackage);

        for (Uri uri : uris) {
            ItemState item = new ItemState();
            item.uri = uri;
            item.name = guessName(uri);
            items.add(item);
        }

        updateCapabilities();
        renderSelection();

        locateBtn.setOnClickListener(v -> openLocate());
        textBtn.setOnClickListener(v -> openTextEditor());
        imageBtn.setOnClickListener(v -> openImageEditor());
        mediaBtn.setOnClickListener(v -> openMediaPlayer());
        hexBtn.setOnClickListener(v -> openHexEditor());
        archiveBtn.setOnClickListener(v -> openArchiveViewer());
        saveBtn.setOnClickListener(v -> pickSaveFolder());

        // Resolve immediately on a worker thread; the UI is already visible.
        executor = Executors.newSingleThreadExecutor();
        resolveJob = executor.submit(this::resolveAll);
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        try {
            if (resolveJob != null) resolveJob.cancel(true);
        } catch (Exception ignored) {
        }
        try {
            if (executor != null) executor.shutdownNow();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------ intent in

    /**
     * Figures out which app shared the content so path resolution can look in
     * that app's private data dir first instead of guessing by file name.
     * Tries the share-sheet referrer ({@code android-app://<package>}), then
     * maps the content authority back to an installed package.
     */
    private String detectSourcePackage(Intent intent) {
        // 1. Referrer set by the system chooser, e.g. android-app://com.whatsapp
        try {
            if (Build.VERSION.SDK_INT >= 22) {
                Uri referrer = getReferrer();
                if (referrer != null && "android-app".equals(referrer.getScheme())) {
                    String host = referrer.getHost();
                    if (isInstalledPackage(host)) return host;
                }
            }
        } catch (Exception ignored) {
        }
        // 2. Content authority, e.g. com.whatsapp.provider.media -> com.whatsapp
        try {
            for (Uri uri : uris) {
                String pkg = packageFromAuthority(uri.getAuthority());
                if (pkg != null) return pkg;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isInstalledPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            //noinspection deprecation
            return getPackageManager().getPackageInfo(pkg, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String packageFromAuthority(String authority) {
        if (authority == null || authority.isEmpty()) return null;
        // System providers carry no app identity.
        if (authority.startsWith("com.android.")
                || authority.startsWith("android.")
                || authority.startsWith("com.google.android.providers.")) return null;
        String candidate = authority;
        while (candidate != null && !candidate.isEmpty()) {
            if (isInstalledPackage(candidate)) return candidate;
            int dot = candidate.lastIndexOf('.');
            if (dot <= 0) break;
            candidate = candidate.substring(0, dot);
        }
        return null;
    }

    private void parseIntent(Intent intent) {
        uris.clear();
        sharedText = null;
        intentMime = intent == null ? null : intent.getType();
        if (intent == null) return;
        String action = intent.getAction();
        try {
            if (Intent.ACTION_SEND.equals(action)) {
                Uri uri = null;
                try {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                } catch (Exception ignored) {
                }
                if (uri == null) uri = intent.getData();
                if (uri != null) uris.add(uri);
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                try {
                    ArrayList<Uri> multi = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (multi != null) uris.addAll(multi);
                } catch (Exception ignored) {
                }
                if (uris.isEmpty() && intent.getData() != null) uris.add(intent.getData());
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else {
                if (intent.getData() != null) uris.add(intent.getData());
                else {
                    try {
                        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                        if (stream != null) uris.add(stream);
                    } catch (Exception ignored) {
                    }
                }
                try {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        // Take one stream permission so forwarded intents can read the content.
        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------ capabilities

    private void updateCapabilities() {
        boolean hasUris = !uris.isEmpty();
        boolean hasText = sharedText != null && !sharedText.isEmpty();

        boolean anyImage = false;
        boolean anyTextLike = hasText;
        boolean allImages = hasUris;
        boolean anyMedia = false;
        boolean allMedia = hasUris;
        boolean anyArchive = false;
        boolean allArchive = hasUris;
        for (ItemState item : items) {
            String mime = effectiveMime(item);
            String ext = extensionOf(item.name);
            boolean image = isImage(mime, ext);
            boolean textLike = isTextLike(mime, ext);
            boolean media = isMedia(mime, ext);
            boolean archive = isArchive(mime, ext);
            anyImage |= image;
            anyTextLike |= textLike;
            allImages &= image;
            anyMedia |= media;
            allMedia &= media;
            anyArchive |= archive;
            allArchive &= archive;
        }

        // Save works for any stream, plus shared text saved as a .txt file.
        setAction(saveBtn, saveHint, hasUris || hasText,
                hasUris || hasText ? null : getString(R.string.shared_entry_nothing_to_save));

        // Text editor: enabled when there is text or anything text-like. Pure
        // media shares disable it instead of opening binary in the editor.
        boolean textOk = anyTextLike;
        setAction(textBtn, textHint, textOk,
                textOk ? null : getString(R.string.shared_entry_no_text));

        // Image editor needs exactly one image.
        boolean imageOk = items.size() == 1 && anyImage && allImages;
        String imageReason = null;
        if (!imageOk) {
            if (items.size() != 1) imageReason = getString(R.string.shared_entry_single_image);
            else imageReason = getString(R.string.shared_entry_not_image);
        }
        setAction(imageBtn, imageHint, imageOk, imageReason);

        // Media player needs exactly one video/audio file.
        boolean mediaOk = items.size() == 1 && anyMedia && allMedia;
        String mediaReason = null;
        if (!mediaOk) {
            if (items.size() != 1) mediaReason = getString(R.string.shared_entry_single_media);
            else mediaReason = getString(R.string.shared_entry_no_media);
        }
        setAction(mediaBtn, mediaHint, mediaOk, mediaReason);

        // Hex editor: any single file works. Without a directly readable path a
        // temporary copy is staged from the shared stream, so nothing to check here.
        boolean hexOk = hasUris && items.size() == 1;
        setAction(hexBtn, hexHint, hexOk,
                hexOk ? null : getString(R.string.shared_entry_single_file));

        // Archive viewer needs exactly one archive.
        boolean archiveOk = items.size() == 1 && anyArchive && allArchive;
        String archiveReason = null;
        if (!archiveOk) {
            if (items.size() != 1) archiveReason = getString(R.string.shared_entry_single_archive);
            else archiveReason = getString(R.string.shared_entry_not_archive);
        }
        setAction(archiveBtn, archiveHint, archiveOk, archiveReason);

        // Locate starts disabled; the resolver enables it per file below.
        // With no streams at all (shared text only) there is nothing to locate.
        if (items.isEmpty()) {
            setAction(locateBtn, locateHint, false,
                    getString(R.string.shared_entry_no_path_hint));
        } else {
            setAction(locateBtn, locateHint, false, getString(R.string.locate_resolving));
        }
    }

    private String effectiveMime(ItemState item) {
        if (item.mime != null) return item.mime;
        if (items.size() == 1 && intentMime != null) return intentMime;
        return null;
    }

    // ------------------------------------------------------------ resolving

    private void resolveAll() {
        // Fast meta first (one indexed query per uri), then the path lookup.
        for (int i = 0; i < items.size() && !cancelled; i++) {
            ItemState item = items.get(i);
            queryMeta(item);
            final int index = i;
            runOnUiThread(() -> {
                if (index == selected) renderSelection();
                renderFileList();
            });
        }
        runOnUiThread(this::updateCapabilities);
        for (int i = 0; i < items.size() && !cancelled; i++) {
            ItemState item = items.get(i);
            SharedPathResolver.Result r;
            try {
                r = SharedPathResolver.resolve(this, item.uri, sourcePackage, () -> cancelled);
            } catch (Exception e) {
                r = new SharedPathResolver.Result();
            }
            item.resolved = r;
            item.resolving = false;
            if (item.name == null || item.name.isEmpty()) item.name = r.displayName;
            final int index = i;
            runOnUiThread(() -> {
                if (index == selected) renderSelection();
                renderFileList();
            });
        }
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            progress.setIndeterminate(false);
            renderSelection();
            renderFileList();
        });
    }

    private void queryMeta(ItemState item) {
        try {
            String type = getContentResolver().getType(item.uri);
            if (type != null && !type.isEmpty()) item.mime = type;
        } catch (Exception ignored) {
        }
        String[] projection = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        try (Cursor c = getContentResolver().query(item.uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) {
                    String name = c.getString(nameIdx);
                    if (name != null && !name.isEmpty()) item.name = name;
                }
                int sizeIdx = c.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) {
                    long s = c.getLong(sizeIdx);
                    if (s > 0) item.size = s;
                }
            }
        } catch (Exception ignored) {
        }
        if (item.name == null || item.name.isEmpty()) item.name = guessName(item.uri);
        if (item.mime == null) item.mime = guessMimeFromName(item.name);
    }

    // ------------------------------------------------------------ rendering

    private ItemState current() {
        if (items.isEmpty()) return null;
        if (selected < 0 || selected >= items.size()) selected = 0;
        return items.get(selected);
    }

    private void renderSelection() {
        ItemState item = current();
        if (item == null) {
            nameView.setText(sharedText != null ? sharedText : "");
            metaView.setText(intentMime != null ? intentMime : "");
            pathView.setText("");
            pathHintView.setText(R.string.locate_failed);
            setAction(locateBtn, locateHint, false,
                    getString(R.string.shared_entry_no_path_hint));
            return;
        }
        nameView.setText(item.name != null ? item.name : getString(R.string.shared_entry_unknown));
        StringBuilder meta = new StringBuilder();
        if (item.mime != null && !item.mime.isEmpty()) meta.append(item.mime);
        if (item.size > 0) {
            if (meta.length() > 0) meta.append(" · ");
            try {
                meta.append(Formatter.formatShortFileSize(this, item.size));
            } catch (Exception e) {
                meta.append(item.size).append(" B");
            }
        }
        if (uris.size() > 1) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(getString(R.string.shared_entry_file_count, selected + 1, items.size()));
        }
        if (sourcePackage != null && !sourcePackage.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(sourcePackage);
        }
        metaView.setText(meta.toString());
        iconView.setImageResource(iconFor(item.mime, item.name));

        if (item.resolving) {
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            pathView.setText(R.string.locate_resolving);
            pathHintView.setText("");
            setAction(locateBtn, locateHint, false, getString(R.string.locate_resolving));
        } else if (item.resolved != null && item.resolved.path != null && !item.resolved.inaccessible) {
            progress.setVisibility(View.GONE);
            pathView.setText(item.resolved.path);
            if (item.resolved.potential) {
                pathHintView.setText(R.string.shared_entry_potential_match);
            } else if (item.resolved.needsElevated) {
                pathHintView.setText(R.string.shared_entry_elevated_path);
            } else {
                pathHintView.setText(R.string.shared_entry_path_ok);
            }
            setAction(locateBtn, locateHint, true, null);
        } else if (item.resolved != null && item.resolved.inaccessible) {
            progress.setVisibility(View.GONE);
            pathView.setText(item.resolved.path != null ? item.resolved.path : "");
            pathHintView.setText(R.string.shared_entry_needs_root);
            setAction(locateBtn, locateHint, false,
                    getString(R.string.shared_entry_needs_root));
        } else {
            if (allDone()) progress.setVisibility(View.GONE);
            pathView.setText(R.string.locate_failed);
            pathHintView.setText(R.string.shared_entry_no_path_hint);
            setAction(locateBtn, locateHint, false,
                    getString(R.string.shared_entry_no_path_hint));
        }
        renderFileList();
    }

    private boolean allDone() {
        for (ItemState item : items) if (item.resolving) return false;
        return true;
    }

    private void renderFileList() {
        fileList.removeAllViews();
        if (items.size() <= 1) {
            fileList.setVisibility(View.GONE);
            return;
        }
        fileList.setVisibility(View.VISIBLE);
        for (int i = 0; i < items.size(); i++) {
            ItemState item = items.get(i);
            TextView row = new TextView(this);
            row.setTextSize(13);
            int pad = dpPx(10);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            String status;
            if (item.resolving) status = "…";
            else if (item.resolved != null && item.resolved.path != null && !item.resolved.inaccessible) {
                status = item.resolved.potential ? "~" : "✓";
            } else status = "✕";
            row.setText((i == selected ? "▶ " : "    ") + status + "  "
                    + (item.name != null ? item.name : "?"));
            row.setAlpha(i == selected ? 1f : 0.75f);
            final int index = i;
            row.setOnClickListener(v -> {
                selected = index;
                renderSelection();
            });
            fileList.addView(row);
        }
    }

    private void setAction(MaterialButton btn, TextView hint, boolean enabled, String reason) {
        btn.setEnabled(enabled);
        btn.setAlpha(enabled ? 1f : 0.55f);
        if (reason == null || reason.isEmpty() || enabled) {
            hint.setVisibility(View.GONE);
        } else {
            hint.setVisibility(View.VISIBLE);
            hint.setText(reason);
        }
    }

    // ------------------------------------------------------------ actions

    private void openLocate() {
        ItemState item = current();
        if (item == null || item.resolved == null
                || item.resolved.path == null || item.resolved.inaccessible) return;
        try {
            String path = rootRemapForElevated(item.resolved.path);
            Intent open = new Intent(this, MainActivity.class);
            open.putExtra("locatePath", path);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(open);
            finish();
        } catch (Exception e) {
            Extensions.showMessage(this, getString(R.string.locate_failed));
        }
    }

    private void openTextEditor() {
        try {
            if (!uris.isEmpty()) {
                for (Uri uri : uris) {
                    Intent open = new Intent(this, TextEditorActivity.class);
                    open.setAction(Intent.ACTION_VIEW);
                    String mime = intentMime;
                    if (mime == null || mime.isEmpty()) {
                        try {
                            mime = getContentResolver().getType(uri);
                        } catch (Exception ignored) {
                        }
                    }
                    if (mime == null || mime.isEmpty()) mime = "text/plain";
                    open.setDataAndType(uri, mime);
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ItemState match = findItem(uri);
                    if (match != null && match.resolved != null
                            && match.resolved.path != null && !match.resolved.inaccessible) {
                        open.putExtra("path", match.resolved.path);
                    }
                    if (sharedText != null && !sharedText.isEmpty()) {
                        open.putExtra(Intent.EXTRA_TEXT, sharedText);
                    }
                    // SingleTop routes follow-up intents to onNewIntent as extra tabs.
                    startActivity(open);
                }
                finish();
            } else if (sharedText != null) {
                Intent open = new Intent(this, TextEditorActivity.class);
                open.setAction(Intent.ACTION_SEND);
                open.setType("text/plain");
                open.putExtra(Intent.EXTRA_TEXT, sharedText);
                startActivity(open);
                finish();
            }
        } catch (Exception e) {
            Extensions.showMessage(this, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private void openImageEditor() {
        ItemState item = current();
        if (item == null || items.size() != 1) return;
        try {
            String path = null;
            if (item.resolved != null && item.resolved.path != null && !item.resolved.inaccessible) {
                path = rootRemapForElevated(item.resolved.path);
            }
            if (path != null && (new File(path).isFile() || isRootFilePath(path))) {
                ImageEditActivity.sessionPath = path;
                startActivity(new Intent(this, ImageEditActivity.class));
            } else {
                Intent open = new Intent(this, ImageEditActivity.class);
                open.setAction(Intent.ACTION_SEND);
                open.putExtra(Intent.EXTRA_STREAM, item.uri);
                open.setType(item.mime != null ? item.mime : "image/*");
                open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(open);
            }
            finish();
        } catch (Exception e) {
            Extensions.showMessage(this, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private boolean isRootFilePath(String path) {
        if (path == null) return false;
        try {
            RootManager rm = RootManager.getInstance(this);
            if (rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
                RootManager.ShellResult r = rm.execute("test -f " + RootManager.escapeShellArg(path), 5);
                return r.isSuccess();
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * When elevated file ops are on, editors that browse through the elevated
     * backend see the raw {@code /data/media} tree rather than the FUSE
     * {@code /storage/emulated} view, so hand them the raw path.
     */
    private String rootRemapForElevated(String path) {
        try {
            RootManager rm = RootManager.getInstance(this);
            if (rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
                if (path.startsWith("/storage/emulated/0")) {
                    return "/data/media/0" + path.substring("/storage/emulated/0".length());
                } else if (path.startsWith("/storage/emulated/")) {
                    return "/data/media/" + path.substring("/storage/emulated/".length());
                }
            }
        } catch (Exception ignored) {
        }
        return path;
    }

    // ------------------------------------------------------------ media / hex / archive

    private void openMediaPlayer() {
        ItemState item = current();
        if (item == null || items.size() != 1) return;
        File direct = usableResolvedPath(item);
        if (direct != null) {
            launchPlayer(direct.getAbsolutePath());
        } else {
            // No on-disk path (cloud or app-private content): stage a copy in a
            // folder of its own so the player's sibling scan sees just this file.
            stageTempCopy(item, staged -> {
                if (!launchPlayer(staged.getAbsolutePath())) return;
                finish();
            });
        }
    }

    private boolean launchPlayer(String path) {
        try {
            Intent open = new Intent(this, MediaPlayerActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.putExtra("play_path", path);
            startActivity(open);
            return true;
        } catch (Exception e) {
            Extensions.showMessage(this, e.getMessage() != null ? e.getMessage() : e.toString());
            return false;
        }
    }

    private void openHexEditor() {
        ItemState item = current();
        if (item == null || items.size() != 1) return;
        File direct = usableResolvedPath(item);
        if (direct != null) {
            launchHexEditor(direct.getAbsolutePath(), null);
        } else if (item.resolved != null && item.resolved.path != null) {
            // The file exists but only through root/Shizuku. Stage a readable
            // copy from the shared stream and let the hex editor write changes
            // back to the original through its root-original path flow.
            String original = item.resolved.path;
            stageTempCopy(item, staged -> {
                String rootOriginal = original.equals(staged.getAbsolutePath())
                        ? null : original;
                if (!launchHexEditor(staged.getAbsolutePath(), rootOriginal)) return;
                finish();
            });
        } else {
            stageTempCopy(item, staged -> {
                if (!launchHexEditor(staged.getAbsolutePath(), null)) return;
                finish();
            });
        }
    }

    private boolean launchHexEditor(String path, String rootOriginalPath) {
        try {
            Intent open = new Intent(this, HexEditorActivity.class);
            open.putExtra("path", path);
            if (rootOriginalPath != null && !rootOriginalPath.equals(path)) {
                open.putExtra("rootOriginalPath", rootOriginalPath);
            }
            startActivity(open);
            return true;
        } catch (Exception e) {
            Extensions.showMessage(this, e.getMessage() != null ? e.getMessage() : e.toString());
            return false;
        }
    }

    private void openArchiveViewer() {
        ItemState item = current();
        if (item == null || items.size() != 1) return;
        File direct = usableResolvedPath(item);
        if (direct != null) {
            launchArchiveViewer(direct.getAbsolutePath());
        } else {
            stageTempCopy(item, staged -> {
                if (!launchArchiveViewer(staged.getAbsolutePath())) return;
                finish();
            });
        }
    }

    private boolean launchArchiveViewer(String path) {
        try {
            Intent open = new Intent(this, MainActivity.class);
            // locatePath puts MainActivity into locate mode (pane 1 is left for
            // onResume); openArchivePath tells it to browse the archive itself
            // instead of opening the parent folder.
            open.putExtra("locatePath", path);
            open.putExtra("openArchivePath", path);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(open);
            return true;
        } catch (Exception e) {
            Extensions.showMessage(this, e.getMessage() != null ? e.getMessage() : e.toString());
            return false;
        }
    }

    /** The resolved path, when it is a plain file this process can read. */
    private File usableResolvedPath(ItemState item) {
        if (item.resolved == null || item.resolved.path == null || item.resolved.inaccessible) return null;
        try {
            File f = new File(item.resolved.path);
            if (f.isFile()) return f;
        } catch (Exception ignored) {
        }
        return null;
    }

    private interface StageConsumer {
        void accept(File staged);
    }

    /**
     * Copies the shared stream into a unique temp folder (so later operations
     * never pick up unrelated leftovers) and hands the file to {@code consumer}
     * on the UI thread.
     */
    private void stageTempCopy(ItemState item, StageConsumer consumer) {
        ProgressManager pm = new ProgressManager(this, true).show();
        Uri uri = item.uri;
        new Thread(() -> {
            File staged = null;
            try {
                File base = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                File dir = new File(base, "shared_entry_" + System.currentTimeMillis());
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdirs failed");
                String name = (item.name != null && !item.name.isEmpty() ? item.name : "shared_file")
                        .replaceAll("[\\\\/:*?\"<>|]", "_");
                staged = new File(dir, name);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream os = new FileOutputStream(staged)) {
                    if (in == null) throw new IOException("stream closed");
                    byte[] buf = new byte[65536];
                    int read;
                    while ((read = in.read(buf)) != -1) os.write(buf, 0, read);
                }
                if (staged.length() == 0) throw new IOException("empty copy");
            } catch (Exception e) {
                staged = null;
            }
            final File result = staged;
            runOnUiThread(() -> {
                try {
                    pm.dismiss();
                } catch (Exception ignored) {
                }
                if (cancelled || isFinishing() || isDestroyed()) return;
                if (result != null && result.isFile()) {
                    consumer.accept(result);
                } else {
                    Extensions.showMessage(this, getString(R.string.shared_entry_stage_failed));
                }
            });
        }).start();
    }

    private void pickSaveFolder() {
        if (uris.isEmpty() && (sharedText == null || sharedText.isEmpty())) return;
        try {
            FilePickerDialog.Properties props = new FilePickerDialog.Properties();
            props.selection_mode = FilePickerDialog.SINGLE_MODE;
            props.selection_type = FilePickerDialog.DIR_SELECT;
            FilePickerDialog picker = new FilePickerDialog(this, props);
            picker.setTitle(getString(R.string.save_to_folder));
            picker.setDialogSelectionListener(files -> {
                if (files == null || files.length == 0 || files[0] == null) return;
                saveToFolder(new File(files[0]));
            });
            picker.show();
        } catch (Exception e) {
            Extensions.showMessage(this, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private void saveToFolder(File dir) {
        List<Uri> copy = new ArrayList<>(uris);
        String text = sharedText;
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            int done = 0;
            int total = copy.size() + ((text != null && !text.isEmpty() && copy.isEmpty()) ? 1 : 0);
            int n = 0;
            for (Uri uri : copy) {
                n++;
                try {
                    String name = displayName(uri, n).replaceAll("[\\\\/:*?\"<>|]", "_");
                    File dest = uniqueDest(dir, name);
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         OutputStream os = new FileOutputStream(dest)) {
                        if (in == null) continue;
                        byte[] buf = new byte[65536];
                        int read;
                        while ((read = in.read(buf)) != -1) os.write(buf, 0, read);
                    }
                    done++;
                } catch (Exception ignored) {
                }
                if (cancelled) break;
            }
            if (copy.isEmpty() && text != null && !text.isEmpty()) {
                try {
                    File dest = uniqueDest(dir, "shared_text.txt");
                    try (OutputStream os = new FileOutputStream(dest)) {
                        os.write(text.getBytes(StandardCharsets.UTF_8));
                    }
                    done++;
                } catch (Exception ignored) {
                }
            }
            try {
                pm.dismiss();
            } catch (Exception ignored) {
            }
            int doneCount = done;
            int totalCount = Math.max(total, 1);
            runOnUiThread(() -> {
                Extensions.showMessage(this,
                        getString(R.string.saved_i_of_i_to_x, doneCount, totalCount, dir.getName()));
                finish();
            });
        }).start();
    }

    private File uniqueDest(File dir, String name) {
        File dest = new File(dir, name);
        int copy = 1;
        while (dest.exists()) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            dest = new File(dir, base + " (" + (++copy) + ")" + ext);
        }
        return dest;
    }

    // ------------------------------------------------------------ type helpers

    private ItemState findItem(Uri uri) {
        for (ItemState item : items) {
            if (uri.equals(item.uri)) return item;
        }
        return null;
    }

    private static boolean isImage(String mime, String ext) {
        if (mime != null && mime.toLowerCase(Locale.US).startsWith("image/")) return true;
        return ext != null && IMAGE_EXTS.contains(ext);
    }

    private static boolean isMedia(String mime, String ext) {
        if (mime != null) {
            String m = mime.toLowerCase(Locale.US);
            if (m.startsWith("video/") || m.startsWith("audio/")) return true;
            if (m.equals("application/ogg")) return true;
        }
        return ext != null && MEDIA_EXTS.contains(ext);
    }

    private static boolean isArchive(String mime, String ext) {
        if (mime != null && ARCHIVE_MIMES.contains(mime.toLowerCase(Locale.US))) return true;
        return ext != null && ARCHIVE_EXTS.contains(ext);
    }

    private static boolean isTextLike(String mime, String ext) {
        if (mime != null) {
            String m = mime.toLowerCase(Locale.US);
            if (m.startsWith("text/")) return true;
            if (m.equals("application/json") || m.equals("application/xml")
                    || m.equals("application/javascript") || m.equals("application/x-sh")
                    || m.equals("application/yaml") || m.endsWith("+xml")
                    || m.endsWith("+json")) return true;
            if (m.startsWith("image/") || m.startsWith("video/") || m.startsWith("audio/")) {
                return false;
            }
            // Unknown application/*: fall through to the extension check.
        }
        if (ext != null && !ext.isEmpty()) {
            if (TEXT_EXTS.contains(ext)) return true;
            if (IMAGE_EXTS.contains(ext)) return false;
            return true;
        }
        return true;
    }

    private static String extensionOf(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        int q = ext.indexOf('?');
        if (q >= 0) ext = ext.substring(0, q);
        return ext.isEmpty() ? null : ext;
    }

    private String guessName(Uri uri) {
        try {
            String last = uri.getLastPathSegment();
            if (last != null && !last.isEmpty()) {
                int slash = last.lastIndexOf('/');
                return slash >= 0 ? last.substring(slash + 1) : last;
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.shared_entry_unknown);
    }

    private String displayName(Uri uri, int fallback) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String display = cursor.getString(idx);
                    if (display != null && !display.isEmpty()) return display;
                }
            }
        } catch (Exception ignored) {
        }
        ItemState match = findItem(uri);
        if (match != null && match.name != null && !match.name.isEmpty()) return match.name;
        String last = uri.getLastPathSegment();
        if (last != null && !last.isEmpty()) return last;
        return "shared_file_" + fallback;
    }

    private String guessMimeFromName(String name) {
        try {
            String ext = extensionOf(name);
            if (ext != null) {
                String mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext);
                if (mime != null) return mime;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int iconFor(String mime, String name) {
        try {
            String ext = extensionOf(name);
            if (isImage(mime, ext)) return R.drawable.image_24px;
            if (mime != null) {
                String m = mime.toLowerCase(Locale.US);
                if (m.startsWith("video/")) return R.drawable.video_24px;
                if (m.startsWith("audio/")) return R.drawable.music_24px;
                if (m.equals("application/pdf")) return R.drawable.pdf_24px;
                if (m.startsWith("text/")) return R.drawable.ic_edit_mt;
            }
        } catch (Exception ignored) {
        }
        return R.drawable.baseline_insert_drive_file_24;
    }
}
