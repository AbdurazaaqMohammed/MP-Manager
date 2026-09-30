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
 * Extraction of ToolRunnerActivity.buildDiscount().
 */
public class DiscountTool extends BaseToolPlugin {

    public DiscountTool() {
        super("discount", "Discount Calculator", "Prices, discounts, tax", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Discount Calculator");
        EditText priceInput = ToolViewFactory.makeInput(box, "Original price",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText discInput = ToolViewFactory.makeInput(box, "Discount percent",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText taxInput = ToolViewFactory.makeInput(box, "Tax percent (optional)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Calculate");
        goBtn.setOnClickListener(v -> {
            try {
                double price = Double.parseDouble(priceInput.getText().toString());
                double disc = discInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(discInput.getText().toString());
                double tax = taxInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(taxInput.getText().toString());
                double[] r = Money.discount(price, disc, tax);
                DecimalFormat df = new DecimalFormat("0.00");
                output.setText("You save " + df.format(r[0]) + ", pay " + df.format(r[1]));
            } catch (Exception e) {
                output.setText("Invalid input");
            }
        });
        return box;
    }
}
