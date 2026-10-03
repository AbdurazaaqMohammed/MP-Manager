package io.github.abdurazaaqmohammed.data.remote.s3;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;

/**
 * Reads the two S3 responses this backend cares about: ListObjectsV2 and
 * CopyObject.
 *
 * <p>XML hardening is identical to the WebDAV parser on purpose. Android's
 * parser rejects FEATURE_SECURE_PROCESSING and has no XInclude, so asking for
 * them unguarded breaks every request; the EntityResolver is what actually
 * blocks external entities and it is always installed.
 */
final class S3Xml {

    private S3Xml() {
    }

    static final class Listing {
        final List<RemoteEntry> entries = new ArrayList<>();
        boolean truncated;
        String nextToken;
    }

    static Listing parseListing(InputStream in, String keyPrefix)
            throws RemoteException {
        Document doc = parse(in);
        Listing out = new Listing();
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            String name = localName(e);
            switch (name) {
                case "IsTruncated":
                    out.truncated = "true".equalsIgnoreCase(text(e));
                    break;
                case "NextContinuationToken":
                    out.nextToken = text(e);
                    break;
                case "Contents": {
                    String key = childText(e, "Key");
                    if (key == null || key.isEmpty()) break;
                    // S3 has no directories: a "folder" is a key ending in '/'.
                    boolean directory = key.endsWith("/");
                    String display = displayName(key, keyPrefix, directory);
                    long size = parseLong(childText(e, "Size"), 0L);
                    long modified = parseIso(childText(e, "LastModified"));
                    if (size == 0L && !directory) {
                        out.entries.add(RemoteEntry.unknownSize(
                                logicalPath(key, keyPrefix), display, false, modified));
                    } else {
                        out.entries.add(RemoteEntry.file(
                                logicalPath(key, keyPrefix), display, size, modified));
                    }
                    break;
                }
                case "CommonPrefixes": {
                    String key = childText(e, "Prefix");
                    if (key == null || key.isEmpty()) break;
                    out.entries.add(RemoteEntry.directory(
                            logicalPath(stripTrailingSlash(key), keyPrefix),
                            displayName(stripTrailingSlash(key), keyPrefix, true),
                            0L));
                    break;
                }
                default:
                    break;
            }
        }
        return out;
    }

    /**
     * S3 returns an absolute ISO timestamp; LastModified is optional and the
     * epoch is the right answer when it is missing.
     */
    static long parseIso(String value) {
        if (value == null || value.isEmpty()) return 0L;
        String v = value.trim();
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(v).getTime();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(v).getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /** The name shown for an entry, relative to the directory being listed. */
    static String displayName(String key, String keyPrefix, boolean directory) {
        String name = stripTrailingSlash(key);
        String dir = stripTrailingSlash(keyPrefix);
        if (!dir.isEmpty() && name.startsWith(dir)) {
            name = name.substring(dir.length());
        }
        while (name.startsWith("/")) name = name.substring(1);
        return name;
    }

    /** The path this backend uses for an entry, relative to the bucket root. */
    static String logicalPath(String key, String keyPrefix) {
        boolean directory = key.endsWith("/");
        String name = stripTrailingSlash(key);
        String dir = stripTrailingSlash(keyPrefix);
        if (!dir.isEmpty() && name.startsWith(dir + "/")) {
            name = name.substring(dir.length());
        }
        if (name.equals(dir)) return "/";
        // Directories keep the trailing slash: it is what makes an S3 key a
        // folder marker, so dropping it would make exists("/dir") miss.
        return directory ? "/" + name + "/" : "/" + name;
    }

    static String stripTrailingSlash(String s) {
        String p = s == null ? "" : s;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return "/".equals(p) ? "" : p;
    }

    private static long parseLong(String s, long fallback) {
        if (s == null) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String localName(Element e) {
        String n = e.getLocalName();
        return n != null ? n : e.getNodeName().replaceFirst("^.*:", "");
    }

    private static String childText(Element parent, String want) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            if (!want.equals(localName((Element) n))) continue;
            return n.getTextContent() == null ? "" : n.getTextContent().trim();
        }
        return null;
    }

    private static String text(Element e) {
        return e.getTextContent() == null ? "" : e.getTextContent().trim();
    }

    private static Document parse(InputStream in) throws RemoteException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            setFeatureQuietly(f, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeatureQuietly(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureQuietly(f, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureQuietly(f, "http://xml.org/sax/features/external-parameter-entities", false);
            setQuietly(() -> f.setXIncludeAware(false));
            setQuietly(() -> f.setExpandEntityReferences(false));
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(
                    new ByteArrayInputStream(new byte[0])));
            return b.parse(in);
        } catch (Exception e) {
            throw new RemoteException("Malformed S3 response: " + e.getMessage(), e);
        }
    }

    private static void setFeatureQuietly(DocumentBuilderFactory f, String feature, boolean value) {
        try {
            f.setFeature(feature, value);
        } catch (Exception ignored) {
            // Android's parser does not know every feature; the rest still apply.
        }
    }

    private static void setQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // XInclude is unsupported on Android.
        }
    }
}