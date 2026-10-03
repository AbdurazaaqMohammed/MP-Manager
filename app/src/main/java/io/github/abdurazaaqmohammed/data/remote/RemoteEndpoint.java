package io.github.abdurazaaqmohammed.data.remote;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Splits whatever the user typed in the host field into connection parts.
 *
 * <p>People paste the whole DAV URL rather than filling four boxes, so the
 * host field has to cope with a scheme, a port and a base path. Guessing wrong
 * is not cosmetic: defaulting to cleartext makes an https-only server answer
 * with a redirect that a PROPFIND will not follow, which surfaces as an
 * unexplained connection failure.
 *
 * <p>The scheme is taken from the input, never assumed. No scheme means https,
 * which is the safe default; http is only used when it was typed explicitly.
 */
public final class RemoteEndpoint {

    /** Result of splitting the host/port/path inputs. */
    public record Parsed(String host, int port, String path, boolean plainHttp) {
    }

    private RemoteEndpoint() {
    }

    public static Parsed parse(String hostField, String portField, String pathField) {
        String raw = hostField == null ? "" : hostField.trim();
        boolean plain = false;

        String lower = raw.toLowerCase(Locale.US);
        if (lower.startsWith("https://")) {
            raw = raw.substring("https://".length());
        } else if (lower.startsWith("http://")) {
            plain = true;
            raw = raw.substring("http://".length());
        }

        // Trailing junk that is not a path (query, fragment) is dropped along
        // with the path separator itself.
        int cut = indexOfAny(raw, "/?#");
        String pathFromUrl = null;
        if (cut >= 0) {
            pathFromUrl = raw.substring(cut);
            raw = raw.substring(0, cut);
        }

        // Credentials embedded in the URL are accepted but dropped: storing them
        // in the password field instead keeps one source of truth.
        int at = raw.lastIndexOf('@');
        if (at >= 0) {
            raw = raw.substring(at + 1);
        }

        HostPort split = splitHostPort(raw);

        int port = parsePort(portField);
        if (port <= 0) port = split.port();

        String path = pathField == null ? "" : pathField.trim();
        if (path.isEmpty() || "/".equals(path)) {
            path = pathFromUrl == null ? "/" : pathFromUrl;
        }
        path = cleanPath(path);

        return new Parsed(split.host(), port, path, plain);
    }

    /** Host with its port removed; brackets on IPv6 literals are kept. */
    private record HostPort(String host, int port) {
    }

    private static HostPort splitHostPort(String s) {
        String h = s == null ? "" : s;
        // Bracketed IPv6, optionally with a port: [2001:db8::1]:8443
        if (h.startsWith("[")) {
            int close = h.indexOf(']');
            if (close > 0) {
                String rest = h.substring(close + 1);
                int port = rest.startsWith(":") ? parsePort(rest.substring(1)) : 0;
                return new HostPort(h.substring(0, close + 1), port);
            }
            return new HostPort(h, 0);
        }
        int first = h.indexOf(':');
        if (first > 0 && first == h.lastIndexOf(':')) {
            // Exactly one colon is a port. Several means a bare IPv6 literal,
            // which needs brackets once it becomes an authority.
            int port = parsePort(h.substring(first + 1));
            return new HostPort(h.substring(0, first), port);
        }
        if (first > 0) {
            return new HostPort("[" + h + "]", 0);
        }
        return new HostPort(h, 0);
    }

    /**
     * Builds the extras map for a parsed endpoint.
     *
     * <p>Only writes plainHttp when it is actually wanted, so an absent key keeps
     * the backend's https default.
     */
    public static Map<String, String> extrasFor(Parsed parsed) {
        Map<String, String> extra = new LinkedHashMap<>();
        if (parsed.plainHttp()) {
            extra.put("plainHttp", "true");
        }
        return extra;
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }

    /** 0 when absent or out of range, so callers can fall back to a default. */
    public static int parsePort(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isEmpty()) return 0;
        int v;
        try {
            v = Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return 0;
        }
        return v > 0 && v <= 65535 ? v : 0;
    }

    /** Normalises to a leading slash, no trailing slash, no duplicate slashes. */
    static String cleanPath(String p) {
        String s = p == null ? "" : p.trim();
        if (s.isEmpty()) return "/";
        int schemeEnd = s.indexOf("://");
        if (schemeEnd >= 0) {
            int slash = s.indexOf('/', schemeEnd + 3);
            s = slash >= 0 ? s.substring(slash) : "/";
        }
        s = stripQueryAndFragment(s);
        if (!s.startsWith("/")) s = "/" + s;
        while (s.contains("//")) s = s.replace("//", "/");
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "/" : s;
    }

    private static String stripQueryAndFragment(String s) {
        int cut = indexOfAny(s, "?#");
        return cut >= 0 ? s.substring(0, cut) : s;
    }

}