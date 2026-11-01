package io.github.abdurazaaqmohammed.mcp;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * APK edit sessions for the MCP server. Opening a session copies the APK into
 * {@code <home>/mcp_workspace/<sessionId>/apk.apk}; every edit tool mutates
 * that working copy only — the source APK is never touched — and
 * {@code mp_apk_build} writes the result elsewhere (home by default).
 */
public class McpApkSessions {

    public static final class Session {
        public final String id;
        public final String sourcePath;
        public final File workDir;
        public final File workApk;

        Session(String id, String sourcePath, File workDir, File workApk) {
            this.id = id;
            this.sourcePath = sourcePath;
            this.workDir = workDir;
            this.workApk = workApk;
        }
    }

    private final McpToolContext ctx;
    private final Map<String, Session> sessions = new LinkedHashMap<>();

    public McpApkSessions(McpToolContext ctx) {
        this.ctx = ctx;
    }

    public synchronized Session create(String sourcePath) throws McpJson.McpToolException {
        File source = new File(sourcePath);
        if (!source.isFile()) {
            throw new McpJson.McpToolException("APK not found: " + sourcePath);
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        File workDir = new File(ctx.home(), "mcp_workspace/" + id);
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            throw new McpJson.McpToolException("Cannot create workspace dir " + workDir);
        }
        File workApk = new File(workDir, "apk.apk");
        try {
            org.apache.commons.io.FileUtils.copyFile(source, workApk);
        } catch (IOException e) {
            throw new McpJson.McpToolException("Failed to copy APK into workspace: " + e.getMessage());
        }
        Session session = new Session(id, source.getPath(), workDir, workApk);
        sessions.put(id, session);
        return session;
    }

    public synchronized Session get(String id) throws McpJson.McpToolException {
        Session session = sessions.get(id);
        if (session == null) {
            throw new McpJson.McpToolException("Unknown or closed APK session: " + id
                    + ". Open one with mp_apk_open first; sessions do not survive a server restart.");
        }
        return session;
    }

    public synchronized Map<String, Session> all() {
        return sessions;
    }

    public synchronized boolean close(String id) {
        Session session = sessions.remove(id);
        if (session == null) return false;
        try {
            org.apache.commons.io.FileUtils.deleteDirectory(session.workDir);
        } catch (IOException ignored) {
        }
        return true;
    }

    public synchronized void closeAll() {
        for (String id : sessions.keySet().toArray(new String[0])) {
            close(id);
        }
    }
}
