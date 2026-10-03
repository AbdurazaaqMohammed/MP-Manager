package io.github.abdurazaaqmohammed.data.remote.webdav;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;

/**
 * Parser for a WebDAV {@code 207 Multi-Status} body.
 *
 * <p>Hardened against XXE and entity expansion: a WebDAV server is frequently
 * third-party and a self-signed connection may be interceptable, so the document
 * is parsed with DTDs and external entities switched off.
 */
final class WebDavMultiStatus {

    private static final String DAV = "DAV:";

    private WebDavMultiStatus() {
    }

    /**
     * @param xml        the multistatus body
     * @param basePath   collection the listing was requested for, used to place
     *                   relative hrefs
     * @param selfPath   absolute path of the listed collection, so its own
     *                   response can be skipped
     * @return the child entries, never null
     */
    static List<RemoteEntry> parse(InputStream xml, String basePath, String selfPath)
            throws RemoteException {
        Document doc = parseXml(xml);
        List<RemoteEntry> out = new ArrayList<>();
        NodeList responses = doc.getElementsByTagNameNS(DAV, "response");
        for (int i = 0; i < responses.getLength(); i++) {
            Element response = (Element) responses.item(i);
            RemoteEntry entry = toEntry(response, basePath, selfPath);
            if (entry != null) out.add(entry);
        }
        return out;
    }

    /** First entry matching {@code selfPath}, or null when absent. */
    static RemoteEntry parseSelf(InputStream xml, String basePath, String selfPath)
            throws RemoteException {
        List<RemoteEntry> all = parse(xml, basePath, selfPath);
        for (RemoteEntry e : all) {
            if (e.path().equals(selfPath)) return e;
        }
        return null;
    }

    private static RemoteEntry toEntry(Element response, String basePath, String selfPath) {
        String href = childText(response, "href");
        if (href == null) return null;

        String path;
        try {
            path = WebDavPaths.hrefToPath(href, basePath);
        } catch (IllegalArgumentException e) {
            // A hostile or broken href that escapes the base: skip it rather
            // than failing the whole listing.
            return null;
        }

        Element propstat = findSuccessfulPropstat(response);
        if (propstat == null) return null;

        boolean directory = isCollection(propstat);
        long size = parseLong(childText(propstat, "getcontentlength"), -1);
        long modified = parseHttpDate(childText(propstat, "getlastmodified"));

        if (path.equals(selfPath)) {
            // The collection itself. Its own resourcetype/size still have to be
            // honoured: list() only needs the name, but stat() is asking about
            // exactly this entry and must report a real file's size. Hardcoding
            // directory=true here made stat() return size -1 for every file.
            return new RemoteEntry(path, WebDavPaths.nameOf(path), directory,
                    directory ? -1 : size, modified, false, null);
        }
        String name = WebDavPaths.nameOf(path);
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) return null;
        return new RemoteEntry(path, name, directory, directory ? -1 : size, modified, false, null);
    }

    /** The first {@code propstat} that carries a 2xx status. */
    private static Element findSuccessfulPropstat(Element response) {
        NodeList props = response.getElementsByTagNameNS(DAV, "propstat");
        Element fallback = null;
        for (int i = 0; i < props.getLength(); i++) {
            Element ps = (Element) props.item(i);
            String status = childText(ps, "status");
            if (status == null) {
                if (fallback == null) fallback = ps;
                continue;
            }
            if (status.contains(" 200 ")) return ps;
        }
        return fallback;
    }

    /**
     * A collection is marked by an empty {@code <collection/>} element INSIDE
     * {@code <resourcetype>}. Comparing the resourcetype element's own localName
     * is always "resourcetype", so the children have to be examined.
     */
    private static boolean isCollection(Element propstat) {
        NodeList rt = propstat.getElementsByTagNameNS(DAV, "resourcetype");
        for (int i = 0; i < rt.getLength(); i++) {
            Node kids = rt.item(i).getFirstChild();
            while (kids != null) {
                if (kids instanceof Element
                        && "collection".equals(((Element) kids).getLocalName())) {
                    return true;
                }
                kids = kids.getNextSibling();
            }
        }
        return false;
    }

    private static String childText(Element parent, String localName) {
        NodeList list = parent.getElementsByTagNameNS(DAV, localName);
        if (list.getLength() == 0) {
            // Some servers answer without the namespace; fall back to the name.
            list = parent.getElementsByTagName(localName);
        }
        if (list.getLength() == 0) return null;
        Node n = list.item(0);
        String text = n.getTextContent();
        return text == null ? null : text.trim();
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** RFC 1123 date, the format {@code getlastmodified} uses. */
    private static long parseHttpDate(String s) {
        if (s == null || s.isEmpty()) return 0;
        String[] patterns = {
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEEE, dd-MMM-yy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date d = f.parse(s);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
                // try the next pattern
            }
        }
        return 0;
    }

    private static Document parseXml(InputStream in) throws RemoteException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // A WebDAV server is often third-party; do not let its XML reach
            // the filesystem or the network.
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeatureQuietly(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureQuietly(f, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureQuietly(f, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureQuietly(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new ByteArrayInputStream(new byte[0])));
            return b.parse(in);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RemoteException("Malformed WebDAV response: " + e.getMessage(), e);
        }
    }

    private static void setFeatureQuietly(DocumentBuilderFactory f, String feature, boolean value) {
        try {
            f.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // Not every parser knows every feature; the others still apply.
        }
    }
}