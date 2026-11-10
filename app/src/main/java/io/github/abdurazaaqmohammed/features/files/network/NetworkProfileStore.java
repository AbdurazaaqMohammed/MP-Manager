package io.github.abdurazaaqmohammed.features.files.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.abdurazaaqmohammed.data.prefs.SidebarPins;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** All profile fields (including passwords) are encrypted with an app-private Keystore key. */
public final class NetworkProfileStore {
    private static final String ALIAS = "mp_network_profiles_v1";
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    private final Context context;
    public NetworkProfileStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences("network_profiles", Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    public List<NetworkProfile> load() throws Exception {
        synchronized (LOCK) { return loadLocked(); }
    }

    private List<NetworkProfile> loadLocked() throws Exception {
        String data = preferences.getString("encrypted", null);
        if (data == null) return new ArrayList<>();
        String[] parts = data.split(":", -1);
        if (parts.length != 2) throw new java.io.IOException("Invalid saved network profiles");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        String json = new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        List<NetworkProfile> profiles = new Gson().fromJson(json, new TypeToken<List<NetworkProfile>>() {}.getType());
        if (profiles == null) throw new java.io.IOException("Invalid saved network profiles");
        return profiles;
    }

    private void save(List<NetworkProfile> profiles) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(new Gson().toJson(profiles).getBytes(StandardCharsets.UTF_8));
        String data = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!preferences.edit().putString("encrypted", data).commit()) throw new java.io.IOException("Could not save connection");
    }

    public void put(NetworkProfile profile) throws Exception {
        synchronized (LOCK) {
            List<NetworkProfile> profiles = loadLocked();
            profiles.removeIf(p -> p.protocol.equals(profile.protocol)
                    && (p.name.equals(profile.name) || p.name.equals(profile.previousName)));
            profiles.add(profile);
            save(profiles);
            if (profile.previousName != null)
                SidebarPins.replace(context, profile.protocol + ":" + profile.previousName, profile.pinId());
        }
    }

    public void remove(NetworkProfile profile) throws Exception {
        synchronized (LOCK) {
            List<NetworkProfile> profiles = loadLocked();
            profiles.removeIf(p -> p.protocol.equals(profile.protocol) && p.name.equals(profile.name));
            save(profiles);
            SidebarPins.remove(context, profile.pinId());
        }
    }
}
