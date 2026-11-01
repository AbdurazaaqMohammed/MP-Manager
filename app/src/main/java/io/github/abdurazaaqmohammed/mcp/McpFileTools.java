package io.github.abdurazaaqmohammed.mcp;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * MCP file tools: browse, read, edit, search, manage, upload and download.
 * Mirrors MT Manager's mt_file_* tool set. Every path goes through the
 * {@link McpPathPolicy}; edit and delete operations are permanent (no recycle
 * bin), which is surfaced in tool descriptions so the AI can warn the user.
 */
public final class McpFileTools {

    /** Max bytes for a single text read (MT-style limit). */
    static final int MAX_TEXT_BYTES = 10 * 1024 * 1024;
    /** Max bytes for a binary page read (MT-style 64 KiB page). */
    static final int MAX_BINARY_PAGE = 64 * 1024;
    /** Max bytes for whole-file base64 upload/download. */
    static final int MAX_TRANSFER_BYTES = 32 * 1024 * 1024;

    private final McpToolContext ctx;

    public McpFileTools(McpToolContext ctx) {
        this.ctx = ctx;
    }

    private File resolveChecked(String path, boolean write) throws McpJson.McpToolException {
        String normalized = McpPathPolicy.normalizePath(path, ctx.policy.getHome());
        if (normalized == null) {
            throw new McpJson.McpToolException("Missing path parameter");
        }
        File file = ctx.policy.resolve(normalized);
        String canonical;
        try {
            canonical = file.getCanonicalPath();
        } catch (IOException e) {
            canonical = file.getAbsolutePath();
        }
        if (write ? !ctx.policy.canWrite(canonical) : !ctx.policy.canRead(canonical)) {
            throw new McpJson.McpToolException((write ? "Write" : "Read") + " access denied for " + canonical
                    + ". The user can grant access in the MCP service settings (path rules).");
        }
        return file;
    }

