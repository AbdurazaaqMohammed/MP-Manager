package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ProfileManager {
    private static final String PREFS_NAME = "FtpProfiles";
    private static final String PROFILES_KEY = "profiles";
    private final String DEFAULT_SERVER_PROFILE;
    private final String DEFAULT_CLIENT_PROFILE;
    private final SharedPreferences prefs;
    private List<FtpProfile> profiles;
    private final Gson gson;

    public ProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        DEFAULT_SERVER_PROFILE = context.getString(R.string.default_server);
        DEFAULT_CLIENT_PROFILE = context.getString(R.string.default_client);
        gson = new Gson();
        loadProfiles();
        ensureDefaultProfiles();
    }

    private void loadProfiles() {
        String json = prefs.getString(PROFILES_KEY, null);
        Type type = new TypeToken<List<FtpProfile>>(){}.getType();
        profiles = json != null ? gson.fromJson(json, type) : new ArrayList<>();
    }

    private void saveProfiles() {
        String json = gson.toJson(profiles);
        prefs.edit().putString(PROFILES_KEY, json).apply();
    }

    private void ensureDefaultProfiles() {
        if (profiles.isEmpty()) {
            // Add default server profile
            profiles.add(new FtpProfile(
                    DEFAULT_SERVER_PROFILE,
                    "192.168.1.1",
                    2121,
                    "admin",
                    "admin",
                    true
            ));

            // Add default client profile
            profiles.add(new FtpProfile(
                    DEFAULT_CLIENT_PROFILE,
                    "192.168.1.1",
                    2121,
                    "admin",
                    "admin",
                    false
            ));

            saveProfiles();
        }
    }

    public void addProfile(FtpProfile profile) {
        profiles.add(profile);
        saveProfiles();
    }

    public void updateProfile(int index, FtpProfile profile) {
        if (index >= 0 && index < profiles.size()) {
            profiles.set(index, profile);
            saveProfiles();
        }
    }

    public void deleteProfile(int index) {
        if (profiles.size() > 1) { // Don't allow deleting the last profile
            profiles.remove(index);
            saveProfiles();
        }
    }

    public List<FtpProfile> getProfiles() {
        return new ArrayList<>(profiles); // Return a copy to prevent external modification
    }

    public FtpProfile getProfile(int index) {
        if (index >= 0 && index < profiles.size()) {
            return profiles.get(index);
        }
        return null;
    }
}