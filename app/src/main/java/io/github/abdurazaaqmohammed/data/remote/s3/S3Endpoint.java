package io.github.abdurazaaqmohammed.data.remote.s3;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;

/**
 * Turns {@link RemoteCredentials} into the pieces an S3 request needs.
 *
 * <p>S3 has no single URL, so the generic fields are mapped like this:
 * <ul>
 *   <li>{@code host} + {@code port} + the {@code endpoint} extra give the base
 *       URL; {@code plainHttp} selects http for a local MinIO</li>
 *   <li>{@code username} / {@code password} are the access key id and secret
 *       access key, {@code sessionToken} the extra for temporary credentials</li>
 *   <li>{@code path} is {@code /bucket} with an optional prefix, which is what
 *       makes the bucket the root of the browsable tree</li>
 * </ul>
 */
public final class S3Endpoint {

    /** Resolved base URL, bucket and optional key prefix. */
    public record Resolved(String baseUrl, String hostHeader, String bucket,
                           String prefix, String region, boolean pathStyle) {
    }

    private S3Endpoint() {
    }

    public static Resolved resolve(RemoteCredentials creds) throws RemoteException {
        String host = creds.host() == null ? "" : creds.host().trim();
        if (host.isEmpty()) {
            throw new RemoteException("S3 needs an endpoint host");
        }
        // The path carries the bucket, e.g. "/mybucket" or "/mybucket/reports".
        String path = creds.path() == null ? "" : creds.path().trim();
        String bucketName;
        String prefix = "";
        if (path.isEmpty() || "/".equals(path)) {
            // The bucket must be named explicitly; there is no default.
            bucketName = "";
        } else {
            String p = path.startsWith("/") ? path.substring(1) : path;
            int slash = p.indexOf('/');
            if (slash < 0) {
                bucketName = p;
            } else {
                bucketName = p.substring(0, slash);
                prefix = p.substring(slash + 1);
            }
        }
        if (bucketName.isEmpty()) {
            throw new RemoteException("S3 needs a bucket; put it in the path field, e.g. /mybucket");
        }

        boolean plain = creds.extraBoolean("plainHttp", false);
        String explicit = creds.extra("endpoint", "");
        String scheme;
        String authority;
        if (explicit != null && !explicit.isEmpty()) {
            String trimmed = explicit.trim();
            int schemeEnd = trimmed.indexOf("://");
            scheme = schemeEnd >= 0 ? trimmed.substring(0, schemeEnd) : (plain ? "http" : "https");
            String rest = schemeEnd >= 0 ? trimmed.substring(schemeEnd + 3) : trimmed;
            while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
            authority = rest;
            plain = "http".equalsIgnoreCase(scheme);
        } else {
            scheme = plain ? "http" : "https";
            int port = creds.port();
            // A non-default port belongs in the Host header too, or the server
            // rebuilds a different signature and rejects the request.
            authority = port > 0 ? host + ":" + port : host;
        }

        boolean pathStyle = creds.extraBoolean("pathStyle", true);
        String region = creds.extra("region", "us-east-1");

        String baseUrl = scheme + "://" + authority;
        // Virtual-hosted style moves the bucket into the hostname; path style
        // keeps it in the path, which is what MinIO and Ceph expect.
        String hostHeader = pathStyle ? authority : bucketName + "." + authority;

        return new Resolved(baseUrl, hostHeader, bucketName, prefix, region, pathStyle);
    }

    /** {@code yyyyMMdd'T'HHmmss'Z'}, the x-amz-date format. */
    public static String amzDate(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    static String joinKey(String prefix, String relative) {
        String base = prefix == null ? "" : prefix;
        if (relative == null || relative.isEmpty()) return base;
        if (base.isEmpty()) return relative;
        if (base.endsWith("/")) return base + relative;
        return base + "/" + relative;
    }
}