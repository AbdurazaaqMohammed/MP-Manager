package io.github.abdurazaaqmohammed.packs.network;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildQrScan().
 *
 * <p>The scan result returns to the host activity and is forwarded here
 * via {@link #onActivityResult(int, int, Intent)}. Camera grants cannot
 * auto-retry from a pack (host owns the callback), so the user taps Scan
 * again after granting.
 */
public class QrScanTool extends BaseToolPlugin {

    private TextView qrScanOutput;

    public QrScanTool() {
        super("qrscan", "QR Scanner", "Scan barcodes with camera", ToolCategories.NETWORK);
    }

    private void startQrScan(Activity activity) {
        try {
            IntentIntegrator integrator = new IntentIntegrator(activity);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Scan a QR code");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        } catch (Exception e) {
            ToolViewFactory.toast(activity, "Scanner failed: " + e.getMessage());
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("network", R.string.s_qr_scanner, "QR Scanner"));
        MaterialButton scanBtn = ToolViewFactory.makeButton(box, PackRes.str("network", R.string.s_scan_with_camera, "Scan with camera"));
        scanBtn.setOnClickListener(v -> {
            try {
                Activity activity = (Activity) context;
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 9005);
                    ToolViewFactory.toast(context, PackRes.str("network", R.string.s_camera_permission_needed_then_tap_scan, "Camera permission needed, then tap Scan"));
                    return;
                }
                startQrScan(activity);
            } catch (Exception e) {
                ToolViewFactory.toast(context, "Scanner failed: " + e.getMessage());
            }
        });
        qrScanOutput = ToolViewFactory.makeOutput(box);
        qrScanOutput.setText(PackRes.str("network", R.string.s_no_scan_yet, "No scan yet"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, PackRes.str("network", R.string.s_copy, "Copy"), 1f);
        MaterialButton shareBtn = ToolViewFactory.makeRowButton(row, PackRes.str("network", R.string.s_share, "Share"), 1f);
        copyBtn.setOnClickListener(v -> {
            if (qrScanOutput != null) {
                ToolViewFactory.copyText(context, PackRes.str("network", R.string.s_qr, "qr"), qrScanOutput.getText().toString());
            }
        });
        shareBtn.setOnClickListener(v -> {
            if (qrScanOutput == null) return;
            String text = qrScanOutput.getText().toString();
            if (text.isEmpty()) {
                ToolViewFactory.toast(context, PackRes.str("network", R.string.s_nothing_to_share, "Nothing to share"));
                return;
            }
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            context.startActivity(Intent.createChooser(share, "Share scan"));
        });
        // Manual entry fallback when the camera flow is unavailable.
        EditText manual = ToolViewFactory.makeInput(box, PackRes.str("network", R.string.s_or_paste_code_text, "Or paste code text"), InputType.TYPE_CLASS_TEXT);
        MaterialButton manualBtn = ToolViewFactory.makeButton(box, PackRes.str("network", R.string.s_use_pasted_text, "Use pasted text"));
        manualBtn.setOnClickListener(v -> {
            if (qrScanOutput != null) {
                qrScanOutput.setText(manual.getText().toString());
            }
        });
        return box;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (scanResult != null && scanResult.getContents() != null && qrScanOutput != null) {
                qrScanOutput.setText(scanResult.getContents());
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        qrScanOutput = null;
    }
}
