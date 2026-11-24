package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.privilege.internal.NativeToolManager;
import io.github.abdurazaaqmohammed.util.text.AppGson;

/** Persists AAPT2 filter profiles and the last used selection. */
final class Aapt2ProfileManager {
    private static final String PREFS_NAME = "Aapt2Profiles";
    private static final String PROFILES_KEY = "profiles";
    private static final String LAST_USED_KEY = "last_used";
    private static final String LAST_SELECTED_KEY = "last_selected";

    private final SharedPreferences prefs;
    private final Gson gson = AppGson.get();
    private final Context context;
    private final List<Aapt2Profile> profiles = new ArrayList<>();

    Aapt2ProfileManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
        ensureDeviceDefault();
    }

    private void load() {
        String json = prefs.getString(PROFILES_KEY, null);
        if (json == null) return;
        try {
            Type type = new TypeToken<List<Aapt2Profile>>() {}.getType();
            List<Aapt2Profile> stored = gson.fromJson(json, type);
            if (stored != null) profiles.addAll(stored);
        } catch (Exception ignored) {
        }
    }

    private void save() {
        prefs.edit().putString(PROFILES_KEY, gson.toJson(profiles)).apply();
    }

    private void ensureDeviceDefault() {
        Aapt2Profile device = deviceProfile();
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (!profiles.get(i).isDeviceDefault()) continue;
            profiles.set(i, device);
            found = true;
            break;
        }
        if (!found) profiles.add(0, device);
        save();
    }

    Aapt2Profile deviceProfile() {
        int dpi = context.getResources().getDisplayMetrics().densityDpi;
        String density = Aapt2ApkSpecs.densityBucket(dpi);
        String language = localeQualifier(Locale.getDefault());
        String abi = NativeToolManager.deviceAbi();
        return new Aapt2Profile(deviceProfileName(), density, language, abi, "", "", true);
    }

    private String deviceProfileName() {
        return context.getString(io.github.abdurazaaqmohammed.MPManager.R.string.aapt2_profile_this_device);
    }

    static String localeQualifier(Locale locale) {
        if (locale == null) return "";
        String language = locale.getLanguage();
        if (language == null || language.isEmpty()) return "";
        language = language.toLowerCase(Locale.ROOT);
        String script = locale.getScript();
        String country = locale.getCountry();
        if (script != null && !script.isEmpty()) {
            return "b+" + language + "+" + script
                    + (country == null || country.isEmpty() ? "" : "+" + country.toUpperCase(Locale.ROOT));
        }
        if (country == null || country.isEmpty()) return language;
        if (country.length() == 3 && Character.isDigit(country.charAt(0))) {
            return "b+" + language + "+" + country;
        }
        return language + "-r" + country.toUpperCase(Locale.ROOT);
    }

    List<Aapt2Profile> getProfiles() {
        return new ArrayList<>(profiles);
    }

    Aapt2Profile getProfile(int index) {
        return index >= 0 && index < profiles.size() ? profiles.get(index) : null;
    }

    void add(Aapt2Profile profile) {
        if (profile == null) return;
        profile.setDeviceDefault(false);
        profiles.add(profile);
        save();
    }

    void update(int index, Aapt2Profile profile) {
        if (index < 0 || index >= profiles.size() || profile == null) return;
        if (profiles.get(index).isDeviceDefault()) {
            profiles.set(index, deviceProfile());
        } else {
            profile.setDeviceDefault(false);
            profiles.set(index, profile);
        }
        save();
    }

    void delete(int index) {
        if (index < 0 || index >= profiles.size() || profiles.get(index).isDeviceDefault()) return;
        profiles.remove(index);
        if (lastSelectedIndex() >= profiles.size()) setLastSelectedIndex(0);
        save();
    }

    Aapt2Profile getLastUsed() {
        String json = prefs.getString(LAST_USED_KEY, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, Aapt2Profile.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    void setLastUsed(Aapt2Profile profile) {
        if (profile == null) return;
        prefs.edit().putString(LAST_USED_KEY, gson.toJson(profile.copyValues())).apply();
    }

    int lastSelectedIndex() {
        return prefs.getInt(LAST_SELECTED_KEY, 0);
    }

    void setLastSelectedIndex(int index) {
        prefs.edit().putInt(LAST_SELECTED_KEY, Math.max(0, index)).apply();
    }
}
