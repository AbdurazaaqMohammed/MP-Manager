package io.github.abdurazaaqmohammed.packs.random;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PickListTool extends BaseToolPlugin {

    public PickListTool() {
        super("pick", "List Picker", "Pick a random entry from a list", ToolCategories.RAND);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("random", R.string.s_list_picker, "List Picker"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("random", R.string.s_comma_or_line_separated_options, "Comma or line separated options"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setTextSize(28);
        output.setGravity(Gravity.CENTER);
        output.setText(PackRes.str("random", R.string.s_x, "—"));
        MaterialButton pick = ToolViewFactory.makeButton(box, PackRes.str("random", R.string.s_pick_one, "Pick one"));
        Random random = new Random();
        pick.setOnClickListener(v -> {
            String[] lines = input.getText().toString().split("[,\\n]+");
            List<String> items = new ArrayList<>();
            for (String l : lines) {
                String t = l.trim();
                if (!t.isEmpty()) items.add(t);
            }
            if (items.isEmpty()) {
                output.setText(PackRes.str("random", R.string.s_add_options_first, "Add options first"));
                return;
            }
            output.setText(items.get(random.nextInt(items.size())));
            ToolViewFactory.vibrateTick(context);
        });
        MaterialButton copy = ToolViewFactory.makeButton(box, PackRes.str("random", R.string.s_copy_result, "Copy result"));
        copy.setOnClickListener(v -> ToolViewFactory.copyText(context, PackRes.str("random", R.string.s_pick, "pick"), output.getText().toString()));
        return box;
    }
}
