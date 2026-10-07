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

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildFuel().
 */
public class FuelTool extends BaseToolPlugin {

    public FuelTool() {
        super("fuel", "Fuel Calculator", "Mileage and fuel cost", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_fuel_calculator, "Fuel Calculator"));
        EditText distInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_distance_in_km, "Distance in km"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText fuelInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_fuel_used_in_liters, "Fuel used in liters"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText priceInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_price_per_liter_optional, "Price per liter (optional)"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                double dist = Double.parseDouble(distInput.getText().toString());
                double fuel = Double.parseDouble(fuelInput.getText().toString());
                if (dist <= 0 || fuel <= 0) {
                    output.setText(PackRes.str("math", R.string.s_distance_and_fuel_must_be_above_zero, "Distance and fuel must be above zero"));
                    return;
                }
                double per100 = fuel / dist * 100.0;
                double kml = dist / fuel;
                double mpg = kml * 2.35215;
                DecimalFormat df = new DecimalFormat("0.00");
                StringBuilder b = new StringBuilder();
                b.append("Consumption ").append(df.format(per100)).append(" L/100km\n");
                b.append("Economy ").append(df.format(kml)).append(" km/L  (").append(df.format(mpg)).append(" mpg)\n");
                String ps = priceInput.getText().toString().trim();
                if (!ps.isEmpty()) {
                    double price = Double.parseDouble(ps);
                    b.append("Trip cost ").append(df.format(fuel * price)).append("  (").append(df.format(fuel * price / dist)).append(" per km)");
                }
                output.setText(b.toString());
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_enter_distance_and_fuel, "Enter distance and fuel"));
            }
        });
        return box;
    }
}
