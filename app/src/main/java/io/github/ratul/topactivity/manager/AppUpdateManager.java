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
package io.github.ratul.topactivity.manager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.multidex.BuildConfig;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import io.github.ratul.topactivity.App;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.extensions.ActivityExtensions;

public class AppUpdateManager {

    private final AppCompatActivity activity;

    public AppUpdateManager(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void checkForUpdate(boolean silent) {
        try {
            Volley.newRequestQueue(activity).add(buildVersionCheckRequest(silent));
        } catch (Exception e) {
            handleUpdateError(silent);
        }
    }

    private JsonObjectRequest buildVersionCheckRequest(boolean silent) {
        String url = App.API_URL + "/releases/latest";
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        processUpdateResponse(response, silent);
                    } catch (Exception e) {
                        handleUpdateError(silent);
                    }
                },
                error -> handleUpdateError(silent)
        );
        request.setShouldRetryConnectionErrors(true);
        request.setShouldCache(false);
        return request;
    }

    private void handleUpdateError(boolean silent) {
        if (!silent) {
            ActivityExtensions.showMessage(activity, R.string.update_check_failed);
            ActivityExtensions.openLink(activity, App.REPO_URL + "/releases/latest");
        }
    }

    private void processUpdateResponse(JSONObject response, boolean silent) {
        String tag = response.optString("tag_name");
        int serverVersion = Integer.parseInt(tag.replaceAll("[^0-9]", ""));
        int currentVersion = Integer.parseInt(BuildConfig.VERSION_NAME.replaceAll("[^0-9]", ""));

        if (serverVersion > currentVersion) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.update_available)
                    .setMessage(activity.getString(R.string.new_version_available, tag))
                    .setPositiveButton(R.string.download, (dialog, which) -> {
                        ActivityExtensions.openLink(activity, App.REPO_URL + "/releases/tag/" + tag);
                        dialog.dismiss();
                    })
                    .setNeutralButton(R.string.later, (dialog, which) -> dialog.dismiss())
                    .show();
        } else if (!silent) {
            ActivityExtensions.showMessage(activity, R.string.already_using_latest);
        }
    }
}
