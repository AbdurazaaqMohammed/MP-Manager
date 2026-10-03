package io.github.abdurazaaqmohammed.packs.text;

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
        super("morse", R.string.morse_title, R.string.morse_sub,  ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.morse_morse_code));
        Map<String, String> enc = TextCodecs.morseEncodeMap();
        Map<String, String> dec = new HashMap<>();
        for (Map.Entry<String, String> e : enc.entrySet()) {
            dec.put(e.getValue(), e.getKey());
        }
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.morse_text_or_morse),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.binarytext_result));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.base64_encode), 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.base64_decode), 1f);
        encBtn.setOnClickListener(v -> output.setText(TextCodecs.morseEncode(input.getText().toString(), enc)));
        decBtn.setOnClickListener(v -> output.setText(TextCodecs.morseDecode(input.getText().toString(), dec)));
        LinearLayout row2 = ToolViewFactory.makeRow(box);
        MaterialButton playBtn = ToolViewFactory.makeRowButton(row2, row2.getContext().getString(R.string.morse_play), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row2, row2.getContext().getString(R.string.colorconv_copy), 1f);
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
                ToolViewFactory.copyText(context, context.getString(R.string.morse_morse), output.getText().toString()));
        return box;
    }
}
