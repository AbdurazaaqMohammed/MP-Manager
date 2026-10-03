package io.github.abdurazaaqmohammed.data.remote.s3;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import io.github.abdurazaaqmohammed.domain.remote.Capability;
import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystem;

/**
 * Generic S3 / S3-compatible object storage.
 *
 * <p>Works against AWS and against MinIO, Ceph, R2, B2 and similar, which is
 * why bucket addressing and the region are configuration rather than constants.
 * Path-style addressing is the default because compatible servers rarely have
 * wildcard DNS set up for virtual-hosted buckets.
 *
 * <p>S3 has no real directories: a folder is a zero-byte key ending in '/'. So
 * mkdir writes such a key, listing synthesises folders from CommonPrefixes, and
 * a folder rename moves each key underneath it. Appending is impossible because
 * object keys are immutable, and a copy onto an existing key overwrites it.
 *
 * <p>The canonical path handed to {@link S3Signer} is always the path actually
 * requested, including the bucket in path-style mode. Signing anything else is
 * the classic way to get a 403 SignatureDoesNotMatch.
 */
public class S3RemoteFileSystem implements RemoteFileSystem {

    private static final int MAX_KEYS_PER_PAGE = 1000;
    /** Listing walks every page; this bounds a pathological bucket. */
    private static final int MAX_PAGES = 100;

    private final AtomicBoolean connected = new AtomicBoolean();
    private OkHttpClient client;
    private S3Endpoint.Resolved endpoint;
    private String accessKey;
    private String secretKey;
    private String sessionToken;

    @Override
    public RemoteCredentials.Kind kind() {
        return RemoteCredentials.Kind.S3;
    }

    @Override
    public String displayName() {
        return "S3";
    }

    @Override
    public void connect(RemoteCredentials creds) throws RemoteException {
        if (creds.kind() != RemoteCredentials.Kind.S3) {
            throw new RemoteException("Not an S3 profile: " + creds.kind());
        }
        if (creds.username() == null || creds.username().trim().isEmpty()) {
            throw new RemoteException("S3 needs an access key id");
        }
        if (creds.password() == null || creds.password().isEmpty()) {
            throw new RemoteException("S3 needs a secret access key");
        }
        accessKey = creds.username().trim();
        secretKey = creds.password();
        sessionToken = creds.extra("sessionToken", "");
        endpoint = S3Endpoint.resolve(creds);

        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();

        // HeadBucket fails fast on a wrong bucket, key or signature rather than
        // letting the first listing report something less obvious.
        Response response = execute(send("HEAD", bucketPath(""), null, null, null,
                S3Signer.payloadHash(new byte[0])));
        try {
            if (!response.isSuccessful()) {
                throw failure("connect", response);
            }
        } finally {
            response.close();
        }
        connected.set(true);
    }

    @Override
    public boolean isConnected() {
        return connected.get() && client != null;
    }

    @Override
    public void disconnect() {
        connected.set(false);
    }

    @Override
    public void close() {
        disconnect();
        client = null;
        endpoint = null;
    }

    @Override
    public String root() {
        return "/";
    }

    @Override
    public boolean supports(Capability capability) {
        return capability != Capability.APPEND;
    }

    // -------------------------------------------------------------- browsing

    @Override
    public List<RemoteEntry> list(String path) throws RemoteException {
        requireConnected();
        String keyPrefix = keyPrefixFor(path);

        List<RemoteEntry> out = new ArrayList<>();
        String token = null;
        int pages = 0;
        do {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("list-type", "2");
            query.put("delimiter", "/");
            if (!keyPrefix.isEmpty()) query.put("prefix", keyPrefix);
            if (token != null) query.put("continuation-token", token);

            Response response = execute(send("GET", bucketPath(""), query, null, null,
                    S3Signer.payloadHash(new byte[0])));
            try {
                requireSuccess(response, "list " + path);
                ResponseBody body = response.body();
                if (body == null) return out;
                S3Xml.Listing page = S3Xml.parseListing(body.byteStream(), keyPrefix);
                // The prefix marker itself is the directory being listed, not a
                // child of it; keeping it yields a nameless row.
                for (RemoteEntry entry : page.entries) {
                    if (entry.name() != null && !entry.name().isEmpty()) {
                        out.add(entry);
                    }
                }
                token = page.truncated ? page.nextToken : null;
            } finally {
                response.close();
            }
            pages++;
        } while (token != null && pages < MAX_PAGES);

        return out;
    }

