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
    private final McpNativeTools nativeTools;

    public McpToolRegistry(McpToolContext ctx) {
        this.ctx = ctx;
        this.fileTools = new McpFileTools(ctx);
        this.zipTools = new McpZipTools(ctx, fileTools);
        this.apkTools = new McpApkTools(ctx);
        this.nativeTools = new McpNativeTools(ctx);
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
        } catch (Throwable e) {
            // Throwable, not Exception: an Error (e.g. NoSuchMethodError on an
            // older device) must surface as a tool error, not kill the process.
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
            case "mp_file_append_text": return fileTools.appendText(args);
            case "mp_file_append_bytes": return fileTools.appendBytes(args);
            case "mp_file_replace_text": return fileTools.replaceText(args);
            case "mp_file_patch_bytes": return fileTools.patchBytes(args);
            case "mp_file_truncate_bytes": return fileTools.truncateBytes(args);
            case "mp_file_get_access_policy": return fileTools.accessPolicy(args);
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
            case "mp_apk_jiagu_dump": return apkTools.jiaguDump(args);
            case "mp_apk_jiagu_unpack": return apkTools.jiaguUnpack(args);
            case "mp_apk_copy_entry": return apkTools.copyEntry(args);
            case "mp_apk_search_entries": return apkTools.searchEntries(args);
            case "mp_apk_read_signature": return apkTools.readSignature(args);
            case "mp_apk_list_available_apks": return apkTools.listAvailableApks(args);
            case "mp_apk_dex_outline": return apkTools.dexOutline(args);
            case "mp_apk_dex_xref": return apkTools.dexXref(args);
            // ---------------- native libs ----------------
            case "mp_apk_native_inspect": return nativeTools.inspect(args);
            case "mp_apk_native_list_items": return nativeTools.listItems(args);
            case "mp_apk_native_disassemble": return nativeTools.disassemble(args);
            case "mp_apk_native_map_address": return nativeTools.mapAddress(args);
            case "mp_apk_native_xref": return nativeTools.xref(args);
            case "mp_apk_native_control_flow": return nativeTools.controlFlow(args);
            case "mp_apk_native_patch_string": return nativeTools.patchString(args);
            case "mp_apk_native_patch_bytes": return nativeTools.patchBytes(args);
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
        put("mp_apk_jiagu_dump",
                "Root Jiagu Dumper: launch a Jiagu (com.stub.StubApp) protected app, wait for its stub to unpack"
                        + " the real dexes into memory, and carve every dex out of the process memory through su."
                        + " Optional apk_path rebuilds an unpacked APK from the dump in the same run.",
                schemaProps(new String[]{"package"},
                        "package", "string", "Target app package name, e.g. com.example.app",
                        "wait_seconds", "integer", "Seconds to wait after launch for unpacking (default 15)",
                        "apk_path", "string", "Optional original APK path to rebuild right after the dump"));
        put("mp_apk_jiagu_unpack",
                "Offline Jiagu unpacker: rebuild an installable APK from the original Jiagu APK plus the dex dump"
                        + " produced by mp_apk_jiagu_dump. Replaces the stub application with the real one and NOPs"
                        + " all injected hook calls. Signs the result by default.",
                schemaProps(new String[]{"apk_path"},
                        "apk_path", "string", "Original protected APK path",
                        "dump_dir", "string", "Dump folder (default: newest dump of the APK's package)",
                        "output_dir", "string", "Folder for the rebuilt APK (default: next to the original APK)",
                        "sign", "boolean", "Sign the rebuilt APK with the configured key (default true)"));
        put("mp_apk_install",
                "Hand an APK to the Android system installer (a confirmation dialog appears on the phone).",
                schemaProps(new String[0],
                        "path", "string", "APK file to install",
                        "session", "string", "Or install a session's working copy"));
        put("mp_file_append_text",
                "Append text to a file (creates it when missing).",
                schemaProps(new String[]{"path", "content"},
                        "path", "string", "File path",
                        "content", "string", "Text to append"));
        put("mp_file_append_bytes",
                "Append binary data (base64) to a file.",
                schemaProps(new String[]{"path", "base64"},
                        "path", "string", "File path",
                        "base64", "string", "base64-encoded bytes to append"));
        put("mp_file_replace_text",
                "Replace all occurrences of a substring in a text file (literal or regex).",
                schemaProps(new String[]{"path", "old"},
                        "path", "string", "File path",
                        "old", "string", "Text (or regex) to replace",
                        "new", "string", "Replacement text",
                        "regex", "boolean", "Treat old as regex (default false)"));
        put("mp_file_patch_bytes",
                "Patch raw bytes in a file: at an offset or by searching for old_hex.",
                schemaProps(new String[]{"path", "hex"},
                        "path", "string", "File path",
                        "hex", "string", "New bytes as hex",
                        "old_hex", "string", "Find this hex first and patch there (optional)",
                        "offset", "integer", "Byte offset to patch (used when old_hex is absent)"));
        put("mp_file_truncate_bytes",
                "Truncate (or extend) a file to an exact byte size.",
                schemaProps(new String[]{"path", "size"},
                        "path", "string", "File path",
                        "size", "integer", "New size in bytes"));
        put("mp_file_get_access_policy",
                "Report the effective read/write policy for a path and the rule that decided it. The AI cannot change these.",
                schema("path"));

        put("mp_apk_copy_entry",
                "Copy an entry to a new name inside the session APK (same bytes, new path).",
                schemaProps(new String[]{"session", "entry", "new_entry"},
                        "session", "string", "APK session id",
                        "entry", "string", "Existing entry name",
                        "new_entry", "string", "Destination entry name"));
        put("mp_apk_search_entries",
                "Search APK entries by name and optionally by content.",
                schemaProps(new String[]{"session", "query"},
                        "session", "string", "APK session id",
                        "query", "string", "Substring to find",
                        "search_content", "boolean", "Also match entry contents (default false)",
                        "max_results", "integer", "Stop after this many (default 50, max 500)"));
        put("mp_apk_read_signature",
                "Verify an APK's signature and list its certificates (subject, issuer, SHA-256, validity).",
                schemaProps(new String[0],
                        "path", "string", "APK file",
                        "session", "string", "Or a session's working copy"));
        put("mp_apk_list_available_apks",
                "Find .apk files under a folder (recursive), e.g. the home folder or Download.",
                schemaProps(new String[0],
                        "path", "string", "Folder to scan (default MCP home)",
                        "max_results", "integer", "Stop after this many (default 100)"));
        put("mp_apk_dex_outline",
                "List a class's superclass, interfaces, methods and fields with access flags.",
                schemaProps(new String[]{"session", "class"},
                        "session", "string", "APK session id",
                        "class", "string", "Fully qualified class name"));
        put("mp_apk_dex_xref",
                "Find where a method/class/string/field is referenced across all dexes.",
                schemaProps(new String[]{"session", "query"},
                        "session", "string", "APK session id",
                        "query", "string", "Substring, e.g. Landroid/content/Context;->getPackageName or a string literal",
                        "kind", "string", "method | class | string | field (default method)",
                        "max_results", "integer", "Stop after this many (default 50)"));

        put("mp_apk_native_inspect",
                "Inspect a native .so library (ELF): class, machine, entry, sections, segments,"
                        + " SONAME, needed libs, symbol counts. Pure-Java ELF parser, no extra deps.",
                schemaProps(new String[0],
                        "session", "string", "APK session id (with lib)",
                        "lib", "string", "Library name or entry path, e.g. lib/arm64-v8a/libfoo.so",
                        "path", "string", "Or a direct path to a .so file"));
        put("mp_apk_native_list_items",
                "List native library items: symbols (.symtab/.dynsym), imports, sections, segments"
                        + " or strings (.rodata), with a filter.",
                schemaProps(new String[]{"kind"},
                        "kind", "string", "symbols | dynsym | imports | sections | segments | strings",
                        "session", "string", "APK session id",
                        "lib", "string", "Library name or entry path",
                        "path", "string", "Or a direct path to a .so file",
                        "filter", "string", "Substring filter",
                        "max_results", "integer", "Maximum items returned (default 200, max 2000)",
                        "min_length", "integer", "Min string length for kind=strings (default 6)"));
        put("mp_apk_native_disassemble",
                "Disassemble native code (built-in ARM64 + ARM32/Thumb decoder; other archs fall back to raw bytes)."
                        + " Branch/call targets are annotated with symbol names.",
                schemaProps(new String[0],
                        "session", "string", "APK session id",
                        "lib", "string", "Library name or entry path",
                        "path", "string", "Or a direct path to a .so file",
                        "symbol", "string", "Function symbol to start from",
                        "address", "string", "Or a hex address",
                        "count", "integer", "Instruction count (default 64, max 2000)",
                        "thumb", "boolean", "ARM32: decode in Thumb mode (default true)"));
        put("mp_apk_native_map_address",
                "Map a virtual address to its section, file offset and containing symbol.",
                schemaProps(new String[]{"address"},
                        "address", "string", "Hex address (0x…)",
                        "session", "string", "APK session id",
                        "lib", "string", "Library name or entry path",
                        "path", "string", "Or a direct path to a .so file"));
        put("mp_apk_native_xref",
                "Scan .text for references to an address/symbol: BL calls and ADRP+ADD constant"
                        + " references (AArch64) or BL calls (ARM32).",
                schemaProps(new String[0],
                        "session", "string", "APK session id",
                        "lib", "string", "Library name or entry path",
                        "path", "string", "Or a direct path to a .so file",
                        "symbol", "string", "Symbol to find references to",
                        "address", "string", "Or a hex address",
                        "max_results", "integer", "Maximum hits (default 50, max 500)"));
        put("mp_apk_native_control_flow",
                "Lightweight control-flow overview of one function: call sites (with resolved"
                        + " target symbols) and branch sites.",
                schemaProps(new String[]{"symbol"},
                        "symbol", "string", "Function symbol",
                        "session", "string", "APK session id",
                        "lib", "string", "Library name or entry path",
                        "path", "string", "Or a direct path to a .so file",
                        "thumb", "boolean", "ARM32: decode in Thumb mode (default true)"));
        put("mp_apk_native_patch_string",
                "Patch a NUL-terminated string inside a native library section in place."
                        + " The replacement must not be longer than the original. Writes back into the session APK.",
                schemaProps(new String[]{"old", "new"},
                        "old", "string", "Existing string",
                        "new", "string", "Replacement (length <= old)",
                        "section", "string", "Section to patch (default .rodata)",
                        "session", "string", "APK session id",
                        "lib", "string", "Library name or entry path",
                        "path", "string", "Or a direct path to a .so file"));
        put("mp_apk_native_patch_bytes",
                "Patch raw bytes at a virtual address in a native library; writes back into the session APK.",
                schemaProps(new String[]{"address", "hex"},
                        "address", "string", "Hex address (0x…)",
                        "hex", "string", "New bytes as even-length hex",
                        "session", "string", "APK session id",
                        "lib", "string", "Library name or entry path",
                        "path", "string", "Or a direct path to a .so file"));
        return toolMap;
    }

    private void put(String name, String description, JsonObject schema) {
        toolMap.put(name, new ToolDef(description, schema));
    }
}
