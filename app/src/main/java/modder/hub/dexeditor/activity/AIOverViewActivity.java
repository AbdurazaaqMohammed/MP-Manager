/*
 * Dex-Editor-Android an Advanced Dex Editor for Android
 * Copyright 2024-26, developer-krushna
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


 *     Please contact Krushna by email mt.modder.hub@gmail.com if you need
 *     additional information or have any questions
 */


package modder.hub.dexeditor.activity;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.activities.EditorSettingsActivity;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.noties.markwon.Markwon;
import modder.hub.dexeditor.fragment.SettingsFragment;
import modder.hub.dexeditor.views.AlertCircularProgress;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/*
Author @developer-krushna
Code fixed comments by ChatGPT
*/

public class AIOverViewActivity extends BaseActivity {
    private static final long DOUBLE_PRESS_INTERVAL = 2000;
    private AppBarLayout appBarLayout;
    private CoordinatorLayout coordinatorLayout;
    private Toolbar toolbar;
    private TextView markdownText;
    private ScrollView scrollView;
    private String smaliCode = "";
    private String GEMINI_API_KEY = "";
    private String GEMINI_MODEL = "gemini-3.5-flash-lite";
    private AlertCircularProgress progressDialog;
    private final String DefaultMsg = "Analyze this Smali method and explain the register usage:\n\nMethod:\n";
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ai_overview_activity);
        initialize(savedInstanceState);
        initializeLogic();

    }

    private void initialize(Bundle savedInstanceState) {
        appBarLayout = findViewById(R.id._app_bar);
        coordinatorLayout = findViewById(R.id._coordinator);
        toolbar = findViewById(R.id._toolbar);
        setSupportActionBar(toolbar);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        toolbar.setNavigationOnClickListener(view -> onBackPressed());

        markdownText = findViewById(R.id.markdownText);
        scrollView = findViewById(R.id.scrollView);
    }

    private void initializeLogic() {
        setTitle(R.string.ai_explanation);
        markdownText.setTextIsSelectable(true);
        smaliCode = getIntent().getStringExtra("smali");
        GEMINI_API_KEY = SettingsFragment.getChatGptApiKey(this);
        GEMINI_MODEL = SettingsFragment.getGeminiModel(this);
        markdownText.setTextColor(MaterialColors.getColor(markdownText, android.R.attr.textColorPrimary));
        if (TextUtils.isEmpty(GEMINI_API_KEY)) {
            Intent settingsIntent = new Intent(AIOverViewActivity.this, EditorSettingsActivity.class);
            settingsIntent.putExtra("from_dex", true);
            startActivity(settingsIntent);
            return;
        }
        if (getSharedPreferences("editor_prefs", MODE_PRIVATE).getBoolean("ai_disclaimer_accepted", false)) {
            runAiRequest();
        } else {
            showAiDisclaimerDialog();
        }
    }

    private void runAiRequest() {
        boolean allowEditPrompt = SettingsFragment.shouldEditAiPrompt(this);
        if (allowEditPrompt) {
            showPromptEditorDialog();
        } else {
            startAnalysis(DefaultMsg + smaliCode);
        }
    }

    private void showAiDisclaimerDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ai_usage_disclaimer)
                .setMessage(R.string.ai_warning)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setPositiveButton(R.string.agree, (dialog, which) -> {
                    getSharedPreferences("editor_prefs", MODE_PRIVATE).edit().putBoolean("ai_disclaimer_accepted", true).apply();
                    runAiRequest();
                })
                .show();
    }

    private void showPromptEditorDialog() {
        final EditText input = new EditText(this);
        input.setHint("Enter your custom prompt");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        FrameLayout container = new FrameLayout(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = padding;
        params.topMargin = padding;
        params.rightMargin = padding;
        params.bottomMargin = padding;
        TextInputLayout boxed =
                UiFields.wrap(this, input, null, 0);
        boxed.setLayoutParams(params);
        container.addView(boxed);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Prompt")
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    String customPrompt = input.getText().toString().trim();
                    if (customPrompt.isEmpty()) {
                        customPrompt = DefaultMsg + smaliCode;
                    } else {
                        customPrompt = customPrompt + "\n" + smaliCode;
                    }
                    startAnalysis(customPrompt);
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> finish())
                .create();

        dialog.show();
    }

    @SuppressLint("SetTextI18n")
    private void startAnalysis(String prompt) {
        progressDialogShow(true);
        if(Build.VERSION.SDK_INT < 20) return;
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        try {
            // Build the request JSON
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();

            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            Request request = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + GEMINI_API_KEY)
                    .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            progressDialogShow(false);
                            markdownText.setText("Network error: " + e.getMessage());
                        }
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body().string();
                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                if (jsonResponse.has("candidates")) {
                                    String generatedText = jsonResponse.getJSONArray("candidates")
                                            .getJSONObject(0)
                                            .getJSONObject("content")
                                            .getJSONArray("parts")
                                            .getJSONObject(0)
                                            .getString("text");

                                    renderMarkdown(generatedText);
                                } else {
                                    markdownText.setText("Error: No candidates in response");
                                }
                            } catch (Exception e) {
                                markdownText.setText("Error parsing response");
                            }
                            progressDialogShow(false);
                        }
                    });
                }
            });
        } catch (JSONException e) {
            progressDialogShow(false);
            markdownText.setText("Error creating request");
        }
    }

    private void renderMarkdown(String content) {
        Markwon markwon = Markwon.create(AIOverViewActivity.this);
        markwon.setMarkdown(markdownText, content);
    }

    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime < DOUBLE_PRESS_INTERVAL) {
            super.onBackPressed();
        } else {
            lastBackPressTime = currentTime;
            Extensions.showMessage(this, "Press back again to exit");
        }
    }

    private void progressDialogShow(boolean show) {
        if (show) {
            if (progressDialog == null) {
                progressDialog = new AlertCircularProgress(this);
            }
            progressDialog.setMessage("Generating...");
            progressDialog.show();
        } else if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }
}