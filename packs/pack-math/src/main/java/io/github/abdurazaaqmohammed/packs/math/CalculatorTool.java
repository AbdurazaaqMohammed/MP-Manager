package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.domain.math.ExpressionEvaluator;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculator suite: standard + scientific calculator on the first tab, and
 * the rest of the math pack grouped into Finance / Convert / Algebra menus
 * on bottom-navigation tabs. Sibling tools are hosted inline.
 */
public class CalculatorTool extends BaseToolPlugin {

    private static final int NAV_CALC = 1;
    private static final int NAV_FINANCE = 2;
    private static final int NAV_CONVERT = 3;
    private static final int NAV_ALGEBRA = 4;

    private static final String[] FINANCE = {
            "emi", "compound", "savings", "gst", "tip", "discount", "percent", "unitprice"};
    private static final String[] CONVERT = {"converter", "baseconv", "currency", "cooking"};
    private static final String[] ALGEBRA = {
            "quadratic", "matrix", "triangle", "geometry", "fraction", "prime",
            "gpa", "pace", "fuel", "ohm", "resistor"};

    private Context host;
    private FrameLayout content;
    private View calcPage;
    private final List<ToolPlugin> hosted = new ArrayList<>();

    private final StringBuilder expr = new StringBuilder();
    private TextView exprView;
    private TextView resultView;
    private TextView historyView;
    private final List<String> history = new ArrayList<>();
    private String lastAns = "0";
    private boolean justEvaluated;
    private LinearLayout sciPanel;
    private boolean sciShown;

    public CalculatorTool() {
        super("calc", "Calculator", "Calculate science expressions", ToolCategories.MATH);
    }

