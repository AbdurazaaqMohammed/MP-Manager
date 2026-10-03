package io.github.abdurazaaqmohammed.packs.network;

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
        super("qrscan", R.string.qrscan_title, R.string.qrscan_sub,  ToolCategories.NETWORK);
    }

    private void startQrScan(Activity activity) {
        try {
            IntentIntegrator integrator = new IntentIntegrator(activity);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt(activity.getString(R.string.qrscan_scan_a_qr_code));
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.initiateScan();
        } catch (Exception e) {
            ToolViewFactory.toast(activity, activity.getString(R.string.qrscan_scanner_failed) + e.getMessage());
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.qrscan_qr_scanner));
        MaterialButton scanBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.qrscan_scan_with_camera));
        scanBtn.setOnClickListener(v -> {
            try {
                Activity activity = (Activity) context;
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, 9005);
                    ToolViewFactory.toast(context, context.getString(R.string.qrscan_camera_permission_needed_then_));
                    return;
                }
                startQrScan(activity);
            } catch (Exception e) {
                ToolViewFactory.toast(context, context.getString(R.string.qrscan_scanner_failed) + e.getMessage());
            }
        });
        qrScanOutput = ToolViewFactory.makeOutput(box);
        qrScanOutput.setText(qrScanOutput.getContext().getString(R.string.qrscan_no_scan_yet));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.connectivity_copy), 1f);
        MaterialButton shareBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.qrgen_share), 1f);
        copyBtn.setOnClickListener(v -> {
            if (qrScanOutput != null) {
                ToolViewFactory.copyText(context, context.getString(R.string.qrgen_qr), qrScanOutput.getText().toString());
            }
        });
        shareBtn.setOnClickListener(v -> {
            if (qrScanOutput == null) return;
            String text = qrScanOutput.getText().toString();
            if (text.isEmpty()) {
                ToolViewFactory.toast(context, context.getString(R.string.qrscan_nothing_to_share));
                return;
            }
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            context.startActivity(Intent.createChooser(share, context.getString(R.string.qrscan_share_scan)));
        });
        // Manual entry fallback when the camera flow is unavailable.
        EditText manual = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.qrscan_or_paste_code_text), InputType.TYPE_CLASS_TEXT);
        MaterialButton manualBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.qrscan_use_pasted_text));
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
