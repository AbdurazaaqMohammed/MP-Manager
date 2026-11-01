package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UiFields;

public class PermissionsEditorHelper {

    private final MainActivity context;

    private CheckBox ownerRead, ownerWrite, ownerExec;
    private CheckBox groupRead, groupWrite, groupExec;
    private CheckBox otherRead, otherWrite, otherExec;
    private CheckBox setUid, setGid, sticky;
    private TextView symbolicPreview;
    private EditText numericInput;

    public PermissionsEditorHelper(MainActivity context) {
        this.context = context;
    }

    public View createPermissionsEditor(String currentPerms) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(4));

        int[] permBits = parseNumericPerms(currentPerms);

        LinearLayout colHeaderRow = new LinearLayout(context);
        colHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        colHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        colHeaderRow.setPadding(0, dp(4), 0, dp(4));

        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));
        colHeaderRow.addView(spacer);

        String[] headers = {"Read", "Write", "Exec"};
        for (String h : headers) {
            TextView tv = new TextView(context);
            tv.setText(h);
            tv.setTextSize(12);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
            tv.setLayoutParams(new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT));
            colHeaderRow.addView(tv);
        }
        root.addView(colHeaderRow);

        ownerRead = createPermCheckbox(permBits[0] & 4);
        ownerWrite = createPermCheckbox(permBits[0] & 2);
        ownerExec = createPermCheckbox(permBits[0] & 1);
        root.addView(createPermRow(context.getString(R.string.owner), ownerRead, ownerWrite, ownerExec));

        groupRead = createPermCheckbox(permBits[1] & 4);
        groupWrite = createPermCheckbox(permBits[1] & 2);
        groupExec = createPermCheckbox(permBits[1] & 1);
        root.addView(createPermRow(context.getString(R.string.group), groupRead, groupWrite, groupExec));

        otherRead = createPermCheckbox(permBits[2] & 4);
        otherWrite = createPermCheckbox(permBits[2] & 2);
        otherExec = createPermCheckbox(permBits[2] & 1);
        root.addView(createPermRow(context.getString(R.string.other), otherRead, otherWrite, otherExec));

        LinearLayout specialHeader = new LinearLayout(context);
        specialHeader.setOrientation(LinearLayout.HORIZONTAL);
        specialHeader.setGravity(Gravity.CENTER);
        specialHeader.setPadding(0, dp(10), 0, dp(4));

        TextView specialLabel = new TextView(context);
        specialLabel.setText("---- " + context.getString(R.string.special_permissions) + " ----");
        specialLabel.setTextSize(12);
        specialLabel.setTypeface(Typeface.MONOSPACE);
        specialLabel.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        specialHeader.addView(specialLabel);
        root.addView(specialHeader);

        setUid = new CheckBox(context);
        setUid.setText(context.getString(R.string.set_uid));
        setUid.setTextSize(12);
        setUid.setChecked((permBits[3] & 4) != 0);
        setUid.setPadding(dp(4), 0, dp(12), 0);

        setGid = new CheckBox(context);
        setGid.setText(context.getString(R.string.set_gid));
        setGid.setTextSize(12);
        setGid.setChecked((permBits[3] & 2) != 0);
        setGid.setPadding(dp(4), 0, dp(12), 0);

        sticky = new CheckBox(context);
        sticky.setText(context.getString(R.string.sticky));
        sticky.setTextSize(12);
        sticky.setChecked((permBits[3] & 1) != 0);
        sticky.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout specialRow = new LinearLayout(context);
        specialRow.setOrientation(LinearLayout.HORIZONTAL);
        specialRow.setGravity(Gravity.CENTER);
        specialRow.setPadding(0, dp(2), 0, dp(10));
        specialRow.addView(setUid);
        specialRow.addView(setGid);
        specialRow.addView(sticky);
        root.addView(specialRow);

        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER);
        previewRow.setPadding(0, dp(6), 0, dp(8));

        symbolicPreview = new TextView(context);
        symbolicPreview.setText(getSymbolicString());
        symbolicPreview.setTypeface(Typeface.MONOSPACE);
        symbolicPreview.setTextSize(15);
        symbolicPreview.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        previewRow.addView(symbolicPreview);

        TextView openParen = new TextView(context);
        openParen.setText(" (");
        openParen.setTypeface(Typeface.MONOSPACE);
        openParen.setTextSize(15);
        openParen.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        previewRow.addView(openParen);

        numericInput = new EditText(context);
        numericInput.setTextSize(15);
        numericInput.setTypeface(Typeface.MONOSPACE);
        numericInput.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        numericInput.setText(String.valueOf(permBits[4]));
        numericInput.setSelectAllOnFocus(true);
        numericInput.setGravity(Gravity.CENTER);
        numericInput.setBackground(null);
        numericInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(4), octalOnly() });
        numericInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String val = s.toString().trim();
                if (val.length() == 3 || val.length() == 4) {
                    try {
                        int num = Integer.parseInt(val);
                        int[] bits = parseNumericPerms(String.valueOf(num));
                        setCheckboxesFromBits(bits);
                        symbolicPreview.setText(getSymbolicString());
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
        previewRow.addView(numericInput);

        TextView closeParen = new TextView(context);
        closeParen.setText(")");
        closeParen.setTypeface(Typeface.MONOSPACE);
        closeParen.setTextSize(15);
        closeParen.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        previewRow.addView(closeParen);

        root.addView(previewRow);

        View.OnClickListener permListener = v -> {
            symbolicPreview.setText(getSymbolicString());
            numericInput.setText(String.valueOf(getNumericValue()));
        };
        for (CheckBox cb : new CheckBox[]{ownerRead, ownerWrite, ownerExec, groupRead, groupWrite, groupExec,
                otherRead, otherWrite, otherExec, setUid, setGid, sticky}) {
            cb.setOnClickListener(permListener);
        }

        return root;
    }

    public String getSymbolicString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ownerRead.isChecked() ? 'r' : '-');
        sb.append(ownerWrite.isChecked() ? 'w' : '-');
        if (setUid.isChecked() && ownerExec.isChecked()) sb.append('s');
        else if (ownerExec.isChecked()) sb.append('x');
        else sb.append(setUid.isChecked() ? 'S' : '-');

        sb.append(groupRead.isChecked() ? 'r' : '-');
        sb.append(groupWrite.isChecked() ? 'w' : '-');
        if (setGid.isChecked() && groupExec.isChecked()) sb.append('s');
        else if (groupExec.isChecked()) sb.append('x');
        else sb.append(setGid.isChecked() ? 'S' : '-');

        sb.append(otherRead.isChecked() ? 'r' : '-');
        sb.append(otherWrite.isChecked() ? 'w' : '-');
        if (sticky.isChecked() && otherExec.isChecked()) sb.append('t');
        else if (otherExec.isChecked()) sb.append('x');
        else sb.append(sticky.isChecked() ? 'T' : '-');

        return sb.toString();
    }

    public int getNumericValue() {
        int owner = (ownerRead.isChecked() ? 4 : 0) | (ownerWrite.isChecked() ? 2 : 0) | (ownerExec.isChecked() ? 1 : 0);
        int group = (groupRead.isChecked() ? 4 : 0) | (groupWrite.isChecked() ? 2 : 0) | (groupExec.isChecked() ? 1 : 0);
        int other = (otherRead.isChecked() ? 4 : 0) | (otherWrite.isChecked() ? 2 : 0) | (otherExec.isChecked() ? 1 : 0);
        int special = (setUid.isChecked() ? 4 : 0) | (setGid.isChecked() ? 2 : 0) | (sticky.isChecked() ? 1 : 0);
        return special * 1000 + owner * 100 + group * 10 + other;
    }

    public static String numericToSymbolic(String perms) {
        int[] bits = parseNumericPerms(perms);
        StringBuilder sb = new StringBuilder();
        sb.append((bits[0] & 4) != 0 ? 'r' : '-');
        sb.append((bits[0] & 2) != 0 ? 'w' : '-');
        if ((bits[3] & 4) != 0 && (bits[0] & 1) != 0) sb.append('s');
        else if ((bits[0] & 1) != 0) sb.append('x');
        else sb.append((bits[3] & 4) != 0 ? 'S' : '-');

        sb.append((bits[1] & 4) != 0 ? 'r' : '-');
        sb.append((bits[1] & 2) != 0 ? 'w' : '-');
        if ((bits[3] & 2) != 0 && (bits[1] & 1) != 0) sb.append('s');
        else if ((bits[1] & 1) != 0) sb.append('x');
        else sb.append((bits[3] & 2) != 0 ? 'S' : '-');

        sb.append((bits[2] & 4) != 0 ? 'r' : '-');
        sb.append((bits[2] & 2) != 0 ? 'w' : '-');
        if ((bits[3] & 1) != 0 && (bits[2] & 1) != 0) sb.append('t');
        else if ((bits[2] & 1) != 0) sb.append('x');
        else sb.append((bits[3] & 1) != 0 ? 'T' : '-');

        return sb.toString();
    }

    public static int[] parseNumericPerms(String perms) {
        int[] bits = new int[5];
        if (perms == null) return bits;
        String clean = perms.trim().replace(" ", "");
        // Index of the first digit that maps to the owner octal. A 4-digit mode
        // has a leading special-bits digit that must not be read as a digit group.
        int offset;
        if (clean.length() == 3) offset = 0;
        else if (clean.length() == 4) offset = 1;
        else return bits;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c < '0' || c > '7') return new int[5];
            int target = i - offset;
            if (target < 0) {
                bits[3] = c - '0';
            } else if (target <= 2) {
                bits[target] = c - '0';
            } else {
                return new int[5];
            }
        }
        bits[4] = bits[3] * 1000 + bits[0] * 100 + bits[1] * 10 + bits[2];
        return bits;
    }

    private void setCheckboxesFromBits(int[] bits) {
        ownerRead.setChecked((bits[0] & 4) != 0);
        ownerWrite.setChecked((bits[0] & 2) != 0);
        ownerExec.setChecked((bits[0] & 1) != 0);
        groupRead.setChecked((bits[1] & 4) != 0);
        groupWrite.setChecked((bits[1] & 2) != 0);
        groupExec.setChecked((bits[1] & 1) != 0);
        otherRead.setChecked((bits[2] & 4) != 0);
        otherWrite.setChecked((bits[2] & 2) != 0);
        otherExec.setChecked((bits[2] & 1) != 0);
        setUid.setChecked((bits[3] & 4) != 0);
        setGid.setChecked((bits[3] & 2) != 0);
        sticky.setChecked((bits[3] & 1) != 0);
    }

    private CheckBox createPermCheckbox(int checkedBit) {
        CheckBox cb = new CheckBox(context);
        cb.setChecked(checkedBit != 0);
        cb.setMinWidth(0);
        cb.setMinimumWidth(0);
        cb.setMinHeight(0);
        cb.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        cb.setLayoutParams(lp);
        return cb;
    }

    private LinearLayout createPermRow(String label, CheckBox read, CheckBox write, CheckBox exec) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTypeface(Typeface.MONOSPACE);
        labelView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        labelView.setPadding(dp(8), 0, dp(4), 0);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(labelView);
        row.addView(read);
        row.addView(write);
        row.addView(exec);
        return row;
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Keeps the numeric field to octal digits. Character.getNumericValue returns
     * -1 for anything else, so typing a non-digit used to flip random checkboxes.
     */
    private static InputFilter octalOnly() {
        return (source, start, end, dest, dstart, dend) -> {
            StringBuilder out = new StringBuilder(end - start);
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (c >= '0' && c <= '7') out.append(c);
            }
            return out;
        };
    }

    /** Rebinds the checkboxes to a mode read from disk after the editor is built. */
    public void applyNumericPerms(String perms) {
        if (perms == null || !isReady()) return;
        int[] bits = parseNumericPerms(perms);
        setCheckboxesFromBits(bits);
        if (symbolicPreview != null) symbolicPreview.setText(getSymbolicString());
        if (numericInput != null) numericInput.setText(String.valueOf(bits[4]));
    }

    public boolean isReady() {
        return ownerRead != null;
    }
}
