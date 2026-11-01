package io.github.abdurazaaqmohammed.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP ZIP tools modeled on MT Manager's mt_zip_* set: browse entries, read
 * without full extraction, check integrity, create with compression level and
 * optional password, and add/replace/delete/rename/extract entries. Built on
 * the zip4j library the app already uses for its archive features.
 */
public final class McpZipTools {

    private final McpToolContext ctx;
    private final McpFileTools files;

    public McpZipTools(McpToolContext ctx, McpFileTools files) {
        this.ctx = ctx;
        this.files = files;
    }

    private File resolveZip(String path, boolean write) throws McpJson.McpToolException {
        File file = ctx.policy.resolve(McpPathPolicy.normalizePath(path, ctx.policy.getHome()));
        String canonical;
        try {
            canonical = file.getCanonicalPath();
        } catch (IOException e) {
            canonical = file.getAbsolutePath();
        }
        if (write ? !ctx.policy.canWrite(canonical) : !ctx.policy.canRead(canonical)) {
            throw new McpJson.McpToolException((write ? "Write" : "Read") + " access denied for " + canonical);
        }
        return file;
    }

    private static ZipFile zip(File file, char[] password) {
        ZipFile zf = new ZipFile(file);
        if (password != null) zf.setPassword(password);
        return zf;
    }

    private static char[] passwordOf(JsonObject args) throws McpJson.McpToolException {
        String password = McpJson.str(args, "password", null);
        return password == null ? null : password.toCharArray();
    }

    private static FileHeader header(ZipFile zf, String entry) throws McpJson.McpToolException {
        try {
            FileHeader header = zf.getFileHeader(entry);
            if (header == null) {
                // Some tools store with a leading slash; try that too.
                header = zf.getFileHeader(entry.startsWith("/") ? entry.substring(1) : "/" + entry);
            }
            if (header == null) {
                throw new McpJson.McpToolException("Entry not found in archive: " + entry);
            }
            return header;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("ZIP error: " + e.getMessage());
        }
    }

