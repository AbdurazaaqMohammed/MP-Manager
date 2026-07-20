/*
 * Dex-Editor-Android an Advanced Dex Editor for Android
 * Copyright 2024-25, developer-krushna
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of developer-krushna nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package modder.hub.dexeditor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import io.github.abdurazaaqmohammed.MPManager.R;
import modder.hub.dexeditor.activity.DexEditorActivity;
import modder.hub.dexeditor.utils.DexFileSelector;
import modder.hub.dexeditor.utils.FileUtil;

public class MainActivity extends AppCompatActivity {
    // UI Components
    private Toolbar toolbar;
    private EditText dexFilePathEditText;
    private MaterialButton pickDexFileButton;
    private MaterialButton openDexFileButton;
    private TextView githubLinkTextView;

    private SharedPreferences sharedPreferences;
    private final Intent dexEditorIntent = new Intent();
    private final Intent githubIntent = new Intent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initialize(savedInstanceState);
        disableUI();
    }

    private void disableUI() {
        pickDexFileButton.setEnabled(false);
        openDexFileButton.setEnabled(false);
        dexFilePathEditText.setEnabled(false);
    }

    private void enableUI() {
        pickDexFileButton.setEnabled(true);
        openDexFileButton.setEnabled(true);
        dexFilePathEditText.setEnabled(true);
    }

    // Initialize UI components and set up listeners
    private void initialize(Bundle ignoredSavedInstanceState) {
        toolbar = findViewById(R.id._toolbar);
        setSupportActionBar(toolbar);

        // Enable back button in the toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

      //  pickDexFileButton = findViewById(R.id.materialbutton1);
     //   openDexFileButton = findViewById(R.id.open_dex);
     //   githubLinkTextView = findViewById(R.id.textview1);
    //    dexFilePathEditText = findViewById(R.id.edit_path);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("pref", 0);

        // Set up button click listeners
        pickDexFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickDexFile(dexFilePathEditText, "Pick .dex file", "Pick", "dex");
            }
        });



        openDexFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dexFilePathEditText.getText().toString().isEmpty()){
                    return;
                }
                String initialPath = dexFilePathEditText.getText().toString();
                DexFileSelector dexSelector = new DexFileSelector(MainActivity.this, initialPath);
                dexSelector.setOnFilesSelectedListener(selectedFilePaths -> {
                    // Convert List<String> to ArrayList<String> (if needed for Intent)
                    ArrayList<String> filePathsArrayList = new ArrayList<String>(selectedFilePaths);

                    // Set up the Intent
                    dexEditorIntent.setClass(MainActivity.this, DexEditorActivity.class);
                    dexEditorIntent.putStringArrayListExtra("SelectedDexFiles", filePathsArrayList);

                    // Start the activity
                    startActivity(dexEditorIntent);
                });
                dexSelector.showDialog();


            }
        });

        githubLinkTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                githubIntent.setAction("android.intent.action.VIEW");
                githubIntent.setData(Uri.parse("https://github.com/developer-krushna/Dex-Editor-Android"));
                startActivity(githubIntent);
            }
        });
    }

    // Open a file picker dialog to select a .dex file
    public void pickDexFile(final TextView textView, String title, String positiveButtonText, String fileExtension) {
        final String lastPathKey = "LastPath";
        DialogProperties dialogProperties = new DialogProperties();
        dialogProperties.selection_mode = 0; // Single file selection
        dialogProperties.selection_type = 0; // File selection
        dialogProperties.root = new File(FileUtil.getExternalStorageDir());
        dialogProperties.error_dir = new File(FileUtil.getExternalStorageDir());

        // Set the initial directory based on the last selected path
        if (sharedPreferences.contains(lastPathKey)) {
            dialogProperties.offset = new File(sharedPreferences.getString(lastPathKey, ""));
        } else {
            dialogProperties.offset = new File(FileUtil.getExternalStorageDir());
        }

        dialogProperties.extensions = new String[]{fileExtension};

        FilePickerDialog filePickerDialog = new FilePickerDialog(this, dialogProperties);
        filePickerDialog.setTitle(title);
        filePickerDialog.setPositiveBtnName(positiveButtonText);
        filePickerDialog.setNegativeBtnName(getResources().getString(R.string.close));

        filePickerDialog.setDialogSelectionListener(new DialogSelectionListener() {
            @Override
            public void onSelectedFilePaths(String[] selectedFilePaths) {
                ArrayList<String> filePaths = new ArrayList<>(Arrays.asList(selectedFilePaths));
                textView.setText(filePaths.get(0)); // Set the selected file path to the TextView

                // Save the last selected directory path in SharedPreferences
                sharedPreferences.edit().putString(lastPathKey, new File(filePaths.get(0)).getParentFile().getAbsolutePath()).apply();
            }
        });

        filePickerDialog.show();
    }
}
