package io.github.abdurazaaqmohammed.mcp;

import com.google.gson.JsonElement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal HTTP/1.1 server hosting the MCP endpoint. Two transports are served:
 *
 * - Streamable HTTP at {@code /mcp}: POST a JSON-RPC request (single or batch),
 *   get a {@code application/json} response (the MCP spec allows either JSON
 *   or an SSE stream for a request/response cycle). GET opens an SSE
 *   keep-alive stream for server-initiated messages; DELETE ends a session.
 * - Legacy SSE at {@code /sse}: GET opens an SSE stream, an {@code endpoint}
 *   event points clients at {@code /message?sessionId=...}, and POSTs to that
 *   endpoint are answered on the stream as {@code message} events.
 *
 * Chosen over a library because the app's HTTP needs here are tiny and
 * dependency-free: one listener socket, one thread per connection, bodies read
 * by Content-Length, every request answered then closed (except SSE streams).
 */
public class McpHttpServer {

    public interface JsonRpcHandler {
        JsonElement[] handle(JsonElement[] requests, String sessionId);
    }

    public static final int MAX_BODY_BYTES = 64 * 1024 * 1024;

    private final int port;
    private final String authToken;
    private final JsonRpcHandler handler;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong requestCount = new AtomicLong();

    /** Live legacy-SSE streams by session id. */
    private final Map<String, SseSession> sseSessions = new ConcurrentHashMap<>();

    public McpHttpServer(int port, String authToken, JsonRpcHandler handler) {
        this.port = port;
        this.authToken = authToken;
        this.handler = handler;
    }

    public static class HttpException extends RuntimeException {
        public final int status;
        public HttpException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50);
        running.set(true);
        Thread acceptor = new Thread(this::acceptLoop, "McpServer-Acceptor");
        acceptor.setDaemon(false);
        acceptor.start();
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        for (SseSession session : sseSessions.values()) {
            session.close();
        }
        sseSessions.clear();
        pool.shutdownNow();
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    public int getPort() {
        return port;
    }

