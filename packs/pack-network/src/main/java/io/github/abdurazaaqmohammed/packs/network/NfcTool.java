package io.github.abdurazaaqmohammed.packs.network;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildNfc().
 *
 * <p>Tag intents arrive at the host activity and are forwarded here via
 * {@link #onNewIntent(Intent)}; dispatch is disabled in onDestroy().
 */
public class NfcTool extends BaseToolPlugin {

    private NfcAdapter nfcAdapter;
    private TextView nfcText;
    private Activity host;

    public NfcTool() {
        super("nfc", "NFC Reader", "Scan NFC tags", ToolCategories.NETWORK);
    }

    private static String decodeNdefText(NdefRecord rec) {
        try {
            if (rec.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(rec.getType(), NdefRecord.RTD_TEXT)) {
                byte[] payload = rec.getPayload();
                boolean utf16 = (payload[0] & 0x80) != 0;
                int langLen = payload[0] & 0x3F;
                return new String(payload, 1 + langLen, payload.length - 1 - langLen, utf16 ? "UTF-16" : "UTF-8");
            } else if (rec.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(rec.getType(), NdefRecord.RTD_URI)) {
                byte[] payload = rec.getPayload();
                return new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void enableDispatch(Activity activity) {
        try {
            if (nfcAdapter == null) {
                nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
            }
            if (nfcAdapter == null) {
                return;
            }
            Intent intent = new Intent(activity, activity.getClass());
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pending = PendingIntent.getActivity(activity, 0, intent, flags);
            IntentFilter[] filters = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)};
            nfcAdapter.enableForegroundDispatch(activity, pending, filters, null);
        } catch (Exception ignored) {
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "NFC Reader");
        nfcText = ToolViewFactory.makeOutput(box);
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
            if (adapter == null) {
                nfcText.setText("No NFC hardware on this device");
                return box;
            }
            if (!adapter.isEnabled()) {
                nfcText.setText("Turn on NFC, then hold a tag to the phone");
            } else {
                nfcText.setText("Hold a tag to the phone");
            }
        } catch (Exception e) {
            nfcText.setText("NFC unavailable");
            return box;
        }
        try {
            host = (Activity) context;
            enableDispatch(host);
        } catch (Exception ignored) {
        }
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, "Copy tag info");
        copyBtn.setOnClickListener(v -> ToolViewFactory.copyText(context, "nfc", nfcText.getText().toString()));
        return box;
    }

    @Override
    public void onNewIntent(Intent intent) {
        try {
            String action = intent.getAction();
            if (!NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) && !NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) && !NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
                return;
            }
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag == null || nfcText == null) {
                return;
            }
            StringBuilder b = new StringBuilder();
            byte[] id = tag.getId();
            StringBuilder hex = new StringBuilder();
            for (byte bb : id) {
                String h = Integer.toHexString(0xFF & bb);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h.toUpperCase(Locale.US));
            }
            b.append("ID ").append(hex).append("\n");
            String[] techs = tag.getTechList();
            b.append("Tech: ");
            for (int i = 0; i < techs.length; i++) {
                if (i > 0) {
                    b.append(", ");
                }
                String t = techs[i];
                int dot = t.lastIndexOf('.');
                b.append(dot >= 0 ? t.substring(dot + 1) : t);
            }
            NdefMessage[] msgs = null;
            try {
                Parcelable[] raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                if (raw != null) {
                    msgs = new NdefMessage[raw.length];
                    for (int i = 0; i < raw.length; i++) {
                        msgs[i] = (NdefMessage) raw[i];
                    }
                }
            } catch (Exception ignored) {
            }
            if (msgs != null) {
                for (NdefMessage msg : msgs) {
                    for (NdefRecord rec : msg.getRecords()) {
                        String payload = decodeNdefText(rec);
                        if (payload != null) {
                            b.append("\n").append(payload);
                        }
                    }
                }
            }
            nfcText.setText(b.toString());
        } catch (Exception e) {
            if (nfcText != null) {
                nfcText.setText("Read failed");
            }
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (nfcAdapter != null && host != null) {
                nfcAdapter.disableForegroundDispatch(host);
            }
        } catch (Exception ignored) {
        }
        nfcAdapter = null;
        nfcText = null;
        host = null;
    }
}
