package io.github.abdurazaaqmohammed.mcp;

import android.os.Build;
import android.util.Base64;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
            result.addProperty("base64", Base64.encodeToString(bytes, Base64.NO_WRAP));
            return result;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("ZIP error (wrong password?): " + e.getMessage());
        }
    }

    private static byte[] readEntry(ZipFile zf, FileHeader header) throws ZipException, McpJson.McpToolException {
        try (InputStream in = zf.getInputStream(header)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
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
        List<File> filesToAdd = new ArrayList<>();
        for (JsonElement el : paths) {
            File f = ctx.policy.resolve(McpPathPolicy.normalizePath(el.getAsString(), ctx.policy.getHome()));
            if (!f.exists()) {
                throw new McpJson.McpToolException("Path does not exist: " + f.getPath());
            }
            filesToAdd.add(f);
        }
        if (password != null && legacyZip()) {
            throw new McpJson.McpToolException("Password-protected archives require Android 8+ on this build");
        }
        try {
            if (legacyZip()) {
                writeNewZip(zipPath, filesToAdd, null, Math.max(0, Math.min(9, level)), false);
                ZipFile zf = zip(zipPath, null);
                JsonObject result = new JsonObject();
                result.addProperty("zip", zipPath.getPath());
                result.addProperty("size", zipPath.length());
                result.addProperty("entries", zf.getFileHeaders().size());
                return result;
            }
            ZipFile zf = zip(zipPath, password);
            ZipParameters params = baseParams(level, password != null);
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

    /** zip4j's write path needs real java.nio (API 26+); older desugared NIO
     *  builds throw PosixFileAttributeView NPEs, so those fall back to
     *  java.util.zip (no encryption support). Reading works everywhere. */
    private static boolean legacyZip() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O;
    }

    private void writeNewZip(File zipPath, List<File> files, char[] password,
                             int level, boolean deflate) throws IOException {
        File parent = zipPath.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create parent directory " + parent);
        }
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipPath))) {
            out.setLevel(level);
            for (File root : files) {
                String baseName = root.getName();
                addTreeToZip(root, baseName, out, new HashSet<>());
            }
        }
    }

    private void addTreeToZip(File file, String entryName, ZipOutputStream out,
                              Set<String> seen) throws IOException {
        if (!seen.add(entryName)) return;
        if (file.isDirectory()) {
            if (!entryName.endsWith("/")) entryName += "/";
            out.putNextEntry(new ZipEntry(entryName));
            out.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addTreeToZip(child, entryName + child.getName(), out, seen);
                }
            }
            return;
        }
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        out.putNextEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        out.closeEntry();
    }

    public JsonObject addFiles(JsonObject args) throws McpJson.McpToolException {
        File zipPath = resolveZip(McpJson.reqStr(args, "zip"), true);
        JsonArray paths = McpJson.array(args, "paths");
        if (paths.size() == 0) {
            throw new McpJson.McpToolException("paths must list at least one file or folder to add");
        }
        int level = Math.max(0, Math.min(9, McpJson.intVal(args, "level", 5)));
        char[] password = passwordOf(args);
        String baseDir = McpJson.str(args, "base_dir", null);
        List<File> filesToAdd = new ArrayList<>();
        for (JsonElement el : paths) {
            File f = ctx.policy.resolve(McpPathPolicy.normalizePath(el.getAsString(), ctx.policy.getHome()));
            if (!f.exists()) {
                throw new McpJson.McpToolException("Path does not exist: " + f.getPath());
            }
            filesToAdd.add(f);
        }
        if (password != null && legacyZip()) {
            throw new McpJson.McpToolException("Password-protected archives require Android 8+ on this build");
        }
        try {
            if (legacyZip()) {
                rewriteZipAdding(zipPath, filesToAdd, baseDir);
                ZipFile zf = zip(zipPath, null);
                JsonObject result = new JsonObject();
                result.addProperty("zip", zipPath.getPath());
                result.addProperty("entries", zf.getFileHeaders().size());
                result.addProperty("added", filesToAdd.size());
                return result;
            }
            ZipFile zf = zip(zipPath, password);
            ZipParameters params = baseParams(level, password != null);
            if (baseDir != null && !baseDir.isEmpty()) {
                params.setRootFolderNameInZip(baseDir.endsWith("/") ? baseDir : baseDir + "/");
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

    /** Rewrites the whole archive, copying existing entries and adding (or
     *  replacing) the given files. Used where zip4j cannot run (API<26). */
    private void rewriteZipAdding(File zipPath, List<File> filesToAdd, String baseDir) throws IOException {
        File temp = new File(zipPath.getParentFile(), zipPath.getName() + ".mcp.tmp");
        Map<String, String> additions = new LinkedHashMap<>();
        for (File f : filesToAdd) {
            String name = (baseDir == null || baseDir.isEmpty() ? ""
                    : baseDir.endsWith("/") ? baseDir : baseDir + "/") + f.getName();
            additions.put(name, f.getPath());
        }
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(temp));
             ZipFile existing = zip(zipPath, null)) {
            out.setLevel(Deflater.BEST_SPEED);
            Set<String> replaced = new HashSet<>();
            if (zipPath.exists()) {
                for (FileHeader header : existing.getFileHeaders()) {
                    String name = header.getFileName();
                    if (header.isDirectory()) continue;
                    if (additions.containsKey(name)) {
                        replaced.add(name);
                        putFileEntry(out, name, new File(additions.get(name)));
                        continue;
                    }
                    putStreamEntry(out, name, existing.getInputStream(header));
                }
            }
            for (Map.Entry<String, String> add : additions.entrySet()) {
                if (!replaced.contains(add.getKey())) {
                    putFileEntry(out, add.getKey(), new File(add.getValue()));
                }
            }
        }
        if (zipPath.exists() && !zipPath.delete()) {
            throw new IOException("Could not replace archive " + zipPath);
        }
        if (!temp.renameTo(zipPath)) {
            throw new IOException("Could not move new archive into place");
        }
    }

    private static void putFileEntry(ZipOutputStream out, String name, File file)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(file.lastModified());
        out.putNextEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        out.closeEntry();
    }

    private static void putStreamEntry(ZipOutputStream out, String name, InputStream in)
            throws IOException {
        try {
            out.putNextEntry(new ZipEntry(name));
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.closeEntry();
        } finally {
            in.close();
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
                ? Base64.decode(base64, Base64.DEFAULT)
                : text.getBytes(StandardCharsets.UTF_8);
        int level = Math.max(0, Math.min(9, McpJson.intVal(args, "level", 5)));
        char[] password = passwordOf(args);
        if (password != null && legacyZip()) {
            throw new McpJson.McpToolException("Password-protected archives require Android 8+ on this build");
        }
        if (legacyZip()) {
            // Stage the payload in a temp file and reuse the rewrite path.
            File stage = null;
            try {
                stage = File.createTempFile("mcp_entry_", ".tmp", ctx.context.getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(stage)) {
                    fos.write(bytes);
                }
                Map<String, File> single = new LinkedHashMap<>();
                single.put(entry, stage);
                rewriteZipAddFiles(zipPath, single);
                ZipFile zf = zip(zipPath, null);
                JsonObject result = new JsonObject();
                result.addProperty("zip", zipPath.getPath());
                result.addProperty("entry", entry);
                result.addProperty("size", bytes.length);
                result.addProperty("entries", zf.getFileHeaders().size());
                return result;
            } catch (IOException e) {
                throw new McpJson.McpToolException("Add failed: " + e.getMessage());
            } finally {
                if (stage != null) stage.delete();
            }
        }
        File temp = null;
        try {
            ZipFile zf = zip(zipPath, password);
            // zip4j streams files, not bytes, so stage the payload in a temp file.
            temp = File.createTempFile("mcp_entry_", ".tmp", ctx.context.getCacheDir());
            Files.write(temp.toPath(), bytes);
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

    /** Rewrites the archive copying every existing entry and adding/replacing
     *  the entries in {@code additions} (entry name -> file). */
    private static void rewriteZipAddFiles(File zipPath, Map<String, File> additions)
            throws IOException {
        File temp = new File(zipPath.getParentFile(), zipPath.getName() + ".mcp.tmp");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(temp));
             ZipFile existing = zip(zipPath, null)) {
            out.setLevel(Deflater.BEST_SPEED);
            Set<String> replaced = new HashSet<>();
            if (zipPath.exists()) {
                for (FileHeader header : existing.getFileHeaders()) {
                    String name = header.getFileName();
                    if (header.isDirectory()) continue;
                    if (additions.containsKey(name)) {
                        replaced.add(name);
                        putFileEntry(out, name, additions.get(name));
                        continue;
                    }
                    putStreamEntry(out, name, existing.getInputStream(header));
                }
            }
            for (Map.Entry<String, File> add : additions.entrySet()) {
                if (!replaced.contains(add.getKey())) {
                    putFileEntry(out, add.getKey(), add.getValue());
                }
            }
        }
        if (zipPath.exists() && !zipPath.delete()) {
            throw new IOException("Could not replace archive " + zipPath);
        }
        if (!temp.renameTo(zipPath)) {
            throw new IOException("Could not move new archive into place");
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
        char[] password = passwordOf(args);
        JsonArray entries = McpJson.array(args, "entries");
        if (password != null && legacyZip()) {
            throw new McpJson.McpToolException("Extracting encrypted archives requires Android 8+ on this build");
        }
        if (legacyZip()) {
            int count = streamExtract(zipPath, dest, entries);
            JsonObject result = new JsonObject();
            result.addProperty("zip", zipPath.getPath());
            result.addProperty("dest", dest.getPath());
            result.addProperty("extracted_entries", count);
            return result;
        }
        ZipFile zf = zip(zipPath, password);
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
            result.addProperty("extracted_entries", Math.max(entries.size(), 1));
            return result;
        } catch (ZipException e) {
            throw new McpJson.McpToolException("Extract failed (wrong password?): " + e.getMessage());
        }
    }

    /** Streaming extract for devices where zip4j cannot extract (API<26).
     *  Supports unencrypted archives only. */
    private static int streamExtract(File zipPath, File dest, JsonArray entries) throws McpJson.McpToolException {
        Set<String> wanted = new HashSet<>();
        for (JsonElement el : entries) {
            String name = el.getAsString();
            wanted.add(name);
            wanted.add(name.startsWith("/") ? name.substring(1) : "/" + name);
        }
        if (!dest.isDirectory() && !dest.mkdirs() && !dest.isDirectory()) {
            throw new McpJson.McpToolException("Cannot create destination directory " + dest);
        }
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zipPath))) {
            byte[] buffer = new byte[16384];
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (wanted.size() > 0 && !wanted.contains(name)) continue;
                File out = new File(dest, name);
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new McpJson.McpToolException("Cannot create directory " + parent);
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
                out.setLastModified(entry.getTime());
                count++;
            }
        } catch (IOException e) {
            throw new McpJson.McpToolException("Extract failed: " + e.getMessage());
        }
        if (wanted.size() > 0 && count < wanted.size()) {
            throw new McpJson.McpToolException("Some entries were not found in the archive (extracted " + count + ")");
        }
        return count;
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
