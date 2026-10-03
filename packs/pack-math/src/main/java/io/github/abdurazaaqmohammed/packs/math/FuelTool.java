package io.github.abdurazaaqmohammed.packs.math;

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
        super("fuel", R.string.fuel_title, R.string.fuel_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.fuel_fuel_calculator));
        EditText distInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.fuel_distance_in_km),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText fuelInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.fuel_fuel_used_in_liters),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText priceInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.fuel_price_per_liter_optional),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.compound_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                double dist = Double.parseDouble(distInput.getText().toString());
                double fuel = Double.parseDouble(fuelInput.getText().toString());
                if (dist <= 0 || fuel <= 0) {
                    output.setText(output.getContext().getString(R.string.fuel_distance_and_fuel_must_be_abov));
                    return;
                }
                double per100 = fuel / dist * 100.0;
                double kml = dist / fuel;
                double mpg = kml * 2.35215;
                DecimalFormat df = new DecimalFormat("0.00");
                StringBuilder b = new StringBuilder();
                b.append(output.getContext().getString(R.string.fuel_consumption)).append(df.format(per100)).append(" L/100km\n");
                b.append(output.getContext().getString(R.string.fuel_economy)).append(df.format(kml)).append(" km/L  (").append(df.format(mpg)).append(" mpg)\n");
                String ps = priceInput.getText().toString().trim();
                if (!ps.isEmpty()) {
                    double price = Double.parseDouble(ps);
                    b.append(output.getContext().getString(R.string.fuel_trip_cost)).append(df.format(fuel * price))
                            .append(output.getContext().getString(R.string.fuel_cost_per_km, df.format(fuel * price / dist)));
                }
                output.setText(b.toString());
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.fuel_enter_distance_and_fuel));
            }
        });
        return box;
    }
}
