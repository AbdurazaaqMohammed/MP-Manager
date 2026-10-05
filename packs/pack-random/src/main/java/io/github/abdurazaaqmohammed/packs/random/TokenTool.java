package io.github.abdurazaaqmohammed.packs.random;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.security.SecureRandom;

public class TokenTool extends BaseToolPlugin {

    public TokenTool() {
        super("token", "Token Generator", "Secure random hex tokens", ToolCategories.RAND);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Token Generator");
        EditText bytesInput = ToolViewFactory.makeInput(box, "Bytes (max 64)",
                InputType.TYPE_CLASS_NUMBER);
        bytesInput.setText("16");
        TextView output = ToolViewFactory.makeOutput(box);
        output.setTextSize(20);
        output.setGravity(Gravity.CENTER);
        MaterialButton gen = ToolViewFactory.makeButton(box, "Generate");
        gen.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(bytesInput.getText().toString().trim());
                if (n < 4 || n > 64) {
                    output.setText("Use 4-64 bytes");
                    return;
                }
                byte[] buf = new byte[n];
                new SecureRandom().nextBytes(buf);
                StringBuilder sb = new StringBuilder();
                for (byte b : buf) sb.append(String.format("%02x", b));
                output.setText(sb.toString());
            } catch (Exception e) {
                output.setText("Check the number");
            }
        });
        MaterialButton copy = ToolViewFactory.makeButton(box, "Copy");
        copy.setOnClickListener(v -> ToolViewFactory.copyText(context, "token", output.getText().toString()));
        return box;
    }
}
