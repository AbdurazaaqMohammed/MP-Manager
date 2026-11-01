package io.github.abdurazaaqmohammed.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declares every MCP tool (name, description, JSON schema) and dispatches
 * tools/call to the implementing classes. Tool names use the mp_ prefix
 * (MP Manager's counterpart of MT Manager's mt_ prefix):
 * mp_file_* files, mp_zip_* archives, mp_apk_* APK analysis and editing.
 */
public class McpToolRegistry {

    private final McpToolContext ctx;
    private final McpFileTools fileTools;
    private final McpZipTools zipTools;
    private final McpApkTools apkTools;

    public McpToolRegistry(McpToolContext ctx) {
        this.ctx = ctx;
        this.fileTools = new McpFileTools(ctx);
        this.zipTools = new McpZipTools(ctx, fileTools);
        this.apkTools = new McpApkTools(ctx);
    }

    public JsonArray listTools() {
        JsonArray tools = new JsonArray();
        for (Map.Entry<String, ToolDef> entry : tools().entrySet()) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", entry.getKey());
            tool.addProperty("description", entry.getValue().description);
            tool.add("inputSchema", entry.getValue().schema);
            tools.add(tool);
        }
        return tools;
    }

    /** @return the tool result as an MCP CallToolResult, or null when the tool name is unknown. */
    public JsonObject call(String name, JsonObject args) {
        try {
            JsonObject result = dispatch(name, args == null ? new JsonObject() : args);
            if (result == null) {
                return McpJson.textResult("ok");
            }
            return McpJson.textResult((JsonElement) result);
        } catch (McpJson.McpToolException e) {
            return McpJson.errorResult(e.getMessage());
        } catch (Exception e) {
            return McpJson.errorResult("Tool failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private JsonObject dispatch(String name, JsonObject args) throws McpJson.McpToolException {
        switch (name) {
            // ---------------- files ----------------
            case "mp_file_list_roots": return fileTools.listRoots(args);
            case "mp_file_list": return fileTools.list(args);
            case "mp_file_info": return fileTools.info(args);
            case "mp_file_read_text": return fileTools.readText(args);
            case "mp_file_read_bytes": return fileTools.readBytes(args);
            case "mp_file_write_text": return fileTools.writeText(args);
            case "mp_file_write_bytes": return fileTools.writeBytes(args);
            case "mp_file_upload": return fileTools.upload(args);
            case "mp_file_download": return fileTools.download(args);
            case "mp_file_search": return fileTools.search(args);
            case "mp_file_mkdir": return fileTools.mkdir(args);
            case "mp_file_touch": return fileTools.touch(args);
            case "mp_file_copy": return fileTools.copy(args);
            case "mp_file_move": return fileTools.move(args);
            case "mp_file_rename": return fileTools.rename(args);
            case "mp_file_delete": return fileTools.delete(args);
            // ---------------- zip ----------------
            case "mp_zip_list": return zipTools.list(args);
            case "mp_zip_read": return zipTools.read(args);
            case "mp_zip_read_bytes": return zipTools.readBytes(args);
            case "mp_zip_create": return zipTools.create(args);
            case "mp_zip_add_files": return zipTools.addFiles(args);
            case "mp_zip_add_entry": return zipTools.addEntry(args);
            case "mp_zip_delete_entries": return zipTools.deleteEntries(args);
            case "mp_zip_rename_entry": return zipTools.renameEntry(args);
            case "mp_zip_extract": return zipTools.extract(args);
            case "mp_zip_check": return zipTools.check(args);
            // ---------------- apk ----------------
            case "mp_apk_list_installed": return apkTools.listInstalled(args);
            case "mp_apk_install": return apkTools.install(args);
            case "mp_apk_open": return apkTools.open(args);
            case "mp_apk_sessions": return apkTools.sessions(args);
            case "mp_apk_close": return apkTools.closeSession(args);
            case "mp_apk_info": return apkTools.info(args);
            case "mp_apk_manifest": return apkTools.manifest(args);
            case "mp_apk_list_entries": return apkTools.listEntries(args);
            case "mp_apk_read_entry": return apkTools.readEntry(args);
            case "mp_apk_write_entry": return apkTools.writeEntry(args);
            case "mp_apk_delete_entry": return apkTools.deleteEntry(args);
            case "mp_apk_add_files": return apkTools.addFilesToApk(args);
            case "mp_apk_list_strings": return apkTools.listStrings(args);
            case "mp_apk_set_string": return apkTools.setString(args);
            case "mp_apk_list_classes": return apkTools.listClasses(args);
            case "mp_apk_decompile_class": return apkTools.decompileClass(args);
            case "mp_apk_build": return apkTools.build(args);
            case "mp_apk_sign": return apkTools.sign(args);
            case "mp_apk_ssl_unpin": return apkTools.sslUnpin(args);
            default:
                return null;
        }
    }

    private static class ToolDef {
        final String description;
        final JsonObject schema;

        ToolDef(String description, JsonObject schema) {
            this.description = description;
            this.schema = schema;
        }
    }

    private static JsonObject schema(String... requiredAndProps) {
        return schemaProps(new String[0], requiredAndProps);
    }

    /** props: alternating name, type, description; required: names listed first. */
    private static JsonObject schemaProps(String[] required, String... props) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        for (int i = 0; i + 2 < props.length; i += 3) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", props[i + 1]);
            prop.addProperty("description", props[i + 2]);
            properties.add(props[i], prop);
        }
        schema.add("properties", properties);
        JsonArray req = new JsonArray();
        for (String r : required) req.add(r);
        schema.add("required", req);
        return schema;
    }

    private Map<String, ToolDef> toolMap;

    private Map<String, ToolDef> tools() {
        if (toolMap != null) return toolMap;
        toolMap = new LinkedHashMap<>();
        String home = ctx.home().getPath();

        // ---------------- files ----------------
        put("mp_file_list_roots",
                "List accessible storage roots, the MCP home folder (always read/write, currently " + home + "),"
                        + " and the user's path rules. Call this first to learn what paths the AI can touch.",
                schema());
        put("mp_file_list",
                "List a directory's entries with type, size and modified date.",
                schemaProps(new String[]{"path"},
                        "path", "string", "Absolute path, or relative to the MCP home folder",
                        "show_hidden", "boolean", "Include hidden files (default false)"));
        put("mp_file_info",
                "Get file/folder details: type, size, dates, permissions and SHA-256 for files.",
                schema("path"));
        put("mp_file_read_text",
                "Read a text file (UTF-8, up to 10 MiB). Optional line windowing.",
                schemaProps(new String[]{"path"},
                        "path", "string", "File path",
                        "start_line", "integer", "1-based first line to return (default 1)",
                        "max_lines", "integer", "Maximum number of lines to return (default all)"));
        put("mp_file_read_bytes",
                "Read binary data as base64 in pages of at most 64 KiB using offset/length.",
                schemaProps(new String[]{"path"},
                        "path", "string", "File path",
                        "offset", "integer", "Byte offset to start from (default 0)",
                        "length", "integer", "Bytes to read, max 65536 (default 65536)"));
        put("mp_file_write_text",
                "Create or overwrite (or append) a text file. Directories are created as needed. Edits are permanent.",
                schemaProps(new String[]{"path", "content"},
                        "path", "string", "File path",
                        "content", "string", "Full text content to write",
                        "append", "boolean", "Append instead of overwrite (default false)"));
        put("mp_file_write_bytes",
                "Create or overwrite (or append) a binary file from base64. Max 32 MiB per call; chunk with append=true.",
                schemaProps(new String[]{"path", "base64"},
                        "path", "string", "File path",
                        "base64", "string", "base64-encoded content",
                        "append", "boolean", "Append instead of overwrite (default false)"));
        put("mp_file_upload",
                "Upload a file from the AI device to this phone. Same parameters as mp_file_write_bytes.",
                schemaProps(new String[]{"path", "base64"},
                        "path", "string", "Destination path on the phone",
                        "base64", "string", "base64-encoded file content",
                        "append", "boolean", "Append instead of overwrite (default false)"));
        put("mp_file_download",
                "Download a whole file (max 32 MiB) from the phone to the AI device as base64.",
                schema("path"));
        put("mp_file_search",
                "Search file names (and optionally contents) under a folder, with regex, case, size filters.",
                schemaProps(new String[]{"path", "query"},
                        "path", "string", "Folder to search",
                        "query", "string", "Name/content substring or regex",
                        "search_content", "boolean", "Also match file contents (default false)",
                        "regex", "boolean", "Treat query as regex (default false)",
                        "case_sensitive", "boolean", "Match case (default false)",
                        "include_hidden", "boolean", "Include hidden files (default false)",
                        "max_results", "integer", "Stop after this many matches (default 100, max 2000)",
                        "min_size", "integer", "Minimum file size in bytes",
                        "max_size", "integer", "Maximum file size in bytes"));
        put("mp_file_mkdir",
                "Create a directory (parents created too).",
                schema("path"));
        put("mp_file_touch",
                "Create an empty file, or update its modified time if it exists.",
                schema("path"));
        put("mp_file_copy",
                "Copy a file or folder. Fails when the destination exists unless overwrite=true.",
                schemaProps(new String[]{"src", "dst"},
                        "src", "string", "Source path",
                        "dst", "string", "Destination path",
                        "overwrite", "boolean", "Replace existing destination (default false)"));
        put("mp_file_move",
                "Move/rename a file or folder. Fails when the destination exists unless overwrite=true.",
                schemaProps(new String[]{"src", "dst"},
                        "src", "string", "Source path",
                        "dst", "string", "Destination path",
                        "overwrite", "boolean", "Replace existing destination (default false)"));
        put("mp_file_rename",
                "Rename a file or folder within its directory.",
                schemaProps(new String[]{"path", "new_name"},
                        "path", "string", "Path to rename",
                        "new_name", "string", "New plain name (no separators)"));
        put("mp_file_delete",
                "PERMANENTLY delete a file or folder (no recycle bin). Directories need recursive=true.",
                schemaProps(new String[]{"path"},
                        "path", "string", "Path to delete",
                        "recursive", "boolean", "Delete non-empty directories recursively (default false)"));

        // ---------------- zip ----------------
        put("mp_zip_list",
                "List a ZIP/APK archive's entries without extracting (name, sizes, CRC, date).",
                schemaProps(new String[]{"zip"},
                        "zip", "string", "Archive path",
                        "filter", "string", "Only entries whose name contains this substring",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_read",
                "Read a text entry from an archive without extracting (max 10 MiB).",
                schemaProps(new String[]{"zip", "entry"},
                        "zip", "string", "Archive path",
                        "entry", "string", "Entry name inside the archive",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_read_bytes",
                "Read a binary entry from an archive as base64 (max 32 MiB).",
                schemaProps(new String[]{"zip", "entry"},
                        "zip", "string", "Archive path",
                        "entry", "string", "Entry name inside the archive",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_create",
                "Create a new ZIP from files/folders with compression level 0-9 and optional password.",
                schemaProps(new String[]{"zip", "paths"},
                        "zip", "string", "New archive path (must not exist)",
                        "paths", "array", "Files or folders to add",
                        "level", "integer", "Compression level 0-9 (default 5)",
                        "password", "string", "Optional AES-256 password"));
        put("mp_zip_add_files",
                "Add files/folders to an existing (or new) ZIP, replacing same-name entries.",
                schemaProps(new String[]{"zip", "paths"},
                        "zip", "string", "Archive path",
                        "paths", "array", "Files or folders to add",
                        "base_dir", "string", "Folder prefix to store them under (optional)",
                        "level", "integer", "Compression level 0-9 (default 5)",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_add_entry",
                "Create/update one entry from text or base64 content (updates ZIP in place).",
                schemaProps(new String[]{"zip", "entry"},
                        "zip", "string", "Archive path",
                        "entry", "string", "Entry name inside the archive",
                        "content", "string", "Text content",
                        "base64", "string", "Or binary content as base64",
                        "level", "integer", "Compression level 0-9 (default 5)",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_delete_entries",
                "Delete entries from a ZIP permanently.",
                schemaProps(new String[]{"zip", "entries"},
                        "zip", "string", "Archive path",
                        "entries", "array", "Entry names to delete",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_rename_entry",
                "Rename an entry inside a ZIP.",
                schemaProps(new String[]{"zip", "entry", "new_entry"},
                        "zip", "string", "Archive path",
                        "entry", "string", "Existing entry name",
                        "new_entry", "string", "New entry name",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_extract",
                "Extract the whole archive or selected entries to a folder.",
                schemaProps(new String[]{"zip", "dest"},
                        "zip", "string", "Archive path",
                        "dest", "string", "Destination folder",
                        "entries", "array", "Only extract these entry names (default all)",
                        "password", "string", "Archive password if encrypted"));
        put("mp_zip_check",
                "Check archive validity, encryption and per-entry CRC integrity.",
                schemaProps(new String[]{"zip"},
                        "zip", "string", "Archive path",
                        "password", "string", "Archive password if encrypted"));

        // ---------------- apk ----------------
        put("mp_apk_list_installed",
                "List installed apps (label, package, version, APK path) with optional name/package filter.",
                schemaProps(new String[0], "filter", "string", "Substring to match label or package name"));
        put("mp_apk_open",
                "Open an APK for analysis/editing: by path or by installed package name. Returns a session id;"
                        + " all edit tools work on a session copy so the source APK is never modified.",
                schemaProps(new String[0],
                        "path", "string", "Path to an .apk file",
                        "package", "string", "Or an installed package name"));
        put("mp_apk_sessions",
                "List open APK edit sessions.", schema());
        put("mp_apk_close",
                "Close an APK session and delete its working copy.", schema("session"));
        put("mp_apk_info",
                "Analyze an open APK session: package, versions, SDKs, permissions, component counts,"
                        + " dex list, size and signing summary.",
                schema("session"));
        put("mp_apk_manifest",
                "Get the decoded AndroidManifest.xml of an open session as text.",
                schema("session"));
        put("mp_apk_list_entries",
                "List the entries (files) inside the session's APK.",
                schemaProps(new String[]{"session"},
                        "session", "string", "APK session id",
                        "filter", "string", "Only entries whose name contains this substring"));
        put("mp_apk_read_entry",
                "Read an entry of the session APK (text auto-detected, else base64).",
                schemaProps(new String[]{"session", "entry"},
                        "session", "string", "APK session id",
                        "entry", "string", "Entry name, e.g. res/values/strings.xml or classes.dex"));
        put("mp_apk_write_entry",
                "Replace an entry's content in the session APK from text or base64,"
                        + " creating it when missing. Binary XML (manifest, res/**) and dex should only be"
                        + " replaced with valid binary content of the same kind.",
                schemaProps(new String[]{"session", "entry"},
                        "session", "string", "APK session id",
                        "entry", "string", "Entry name to replace",
                        "content", "string", "Text content",
                        "base64", "string", "Or binary content as base64"));
        put("mp_apk_delete_entry",
                "Permanently delete an entry from the session APK. Deleting required entries can make the APK unbuildable/uninstallable.",
                schemaProps(new String[]{"session", "entry"},
                        "session", "string", "APK session id",
                        "entry", "string", "Entry name to delete"));
        put("mp_apk_add_files",
                "Add local files (e.g. a new .so, image or asset) into the session APK, replacing same-name entries.",
                schemaProps(new String[]{"session", "paths"},
                        "session", "string", "APK session id",
                        "paths", "array", "Paths of files to add",
                        "base_dir", "string", "Folder prefix inside the APK (optional, e.g. assets/)"));
        put("mp_apk_list_strings",
                "List string resources (ARSCLib) of the session APK with values and configs.",
                schemaProps(new String[]{"session"},
                        "session", "string", "APK session id",
                        "filter", "string", "Match resource name or value substring"));
        put("mp_apk_set_string",
                "Change a string resource's value in the session APK (all configs, or one config).",
                schemaProps(new String[]{"session", "name", "value"},
                        "session", "string", "APK session id",
                        "name", "string", "Resource name, e.g. app_name",
                        "value", "string", "New string value",
                        "config", "string", "Limit to a specific config (optional)"));
        put("mp_apk_list_classes",
                "List Java class names in the session APK's dex files (filterable).",
                schemaProps(new String[]{"session"},
                        "session", "string", "APK session id",
                        "dex", "string", "Only list this dex, e.g. classes2 (optional)",
                        "filter", "string", "Substring of the dotted class name (optional)",
                        "max_results", "integer", "Maximum classes returned (default 500, max 5000)"));
        put("mp_apk_decompile_class",
                "Decompile one class of the session APK to Java source with the bundled jadx.",
                schemaProps(new String[]{"session", "class"},
                        "session", "string", "APK session id",
                        "class", "string", "Fully qualified class name, e.g. com.example.app.MainActivity"));
        put("mp_apk_build",
                "Rebuild the session's modified APK (zipalign) and optionally sign it with the configured"
                        + " signing key (fallback: debug key). Output goes to the home folder by default. The source APK is never modified.",
                schemaProps(new String[]{"session"},
                        "session", "string", "APK session id",
                        "sign", "boolean", "Sign the output (default true)",
                        "output", "string", "Output path (default <home>/output/<name>-mcp-signed.apk)"));
        put("mp_apk_sign",
                "Sign an APK file with the configured signing key (fallback: debug key) and save it next to the input.",
                schemaProps(new String[0],
                        "path", "string", "APK file to sign",
                        "session", "string", "Or sign a session's working copy"));
        put("mp_apk_install",
                "Hand an APK to the Android system installer (a confirmation dialog appears on the phone).",
                schemaProps(new String[0],
                        "path", "string", "APK file to install",
                        "session", "string", "Or install a session's working copy"));
        put("mp_apk_ssl_unpin",
                "Patch an APK to bypass SSL certificate pinning: neutralizes X509TrustManager checks,"
                        + " OkHttp CertificatePinner, OkHostnameVerifier, custom HostnameVerifiers and WebView"
                        + " SSL errors at the dex level, then rebuilds and (optionally) signs. Use only on apps you own"
                        + " or are authorized to test.",
                schemaProps(new String[0],
                        "path", "string", "APK file to patch",
                        "session", "string", "Or patch a session's working copy (session keeps the patched result)",
                        "output", "string", "Output path (default next to input, -unpinned.apk)",
                        "sign", "boolean", "Sign the output (default true)"));
        return toolMap;
    }

    private void put(String name, String description, JsonObject schema) {
        toolMap.put(name, new ToolDef(description, schema));
    }
}