    public static String lanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr.isSiteLocalAddress()) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(() -> serve(socket));
            } catch (SocketException e) {
                break; // listener closed
            } catch (IOException e) {
                if (running.get()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }
    }

    private static final class Request {
        String method;
        String path;
        Map<String, String> headers;
        byte[] body;
    }

    private void serve(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            Request request = readRequest(socket.getInputStream());
            if (request == null) {
                writeSimple(socket, 400, "text/plain", "Bad request");
                return;
            }
            requestCount.incrementAndGet();
            String lowerPath = request.path.toLowerCase(Locale.ROOT);
            if (authToken != null && !authToken.isEmpty()) {
                String auth = request.headers.get("authorization");
                if (auth == null || !auth.replace("Bearer ", "").replace("bearer ", "").trim().equals(authToken)) {
                    writeSimple(socket, 401, "text/plain", "Unauthorized");
                    return;
                }
            }
            if (lowerPath.equals("/mcp")) {
                serveStreamableHttp(socket, request);
            } else if (lowerPath.equals("/sse")) {
                serveLegacySse(socket, request);
            } else if (lowerPath.startsWith("/message")) {
                serveLegacyMessage(socket, request);
            } else if (lowerPath.equals("/") || lowerPath.isEmpty()) {
                writeSimple(socket, 200, "text/plain",
                        "MP Manager MCP Server. Streamable HTTP: POST /mcp (or legacy SSE: GET /sse)");
            } else {
                writeSimple(socket, 404, "text/plain", "Not found");
            }
        } catch (HttpException e) {
            writeSimple(socket, e.status, "text/plain", String.valueOf(e.getMessage()));
        } catch (IOException e) {
            // client went away or connection broke; nothing to do
        } catch (Throwable e) {
            try {
                writeSimple(socket, 500, "text/plain", "Internal error: " + e);
            } catch (Exception ignored) {
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void serveStreamableHttp(Socket socket, Request request) throws IOException {
        if ("OPTIONS".equals(request.method)) {
            // Browser-based MCP clients preflight; CORS headers are in every response.
            writeSimple(socket, 204, "text/plain", "");
            return;
        }
        if ("DELETE".equals(request.method)) {
            writeSimple(socket, 200, "text/plain", "Session ended");
            return;
        }
        if ("GET".equals(request.method)) {
            // Server-initiated SSE stream; we have no subscriptions, so it is a
            // keep-alive channel that closes when the client hangs up.
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/event-stream\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            keepAliveStream(out);
            return;
        }
        if (!"POST".equals(request.method)) {
            writeSimple(socket, 405, "text/plain", "Method not allowed");
            return;
        }
        String ct = request.headers.get("content-type");
        if (ct != null && ct.toLowerCase(Locale.ROOT).contains("application/json")) {
            // ok
        }
        JsonElement[] parsed = McpJson.parseArrayOrObject(new String(request.body, StandardCharsets.UTF_8));
        if (parsed == null) {
            writeSimple(socket, 400, "application/json",
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}");
            return;
        }
        String sessionId = request.headers.get("mcp-session-id");
        JsonElement[] responses = handler.handle(parsed, sessionId);
        if (responses.length == 0) {
            // Notifications only: 202 Accepted with no body.
            writeSimple(socket, 202, "text/plain", "Accepted");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonElement response : responses) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(response.toString());
        }
        String newSession = sessionId != null ? sessionId : UUID.randomUUID().toString();
        writeSimple(socket, 200, "application/json", sb.toString(), newSession);
    }

    private void serveLegacySse(Socket socket, Request request) throws IOException {
        if (!"GET".equals(request.method)) {
            writeSimple(socket, 405, "text/plain", "Method not allowed");
            return;
        }
        String sessionId = UUID.randomUUID().toString();
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/event-stream\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        SseSession session = new SseSession(out);
        sseSessions.put(sessionId, session);
        try {
            sendSseEvent(out, "endpoint", "/message?sessionId=" + sessionId);
            keepAliveStream(out);
        } finally {
            sseSessions.remove(sessionId);
            session.close();
        }
    }

    private void serveLegacyMessage(Socket socket, Request request) throws IOException {
        if (!"POST".equals(request.method)) {
            writeSimple(socket, 405, "text/plain", "Method not allowed");
            return;
        }
        String query = request.path.contains("?") ? request.path.substring(request.path.indexOf('?') + 1) : "";
        String sessionId = null;
        for (String pair : query.split("&")) {
            if (pair.startsWith("sessionId=")) sessionId = pair.substring("sessionId=".length());
        }
        JsonElement[] parsed = McpJson.parseArrayOrObject(new String(request.body, StandardCharsets.UTF_8));
        if (parsed == null) {
            writeSimple(socket, 400, "application/json",
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}");
            return;
        }
        JsonElement[] responses = handler.handle(parsed, sessionId);
        SseSession session = sessionId != null ? sseSessions.get(sessionId) : null;
        if (session != null && responses.length > 0) {
            for (JsonElement response : responses) {
                session.send(response.toString());
            }
        }
        writeSimple(socket, 202, "text/plain", "Accepted");
    }

    /** Writes ": ping" comments every ~15s until the stream dies or the server stops. */
    private void keepAliveStream(OutputStream out) {
        try {
            while (running.get()) {
                Thread.sleep(15000);
                out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // client disconnected
        }
    }

    private static void sendSseEvent(OutputStream out, String event, String data) throws IOException {
        out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static final class SseSession {
        private final OutputStream out;
        private volatile boolean open = true;

        SseSession(OutputStream out) {
            this.out = out;
        }

        synchronized void send(String json) {
            if (!open) return;
            try {
                sendSseEvent(out, "message", json);
            } catch (IOException e) {
                open = false;
            }
        }

        void close() {
            open = false;
        }
    }

    /** Reads one CRLF-terminated header line straight from the raw stream, so the
     *  body that follows is never swallowed by a read-ahead buffer. */
    private static String readRawLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') line.write(b);
        }
        return line.toString("UTF-8");
    }

    private static Request readRequest(InputStream in) throws IOException {
        String line = readRawLine(in);
        if (line == null || line.isEmpty()) return null;
        String[] parts = line.split(" ");
        if (parts.length < 2) return null;
        Request request = new Request();
        request.method = parts[0].toUpperCase(Locale.ROOT);
        request.path = parts[1];
        Map<String, String> headers = new HashMap<>();
        while ((line = readRawLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        request.headers = headers;
        int length = 0;
        String cl = headers.get("content-length");
        if (cl != null) {
            try {
                length = Math.max(0, Integer.parseInt(cl.trim()));
            } catch (NumberFormatException e) {
                throw new HttpException(400, "Bad content-length");
            }
        }
        if (length > MAX_BODY_BYTES) {
            throw new HttpException(413, "Body too large (limit " + MAX_BODY_BYTES + " bytes)");
        }
        request.body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(request.body, read, length - read);
            if (n < 0) break;
            read += n;
        }
        return request;
    }

    private static void writeSimple(Socket socket, int status, String contentType, String body) {
        writeSimple(socket, status, contentType, body, null);
    }

    private static void writeSimple(Socket socket, int status, String contentType, String body, String sessionId) {
        try {
            OutputStream out = socket.getOutputStream();
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
            head.append("Content-Type: ").append(contentType).append("; charset=utf-8\r\n");
            head.append("Content-Length: ").append(bytes.length).append("\r\n");
            head.append("Cache-Control: no-cache\r\n");
            head.append("Access-Control-Allow-Origin: *\r\n");
            head.append("Access-Control-Allow-Headers: Authorization, Content-Type, Mcp-Session-Id, Mcp-Protocol-Version, Last-Event-ID\r\n");
            head.append("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n");
            if (sessionId != null) {
                head.append("Mcp-Session-Id: ").append(sessionId).append("\r\n");
            }
            head.append("Connection: close\r\n\r\n");
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 202: return "Accepted";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 413: return "Payload Too Large";
            case 500: return "Internal Server Error";
            default: return "Status " + status;
        }
    }
}
