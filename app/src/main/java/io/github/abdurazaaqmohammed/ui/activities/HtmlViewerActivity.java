package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Built-in HTML preview backed by the system {@link WebView} (no external engine).
 * <p>
 * SECURITY: the document is untrusted content. The WebView is hardened as much as
 * possible — JavaScript, cookies, storage, geolocation, content access and
 * cross-origin file access are all off, external links leave the app, and a
 * prominent warning must be acknowledged before every file is rendered. Still,
 * a vulnerable renderer can be exploited by a malicious page, especially on
 * older Android versions whose system WebView no longer receives security
 * updates: the renderer itself is the attack surface and no setting can fix a
 * hole inside it. File access is enabled only for the render because local
 * pages load sibling images/css/frames via file:// URLs, which is the one
 * concession needed to "load everything properly".
 */
public class HtmlViewerActivity extends BaseActivity {

    /** Absolute path of the (already staged/readable) HTML file. */
    public static final String EXTRA_PATH = "htmlPath";
    /** Name to display in the title bar. */
    public static final String EXTRA_NAME = "htmlName";

    private static final int MENU_REFRESH = 1;
    private static final int MENU_SOURCE = 2;
    private static final int MENU_OPEN_TEXT = 3;
    private static final int MENU_OPEN_EXTERNAL = 4;
    private static final int SOURCE_PREVIEW_LIMIT = 2 * 1024 * 1024;

    private WebView webView;
    private ProgressBar progressBar;
    private File file;
    private String displayName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        file = path != null ? new File(path) : null;
        displayName = getIntent().getStringExtra(EXTRA_NAME);
        if (displayName == null || displayName.isEmpty()) {
            displayName = file != null ? file.getName() : "HTML";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(displayName);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpPx(3)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> handleBack());

        configureWebView();

        if (file == null || !file.isFile()) {
            Extensions.showMessage(this, R.string.cannot_open_item);
            finish();
            return;
        }
        showSecurityWarningThenLoad();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        // Least-privileged settings a local document can still render under.
        s.setJavaScriptEnabled(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        // The WebView debug flag follows the app's debuggable flag and is
        // process-global: without this, on a debug build any app sharing the
        // renderer sandbox could attach DevTools to this untrusted page.
        try {
            WebView.setWebContentsDebuggingEnabled(false);
        } catch (Exception ignored) {
        }
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(false);
            cm.setAcceptThirdPartyCookies(webView, false);
        } catch (Exception ignored) {
        }
        webView.setBackgroundColor(Color.WHITE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(LinearLayout.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(LinearLayout.GONE);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Pre-API 24 devices only see this overload.
                return shouldOverrideUrlLoading(view, url, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return shouldOverrideUrlLoading(view, request.getUrl().toString(), request.getUrl());
            }

            private boolean shouldOverrideUrlLoading(WebView view, String url, Uri resolved) {
                // Our document (file://) and WebView-internal schemes stay inside
                // the viewer; anything else (http, mailto, intent:, …) leaves the app.
                if (url.startsWith("file://") || url.startsWith("about:")
                        || url.startsWith("data:") || url.startsWith("blob:")) {
                    return false;
                }
                try {
                    openInExternalApp(resolved != null ? resolved : Uri.parse(url));
                } catch (Exception e) {
                    // Malformed or unroutable target: just block it.
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && !request.isForMainFrame()) return;
                String desc = Build.VERSION.SDK_INT >= 24 ? String.valueOf(error.getDescription()) : "";
                Extensions.showMessage(HtmlViewerActivity.this,
                        getString(R.string.cannot_open_item) + (desc.isEmpty() ? "" : ": " + desc));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) progressBar.setVisibility(LinearLayout.GONE);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                // Console output is attacker-controlled; drop it.
                return true;
            }
        });
    }

    /**
     * Renders from a file:// URL. File access is only switched on for this
     * document's lifetime (see class javadoc) so sibling images/css/frames
     * referenced by the page resolve.
     */
    private void startLoad() {
        File dir = file.getParentFile();
        if (dir == null) dir = new File("/");
        try {
            webView.getSettings().setAllowFileAccess(true);
            webView.loadUrl(encodeFileUri(dir) + "/" + Uri.encode(file.getName()));
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private static String encodeFileUri(File f) {
        return Uri.fromFile(f).toString().replace(" ", "%20");
    }

    private void showSecurityWarningThenLoad() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.html_warning_title)
                .setMessage(R.string.html_warning_message)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setPositiveButton(R.string.html_warn_proceed, (d, w) -> startLoad())
                .show();
    }

    private void handleBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.html_menu_refresh);
        menu.add(Menu.NONE, MENU_SOURCE, Menu.NONE, R.string.html_menu_view_source);
        menu.add(Menu.NONE, MENU_OPEN_TEXT, Menu.NONE, R.string.open_as_text);
        menu.add(Menu.NONE, MENU_OPEN_EXTERNAL, Menu.NONE, R.string.html_menu_open_external);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                startLoad();
                return true;
            case MENU_SOURCE:
                showSourceDialog();
                return true;
            case MENU_OPEN_TEXT:
                startActivity(new Intent(this, TextEditorActivity.class)
                        .putExtra("path", file.getAbsolutePath()));
                return true;
            case MENU_OPEN_EXTERNAL:
                openExternally();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void openExternally() {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    "io.github.abdurazaaqmohammed.MPManager.provider", file);
            openInExternalApp(uri);
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void openInExternalApp(Uri uri) {
        try {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "text/html")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    getString(R.string.open_with)));
        } catch (Exception e) {
            Extensions.showMessage(this, R.string.no_apps_found);
        }
    }

    /**
     * Shows the raw file bytes as text. Reads straight from disk instead of the
     * WebView DOM so it works with JavaScript disabled.
     */
    private void showSourceDialog() {
        new Thread(() -> {
            String text;
            boolean truncated = false;
            try (InputStream is = FileUtils.getInputStream(file)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                int total = 0;
                while ((n = is.read(buf)) != -1) {
                    int keep = Math.min(n, SOURCE_PREVIEW_LIMIT - total);
                    bos.write(buf, 0, keep);
                    total += n;
                    if (total >= SOURCE_PREVIEW_LIMIT) {
                        truncated = true;
                        break;
                    }
                }
                text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
                return;
            }
            if (truncated) text = text + "\n\n[… " + getString(R.string.html_source_truncated) + " …]";
            final String source = text;
            runOnUiThread(() -> {
                TextView tv = new TextView(this);
                tv.setText(source);
                tv.setTextIsSelectable(true);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(12);
                int pad = dpPx(16);
                tv.setPadding(pad, pad, pad, pad);
                ScrollView sv = new ScrollView(this);
                sv.addView(tv);
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.html_menu_view_source) + ": " + displayName)
                        .setView(sv)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
