package io.github.abdurazaaqmohammed.commandhelper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProfileManager {

    private static final String PREF_KEY = "profiles_cache";
    private final SharedPreferences prefs;

    public ProfileManager(Context context) {
        this.prefs = context.getSharedPreferences("command_helper", Context.MODE_PRIVATE);
    }

    public List<Profile> getProfiles() {
        List<Profile> list = new ArrayList<>();
        String json = prefs.getString(PREF_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new Profile(obj.optString("name", ""), obj.optString("command", "")));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public void saveProfiles(List<Profile> profiles) {
        try {
            JSONArray arr = new JSONArray();
            for (Profile p : profiles) {
                JSONObject obj = new JSONObject();
                obj.put("name", p.name);
                obj.put("command", p.command);
                arr.put(obj);
            }
            prefs.edit().putString(PREF_KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void addProfile(Profile p) {
        List<Profile> list = getProfiles();
        list.add(p);
        saveProfiles(list);
    }

    public void updateProfile(int index, Profile p) {
        List<Profile> list = getProfiles();
        if (index >= 0 && index < list.size()) {
            list.set(index, p);
            saveProfiles(list);
        }
    }

    public void deleteProfile(int index) {
        List<Profile> list = getProfiles();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveProfiles(list);
        }
    }

    public static class Profile {
        public String name;
        public String command;

        public Profile(String name, String command) {
            this.name = name;
            this.command = command;
        }

        public String getGeneratedCommand(String filePath) {
            if (command == null || filePath == null) return "";
            String result = command.replace("%FILE%", filePath);
            String fName = filePath.substring(filePath.lastIndexOf('/') + 1);
            result = result.replace("%FNAME%", fName);
            int dotIdx = fName.lastIndexOf('.');
            if (dotIdx > 0) {
                result = result.replace("%NAME%", fName.substring(0, dotIdx));
                result = result.replace("%EXT%", fName.substring(dotIdx + 1));
            } else {
                result = result.replace("%NAME%", fName);
                result = result.replace("%EXT%", "");
            }
            String shellPath = escapeForShell(filePath);
            result = result.replace("%FPATH%", shellPath);
            return result;
        }

        private static String escapeForShell(String path) {
            return "'" + path.replace("'", "'\\''") + "'";
        }
    }
}