    @Override
    public RemoteEntry stat(String path) throws RemoteException {
        requireConnected();
        String key = keyFor(path);
        if (key.isEmpty()) {
            return RemoteEntry.directory("/", endpoint.bucket(), 0L);
        }
        Response response = execute(send("HEAD", objectPath(key), null, null, null,
                S3Signer.payloadHash(new byte[0])));
        try {
            if (response.code() == 404) return null;
            requireSuccess(response, "stat " + path);
            long size = parseLong(response.header("Content-Length"));
            long modified = S3Xml.parseIso(response.header("Last-Modified"));
            boolean directory = path.endsWith("/");
            String name = S3Xml.displayName(key, keyPrefixFor(parentOf(path)), directory);
            return directory
                    ? RemoteEntry.directory(path, name, modified)
                    : RemoteEntry.unknownSize(path, name, false, modified);
        } finally {
            response.close();
        }
    }

    @Override
    public boolean exists(String path) throws RemoteException {
        requireConnected();
        String key = keyFor(path);
        Response response = execute(send("HEAD", key.isEmpty() ? bucketPath("") : objectPath(key),
                null, null, null, S3Signer.payloadHash(new byte[0])));
        try {
            return response.code() != 404 && response.isSuccessful();
        } finally {
            response.close();
        }
    }

    // --------------------------------------------------------------- writing

    @Override
    public void write(String path, InputStream in, boolean append) throws RemoteException {
        requireConnected();
        if (append) {
            throw new RemoteException("S3 cannot append; object keys are immutable");
        }
        String key = keyFor(path);
        if (key.isEmpty()) {
            throw new RemoteException("Cannot write to the bucket root");
        }
        // Hashing the body first would mean buffering it, which defeats the
        // point of streaming large uploads; over TLS an unsigned payload is what
        // the AWS SDKs do too.
        RequestBody body = new StreamingBody(in);
        Response response = execute(send("PUT", objectPath(key), null, null, body,
                S3Signer.unsignedPayload()));
        try {
            requireSuccess(response, "write " + path);
        } finally {
            response.close();
        }
    }

    @Override
    public void delete(String path) throws RemoteException {
        requireConnected();
        String key = keyFor(path);
        if (key.isEmpty()) {
            throw new RemoteException("Refusing to delete the bucket itself");
        }
        Response response = execute(send("DELETE", objectPath(key), null, null, null,
                S3Signer.payloadHash(new byte[0])));
        try {
            requireSuccess(response, "delete " + path);
        } finally {
            response.close();
        }
    }

    @Override
    public void mkdir(String path) throws RemoteException {
        requireConnected();
        if (keyFor(path).isEmpty()) {
            throw new RemoteException("Bucket already exists");
        }
        // A directory is a zero-byte key with a trailing slash.
        write(path.endsWith("/") ? path : path + "/",
                new java.io.ByteArrayInputStream(new byte[0]), false);
    }

