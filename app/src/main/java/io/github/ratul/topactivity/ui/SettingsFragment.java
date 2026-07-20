/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.ui;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.repository.DataRepository;

public class SettingsFragment extends PreferenceFragmentCompat {

    public ListPreference serviceMode;
    public SwitchPreferenceCompat useAccessibility;
    public ListPreference scanSpeed;
    public ListPreference historySize;
    public ListPreference windowSize;
    public Preference enableAutostart;
    public SwitchPreferenceCompat autoUpdate;
    public SwitchPreferenceCompat useSystemFont;
    public Preference checkUpdate;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        serviceMode = findPreference("service_mode");
        useAccessibility = findPreference("use_accessibility");
        scanSpeed = findPreference("scan_speed");
        historySize = findPreference("history_size");
        windowSize = findPreference("window_size");
        enableAutostart = findPreference("enable_autostart");
        autoUpdate = findPreference("auto_update");
        useSystemFont = findPreference("system_font");
        checkUpdate = findPreference("check_update");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setVerticalScrollBarEnabled(false);
        getListView().setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);
    }

    @Override
    public void onStart() {
        super.onStart();

        FragmentActivity attachedActivity = getActivity();
        if (attachedActivity == null) return;
        useSystemFont.setOnPreferenceChangeListener(new RestartOnChange(attachedActivity));
    }

    public static class RestartOnChange implements Preference.OnPreferenceChangeListener {

        private final FragmentActivity activity;

        public RestartOnChange(FragmentActivity activity) {
            this.activity = activity;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            Snackbar.make(
                    activity.getWindow().getDecorView().getRootView(),
                    R.string.restart_required,
                    Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.restart, v -> {
                DataRepository.getInstance().updateStatus(false);
                activity.finishAffinity();
            }).show();
            return true;
        }
    }
}
