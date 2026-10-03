package io.github.abdurazaaqmohammed.packs.math;

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
        super("unitprice", R.string.unitprice_title, R.string.unitprice_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.unitprice_price_compare));
        EditText priceA = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.unitprice_pack_a_price),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText qtyA = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.unitprice_pack_a_quantity),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText priceB = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.unitprice_pack_b_price),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText qtyB = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.unitprice_pack_b_quantity),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.unitprice_compare));
        goBtn.setOnClickListener(v -> {
            try {
                double pa = Double.parseDouble(priceA.getText().toString());
                double qa = Double.parseDouble(qtyA.getText().toString());
                double pb = Double.parseDouble(priceB.getText().toString());
                double qb = Double.parseDouble(qtyB.getText().toString());
                if (qa <= 0 || qb <= 0) {
                    output.setText(output.getContext().getString(R.string.unitprice_quantities_must_be_above_zero));
                    return;
                }
                double[] r = Money.unitPrices(pa, qa, pb, qb);
                double ua = r[0];
                double ub = r[1];
                DecimalFormat df = new DecimalFormat("0.0000");
                StringBuilder b = new StringBuilder();
                b.append("A ").append(df.format(ua)).append(output.getContext().getString(R.string.unitprice_per_unit)).append("\n")
                        .append("B ").append(df.format(ub)).append(output.getContext().getString(R.string.unitprice_per_unit)).append("\n");
                if (ua < ub) {
                    b.append(output.getContext().getString(R.string.unitprice_a_cheaper, new DecimalFormat("0.0").format((ub - ua) / ub * 100)));
                } else if (ub < ua) {
                    b.append(output.getContext().getString(R.string.unitprice_b_cheaper, new DecimalFormat("0.0").format((ua - ub) / ua * 100)));
                } else {
                    b.append(output.getContext().getString(R.string.unitprice_same));
                }
                output.setText(b.toString());
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.unitprice_fill_all_four_fields));
            }
        });
        return box;
    }
}
