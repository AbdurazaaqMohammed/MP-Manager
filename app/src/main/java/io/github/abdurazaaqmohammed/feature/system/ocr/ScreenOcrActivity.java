package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Patterns;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.system.ToolRegistry;
import io.github.abdurazaaqmohammed.feature.system.ToolShortcuts;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrResult;
import io.github.abdurazaaqmohammed.plugins.packs.PackCatalog;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.abdurazaaqmohammed.plugins.packs.PackManager;
import io.github.abdurazaaqmohammed.plugins.packs.PackPrompts;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;

/** Capture/setup UI stays in the small host APK; all OCR inference lives in the optional pack. */
public final class ScreenOcrActivity extends BaseActivity {
    public static final String EXTRA_CAPTURE = "ocr_capture_ready";
    private final ExecutorService imageWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "ocr-image-import"));
    private LinearLayout content;
    private OcrSelectionView preview;
    private TextView status;
    private MaterialButton recognizeButton, copyButton, shareButton, startButton, downloadButton;
    private TextInputEditText resultText;
    private ProgressBar progress;
    private View cropActions, resultActions;
    private ChipGroup smartActions;
    private Bitmap displayedImage;
    private OcrResult displayedResult;
    private boolean loadingImage;
    private boolean askingOverlay;
    private boolean projectionRequested;
    private OcrModels.Language language;
    private final OcrSession.Listener sessionListener = this::renderSession;
    private final OcrModelDownloads.Listener downloadListener = this::updateDownloads;

    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importImage(uri); });
    private final ActivityResultLauncher<Intent> overlayPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                askingOverlay = false;
                if (Settings.canDrawOverlays(this)) requestNotificationThenProjection();
            });
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> requestProjection());
    private final ActivityResultLauncher<Intent> projectionPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                projectionRequested = false;
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    Toast.makeText(this, R.string.ocr_capture_cancelled, Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    Intent service = new Intent(this, ScreenOcrService.class).setAction(ScreenOcrService.ACTION_START)
                            .putExtra(ScreenOcrService.EXTRA_CODE, result.getResultCode())
                            .putExtra(ScreenOcrService.EXTRA_DATA, result.getData());
                    ContextCompat.startForegroundService(this, service);
                    // Let the user choose any app; recognition waits for their bubble tap.
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                } catch (Exception error) {
                    Toast.makeText(this, getString(R.string.ocr_capture_start_failed, error.getMessage()), Toast.LENGTH_LONG).show();
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_screen_ocr);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE); // OCR results can contain sensitive text.
        MaterialToolbar toolbar = findViewById(R.id.ocr_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add(0, 1, 0, R.string.plugin_create_shortcut);
        toolbar.getMenu().add(0, 2, 1, R.string.ocr_licenses);
        toolbar.getMenu().add(0, 3, 2, R.string.ocr_remove_model);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) ToolShortcuts.pin(this, ToolRegistry.findById(this, "screen_ocr"));
            else if (item.getItemId() == 2) showLicenses();
            else removeModel();
            return true;
        });
        content = findViewById(R.id.ocr_content);
        language = OcrModels.selected(this);
        if (state != null) {
            askingOverlay = state.getBoolean("asking_overlay");
            projectionRequested = state.getBoolean("projection_requested");
        }
        renderSession();
    }

    @Override protected void onStart() {
        super.onStart();
        ScreenOcrService.setUiVisible(true);
        OcrSession.keepEngineWarm();
        OcrSession.observe(sessionListener);
        OcrModelDownloads.observe(downloadListener);
    }

    @Override protected void onResume() {
        super.onResume();
        if (OcrSession.image() == null) showHome();
        getIntent().removeExtra(EXTRA_CAPTURE);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        renderSession();
    }

    @Override protected void onStop() {
        OcrSession.removeObserver(sessionListener);
        OcrModelDownloads.removeObserver(downloadListener);
        ScreenOcrService.setUiVisible(false);
        OcrSession.scheduleIdleRelease();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("asking_overlay", askingOverlay);
        state.putBoolean("projection_requested", projectionRequested);
        // Do not persist images, OCR text or the projection permission token.
        super.onSaveInstanceState(state);
    }

    private void showHome() {
        if (content == null) return;
        content.removeAllViews();
        displayedImage = null;
        displayedResult = null;
        preview = null;
        status = null;
        progress = null;
        downloadButton = null;
        startButton = null;
        recognizeButton = null;
        copyButton = null;
        shareButton = null;
        resultText = null;
        cropActions = null;
        resultActions = null;
        smartActions = null;
        language = OcrModels.selected(this);
        addText(content, R.string.ocr_intro, 20);
        addText(content, R.string.ocr_privacy, 14);
        if (Build.VERSION.SDK_INT < 24) {
            addText(content, R.string.ocr_android_required, 16);
            return;
        }
        if (!PackManager.isInstalled(this, "ocr")) {
            LinearLayout runtime = card(content);
            addText(runtime, R.string.ocr_runtime_title, 18);
            addText(runtime, R.string.ocr_runtime_description, 14);
            PackDescriptor pack = PackCatalog.byId(PackCatalog.load(this), "ocr");
            if (pack == null || !pack.hasChecksum()) {
                addText(runtime, R.string.ocr_runtime_unpublished, 14);
            } else {
                MaterialButton install = button(runtime, R.string.ocr_download_runtime, false);
                install.setEnabled(!PackPrompts.isDownloading(pack.id));
                install.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.ocr_runtime_title).setMessage(R.string.ocr_runtime_description)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.ocr_download_runtime,
                                (dialog, which) -> {
                                    install.setEnabled(false);
                                    PackPrompts.downloadPack(this, pack, () -> {
                                        if (!isFinishing() && !isDestroyed()) showHome();
                                    }, install);
                                }).show());
            }
            return;
        }
        LinearLayout models = card(content);
        TextInputLayout dropdown = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        dropdown.setHint(getString(R.string.ocr_language));
        MaterialAutoCompleteTextView field = new MaterialAutoCompleteTextView(dropdown.getContext());
        field.setInputType(InputType.TYPE_NULL);
        List<String> labels = new ArrayList<>();
        for (OcrModels.Language item : OcrModels.LANGUAGES) labels.add(getString(item.label()));
        field.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        field.setText(getString(language.label()), false);
        field.setOnItemClickListener((parent, view, position, id) -> {
            language = OcrModels.LANGUAGES.get(position);
            OcrModels.select(this, language);
            OcrSession.releaseEngine();
            showHome();
        });
        dropdown.addView(field, new LinearLayout.LayoutParams(-1, -2));
        models.addView(dropdown, new LinearLayout.LayoutParams(-1, -2));
        status = addText(models, OcrModels.ready(this, language) ? R.string.ocr_model_ready : R.string.ocr_model_missing, 14);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        models.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        downloadButton = button(models, R.string.ocr_download_model, true);
        downloadButton.setOnClickListener(v -> {
            if (OcrModelDownloads.state().running()) OcrModelDownloads.cancel();
            else new MaterialAlertDialogBuilder(this).setTitle(R.string.ocr_download_model)
                    .setMessage(getString(R.string.ocr_download_confirm, getString(language.label())))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.ocr_download_model,
                            (dialog, which) -> OcrModelDownloads.download(this, language)).show();
        });
        startButton = button(content, ScreenOcrService.isRunning() ? R.string.ocr_stop : R.string.ocr_start, false);
        startButton.setIconResource(R.drawable.screen_ocr_24px);
        startButton.setOnClickListener(v -> {
            if (ScreenOcrService.isRunning()) {
                ScreenOcrService.stop(this);
                startButton.setText(R.string.ocr_start);
            } else startCapture();
        });
        MaterialButton importButton = button(content, R.string.ocr_import, true);
        importButton.setEnabled(OcrModels.ready(this, language));
        importButton.setOnClickListener(v -> imagePicker.launch(new String[]{"image/*"}));
        addText(content, R.string.ocr_capture_help, 14);
        updateDownloads(OcrModelDownloads.state());
    }

    private void updateDownloads(OcrModelDownloads.State state) {
        if (displayedImage != null || status == null || progress == null || downloadButton == null || isDestroyed()) return;
        boolean ready = OcrModels.ready(this, language);
        progress.setVisibility(state.running() ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(state.total() <= 0);
        if (state.total() > 0) progress.setProgress((int) Math.min(100, state.bytes() * 100 / state.total()));
        downloadButton.setVisibility(ready && !state.running() ? View.GONE : View.VISIBLE);
        downloadButton.setText(state.running() ? android.R.string.cancel : R.string.ocr_download_model);
        if (state.running()) status.setText(getString(R.string.ocr_download_progress,
                Formatter.formatShortFileSize(this, state.bytes())));
        else if (state.error() != null && state.language().equals(language.id())) {
            status.setText(getString(R.string.ocr_download_error, state.error()));
        } else status.setText(ready ? R.string.ocr_model_ready : R.string.ocr_model_missing);
        if (startButton != null) startButton.setEnabled(ready || ScreenOcrService.isRunning());
        // The model download callback can enable import without rebuilding the dropdown.
        View imported = content.findViewWithTag("ocr_import_button");
        if (imported != null) imported.setEnabled(ready);
    }

    private void startCapture() {
        if (askingOverlay || projectionRequested || !OcrModels.ready(this, language)) return;
        if (!Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.ocr_overlay_title)
                    .setMessage(R.string.ocr_overlay_explanation).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.ocr_continue, (dialog, which) -> {
                        askingOverlay = true;
                        overlayPermission.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    }).show();
            return;
        }
        requestNotificationThenProjection();
    }

    private void requestNotificationThenProjection() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        else requestProjection();
    }

    private void requestProjection() {
        if (projectionRequested || !Settings.canDrawOverlays(this) || isFinishing()) return;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.ocr_projection_title)
                .setMessage(R.string.ocr_projection_explanation).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ocr_continue, (dialog, which) -> {
                    MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    projectionRequested = true;
                    projectionPermission.launch(manager.createScreenCaptureIntent());
                }).show();
    }

    private void importImage(Uri uri) {
        if (loadingImage) return;
        loadingImage = true;
        imageWorker.execute(() -> {
            try {
                Bitmap bitmap = decodeImage(uri);
                runOnUiThread(() -> {
                    loadingImage = false;
                    if (isFinishing() || isDestroyed()) { bitmap.recycle(); return; }
                    OcrSession.setImage(bitmap);
                });
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    loadingImage = false;
                    if (!isFinishing() && !isDestroyed()) Toast.makeText(this,
                            getString(R.string.ocr_image_error, error.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Bitmap decodeImage(Uri uri) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, options); }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IllegalArgumentException("Unsupported image");
        options.inSampleSize = 1;
        while ((long) (options.outWidth / options.inSampleSize) * (options.outHeight / options.inSampleSize) > 12_000_000
                || Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 4096) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream input = getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
        if (bitmap == null) throw new IllegalArgumentException("Unsupported image");
        Matrix matrix = new Matrix();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(input);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
            }
        } catch (Exception ignored) { /* Screenshots and other formats may have no EXIF. */ }
        if (!matrix.isIdentity()) {
            Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (oriented != bitmap) bitmap.recycle();
            bitmap = oriented;
        }
        if (bitmap.hasAlpha()) {
            Bitmap flattened = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(flattened);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle();
            bitmap = flattened;
        }
        return bitmap;
    }

    private void renderSession() {
        if (content == null || isFinishing() || isDestroyed()) return;
        Bitmap image = OcrSession.image();
        if (image == null) {
            if (displayedImage != null || content.getChildCount() == 0) showHome();
            return;
        }
        if (image != displayedImage) showWorkspace(image);
        boolean busy = OcrSession.busy();
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        recognizeButton.setEnabled(!busy);
        OcrResult result = OcrSession.result();
        if (result != displayedResult) {
            displayedResult = result;
            preview.setResult(result);
            if (result != null) resultText.setText(result.text());
            if (result != null) buildSmartActions(result.text());
        }
        cropActions.setVisibility(result == null ? View.VISIBLE : View.GONE);
        resultActions.setVisibility(result == null ? View.GONE : View.VISIBLE);
        resultText.setVisibility(result == null ? View.GONE : View.VISIBLE);
        if (busy) status.setText(R.string.ocr_recognizing);
        else if (OcrSession.error() != null) status.setText(getString(R.string.ocr_engine_error, OcrSession.error()));
        else if (result == null) status.setText(R.string.ocr_select_region);
        else if (result.lines.isEmpty()) status.setText(R.string.ocr_no_text);
        else status.setText(getString(R.string.ocr_result_summary, result.lines.size(), result.elapsedMillis)
                    + "\n" + getString(R.string.ocr_selection_help));
        copyButton.setEnabled(result != null && resultText.length() > 0);
        shareButton.setEnabled(result != null && resultText.length() > 0);
        View cancel = content.findViewWithTag("ocr_cancel_reading");
        if (cancel != null) cancel.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showWorkspace(Bitmap image) {
        content.removeAllViews();
        displayedImage = image;
        displayedResult = null;
        status = addText(content, R.string.ocr_select_region, 14);
        preview = new OcrSelectionView(this);
        preview.setImage(image);
        preview.setListener((text, selected) -> {
            OcrResult result = OcrSession.result();
            resultText.setText(selected ? text : result == null ? "" : result.text());
            copyButton.setText(selected ? R.string.ocr_copy_selected : R.string.ocr_copy);
        });
        int previewHeight = Math.max(dp(240), Math.min(dp(460), getResources().getDisplayMetrics().heightPixels / 2));
        content.addView(preview, new LinearLayout.LayoutParams(-1, previewHeight));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        MaterialButton cancel = button(content, android.R.string.cancel, true);
        cancel.setTag("ocr_cancel_reading");
        cancel.setOnClickListener(v -> OcrSession.cancelRecognition());
        LinearLayout cropRow = row(content);
        cropActions = cropRow;
        recognizeButton = rowButton(cropRow, R.string.ocr_recognize);
        recognizeButton.setOnClickListener(v -> OcrSession.recognize(this, preview.region()));
        MaterialButton full = rowButton(cropRow, R.string.ocr_full_image);
        full.setOnClickListener(v -> { preview.fullImage(); OcrSession.recognize(this, preview.region()); });
        LinearLayout resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultActions = resultBox;
        content.addView(resultBox);
        LinearLayout selectionRow = row(resultBox);
        rowButton(selectionRow, R.string.ocr_select_all).setOnClickListener(v -> preview.selectAll());
        rowButton(selectionRow, R.string.ocr_clear_selection).setOnClickListener(v -> preview.clearSelection());
        TextInputLayout input = new TextInputLayout(this);
        input.setHint(getString(R.string.ocr_result_hint));
        input.setSaveEnabled(false);
        resultText = new TextInputEditText(input.getContext());
        resultText.setId(R.id.ocr_result_text);
        resultText.setSaveEnabled(false);
        resultText.setFreezesText(false);
        resultText.setMinLines(3);
        resultText.setGravity(Gravity.TOP | Gravity.START);
        resultText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (Build.VERSION.SDK_INT >= 26) resultText.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.addView(resultText, new LinearLayout.LayoutParams(-1, -2));
        resultBox.addView(input, new LinearLayout.LayoutParams(-1, -2));
        smartActions = new ChipGroup(this);
        resultBox.addView(smartActions, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout copyRow = row(resultBox);
        copyButton = rowButton(copyRow, R.string.ocr_copy);
        copyButton.setOnClickListener(v -> copyText());
        shareButton = rowButton(copyRow, R.string.ocr_share);
        shareButton.setOnClickListener(v -> shareText());
        resultText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                copyButton.setEnabled(text.length() > 0);
                shareButton.setEnabled(text.length() > 0);
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        button(resultBox, R.string.ocr_new_region, true).setOnClickListener(v -> {
            displayedImage = null;
            OcrSession.setImage(image);
        });
        button(content, R.string.close, true).setOnClickListener(v -> {
            closeResult();
        });
    }

    private void copyText() {
        if (resultText.getText() == null || resultText.length() == 0) return;
        ClipData clip = ClipData.newPlainText(getString(R.string.screen_ocr_title), resultText.getText().toString());
        if (Build.VERSION.SDK_INT >= 33) {
            PersistableBundle sensitive = new PersistableBundle();
            sensitive.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(sensitive);
        }
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void shareText() {
        if (resultText.getText() == null || resultText.length() == 0) return;
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, resultText.getText().toString()), getString(R.string.ocr_share)));
    }

    /** Useful actions are suggestions only; recognized URLs are never opened automatically. */
    private void buildSmartActions(String text) {
        smartActions.removeAllViews();
        Set<String> values = new LinkedHashSet<>();
        Matcher websites = Patterns.WEB_URL.matcher(text);
        while (websites.find() && values.size() < 3) {
            String value = websites.group();
            String url = value.startsWith("http://") || value.startsWith("https://") ? value : "https://" + value;
            if (values.add(url)) smartAction(R.string.ocr_open_link, value, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
        Matcher email = Patterns.EMAIL_ADDRESS.matcher(text);
        if (email.find()) smartAction(R.string.ocr_send_email, email.group(), new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", email.group(), null)));
        Matcher phone = Patterns.PHONE.matcher(text);
        while (phone.find()) {
            String value = phone.group();
            if (value.replaceAll("\\D", "").length() < 7) continue;
            smartAction(R.string.ocr_dial_number, value, new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null)));
            break;
        }
    }

    private void smartAction(int label, String value, Intent intent) {
        Chip chip = new Chip(this);
        chip.setText(getString(label, value));
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setOnClickListener(v -> {
            try { startActivity(intent); }
            catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, R.string.ocr_no_action_app, Toast.LENGTH_SHORT).show();
            }
        });
        smartActions.addView(chip);
    }

    private void removeModel() {
        if (OcrSession.busy() || OcrModelDownloads.state().running() || ScreenOcrService.isRunning()) return;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.ocr_remove_model)
                .setMessage(R.string.ocr_remove_model_confirm).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.tools_hub_remove, (dialog, which) -> {
                    OcrSession.clear();
                    OcrModels.file(this, OcrModels.selected(this).model()).delete();
                    showHome();
                }).show();
    }

    private void showLicenses() {
        StringBuilder text = new StringBuilder(getString(R.string.ocr_licenses_description));
        try (InputStream input = getAssets().open("Apache-2.0.txt")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
            text.append("\n\n").append(bytes.toString("UTF-8"));
        } catch (Exception ignored) {}
        new MaterialAlertDialogBuilder(this).setTitle(R.string.ocr_licenses)
                .setMessage(text).setPositiveButton(android.R.string.ok, null).show();
    }

    @Override public void onBackPressed() {
        if (OcrSession.busy()) { OcrSession.cancelRecognition(); return; }
        if (OcrSession.image() != null) { closeResult(); return; }
        super.onBackPressed();
    }

    private void closeResult() {
        if (ScreenOcrService.isRunning()) {
            OcrSession.clearImage();
            moveTaskToBack(true); // Return to the app the user was reading.
        } else OcrSession.clear();
    }

    @Override protected void onDestroy() {
        OcrSession.removeObserver(sessionListener);
        OcrModelDownloads.removeObserver(downloadListener);
        imageWorker.shutdownNow();
        if (isFinishing()) OcrSession.clear();
        super.onDestroy();
    }

    private TextView addText(LinearLayout parent, int resource, int size) {
        TextView text = new TextView(this);
        text.setText(resource);
        text.setTextSize(size);
        text.setTextColor(MaterialColors.getColor(this, size >= 18 ? com.google.android.material.R.attr.colorOnSurface
                : com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(text, params);
        return text;
    }

    private LinearLayout card(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, 0));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(box, new ViewGroup.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8); params.bottomMargin = dp(16);
        parent.addView(card, params);
        return box;
    }

    private MaterialButton button(LinearLayout parent, int text, boolean outlined) {
        MaterialButton button = new MaterialButton(this, null, outlined
                ? com.google.android.material.R.attr.materialButtonOutlinedStyle : com.google.android.material.R.attr.materialButtonStyle);
        button.setText(text);
        if (text == R.string.ocr_import) button.setTag("ocr_import_button");
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private MaterialButton rowButton(LinearLayout parent, int text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginEnd(dp(4));
        parent.addView(button, params);
        return button;
    }

    private int dp(int value) { return dpPx(value); }
}