    @Override
    public void rename(String from, String to) throws RemoteException {
        requireConnected();
        String source = keyFor(from);
        String target = keyFor(to);
        if (source.isEmpty() || target.isEmpty()) {
            throw new RemoteException("Cannot rename the bucket root");
        }
        boolean sourceIsDir = from.endsWith("/");
        if (sourceIsDir != to.endsWith("/")) {
            throw new RemoteException("Cannot rename a folder onto a file, or the reverse");
        }
        if (!sourceIsDir) {
            copyThenDelete(source, target);
            return;
        }
        // Create the destination marker first, so renaming an empty folder
        // still leaves a folder behind rather than deleting one and creating
        // nothing.
        write(to.endsWith("/") ? to : to + "/",
                new java.io.ByteArrayInputStream(new byte[0]), false);
        // Every key under the prefix moves individually; S3 has no server-side
        // "move directory".
        for (RemoteEntry child : list(from)) {
            if (child.name() == null || child.name().isEmpty() || "..".equals(child.name())) {
                continue;
            }
            String childFrom = join(from, child.name());
            String childTo = join(to, child.name());
            if (child.directory()) {
                rename(childFrom + "/", childTo + "/");
            } else {
                copyThenDelete(keyFor(childFrom), keyFor(childTo));
            }
        }
        delete(from.endsWith("/") ? from : from + "/");
    }

