package io.github.abdurazaaqmohammed.adapters;

import android.content.Context;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;

public class SignListAdapter extends ArrayAdapter<CharSequence> {
    private final Context context;
    private final CharSequence[] items;
    public boolean v1;
    public boolean v2;
    public boolean v3;
    public boolean v4;

    public SignListAdapter(Context context, CharSequence[] items, boolean v1, boolean v2, boolean v3, boolean v4) {
        super(context, R.layout.custom_list_item, items);
        this.context = context;
        this.items = items;
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.v4 = v4;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.custom_list_item, parent, false);
        }

        TextView textView = convertView.findViewById(R.id.item_text);
        CheckBox checkBox = convertView.findViewById(R.id.item_checkbox);

        textView.setText(items[position]);

        if (position == 0) {
            checkBox.setVisibility(View.GONE);
            convertView.setOnClickListener(v -> {
                EditText pathInput = new EditText(context);
                EditText pwInput = new EditText(context);
                pathInput.setHint("Enter path to signature key");
                pwInput.setHint("Enter password");

                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(48, 24, 48, 24);

                pwInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

                layout.addView(pathInput);
                TextView warning = new TextView(context);
                warning.setText("Warning: The password will be saved in plain text.");
                layout.addView(warning);
                layout.addView(pwInput);

                new MaterialAlertDialogBuilder(context)
                        .setView(layout)
                        .setPositiveButton("OK", (dialogInterface, i) -> {
                            String path = pathInput.getText().toString();
                            String password = pwInput.getText().toString();
                            if (TextUtils.isEmpty(path)) {
                                Toast.makeText(context, "No path entered", Toast.LENGTH_SHORT).show();
                            } else if (TextUtils.isEmpty(password)) {
                                Toast.makeText(context, "No password entered", Toast.LENGTH_SHORT).show();
                            } else if (!new File(path).exists()) {
                                Toast.makeText(context, "Invalid file path", Toast.LENGTH_SHORT).show();
                            } else {
                                LegacyUtils.applySharedPrefEditor(PreferenceManager.getDefaultSharedPreferences(context).edit()
                                        .putString("keyPath", path)
                                        .putString("signatureKeyPassword", password));
                                Toast.makeText(context, "Signature file set", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        } else {
            checkBox.setVisibility(View.VISIBLE);
            switch (position) {
                case 1: checkBox.setChecked(v1); break;
                case 2: checkBox.setChecked(v2); break;
                case 3: checkBox.setChecked(v3); break;
                case 4: checkBox.setChecked(v4); break;
            }
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                switch (position) {
                    case 1: v1 = isChecked; break;
                    case 2: v2 = isChecked; break;
                    case 3: v3 = isChecked; break;
                    case 4: v4 = isChecked; break;
                }
            });
            convertView.setOnClickListener(v -> checkBox.toggle());
        }

        return convertView;
    }
}