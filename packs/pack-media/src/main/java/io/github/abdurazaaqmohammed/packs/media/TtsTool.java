package io.github.abdurazaaqmohammed.packs.media;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildTts().
 *
 * <p>"Locate file" became dependency-free "Open file" like the other ports.
 */
public class TtsTool extends BaseToolPlugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech ttsEngine;
    private File ttsLastFile;

    public TtsTool() {
        super("tts", "Speak Text", "Speak typed text aloud", ToolCategories.MEDIA);
    }

    private void shareFile(Context context, File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType(mime);
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(s, "Share"));
        } catch (Exception e) {
            ToolViewFactory.toast(context, PackRes.str("media", R.string.s_share_failed, "Share failed"));
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("media", R.string.s_speak_text, "Speak Text"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("media", R.string.s_text_to_speak, "Text to speak"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setText(PackRes.str("media", R.string.s_hello_from_tools_kit, "Hello from Tools Kit"));
        TextView pitchLabel = ToolViewFactory.addLabel(box, PackRes.str("media", R.string.s_pitch_1_0, "Pitch: 1.0"));
        SeekBar pitchBar = new SeekBar(context);
        pitchBar.setMax(150);
        pitchBar.setProgress(50);
        box.addView(pitchBar);
        TextView rateLabel = ToolViewFactory.addLabel(box, PackRes.str("media", R.string.s_speed_1_0, "Speed: 1.0"));
        SeekBar rateBar = new SeekBar(context);
        rateBar.setMax(150);
        rateBar.setProgress(50);
        box.addView(rateBar);
        final float[] pitch = new float[]{1.0f};
        final float[] rate = new float[]{1.0f};
        final TextView status = ToolViewFactory.makeOutput(box);
        status.setText(PackRes.str("media", R.string.s_engine_starting, "Engine starting..."));
        try {
            ttsEngine = new TextToSpeech(context.getApplicationContext(), code -> {
                try {
                    if (code == TextToSpeech.SUCCESS) {
                        ttsEngine.setLanguage(Locale.US);
                        status.setText(PackRes.str("media", R.string.s_ready, "Ready"));
                    } else {
                        status.setText(PackRes.str("media", R.string.s_engine_failed, "Engine failed"));
                    }
                } catch (Exception e) {
                    status.setText(PackRes.str("media", R.string.s_engine_failed, "Engine failed"));
                }
            });
        } catch (Exception e) {
            status.setText(PackRes.str("media", R.string.s_engine_failed, "Engine failed"));
        }
        pitchBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                pitch[0] = 0.5f + progress / 100f;
                pitchLabel.setText("Pitch: " + new DecimalFormat("0.0").format(pitch[0]));
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        rateBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                rate[0] = 0.5f + progress / 100f;
                rateLabel.setText("Speed: " + new DecimalFormat("0.0").format(rate[0]));
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton speakBtn = ToolViewFactory.makeRowButton(row, PackRes.str("media", R.string.s_speak, "Speak"), 1f);
        MaterialButton stopBtn = ToolViewFactory.makeRowButton(row, PackRes.str("media", R.string.s_stop, "Stop"), 1f);
        speakBtn.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_enter_text_first, "Enter text first"));
                return;
            }
            if (ttsEngine == null) {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_engine_not_ready, "Engine not ready"));
                return;
            }
            try {
                ttsEngine.setPitch(pitch[0]);
                ttsEngine.setSpeechRate(rate[0]);
                if (Build.VERSION.SDK_INT >= 21) {
                    ttsEngine.speak(t, TextToSpeech.QUEUE_FLUSH, null, "tools-tts");
                } else {
                    ttsEngine.speak(t, TextToSpeech.QUEUE_FLUSH, null);
                }
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_speak_failed, "Speak failed"));
            }
        });
        stopBtn.setOnClickListener(v -> {
            try {
                if (ttsEngine != null) {
                    ttsEngine.stop();
                }
            } catch (Exception ignored) {
            }
        });
        LinearLayout ttsRow2 = ToolViewFactory.makeRow(box);
        MaterialButton saveAudioBtn = ToolViewFactory.makeRowButton(ttsRow2, PackRes.str("media", R.string.s_save_audio, "Save audio"), 1f);
        MaterialButton shareAudioBtn = ToolViewFactory.makeRowButton(ttsRow2, PackRes.str("media", R.string.s_share_audio, "Share audio"), 1f);
        MaterialButton openAudioBtn = ToolViewFactory.makeRowButton(ttsRow2, PackRes.str("media", R.string.s_open_file, "Open file"), 1f);
        saveAudioBtn.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_enter_text_first, "Enter text first"));
                return;
            }
            if (ttsEngine == null) {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_engine_not_ready, "Engine not ready"));
                return;
            }
            try {
                File dir = new File(Environment.getExternalStorageDirectory(), "Speech");
                dir.mkdirs();
                final File out = new File(dir, "speech_" + System.currentTimeMillis() + ".wav");
                ttsEngine.setPitch(pitch[0]);
                ttsEngine.setSpeechRate(rate[0]);
                int rc;
                if (Build.VERSION.SDK_INT >= 21) {
                    rc = ttsEngine.synthesizeToFile(t, null, out, "tools-tts-file");
                } else {
                    rc = ttsEngine.synthesizeToFile(t, null, out.getAbsolutePath());
                }
                if (rc != TextToSpeech.SUCCESS) {
                    ToolViewFactory.toast(context, PackRes.str("media", R.string.s_save_failed, "Save failed"));
                    return;
                }
                status.setText("Saving " + out.getName());
                handler.postDelayed(new Runnable() {
                    int tries = 0;
                    public void run() {
                        tries++;
                        if (out.exists() && out.length() > 0) {
                            ttsLastFile = out;
                            status.setText("Saved " + out.getName());
                            ToolViewFactory.toast(context, PackRes.str("media", R.string.s_audio_saved, "Audio saved"));
                        } else if (tries < 40) {
                            handler.postDelayed(this, 500);
                        } else {
                            status.setText(PackRes.str("media", R.string.s_save_timed_out, "Save timed out"));
                        }
                    }
                }, 500);
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_save_failed, "Save failed"));
            }
        });
        shareAudioBtn.setOnClickListener(v -> {
            if (ttsLastFile != null && ttsLastFile.exists()) shareFile(context, ttsLastFile, "audio/*");
            else ToolViewFactory.toast(context, PackRes.str("media", R.string.s_save_audio_first, "Save audio first"));
        });
        openAudioBtn.setOnClickListener(v -> {
            if (ttsLastFile != null && ttsLastFile.exists()) {
                try {
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", ttsLastFile);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "audio/*");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(i, "Open audio"));
                } catch (Exception e) {
                    ToolViewFactory.toast(context, PackRes.str("media", R.string.s_open_failed, "Open failed"));
                }
            } else {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_save_audio_first, "Save audio first"));
            }
        });
        return box;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            if (ttsEngine != null) {
                ttsEngine.stop();
                ttsEngine.shutdown();
            }
        } catch (Exception ignored) {
        }
        ttsEngine = null;
        ttsLastFile = null;
    }
}