    private static ToolPlugin newTool(String id) {
        switch (id) {
            case "emi":
                return new EmiTool();
            case "compound":
                return new CompoundTool();
            case "savings":
                return new SavingsTool();
            case "gst":
                return new GstTool();
            case "tip":
                return new TipTool();
            case "discount":
                return new DiscountTool();
            case "percent":
                return new PercentTool();
            case "unitprice":
                return new UnitPriceTool();
            case "converter":
                return new ConverterTool();
            case "baseconv":
                return new BaseConvTool();
            case "currency":
                return new CurrencyTool();
            case "cooking":
                return new CookingTool();
            case "quadratic":
                return new QuadraticTool();
            case "matrix":
                return new MatrixTool();
            case "triangle":
                return new TriangleTool();
            case "geometry":
                return new GeometryTool();
            case "fraction":
                return new FractionTool();
            case "prime":
                return new PrimeTool();
            case "gpa":
                return new GpaTool();
            case "pace":
                return new PaceTool();
            case "fuel":
                return new FuelTool();
            case "ohm":
                return new OhmTool();
            case "resistor":
                return new ResistorTool();
            default:
                return null;
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        host = context;
        LinearLayout box = ToolViewFactory.container(context);
        content = new FrameLayout(context);
        box.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        BottomNavigationView nav = new BottomNavigationView(context);
        Menu menu = nav.getMenu();
        navItem(context, menu, NAV_CALC, "Calculator",
                new String[]{"calculate_24px", "calculator_24px", "hex_keyboard_24px"},
                android.R.drawable.ic_menu_edit);
        navItem(context, menu, NAV_FINANCE, "Finance",
                new String[]{"payments_24px", "account_balance_24px", "tag_24px"},
                android.R.drawable.ic_menu_info_details);
        navItem(context, menu, NAV_CONVERT, "Convert",
                new String[]{"swap_horiz_24px", "baseline_swap_horiz_24"},
                android.R.drawable.ic_menu_sort_by_size);
        navItem(context, menu, NAV_ALGEBRA, "Algebra",
                new String[]{"functions_24px", "sigma_24px", "ic_grid"},
                android.R.drawable.ic_menu_help);
        nav.setLabelVisibilityMode(
                com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setSelectedItemId(NAV_CALC);
        nav.setOnItemSelectedListener(item -> {
            destroyHosted();
            int id = item.getItemId();
            if (id == NAV_CALC) showCalc();
            else if (id == NAV_FINANCE) showMenu("Finance", FINANCE);
            else if (id == NAV_CONVERT) showMenu("Convert", CONVERT);
            else showMenu("Algebra", ALGEBRA);
            return true;
        });
        box.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showCalc();
        return box;
    }

    private static void navItem(Context context, Menu menu, int id, String title,
                                String[] candidates, int fallback) {
        MenuItem item = menu.add(0, id, id, title);
        int res = 0;
        try {
            for (String name : candidates) {
                res = context.getResources().getIdentifier(name, "drawable",
                        context.getPackageName());
                if (res != 0) break;
            }
        } catch (Exception ignored) {
        }
        try {
            item.setIcon(ContextCompat.getDrawable(context, res != 0 ? res : fallback));
        } catch (Exception ignored) {
        }
    }

    private void showCalc() {
        if (calcPage == null) calcPage = buildCalc(host);
        content.removeAllViews();
        content.addView(calcPage);
    }

    private void destroyHosted() {
        for (ToolPlugin p : hosted) {
            try {
                p.onDestroy();
            } catch (Exception ignored) {
            }
        }
        hosted.clear();
    }

    private void showMenu(String group, String[] ids) {
        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        ToolViewFactory.addLabel(page, group + " tools");
        for (String id : ids) {
            ToolPlugin tool = newTool(id);
            if (tool == null) continue;
            String title;
            String sub;
            try {
                title = tool.title(host);
                sub = tool.subtitle(host);
            } catch (Exception ignored) {
                continue;
            }
            MaterialCardView card = new MaterialCardView(host);
            card.setRadius(ToolViewFactory.dp(host, 14));
            card.setCardElevation(ToolViewFactory.dp(host, 1));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = ToolViewFactory.dp(host, 5);
            cp.setMargins(0, m, 0, m);
            card.setLayoutParams(cp);
            card.setClickable(true);
            card.setFocusable(true);
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = ToolViewFactory.dp(host, 12);
            row.setPadding(p, p, p, p);
            LinearLayout texts = new LinearLayout(host);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = new TextView(host);
            t.setText(title);
            t.setTextSize(15);
            t.setTextColor(MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
            texts.addView(t);
            TextView s = new TextView(host);
            s.setText(sub == null ? "" : sub);
            s.setTextSize(12);
            s.setAlpha(0.7f);
            s.setTextColor(MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
            texts.addView(s);
            row.addView(texts);
            TextView chev = new TextView(host);
            chev.setText("›");
            chev.setTextSize(24);
            chev.setAlpha(0.5f);
            row.addView(chev);
            card.addView(row);
            final ToolPlugin open = tool;
            final String groupName = group;
            card.setOnClickListener(v -> showTool(groupName, open));
            page.addView(card);
        }
        content.removeAllViews();
        content.addView(page);
    }

    private void showTool(String group, ToolPlugin tool) {
        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        MaterialButton back = new MaterialButton(host, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        back.setText("‹ " + group);
        back.setOnClickListener(v -> {
            destroyHosted();
            if (NAV_FINANCE == currentGroup(group)) showMenu("Finance", FINANCE);
            else if (NAV_CONVERT == currentGroup(group)) showMenu("Convert", CONVERT);
            else showMenu("Algebra", ALGEBRA);
        });
        page.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        try {
            View body = tool.createView(host, page);
            if (body != null && body.getParent() == null) page.addView(body);
            hosted.add(tool);
        } catch (Exception e) {
            TextView err = new TextView(host);
            err.setText("Could not open tool");
            page.addView(err);
        }
        content.removeAllViews();
        content.addView(page);
    }

    private static int currentGroup(String group) {
        if ("Finance".equals(group)) return NAV_FINANCE;
        if ("Convert".equals(group)) return NAV_CONVERT;
        return NAV_ALGEBRA;
    }

    // ---------- calculator page ----------

    private View buildCalc(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        historyView = new TextView(context);
        historyView.setTextSize(13);
        historyView.setGravity(Gravity.END);
        historyView.setAlpha(0.65f);
        historyView.setTypeface(Typeface.MONOSPACE);
        historyView.setMinLines(1);
        historyView.setOnClickListener(v -> {
            if (!history.isEmpty()) {
                String last = history.get(history.size() - 1);
                int eq = last.lastIndexOf('=');
                String recalled = eq > 0 ? last.substring(0, eq).trim() : last;
                expr.setLength(0);
                expr.append(recalled);
                justEvaluated = false;
                render();
            }
        });
        page.addView(historyView);
        exprView = new TextView(context);
        exprView.setTextSize(22);
        exprView.setGravity(Gravity.END);
        exprView.setTypeface(Typeface.MONOSPACE);
        exprView.setTextIsSelectable(true);
        page.addView(exprView);
        resultView = new TextView(context);
        resultView.setTextSize(36);
        resultView.setGravity(Gravity.END);
        resultView.setTypeface(Typeface.MONOSPACE);
        resultView.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, 0xFF000000));
        page.addView(resultView);
        LinearLayout topRow = ToolViewFactory.makeRow(page);
        MaterialButton sciBtn = ToolViewFactory.makeRowButton(topRow, "fx", 1f);
        MaterialButton histBtn = ToolViewFactory.makeRowButton(topRow, "History", 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(topRow, "Copy", 1f);
        sciBtn.setOnClickListener(v -> {
            sciShown = !sciShown;
            sciPanel.setVisibility(sciShown ? View.VISIBLE : View.GONE);
        });
        histBtn.setOnClickListener(v -> {
            history.clear();
            renderHistory();
        });
        copyBtn.setOnClickListener(v -> ToolViewFactory.copyText(context, "calc",
                resultView.getText().toString()));
        sciPanel = new LinearLayout(context);
        sciPanel.setOrientation(LinearLayout.VERTICAL);
        sciPanel.setVisibility(View.GONE);
        page.addView(sciPanel);
        keyRow(sciPanel, new String[]{"sin", "cos", "tan", "π"}, 14);
        keyRow(sciPanel, new String[]{"ln", "log", "√", "x²"}, 14);
        keyRow(sciPanel, new String[]{"xʸ", "e", "1/x", "n!"}, 14);
        keyRow(page, new String[]{"C", "⌫", "%", "÷"}, 16);
        keyRow(page, new String[]{"7", "8", "9", "×"}, 20);
        keyRow(page, new String[]{"4", "5", "6", "−"}, 20);
        keyRow(page, new String[]{"1", "2", "3", "+"}, 20);
        keyRow(page, new String[]{"±", "0", ".", "="}, 20);
        render();
        return page;
    }

    private void keyRow(LinearLayout parent, String[] keys, int textSize) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (String key : keys) {
            boolean eq = "=".equals(key);
            MaterialButton b = eq ? new MaterialButton(host)
                    : new MaterialButton(host, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(key);
            b.setTextSize(textSize);
            b.setMinHeight(ToolViewFactory.dp(host, 52));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int m = ToolViewFactory.dp(host, 3);
            p.setMargins(m, m, m, m);
            row.addView(b, p);
            final String k = key;
            b.setOnClickListener(v -> press(k));
        }
    }

    private void press(String k) {
        String cur = expr.toString();
        switch (k) {
            case "C":
                expr.setLength(0);
                justEvaluated = false;
                resultView.setText("0");
                break;
            case "⌫":
                if (cur.length() > 0) expr.setLength(cur.length() - 1);
                justEvaluated = false;
                break;
            case "=":
                evaluate(true);
                break;
            case "%":
                expr.append("/100");
                justEvaluated = false;
                break;
            case "±":
                negate();
                break;
            case "1/x":
                invert();
                break;
            case "n!":
                factorial();
                break;
            case "x²":
                expr.append("^2");
                justEvaluated = false;
                break;
            case "xʸ":
                expr.append("^");
                justEvaluated = false;
                break;
            case "sin":
            case "cos":
            case "tan":
                freshIfEvaluated(k);
                expr.append(k).append("(");
                justEvaluated = false;
                break;
            case "ln":
            case "log":
                freshIfEvaluated(k);
                expr.append(k).append("(");
                justEvaluated = false;
                break;
            case "√":
                freshIfEvaluated(k);
                expr.append("sqrt(");
                justEvaluated = false;
                break;
            case "π":
                freshIfEvaluated(k);
                expr.append("pi");
                justEvaluated = false;
                break;
            case "e":
                freshIfEvaluated(k);
                expr.append("e");
                justEvaluated = false;
                break;
            default:
                if (justEvaluated && (k.equals("(") || k.equals(")"))) {
                    justEvaluated = false;
                } else if (justEvaluated && isValueKey(k)) {
                    expr.setLength(0);
                    justEvaluated = false;
                }
                expr.append(k);
                break;
        }
        render();
    }

    private static boolean isValueKey(String k) {
        return (k.length() == 1 && ((k.charAt(0) >= '0' && k.charAt(0) <= '9') || k.charAt(0) == '.'));
    }

    private void freshIfEvaluated(String k) {
        if (justEvaluated && (isValueKey(k) || k.length() > 1)) {
            expr.setLength(0);
            justEvaluated = false;
        }
    }

    private void negate() {
        try {
            double v = ExpressionEvaluator.eval(expr.toString());
            String out = ExpressionEvaluator.format(-v);
            expr.setLength(0);
            expr.append(out);
            lastAns = out;
            justEvaluated = true;
        } catch (Exception ignored) {
            expr.insert(0, "-(");
            expr.append(")");
            justEvaluated = false;
        }
    }

    private void invert() {
        try {
            double v = ExpressionEvaluator.eval(expr.toString());
            if (v == 0) {
                resultView.setText("Error");
                return;
            }
            String out = ExpressionEvaluator.format(1 / v);
            expr.setLength(0);
            expr.append(out);
            lastAns = out;
            justEvaluated = true;
        } catch (Exception ignored) {
            expr.insert(0, "1/(");
            expr.append(")");
            justEvaluated = false;
        }
    }

    private void factorial() {
        try {
            double v = ExpressionEvaluator.eval(expr.toString());
            if (v < 0 || v > 170 || v != Math.floor(v)) {
                resultView.setText("Error");
                return;
            }
            double r = 1;
            for (int i = 2; i <= (int) v; i++) r *= i;
            String out = ExpressionEvaluator.format(r);
            expr.setLength(0);
            expr.append(out);
            lastAns = out;
            justEvaluated = true;
        } catch (Exception ignored) {
            resultView.setText("Error");
        }
    }

    private void evaluate(boolean commit) {
        String s = expr.toString();
        if (s.isEmpty()) return;
        try {
            String out = ExpressionEvaluator.format(ExpressionEvaluator.eval(s));
            resultView.setText(out);
            if (commit) {
                history.add(s + " = " + out);
                if (history.size() > 20) history.remove(0);
                renderHistory();
                expr.setLength(0);
                expr.append(out);
                lastAns = out;
                justEvaluated = true;
                render();
            }
        } catch (Exception ignored) {
            if (commit) resultView.setText("Error");
        }
    }

    private void render() {
        String s = expr.toString();
        exprView.setText(s.isEmpty() ? "0" : s);
        if (!justEvaluated && !s.isEmpty()) {
            try {
                resultView.setText(ExpressionEvaluator.format(ExpressionEvaluator.eval(s)));
            } catch (Exception ignored) {
            }
        } else if (s.isEmpty()) {
            resultView.setText("0");
        }
    }

    private void renderHistory() {
        StringBuilder b = new StringBuilder();
        int start = Math.max(0, history.size() - 4);
        for (int i = start; i < history.size(); i++) {
            if (b.length() > 0) b.append('\n');
            b.append(history.get(i));
        }
        historyView.setText(b.toString());
    }

    @Override
    public void onDestroy() {
        destroyHosted();
        calcPage = null;
        exprView = null;
        resultView = null;
        historyView = null;
        sciPanel = null;
    }
}
