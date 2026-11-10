package io.github.abdurazaaqmohammed.features.files.network;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** Android 17 local-network permission classification. DNS must run off the UI thread. */
public final class NetworkAccess {
    public static final String PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK";
    private NetworkAccess() {}

    public static boolean isLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc); // IPv6 unique-local
    }

    public static boolean requiresPermission(String address) throws UnknownHostException {
        String host = URI.create(address).getHost();
        if (host == null) throw new UnknownHostException("Missing host");
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        String lower = host.toLowerCase(java.util.Locale.ROOT);
        // .local resolution itself is permission-protected. Unqualified hostnames
        // are generally LAN/NetBIOS names, so don't attempt resolution first.
        if (lower.endsWith(".local") || lower.endsWith(".local.") || (!host.contains(".") && !host.contains(":"))) return true;
        for (InetAddress resolved : InetAddress.getAllByName(host)) if (isLocal(resolved)) return true;
        return false;
    }
}
