package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

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
        root.setPadding(dp(8), dp(8), dp(8), 0);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.permissions);
        sectionTitle.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        sectionTitle.setPadding(0, dp(4), 0, dp(8));
        root.addView(sectionTitle);

        int[] permBits = parseNumericPerms(currentPerms);

        TextView colHeader = new TextView(context);
        colHeader.setText("        Read    Write   Exec");
        colHeader.setTypeface(Typeface.MONOSPACE);
        colHeader.setTextSize(12);
        colHeader.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        colHeader.setPadding(dp(8), 0, 0, dp(4));
        root.addView(colHeader);

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

        setUid = new CheckBox(context);
        setUid.setText(context.getString(R.string.set_uid));
        setUid.setTextSize(12);
        setUid.setChecked(permBits[3] == 4);
        setUid.setPadding(dp(4), 0, dp(16), 0);

        setGid = new CheckBox(context);
        setGid.setText(context.getString(R.string.set_gid));
        setGid.setTextSize(12);
        setGid.setChecked(permBits[3] == 2);
        setGid.setPadding(dp(4), 0, dp(16), 0);

        sticky = new CheckBox(context);
        sticky.setText(context.getString(R.string.sticky));
        sticky.setTextSize(12);
        sticky.setChecked(permBits[3] == 1);
        sticky.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout specialRow = new LinearLayout(context);
        specialRow.setOrientation(LinearLayout.HORIZONTAL);
        specialRow.setGravity(Gravity.CENTER_VERTICAL);
        specialRow.setPadding(0, dp(4), 0, dp(4));
        specialRow.addView(setUid);
        specialRow.addView(setGid);
        specialRow.addView(sticky);
        root.addView(specialRow);

        symbolicPreview = new TextView(context);
        symbolicPreview.setText(getSymbolicString());
        symbolicPreview.setTypeface(Typeface.MONOSPACE);
        symbolicPreview.setTextSize(14);
        symbolicPreview.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        symbolicPreview.setPadding(0, dp(8), 0, dp(4));
        root.addView(symbolicPreview);

        LinearLayout numericRow = new LinearLayout(context);
        numericRow.setOrientation(LinearLayout.HORIZONTAL);
        numericRow.setGravity(Gravity.CENTER_VERTICAL);
        numericRow.setPadding(0, dp(4), 0, dp(4));
        TextView numLabel = new TextView(context);
        numLabel.setText(context.getString(R.string.numeric));
        numLabel.setTextSize(13);
        numericInput = new EditText(context);
        numericInput.setTextSize(14);
        numericInput.setTypeface(Typeface.MONOSPACE);
        numericInput.setText(String.valueOf(permBits[4]));
        numericInput.setSelectAllOnFocus(true);
        numericInput.setFilters(new android.text.InputFilter[]{ new android.text.InputFilter.LengthFilter(4) });
        numericInput.setLayoutParams(new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));
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
        numericRow.addView(numLabel);
        numericRow.addView(numericInput);
        root.addView(numericRow);

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

    private int[] parseNumericPerms(String perms) {
        int[] bits = new int[5];
        if (perms == null || perms.isEmpty()) return bits;
        try {
            String clean = perms.trim().replace(" ", "");
            if (clean.length() == 3) {
                bits[0] = Character.getNumericValue(clean.charAt(0));
                bits[1] = Character.getNumericValue(clean.charAt(1));
                bits[2] = Character.getNumericValue(clean.charAt(2));
                bits[3] = 0;
                bits[4] = bits[0] * 100 + bits[1] * 10 + bits[2];
            } else if (clean.length() == 4) {
                bits[3] = Character.getNumericValue(clean.charAt(0));
                bits[0] = Character.getNumericValue(clean.charAt(1));
                bits[1] = Character.getNumericValue(clean.charAt(2));
                bits[2] = Character.getNumericValue(clean.charAt(3));
                bits[4] = bits[3] * 1000 + bits[0] * 100 + bits[1] * 10 + bits[2];
            }
        } catch (NumberFormatException e) {
            bits = new int[5];
        }
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
        setUid.setChecked(bits[3] == 4);
        setGid.setChecked(bits[3] == 2);
        sticky.setChecked(bits[3] == 1);
    }

    private CheckBox createPermCheckbox(int checkedBit) {
        CheckBox cb = new CheckBox(context);
        cb.setChecked(checkedBit != 0);
        cb.setPadding(0, 0, dp(8), 0);
        cb.setMinWidth(0);
        cb.setMinimumWidth(0);
        cb.setMinHeight(0);
        cb.setMinimumHeight(0);
        cb.setLayoutParams(new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
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
        labelView.setLayoutParams(new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(labelView);
        row.addView(read);
        row.addView(write);
        row.addView(exec);
        return row;
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
