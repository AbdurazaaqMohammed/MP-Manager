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
        super("prime", "Prime Tools", "Primes and factors", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_prime_tools, "Prime Tools"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_number_up_to_1000000000, "Number up to 1000000000"), InputType.TYPE_CLASS_NUMBER);
        input.setText(PackRes.str("math", R.string.s_97, "97"));
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton checkBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_check_prime_and_factorize, "Check prime and factorize"));
        checkBtn.setOnClickListener(v -> {
            try {
                long n = Long.parseLong(input.getText().toString().trim());
                if (n < 0 || n > 1000000000L) {
                    output.setText(PackRes.str("math", R.string.s_enter_0_to_1000000000, "Enter 0 to 1000000000"));
                    return;
                }
                StringBuilder b = new StringBuilder();
                b.append(n).append(n == 1 ? " is not prime\n" : (Primes.isPrime(n) ? " is prime\n" : " is not prime\n"));
                if (n > 1) {
                    b.append("Factors: ").append(Primes.factorize(n)).append("\n");
                    b.append("Next prime: ").append(Primes.nextPrime(n));
                }
                output.setText(b.toString());
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_enter_an_integer, "Enter an integer"));
            }
        });
        MaterialButton listBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_list_primes_up_to_n_max_10000, "List primes up to N (max 10000)"));
        listBtn.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(input.getText().toString().trim());
                if (n < 2 || n > 10000) {
                    output.setText(PackRes.str("math", R.string.s_enter_2_to_10000, "Enter 2 to 10000"));
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
                output.setText(primes.size() + " primes\n" + b);
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_enter_an_integer, "Enter an integer"));
            }
        });
        return box;
    }
}
