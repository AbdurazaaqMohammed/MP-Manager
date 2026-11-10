package io.github.abdurazaaqmohammed.features.files.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Never scan beyond the current IPv4 /24, even when the LAN has a larger subnet. */
public final class DiscoverySubnet {
    private DiscoverySubnet() {}
    public static List<String> hosts(InetAddress address, int prefix) {
        List<String> result = new ArrayList<>();
        if (!(address instanceof Inet4Address) || prefix < 0 || prefix > 30
                || address.isLoopbackAddress() || address.isLinkLocalAddress()) return result;
        byte[] bytes = address.getAddress();
        long ip = 0;
        for (byte b : bytes) ip = (ip << 8) | (b & 255);
        int bounded = Math.max(24, prefix);
        long mask = (0xffffffffL << (32 - bounded)) & 0xffffffffL;
        long network = ip & mask;
        long broadcast = network | (~mask & 0xffffffffL);
        for (long host = network + 1; host < broadcast; host++) {
            if (host == ip) continue;
            result.add(((host >>> 24) & 255) + "." + ((host >>> 16) & 255) + "."
                    + ((host >>> 8) & 255) + "." + (host & 255));
        }
        return result;
    }

    public static boolean contains(InetAddress local, int prefix, InetAddress peer) {
        if (!(local instanceof Inet4Address) || !(peer instanceof Inet4Address) || prefix < 0 || prefix > 32
                || peer.equals(local) || peer.isAnyLocalAddress() || peer.isLoopbackAddress() || peer.isMulticastAddress()) return false;
        byte[] left = local.getAddress(), right = peer.getAddress();
        for (int bit = 0; bit < prefix; bit++) {
            int mask = 1 << (7 - bit % 8);
            if ((left[bit / 8] & mask) != (right[bit / 8] & mask)) return false;
        }
        return true;
    }
}
