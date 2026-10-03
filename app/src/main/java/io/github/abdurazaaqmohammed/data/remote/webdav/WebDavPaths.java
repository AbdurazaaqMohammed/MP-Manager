package io.github.abdurazaaqmohammed.data.remote.webdav;

/**
 * Path and URL handling for WebDAV.
 *
 * <p>WebDAV paths arrive URL-encoded inside {@code <href>} and are relative to
 * the collection, while {@link RemoteCredentials#path()} is what the user typed.
 * Mixing the two up is the classic source of "lists the wrong folder" bugs, so
 * both directions live here.
 *
 * <p>Security: {@link #decodeHref} rejects decoded paths that escape the root
 * with {@code ..}. A server (or a MITM on a self-signed connection) can return
 * {@code ../../etc} as an href, and without this check it would be joined onto
 * the base URL and written to.
 */
final class WebDavPaths {

    private WebDavPaths() {
    }

    /** Normalise to a leading slash, collapse duplicate slashes, drop a trailing one. */
    static String normalize(String path) {
        if (path == null) return "/";
        String p = path.trim();
        if (p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        while (p.contains("//")) p = p.replace("//", "/");
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /** Parent directory of a normalised path; "/" for a top-level entry. */
    static String parentOf(String normalized) {
        int slash = normalized.lastIndexOf('/');
        return slash <= 0 ? "/" : normalized.substring(0, slash);
    }

    /** Last segment of a normalised path. */
    static String nameOf(String normalized) {
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    static String join(String dir, String name) {
        String base = dir.endsWith("/") ? dir : dir + "/";
        return base + name;
    }

    /**
     * Decode a {@code <href>} into an absolute path below {@code basePath}.
     *
     * @throws IllegalArgumentException when the href escapes the base, which
     *         would let a hostile server redirect reads and writes outside it
     */
    static String hrefToPath(String href, String basePath) {
        String raw = href;
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("empty href");

        // Absolute hrefs may carry scheme://authority/path. Keep only the path.
        int schemeEnd = raw.indexOf("://");
        if (schemeEnd >= 0) {
            int slash = raw.indexOf('/', schemeEnd + 3);
            raw = slash >= 0 ? raw.substring(slash) : "/";
        }
        raw = stripQueryAndFragment(raw);
        String decoded = percentDecode(raw);

        if (basePath == null || basePath.isEmpty() || "/".equals(basePath)) {
            return rejectEscape(normalize(decoded));
        }
        String base = basePath.endsWith("/") ? basePath : basePath + "/";
        String prefix = base.replaceAll("^(https?://[^/]+)?", "");
        if (decoded.startsWith(prefix)) {
            return rejectEscape(normalize("/" + decoded.substring(prefix.length())));
        }
        // Some servers echo the full absolute URL, others a path relative to the
        // base. Fall back to treating it as already-relative to the base.
        if (!decoded.startsWith("/")) {
            return rejectEscape(normalize(join(base, decoded)));
        }
        return rejectEscape(normalize(join(base, decoded)));
    }

    private static String rejectEscape(String path) {
        for (String seg : path.split("/")) {
            if (seg.equals("..")) {
                throw new IllegalArgumentException("href escapes the base path: " + path);
            }
        }
        return path;
    }

    private static String stripQueryAndFragment(String s) {
        int cut = s.length();
        for (char c : new char[]{'#', '?'}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < cut) cut = i;
        }
        return s.substring(0, cut);
    }

    /**
     * Percent-decode without treating "+" as a space: in a URL path "+" is a
     * literal plus, and decoding it as a space corrupts filenames.
     */
    static String percentDecode(String s) {
        if (s == null || s.indexOf('%') < 0) return s;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            // Encode non-ASCII as UTF-8 bytes.
            if (c < 0x80) {
                out.write(c);
            } else {
                byte[] bytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.write(bytes, 0, bytes.length);
            }
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Percent-encode a path for use in a request line, keeping "/" intact so the
     * server still sees the hierarchy.
     */
    static String encodePath(String path) {
        StringBuilder sb = new StringBuilder(path.length() + 16);
        for (byte b : path.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int v = b & 0xFF;
            boolean safe = (v >= 'a' && v <= 'z') || (v >= 'A' && v <= 'Z')
                    || (v >= '0' && v <= '9')
                    || v == '-' || v == '_' || v == '.' || v == '~' || v == '/';
            if (safe) {
                sb.append((char) v);
            } else {
                sb.append('%').append(String.format("%02X", v));
            }
        }
        return sb.toString();
    }
}