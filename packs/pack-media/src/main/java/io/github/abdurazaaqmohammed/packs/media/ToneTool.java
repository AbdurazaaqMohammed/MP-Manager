package io.github.abdurazaaqmohammed.packs.media;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Extraction of ToolRunnerActivity.buildTone().
 *
 * <p>The built-in "Locate file" opened the host file manager; packs cannot
 * do that, so it became "Open file" via a system viewer instead.
 */
public class ToneTool extends BaseToolPlugin {

    private AudioTrack toneTrack;
    private Thread toneThread;
    private boolean tonePlaying;
    private Context hostContext;

    public ToneTool() {
        super("tone", "Tone Generator", "Play custom frequencies", ToolCategories.MEDIA);
    }

    private void stopTone() {
        tonePlaying = false;
        try {
            if (toneThread != null) {
                toneThread.interrupt();
            }
        } catch (Exception ignored) {
        }
        toneThread = null;
        try {
            if (toneTrack != null) {
                toneTrack.stop();
                toneTrack.release();
            }
        } catch (Exception ignored) {
        }
        toneTrack = null;
    }

    private void startTone(final int freqHz) {
        try {
            final int sampleRate = 44100;
            int minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) {
                minBuf = sampleRate;
            }
            final AudioTrack track;
            if (Build.VERSION.SDK_INT >= 23) {
                track = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(Math.max(minBuf, sampleRate)).build();
            } else {
                track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, sampleRate), AudioTrack.MODE_STREAM);
            }
            toneTrack = track;
            tonePlaying = true;
            track.play();
            toneThread = new Thread(() -> {
                try {
                    double phase = 0;
                    double step = 2.0 * Math.PI * freqHz / sampleRate;
                    short[] buf = new short[2048];
                    while (tonePlaying && !Thread.currentThread().isInterrupted()) {
                        for (int i = 0; i < buf.length; i++) {
                            buf[i] = (short) (Math.sin(phase) * 16000);
                            phase += step;
                            if (phase > 2.0 * Math.PI * 4096) {
                                phase -= 2.0 * Math.PI * 4096;
                            }
                        }
                        track.write(buf, 0, buf.length);
                    }
                } catch (Exception ignored) {
                }
            });
            toneThread.start();
        } catch (Exception e) {
            ToolViewFactory.toast(hostContext, PackRes.str("media", R.string.s_tone_failed, "Tone failed"));
        }
    }

    private static void writeToneWav(File out, int freqHz, int kind, int seconds) throws Exception {
        int sr = 44100;
        int n = Math.max(1, sr * Math.max(1, seconds));
        int dataSize = n * 2;
        FileOutputStream os = new FileOutputStream(out);
        byte[] h = new byte[44];
        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        int chunk = 36 + dataSize;
        h[4] = (byte) (chunk & 255); h[5] = (byte) ((chunk >> 8) & 255); h[6] = (byte) ((chunk >> 16) & 255); h[7] = (byte) ((chunk >> 24) & 255);
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        h[16] = 16; h[20] = 1; h[22] = 1;
        h[24] = (byte) (sr & 255); h[25] = (byte) ((sr >> 8) & 255); h[26] = (byte) ((sr >> 16) & 255); h[27] = (byte) ((sr >> 24) & 255);
        int br = sr * 2;
        h[28] = (byte) (br & 255); h[29] = (byte) ((br >> 8) & 255); h[30] = (byte) ((br >> 16) & 255); h[31] = (byte) ((br >> 24) & 255);
        h[32] = 2; h[34] = 16;
        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';
        h[40] = (byte) (dataSize & 255); h[41] = (byte) ((dataSize >> 8) & 255); h[42] = (byte) ((dataSize >> 16) & 255); h[43] = (byte) ((dataSize >> 24) & 255);
        os.write(h);
        double step = 2.0 * Math.PI * freqHz / sr;
        double phase = 0;
        byte[] buf = new byte[4096];
        int pos = 0;
        for (int i = 0; i < n; i++) {
            double s;
            if (kind == 1) s = Math.signum(Math.sin(phase));
            else if (kind == 2) s = 2.0 * (phase / (2.0 * Math.PI) - Math.floor(phase / (2.0 * Math.PI) + 0.5));
            else s = Math.sin(phase);
            short v = (short) (s * 16000);
            buf[pos++] = (byte) (v & 255);
            buf[pos++] = (byte) ((v >> 8) & 255);
            if (pos == buf.length) {
                os.write(buf);
                pos = 0;
            }
            phase += step;
            if (phase > 2.0 * Math.PI * 4096) phase -= 2.0 * Math.PI * 4096;
        }
        if (pos > 0) os.write(buf, 0, pos);
        os.close();
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
        hostContext = context.getApplicationContext();
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("media", R.string.s_tone_generator, "Tone Generator"));
        TextView freqLabel = ToolViewFactory.addLabel(box, PackRes.str("media", R.string.s_frequency_440_hz, "Frequency: 440 Hz"));
        SeekBar freqBar = new SeekBar(context);
        freqBar.setMax(3950);
        freqBar.setProgress(390);
        box.addView(freqBar);
        final int[] freq = new int[]{440};
        freqBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                freq[0] = 50 + progress;
                freqLabel.setText("Frequency: " + freq[0] + " Hz");
                if (tonePlaying) {
                    stopTone();
                    startTone(freq[0]);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton playBtn = ToolViewFactory.makeRowButton(row, PackRes.str("media", R.string.s_play, "Play"), 1f);
        MaterialButton stopBtn = ToolViewFactory.makeRowButton(row, PackRes.str("media", R.string.s_stop, "Stop"), 1f);
        playBtn.setOnClickListener(v -> {
            stopTone();
            startTone(freq[0]);
        });
        stopBtn.setOnClickListener(v -> stopTone());
        ToolViewFactory.addLabel(box, PackRes.str("media", R.string.s_waveform, "Waveform"));
        Spinner waveSpinner = new Spinner(context);
        String[] waves = new String[]{"Sine", "Square", "Sawtooth"};
        ArrayAdapter<String> waveAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, waves);
        waveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        waveSpinner.setAdapter(waveAdapter);
        box.addView(waveSpinner);
        TextView durLabel = ToolViewFactory.addLabel(box, PackRes.str("media", R.string.s_save_length_3_s, "Save length: 3 s"));
        SeekBar durBar = new SeekBar(context);
        durBar.setMax(27);
        durBar.setProgress(2);
        box.addView(durBar);
        final int[] toneSecs = new int[]{3};
        durBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                toneSecs[0] = 1 + progress;
                durLabel.setText("Save length: " + toneSecs[0] + " s");
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout toneRow2 = ToolViewFactory.makeRow(box);
        MaterialButton saveToneBtn = ToolViewFactory.makeRowButton(toneRow2, PackRes.str("media", R.string.s_save_wav, "Save WAV"), 1f);
        MaterialButton shareToneBtn = ToolViewFactory.makeRowButton(toneRow2, PackRes.str("media", R.string.s_share, "Share"), 1f);
        MaterialButton openToneBtn = ToolViewFactory.makeRowButton(toneRow2, PackRes.str("media", R.string.s_open_file, "Open file"), 1f);
        final File[] lastTone = new File[1];
        saveToneBtn.setOnClickListener(v -> {
            try {
                File dir = new File(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_MUSIC), "Tones");
                dir.mkdirs();
                File out = new File(dir, "tone_" + freq[0] + "hz_" + System.currentTimeMillis() + ".wav");
                writeToneWav(out, freq[0], waveSpinner.getSelectedItemPosition(), toneSecs[0]);
                lastTone[0] = out;
                ToolViewFactory.toast(context, "Saved " + out.getName());
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_save_failed, "Save failed"));
            }
        });
        shareToneBtn.setOnClickListener(v -> {
            if (lastTone[0] != null && lastTone[0].exists()) shareFile(context, lastTone[0], "audio/*");
            else ToolViewFactory.toast(context, PackRes.str("media", R.string.s_save_first, "Save first"));
        });
        openToneBtn.setOnClickListener(v -> {
            if (lastTone[0] != null && lastTone[0].exists()) {
                try {
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", lastTone[0]);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "audio/*");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(i, "Open tone"));
                } catch (Exception e) {
                    ToolViewFactory.toast(context, PackRes.str("media", R.string.s_open_failed, "Open failed"));
                }
            } else {
                ToolViewFactory.toast(context, PackRes.str("media", R.string.s_save_first, "Save first"));
            }
        });
        return box;
    }

    @Override
    public void onDestroy() {
        stopTone();
    }
}
