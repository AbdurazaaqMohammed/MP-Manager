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
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

import java.util.Random;

/**
 * Pilot extraction of ToolRunnerActivity.buildRandom().
 */
public class RandomTool extends BaseToolPlugin {

    public RandomTool() {
        super("random", R.string.random_title, R.string.random_sub,  ToolCategories.RAND);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.random_randomizer));
        EditText minInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.random_min),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText maxInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.random_max),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        minInput.setText("1");
        maxInput.setText("100");
        TextView output = ToolViewFactory.makeOutput(box);
        output.setTextSize(40);
        output.setGravity(Gravity.CENTER);
        output.setText("-");
        Random random = new Random();
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton numBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.random_number), 1f);
        MaterialButton diceBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.random_dice), 1f);
        MaterialButton coinBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.random_coin), 1f);
        numBtn.setOnClickListener(v -> {
            try {
                int min = Integer.parseInt(minInput.getText().toString().trim());
                int max = Integer.parseInt(maxInput.getText().toString().trim());
                if (min > max) {
                    int t = min;
                    min = max;
                    max = t;
                }
                output.setText(String.valueOf(min + random.nextInt(max - min + 1)));
                ToolViewFactory.vibrateTick(context);
            } catch (Exception e) {
                output.setText("?");
            }
        });
        diceBtn.setOnClickListener(v -> {
            int d = 1 + random.nextInt(6);
            String[] faces = new String[]{"\u2680", "\u2681", "\u2682", "\u2683", "\u2684", "\u2685"};
            output.setText(faces[d - 1] + "  " + d);
            ToolViewFactory.vibrateTick(context);
        });
        coinBtn.setOnClickListener(v -> output.setText(random.nextBoolean()
                ? output.getContext().getString(R.string.random_heads)
                : output.getContext().getString(R.string.random_tails)));
        return box;
    }
}
