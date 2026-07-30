package io.github.abdurazaaqmohammed.ui.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import io.github.abdurazaaqmohammed.MPManager.R;
import modder.hub.dexeditor.activity.EditFloatingMenusActivity;

public class EditorSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.SharedPreferences settings = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        setTheme(settings.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light));


        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Editor Settings");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("editor_prefs");
            setPreferencesFromResource(R.xml.editor_preferences, rootKey);

            androidx.preference.Preference floatingMenu = findPreference("edit_floating_menus");
            if (floatingMenu != null) {
                floatingMenu.setOnPreferenceClickListener(pref -> {
                    startActivity(new android.content.Intent(getActivity(), EditFloatingMenusActivity.class));
                    return true;
                });
            }
        }

        @Override
        public boolean onPreferenceTreeClick(androidx.preference.Preference preference) {
            String key = preference.getKey();
            if ("pref_bottom_bar_buttons".equals(key)) {
                showBottomBarManagementDialog();
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        private void showBottomBarManagementDialog() {
            android.content.SharedPreferences prefs = android.preference.PreferenceManager
                    .getDefaultSharedPreferences(getContext());
            String json = prefs.getString("pref_bottom_bar_buttons", "[]");
            org.json.JSONArray array;
            try {
                if (json.equals("Search,Copy,Cut,Paste")) {
                    array = new org.json.JSONArray();
                    array.put(new org.json.JSONObject().put("action", "Search").put("label", "Search"));
                    array.put(new org.json.JSONObject().put("action", "Copy selection").put("label", "Copy"));
                    array.put(new org.json.JSONObject().put("action", "Cut selection").put("label", "Cut"));
                    array.put(new org.json.JSONObject().put("action", "Paste selection").put("label", "Paste"));
                } else {
                    array = new org.json.JSONArray(json);
                }
            } catch (Exception e) {
                array = new org.json.JSONArray();
            }

            final org.json.JSONArray finalArray = array;
            android.widget.ListView listView = new android.widget.ListView(getContext());
            io.github.abdurazaaqmohammed.adapters.BottomBarButtonAdapter adapter = new io.github.abdurazaaqmohammed.adapters.BottomBarButtonAdapter(
                    getContext(), finalArray,
                    new io.github.abdurazaaqmohammed.adapters.BottomBarButtonAdapter.OnButtonActionListener() {
                        @Override
                        public void onEdit(int position, org.json.JSONObject button) {
                            showAddEditButtonDialog(finalArray, position, button, () -> {
                                prefs.edit().putString("pref_bottom_bar_buttons", finalArray.toString()).apply();
                                showBottomBarManagementDialog(); // Refresh - ideally use a better way but this is simple
                            });
                        }

                        @Override
                        public void onDelete(int position) {
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                                    .setTitle("Delete Button")
                                    .setMessage("Are you sure you want to delete this button?")
                                    .setPositiveButton("Delete", (dialog, which) -> {
                                        finalArray.remove(position);
                                        prefs.edit().putString("pref_bottom_bar_buttons", finalArray.toString())
                                                .apply();
                                        showBottomBarManagementDialog(); // Refresh
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    });
            listView.setAdapter(adapter);

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                    .setTitle("Manage Bottom Bar")
                    .setView(listView)
                    .setPositiveButton("Add New", (dialog, which) -> showAddEditButtonDialog(finalArray, -1, null, () -> {
                        prefs.edit().putString("pref_bottom_bar_buttons", finalArray.toString()).apply();
                        showBottomBarManagementDialog(); // Refresh
                    }))
                    .setNegativeButton("Close", null)
                    .show();
        }

        private void showAddEditButtonDialog(org.json.JSONArray array, int position, org.json.JSONObject existing,
                Runnable onComplete) {
            String[] actions = {
                    "None", "Search", "Insert text", "Regex find and replace", "Copy selection", "Cut selection",
                    "Paste selection", "Copy line", "Cut line", "Delete line", "Empty line", "Replace line"
            };

            android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
            android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(48, 16, 48, 16);
            scrollView.addView(layout);

            android.widget.EditText labelInput = new android.widget.EditText(getContext());
            labelInput.setHint("Button Label (optional)");
            layout.addView(labelInput);

            android.widget.TextView clickHeader = new android.widget.TextView(getContext());
            clickHeader.setText("Click Action");
            clickHeader.setPadding(0, 32, 0, 8);
            layout.addView(clickHeader);

            android.widget.Spinner actionSpinner = new android.widget.Spinner(getContext());
            android.widget.ArrayAdapter<String> actionAdapter = new android.widget.ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_item, actions);
            actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            actionSpinner.setAdapter(actionAdapter);
            layout.addView(actionSpinner);

            android.widget.EditText dataInput1 = new android.widget.EditText(getContext());
            dataInput1.setHint("Data 1");
            dataInput1.setVisibility(android.view.View.GONE);
            layout.addView(dataInput1);

            android.widget.EditText dataInput2 = new android.widget.EditText(getContext());
            dataInput2.setHint("Data 2");
            dataInput2.setVisibility(android.view.View.GONE);
            layout.addView(dataInput2);

            setupActionSpinner(actionSpinner, dataInput1, dataInput2, actions);

            android.widget.TextView longHeader = new android.widget.TextView(getContext());
            longHeader.setText("Long Press Action");
            longHeader.setPadding(0, 32, 0, 8);
            layout.addView(longHeader);

            android.widget.Spinner longActionSpinner = new android.widget.Spinner(getContext());
            longActionSpinner.setAdapter(actionAdapter);
            layout.addView(longActionSpinner);

            android.widget.EditText longDataInput1 = new android.widget.EditText(getContext());
            longDataInput1.setHint("Long Data 1");
            longDataInput1.setVisibility(android.view.View.GONE);
            layout.addView(longDataInput1);

            android.widget.EditText longDataInput2 = new android.widget.EditText(getContext());
            longDataInput2.setHint("Long Data 2");
            longDataInput2.setVisibility(android.view.View.GONE);
            layout.addView(longDataInput2);

            setupActionSpinner(longActionSpinner, longDataInput1, longDataInput2, actions);

            if (existing != null) {
                labelInput.setText(existing.optString("label", ""));

                String action = existing.optString("action");
                for (int i = 0; i < actions.length; i++)
                    if (actions[i].equals(action))
                        actionSpinner.setSelection(i);
                dataInput1.setText(existing.optString("data1", ""));
                dataInput2.setText(existing.optString("data2", ""));

                String longAction = existing.optString("longAction");
                for (int i = 0; i < actions.length; i++)
                    if (actions[i].equals(longAction))
                        longActionSpinner.setSelection(i);
                longDataInput1.setText(existing.optString("longData1", ""));
                longDataInput2.setText(existing.optString("longData2", ""));
            }

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                    .setTitle(existing == null ? "Add Button" : "Edit Button")
                    .setView(scrollView)
                    .setPositiveButton("Save", (dialog, which) -> {
                        try {
                            org.json.JSONObject obj = existing != null ? existing : new org.json.JSONObject();
                            String label = labelInput.getText().toString();
                            if (!android.text.TextUtils.isEmpty(label))
                                obj.put("label", label);
                            else
                                obj.remove("label");

                            String action = actionSpinner.getSelectedItem().toString();
                            if (action.equals("None"))
                                obj.remove("action");
                            else
                                obj.put("action", action);
                            obj.put("data1", dataInput1.getText().toString());
                            obj.put("data2", dataInput2.getText().toString());

                            String longAction = longActionSpinner.getSelectedItem().toString();
                            if (longAction.equals("None"))
                                obj.remove("longAction");
                            else
                                obj.put("longAction", longAction);
                            obj.put("longData1", longDataInput1.getText().toString());
                            obj.put("longData2", longDataInput2.getText().toString());

                            if (existing == null)
                                array.put(obj);
                            onComplete.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void setupActionSpinner(android.widget.Spinner spinner, android.widget.EditText data1,
                android.widget.EditText data2, String[] actions) {
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos,
                        long id) {
                    String action = actions[pos];
                    if (action.equals("Insert text")) {
                        data1.setVisibility(android.view.View.VISIBLE);
                        data1.setHint("Text to insert");
                        data2.setVisibility(android.view.View.GONE);
                    } else if (action.equals("Regex find and replace")) {
                        data1.setVisibility(android.view.View.VISIBLE);
                        data1.setHint("Find Regex");
                        data2.setVisibility(android.view.View.VISIBLE);
                        data2.setHint("Replace Regex");
                    } else {
                        data1.setVisibility(android.view.View.GONE);
                        data2.setVisibility(android.view.View.GONE);
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }
    }
}