    private static JsonObject fileJson(File file) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", file.getName());
        obj.addProperty("path", file.getPath());
        obj.addProperty("type", file.isDirectory() ? "directory" : "file");
        if (file.isFile()) {
            obj.addProperty("size", file.length());
        }
        obj.addProperty("modified", file.lastModified());
        obj.addProperty("hidden", file.isHidden());
        obj.addProperty("canRead", file.canRead());
        obj.addProperty("canWrite", file.canWrite());
        return obj;
    }

    public JsonObject list(JsonObject args) throws McpJson.McpToolException {
        File dir = resolveChecked(McpJson.reqStr(args, "path"), false);
        if (!dir.exists()) {
            throw new McpJson.McpToolException("Path does not exist: " + dir.getPath());
        }
        if (!dir.isDirectory()) {
            throw new McpJson.McpToolException("Not a directory: " + dir.getPath());
        }
        boolean showHidden = McpJson.bool(args, "show_hidden", false);
        File[] children = dir.listFiles();
        JsonArray items = new JsonArray();
        if (children != null) {
            List<File> files = new ArrayList<>(Arrays.asList(children));
            files.sort((a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File child : files) {
                if (!showHidden && child.isHidden()) continue;
                items.add(fileJson(child));
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", dir.getPath());
        result.addProperty("count", items.size());
        result.add("entries", items);
        return result;
    }

    public JsonObject info(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), false);
        if (!file.exists()) {
            throw new McpJson.McpToolException("Path does not exist: " + file.getPath());
        }
        JsonObject obj = fileJson(file);
        if (file.isFile()) {
            try {
                obj.addProperty("sha256", io.github.abdurazaaqmohammed.utils.HashUtil.hashFile(file, "SHA-256"));
            } catch (Exception ignored) {
            }
        }
        return obj;
    }

    public JsonObject listRoots(JsonObject args) {
        JsonObject result = new JsonObject();
        JsonArray roots = new JsonArray();
        File ext = android.os.Environment.getExternalStorageDirectory();
        if (ext != null) {
            JsonObject root = new JsonObject();
            root.addProperty("path", ext.getPath());
            root.addProperty("label", "External storage");
            roots.add(root);
        }
        JsonObject home = new JsonObject();
        home.addProperty("path", ctx.policy.getHome().getPath());
        home.addProperty("label", "MCP home (read/write)");
        roots.add(home);
        result.add("roots", roots);
        result.add("rules", ctx.policyRulesJson());
        return result;
    }

    public JsonObject readText(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), false);
        if (!file.isFile()) {
            throw new McpJson.McpToolException("Not a file: " + file.getPath());
        }
        long length = file.length();
        if (length > MAX_TEXT_BYTES) {
            throw new McpJson.McpToolException("File too large for text read (" + length
                    + " bytes, limit " + MAX_TEXT_BYTES + "). Use mp_file_read_bytes with offset/length pages.");
        }
        byte[] bytes = readAll(file);
        String text = new String(bytes, StandardCharsets.UTF_8);
        int startLine = McpJson.intVal(args, "start_line", 1);
        int maxLines = McpJson.intVal(args, "max_lines", 0);
        if (startLine > 1 || maxLines > 0) {
            String[] lines = text.split("\n", -1);
            int from = Math.max(0, startLine - 1);
            int to = maxLines > 0 ? Math.min(lines.length, from + maxLines) : lines.length;
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < to; i++) {
                sb.append(lines[i]);
                if (i < to - 1) sb.append('\n');
            }
            text = sb.toString();
            JsonObject result = new JsonObject();
            result.addProperty("path", file.getPath());
            result.addProperty("total_lines", lines.length);
            result.addProperty("start_line", from + 1);
            result.addProperty("end_line", to);
            result.addProperty("content", text);
            return result;
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("size", length);
        result.addProperty("content", text);
        return result;
    }

    /** Paged binary read returning base64; mirrors MT's 64 KiB-per-page limit. */
    public JsonObject readBytes(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), false);
        if (!file.isFile()) {
            throw new McpJson.McpToolException("Not a file: " + file.getPath());
        }
        long total = file.length();
        long offset = Math.max(0, McpJson.longVal(args, "offset", 0));
        int length = McpJson.intVal(args, "length", MAX_BINARY_PAGE);
        if (length <= 0) length = MAX_BINARY_PAGE;
        if (length > MAX_BINARY_PAGE) {
            throw new McpJson.McpToolException("length exceeds " + MAX_BINARY_PAGE + " bytes; page through with offset");
        }
        int avail = (int) Math.min(length, Math.max(0, total - offset));
        byte[] chunk = new byte[avail];
        try (InputStream in = new FileInputStream(file)) {
            long skipped = in.skip(offset);
            if (skipped < offset) {
                avail = 0;
            } else {
                int read = 0;
                while (read < avail) {
                    int n = in.read(chunk, read, avail - read);
                    if (n < 0) break;
                    read += n;
                }
                avail = read;
            }
        } catch (IOException e) {
            throw new McpJson.McpToolException("Read failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("total", total);
        result.addProperty("offset", offset);
        result.addProperty("length", avail);
        result.addProperty("eof", offset + avail >= total);
        result.addProperty("base64", avail == 0 ? "" : android.util.Base64.encodeToString(
                java.util.Arrays.copyOf(chunk, avail), android.util.Base64.NO_WRAP));
        return result;
    }

    public JsonObject writeText(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        String content = McpJson.str(args, "content", "");
        boolean append = McpJson.bool(args, "append", false);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        writeBytes(file, bytes, append);
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("size", file.length());
        result.addProperty("appended", append);
        return result;
    }

    public JsonObject writeBytes(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        String base64 = McpJson.reqStr(args, "base64");
        byte[] bytes;
        try {
            bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new McpJson.McpToolException("Invalid base64 data");
        }
        if (bytes.length > MAX_TRANSFER_BYTES) {
            throw new McpJson.McpToolException("Payload too large (limit " + MAX_TRANSFER_BYTES
                    + " bytes). Split large uploads into appends with mp_file_write_bytes(append=true).");
        }
        boolean append = McpJson.bool(args, "append", false);
        writeBytes(file, bytes, append);
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("size", file.length());
        return result;
    }

    /** Upload from the AI device to this phone (same as write_bytes, MT naming). */
    public JsonObject upload(JsonObject args) throws McpJson.McpToolException {
        return writeBytes(args);
    }

    /** Download a whole file to the AI device as base64. */
    public JsonObject download(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), false);
        if (!file.isFile()) {
            throw new McpJson.McpToolException("Not a file: " + file.getPath());
        }
        if (file.length() > MAX_TRANSFER_BYTES) {
            throw new McpJson.McpToolException("File too large to download whole (" + file.length()
                    + " bytes, limit " + MAX_TRANSFER_BYTES + "). ZIP it first or page through with mp_file_read_bytes.");
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("size", file.length());
        result.addProperty("base64", android.util.Base64.encodeToString(readAll(file), android.util.Base64.NO_WRAP));
        return result;
    }

    private static void writeBytes(File file, byte[] bytes, boolean append) throws McpJson.McpToolException {
        File parent = file.getParentFile();
        try {
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Cannot create parent directory " + parent);
            }
            try (FileOutputStream out = new FileOutputStream(file, append)) {
                out.write(bytes);
            }
        } catch (IOException e) {
            throw new McpJson.McpToolException("Write failed: " + e.getMessage());
        }
    }

    /** Explicit append (MT mt_file_append_text parity). */
    public JsonObject appendText(JsonObject args) throws McpJson.McpToolException {
        args = args == null ? new JsonObject() : args;
        args.addProperty("append", true);
        return writeText(args);
    }

    public JsonObject appendBytes(JsonObject args) throws McpJson.McpToolException {
        args = args == null ? new JsonObject() : args;
        args.addProperty("append", true);
        return writeBytes(args);
    }

    /** Replace all occurrences (literal or regex) of a substring in a text file. */
    public JsonObject replaceText(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        if (!file.isFile()) {
            throw new McpJson.McpToolException("Not a file: " + file.getPath());
        }
        String oldText = McpJson.reqStr(args, "old");
        String newText = McpJson.str(args, "new", "");
        boolean regex = McpJson.bool(args, "regex", false);
        byte[] bytes = readAll(file);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new McpJson.McpToolException("File too large for text replace (" + bytes.length + " bytes)");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        int count;
        if (regex) {
            java.util.regex.Pattern pattern;
            try {
                pattern = java.util.regex.Pattern.compile(oldText);
            } catch (Exception e) {
                throw new McpJson.McpToolException("Invalid regex: " + e.getMessage());
            }
            java.util.regex.Matcher matcher = pattern.matcher(text);
            // StringBuffer overload: the StringBuilder variant is API 26+.
            StringBuffer sb = new StringBuffer();
            count = 0;
            while (matcher.find()) {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(newText));
                count++;
            }
            matcher.appendTail(sb);
            text = sb.toString();
        } else {
            count = 0;
            int idx = 0;
            StringBuilder sb = new StringBuilder();
            while (true) {
                int found = text.indexOf(oldText, idx);
                if (found < 0) {
                    sb.append(text, idx, text.length());
                    break;
                }
                sb.append(text, idx, found).append(newText);
                idx = found + oldText.length();
                count++;
            }
        }
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8), false);
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("replaced", count);
        result.addProperty("size", file.length());
        return result;
    }

    /** Patch raw bytes by hex, either at an offset or by searching for old_hex. */
    public JsonObject patchBytes(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        if (!file.isFile()) {
            throw new McpJson.McpToolException("Not a file: " + file.getPath());
        }
        String hex = McpJson.reqStr(args, "hex").replaceAll("[^0-9a-fA-F]", "");
        if (hex.isEmpty() || hex.length() % 2 != 0) {
            throw new McpJson.McpToolException("hex must be a valid even-length hex string");
        }
        byte[] patch = new byte[hex.length() / 2];
        for (int i = 0; i < patch.length; i++) {
            patch[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        String oldHex = McpJson.str(args, "old_hex", null);
        byte[] data = readAll(file);
        long position;
        if (oldHex != null && !oldHex.isEmpty()) {
            String clean = oldHex.replaceAll("[^0-9a-fA-F]", "");
            byte[] search = new byte[clean.length() / 2];
            for (int i = 0; i < search.length; i++) {
                search[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
            int found = -1;
            for (int i = 0; i + search.length <= data.length; i++) {
                boolean eq = true;
                for (int j = 0; j < search.length; j++) {
                    if (data[i + j] != search[j]) {
                        eq = false;
                        break;
                    }
                }
                if (eq) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                throw new McpJson.McpToolException("old_hex not found in file");
            }
            position = found;
        } else {
            position = McpJson.longVal(args, "offset", 0);
        }
        if (position < 0 || position + patch.length > data.length) {
            throw new McpJson.McpToolException("Patch range out of bounds (file size " + data.length + ")");
        }
        byte[] oldBytes = java.util.Arrays.copyOfRange(data, (int) position, (int) position + patch.length);
        System.arraycopy(patch, 0, data, (int) position, patch.length);
        writeBytes(file, data, false);
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("offset", position);
        result.addProperty("old_hex", hexOf(oldBytes));
        result.addProperty("new_hex", hexOf(patch));
        return result;
    }

    /** Truncate or extend a file to an exact size. */
    public JsonObject truncateBytes(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        if (!file.isFile()) {
            throw new McpJson.McpToolException("Not a file: " + file.getPath());
        }
        long size = McpJson.longVal(args, "size", -1);
        if (size < 0) throw new McpJson.McpToolException("size must be >= 0");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw")) {
            raf.setLength(size);
        } catch (IOException e) {
            throw new McpJson.McpToolException("Truncate failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("size", file.length());
        return result;
    }

    /** Reports the effective access policy for a path (AI cannot change it). */
    public JsonObject accessPolicy(JsonObject args) throws McpJson.McpToolException {
        String normalized = McpPathPolicy.normalizePath(McpJson.reqStr(args, "path"), ctx.policy.getHome());
        File file = ctx.policy.resolve(normalized);
        String canonical;
        try {
            canonical = file.getCanonicalPath();
        } catch (IOException e) {
            canonical = file.getAbsolutePath();
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", canonical);
        result.addProperty("in_home", McpPathPolicy.MODE_RW.equals(ctx.policy.matchedMode(canonical))
                && canonical.startsWith(ctx.policy.getHome().getPath()));
        result.addProperty("read", ctx.policy.canRead(canonical));
        result.addProperty("write", ctx.policy.canWrite(canonical));
        String mode = ctx.policy.matchedMode(canonical);
        result.addProperty("matched_rule", mode != null ? mode : "default (read-only in shared storage, else denied)");
        JsonArray rules = new JsonArray();
        for (McpPathPolicy.Rule rule : ctx.policy.getRules()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("path", rule.path);
            obj.addProperty("mode", rule.mode);
            rules.add(obj);
        }
        result.add("rules", rules);
        return result;
    }

    private static String hexOf(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public JsonObject search(JsonObject args) throws McpJson.McpToolException {
        File root = resolveChecked(McpJson.reqStr(args, "path"), false);
        String query = McpJson.reqStr(args, "query");
        boolean searchContent = McpJson.bool(args, "search_content", false);
        boolean regex = McpJson.bool(args, "regex", false);
        boolean caseSensitive = McpJson.bool(args, "case_sensitive", false);
        boolean includeHidden = McpJson.bool(args, "include_hidden", false);
        int maxResults = Math.max(1, Math.min(2000, McpJson.intVal(args, "max_results", 100)));
        long minSize = McpJson.longVal(args, "min_size", -1);
        long maxSize = McpJson.longVal(args, "max_size", -1);
        if (minSize < 0) minSize = 0;
        if (maxSize < 0) maxSize = Long.MAX_VALUE;

        Pattern namePattern = null;
        String needle = caseSensitive ? query : query.toLowerCase();
        if (regex) {
            try {
                namePattern = Pattern.compile(query, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                throw new McpJson.McpToolException("Invalid regex: " + e.getMessage());
            }
        }
        JsonArray matches = new JsonArray();
        int[] truncated = {0};
        searchRecursive(root, needle, namePattern, searchContent, caseSensitive, includeHidden,
                minSize, maxSize, maxResults, matches, truncated, 0);
        JsonObject result = new JsonObject();
        result.addProperty("path", root.getPath());
        result.addProperty("query", query);
        result.addProperty("count", matches.size());
        result.addProperty("truncated", truncated[0] > 0);
        result.add("matches", matches);
        return result;
    }

    private void searchRecursive(File dir, String needle, Pattern namePattern, boolean searchContent,
                                 boolean caseSensitive, boolean includeHidden, long minSize, long maxSize,
                                 int maxResults, JsonArray matches, int[] truncated, int depth) {
        if (matches.size() >= maxResults) {
            truncated[0]++;
            return;
        }
        if (depth > 40) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            if (matches.size() >= maxResults) {
                truncated[0]++;
                break;
            }
            if (!includeHidden && child.isHidden()) continue;
            if (child.isDirectory()) {
                searchRecursive(child, needle, namePattern, searchContent, caseSensitive, includeHidden,
                        minSize, maxSize, maxResults, matches, truncated, depth + 1);
                continue;
            }
            long size = child.length();
            if (size < minSize || size > maxSize) continue;
            boolean nameMatch;
            if (namePattern != null) {
                nameMatch = namePattern.matcher(child.getName()).find();
            } else {
                String name = caseSensitive ? child.getName() : child.getName().toLowerCase();
                nameMatch = name.contains(needle);
            }
            boolean contentMatch = false;
            if (searchContent && size <= MAX_TEXT_BYTES) {
                contentMatch = fileContains(child, needle, caseSensitive);
            }
            if (nameMatch || contentMatch) {
                JsonObject match = fileJson(child);
                match.addProperty("name_matched", nameMatch);
                match.addProperty("content_matched", contentMatch);
                matches.add(match);
            }
        }
    }

    private static boolean fileContains(File file, String needle, boolean caseSensitive) {
        try {
            byte[] bytes = readAll(file);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (caseSensitive) return text.contains(needle);
            return text.toLowerCase().contains(needle);
        } catch (Exception e) {
            return false;
        }
    }

    public JsonObject mkdir(JsonObject args) throws McpJson.McpToolException {
        File dir = resolveChecked(McpJson.reqStr(args, "path"), true);
        if (dir.isDirectory()) {
            JsonObject result = new JsonObject();
            result.addProperty("path", dir.getPath());
            result.addProperty("created", false);
            return result;
        }
        boolean ok = dir.mkdirs();
        if (!ok && !dir.isDirectory()) {
            throw new McpJson.McpToolException("Failed to create directory " + dir.getPath());
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", dir.getPath());
        result.addProperty("created", true);
        return result;
    }

    public JsonObject touch(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        if (file.exists()) {
            if (!file.setLastModified(System.currentTimeMillis())) {
                throw new McpJson.McpToolException("File exists but timestamp could not be updated");
            }
        } else {
            writeBytes(file, new byte[0], false);
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("size", file.length());
        return result;
    }

    private File resolveDest(JsonObject args, String key) throws McpJson.McpToolException {
        return resolveChecked(McpJson.reqStr(args, key), true);
    }

    public JsonObject copy(JsonObject args) throws McpJson.McpToolException {
        File src = resolveChecked(McpJson.reqStr(args, "src"), false);
        File dst = resolveDest(args, "dst");
        if (!src.exists()) throw new McpJson.McpToolException("Source does not exist: " + src.getPath());
        boolean overwrite = McpJson.bool(args, "overwrite", false);
        if (dst.exists() && !overwrite) {
            throw new McpJson.McpToolException("Destination exists: " + dst.getPath() + " (pass overwrite=true)");
        }
        try {
            if (src.isDirectory()) {
                copyTree(src, dst);
            } else {
                streamCopy(src, dst);
            }
        } catch (IOException e) {
            throw new McpJson.McpToolException("Copy failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("src", src.getPath());
        result.addProperty("dst", dst.getPath());
        result.addProperty("size", dst.isDirectory() ? 0 : dst.length());
        return result;
    }

    public JsonObject move(JsonObject args) throws McpJson.McpToolException {
        File src = resolveChecked(McpJson.reqStr(args, "src"), true);
        File dst = resolveDest(args, "dst");
        if (!src.exists()) throw new McpJson.McpToolException("Source does not exist: " + src.getPath());
        boolean overwrite = McpJson.bool(args, "overwrite", false);
        if (dst.exists() && !overwrite) {
            throw new McpJson.McpToolException("Destination exists: " + dst.getPath() + " (pass overwrite=true)");
        }
        // Plain rename fails across mount points, fall back to copy+delete.
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new McpJson.McpToolException("Cannot create destination directory " + parent);
        }
        if (!src.renameTo(dst)) {
            try {
                if (src.isDirectory()) {
                    copyTree(src, dst);
                    deleteTree(src);
                } else {
                    streamCopy(src, dst);
                    if (!src.delete()) {
                        throw new IOException("Could not delete source after copy");
                    }
                }
            } catch (IOException e) {
                throw new McpJson.McpToolException("Move failed: " + e.getMessage());
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("src", src.getPath());
        result.addProperty("dst", dst.getPath());
        return result;
    }

    public JsonObject rename(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        String newName = McpJson.reqStr(args, "new_name");
        if (newName.contains(File.separator)) {
            throw new McpJson.McpToolException("new_name must be a plain name without path separators");
        }
        File target = new File(file.getParentFile(), newName);
        if (target.exists()) {
            throw new McpJson.McpToolException("Target exists: " + target.getPath());
        }
        if (!file.renameTo(target)) {
            throw new McpJson.McpToolException("Rename failed");
        }
        JsonObject result = new JsonObject();
        result.addProperty("old_path", file.getPath());
        result.addProperty("new_path", target.getPath());
        return result;
    }

    public JsonObject delete(JsonObject args) throws McpJson.McpToolException {
        File file = resolveChecked(McpJson.reqStr(args, "path"), true);
        if (!file.exists()) {
            throw new McpJson.McpToolException("Path does not exist: " + file.getPath());
        }
        boolean recursive = McpJson.bool(args, "recursive", false);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null && children.length > 0 && !recursive) {
                throw new McpJson.McpToolException("Directory not empty; pass recursive=true to delete permanently");
            }
        }
        boolean ok = file.isDirectory()
                ? deleteTree(file)
                : file.delete();
        if (!ok) {
            throw new McpJson.McpToolException("Delete failed (permanent operation, no recycle bin)");
        }
        JsonObject result = new JsonObject();
        result.addProperty("path", file.getPath());
        result.addProperty("deleted", true);
        return result;
    }

    private static void streamCopy(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create parent directory " + parent);
        }
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        dst.setLastModified(src.lastModified()); // best effort
    }

    private static void copyTree(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.isDirectory() && !dst.mkdirs() && !dst.isDirectory()) {
                throw new IOException("Cannot create directory " + dst);
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyTree(child, new File(dst, child.getName()));
                }
            }
        } else {
            streamCopy(src, dst);
        }
    }

    private static boolean deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteTree(child)) return false;
            }
        }
        return file.delete();
    }

    private static byte[] readAll(File file) throws McpJson.McpToolException {
        try {
            return java.nio.file.Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new McpJson.McpToolException("Read failed: " + e.getMessage());
        }
    }
}
