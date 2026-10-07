package io.github.abdurazaaqmohammed.vault;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Bitwarden-compatible vault state for the self-hosted client: password login, unlock of a
 * persisted session, sync parsing and per-entry decryption.
 *
 * <p>Pure Java (org.json + {@link BwApi} + {@link BwCrypto}), deliberately free of any Android
 * dependency so the whole flow stays testable on the JVM. Session persistence is the caller's
 * business: it serialises to/from JSON and the UI stores that string.
 */
public final class BwVault {

    /** Name of the dedicated cipher that backs up the app's archive password list. */
    public static final String ARCHIVE_MARKER = "MP Manager 文件密码";

    private BwVault() {
    }

    /** Persisted authentication material; safe to store, contains no secrets by itself. */
    public static final class Session {
        public String server;
        public String email;
        public String deviceId;
        public String accessToken;
        public String refreshToken;
        /** Optional per-connection proxy, e.g. {@code socks5://127.0.0.1:10808}. */
        public String proxy;
        /** User key encrypted with the stretched master key; the only way back in. */
        public String encryptedKey;
        /** Epoch millis at which {@link #accessToken} stops being accepted. */
        public long expiresAt;
        public BwCrypto.KdfConfig kdf;

        public String toJSON() {
            JSONObject o = new JSONObject();
            putQuiet(o, "server", server);
            putQuiet(o, "email", email);
            putQuiet(o, "deviceId", deviceId);
            putQuiet(o, "accessToken", accessToken);
            putQuiet(o, "refreshToken", refreshToken);
            putQuiet(o, "proxy", proxy);
            putQuiet(o, "encryptedKey", encryptedKey);
            putQuiet(o, "expiresAt", expiresAt);
            if (kdf != null) {
                JSONObject k = new JSONObject();
                putQuiet(k, "type", kdf.type);
                putQuiet(k, "iterations", kdf.iterations);
                putQuiet(k, "memoryMB", kdf.memoryMB);
                putQuiet(k, "parallelism", kdf.parallelism);
                putQuiet(o, "kdf", k);
            }
            return o.toString();
        }

        public static Session fromJSON(String raw) {
            if (raw == null || raw.isEmpty()) return null;
            try {
                JSONObject o = new JSONObject(raw);
                Session s = new Session();
                s.server = o.optString("server", null);
                s.email = o.optString("email", null);
                s.deviceId = o.optString("deviceId", null);
                s.accessToken = o.optString("accessToken", null);
                s.refreshToken = o.optString("refreshToken", null);
                s.proxy = o.optString("proxy", null);
                s.encryptedKey = o.optString("encryptedKey", null);
                s.expiresAt = o.optLong("expiresAt", 0);
                JSONObject k = o.optJSONObject("kdf");
                if (k != null) {
                    s.kdf = new BwCrypto.KdfConfig(
                            k.optInt("type", BwCrypto.KDF_PBKDF2),
                            k.optInt("iterations", 600000),
                            k.optInt("memoryMB", 0),
                            k.optInt("parallelism", 0));
                }
                if (s.server == null || s.email == null || s.encryptedKey == null) return null;
                return s;
            } catch (Exception e) {
                return null;
            }
        }

        private static void putQuiet(JSONObject o, String key, Object value) {
            try {
                o.put(key, value);
            } catch (Exception ignored) {
            }
        }
    }

    /** One decrypted vault entry; non-login cipher types carry a name only. */
    public static final class Entry {
        public final String id;
        public final String name;
        public final String username;
        public final String password;
        public final String totp;

