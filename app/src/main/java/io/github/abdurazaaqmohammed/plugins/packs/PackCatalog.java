package io.github.abdurazaaqmohammed.plugins.packs;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Pack catalog: bundled assets/packs.json, optionally refreshed from the
 * release server. Remote refreshes are accepted only with a valid
 * RSA/SHA-256 signature (packs.json.sig); failures silently keep the last
 * good copy. The bundled asset is trusted as shipped inside the signed APK.
 */
public final class PackCatalog {

    /** Remote catalog location; update on release. */
    public static final String REMOTE_URL =
            "https://github.com/AbdurazaaqMohammed/MP-Manager/releases/download/tool-packs-v1/packs.json";

    /** Detached base64 signature of the remote catalog (same release). */
    public static final String REMOTE_SIG_URL = REMOTE_URL + ".sig";

    /**
     * Pinned X.509 (base64) catalog signing public key. The private key lives
     * with the maintainer only (tools/SignCatalog.java); rotate by shipping
     * a new key in an app update, never over the wire.
     */
    private static final String CATALOG_PUBLIC_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1Pn0LrDvw7GAo9Rfulmw4cxAcv4hVPWyPYF/ZBFYVFUJP1jlgLnVx4E1+nz4dMyogz9awEI7pvFi5C63XMBEAxqJ1H4fA/tw+DrGKu/02ZU9ww85AItsbNOE8sVIKOPyEMujuarNbJx0VVV8yRBDYKA5oWBfHEqzZwFRzF/ni+s3HAVg8En0St3lry0gZsvQjDMmLFGm9ogyKcg6X/+B5rwVaVdi27XTz7EB/GfSs0zQOGWVkGllt1h0VJ+NmR8kvDDPs3sjbUum4QsODD6Llg5PkdmsjIs2hyQMUpJCfThh+0zyJD9Yr2k8FjLkQcCyh9fk9nMe4KnjBA6k9cxXlQIDAQAB";

    private static final String CACHE_NAME = "packs-catalog.json";

    private PackCatalog() {
    }

    public static List<PackDescriptor> load(Context context) {
        List<PackDescriptor> out = loadFile(cachedFile(context));
        if (!out.isEmpty()) return out;
        return loadAsset(context);
    }

    public static List<PackDescriptor> loadAsset(Context context) {
        try (InputStream is = context.getAssets().open("packs.json")) {
            return parse(readAll(is));
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    /** Best-effort refresh on a worker thread; callback runs on that thread. */
    public static void refreshAsync(Context context, RefreshCallback callback) {
        new Thread(() -> {
            List<PackDescriptor> fresh = null;
            try {
                byte[] body = fetch(REMOTE_URL);
                String sig = null;
                if (body != null) {
                    byte[] sigBytes = fetch(REMOTE_SIG_URL);
                    if (sigBytes != null) sig = new String(sigBytes, "UTF-8").trim();
                }
                if (body != null && sig != null && !sig.isEmpty()
                        && verifySignature(body, sig)) {
                    String text = new String(body, "UTF-8");
                    fresh = parse(text);
                    if (!fresh.isEmpty()) {
                        writeFile(cachedFile(context), text);
                    }
                }
            } catch (Exception ignored) {
            }
            if (callback != null) callback.onDone(fresh);
        }).start();
    }

    private static byte[] fetch(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.connect();
            if (conn.getResponseCode() != 200) return null;
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** RSA/SHA-256 verification of the catalog bytes. Fail closed. */
    static boolean verifySignature(byte[] data, String sigB64) {
        try {
            byte[] keyBytes = Base64.decode(CATALOG_PUBLIC_KEY_B64,
                    Base64.DEFAULT);
            X509EncodedKeySpec spec =
                    new X509EncodedKeySpec(keyBytes);
            PublicKey key =
                    KeyFactory.getInstance("RSA").generatePublic(spec);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update(data);
            byte[] raw = Base64.decode(sigB64.trim(), Base64.DEFAULT);
            return sig.verify(raw);
        } catch (Exception ignored) {
            return false;
        }
    }

    public interface RefreshCallback {
        void onDone(List<PackDescriptor> freshOrNull);
    }

    public static PackDescriptor byId(List<PackDescriptor> catalog, String packId) {
        for (PackDescriptor p : catalog) {
            if (p.id.equals(packId)) return p;
        }
        return null;
    }

    public static PackDescriptor packForTool(List<PackDescriptor> catalog, String toolId) {
        for (PackDescriptor p : catalog) {
            if (p.tool(toolId) != null) return p;
        }
        return null;
    }

    private static File cachedFile(Context context) {
        return new File(context.getFilesDir(), CACHE_NAME);
    }

    private static List<PackDescriptor> loadFile(File f) {
        try (InputStream is = new FileInputStream(f)) {
            return parse(readAll(is));
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    private static List<PackDescriptor> parse(String body) {
        List<PackDescriptor> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(body);
            JSONArray arr = root.optJSONArray("packs");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                try {
                    PackDescriptor p = PackDescriptor.parse(arr.getJSONObject(i));
                    if (!p.id.isEmpty() && !p.entryClass.isEmpty()) out.add(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String readAll(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            sb.append(new String(buf, 0, n, "UTF-8"));
        }
        return sb.toString();
    }

    private static void writeFile(File f, String body) {
        try (FileWriter w = new FileWriter(f)) {
            w.write(body);
        } catch (Exception ignored) {
        }
    }
}
