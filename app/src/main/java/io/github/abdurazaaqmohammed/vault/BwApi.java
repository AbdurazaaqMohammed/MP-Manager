package io.github.abdurazaaqmohammed.vault;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Bitwarden-compatible HTTP client aimed at self-hosted servers (NodeWarden first).
 *
 * <p>Covers the password path only: prelogin, password login with optional new-device OTP and
 * TOTP two-factor, token refresh and the vault sync. Everything is plain
 * {@link HttpURLConnection} + org.json, so there is nothing new to bundle.
 */
public final class BwApi {

    private static final int TIMEOUT_MS = 15000;

    private BwApi() {
    }

    /** Answer of a successful token request. */
    public static final class TokenResult {
        public final String accessToken;
        public final String refreshToken;
        public final int expiresInSeconds;
        /** User symmetric key, encrypted with the stretched master key (cipher string). */
        public final String encryptedKey;

        TokenResult(JSONObject json) {
            this.accessToken = json.optString("access_token");
            this.refreshToken = json.optString("refresh_token");
            this.expiresInSeconds = json.optInt("expires_in");
            this.encryptedKey = json.optString("Key", null);
        }
    }

    /**
     * A failed token request. {@link #twoFactorRequired} means the server wants a second factor;
     * {@link #twoFactorProviders} then mirrors the {@code TwoFactorProviders2} object so the UI
     * can offer the factors it understands.
     */
    public static final class LoginFailed extends Exception {
        public final boolean twoFactorRequired;
        public final JSONObject twoFactorProviders;

        LoginFailed(String message, boolean twoFactorRequired, JSONObject twoFactorProviders) {
            super(message);
            this.twoFactorRequired = twoFactorRequired;
            this.twoFactorProviders = twoFactorProviders;
        }
    }

    // ------------------------------------------------------------------ endpoints

    public static BwCrypto.KdfConfig prelogin(String host, String email) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
        } catch (Exception ignored) {
        }
        JSONObject answer = post(host, "/identity/accounts/prelogin",
                "application/json", body.toString().getBytes(StandardCharsets.UTF_8), null);
        return BwCrypto.KdfConfig.fromPrelogin(answer);
    }

    /**
     * Password grant. {@code twoFactorToken} is a TOTP code when the first attempt failed with
     * {@link LoginFailed#twoFactorRequired}; {@code newDeviceOtp} is the code the server emails
     * when it does not trust this device yet.
     */
    public static TokenResult loginPassword(String host, String email, String masterKeyHash,
                                            String deviceId, String deviceName,
                                            String newDeviceOtp, String twoFactorToken)
            throws IOException, LoginFailed {
        Form form = new Form()
                .param("scope", "api offline_access")
                .param("client_id", "cli")
                .param("deviceType", "25")
                .param("deviceIdentifier", deviceId)
                .param("deviceName", deviceName)
                .param("grant_type", "password")
                .param("username", email)
                .param("password", masterKeyHash);
        if (newDeviceOtp != null) form.param("newDeviceOtp", newDeviceOtp);
        if (twoFactorToken != null) {
            form.param("twoFactorToken", twoFactorToken)
                    .param("twoFactorProvider", "0")  // 0 = authenticator app (TOTP)
                    .param("twoFactorRemember", "0");
        }
        try {
            return new TokenResult(post(host, "/identity/connect/token",
                    "application/x-www-form-urlencoded",
                    form.toString().getBytes(StandardCharsets.UTF_8), null));
        } catch (HttpError e) {
            throw toLoginFailed(e);
        }
    }

    public static TokenResult refresh(String host, String refreshToken)
            throws IOException, LoginFailed {
        String form = new Form()
                .param("grant_type", "refresh_token")
                .param("client_id", "cli")
                .param("refresh_token", refreshToken)
                .toString();
        try {
            return new TokenResult(post(host, "/identity/connect/token",
                    "application/x-www-form-urlencoded",
                    form.getBytes(StandardCharsets.UTF_8), null));
        } catch (HttpError e) {
            throw toLoginFailed(e);
        }
    }

    /** Raw vault state: folders, ciphers, profile. Decryption happens on the client side. */
    public static JSONObject sync(String host, String accessToken) throws IOException {
        return get(host, "/api/sync", accessToken);
    }

    // ------------------------------------------------------------------ plumbing

    private static StringBuilder param(StringBuilder sb, String key, String value) {
        try {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(key, "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(value, "UTF-8"));
        } catch (Exception ignored) {
        }
        return sb;
    }

    /** Small form-urlencoded builder so the requests read like the spec. */
    private static final class Form {
        private final java.lang.StringBuilder sb = new java.lang.StringBuilder();

        Form param(String key, String value) {
            BwApi.param(sb, key, value);
            return this;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    /** A non-2xx HTTP answer, with the server body retained for error parsing. */
    private static final class HttpError extends IOException {
        final int status;
        final String body;

        HttpError(int status, String body) {
            super("HTTP " + status + ": " + body);
            this.status = status;
            this.body = body;
        }
    }

    private static LoginFailed toLoginFailed(HttpError e) {
        try {
            JSONObject json = new JSONObject(e.body);
            String description = json.optString("error_description",
                    json.optString("error", "login failed"));
            JSONObject providers = json.optJSONObject("TwoFactorProviders2");
            boolean twoFactor = description.toLowerCase().contains("two factor")
                    || providers != null;
            return new LoginFailed(description, twoFactor, providers);
        } catch (Exception ignored) {
            return new LoginFailed(e.getMessage(), false, null);
        }
    }

    private static JSONObject get(String host, String path, String bearer) throws IOException {
        HttpURLConnection c = open(host, path);
        try {
            c.setRequestMethod("GET");
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            return readAnswer(c);
        } finally {
            c.disconnect();
        }
    }

    private static JSONObject post(String host, String path, String contentType, byte[] body,
                                   String bearer) throws IOException {
        HttpURLConnection c = open(host, path);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
            return readAnswer(c);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String host, String path) throws IOException {
        String base = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        return c;
    }

    private static JSONObject readAnswer(HttpURLConnection c) throws IOException {
        int status = c.getResponseCode();
        InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = readAll(stream);
        if (status >= 400) throw new HttpError(status, text);
        try {
            return new JSONObject(text);
        } catch (Exception e) {
            throw new IOException("server answered non-JSON for " + c.getURL());
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        java.lang.StringBuilder sb = new java.lang.StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
