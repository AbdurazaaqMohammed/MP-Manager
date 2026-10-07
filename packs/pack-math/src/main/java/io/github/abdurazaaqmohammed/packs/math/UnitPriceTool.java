package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Money;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildUnitPrice().
 */
public class UnitPriceTool extends BaseToolPlugin {

    public UnitPriceTool() {
        super("unitprice", "Price Compare", "Compare best values", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_price_compare, "Price Compare"));
        EditText priceA = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_pack_a_price, "Pack A price"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText qtyA = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_pack_a_quantity, "Pack A quantity"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText priceB = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_pack_b_price, "Pack B price"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText qtyB = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_pack_b_quantity, "Pack B quantity"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_compare, "Compare"));
        goBtn.setOnClickListener(v -> {
            try {
                double pa = Double.parseDouble(priceA.getText().toString());
                double qa = Double.parseDouble(qtyA.getText().toString());
                double pb = Double.parseDouble(priceB.getText().toString());
                double qb = Double.parseDouble(qtyB.getText().toString());
                if (qa <= 0 || qb <= 0) {
                    output.setText(PackRes.str("math", R.string.s_quantities_must_be_above_zero, "Quantities must be above zero"));
                    return;
                }
                double[] r = Money.unitPrices(pa, qa, pb, qb);
                double ua = r[0];
                double ub = r[1];
                DecimalFormat df = new DecimalFormat("0.0000");
                StringBuilder b = new StringBuilder();
                b.append("A ").append(df.format(ua)).append(" per unit\nB ").append(df.format(ub)).append(" per unit\n");
                if (ua < ub) {
                    b.append("A is cheaper by ").append(new DecimalFormat("0.0").format((ub - ua) / ub * 100)).append("%");
                } else if (ub < ua) {
                    b.append("B is cheaper by ").append(new DecimalFormat("0.0").format((ua - ub) / ua * 100)).append("%");
                } else {
                    b.append("Same value");
                }
                output.setText(b.toString());
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_fill_all_four_fields, "Fill all four fields"));
            }
        });
        return box;
    }
}
