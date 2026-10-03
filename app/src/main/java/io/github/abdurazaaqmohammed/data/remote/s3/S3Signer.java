package io.github.abdurazaaqmohammed.data.remote.s3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS Signature Version 4, header-based, for S3 and S3-compatible services.
 *
 * <p>Written directly rather than pulling in the AWS SDK: the SDK would add
 * several megabytes and a large transitive dependency tree for what is a few
 * hundred lines of HMAC chaining, and S3-compatible servers (MinIO, Ceph, R2,
 * B2) only need the signature itself, not the rest of the SDK.
 *
 * <p>Deliberately free of Android imports so it can be exercised on a plain JVM.
 */
public final class S3Signer {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";
    private static final String TERMINATOR = "aws4_request";
    private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String sessionToken;
    private final String amzDate;
    private final String dateStamp;

    /**
     * @param amzDate basic ISO 8601 UTC, {@code yyyyMMdd'T'HHmmss'Z'}; passed in
     *                so the caller controls the clock and tests stay
     *                deterministic
     */
    public S3Signer(String accessKey, String secretKey, String region,
                    String sessionToken, String amzDate) {
        this.accessKey = accessKey == null ? "" : accessKey;
        this.secretKey = secretKey == null ? "" : secretKey;
        this.region = region == null || region.isEmpty() ? "us-east-1" : region;
        this.sessionToken = sessionToken;
        this.amzDate = amzDate;
        this.dateStamp = amzDate.length() >= 8 ? amzDate.substring(0, 8) : amzDate;
    }

    /** Headers that must be applied to the request, including Authorization. */
    public static final class Signed {
        public final Map<String, String> headers;

        Signed(Map<String, String> headers) {
            this.headers = headers;
        }
    }

    /**
     * Signs a request.
     *
     * @param method       HTTP verb
     * @param hostHeader   value for the Host header; must match what is actually
     *                     sent, including a non-default port, or the server
     *                     recomputes a different signature
     * @param canonicalUri already URI-encoded path, beginning with '/'
     * @param query        query parameters, may be empty
     * @param headers      extra headers to sign and send (e.g. Range); may be
     *                     empty
     * @param payloadHash  hex SHA-256 of the body, or {@link #UNSIGNED_PAYLOAD}
     */
    public Signed sign(String method, String hostHeader, String canonicalUri,
                       Map<String, String> query, Map<String, String> headers,
                       String payloadHash) {
        String hash = payloadHash == null || payloadHash.isEmpty() ? EMPTY_SHA256 : payloadHash;

        // Lower-cased, sorted, trimmed: the exact form the server rebuilds.
        Map<String, String> signedHeaders = new TreeMap<>();
        signedHeaders.put("host", hostHeader);
        signedHeaders.put("x-amz-content-sha256", hash);
        signedHeaders.put("x-amz-date", amzDate);
        if (sessionToken != null && !sessionToken.isEmpty()) {
            signedHeaders.put("x-amz-security-token", sessionToken);
        }
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                signedHeaders.put(e.getKey().toLowerCase(Locale.US), e.getValue().trim());
            }
        }

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaderNames = new StringBuilder();
        for (Map.Entry<String, String> e : signedHeaders.entrySet()) {
            canonicalHeaders.append(e.getKey()).append(':')
                    .append(normalizeHeaderValue(e.getValue())).append('\n');
            if (signedHeaderNames.length() > 0) signedHeaderNames.append(';');
            signedHeaderNames.append(e.getKey());
        }

        String canonicalRequest = method
                + '\n' + canonicalUri
                + '\n' + canonicalQuery(query)
                + '\n' + canonicalHeaders
                + '\n' + signedHeaderNames
                + '\n' + hash;

        String scope = dateStamp + '/' + region + '/' + SERVICE + '/' + TERMINATOR;
        String stringToSign = ALGORITHM + '\n'
                + amzDate + '\n'
                + scope + '\n'
                + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = signingKey(dateStamp);
        String signature = hex(hmacSha256(signingKey, stringToSign));

        Map<String, String> out = new LinkedHashMap<>();
        out.put("x-amz-date", amzDate);
        out.put("x-amz-content-sha256", hash);
        if (sessionToken != null && !sessionToken.isEmpty()) {
            out.put("x-amz-security-token", sessionToken);
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) out.put(e.getKey(), e.getValue());
        }
        out.put("Authorization", ALGORITHM
                + " Credential=" + accessKey + '/' + scope
                + ", SignedHeaders=" + signedHeaderNames
                + ", Signature=" + signature);
        return new Signed(out);
    }

    /** Exposed for tests and for callers that need to hash a body first. */
    public static String payloadHash(byte[] body) {
        return body == null || body.length == 0
                ? EMPTY_SHA256
                : hex(sha256(body));
    }

    public static String unsignedPayload() {
        return UNSIGNED_PAYLOAD;
    }

    // ------------------------------------------------------------- internals

    private byte[] signingKey(String stamp) {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), stamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, SERVICE);
        return hmacSha256(kService, TERMINATOR);
    }

    /** Sorted by key then value, RFC 3986 encoded, joined with '&'. */
    public static String canonicalQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        List<String> pairs = new ArrayList<>(query.size());
        for (Map.Entry<String, String> e : query.entrySet()) {
            pairs.add(encode(e.getKey()) + '=' + encode(e.getValue()));
        }
        Collections.sort(pairs);
        return String.join("&", pairs);
    }

    /** SigV4 folds runs of spaces so signing matches what the server recomputes. */
    private static String normalizeHeaderValue(String v) {
        String s = v == null ? "" : v.trim();
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (!lastWasSpace) sb.append(c);
                lastWasSpace = true;
            } else {
                sb.append(c);
                lastWasSpace = false;
            }
        }
        return sb.toString();
    }

    /**
     * Query parameters encoded exactly as {@link #canonicalQuery} encodes them,
     * so the request line and the signature cannot drift apart.
     */
    public static Map<String, String> canonicalEntries(Map<String, String> query) {
        Map<String, String> out = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return out;
        List<String> keys = new ArrayList<>(query.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            out.put(encode(k), encode(query.get(k)));
        }
        return out;
    }

    /** RFC 3986 unreserved set; '/' is encoded because callers pass a path field. */
    public static String encode(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int v = b & 0xFF;
            if ((v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z')
                    || (v >= '0' && v <= '9')
                    || v == '-' || v == '_' || v == '.' || v == '~') {
                sb.append((char) v);
            } else {
                sb.append('%').append(String.format("%02X", v));
            }
        }
        return sb.toString();
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}