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
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;

public class LocateActivity extends BaseActivity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);
        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setText(getString(R.string.locate_resolving));
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton cancelBtn = new MaterialButton(this);
        cancelBtn.setText(android.R.string.cancel);
        cancelBtn.setOnClickListener(v -> finish());
        root.addView(cancelBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
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
            runOnUiThread(() -> {
                if (found != null && !found.isEmpty()) {
                    openManager(found);
                } else {
                    String detail = last == null ? "no file shared"
                            : ("not a local file: " + last);
                    statusView.setText(getString(R.string.locate_failed) + "\n" + detail);
                }
            });
        }).start();
    }

    private void openManager(String path) {
        try {
            Intent open = new Intent(this, MainActivity.class);
            open.putExtra("locatePath", path);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(open);
        } catch (Exception e) {
            Log.e("LocateActivity", "open manager failed", e);
            statusView.setText(getString(R.string.locate_failed) + "\n" + e);
            return;
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

    private String resolvePath(Uri uri) {
        if (uri == null) return null;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String p = uri.getPath();
                if (p != null && new File(p).exists()) return p;
            }
        } catch (Exception ignored) {
        }
        try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                String doc = DocumentsContract.getDocumentId(uri);
                String authority = uri.getAuthority();
                if ("com.android.externalstorage.documents".equals(authority) && doc != null) {
                    int colon = doc.indexOf(':');
                    String volume = colon < 0 ? "primary" : doc.substring(0, colon);
                    String rel = colon < 0 ? "" : doc.substring(colon + 1);
                    File base = "primary".equalsIgnoreCase(volume)
                            ? Environment.getExternalStorageDirectory()
                            : new File("/storage/" + volume);
                    File f = new File(base, rel);
                    if (f.exists()) return f.getAbsolutePath();
                } else if ("com.android.providers.downloads.documents".equals(authority)
                        && doc != null) {
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
                } else if ("com.android.providers.media.documents".equals(authority)
                        && doc != null) {
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
                        } else if ("document".equals(type)
                                && Build.VERSION.SDK_INT >= 29) {
                            content = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                        }
                        if (content != null) {
                            String p = queryData(ContentUris.withAppendedId(content,
                                    Long.parseLong(id)));
                            if (p != null) return p;
                        }
                    }
                }
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
        try {
            String last = uri.getLastPathSegment();
            if (last != null && new File(last).exists()) return last;
        } catch (Exception ignored) {
        }
        return null;
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
}
