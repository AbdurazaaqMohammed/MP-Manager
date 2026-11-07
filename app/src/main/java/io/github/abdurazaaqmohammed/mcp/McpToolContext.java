package io.github.abdurazaaqmohammed.mcp;

import android.content.Context;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;

/** Shared services handed to every MCP tool call. */
public class McpToolContext {

    public final Context context;
    public final McpPathPolicy policy;
    public final McpApkSessions apkSessions;

    public McpToolContext(Context context, McpPathPolicy policy) {
        this.context = context.getApplicationContext();
        this.policy = policy;
        this.apkSessions = new McpApkSessions(this);
    }

    public File home() {
        return policy.getHome();
    }

    public JsonElement policyRulesJson() {
        return JsonParser.parseString(
                McpPathPolicy.rulesToJson(policy.getRules()));
    }
}
