package io.github.abdurazaaqmohammed.data.remote.webdav;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

import io.github.abdurazaaqmohammed.domain.remote.Capability;
import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystem;

/**
 * WebDAV backend.
 *
 * <p>Unlike FTP this one is genuinely streaming: okhttp hands back a live
 * {@code ResponseBody}, so {@link #read} returns it directly instead of staging
 * through a cache file.
 *
 * <p>Server support varies more than any other protocol here, so each call
 * maps the status codes it can encounter rather than assuming:
 * <ul>
 *   <li>207 is the only success for PROPFIND; anything else is an error.</li>
 *   <li>404 on stat/exists means "absent", not "broken" -- reported as null/false
 *       rather than thrown, since callers use it to probe.</li>
 *   <li>401/403 raise an auth-failure RemoteException so the host re-prompts.</li>
 *   <li>405 means the server refuses that verb; {@link #supports} reports which
 *       verbs we assume, and the UI greys them out.</li>
 * </ul>
 */
public class WebDavRemoteFileSystem implements RemoteFileSystem {

    private static final MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
    private static final String PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
            + "<D:propfind xmlns:D=\"DAV:\"><D:prop>"
            + "<D:resourcetype/><D:getcontentlength/><D:getlastmodified/><D:displayname/>"
            + "</D:prop></D:propfind>";

    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * No Context needed: WebDAV streams in both directions, so unlike the FTP
     * backend there is nothing to stage on disk and no cache directory to keep.
     */
    public WebDavRemoteFileSystem() {
    }

    private OkHttpClient client;
    private HttpUrl baseUrl;
    private RemoteCredentials credentials;
    private String basePath = "/";

    @Override
    public RemoteCredentials.Kind kind() {
        return RemoteCredentials.Kind.WEBDAV;
    }

    @Override
    public String displayName() {
        return "WebDAV";
    }

    @Override
    public String root() {
        return "/";
    }

    @Override
    public void connect(RemoteCredentials creds) throws RemoteException {
        this.credentials = creds;
        this.basePath = WebDavPaths.normalize(creds.extra("basePath", creds.path()));

        String scheme = creds.extraBoolean("plainHttp", false) ? "http" : "https";
        int port = creds.effectivePort();
        String authority = creds.host() + (port > 0 ? ":" + port : "");
        HttpUrl parsed = HttpUrl.parse(scheme + "://" + authority + WebDavPaths.encodePath(basePath));
        if (parsed == null) {
            throw new RemoteException("Not a usable WebDAV URL: " + creds.host());
        }
        this.baseUrl = parsed;

        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true);

        boolean trustSelfSigned = creds.insecure()
                || creds.extraBoolean("trustSelfSigned", false);
        if (trustSelfSigned) {
            applyInsecureTls(b);
        }
        if (!creds.username().isEmpty()) {
            applyAuth(b, creds.extra("authScheme", "basic"));
        }

