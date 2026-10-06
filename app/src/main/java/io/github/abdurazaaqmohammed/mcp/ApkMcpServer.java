package io.github.abdurazaaqmohammed.mcp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loopback-only MCP server hosting the APK tools inside the app.
 *
 * <p>Streamable HTTP transport in its simplest supported shape: the client POSTs one JSON-RPC
 * message to {@code /mcp} and gets one JSON answer back, then the connection closes. Nothing is
 * reachable from other devices (binds 127.0.0.1 only) and browser pages are refused outright via
 * the Origin and Host checks, so a web page cannot drive the tools while the server is up.
 */
public final class ApkMcpServer {

    public static final int DEFAULT_PORT = 8765;
    private static final int PORTS_TO_TRY = 10;
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private static final int MAX_LINE = 8 * 1024;

    private static final Object LOCK = new Object();
    private static ServerSocket serverSocket;
    private static volatile boolean running;
    private static volatile int port;
    private static volatile Context appContext;

    private ApkMcpServer() {
    }

    /** Starts the server on the first free port at or above {@link #DEFAULT_PORT}. */
    public static boolean start(Context context) {
        synchronized (LOCK) {
            if (running) return true;
            appContext = context.getApplicationContext();
            for (int candidate = DEFAULT_PORT; candidate < DEFAULT_PORT + PORTS_TO_TRY; candidate++) {
                try {
                    ServerSocket ss = new ServerSocket(candidate, 50,
                            InetAddress.getByName("127.0.0.1"));
                    serverSocket = ss;
                    port = candidate;
                    running = true;
                    Thread accept = new Thread(() -> acceptLoop(ss), "apk-mcp-accept");
                    accept.setDaemon(true);
                    accept.start();
                    return true;
                } catch (IOException ignored) {
                    // Port taken, try the next one.
                }
            }
            running = false;
            return false;
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            running = false;
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
                serverSocket = null;
            }
            port = 0;
        }
    }

    public static boolean isRunning() {
        return running;
    }

    /** The URL an MCP client connects to, e.g. {@code http://127.0.0.1:8765/mcp}. */
    public static String url() {
        return "http://127.0.0.1:" + port + "/mcp";
    }

    private static void acceptLoop(ServerSocket ss) {
        while (running && !ss.isClosed()) {
            try {
                Socket client = ss.accept();
                Thread worker = new Thread(() -> handle(client), "apk-mcp-conn");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (ss.isClosed()) break;
            }
        }
    }

    private static void handle(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(30000);
            InputStream in = s.getInputStream();

            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                respond(s, 400, "Bad Request", jsonBytes("{\"error\":\"bad request\"}"));
                return;
            }
            String method = parts[0];

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim()
                            .toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
            }

            // A browser always sends Origin; CLI clients never do. Host is checked too, which
            // closes the DNS-rebinding hole where a public name resolves to this machine.
            if (headers.containsKey("origin")) {
                respond(s, 403, "Forbidden", jsonBytes("{\"error\":\"origin refused\"}"));
                return;
            }
            String host = headers.get("host");
            if (host != null && !isLoopbackHost(host)) {
                respond(s, 403, "Forbidden", jsonBytes("{\"error\":\"host refused\"}"));
                return;
            }

            if (!"POST".equals(method)) {
                respond(s, 405, "Method Not Allowed", jsonBytes("{\"error\":\"use POST\"}"));
                return;
            }

            int length = 0;
            try {
                String len = headers.get("content-length");
                length = len == null ? 0 : Integer.parseInt(len.trim());
            } catch (NumberFormatException ignored) {
            }
            if (length < 0 || length > MAX_BODY) {
                respond(s, 413, "Payload Too Large", jsonBytes("{\"error\":\"body too large\"}"));
                return;
            }
            byte[] body = readFully(in, length);
            if (body == null) {
                respond(s, 400, "Bad Request", jsonBytes("{\"error\":\"truncated body\"}"));
                return;
            }

            Context ctx = appContext;
            if (ctx == null) {
                respond(s, 503, "Service Unavailable", jsonBytes("{\"error\":\"not ready\"}"));
                return;
            }

            JsonRpcResult result = dispatchBody(ctx, body);
            if (result.body == null) {
                // A pure notification (or an empty batch): acknowledge without a message.
                respondEmpty(s, 202, "Accepted");
            } else {
                respond(s, 200, "OK", result.body);
            }
        } catch (Exception ignored) {
            // A broken connection is just that; nothing to clean up.
        }
    }

    private static final class JsonRpcResult {
        final byte[] body;

        JsonRpcResult(byte[] body) {
            this.body = body;
        }
    }

    private static JsonRpcResult dispatchBody(Context ctx, byte[] body) throws Exception {
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return new JsonRpcResult(jsonBytes(errorBody(null, -32700, "Parse error")));
        }
        if (text.startsWith("[")) {
            JSONArray batch = new JSONArray(text);
            JSONArray out = new JSONArray();
            for (int i = 0; i < batch.length(); i++) {
                JSONObject one = batch.optJSONObject(i);
                if (one == null) continue;
                JSONObject resp = dispatchMessage(ctx, one);
                if (resp != null) out.put(resp);
            }
            return new JsonRpcResult(out.length() == 0 ? null : jsonBytes(out.toString()));
        }
        JSONObject resp = dispatchMessage(ctx, new JSONObject(text));
        return new JsonRpcResult(resp == null ? null : jsonBytes(resp.toString()));
    }

    /** One JSON-RPC message; returns null for notifications, which get no answer. */
    private static JSONObject dispatchMessage(Context ctx, JSONObject req) {
        Object id = req.isNull("id") ? null : req.opt("id");
        String method = req.optString("method", "");
        JSONObject params = req.optJSONObject("params");
        if (params == null) params = new JSONObject();
        if (id == null) {
            // notifications/initialized, notifications/cancelled, ... - nothing to answer.
            return null;
        }
        JSONObject resp = new JSONObject();
        try {
            resp.put("jsonrpc", "2.0");
            resp.put("id", id);
            resp.put("result", dispatch(ctx, method, params));
        } catch (RpcException e) {
            try {
                resp.put("jsonrpc", "2.0");
                resp.put("id", id);
                resp.put("error", new JSONObject().put("code", e.code).put("message", e.message));
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            try {
                resp.put("jsonrpc", "2.0");
                resp.put("id", id);
                resp.put("error", new JSONObject().put("code", -32603)
                        .put("message", String.valueOf(e)));
            } catch (Exception ignored) {
            }
        }
        return resp;
    }

    private static JSONObject dispatch(Context ctx, String method, JSONObject params)
            throws RpcException {
        switch (method) {
            case "initialize": {
                JSONObject result = new JSONObject();
                result.put("protocolVersion",
                        params.optString("protocolVersion", "2025-06-18"));
                result.put("capabilities",
                        new JSONObject().put("tools",
                                new JSONObject().put("listChanged", false)));
                result.put("serverInfo", new JSONObject()
                        .put("name", "mp-manager-apk-mcp")
                        .put("version", "1.0.0"));
                return result;
            }
            case "ping":
                return new JSONObject();
            case "tools/list": {
                JSONObject result = new JSONObject();
                result.put("tools", ApkMcpTools.list());
                return result;
            }
            case "tools/call": {
                String name = params.optString("name", "");
                if (!ApkMcpTools.known(name)) {
                    throw new RpcException(-32602, "Unknown tool: " + name);
                }
                JSONObject args = params.optJSONObject("arguments");
                if (args == null) args = new JSONObject();
                JSONObject result = new JSONObject();
                try {
                    String text = ApkMcpTools.call(ctx, name, args);
                    result.put("content", new JSONArray().put(
                            new JSONObject().put("type", "text").put("text", text)));
                    result.put("isError", false);
                } catch (Exception e) {
                    // Tool failures travel as an isError result so the model can read them.
                    result.put("content", new JSONArray().put(
                            new JSONObject().put("type", "text")
                                    .put("text", String.valueOf(e.getMessage()))));
                    result.put("isError", true);
                }
                return result;
            }
            default:
                throw new RpcException(-32601, "Method not found: " + method);
        }
    }

    private static final class RpcException extends Exception {
        final int code;

        RpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static String errorBody(Object id, int code, String message) {
        try {
            return new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id == null ? JSONObject.NULL : id)
                    .put("error", new JSONObject().put("code", code).put("message", message))
                    .toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ------------------------------------------------------------------ HTTP plumbing

    private static boolean isLoopbackHost(String host) {
        String bare = host.trim().toLowerCase(Locale.ROOT);
        int colon = bare.lastIndexOf(':');
        if (colon > 0 && bare.indexOf(']') < 0) bare = bare.substring(0, colon);
        return bare.equals("127.0.0.1") || bare.equals("localhost") || bare.equals("[::1]")
                || bare.startsWith("127.");
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(64);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > MAX_LINE) throw new IOException("header line too long");
        }
        if (b == -1 && buf.size() == 0) return null;
        return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(data, off, length - off);
            if (n < 0) return null;
            off += n;
        }
        return data;
    }

    private static byte[] jsonBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(Socket s, int code, String status, byte[] body)
            throws IOException {
        OutputStream out = s.getOutputStream();
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static void respondEmpty(Socket s, int code, String status) throws IOException {
        OutputStream out = s.getOutputStream();
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Length: 0\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }
}
