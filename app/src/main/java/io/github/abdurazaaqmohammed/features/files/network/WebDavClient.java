package io.github.abdurazaaqmohammed.features.files.network;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public final class WebDavClient implements RemoteClient {
    private static final String DAV = "DAV:";
    private static final RequestBody PROPERTIES = RequestBody.create(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>"
                    + "<d:resourcetype/><d:getcontentlength/><d:getlastmodified/>"
                    + "</d:prop></d:propfind>", MediaType.get("application/xml; charset=utf-8"));
    private static final RequestBody QUOTA = RequestBody.create(
            "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>"
                    + "<d:quota-used-bytes/><d:quota-available-bytes/></d:prop></d:propfind>",
            MediaType.get("application/xml; charset=utf-8"));
    private final HttpUrl root;
    private final OkHttpClient http;
    private final String authorization;

    public WebDavClient(String address, String username, String password) {
        HttpUrl parsed = HttpUrl.get(address);
        if (!parsed.username().isEmpty() || !parsed.password().isEmpty()
                || parsed.query() != null || parsed.fragment() != null) {
            throw new IllegalArgumentException("Use a collection URL without credentials, query or fragment");
        }
        root = parsed.newBuilder().encodedPath(parsed.encodedPath().endsWith("/")
                ? parsed.encodedPath() : parsed.encodedPath() + "/").build();
        authorization = username.isEmpty() ? null : Credentials.basic(username, password,
                java.nio.charset.StandardCharsets.UTF_8);
        // Redirects are handled below, confined to this origin and collection.
        http = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build();
    }

    HttpUrl url(String path) {
        HttpUrl.Builder builder = root.newBuilder();
        String normalized = RemotePath.normalize(path);
        if (!normalized.isEmpty()) for (String part : normalized.split("/")) builder.addPathSegment(part);
        return builder.build();
    }

    private Request.Builder request(String path) {
        Request.Builder builder = new Request.Builder().url(url(path));
        if (authorization != null) builder.header("Authorization", authorization);
        return builder;
    }

    private static void requireSuccess(Response response) throws IOException {
        if (!response.isSuccessful()) throw new IOException("WebDAV HTTP " + response.code()
                + (response.code() == 401 || response.code() == 403 ? " (check credentials and permissions)" : ""));
    }

    private static void requireCompletedMutation(Response response) throws IOException {
        requireSuccess(response);
        if (response.code() != 200 && response.code() != 201 && response.code() != 204)
            throw new IOException("WebDAV operation not fully completed (HTTP " + response.code() + ")");
    }

    private Response execute(Request request) throws IOException {
        for (int redirects = 0; ; redirects++) {
            Response response = http.newCall(request).execute();
            int code = response.code();
            if (code != 301 && code != 302 && code != 307 && code != 308) return response;
            String location = response.header("Location");
            HttpUrl next = location == null ? null : request.url().resolve(location);
            response.close();
            if (request.body() != null && request.body().isOneShot())
                throw new IOException("Upload redirected; use the canonical collection URL before retrying");
            if (redirects >= 5 || next == null || !next.scheme().equals(root.scheme())
                    || !next.host().equals(root.host()) || next.port() != root.port()
                    || !next.username().isEmpty() || !next.password().isEmpty()
                    || next.query() != null || next.fragment() != null
                    || !next.encodedPath().startsWith(root.encodedPath()))
                throw new IOException("Unsafe or excessive WebDAV redirect; use the canonical collection URL");
            // Preserve PROPFIND/MOVE/PUT instead of rewriting them to GET.
            request = request.newBuilder().url(next).build();
        }
    }

    @Override public List<RemoteEntry> list(String directory) throws IOException {
        HttpUrl collection = url(directory);
        if (!collection.encodedPath().endsWith("/")) collection = collection.newBuilder().addPathSegment("").build();
        try (Response response = execute(request(directory).url(collection).header("Depth", "1")
                .method("PROPFIND", PROPERTIES).build())) {
            requireSuccess(response);
            if (response.code() != 207 || response.body() == null) throw new IOException("Not a WebDAV collection");
            List<RemoteEntry> entries = parse(response.body().byteStream(), response.request().url());
            RemoteEntry self = null;
            List<RemoteEntry> children = new ArrayList<>();
            String normalized = RemotePath.normalize(directory);
            for (RemoteEntry entry : entries) {
                if (entry.path.equals(normalized)) self = entry;
                else if (RemotePath.parent(entry.path).equals(normalized)) children.add(entry);
            }
            if (self == null || !self.directory) throw new IOException("Not a readable WebDAV collection");
            return children;
        }
    }

    @Override public RemoteEntry stat(String path) throws IOException {
        try (Response response = execute(request(path).header("Depth", "0")
                .method("PROPFIND", PROPERTIES).build())) {
            if (response.code() == 404) return null;
            requireSuccess(response);
            if (response.code() != 207 || response.body() == null) throw new IOException("Invalid WebDAV response");
            for (RemoteEntry entry : parse(response.body().byteStream(), response.request().url())) {
                if (entry.path.equals(RemotePath.normalize(path))) return entry;
            }
            throw new IOException("WebDAV resource properties unavailable");
        }
    }

    /** Namespace-aware and entity-safe; hrefs must remain inside this connection's root. */
    List<RemoteEntry> parse(InputStream input, HttpUrl requested) throws IOException {
        try {
            org.w3c.dom.Document document = parseDocument(input);
            NodeList responses = document.getElementsByTagNameNS(DAV, "response");
            List<RemoteEntry> entries = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < responses.getLength(); i++) {
                Element response = (Element) responses.item(i);
                HttpUrl href = requested.resolve(text(response, "href"));
                if (href == null || !href.scheme().equals(root.scheme()) || !href.host().equals(root.host())
                        || href.port() != root.port() || !href.username().isEmpty() || !href.password().isEmpty()
                        || href.query() != null || href.fragment() != null) continue;
                String encoded = href.encodedPath();
                String base = root.encodedPath();
                if (encoded.equals(base.substring(0, base.length() - 1))) encoded = base;
                if (!encoded.startsWith(base)) continue;
                // Decode per segment: an encoded slash must not become a nested/local path.
                List<String> segments = href.pathSegments();
                int start = root.pathSegments().size() - 1;
                String path = "";
                try {
                    for (int s = start; s < segments.size(); s++) {
                        if (s == segments.size() - 1 && segments.get(s).isEmpty()) continue;
                        path = RemotePath.child(path, segments.get(s));
                    }
                } catch (IllegalArgumentException unsafe) { continue; }
                NodeList propstats = response.getElementsByTagNameNS(DAV, "propstat");
                boolean readable = false, directory = false;
                long size = 0, modified = 0;
                for (int p = 0; p < propstats.getLength(); p++) {
                    Element propstat = (Element) propstats.item(p);
                    if (!text(propstat, "status").matches("HTTP/\\S+ 2\\d\\d(?: .*)?")) continue;
                    readable = true;
                    directory |= propstat.getElementsByTagNameNS(DAV, "collection").getLength() > 0;
                    try { size = Long.parseLong(text(propstat, "getcontentlength")); }
                    catch (NumberFormatException ignored) {}
                    String date = text(propstat, "getlastmodified");
                    if (!date.isEmpty()) {
                        try {
                            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                            format.setTimeZone(TimeZone.getTimeZone("GMT"));
                            modified = format.parse(date).getTime();
                        } catch (java.text.ParseException ignored) {}
                    }
                }
                if (readable && seen.add(path)) entries.add(new RemoteEntry(path, directory, size, modified));
            }
            return entries;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Invalid WebDAV XML", e); }
    }

    private static String text(Element element, String name) {
        NodeList list = element.getElementsByTagNameNS(DAV, name);
        return list.getLength() == 0 ? "" : list.item(0).getTextContent().trim();
    }

    private static org.w3c.dom.Document parseDocument(InputStream input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        // Android's XML implementation does not support every JAXP feature.
        for (String feature : new String[]{"http://apache.org/xml/features/disallow-doctype-decl",
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities"}) {
            try { factory.setFeature(feature, feature.contains("disallow-doctype")); }
            catch (javax.xml.parsers.ParserConfigurationException ignored) {}
        }
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        org.w3c.dom.Document document = builder.parse(input);
        if (document.getDoctype() != null) throw new IOException("WebDAV XML must not contain a DTD");
        return document;
    }

    /** RFC 4331 quotas describe the authenticated user's collection, not necessarily the whole disk. */
    @Override public RemoteSpace space() throws IOException {
        try (Response response = execute(request("").header("Depth", "0").method("PROPFIND", QUOTA).build())) {
            if (response.code() == 403 || response.code() == 404 || response.code() == 405 || response.code() == 501)
                return null;
            requireSuccess(response);
            if (response.code() != 207 || response.body() == null) return null;
            NodeList responses = parseDocument(response.body().byteStream()).getElementsByTagNameNS(DAV, "response");
            for (int i = 0; i < responses.getLength(); i++) {
                Element entry = (Element) responses.item(i);
                String rawHref = text(entry, "href");
                if (rawHref.isEmpty()) continue;
                HttpUrl href = response.request().url().resolve(rawHref);
                if (href == null || !href.scheme().equals(root.scheme()) || !href.host().equals(root.host())
                        || href.port() != root.port() || !href.username().isEmpty() || !href.password().isEmpty()
                        || href.query() != null || href.fragment() != null) continue;
                String path = href.encodedPath();
                if (!path.endsWith("/")) path += "/";
                if (!path.equals(root.encodedPath())) continue;
                long used = -1, free = -1;
                NodeList propstats = entry.getElementsByTagNameNS(DAV, "propstat");
                for (int p = 0; p < propstats.getLength(); p++) {
                    Element props = (Element) propstats.item(p);
                    if (!text(props, "status").matches("HTTP/\\S+ 2\\d\\d(?: .*)?")) continue;
                    long u = quotaNumber(text(props, "quota-used-bytes"));
                    long f = quotaNumber(text(props, "quota-available-bytes"));
                    if (u >= 0) used = u;
                    if (f >= 0) free = f;
                }
                if (free >= 0) {
                    try { return used >= 0 ? RemoteSpace.fromQuota(used, free) : RemoteSpace.available(free); }
                    catch (IllegalArgumentException | ArithmeticException ignored) { return null; }
                }
            }
            return null;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Invalid WebDAV quota response", e); }
    }

    private static long quotaNumber(String value) {
        try { return Math.max(-1, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    @Override public void download(String path, OutputStream destination) throws IOException {
        try (Response response = execute(request(path).get().build())) {
            requireSuccess(response);
            if (response.code() != 200) throw new IOException("Unexpected WebDAV download status: " + response.code());
            if (response.body() == null) throw new IOException("Missing WebDAV content");
            copy(response.body().byteStream(), destination);
        }
    }

    @Override public void upload(String path, InputStream source, long size) throws IOException {
        requireNonRoot(path);
        RequestBody body = new RequestBody() {
            @Override public MediaType contentType() { return MediaType.get("application/octet-stream"); }
            @Override public long contentLength() { return size; }
            @Override public boolean isOneShot() { return true; }
            @Override public void writeTo(BufferedSink sink) throws IOException { copy(source, sink.outputStream()); }
        };
        try (Response response = execute(request(path).header("If-None-Match", "*")
                .put(body).build())) { requireCompletedMutation(response); }
    }

    @Override public void mkdir(String path) throws IOException {
        requireNonRoot(path);
        try (Response response = execute(request(path).method("MKCOL", null).build())) {
            requireCompletedMutation(response);
        }
    }

    @Override public void rename(String path, String destination) throws IOException {
        requireNonRoot(path);
        requireNonRoot(destination);
        try (Response response = execute(request(path).header("Destination", url(destination).toString())
                .header("Overwrite", "F").method("MOVE", null).build())) { requireCompletedMutation(response); }
    }

    @Override public void delete(String path, boolean directory) throws IOException {
        requireNonRoot(path);
        try (Response response = execute(request(path).delete().build())) {
            requireCompletedMutation(response);
        }
    }

    static void requireNonRoot(String path) throws IOException {
        if (RemotePath.normalize(path).isEmpty()) throw new IOException("Cannot modify the connection root");
    }

    static void copy(InputStream source, OutputStream destination) throws IOException {
        byte[] buffer = new byte[65536];
        int n;
        while ((n = source.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Transfer cancelled");
            destination.write(buffer, 0, n);
        }
    }

    @Override public void close() {
        http.dispatcher().cancelAll();
        http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdown();
    }
}
