package io.github.abdurazaaqmohammed.features.files.network;

import org.junit.Test;
import java.net.InetAddress;
import java.util.List;
import static org.junit.Assert.*;

public class DiscoverySubnetTest {
    @Test public void scanIsBoundedAndExcludesSelfNetworkAndBroadcast() throws Exception {
        List<String> hosts = DiscoverySubnet.hosts(InetAddress.getByName("192.168.2.10"), 16);
        assertEquals(253, hosts.size());
        assertTrue(hosts.contains("192.168.2.1"));
        assertTrue(hosts.contains("192.168.2.254"));
        assertFalse(hosts.contains("192.168.2.0"));
        assertFalse(hosts.contains("192.168.2.255"));
        assertFalse(hosts.contains("192.168.2.10"));
        assertFalse(hosts.contains("192.168.1.1"));
    }
    @Test public void smallerNetworksAndUnsupportedAddressesAreSafe() throws Exception {
        assertEquals(5, DiscoverySubnet.hosts(InetAddress.getByName("10.0.0.2"), 29).size());
        assertTrue(DiscoverySubnet.hosts(InetAddress.getByName("127.0.0.1"), 24).isEmpty());
        assertTrue(DiscoverySubnet.hosts(InetAddress.getByName("::1"), 64).isEmpty());
        assertTrue(DiscoverySubnet.hosts(InetAddress.getByName("10.0.0.1"), 32).isEmpty());
    }
}
