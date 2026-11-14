package io.github.abdurazaaqmohammed.feature.files.network;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Windows WS-Discovery over UDP. Never follows a peer's advertised HTTP/XAddrs URLs. */
final class WsDiscovery {
    private static final String WSA = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
    private static final String WSD = "http://schemas.xmlsoap.org/ws/2005/04/discovery";
    private WsDiscovery() {}

    static byte[] probe(String id) {
        return ("<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"" + WSA
                + "\" xmlns:d=\"" + WSD + "\" xmlns:wsdp=\"http://schemas.xmlsoap.org/ws/2006/02/devprof\">"
                + "<s:Header><a:Action>" + WSD + "/Probe</a:Action><a:MessageID>" + id
                + "</a:MessageID><a:ReplyTo><a:Address>" + WSA + "/role/anonymous</a:Address></a:ReplyTo>"
                + "<a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To></s:Header>"
                + "<s:Body><d:Probe><d:Types>wsdp:Device</d:Types></d:Probe></s:Body></s:Envelope>")
                .getBytes(StandardCharsets.UTF_8);
    }

    static boolean isProbeMatch(byte[] bytes, int length, String id) {
        if (length <= 0 || length > 32768 || length > bytes.length) return false;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            for (String feature : new String[]{"http://apache.org/xml/features/disallow-doctype-decl",
                    "http://xml.org/sax/features/external-general-entities",
                    "http://xml.org/sax/features/external-parameter-entities"}) {
                try { factory.setFeature(feature, feature.contains("disallow-doctype")); }
                catch (ParserConfigurationException ignored) {}
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            Document document = builder.parse(new ByteArrayInputStream(bytes, 0, length));
            if (document.getDoctype() != null) return false;
            NodeList actions = document.getElementsByTagNameNS(WSA, "Action");
            NodeList relates = document.getElementsByTagNameNS(WSA, "RelatesTo");
            return actions.getLength() == 1 && relates.getLength() == 1
                    && (WSD + "/ProbeMatches").equals(actions.item(0).getTextContent().trim())
                    && id.equals(relates.item(0).getTextContent().trim())
                    && document.getElementsByTagNameNS(WSD, "ProbeMatch").getLength() > 0;
        } catch (Exception ignored) { return false; }
    }
}
