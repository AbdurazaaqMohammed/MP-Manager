package io.github.abdurazaaqmohammed.data.remote;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystem;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystemFactory;

/**
 * Where remote connections are kept, and which backend serves each kind.
 *
 * <p>Profiles live in SharedPreferences as one JSON array, matching how the FTP
 * side already persists its profiles. Credentials are stored in plain text for
 * the same reason and with the same caveat: that is a separate change to
 * move them behind EncryptedSharedPreferences, and doing it halfway would be
 * worse than either extreme.
 */
public final class RemoteProfiles {

    private static final String PREFS = "remote_profiles";
    private static final String KEY_PROFILES = "profiles";

    private final Context appContext;

    public RemoteProfiles(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ------------------------------------------------------------- profiles

    public List<RemoteCredentials> load() {
        String raw = prefs().getString(KEY_PROFILES, "[]");
        List<RemoteCredentials> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Map<String, String> extra = new LinkedHashMap<>();
                JSONObject ex = o.optJSONObject("extra");
                if (ex != null) {
                    java.util.Iterator<String> it = ex.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        extra.put(k, ex.optString(k, ""));
                    }
                }
                out.add(new RemoteCredentials(
                        o.optString("id", "p" + i),
                        RemoteCredentials.Kind.valueOf(o.optString("kind", "FTP")),
                        o.optString("host", ""),
                        o.optInt("port", 0),
                        o.optString("username", ""),
                        o.optString("password", ""),
                        o.optString("path", "/"),
                        o.optBoolean("insecure", false),
                        extra));
            }
        } catch (JSONException e) {
            // A corrupt blob must not brick the dialog; start empty instead.
            return new ArrayList<>();
        }
        return out;
    }

    public void save(List<RemoteCredentials> profiles) {
        JSONArray arr = new JSONArray();
        for (RemoteCredentials c : profiles) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", c.id());
                o.put("kind", c.kind().name());
                o.put("host", c.host());
                o.put("port", c.port());
                o.put("username", c.username());
                o.put("password", c.password());
                o.put("path", c.path());
                o.put("insecure", c.insecure());
                if (c.extra() != null && !c.extra().isEmpty()) {
                    JSONObject ex = new JSONObject();
                    for (Map.Entry<String, String> en : c.extra().entrySet()) {
                        ex.put(en.getKey(), en.getValue());
                    }
                    o.put("extra", ex);
                }
            } catch (JSONException ignored) {
                // Only thrown for NaN/Infinity, which these fields never hold.
            }
            arr.put(o);
        }
        prefs().edit().putString(KEY_PROFILES, arr.toString()).apply();
    }

    public void put(RemoteCredentials credentials) {
        List<RemoteCredentials> all = load();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(credentials.id())) {
                all.set(i, credentials);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(credentials);
        save(all);
    }

    public void remove(String id) {
        List<RemoteCredentials> all = load();
        all.removeIf(c -> c.id().equals(id));
        save(all);
    }

    public RemoteCredentials byId(String id) {
        for (RemoteCredentials c : load()) {
            if (c.id().equals(id)) return c;
        }
        return null;
    }

    // -------------------------------------------------------------- factory

    /**
     * Builds a backend for a profile, or throws when no implementation is
     * registered for its kind. Registration is explicit rather than reflective
     * so a half-initialised class cannot be picked up by accident.
     */
    public RemoteFileSystem create(RemoteCredentials credentials) {
        RemoteFileSystemFactory factory = RemoteRegistry.factoryFor(credentials.kind());
        if (factory == null) {
            throw new UnsupportedOperationException(
                    "No backend registered for " + credentials.kind());
        }
        return factory.create(appContext);
    }

    /** Whether a profile's kind is actually usable right now. */
    public boolean isSupported(RemoteCredentials.Kind kind) {
        return RemoteRegistry.factoryFor(kind) != null;
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}