    public JsonObject list(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), false);
        ZipFile zf = zip(zipPath, passwordOf(args));
        JsonArray entries = new JsonArray();
        try {
            if (!zf.isValidZipFile()) {
                throw new McpJson.McpToolException("Not a valid ZIP archive: " + zipPath.getPath());
            }
            String filter = McpJson.str(args, "filter", null);
            List<FileHeader> headers = zf.getFileHeaders();
            for (FileHeader header : headers) {
                String name = header.getFileName();
                if (filter != null && !filter.isEmpty() && !name.toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("name", name);
                entry.addProperty("directory", header.isDirectory());
                entry.addProperty("size", header.getUncompressedSize());
                entry.addProperty("compressed_size", header.getCompressedSize());
                entry.addProperty("crc32", header.getCrc());
                entry.addProperty("modified", header.getLastModifiedTimeEpoch());
                entries.add(entry);
            }
        } catch (ZipException e) {
            throw new McpJson.McpToolException("ZIP error: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("zip", zipPath.getPath());
        result.addProperty("count", entries.size());
        result.add("entries", entries);
        return result;
    }

    public JsonObject read(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), false);
        String entry = McpJson.reqStr(args, "entry");
        ZipFile zf = zip(zipPath, passwordOf(args));
        try {
            FileHeader header = header(zf, entry);
            if (header.isDirectory()) {
                throw new McpJson.McpToolException("Entry is a directory: " + entry);
            }
            if (header.getUncompressedSize() > McpFileTools.MAX_TEXT_BYTES) {
                throw new McpJson.McpToolException("Entry too large for text read; use mp_zip_read_bytes");
            }
            byte[] bytes = readEntry(zf, header);
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("entry", header.getFileName());
            result.addProperty("size", bytes.length);
            result.addProperty("content", new String(bytes, StandardCharsets.UTF_8));
            return result;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("ZIP error (wrong password?): " + e.getMessage());
        }
    }

    public JsonObject readBytes(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), false);
        String entry = McpJson.reqStr(args, "entry");
        ZipFile zf = zip(zipPath, passwordOf(args));
        try {
            FileHeader header = header(zf, entry);
            if (header.getUncompressedSize() > McpFileTools.MAX_TRANSFER_BYTES) {
                throw new McpJson.McpToolException("Entry too large (" + header.getUncompressedSize()
                        + " bytes); extract it to disk with mp_zip_extract instead");
            }
            byte[] bytes = readEntry(zf, header);
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("entry", header.getFileName());
            result.addProperty("size", bytes.length);
            result.addProperty("base64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
            return result;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("ZIP error (wrong password?): " + e.getMessage());
        }
    }

    private static byte[] readEntry(ZipFile zf, FileHeader header) throws ZipException, McpJson.McpToolException {
        try (InputStream in = zf.getInputStream(header)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new McpJson.McpToolException("Entry read failed: " + e.getMessage());
        }
    }

    public JsonObject create(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.str(args, "zip", null), true);
        JsonArray paths = McpJson.array(args, "paths");
        if (paths.size() == 0) {
            throw new McpJson.McpToolException("paths must list at least one file or folder to add");
        }
        int level = Math.max(0, Math.min(9, McpJson.intVal(args, "level", 5)));
        char[] password = passwordOf(args);
        if (zipPath.exists()) {
            throw new McpJson.McpToolException("Archive already exists: " + zipPath.getPath());
        }
        try {
            ZipFile zf = zip(zipPath, password);
            ZipParameters params = baseParams(level, password != null);
            List<File> filesToAdd = new ArrayList<>();
            for (JsonElement el : paths) {
                File f = ctx.policy.resolve(McpPathPolicy.normalizePath(el.getAsString(), ctx.policy.getHome()));
                if (!f.exists()) {
                    throw new McpJson.McpToolException("Path does not exist: " + f.getPath());
                }
                filesToAdd.add(f);
            }
            // zip4j creates the archive with the first add when it does not exist yet.
            zf.addFiles(filesToAdd, params);
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("size", zipPath.length());
            result.addProperty("entries", zf.getFileHeaders().size());
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("Create failed: " + e.getMessage());
        }
    }

    public JsonObject addFiles(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), true);
        JsonArray paths = McpJson.array(args, "paths");
        if (paths.size() == 0) {
            throw new McpJson.McpToolException("paths must list at least one file or folder to add");
        }
        int level = Math.max(0, Math.min(9, McpJson.intVal(args, "level", 5)));
        char[] password = passwordOf(args);
        try {
            ZipFile zf = zip(zipPath, password);
            ZipParameters params = baseParams(level, password != null);
            String baseDir = McpJson.str(args, "base_dir", null);
            if (baseDir != null && !baseDir.isEmpty()) {
                params.setRootFolderNameInZip(baseDir.endsWith("/") ? baseDir : baseDir + "/");
            }
            List<File> filesToAdd = new ArrayList<>();
            for (JsonElement el : paths) {
                File f = ctx.policy.resolve(McpPathPolicy.normalizePath(el.getAsString(), ctx.policy.getHome()));
                if (!f.exists()) {
                    throw new McpJson.McpToolException("Path does not exist: " + f.getPath());
                }
                filesToAdd.add(f);
            }
            zf.addFiles(filesToAdd, params); // zip4j replaces same-name entries
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("entries", zf.getFileHeaders().size());
            result.addProperty("added", filesToAdd.size());
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("Add failed: " + e.getMessage());
        }
    }

    public JsonObject addEntry(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), true);
        String entry = McpJson.reqStr(args, "entry");
        if (entry.startsWith("/")) entry = entry.substring(1);
        String text = McpJson.str(args, "content", null);
        String base64 = McpJson.str(args, "base64", null);
        if (text == null && base64 == null) {
            throw new McpJson.McpToolException("Provide content (text) or base64");
        }
        byte[] bytes = base64 != null
                ? android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                : text.getBytes(StandardCharsets.UTF_8);
        int level = Math.max(0, Math.min(9, McpJson.intVal(args, "level", 5)));
        char[] password = passwordOf(args);
        File temp = null;
        try {
            ZipFile zf = zip(zipPath, password);
            // zip4j streams files, not bytes, so stage the payload in a temp file.
            temp = File.createTempFile("mcp_entry_", ".tmp", ctx.context.getCacheDir());
            java.nio.file.Files.write(temp.toPath(), bytes);
            ZipParameters params = baseParams(level, password != null);
            params.setFileNameInZip(entry);
            zf.addFile(temp, params);
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("entry", entry);
            result.addProperty("size", bytes.length);
            result.addProperty("entries", zf.getFileHeaders().size());
            return result;
        } catch (IOException e) {
            throw new McpJson.McpToolException("Add failed: " + e.getMessage());
        } finally {
            if (temp != null) temp.delete();
        }
    }

    public JsonObject deleteEntries(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), true);
        JsonArray entries = McpJson.array(args, "entries");
        if (entries.size() == 0) {
            throw new McpJson.McpToolException("entries must be a non-empty array of entry names");
        }
        ZipFile zf = zip(zipPath, passwordOf(args));
        List<String> names = new ArrayList<>();
        try {
            for (JsonElement el : entries) {
                names.add(el.getAsString());
            }
            zf.removeFiles(names);
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("deleted", names.size());
            result.addProperty("entries", zf.getFileHeaders().size());
            return result;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("Delete failed: " + e.getMessage());
        }
    }

    public JsonObject renameEntry(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), true);
        String entry = McpJson.reqStr(args, "entry");
        String newEntry = McpJson.reqStr(args, "new_entry");
        ZipFile zf = zip(zipPath, passwordOf(args));
        try {
            FileHeader header = header(zf, entry);
            zf.renameFile(header, newEntry);
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("old_entry", entry);
            result.addProperty("new_entry", newEntry);
            return result;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("Rename failed: " + e.getMessage());
        }
    }

    public JsonObject extract(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), false);
        File dest = ctx.policy.resolve(McpPathPolicy.normalizePath(McpJson.reqStr(args, "dest"), ctx.policy.getHome()));
        String destCanonical;
        try {
            destCanonical = dest.getCanonicalPath();
        } catch (IOException e) {
            destCanonical = dest.getAbsolutePath();
        }
        if (!ctx.policy.canWrite(destCanonical)) {
            throw new McpJson.McpToolException("Write access denied for " + destCanonical);
        }
        ZipFile zf = zip(zipPath, passwordOf(args));
        JsonArray entries = McpJson.array(args, "entries");
        try {
            if (entries.size() == 0) {
                zf.extractAll(dest.getPath());
            } else {
                if (!dest.exists() && !dest.mkdirs() && !dest.isDirectory()) {
                    throw new McpJson.McpToolException("Cannot create destination directory " + dest);
                }
                for (JsonElement el : entries) {
                    FileHeader header = header(zf, el.getAsString());
                    zf.extractFile(header, dest.getPath());
                }
            }
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("dest", dest.getPath());
            result.addProperty("extracted_entries", entries.size());
            return result;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("Extract failed (wrong password?): " + e.getMessage());
        }
    }

    public JsonObject check(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), false);
        ZipFile zf = zip(zipPath, passwordOf(args));
        JsonObject result = new JsonObject();
        result.addProperty("zip", zipPath.getPath());
        try {
            result.addProperty("valid", zf.isValidZipFile());
            result.addProperty("encrypted", zf.isEncrypted());
            List<FileHeader> headers = zf.getFileHeaders();
            result.addProperty("entries", headers.size());
            // Drain every entry so the stream CRC check runs (MT-style integrity check).
            int checked = 0;
            boolean corrupted = false;
            for (FileHeader header : headers) {
                if (header.isDirectory()) continue;
                try (InputStream in = zf.getInputStream(header)) {
                    byte[] buffer = new byte[8192];
                    while (in.read(buffer) != -1) {
                        // CRC is verified while reading; just drain the stream.
                    }
                    checked++;
                } catch (Exception readError) {
                    corrupted = true;
                }
            }
            result.addProperty("entries_checked", checked);
            result.addProperty("integrity_ok", !corrupted);
        } catch (ZipException e) {
            result.addProperty("valid", false);
            result.addProperty("error", String.valueOf(e.getMessage()));
        }
        return result;
    }

    private static CompressionLevel levelOf(int level) {
        switch (level) {
            case 0: return CompressionLevel.NO_COMPRESSION;
            case 1: return CompressionLevel.FASTEST;
            case 2:
            case 3: return CompressionLevel.FAST;
            case 4:
            case 5: return CompressionLevel.NORMAL;
            case 6:
            case 7: return CompressionLevel.MAXIMUM;
            default: return CompressionLevel.ULTRA;
        }
    }

    private static ZipParameters baseParams(int level, boolean encrypt) {
        ZipParameters params = new ZipParameters();
        params.setCompressionMethod(CompressionMethod.DEFLATE);
        params.setCompressionLevel(levelOf(level));
        if (encrypt) {
            params.setEncryptFiles(true);
            params.setEncryptionMethod(EncryptionMethod.AES);
            params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }
        return params;
    }
}
