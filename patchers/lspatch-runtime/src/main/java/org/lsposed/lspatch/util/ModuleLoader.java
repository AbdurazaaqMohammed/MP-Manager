package org.lsposed.lspatch.util;

import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import org.matrix.vector.ipc.ModuleCode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipFile;

/**
 * Reads a module APK into the {@link ModuleCode} the Vector framework loads modules from, mirroring
 * the daemon's {@code FileSystem.loadModule}: a module targeting API 101+ is modern and described by
 * {@code META-INF/xposed/}; one carrying {@code assets/xposed_init} is legacy; API 100 is refused.
 */
public class ModuleLoader {

    private static final String TAG = "LSPatch";

    private static void readDexes(ZipFile apkFile, List<SharedMemory> preLoadedDexes) {
        int secondary = 2;
        for (var dexFile = apkFile.getEntry("classes.dex"); dexFile != null;
             dexFile = apkFile.getEntry("classes" + secondary + ".dex"), secondary++) {
            try (var in = apkFile.getInputStream(dexFile)) {
                var memory = SharedMemory.create(null, in.available());
                var byteBuffer = memory.mapReadWrite();
                Channels.newChannel(in).read(byteBuffer);
                SharedMemory.unmap(byteBuffer);
                memory.setProtect(OsConstants.PROT_READ);
                preLoadedDexes.add(memory);
            } catch (Throwable e) {
                // SharedMemory.create/mapReadWrite/setProtect throw unchecked exceptions (e.g. a
                // zero-length classes.dex is an IllegalArgumentException), so catch broadly.
                Log.w(TAG, "Can not load " + dexFile + " in " + apkFile, e);
            }
        }
    }

    private static void readName(ZipFile apkFile, String initName, List<String> names) {
        var initEntry = apkFile.getEntry(initName);
        if (initEntry == null) return;
        try (var in = apkFile.getInputStream(initEntry)) {
            var reader = new BufferedReader(new InputStreamReader(in));
            String name;
            while ((name = reader.readLine()) != null) {
                name = name.trim();
                if (name.isEmpty() || name.startsWith("#")) continue;
                names.add(name);
            }
        } catch (IOException e) {
            Log.e(TAG, "Can not open " + initEntry, e);
        }
    }

    // Leading digits only, so "101.0" -> 101 and absent/garbage -> 0, matching FileSystem.leadingInt.
    private static int leadingInt(String value) {
        if (value == null) return 0;
        value = value.trim();
        int i = 0;
        while (i < value.length() && Character.isDigit(value.charAt(i))) i++;
        if (i == 0) return 0;
        try {
            return Integer.parseInt(value.substring(0, i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static ModuleCode loadModule(String path) {
        if (path == null) return null;
        var file = new ModuleCode();
        var preLoadedDexes = new ArrayList<SharedMemory>();
        var moduleClassNames = new ArrayList<String>(1);
        var moduleLibraryNames = new ArrayList<String>(1);
        try (var apkFile = new ZipFile(path)) {
            var props = new Properties();
            var propEntry = apkFile.getEntry("META-INF/xposed/module.prop");
            if (propEntry != null) {
                try (var in = apkFile.getInputStream(propEntry)) {
                    props.load(in);
                } catch (Throwable t) {
                    // A legacy module has no module.prop at all, and a malformed one must not make
                    // the APK unloadable.
                    Log.w(TAG, "Malformed module.prop in " + path, t);
                }
            }

            int targetApi = leadingInt(props.getProperty("targetApiVersion"));
            boolean autoHotReload = Boolean.parseBoolean(
                    props.getProperty("autoHotReload", "false").trim());
            boolean exceptionPassthrough = "passthrough".equalsIgnoreCase(
                    props.getProperty("exceptionMode", "").trim());
            boolean hasLegacyFile = apkFile.getEntry("assets/xposed_init") != null;

            boolean legacy;
            if (targetApi >= 101) {
                legacy = false;
                readName(apkFile, "META-INF/xposed/java_init.list", moduleClassNames);
                readName(apkFile, "META-INF/xposed/native_init.list", moduleLibraryNames);
            } else if (hasLegacyFile) {
                legacy = true;
                readName(apkFile, "assets/xposed_init", moduleClassNames);
                readName(apkFile, "assets/native_init", moduleLibraryNames);
            } else {
                // API 100 is dropped outright; anything else names no entry classes.
                Log.w(TAG, "Unsupported or non-module APK: " + path + " (targetApi=" + targetApi + ")");
                return null;
            }

            if (moduleClassNames.isEmpty()) {
                Log.e(TAG, "No entry classes for " + path);
                return null;
            }

            readDexes(apkFile, preLoadedDexes);
            if (preLoadedDexes.isEmpty()) return null;

            file.preLoadedDexes = preLoadedDexes;
            file.moduleClassNames = moduleClassNames;
            file.moduleLibraryNames = moduleLibraryNames;
            file.legacy = legacy;
            file.targetApiVersion = targetApi;
            file.autoHotReload = autoHotReload;
            file.exceptionPassthrough = exceptionPassthrough;
            file.nativeLibraryDir = null; // Only system_server needs staged libraries; LSPatch never injects it.
            return file;
        } catch (Throwable e) {
            // A malformed module must not be able to abort the whole loader; the daemon this mirrors
            // (FileSystem.loadModule) likewise treats any failure as an unloadable module.
            Log.e(TAG, "Can not open " + path, e);
            return null;
        }
    }
}
