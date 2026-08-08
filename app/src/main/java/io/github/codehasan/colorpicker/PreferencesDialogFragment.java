package io.github.codehasan.colorpicker;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;

public class PreferencesDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireActivity())
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setView(R.layout.color_picker_settings)
                .create();
    }

    public static class PreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.color_picker_preferences, rootKey);
        }
    }
}
