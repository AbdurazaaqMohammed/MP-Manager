package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Primes;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.List;

/**
 * Extraction of ToolRunnerActivity.buildPrime().
 */
public class PrimeTool extends BaseToolPlugin {

    public PrimeTool() {
        super("prime", R.string.prime_title, R.string.prime_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.prime_prime_tools));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.prime_number_up_to_1000000000), InputType.TYPE_CLASS_NUMBER);
        input.setText("97");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton checkBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.prime_check_prime_and_factorize));
        checkBtn.setOnClickListener(v -> {
            try {
                long n = Long.parseLong(input.getText().toString().trim());
                if (n < 0 || n > 1000000000L) {
                    output.setText(output.getContext().getString(R.string.prime_enter_0_to_1000000000));
                    return;
                }
                StringBuilder b = new StringBuilder();
                b.append(n).append(n == 1 ? output.getContext().getString(R.string.prime_not_prime) : (Primes.isPrime(n) ? output.getContext().getString(R.string.prime_is_prime) : output.getContext().getString(R.string.prime_not_prime)));
                if (n > 1) {
                    b.append(output.getContext().getString(R.string.prime_factors)).append(Primes.factorize(n)).append("\n");
                    b.append(output.getContext().getString(R.string.prime_next_prime)).append(Primes.nextPrime(n));
                }
                output.setText(b.toString());
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.prime_enter_an_integer));
            }
        });
        MaterialButton listBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.prime_list_primes_up_to_n_max_10000));
        listBtn.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(input.getText().toString().trim());
                if (n < 2 || n > 10000) {
                    output.setText(output.getContext().getString(R.string.prime_enter_2_to_10000));
                    return;
                }
                List<Integer> primes = Primes.listUpTo(n);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < primes.size(); i++) {
                    if (i > 0) {
                        b.append(", ");
                    }
                    b.append(primes.get(i));
                }
                output.setText(output.getContext().getString(R.string.prime_count, primes.size()) + b);
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.prime_enter_an_integer));
            }
        });
        return box;
    }
}
