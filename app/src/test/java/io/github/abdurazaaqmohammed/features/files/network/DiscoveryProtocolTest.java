package io.github.abdurazaaqmohammed.features.files.network;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.net.SocketFactory;

import static org.junit.Assert.*;

public class DiscoveryProtocolTest {
    private static final String ID = "urn:uuid:12345678-1234-1234-1234-123456789abc";

    private static byte[] match(String id) {
        return ("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' "
                + "xmlns:a='http://schemas.xmlsoap.org/ws/2004/08/addressing' "
                + "xmlns:d='http://schemas.xmlsoap.org/ws/2005/04/discovery'>"
                + "<s:Header><a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</a:Action>"
                + "<a:RelatesTo>" + id + "</a:RelatesTo></s:Header>"
                + "<s:Body><d:ProbeMatches><d:ProbeMatch><d:XAddrs>http://foreign.invalid/ignored</d:XAddrs>"
                + "</d:ProbeMatch></d:ProbeMatches></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
    }

    @Test public void windowsProbeAndMatchingReply() {
        String probe = new String(WsDiscovery.probe(ID), StandardCharsets.UTF_8);
        assertTrue(probe.contains("wsdp:Device"));
        assertTrue(probe.contains(ID));
        byte[] bytes = match(ID);
        assertTrue(WsDiscovery.isProbeMatch(bytes, bytes.length, ID));
        assertFalse(WsDiscovery.isProbeMatch(bytes, bytes.length, "another-probe"));
    }

    @Test public void windowsRepliesRejectMalformedOversizedAndEntityXml() {
        byte[] bytes = match(ID);
        assertFalse(WsDiscovery.isProbeMatch(bytes, 10, ID));
        assertFalse(WsDiscovery.isProbeMatch(new byte[32769], 32769, ID));
        byte[] entity = ("<!DOCTYPE x [<!ENTITY bad SYSTEM 'file:///not-allowed'>]>"
                + new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        assertFalse(WsDiscovery.isProbeMatch(entity, entity.length, ID));
        byte[] wrong = new String(bytes, StandardCharsets.UTF_8).replace("/ProbeMatches</a:Action>", "/Hello</a:Action>")
                .getBytes(StandardCharsets.UTF_8);
        assertFalse(WsDiscovery.isProbeMatch(wrong, wrong.length, ID));
    }

    private static byte[] nodeStatus(int id) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(id); out.writeShort(0x8500);
        out.writeShort(1); out.writeShort(1); out.writeShort(0); out.writeShort(0);
        out.write(NetBiosNames.query(id), 12, 38);
        out.writeShort(0xc00c); out.writeShort(0x21); out.writeShort(1); out.writeInt(0);
        out.writeShort(1 + 18 * 2 + 6); out.writeByte(2);
        out.write("WORKGROUP      ".getBytes(StandardCharsets.US_ASCII)); out.writeByte(0); out.writeShort(0x8000);
        out.write("DESKTOP-PC     ".getBytes(StandardCharsets.US_ASCII)); out.writeByte(0x20); out.writeShort(0x0400);
        out.write(new byte[6]);
        return bytes.toByteArray();
    }

    @Test public void netbiosNamesPreferTheServerNotTheWorkgroup() throws Exception {
        byte[] bytes = nodeStatus(1234);
        assertEquals("DESKTOP-PC", NetBiosNames.parse(bytes, bytes.length, 1234));
        assertNull(NetBiosNames.parse(bytes, bytes.length, 1235));
        assertEquals(50, NetBiosNames.query(1234).length);
        assertEquals(0x21, NetBiosNames.query(1234)[47]);
    }

    @Test public void netbiosMalformedLengthsAndCompressedNamesAreBounded() throws Exception {
        byte[] bytes = nodeStatus(1234);
        for (int length = 0; length < 61; length++) assertNull(NetBiosNames.parse(bytes, length, 1234));
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 1);
        assertNull(NetBiosNames.parse(truncated, truncated.length, 1234));
        bytes[12] = (byte) 0xff;
        assertNull(NetBiosNames.parse(bytes, bytes.length, 1234));
    }

    @Test public void announcementsCanFindPeersOutsideTheScanned24ButNotOutsideTheLan() throws Exception {
        InetAddress local = InetAddress.getByName("192.168.1.20");
        assertTrue(DiscoverySubnet.contains(local, 16, InetAddress.getByName("192.168.2.30")));
        assertFalse(DiscoverySubnet.contains(local, 24, InetAddress.getByName("192.168.2.30")));
        assertFalse(DiscoverySubnet.contains(local, 16, InetAddress.getByName("192.169.1.30")));
        assertFalse(DiscoverySubnet.contains(local, 16, local));
        assertFalse(DiscoverySubnet.contains(local, 16, InetAddress.getByName("239.255.255.250")));
    }

    private static final class TestSocket extends Socket {
        int timeout;
        boolean closed, fail;
        @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
            this.timeout = timeout;
            if (fail) throw new IOException("Offline host");
        }
        @Override public void close() { closed = true; }
    }

    private static SocketFactory delegate(TestSocket socket) {
        return new SocketFactory() {
            @Override public Socket createSocket() { return socket; }
            @Override public Socket createSocket(String h, int p) { throw new AssertionError(); }
            @Override public Socket createSocket(InetAddress h, int p) { throw new AssertionError(); }
            @Override public Socket createSocket(String h, int p, InetAddress l, int lp) { throw new AssertionError(); }
            @Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) { throw new AssertionError(); }
        };
    }

    @Test public void smbConnectTimeoutIsActuallyApplied() throws Exception {
        TestSocket socket = new TestSocket();
        assertSame(socket, new TimedSocketFactory(delegate(socket), 500).createSocket("127.0.0.1", 445));
        assertEquals(500, socket.timeout);
        assertFalse(socket.closed);
    }

    @Test public void failedSmbConnectClosesItsSocket() {
        TestSocket socket = new TestSocket(); socket.fail = true;
        assertThrows(IOException.class, () -> new TimedSocketFactory(delegate(socket), 500).createSocket("127.0.0.1", 445));
        assertTrue(socket.closed);
    }
}
