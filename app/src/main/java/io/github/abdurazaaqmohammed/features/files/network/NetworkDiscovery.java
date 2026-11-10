package io.github.abdurazaaqmohammed.features.files.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import io.github.abdurazaaqmohammed.MPManager.R;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** SMB-tab discovery: mDNS, Windows WS-Discovery and a bounded SMB2/3 negotiation scan. */
public final class NetworkDiscovery implements Closeable {
    public interface Listener {
        void found(NetworkProfile profile);
        void status(String message);
    }
    private final Context context;
    private final Listener listener;
    private final NsdManager nsd;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final ExecutorService probes = Executors.newFixedThreadPool(8);
    private final ExecutorService announcements = Executors.newSingleThreadExecutor();
    private final Set<DatagramSocket> sockets = ConcurrentHashMap.newKeySet();
    private WifiManager.MulticastLock multicastLock;
    private final List<NsdManager.DiscoveryListener> discoveries = new ArrayList<>();
    private final ArrayDeque<NsdServiceInfo> resolveQueue = new ArrayDeque<>();
    private final Set<String> seenServices = new HashSet<>();
    private final Map<String, NetworkProfile> seenEndpoints = new LinkedHashMap<>();
    private boolean resolving;

    public NetworkDiscovery(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void start() {
        if (closed.get()) return;
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("mp-network-discovery");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (RuntimeException ignored) { /* Ethernet and filtered Wi-Fi can still use unicast probes. */ }
        if (nsd == null) { listener.status(context.getString(R.string.network_discovery_unavailable)); return; }
        for (String type : new String[]{"_smb._tcp.", "_webdav._tcp.", "_webdavs._tcp."}) {
            NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String serviceType) {}
                @Override public void onDiscoveryStopped(String serviceType) {}
                @Override public void onServiceLost(NsdServiceInfo service) {}
                @Override public void onStartDiscoveryFailed(String serviceType, int error) {
                    post(() -> listener.status(context.getString(R.string.network_discovery_unavailable)));
                }
                @Override public void onStopDiscoveryFailed(String serviceType, int error) {}
                @Override public void onServiceFound(NsdServiceInfo service) {
                    post(() -> {
                        String key = service.getServiceType() + ":" + service.getServiceName();
                        if (seenServices.add(key)) { resolveQueue.add(service); resolveNext(); }
                    });
                }
            };
            discoveries.add(discovery);
            try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery); }
            catch (RuntimeException e) { listener.status(context.getString(R.string.network_discovery_unavailable)); }
        }
    }

    private void post(Runnable task) { main.post(() -> { if (!closed.get()) task.run(); }); }

    private void resolveNext() {
        if (closed.get() || resolving || resolveQueue.isEmpty()) return;
        resolving = true;
        NsdServiceInfo next = resolveQueue.remove();
        try {
            nsd.resolveService(next, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo service, int error) { post(() -> { resolving = false; resolveNext(); }); }
                @Override public void onServiceResolved(NsdServiceInfo service) {
                    post(() -> {
                        try {
                            if (service.getHost() != null && service.getPort() > 0) {
                                String type = service.getServiceType();
                                NetworkProfile profile = new NetworkProfile();
                                profile.name = service.getServiceName();
                                String host = service.getHost().getHostAddress();
                                if (type.contains("_smb.")) {
                                    profile.protocol = "smb";
                                    profile.address = new java.net.URI("smb", null, host, service.getPort(), "/", null, null).toASCIIString();
                                } else {
                                    profile.protocol = "webdav";
                                    byte[] pathValue = service.getAttributes().get("path");
                                    String path = pathValue == null ? "/" : new String(pathValue, StandardCharsets.UTF_8);
                                    if (!path.startsWith("/")) path = "/" + path;
                                    profile.address = new okhttp3.HttpUrl.Builder().scheme(type.contains("_webdavs.") ? "https" : "http")
                                            .host(host).port(service.getPort()).encodedPath(path).build().toString();
                                }
                                emit(profile);
                            }
                        } catch (Exception ignored) { /* Ignore advertisements with invalid endpoints. */ }
                        resolving = false;
                        resolveNext();
                    });
                }
            });
        } catch (RuntimeException e) { resolving = false; resolveNext(); }
    }

    private void emit(NetworkProfile profile) {
        String key = endpointKey(profile);
        NetworkProfile previous = seenEndpoints.get(key);
        String host = java.net.URI.create(profile.address).getHost();
        if (previous == null || (previous.name.equals(host) && !profile.name.equals(host))) {
            seenEndpoints.put(key, profile);
            listener.found(profile);
        }
    }

    public static String endpointKey(NetworkProfile profile) {
        if (!profile.protocol.equals("smb")) return profile.protocol + ":" + profile.address;
        java.net.URI uri = java.net.URI.create(profile.address);
        return "smb:" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + (uri.getPort() == -1 ? 445 : uri.getPort());
    }

    public void scanSmb() {
        if (closed.get() || !scanning.compareAndSet(false, true)) return;
        Lan lan = findLan();
        if (lan == null) {
            scanning.set(false);
            listener.status(context.getString(R.string.network_discovery_no_lan));
            return;
        }
        listener.status(context.getString(R.string.network_discovery_scanning));
        List<String> targets = DiscoverySubnet.hosts(lan.address.getAddress(), lan.address.getPrefixLength());
        announcements.submit(() -> scanWindows(lan));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        AtomicInteger remaining = new AtomicInteger(targets.size());
        for (String host : targets) probes.submit(() -> {
            try {
                if (closed.get() || System.nanoTime() > deadline) return;
                probeSmb(lan, host);
            } catch (Exception ignored) { /* Offline hosts and non-SMB ports are expected. */ }
            finally {
                if (remaining.decrementAndGet() == 0) post(() -> {
                    scanning.set(false);
                    listener.status(context.getString(R.string.network_discovery_finished));
                });
            }
        });
    }

    private record Lan(android.net.Network network, LinkAddress address, LinkProperties properties) {}

    private Lan findLan() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        List<android.net.Network> networks = new ArrayList<>();
        if (cm.getActiveNetwork() != null) networks.add(cm.getActiveNetwork());
        for (android.net.Network network : cm.getAllNetworks()) if (!networks.contains(network)) networks.add(network);
        for (android.net.Network network : networks) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            LinkProperties properties = cm.getLinkProperties(network);
            if (capabilities == null || properties == null || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            for (LinkAddress address : properties.getLinkAddresses())
                if (!DiscoverySubnet.hosts(address.getAddress(), address.getPrefixLength()).isEmpty())
                    return new Lan(network, address, properties);
        }
        return null;
    }

    private void probeSmb(Lan lan, String host) throws java.io.IOException {
        if (closed.get() || Thread.currentThread().isInterrupted()) return;
        // Negotiate SMB2/3, never authenticate or enumerate shares during discovery.
        try (SMBClient client = new SMBClient(SmbConfig.builder().withSoTimeout(1000)
                .withSocketFactory(new TimedSocketFactory(lan.network.getSocketFactory(), 500))
                .withTimeout(1, TimeUnit.SECONDS).build());
             com.hierynomus.smbj.connection.Connection connection = client.connect(host, 445)) {
            NetworkProfile profile = new NetworkProfile();
            profile.protocol = "smb";
            profile.name = host;
            profile.address = "smb://" + host + ":445/";
            post(() -> emit(profile));
            String name = computerName(lan, host);
            if (name != null) {
                NetworkProfile named = new NetworkProfile();
                named.protocol = "smb"; named.name = name; named.address = profile.address;
                post(() -> emit(named));
            }
        }
    }

    private String computerName(Lan lan, String host) {
        if (closed.get()) return null;
        try (DatagramSocket socket = new DatagramSocket(null)) {
            sockets.add(socket);
            try {
                if (closed.get()) return null;
                lan.network.bindSocket(socket);
                socket.bind(new InetSocketAddress(lan.address.getAddress(), 0));
                socket.setSoTimeout(300);
                int transaction = (int) System.nanoTime() & 0xffff;
                byte[] query = NetBiosNames.query(transaction);
                InetAddress peer = InetAddress.getByName(host);
                socket.send(new DatagramPacket(query, query.length, peer, 137));
                byte[] response = new byte[4096];
                DatagramPacket packet = new DatagramPacket(response, response.length);
                socket.receive(packet);
                if (!peer.equals(packet.getAddress()) || packet.getPort() != 137) return null;
                return NetBiosNames.parse(response, packet.getLength(), transaction);
            } finally { sockets.remove(socket); }
        } catch (Exception ignored) { return null; }
    }

    private void scanWindows(Lan lan) {
        try (MulticastSocket socket = new MulticastSocket(null)) {
            sockets.add(socket);
            try {
                if (closed.get()) return;
                lan.network.bindSocket(socket);
                socket.bind(new InetSocketAddress(lan.address.getAddress(), 0));
                NetworkInterface iface = NetworkInterface.getByName(lan.properties.getInterfaceName());
                if (iface != null) socket.setNetworkInterface(iface);
                socket.setTimeToLive(1);
                socket.setSoTimeout(250);
                String id = "urn:uuid:" + java.util.UUID.randomUUID();
                byte[] probe = WsDiscovery.probe(id);
                DatagramPacket request = new DatagramPacket(probe, probe.length, InetAddress.getByName("239.255.255.250"), 3702);
                long start = System.nanoTime(), nextSend = start;
                int sent = 0;
                Set<String> seen = new HashSet<>();
                byte[] bytes = new byte[32768];
                while (!closed.get() && System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5)) {
                    if (sent < 3 && System.nanoTime() >= nextSend) {
                        socket.send(request);
                        nextSend = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250L << sent++);
                    }
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                    try { socket.receive(packet); } catch (SocketTimeoutException ignored) { continue; }
                    if (!DiscoverySubnet.contains(lan.address.getAddress(), lan.address.getPrefixLength(), packet.getAddress())
                            || !WsDiscovery.isProbeMatch(bytes, packet.getLength(), id)) continue;
                    String host = packet.getAddress().getHostAddress();
                    if (seen.size() < 64 && seen.add(host)) {
                        try { probeSmb(lan, host); } catch (Exception ignored) {}
                    }
                }
            } finally { sockets.remove(socket); }
        } catch (Exception ignored) { /* Port-445 and mDNS discovery remain available if multicast is filtered. */ }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        probes.shutdownNow();
        announcements.shutdownNow();
        for (DatagramSocket socket : sockets) socket.close();
        sockets.clear();
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); }
        catch (RuntimeException ignored) {}
        main.post(() -> {
            resolveQueue.clear();
            if (nsd != null) for (NsdManager.DiscoveryListener discovery : discoveries)
                try { nsd.stopServiceDiscovery(discovery); } catch (RuntimeException ignored) {}
            discoveries.clear();
        });
    }
}
