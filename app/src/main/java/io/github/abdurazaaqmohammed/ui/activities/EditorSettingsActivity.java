package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.BottomBarButtonAdapter;
import modder.hub.dexeditor.activity.EditFloatingMenusActivity;

public class EditorSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences settings = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(settings.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light));


        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.editor_settings);
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

            Preference floatingMenu = findPreference("edit_floating_menus");
            if (floatingMenu != null) {
                floatingMenu.setOnPreferenceClickListener(pref -> {
                    startActivity(new Intent(getActivity(), EditFloatingMenusActivity.class));
                    return true;
                });
            }
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            String key = preference.getKey();
            if ("pref_bottom_bar_buttons".equals(key)) {
                showBottomBarManagementDialog();
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        private void showBottomBarManagementDialog() {
            SharedPreferences prefs = android.preference.PreferenceManager
                    .getDefaultSharedPreferences(getContext());
            String json = prefs.getString("pref_bottom_bar_buttons", "[]");
            JSONArray array;
            try {
                if (json.equals("Search,Copy,Cut,Paste")) {
                    array = new JSONArray();
                    array.put(new JSONObject().put("action", "Search").put("label", "Search"));
                    array.put(new JSONObject().put("action", "Copy selection").put("label", "Copy"));
                    array.put(new JSONObject().put("action", "Cut selection").put("label", "Cut"));
                    array.put(new JSONObject().put("action", "Paste selection").put("label", "Paste"));
                } else {
                    array = new JSONArray(json);
                }
            } catch (Exception e) {
                array = new JSONArray();
            }

            final JSONArray finalArray = array;
            ListView listView = new ListView(getContext());
            BottomBarButtonAdapter adapter = new BottomBarButtonAdapter(
                    getContext(), finalArray,
                    new BottomBarButtonAdapter.OnButtonActionListener() {
                        @Override
                        public void onEdit(int position, JSONObject button) {
                            showAddEditButtonDialog(finalArray, position, button, () -> {
                                prefs.edit().putString("pref_bottom_bar_buttons", finalArray.toString()).apply();
                                showBottomBarManagementDialog(); // Refresh - ideally use a better way but this is simple
                            });
                        }

                        @Override
                        public void onDelete(int position) {
                            new MaterialAlertDialogBuilder(getContext())
                                    .setTitle(R.string.delete_button)
                                    .setMessage(getString(R.string.confirm_delete_f, "this button"))
                                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                                        finalArray.remove(position);
                                        prefs.edit().putString("pref_bottom_bar_buttons", finalArray.toString()).apply();
                                        showBottomBarManagementDialog(); // Refresh
                                    })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                        }
                    });
            listView.setAdapter(adapter);

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.manage_bottom_bar)
                    .setView(listView)
                    .setPositiveButton(R.string.add, (dialog, which) -> showAddEditButtonDialog(finalArray, -1, null, () -> {
                        prefs.edit().putString("pref_bottom_bar_buttons", finalArray.toString()).apply();
                        showBottomBarManagementDialog(); // Refresh
                    }))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showAddEditButtonDialog(JSONArray array, int position, JSONObject existing,
                                             Runnable onComplete) {
            String[] actions = {
                    "None", "Search", "Insert text", "Regex find and replace", "Copy selection", "Cut selection",
                    "Paste selection", "Copy line", "Cut line", "Delete line", "Empty line", "Replace line"
            };

            ScrollView scrollView = new ScrollView(getContext());
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(48, 16, 48, 16);
            scrollView.addView(layout);

            EditText labelInput = new EditText(getContext());
            labelInput.setHint("Button Label (optional)");
            layout.addView(labelInput);

            TextView clickHeader = new TextView(getContext());
            clickHeader.setText(getContext().getString(R.string.edsettings_click));
            clickHeader.setPadding(0, 32, 0, 8);
            layout.addView(clickHeader);

            Spinner actionSpinner = new Spinner(getContext());
            ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_item, actions);
            actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            actionSpinner.setAdapter(actionAdapter);
            layout.addView(actionSpinner);

            EditText dataInput1 = new EditText(getContext());
            dataInput1.setHint("Data 1");
            dataInput1.setVisibility(View.GONE);
            layout.addView(dataInput1);

            EditText dataInput2 = new EditText(getContext());
            dataInput2.setHint("Data 2");
            dataInput2.setVisibility(View.GONE);
            layout.addView(dataInput2);

            setupActionSpinner(actionSpinner, dataInput1, dataInput2, actions);

            TextView longHeader = new TextView(getContext());
            longHeader.setText(getContext().getString(R.string.edsettings_long));
            longHeader.setPadding(0, 32, 0, 8);
            layout.addView(longHeader);

            Spinner longActionSpinner = new Spinner(getContext());
            longActionSpinner.setAdapter(actionAdapter);
            layout.addView(longActionSpinner);

            EditText longDataInput1 = new EditText(getContext());
            longDataInput1.setHint("Long Data 1");
            longDataInput1.setVisibility(View.GONE);
            layout.addView(longDataInput1);

            EditText longDataInput2 = new EditText(getContext());
            longDataInput2.setHint("Long Data 2");
            longDataInput2.setVisibility(View.GONE);
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

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(existing == null ? getContext().getString(R.string.edsettings_add) : getContext().getString(R.string.edsettings_edit))
                    .setView(scrollView)
                    .setPositiveButton(getContext().getString(R.string.save), (dialog, which) -> {
                        try {
                            JSONObject obj = existing != null ? existing : new JSONObject();
                            String label = labelInput.getText().toString();
                            if (!TextUtils.isEmpty(label))
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
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void setupActionSpinner(Spinner spinner, EditText data1,
                                        EditText data2, String[] actions) {
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos,
                                           long id) {
                    String action = actions[pos];
                    if (action.equals("Insert text")) {
                        data1.setVisibility(View.VISIBLE);
                        data1.setHint("Text to insert");
                        data2.setVisibility(View.GONE);
                    } else if (action.equals("Regex find and replace")) {
                        data1.setVisibility(View.VISIBLE);
                        data1.setHint("Find Regex");
                        data2.setVisibility(View.VISIBLE);
                        data2.setHint("Replace Regex");
                    } else {
                        data1.setVisibility(View.GONE);
                        data2.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }
}
