package io.github.abdurazaaqmohammed.features.files.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.SocketFactory;

/** SMBJ's SO_TIMEOUT only bounds reads; its default TCP connect can otherwise hang a scan. */
final class TimedSocketFactory extends SocketFactory {
    private final SocketFactory delegate;
    private final int timeout;

    TimedSocketFactory(SocketFactory delegate, int timeout) {
        this.delegate = delegate;
        this.timeout = timeout;
    }

    @Override public Socket createSocket() throws IOException { return delegate.createSocket(); }
    @Override public Socket createSocket(String host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }
    @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }

    private Socket connect(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = createSocket();
        try {
            if (local != null) socket.bind(local);
            socket.connect(remote, timeout);
            return socket;
        } catch (IOException | RuntimeException e) {
            try { socket.close(); } catch (IOException ignored) {}
            throw e;
        }
    }
}