        Entry(String id, String name, String username, String password, String totp) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.password = password;
            this.totp = totp;
        }
    }

    /** Everything a screen needs after a successful login or unlock. */
    public static final class Result {
        public final Session session;
        public final List<Entry> entries;
        /** Kept only in memory so a re-sync does not need the master password again. */
        public final BwCrypto.SymKey userKey;

        Result(Session session, List<Entry> entries, BwCrypto.SymKey userKey) {
            this.session = session;
            this.entries = entries;
            this.userKey = userKey;
        }
    }

    /** First login against a self-hosted server: prelogin, password grant, sync. */
    public static Result login(String server, String email, String password, String deviceId,
                               String twoFactorToken, String proxy) throws Exception {
        BwCrypto.KdfConfig kdf = BwApi.prelogin(server, email, proxy);
        return authenticate(server, email, password, kdf, deviceId, twoFactorToken, proxy);
    }

    /**
     * Unlock a persisted session with the master password. Key derivation is offline; the token
     * is refreshed only when it is (about to be) expired, then the vault is fetched.
     */
    public static Result unlock(Session s, String password) throws Exception {
        if (s.kdf == null) throw new Exception("saved session has no KDF parameters");
        byte[] masterKey = BwCrypto.deriveMasterKey(password, s.email, s.kdf);
        BwCrypto.SymKey stretched = BwCrypto.stretchMasterKey(masterKey);
        BwCrypto.SymKey userKey;
        try {
            userKey = BwCrypto.decryptUserKey(stretched, s.encryptedKey);
        } catch (Exception e) {
            throw new Exception("wrong master password");
        }
        refreshIfExpired(s);
        return fetch(s, userKey);
    }

    /** Re-sync an unlocked vault without asking for the password again. */
    public static Result resync(Session s, BwCrypto.SymKey userKey) throws Exception {
        refreshIfExpired(s);
        return fetch(s, userKey);
    }

    /** First entry carrying the given (decrypted) name, or null. */
    public static Entry findEntry(List<Entry> entries, String name) {
        if (entries == null) return null;
        for (Entry e : entries) if (name.equals(e.name)) return e;
        return null;
    }

    /** The archive-password backup cipher, when the vault already has one. */
    public static Entry findArchiveEntry(List<Entry> entries) {
        return findEntry(entries, ARCHIVE_MARKER);
    }

    /**
     * Writes the archive password list into its dedicated vault cipher: creates it on first
     * use, updates it afterwards. {@code content} is the newline-joined list; an empty string
     * still keeps the cipher (with an empty password) so the marker stays easy to find.
     *
     * @return the resulting entry (fresh id on create) so the caller can keep its list current
     */
    public static Entry pushArchivePasswords(Session s, BwCrypto.SymKey userKey,
                                             List<Entry> known, String content) throws Exception {
        return pushArchivePasswords(s, userKey, known, ARCHIVE_MARKER, content);
    }

    static Entry pushArchivePasswords(Session s, BwCrypto.SymKey userKey, List<Entry> known,
                                      String marker, String content) throws Exception {
        refreshIfExpired(s);
        Entry existing = findEntry(known, marker);
        JSONObject body = archiveBody(userKey, marker, content,
                existing == null ? null : existing.id);
        JSONObject answer = existing == null
                ? BwApi.createCipher(s.server, s.accessToken, s.proxy, body)
                : BwApi.updateCipher(s.server, s.accessToken, s.proxy, existing.id, body);
        String id = existing != null ? existing.id
                : answer == null ? null
                : firstText(answer, "id", "Id");
        if (!hasText(id)) id = existing != null ? existing.id : "";
        return new Entry(id, marker, null, content, null);
    }

    /**
     * Cipher body matching what current clients write: a fresh random per-cipher key wraps
     * the name and the newline-joined password list, and the key itself is wrapped by the
     * user key. Field casing follows NodeWarden (camelCase), which also accepts the values
     * an official server would expect.
     */
    static JSONObject archiveBody(BwCrypto.SymKey userKey, String marker, String content,
                                  String existingId) throws Exception {
        byte[] raw = new byte[64];
        new java.security.SecureRandom().nextBytes(raw);
        BwCrypto.SymKey cipherKey = BwCrypto.SymKey.of64(raw);

        JSONObject body = new JSONObject();
        put(body, "type", 1);  // 1 = login in the sync API's numbering
        put(body, "name", BwCrypto.encryptToString(cipherKey, marker));
        put(body, "key", BwCrypto.encrypt(userKey, raw));
        put(body, "favorite", false);
        put(body, "reprompt", 0);
        if (hasText(existingId)) put(body, "id", existingId);
        JSONObject login = new JSONObject();
        put(login, "password", BwCrypto.encryptToString(cipherKey, content));
        put(body, "login", login);
        return body;
    }

    private static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ internals

    private static Result authenticate(String server, String email, String password,
                                       BwCrypto.KdfConfig kdf, String deviceId,
                                       String twoFactorToken, String proxy) throws Exception {
        byte[] masterKey = BwCrypto.deriveMasterKey(password, email, kdf);
        String masterKeyHash = BwCrypto.deriveMasterKeyHash(masterKey, password);
        BwApi.TokenResult token = BwApi.loginPassword(server, email, masterKeyHash, deviceId,
                "MP Manager", null, twoFactorToken, proxy);

        Session s = new Session();
        s.server = server;
        s.email = email;
        s.deviceId = deviceId;
        s.proxy = proxy;
        s.kdf = kdf;
        apply(s, token);
        if (s.encryptedKey == null || s.encryptedKey.isEmpty()) {
            throw new Exception("server did not return the encrypted vault key");
        }

        BwCrypto.SymKey stretched = BwCrypto.stretchMasterKey(masterKey);
        BwCrypto.SymKey userKey;
        try {
            userKey = BwCrypto.decryptUserKey(stretched, s.encryptedKey);
        } catch (Exception e) {
            throw new Exception("could not unlock the vault key");
        }
        return fetch(s, userKey);
    }

    private static void refreshIfExpired(Session s) throws Exception {
        if (s.accessToken == null || s.refreshToken == null) {
            throw new Exception("session has no tokens");
        }
        if (s.expiresAt > System.currentTimeMillis() + 60_000L) return;
        apply(s, BwApi.refresh(s.server, s.refreshToken, s.proxy));
    }

    private static void apply(Session s, BwApi.TokenResult t) {
        s.accessToken = t.accessToken;
        if (t.refreshToken != null && !t.refreshToken.isEmpty()) s.refreshToken = t.refreshToken;
        s.expiresAt = System.currentTimeMillis() + t.expiresInSeconds * 1000L;
        if (t.encryptedKey != null && !t.encryptedKey.isEmpty()) s.encryptedKey = t.encryptedKey;
    }

    private static Result fetch(Session s, BwCrypto.SymKey userKey) throws Exception {
        JSONObject sync = BwApi.sync(s.server, s.accessToken, s.proxy);
        return parse(s, userKey, sync);
    }

    static Result parse(Session s, BwCrypto.SymKey userKey, JSONObject sync)
            throws Exception {
        // Official servers answer in PascalCase, NodeWarden in camelCase; accept both.
        JSONObject profile = firstObject(sync, "Profile", "profile");
        if (profile != null) {
            String key = firstText(profile, "Key", "key");
            if (hasText(key)) s.encryptedKey = key;
        }
        List<Entry> entries = new ArrayList<>();
        JSONArray ciphers = firstArray(sync, "Ciphers", "ciphers");
        if (ciphers != null) {
            for (int i = 0; i < ciphers.length(); i++) {
                JSONObject c = ciphers.optJSONObject(i);
                if (c == null) continue;
                if (hasText(firstText(c, "DeletedDate", "deletedDate"))) continue;
                try {
                    // Per-cipher key (cipher key encryption): unwrap once, decrypt with it.
                    BwCrypto.SymKey key = userKey;
                    String wrapped = firstText(c, "Key", "key");
                    if (hasText(wrapped)) {
                        try {
                            key = BwCrypto.decryptUserKey(userKey, wrapped);
                        } catch (Exception undecryptable) {
                            continue;
                        }
                    }
                    String name = decryptField(key, c, "Name", "name");
                    if (name == null) continue;
                    String username = null;
                    String password = null;
                    String totp = null;
                    JSONObject login = firstObject(c, "Login", "login");
                    if (login != null) {
                        username = decryptField(key, login, "Username", "username");
                        password = decryptField(key, login, "Password", "password");
                        totp = decryptField(key, login, "Totp", "totp");
                    }
                    String id = firstText(c, "Id", "id");
                    entries.add(new Entry(hasText(id) ? id : "", name, username, password, totp));
                } catch (Exception corrupt) {
                    // One undecryptable entry must not take the whole vault down.
                }
            }
        }
        return new Result(s, entries, userKey);
    }

    /** Decrypt a field present under either PascalCase or camelCase; absent/null stays null. */
    private static String decryptField(BwCrypto.SymKey key, JSONObject parent,
                                       String pascal, String camel) throws Exception {
        String raw = firstText(parent, pascal, camel);
        if (!hasText(raw)) return null;
        return BwCrypto.decryptToString(key, raw);
    }

    private static JSONObject firstObject(JSONObject o, String pascal, String camel) {
        JSONObject r = o.optJSONObject(pascal);
        return r != null ? r : o.optJSONObject(camel);
    }

    private static JSONArray firstArray(JSONObject o, String pascal, String camel) {
        JSONArray r = o.optJSONArray(pascal);
        return r != null ? r : o.optJSONArray(camel);
    }

    private static String firstText(JSONObject o, String pascal, String camel) {
        String r = o.optString(pascal, null);
        if (!hasText(r)) r = o.optString(camel, null);
        return r;
    }

    /**
     * Real text, not the literal "null": some org.json implementations turn an explicit
     * JSON null into the string "null" instead of honouring the fallback.
     */
    private static boolean hasText(String v) {
        return v != null && !v.isEmpty() && !"null".equals(v);
    }
}