    /**
     * Server-side copy followed by a delete, which is how S3 spells a move.
     *
     * <p>x-amz-copy-source has to be part of the signature, so it goes in before
     * signing rather than being added to the finished request.
     */
    private void copyThenDelete(String fromKey, String toKey) throws RemoteException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-amz-copy-source",
                "/" + S3Signer.encode(endpoint.bucket()) + "/" + encodeObjectKey(fromKey));

        Response response = execute(send("PUT", objectPath(toKey), null, headers,
                RequestBody.create(new byte[0], null), S3Signer.payloadHash(new byte[0])));
        try {
            requireSuccess(response, "copy " + fromKey + " -> " + toKey);
        } finally {
            response.close();
        }

        Response remove = execute(send("DELETE", objectPath(fromKey), null, null, null,
                S3Signer.payloadHash(new byte[0])));
        try {
            requireSuccess(remove, "delete after copy " + fromKey);
        } finally {
            remove.close();
        }
    }

    @Override
    public InputStream read(String path) throws RemoteException {
        requireConnected();
        String key = keyFor(path);
        if (key.isEmpty()) {
            throw new RemoteException("Cannot read the bucket root");
        }
        Response response = execute(send("GET", objectPath(key), null, null, null,
                S3Signer.payloadHash(new byte[0])));
        if (!response.isSuccessful()) {
            RemoteException error = failure("read " + path, response);
            response.close();
            throw error;
        }
        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            throw new RemoteException("Empty response body for " + path);
        }
        // The caller closes the stream, which releases the connection.
        return body.byteStream();
    }

    /**
     * Streams the body instead of buffering it.
     *
     * <p>OkHttp 4 has no RequestBody factory taking an InputStream, and hashing
     * the payload up front would mean holding a whole upload in memory. The
     * declared contract says write() consumes and closes the stream, so it is
     * closed here.
     */
    private static final class StreamingBody extends RequestBody {
        private final InputStream source;

        StreamingBody(InputStream source) {
            this.source = source;
        }

        @Override
        public okhttp3.MediaType contentType() {
            return null;
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public void writeTo(okio.BufferedSink sink) throws IOException {
            try (InputStream in = source) {
                sink.writeAll(okio.Okio.source(in));
            }
        }
    }

    // --------------------------------------------------------------- signing

    /**
     * Builds a signed request for an already-encoded path.
     *
     * @param path     canonical URI path, percent-encoded, starting with '/'
     * @param extraHeaders headers that must be signed too, such as
     *                     x-amz-copy-source
     */
    private Request send(String method, String path, Map<String, String> query,
                         Map<String, String> extraHeaders, RequestBody body,
                         String payloadHash) throws RemoteException {
        Map<String, String> toSign = extraHeaders == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(extraHeaders);
        if (body != null && !"UNSIGNED-PAYLOAD".equals(payloadHash)) {
            // Only used for empty bodies; streaming bodies stay unsigned.
            // contentLength() declares IOException, so a length that cannot be
            // determined simply is not added to the signature.
            try {
                long length = body.contentLength();
                if (length >= 0) toSign.put("content-length", String.valueOf(length));
            } catch (IOException ignored) {
                // Unknown length: leave it out.
            }
        }

        S3Signer signer = new S3Signer(accessKey, secretKey, endpoint.region(),
                sessionToken, S3Endpoint.amzDate(System.currentTimeMillis()));
        S3Signer.Signed signed = signer.sign(method, endpoint.hostHeader(), path,
                query, toSign, payloadHash);

        HttpUrl url = HttpUrl.parse(endpoint.baseUrl() + path);
        if (url == null) {
            throw new RemoteException("Cannot build URL for " + path);
        }
        HttpUrl.Builder urlBuilder = url.newBuilder();
        if (query != null) {
            // Re-encode the query in the exact form that was signed.
            for (Map.Entry<String, String> e : S3Signer.canonicalEntries(query).entrySet()) {
                urlBuilder.addEncodedQueryParameter(e.getKey(), e.getValue());
            }
        }

        Request.Builder builder = new Request.Builder().url(urlBuilder.build());
        for (Map.Entry<String, String> e : signed.headers.entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }
        return builder.method(method, body).build();
    }

    private Response execute(Request request) throws RemoteException {
        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            throw new RemoteException("S3 request failed: " + e.getMessage(), e);
        }
    }

    private void requireSuccess(Response response, String what) throws RemoteException {
        if (!response.isSuccessful()) {
            throw failure(what, response);
        }
    }

    /** Maps the common S3 status codes onto something a user can act on. */
    private RemoteException failure(String what, Response response) {
        String detail = "";
        try {
            ResponseBody body = response.body();
            if (body != null) {
                String text = body.string();
                if (text != null) {
                    if (text.length() > 300) text = text.substring(0, 300);
                    detail = " " + text.replaceAll("\\s+", " ").trim();
                }
            }
        } catch (IOException ignored) {
            // The body is a bonus, not required for the message.
        }
        int code = response.code();
        String hint;
        if (code == 401 || code == 403) {
            hint = " (bad credentials, wrong region, or signature mismatch)";
        } else if (code == 404) {
            hint = " (no such bucket or key)";
        } else {
            hint = "";
        }
        return new RemoteException(what + " failed: HTTP " + code + hint + detail);
    }

    // ------------------------------------------------------------ URL building

    private void requireConnected() throws RemoteException {
        if (!isConnected()) {
            throw new RemoteException("S3 is not connected");
        }
    }

    /** Canonical path for the bucket itself, honouring the addressing style. */
    private String bucketPath(String suffix) {
        if (endpoint.pathStyle()) {
            return "/" + S3Signer.encode(endpoint.bucket()) + suffix;
        }
        return suffix.isEmpty() ? "/" : suffix;
    }

    /** Canonical path for one key. */
    private String objectPath(String key) {
        return bucketPath("/" + encodeObjectKey(key));
    }

    private String keyPrefixFor(String path) {
        String prefix = endpoint.prefix() == null ? "" : endpoint.prefix();
        String rel = trimSlashes(path);
        if (rel.isEmpty()) return prefix;
        String joined = S3Endpoint.joinKey(prefix, rel + "/");
        return joined;
    }

    private String keyFor(String path) {
        String prefix = endpoint.prefix() == null ? "" : endpoint.prefix();
        boolean directory = path != null && path.endsWith("/") && path.length() > 1;
        String rel = trimSlashes(path);
        if (rel.isEmpty()) return prefix;
        String key = S3Endpoint.joinKey(prefix, rel);
        return directory ? key + "/" : key;
    }

    /** Keys are encoded per segment so '/' stays a path separator. */
    static String encodeObjectKey(String key) {
        StringBuilder sb = new StringBuilder(key.length() + 16);
        String[] parts = key.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(S3Signer.encode(parts[i]));
        }
        return sb.toString();
    }

    private static String trimSlashes(String path) {
        String p = path == null ? "" : path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String join(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    private static long parseLong(String s) {
        if (s == null) return -1;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String parentOf(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return "/";
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = p.lastIndexOf('/');
        if (slash <= 0) return "/";
        return p.substring(0, slash);
    }
}