        this.client = b.build();
        // PROPFIND the base collection: fails fast on bad credentials or a wrong
        // base path. stat() returns null on 404 rather than throwing, so the null
        // has to be checked here or a bad base path reports a successful connect
        // and only fails later, at the first list.
        if (stat("/") == null) {
            throw new RemoteException("WebDAV base is not a readable collection: " + baseUrl);
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
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            client = null;
        }
        baseUrl = null;
    }

    @Override
    public void close() {
        disconnect();
    }

    // ---------------------------------------------------------------- listing

    @Override
    public List<RemoteEntry> list(String path) throws RemoteException {
        String target = WebDavPaths.normalize(path);
        try (Response response = propfind(target, "1")) {
            requireSuccess(response, "list " + target);
            try (InputStream body = response.body() == null ? null : response.body().byteStream()) {
                if (body == null) return new ArrayList<>();
                List<RemoteEntry> entries = WebDavMultiStatus.parse(body, basePath, target);
                // A Depth:1 multistatus includes the collection itself. It is the
                // pane's current directory, not a child of it, and its name
                // resolves to "" -- keeping it would render a blank row.
                entries.removeIf(e -> target.equals(e.path()));
                return entries;
            }
        } catch (IOException e) {
            throw new RemoteException("WebDAV list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RemoteEntry stat(String path) throws RemoteException {
        String target = WebDavPaths.normalize(path);
        try (Response response = propfind(target, "0")) {
            if (response.code() == 404 || response.code() == 410) return null;
            requireSuccess(response, "stat " + target);
            try (InputStream body = response.body() == null ? null : response.body().byteStream()) {
                if (body == null) return null;
                return WebDavMultiStatus.parseSelf(body, basePath, target);
            }
        } catch (IOException e) {
            throw new RemoteException("WebDAV stat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String path) throws RemoteException {
        return stat(path) != null;
    }

    // ------------------------------------------------------------------ files

    @Override
    public InputStream read(String path) throws RemoteException {
        Request request = new Request.Builder().url(urlFor(path)).get().build();
        Response response = execute(request, "read " + path);
        if (response.code() == 404) {
            response.close();
            throw new RemoteException("No such file: " + path);
        }
        if (!response.isSuccessful()) {
            int code = response.code();
            response.close();
            throw failureFor(code, "read " + path);
        }
        if (response.body() == null) {
            response.close();
            throw new RemoteException("Empty response body for " + path);
        }
        // The caller closes the stream, which closes the body and the response.
        return response.body().byteStream();
    }

    @Override
    public void write(String path, InputStream in, boolean append) throws RemoteException {
        if (append) throw new RemoteException("WebDAV append is not supported");
        String target = WebDavPaths.normalize(path);
        // Unknown length up front, so this streams rather than buffering.
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return OCTET_STREAM;
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public void writeTo(okio.BufferedSink sink) throws IOException {
                in.transferTo(sink.outputStream());
            }

            @Override
            public boolean isOneShot() {
                return true;
            }
        };
        Request request = new Request.Builder().url(urlFor(target)).put(body).build();
        try (Response response = execute(request, "write " + target)) {
            requireSuccess(response, "write " + target);
        } catch (IOException e) {
            throw new RemoteException("WebDAV upload failed: " + e.getMessage(), e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // the contract says write() consumes and closes it
            }
        }
    }

    @Override
    public void delete(String path) throws RemoteException {
        Request request = new Request.Builder().url(urlFor(path)).delete().build();
        try (Response response = execute(request, "delete " + path)) {
            if (response.code() == 404) throw new RemoteException("No such entry: " + path);
            requireSuccess(response, "delete " + path);
        } catch (IOException e) {
            throw new RemoteException("WebDAV delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void mkdir(String path) throws RemoteException {
        Request request = new Request.Builder().url(urlFor(path)).method("MKCOL", null).build();
        try (Response response = execute(request, "mkdir " + path)) {
            if (response.code() == 405) {
                throw new RemoteException("Server refuses MKCOL");
            }
            if (response.code() == 409) {
                throw new RemoteException("Parent collection does not exist");
            }
            requireSuccess(response, "mkdir " + path);
        } catch (IOException e) {
            throw new RemoteException("WebDAV MKCOL failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void rename(String from, String to) throws RemoteException {
        String src = WebDavPaths.normalize(from);
        String dst = WebDavPaths.normalize(to);
        String parent = WebDavPaths.parentOf(dst);
        Request request = new Request.Builder()
                .url(urlFor(src))
                .method("MOVE", null)
                .header("Destination", destinationHeader(dst, parent))
                // Default is to refuse clobbering; say so explicitly.
                .header("Overwrite", "T")
                .build();
        try (Response response = execute(request, "rename " + src)) {
            if (response.code() == 405) {
                throw new RemoteException("Server refuses MOVE");
            }
            if (response.code() == 412) {
                throw new RemoteException("Destination already exists");
            }
            requireSuccess(response, "rename " + src);
        } catch (IOException e) {
            throw new RemoteException("WebDAV MOVE failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(Capability capability) {
        return switch (capability) {
            // MKCOL / MOVE / PROPFIND are core WebDAV; only extension properties
            // and HTTP-range semantics are missing.
            case DIRECTORIES, MKDIR, RENAME -> true;
            case APPEND, SYMLINKS, SET_TIMESTAMPS, SERVER_SIDE_COPY -> false;
        };
    }

    // ---------------------------------------------------------------- helpers

    private Response propfind(String path, String depth) throws IOException {
        Request request = new Request.Builder()
                .url(urlFor(path))
                .method("PROPFIND", RequestBody.create(PROPFIND_BODY,
                        MediaType.parse("application/xml; charset=utf-8")))
                .header("Depth", depth)
                .header("Accept", "application/xml, text/xml")
                .build();
        return execute(request, "propfind " + path);
    }

    private HttpUrl urlFor(String path) throws RemoteException {
        if (baseUrl == null) throw new RemoteException("WebDAV is not connected");
        String normalized = WebDavPaths.normalize(path);
        // HttpUrl.resolve() treats a leading "/" as server-root-absolute, so
        // resolving "/" or "/dir" against a base of "/dav/Koofr" produced
        // "https://host/" and dropped the DAV prefix entirely -- a 404 on any
        // server that does not publish the collection at the site root. Resolve
        // against the base as a *directory* instead, trailing slash included.
        HttpUrl baseDir = baseUrl.newBuilder()
                .encodedPath(withTrailingSlash(baseUrl.encodedPath()))
                .build();
        String relative = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        if (relative.isEmpty()) return baseDir;
        HttpUrl resolved = baseDir.resolve(WebDavPaths.encodePath(relative));
        if (resolved == null) {
            throw new RemoteException("Cannot build URL for " + path);
        }
        return resolved;
    }

    private static String withTrailingSlash(String encodedPath) {
        return encodedPath.endsWith("/") ? encodedPath : encodedPath + "/";
    }

    /** MOVE needs an absolute Destination URL, including the base path prefix. */
    private String destinationHeader(String dst, String parent) throws RemoteException {
        String name = WebDavPaths.nameOf(dst);
        // urlFor() returns a URL with no trailing slash. HttpUrl.resolve()
        // treats such a base as a FILE and replaces its last segment, which
        // silently turned "/dir/a.txt" + "b.txt" into "/b.txt". Appending the
        // slash first makes the resolution directory-relative as intended.
        HttpUrl parentUrl = urlFor(parent);
        if (!parentUrl.encodedPath().endsWith("/")) {
            parentUrl = parentUrl.newBuilder().encodedPath(parentUrl.encodedPath() + "/").build();
        }
        HttpUrl resolved = parentUrl.resolve(WebDavPaths.encodePath(name));
        if (resolved == null) throw new RemoteException("Cannot build destination for " + dst);
        return resolved.toString();
    }

    private Response execute(Request request, String what) throws RemoteException {
        if (client == null) throw new RemoteException("WebDAV is not connected");
        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            throw new RemoteException("WebDAV " + what + " failed: " + e.getMessage(), e);
        }
    }

    private static void requireSuccess(Response response, String what) throws RemoteException {
        if (response.isSuccessful()) return;
        int code = response.code();
        if (code == 207) return;
        throw failureFor(code, what);
    }

    private static RemoteException failureFor(int code, String what) {
        if (code == 401 || code == 403) {
            return RemoteException.auth("WebDAV rejected the credentials (" + code + ")");
        }
        if (code == 405) {
            return new RemoteException("Server does not allow this operation (" + code + ")", code, false, null);
        }
        return new RemoteException("WebDAV " + what + " failed with HTTP " + code, code, false, null);
    }

    private void applyAuth(OkHttpClient.Builder b, String scheme) {
        if ("none".equalsIgnoreCase(scheme)) return;
        if ("digest".equalsIgnoreCase(scheme)) {
            b.authenticator(new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) {
                    // Give up after one retry so a wrong password cannot loop.
                    if (responseCount(response) > 2) return null;
                    return response.request().newBuilder()
                            .header("Authorization", Credentials.basic(
                                    credentials.username(), credentials.password()))
                            .build();
                }
            });
            return;
        }
        String header = Credentials.basic(credentials.username(), credentials.password());
        b.addInterceptor(chain -> chain.proceed(
                chain.request().newBuilder().header("Authorization", header).build()));
    }

    private static int responseCount(Response response) {
        int n = 1;
        while ((response = response.priorResponse()) != null) n++;
        return n;
    }

    /**
     * Accept any certificate. Only reachable when the profile opted in, because
     * it disables the check that makes HTTPS meaningful.
     */
    private void applyInsecureTls(OkHttpClient.Builder b) {
        try {
            X509TrustManager trustAll = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{trustAll}, new SecureRandom());
            SSLSocketFactory factory = ctx.getSocketFactory();
            b.sslSocketFactory(factory, trustAll);
            b.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot disable TLS verification", e);
        }
    }
}