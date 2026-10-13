package io.github.abdurazaaqmohammed.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal ADB client that borrows the system ADB server's shell.
 *
 * <p>File managers reach paths outside their own sandbox in three ways: a
 * root binary, a Shizuku helper, or the ADB server itself. This class is the
 * third. On Android 11 and up the system runs an ADB server on
 * {@code 127.0.0.1:5037} whenever wireless debugging is on, and that server will
 * hand out a {@code shell} (uid 2000) session to any local client that speaks the
 * protocol. That shell sits outside this app's sandbox, so it reaches shared
 * locations like {@code /data/local/tmp}, {@code /sdcard} and {@code /system}
 * that the app cannot — while SELinux still applies the shell policy (it is not
 * root).
 *
 * <p>The server speaks a length-prefixed protocol: every request is a 4-hex-digit
 * byte length followed by the ASCII service name, and every reply starts with
 * {@code OKAY} or {@code FAIL}. Two services matter here — {@code shell:} to run a
 * command, and {@code sync:} to transfer files (see {@link AdbSync}).
 *
 * <p>Nothing here pairs or grants anything: the caller must have wireless
 * debugging already enabled. This class only talks to a server that is already
 * running and authorized.
 */
public final class AdbClient implements AutoCloseable {

    /** Loopback endpoint the system ADB server listens on. */
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 5037;

    /** Upper bound on a single reply payload, so a bad server cannot exhaust us. */
    private static final int MAX_PAYLOAD = 8 << 20;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private AdbClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /** Connect to an ADB server. */
    public static AdbClient connect(String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        return new AdbClient(socket);
    }

    /** Connect to the local server with a short default timeout. */
    public static AdbClient connect() throws IOException {
        return connect(DEFAULT_HOST, DEFAULT_PORT, 5_000);
    }

    /**
     * Select a device, then open a service on it and return the raw stream.
     *
     * <p>Two length-prefixed requests go out — the transport selector, then the
     * service — and the caller reads {@code OKAY} before the stream turns binary.
     * Keeping this raw lets {@link AdbSync} drive the binary sync framing itself.
     */
    public InputStream openService(String transport, String service) throws IOException {
        request("host:transport:" + transport);
        sendLengthPrefixed(service);
        expectOkay(service);
        return in;
    }

    /** Run one command as the shell user and return everything it printed. */
    public String shell(String command) throws IOException {
        InputStream stream = openService("local", "shell:" + command);
        return readToEnd(stream);
    }

    /** Ask the server which device it is serving, for a connectivity check. */
    public String deviceModel() throws IOException {
        request("host:transport:local");
        openStream("shell:getprop ro.product.model");
        return readToEnd(in).trim();
    }

    /** Send a length-prefixed service request and require {@code OKAY}. */
    private void request(String service) throws IOException {
        sendLengthPrefixed(service);
        expectOkay(service);
    }

    /** Open a service without consuming its {@code OKAY} into a typed read. */
    private void openStream(String service) throws IOException {
        sendLengthPrefixed(service);
        expectOkay(service);
    }

    private void sendLengthPrefixed(String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > 0xffff) {
            throw new IOException("service name too long: " + bytes.length);
        }
        out.write(String.format("%04x", bytes.length).getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private void expectOkay(String service) throws IOException {
        String status = readAscii(4);
        if ("OKAY".equals(status)) {
            return;
        }
        if ("FAIL".equals(status)) {
            int len = readHexLength();
            throw new IOException("adb refused " + service + ": " + readAscii(len));
        }
        throw new IOException("unexpected adb status '" + status + "' for " + service);
    }

    int readHexLength() throws IOException {
        int len = Integer.parseInt(readAscii(4), 16);
        if (len < 0 || len > MAX_PAYLOAD) {
            throw new IOException("implausible adb payload length");
        }
        return len;
    }

    String readAscii(int count) throws IOException {
        return new String(readFully(count), StandardCharsets.US_ASCII);
    }

    byte[] readFully(int count) throws IOException {
        byte[] buf = new byte[count];
        int off = 0;
        while (off < count) {
            int read = in.read(buf, off, count - off);
            if (read == -1) {
                throw new IOException("adb stream ended early");
            }
            off += read;
        }
        return buf;
    }

    static String readToEnd(InputStream stream) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buf.write(chunk, 0, read);
            if (buf.size() > MAX_PAYLOAD) {
                throw new IOException("adb stream exceeded " + MAX_PAYLOAD + " bytes");
            }
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Raw output side, used by {@link AdbSync} to write request frames. */
    OutputStream writer() {
        return out;
    }

    /** Raw input side, used by {@link AdbSync} to read response frames. */
    InputStream stream() {
        return in;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
