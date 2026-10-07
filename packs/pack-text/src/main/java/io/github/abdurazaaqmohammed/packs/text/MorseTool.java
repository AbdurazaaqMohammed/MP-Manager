package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.text.TextCodecs;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

import java.util.HashMap;
import java.util.Map;

/**
 * Extraction of ToolRunnerActivity.buildMorse().
 */
public class MorseTool extends BaseToolPlugin {

    public MorseTool() {
        super("morse", "Morse Code", "Encode, decode, play Morse", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_morse_code, "Morse Code"));
        Map<String, String> enc = TextCodecs.morseEncodeMap();
        Map<String, String> dec = new HashMap<>();
        for (Map.Entry<String, String> e : enc.entrySet()) {
            dec.put(e.getValue(), e.getKey());
        }
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_text_or_morse, "Text or morse"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("text", R.string.s_result, "Result"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_encode, "Encode"), 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_decode, "Decode"), 1f);
        encBtn.setOnClickListener(v -> output.setText(TextCodecs.morseEncode(input.getText().toString(), enc)));
        decBtn.setOnClickListener(v -> output.setText(TextCodecs.morseDecode(input.getText().toString(), dec)));
        LinearLayout row2 = ToolViewFactory.makeRow(box);
        MaterialButton playBtn = ToolViewFactory.makeRowButton(row2, PackRes.str("text", R.string.s_play, "Play"), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row2, PackRes.str("text", R.string.s_copy, "Copy"), 1f);
        playBtn.setOnClickListener(v -> {
            final String code = output.getText().toString();
            new Thread(() -> {
                try {
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                    for (int i = 0; i < code.length(); i++) {
                        char c = code.charAt(i);
                        if (c == '.') {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
                            Thread.sleep(200);
                        } else if (c == '-') {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 360);
                            Thread.sleep(440);
                        } else {
                            Thread.sleep(240);
                        }
                    }
                    tg.release();
                } catch (Exception ignored) {
                }
            }).start();
        });
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, PackRes.str("text", R.string.s_morse, "morse"), output.getText().toString()));
        return box;
    }
}
