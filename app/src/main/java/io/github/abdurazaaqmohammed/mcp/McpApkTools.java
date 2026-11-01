package io.github.abdurazaaqmohammed.mcp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.InputSource;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.SignWrapper;

/**
 * MCP APK tools — the app's specialty. Wraps the same engines the GUI uses:
 * com.apk.axml.APKParser for manifest/permission analysis, ARSCLib
 * (com.reandroid) for resource tables and module rebuilding, zip4j for entry
 * edits, ApkZipAlignUtil + apksig (SignWrapper) for the build/sign step, and
 * jadx for Java decompilation. Source APKs are never modified: edits land in a
 * session working copy and builds write to a separate output file.
 */
public final class McpApkTools {

    private final McpToolContext ctx;

    public McpApkTools(McpToolContext ctx) {
        this.ctx = ctx;
    }

    private McpApkSessions.Session session(JsonObject args) throws McpJson.McpToolException {
        return ctx.apkSessions.get(McpJson.reqStr(args, "session"));
    }

    private File resolvePath(String path, boolean write) throws McpJson.McpToolException {
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

    // ------------------------------------------------------------------
    // Installed apps
    // ------------------------------------------------------------------

    public JsonObject listInstalled(JsonObject args) throws McpJson.McpToolException {
        PackageManager pm = ctx.context.getPackageManager();
        String filter = McpJson.str(args, "filter", null);
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        JsonArray apps = new JsonArray();
        for (PackageInfo info : packages) {
            String label;
            try {
                label = String.valueOf(info.applicationInfo.loadLabel(pm));
            } catch (Exception e) {
                label = info.packageName;
            }
            if (filter != null && !filter.isEmpty()
                    && !label.toLowerCase().contains(filter.toLowerCase())
                    && !info.packageName.toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }
            JsonObject app = new JsonObject();
            app.addProperty("package", info.packageName);
            app.addProperty("label", label);
            app.addProperty("version_name", info.versionName);
            app.addProperty("version_code", info.getLongVersionCode());
            app.addProperty("apk_path", info.applicationInfo != null ? info.applicationInfo.sourceDir : null);
            apps.add(app);
        }
        JsonObject result = new JsonObject();
        result.addProperty("count", apps.size());
        result.add("apps", apps);
        return result;
    }

    public JsonObject install(JsonObject args) throws McpJson.McpToolException {
        String pathArg = McpJson.str(args, "path", null);
        File apk;
        if (pathArg == null && args != null && args.has("session")) {
            apk = session(args).workApk;
        } else {
            apk = resolvePath(pathArg, false);
        }
        if (!apk.isFile()) throw new McpJson.McpToolException("APK not found: " + apk);
        if (!apk.getName().toLowerCase().endsWith(".apk") && !isApkFile(apk)) {
            throw new McpJson.McpToolException("Not an APK file: " + apk.getName());
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(androidx.core.content.FileProvider.getUriForFile(ctx.context,
                ctx.context.getPackageName() + ".provider", apk), "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            ctx.context.startActivity(intent);
        } catch (Exception e) {
            throw new McpJson.McpToolException("Cannot open installer: " + e.getMessage()
                    + ". Install it manually from " + apk.getPath());
        }
        JsonObject result = new JsonObject();
        result.addProperty("apk", apk.getPath());
        result.addProperty("handed_to_system_installer", true);
        return result;
    }

    private static boolean isApkFile(File file) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            return raf.readInt() == 0x504B0304;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    public JsonObject open(JsonObject args) throws McpJson.McpToolException {
        String packageArg = McpJson.str(args, "package", null);
        String pathArg = McpJson.str(args, "path", null);
        String source;
        if (packageArg != null && !packageArg.isEmpty()) {
            try {
                source = ctx.context.getPackageManager().getApplicationInfo(packageArg, 0).sourceDir;
            } catch (PackageManager.NameNotFoundException e) {
                throw new McpJson.McpToolException("Package not installed: " + packageArg);
            }
        } else if (pathArg != null && !pathArg.isEmpty()) {
            source = resolvePath(pathArg, false).getPath();
        } else {
            throw new McpJson.McpToolException("Provide path or package");
        }
        McpApkSessions.Session session = ctx.apkSessions.create(source);
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("source", session.sourcePath);
        result.addProperty("work_apk", session.workApk.getPath());
        result.addProperty("note", "Edits apply to the session copy; the source APK is never modified."
                + " Build output with mp_apk_build.");
        return result;
    }

    public JsonObject sessions(JsonObject args) {
        JsonArray arr = new JsonArray();
        for (McpApkSessions.Session session : ctx.apkSessions.all().values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("session", session.id);
            obj.addProperty("source", session.sourcePath);
            obj.addProperty("work_apk", session.workApk.getPath());
            obj.addProperty("work_size", session.workApk.length());
            arr.add(obj);
        }
        JsonObject result = new JsonObject();
        result.addProperty("count", arr.size());
        result.add("sessions", arr);
        return result;
    }

    public JsonObject closeSession(JsonObject args) throws McpJson.McpToolException {
        String id = McpJson.reqStr(args, "session");
        if (!ctx.apkSessions.close(id)) {
            throw new McpJson.McpToolException("Unknown session: " + id);
        }
        return McpJson.obj("session", id, "closed", "true");
    }

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    public JsonObject info(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        File apk = session.workApk;
        com.apk.axml.APKParser parser = new com.apk.axml.APKParser();
        parser.parse(apk.getPath(), ctx.context);
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("source", session.sourcePath);
        result.addProperty("apk", apk.getPath());
        result.addProperty("size", apk.length());
        if (parser.isParsed()) {
            result.addProperty("package", parser.getPackageName());
            result.addProperty("version_name", parser.getVersionName());
            result.addProperty("version_code", parser.getVersionCode());
            result.addProperty("min_sdk", parser.getMinSDKVersion());
            result.addProperty("target_sdk", parser.getTargetSDKVersion());
            result.addProperty("app_name", parser.getAppName());
            JsonArray permissions = new JsonArray();
            for (String permission : parser.getPermissions()) {
                permissions.add(permission);
            }
            result.add("permissions", permissions);
            result.addProperty("activities", parser.getActivities().size());
            result.addProperty("services", parser.getServices().size());
            result.addProperty("receivers", parser.getReceivers().size());
            result.addProperty("providers", parser.getProviders().size());
        }
        JsonArray dexes = new JsonArray();
        try {
            for (String dexName : dexNames(apk)) {
                JsonObject dex = new JsonObject();
                dex.addProperty("name", dexName);
                long size = entrySize(apk, dexName);
                dex.addProperty("size", size);
                dexes.add(dex);
            }
        } catch (IOException e) {
            throw new McpJson.McpToolException("APK read failed: " + e.getMessage());
        }
        result.add("dexes", dexes);
        result.addProperty("signing", describeSignature(apk));
        return result;
    }

    public JsonObject manifest(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        com.apk.axml.APKParser parser = new com.apk.axml.APKParser();
        parser.parse(session.workApk.getPath(), ctx.context);
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        try {
            result.addProperty("manifest", parser.getManifestAsString());
        } catch (Exception e) {
            throw new McpJson.McpToolException("Manifest decode failed: " + e.getMessage());
        }
        return result;
    }

    public JsonObject listEntries(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String filter = McpJson.str(args, "filter", null);
        JsonArray entries = new JsonArray();
        try (ZipFile zf = new ZipFile(session.workApk)) {
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
                entries.add(entry);
            }
        } catch (Exception e) {
            throw new McpJson.McpToolException("APK read failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("count", entries.size());
        result.add("entries", entries);
        return result;
    }

    public JsonObject readEntry(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String entry = McpJson.reqStr(args, "entry");
        try (ZipFile zf = new ZipFile(session.workApk)) {
            FileHeader header = zf.getFileHeader(entry);
            if (header == null) throw new McpJson.McpToolException("Entry not found: " + entry);
            if (header.isDirectory()) throw new McpJson.McpToolException("Entry is a directory: " + entry);
            byte[] bytes = readEntryBytes(zf, header);
            JsonObject result = new JsonObject();
            result.addProperty("session", session.id);
            result.addProperty("entry", entry);
            result.addProperty("size", bytes.length);
            if (isProbablyText(bytes)) {
                result.addProperty("content", new String(bytes, StandardCharsets.UTF_8));
            } else {
                result.addProperty("base64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
                result.addProperty("encoding", "base64");
            }
            return result;
        } catch (Exception e) {
            throw new McpJson.McpToolException("Entry read failed: " + e.getMessage());
        }
    }

    public JsonObject writeEntry(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String entry = McpJson.reqStr(args, "entry");
        String text = McpJson.str(args, "content", null);
        String base64 = McpJson.str(args, "base64", null);
        if (text == null && base64 == null) {
            throw new McpJson.McpToolException("Provide content (text) or base64");
        }
        byte[] bytes = base64 != null
                ? android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                : text.getBytes(StandardCharsets.UTF_8);
        try {
            replaceEntry(session.workApk, entry, bytes);
        } catch (IOException e) {
            throw new McpJson.McpToolException("Entry write failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("entry", entry);
        result.addProperty("size", bytes.length);
        result.addProperty("warning", "Working copy updated. Call mp_apk_build to produce a signed APK.");
        return result;
    }

    public JsonObject deleteEntry(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String entry = McpJson.reqStr(args, "entry");
        try {
            deleteEntries(session.workApk, new String[]{entry});
        } catch (IOException e) {
            throw new McpJson.McpToolException("Entry delete failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("entry", entry);
        result.addProperty("deleted", true);
        result.addProperty("warning", "Deleting resources.arsc, dex, or manifest entries will make the APK uninstallable unless you know what you are doing.");
        return result;
    }

    public JsonObject addFilesToApk(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        JsonArray paths = McpJson.array(args, "paths");
        String baseDir = McpJson.str(args, "base_dir", null);
        if (paths.size() == 0) {
            throw new McpJson.McpToolException("paths must list at least one file to add");
        }
        int added = 0;
        try (ZipFile zf = new ZipFile(session.workApk)) {
            for (JsonElement el : paths) {
                File f = ctx.policy.resolve(McpPathPolicy.normalizePath(el.getAsString(), ctx.policy.getHome()));
                if (!f.isFile()) {
                    throw new McpJson.McpToolException("Not a file: " + f.getPath());
                }
                String name = (baseDir == null || baseDir.isEmpty() ? "" : baseDir.endsWith("/") ? baseDir : baseDir + "/")
                        + f.getName();
                replaceEntryWithFile(session.workApk, zf, f, name);
                added++;
            }
        } catch (Exception e) {
            throw new McpJson.McpToolException("Add failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("added", added);
        result.addProperty("warning", "Working copy updated. Call mp_apk_build to produce a signed APK.");
        return result;
    }

    // ------------------------------------------------------------------
    // Resources (ARSCLib)
    // ------------------------------------------------------------------

    public JsonObject listStrings(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String filter = McpJson.str(args, "filter", null);
        File arsc = extractArsc(session.workApk);
        if (arsc == null) {
            throw new McpJson.McpToolException("APK has no resources.arsc");
        }
        JsonArray strings = new JsonArray();
        try {
            com.reandroid.arsc.chunk.TableBlock table = com.reandroid.arsc.chunk.TableBlock.load(arsc);
            for (com.reandroid.arsc.chunk.PackageBlock pkg : table.listPackages()) {
                for (com.reandroid.arsc.container.SpecTypePair spec : pkg.listSpecTypePairs()) {
                    if (!"string".equals(spec.getTypeName())) continue;
                    java.util.Iterator<com.reandroid.arsc.chunk.TypeBlock> blocks = spec.getTypeBlocks();
                    while (blocks.hasNext()) {
                        com.reandroid.arsc.chunk.TypeBlock tb = blocks.next();
                        String config = tb.getQualifiers();
                        for (com.reandroid.arsc.value.Entry e : tb.listEntries(true)) {
                            if (e == null || e.isNull()) continue;
                            String name;
                            try {
                                name = e.getResourceEntry().getName();
                            } catch (Exception ex) {
                                continue;
                            }
                            String value;
                            try {
                                value = e.getValueAsString();
                            } catch (Exception ex) {
                                value = null;
                            }
                            if (filter != null && !filter.isEmpty()
                                    && (value == null || !value.toLowerCase().contains(filter.toLowerCase()))
                                    && !name.toLowerCase().contains(filter.toLowerCase())) {
                                continue;
                            }
                            JsonObject item = new JsonObject();
                            item.addProperty("name", name);
                            item.addProperty("config", config);
                            item.addProperty("value", value);
                            strings.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new McpJson.McpToolException("Resource read failed: " + e.getMessage());
        } finally {
            arsc.delete();
        }
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("count", strings.size());
        result.add("strings", strings);
        return result;
    }

    public JsonObject setString(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String name = McpJson.reqStr(args, "name");
        String value = McpJson.reqStr(args, "value");
        String config = McpJson.str(args, "config", null);
        File arsc = extractArsc(session.workApk);
        if (arsc == null) {
            throw new McpJson.McpToolException("APK has no resources.arsc");
        }
        int changed = 0;
        try {
            com.reandroid.arsc.chunk.TableBlock table = com.reandroid.arsc.chunk.TableBlock.load(arsc);
            for (com.reandroid.arsc.chunk.PackageBlock pkg : table.listPackages()) {
                for (com.reandroid.arsc.container.SpecTypePair spec : pkg.listSpecTypePairs()) {
                    if (!"string".equals(spec.getTypeName())) continue;
                    java.util.Iterator<com.reandroid.arsc.chunk.TypeBlock> blocks = spec.getTypeBlocks();
                    while (blocks.hasNext()) {
                        com.reandroid.arsc.chunk.TypeBlock tb = blocks.next();
                        if (config != null && !config.isEmpty()
                                && !config.equals(tb.getQualifiers())) continue;
                        for (com.reandroid.arsc.value.Entry e : tb.listEntries(true)) {
                            if (e == null || e.isNull()) continue;
                            String entryName;
                            try {
                                entryName = e.getResourceEntry().getName();
                            } catch (Exception ex) {
                                continue;
                            }
                            if (!name.equals(entryName)) continue;
                            if (e.isComplex()) continue;
                            if (e.getValueType() != com.reandroid.arsc.value.ValueType.STRING) continue;
                            e.setValueAsString(value);
                            changed++;
                        }
                    }
                }
            }
            if (changed == 0) {
                throw new McpJson.McpToolException("No string resource named '" + name
                        + "' found (list them with mp_apk_list_strings)");
            }
            table.refresh();
            File written = new File(arsc.getParentFile(), "table_" + System.nanoTime() + ".arsc");
            table.writeBytes(written);
            byte[] bytes;
            try {
                bytes = java.nio.file.Files.readAllBytes(written.toPath());
            } finally {
                written.delete();
            }
            replaceEntry(session.workApk, "resources.arsc", bytes);
            JsonObject result = new JsonObject();
            result.addProperty("session", session.id);
            result.addProperty("name", name);
            result.addProperty("value", value);
            result.addProperty("updated", changed);
            result.addProperty("warning", "Resource table updated in the working copy. Call mp_apk_build to produce a signed APK.");
            return result;
        } catch (McpJson.McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpJson.McpToolException("Resource write failed: " + e.getMessage());
        } finally {
            arsc.delete();
        }
    }

    /** Extracts resources.arsc to a temp file, or null when the APK has none. */
    private File extractArsc(File apk) {
        try (ZipFile zf = new ZipFile(apk)) {
            FileHeader header = zf.getFileHeader("resources.arsc");
            if (header == null) return null;
            File temp = File.createTempFile("mcp_arsc_", ".arsc", ctx.context.getCacheDir());
            try (InputStream in = zf.getInputStream(header)) {
                java.nio.file.Files.copy(in, temp.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return temp;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Code analysis
    // ------------------------------------------------------------------

    public JsonObject listClasses(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String dex = McpJson.str(args, "dex", null);
        String filter = McpJson.str(args, "filter", null);
        int maxResults = Math.max(1, Math.min(5000, McpJson.intVal(args, "max_results", 500)));
        JsonArray classes = new JsonArray();
        int total = 0;
        try {
            for (String dexName : dexNames(session.workApk)) {
                if (dex != null && !dex.isEmpty() && !dexName.equals(dex)
                        && !(dexName.equals(dex + ".dex") || (dex + ".dex").equals(dexName))) {
                    continue;
                }
                byte[] dexBytes = readZipEntry(session.workApk, dexName);
                com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile dexFile =
                        com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile.fromInputStream(
                                com.android.tools.smali.dexlib2.Opcodes.getDefault(),
                                new java.io.ByteArrayInputStream(dexBytes));
                for (com.android.tools.smali.dexlib2.iface.ClassDef classDef : dexFile.getClasses()) {
                    total++;
                    String type = classDef.getType(); // Lcom/foo/Bar;
                    String dotted = type.substring(1, type.length() - 1).replace('/', '.');
                    if (filter != null && !filter.isEmpty() && !dotted.contains(filter)) continue;
                    if (classes.size() < maxResults) {
                        JsonObject cls = new JsonObject();
                        cls.addProperty("name", dotted);
                        cls.addProperty("dex", dexName);
                        int flags = classDef.getAccessFlags();
                        boolean isInterface = (flags & com.android.tools.smali.dexlib2.AccessFlags.INTERFACE.getValue()) != 0;
                        boolean isAbstract = (flags & com.android.tools.smali.dexlib2.AccessFlags.ABSTRACT.getValue()) != 0;
                        cls.addProperty("type", isInterface ? "interface" : isAbstract ? "abstract" : "class");
                        classes.add(cls);
                    }
                }
            }
        } catch (Exception e) {
            throw new McpJson.McpToolException("Dex parse failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("total_classes", total);
        result.addProperty("returned", classes.size());
        result.add("classes", classes);
        return result;
    }

    /** Full Java decompilation of one class via the bundled jadx. */
    public JsonObject decompileClass(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        String className = McpJson.reqStr(args, "class");
        try {
            jadx.api.JadxArgs jadxArgs = new jadx.api.JadxArgs();
            jadxArgs.setInputFile(session.workApk);
            jadxArgs.setSkipSources(true);
            jadxArgs.setSkipResources(true);
            jadx.api.JadxDecompiler decompiler = new jadx.api.JadxDecompiler(jadxArgs);
            try {
                decompiler.load();
                for (jadx.api.JavaClass cls : decompiler.getClasses()) {
                    if (cls.getFullName().equals(className)) {
                        String code = cls.getCode();
                        JsonObject result = new JsonObject();
                        result.addProperty("session", session.id);
                        result.addProperty("class", cls.getFullName());
                        result.addProperty("code", code == null ? "" : code);
                        return result;
                    }
                }
                throw new McpJson.McpToolException("Class not found in APK: " + className);
            } finally {
                decompiler.close();
            }
        } catch (McpJson.McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpJson.McpToolException("Decompile failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Build / sign
    // ------------------------------------------------------------------

    public JsonObject build(JsonObject args) throws McpJson.McpToolException {
        McpApkSessions.Session session = session(args);
        boolean sign = McpJson.bool(args, "sign", true);
        String outputArg = McpJson.str(args, "output", null);
        File output;
        if (outputArg != null && !outputArg.isEmpty()) {
            output = resolvePath(outputArg, true);
        } else {
            File outputDir = new File(ctx.home(), "output");
            if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
                throw new McpJson.McpToolException("Cannot create output dir " + outputDir);
            }
            output = new File(outputDir, baseName(session.sourcePath) + "-mcp" + (sign ? "-signed" : "") + ".apk");
            int n = 2;
            while (output.exists()) {
                output = new File(outputDir, baseName(session.sourcePath) + "-mcp" + (sign ? "-signed" : "") + "_" + n + ".apk");
                n++;
            }
        }
        File aligned = null;
        try {
            aligned = new File(session.workDir, "aligned_" + System.nanoTime() + ".apk");
            try {
                ApkZipAlignUtil.rebuildApk(session.workApk, aligned, null, null, null, null);
            } catch (Exception e) {
                // Alignment is best-effort; fall back to the raw working copy.
                aligned = session.workApk;
            }
            File buildInput = aligned;
            if (sign) {
                SignWrapper wrapper = defaultSignWrapper();
                wrapper.signApk(buildInput, output);
            } else {
                org.apache.commons.io.FileUtils.copyFile(buildInput, output);
            }
        } catch (Exception e) {
            throw new McpJson.McpToolException("Build failed: " + e.getMessage());
        } finally {
            if (aligned != null && aligned != session.workApk) aligned.delete();
        }
        JsonObject result = new JsonObject();
        result.addProperty("session", session.id);
        result.addProperty("output", output.getPath());
        result.addProperty("size", output.length());
        result.addProperty("signed", sign);
        return result;
    }

    public JsonObject sign(JsonObject args) throws McpJson.McpToolException {
        String pathArg = McpJson.str(args, "path", null);
        File input;
        if (pathArg == null && args != null && args.has("session")) {
            input = session(args).workApk;
        } else {
            input = resolvePath(pathArg, true);
        }
        if (!input.isFile()) throw new McpJson.McpToolException("APK not found: " + input);
        File output = new File(input.getParentFile(), stripExt(input.getName()) + "-signed.apk");
        try {
            defaultSignWrapper().signApk(input, output);
        } catch (Exception e) {
            throw new McpJson.McpToolException("Sign failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("input", input.getPath());
        result.addProperty("output", output.getPath());
        result.addProperty("size", output.length());
        return result;
    }

    /**
     * Signs with the user's configured signing key when one is stored (no
     * interactive prompt is possible mid-conversation), else falls back to the
     * app's debug keystore.
     */
    private SignWrapper defaultSignWrapper() throws Exception {
        android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx.context);
        String keyPath = prefs.getString("keyPath", null);
        if (keyPath != null && !keyPath.isEmpty() && new File(keyPath).isFile()
                && !keyPath.endsWith("debug.keystore")) {
            String keyPass = io.github.abdurazaaqmohammed.utils.PasswordEncryptor.decryptString(
                    prefs.getString("keyPass", ""));
            if (keyPass != null && !keyPass.isEmpty()) {
                return new SignWrapper(keyPath, keyPass, true, true, true, false);
            }
        }
        return new SignWrapper(FileUtils.getDebugKeystore(ctx.context), "android", true, true, true, false);
    }

    // ------------------------------------------------------------------
    // SSL unpinning hook (delegates to the patch engine)
    // ------------------------------------------------------------------

    public JsonObject sslUnpin(JsonObject args) throws McpJson.McpToolException {
        String pathArg = McpJson.str(args, "path", null);
        File input;
        if (pathArg == null && args != null && args.has("session")) {
            input = session(args).workApk;
        } else {
            input = resolvePath(pathArg, false);
        }
        if (!input.isFile()) throw new McpJson.McpToolException("APK not found: " + input);
        String outputArg = McpJson.str(args, "output", null);
        File output = outputArg == null || outputArg.isEmpty()
                ? new File(input.getParentFile(), stripExt(input.getName()) + "-unpinned.apk")
                : resolvePath(outputArg, true);
        boolean sign = McpJson.bool(args, "sign", true);
        try {
            io.github.abdurazaaqmohammed.utils.SslUnpinning.unpinApk(input, output, sign ? defaultSignWrapper() : null);
        } catch (McpJson.McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpJson.McpToolException("SSL unpinning failed: " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("input", input.getPath());
        result.addProperty("output", output.getPath());
        result.addProperty("size", output.length());
        result.addProperty("signed", sign);
        return result;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static String baseName(String path) {
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static List<String> dexNames(File apk) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zf = new ZipFile(apk)) {
            List<FileHeader> headers = zf.getFileHeaders();
            for (FileHeader header : headers) {
                String name = header.getFileName();
                if (name.startsWith("classes") && name.endsWith(".dex")) names.add(name);
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private static long entrySize(File apk, String entry) {
        try (ZipFile zf = new ZipFile(apk)) {
            FileHeader header = zf.getFileHeader(entry);
            return header == null ? -1 : header.getUncompressedSize();
        } catch (Exception e) {
            return -1;
        }
    }

    private static byte[] readZipEntry(File apk, String entry) throws IOException {
        try (ZipFile zf = new ZipFile(apk)) {
            FileHeader header = zf.getFileHeader(entry);
            if (header == null) throw new IOException("Entry not found: " + entry);
            return readEntryBytes(zf, header);
        }
    }

    private static byte[] readEntryBytes(ZipFile zf, FileHeader header) throws IOException {
        try (InputStream in = zf.getInputStream(header)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static boolean isProbablyText(byte[] bytes) {
        int check = Math.min(bytes.length, 4096);
        for (int i = 0; i < check; i++) {
            byte b = bytes[i];
            if (b == 0) return false;
            if (b < 9 || (b > 13 && b < 32)) return false;
        }
        return true;
    }

    /** Rewrites a single zip entry in place via a temp archive (zip4j has no direct byte-level replace). */
    private static void replaceEntry(File apk, String entry, byte[] bytes) throws IOException {
        File temp = new File(apk.getParentFile(), apk.getName() + ".mcp.tmp");
        try (ZipFile src = new ZipFile(apk);
             java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new FileOutputStream(temp))) {
            out.setLevel(java.util.zip.Deflater.BEST_SPEED);
            boolean replaced = false;
            for (FileHeader header : src.getFileHeaders()) {
                String name = header.getFileName();
                if (name.equals(entry)) {
                    putBytes(out, entry, bytes, false);
                    replaced = true;
                } else if (!header.isDirectory()) {
                    putBytes(out, name, readEntryBytes(src, header), header.getFileName().equals("resources.arsc"));
                }
            }
            if (!replaced) {
                putBytes(out, entry, bytes, false);
            }
        }
        if (!apk.delete() || !temp.renameTo(apk)) {
            org.apache.commons.io.FileUtils.copyFile(temp, apk);
            temp.delete();
        }
    }

    private static void replaceEntryWithFile(File apk, ZipFile zf, File file, String entryName) throws IOException {
        File temp = new File(apk.getParentFile(), apk.getName() + ".mcp.tmp");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new FileOutputStream(temp))) {
            out.setLevel(java.util.zip.Deflater.BEST_SPEED);
            boolean replaced = false;
            for (FileHeader header : zf.getFileHeaders()) {
                String name = header.getFileName();
                if (name.equals(entryName)) {
                    putStream(out, entryName, new java.io.FileInputStream(file), false);
                    replaced = true;
                } else if (!header.isDirectory()) {
                    putBytes(out, name, readEntryBytes(zf, header), name.equals("resources.arsc"));
                }
            }
            if (!replaced) {
                putStream(out, entryName, new java.io.FileInputStream(file), false);
            }
        }
        zf.close();
        if (!apk.delete() || !temp.renameTo(apk)) {
            org.apache.commons.io.FileUtils.copyFile(temp, apk);
            temp.delete();
        }
    }

    private static void deleteEntries(File apk, String[] names) throws IOException {
        java.util.Set<String> set = new java.util.HashSet<>(java.util.Arrays.asList(names));
        File temp = new File(apk.getParentFile(), apk.getName() + ".mcp.tmp");
        try (ZipFile src = new ZipFile(apk);
             java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new FileOutputStream(temp))) {
            out.setLevel(java.util.zip.Deflater.BEST_SPEED);
            for (FileHeader header : src.getFileHeaders()) {
                String name = header.getFileName();
                if (set.contains(name) || header.isDirectory()) continue;
                putBytes(out, name, readEntryBytes(src, header), name.equals("resources.arsc"));
            }
        }
        if (!apk.delete() || !temp.renameTo(apk)) {
            org.apache.commons.io.FileUtils.copyFile(temp, apk);
            temp.delete();
        }
    }

    private static void putBytes(java.util.zip.ZipOutputStream out, String name, byte[] bytes, boolean stored)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (stored) {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            entry.setCrc(computeCrc(bytes));
        } else {
            entry.setMethod(ZipEntry.DEFLATED);
        }
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }

    private static void putStream(java.util.zip.ZipOutputStream out, String name, InputStream in, boolean stored)
            throws IOException {
        try {
            ZipEntry entry = new ZipEntry(name);
            if (stored) {
                byte[] bytes = readAll(in);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(bytes.length);
                entry.setCompressedSize(bytes.length);
                entry.setCrc(computeCrc(bytes));
                out.putNextEntry(entry);
                out.write(bytes);
            } else {
                entry.setMethod(ZipEntry.DEFLATED);
                out.putNextEntry(entry);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static long computeCrc(byte[] bytes) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    private static String describeSignature(File apk) {
        try {
            io.github.abdurazaaqmohammed.utils.ApkSignatureInfo sig =
                    io.github.abdurazaaqmohammed.utils.ApkSignatureInfo.analyze(apk);
            if (sig.getStatus() == io.github.abdurazaaqmohammed.utils.ApkSignatureInfo.STATUS_NOT_SIGNED) {
                return "not signed";
            }
            String schemes = sig.getSchemes();
            int count = sig.getCertificates().size();
            return "schemes=" + schemes + ", certificates=" + count;
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
