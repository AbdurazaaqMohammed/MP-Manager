package io.github.abdurazaaqmohammed.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP protocol layer: JSON-RPC 2.0 dispatch for initialize, tools/list,
 * tools/call and ping over the transports served by {@link McpHttpServer}.
 * Tool execution happens on the HTTP thread pool; the heavyweight ones
 * (decompile, build, unpin) may take a while, which Streamable HTTP clients
 * tolerate with their request timeouts.
 */
public class McpProtocol implements McpHttpServer.JsonRpcHandler {

    public static final String SERVER_NAME = "MP Manager";
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpToolRegistry registry;
    private final String serverVersion;
    private final String homePath;

    public McpProtocol(McpToolRegistry registry, String serverVersion, String homePath) {
        this.registry = registry;
        this.serverVersion = serverVersion;
        this.homePath = homePath;
    }

    @Override
    public JsonElement[] handle(JsonElement[] requests, String sessionId) {
        List<JsonElement> responses = new ArrayList<>();
        for (JsonElement request : requests) {
            if (!request.isJsonObject()) {
                responses.add(McpJson.errorResponse(null, -32600, "Invalid Request"));
                continue;
            }
            JsonObject response = handleOne(request.getAsJsonObject(), sessionId);
            if (response != null) {
                responses.add(response);
            }
        }
        return responses.toArray(new JsonElement[0]);
    }

    private JsonObject handleOne(JsonObject request, String sessionId) {
        JsonElement id = request.has("id") && !request.get("id").isJsonNull()
                ? request.get("id") : null;
        String method = McpJson.str(request, "method", null);
        if (method == null) {
            return id == null ? null : McpJson.errorResponse(id, -32600, "Missing method");
        }
        // Notifications (no id) produce no response.
        if (id == null) {
            return null;
        }
        JsonObject params = McpJson.objectArg(request, "params");
        try {
            switch (method) {
                case "initialize":
                    return McpJson.resultResponse(id, initialize(params));
                case "ping":
                    return McpJson.resultResponse(id, new JsonObject());
                case "tools/list":
                    JsonObject list = new JsonObject();
                    list.add("tools", registry.listTools());
                    return McpJson.resultResponse(id, list);
                case "tools/call":
                    return McpJson.resultResponse(id, toolsCall(params));
                case "resources/list":
                    JsonObject resources = new JsonObject();
                    resources.add("resources", new JsonArray());
                    return McpJson.resultResponse(id, resources);
                case "prompts/list":
                    JsonObject prompts = new JsonObject();
                    prompts.add("prompts", new JsonArray());
                    return McpJson.resultResponse(id, prompts);
                default:
                    return McpJson.errorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            return McpJson.errorResponse(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private JsonObject initialize(JsonObject params) {
        JsonObject result = new JsonObject();
        String requested = McpJson.str(params, "protocolVersion", PROTOCOL_VERSION);
        // Echo the client's version when it looks plausible; the spec allows
        // any compatible version negotiation and this maximizes client compat.
        result.addProperty("protocolVersion",
                requested == null || requested.isEmpty() ? PROTOCOL_VERSION : requested);
        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", serverVersion);
        result.add("serverInfo", serverInfo);
        result.addProperty("instructions",
                "MP Manager on-device file, ZIP and APK tools. Files: list/read/edit/search/upload/download"
                        + " via mp_file_*. Archives: mp_zip_*. APKs (specialty): open a session with mp_apk_open"
                        + " (path or installed package), analyze with mp_apk_info/manifest/list_classes/decompile_class,"
                        + " edit with mp_apk_write_entry/set_string, rebuild+sign with mp_apk_build, patch SSL pinning"
                        + " with mp_apk_ssl_unpin. Relative paths resolve against the home folder: " + homePath);
        return result;
    }

    private JsonObject toolsCall(JsonObject params) throws McpJson.McpToolException {
        if (params == null) {
            throw new McpJson.McpToolException("tools/call requires params");
        }
        String name = McpJson.reqStr(params, "name");
        JsonObject args = McpJson.objectArg(params, "arguments");
        if (args == null) args = new JsonObject();
        JsonObject result = registry.call(name, args);
        if (result == null) {
            throw new McpJson.McpToolException("Unknown tool: " + name);
        }
        return result;
    }
}
