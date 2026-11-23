package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.feature.apk.deepopt.AbiFilter;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;

/** Resource optimization with the bundled Android AAPT2, followed by an aligned repack. */
public final class Aapt2Optimizer {
    private Aapt2Optimizer() {}

    public static final class Options {
        public final String densities;
        public final String configurations;
        public final Set<String> abis;
        public final Integer minSdk;
        public final Integer targetSdk;

        public Options(String densities, String configurations, String abis, String minSdk, String targetSdk) {
            this.densities = parseList(densities, "density");
            this.configurations = parseList(configurations, "configuration");
            String architectures = parseList(abis, "architecture");
            Set<String> parsed = new LinkedHashSet<>();
            if (!architectures.isEmpty()) Collections.addAll(parsed, architectures.split(","));
            this.abis = Collections.unmodifiableSet(parsed);
            this.minSdk = parseSdk(minSdk, "Minimum SDK");
            this.targetSdk = parseSdk(targetSdk, "Target SDK");
            if (this.minSdk != null && this.targetSdk != null && this.minSdk > this.targetSdk) {
                throw new IllegalArgumentException("Minimum SDK cannot be greater than target SDK");
            }
        }
    }

    private static String parseList(String value, String label) {
        if (value == null || value.trim().isEmpty()) return "";
        List<String> result = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            String token = part.trim();
            // Includes locale qualifiers such as b+en+US, but never shell syntax.
            if (!token.matches("[A-Za-z0-9_+.-]+")) {
                throw new IllegalArgumentException("Invalid " + label + " list: " + value);
            }
            result.add(token);
        }
        return String.join(",", result);
    }

    private static Integer parseSdk(String value, String label) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            int number = Integer.parseInt(value.trim());
            if (number > 0 && number <= 10000) return number;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException(label + " must be a positive API level");
    }

    /** Writes a new unsigned APK beside the input; the input is never overwritten. */
    public static File optimize(Context context, File input, Options options) throws Exception {
        File binary = new File(context.getApplicationInfo().nativeLibraryDir, "libmp_aapt2.so");
        return optimize(binary, context.getCacheDir(), input, options);
    }

    /** Separated from Context so the complete pipeline can also be tested on a host. */
    static File optimize(File binary, File cacheDir, File input, Options options) throws Exception {
        if (input == null || !input.isFile()) throw new IOException("Input APK is unavailable");
        if (binary == null || !binary.isFile()) throw new IOException("AAPT2 is unavailable for this device");
        if (options == null) throw new IllegalArgumentException("Missing optimization options");
        Set<String> availableAbis = AbiFilter.getAvailableAbis(input);
        if (!options.abis.isEmpty() && !availableAbis.isEmpty()
                && Collections.disjoint(availableAbis, options.abis)) {
            throw new IOException("None of the selected architectures is present in the APK: " + availableAbis);
        }

        File manifestFile = null;
        File prepared = null;
        File optimized = File.createTempFile("aapt2-result-", ".apk", cacheDir);
        File output = null;
        boolean outputCreated = false;
        boolean complete = false;
        try {
            File aaptInput = input;
            if (options.minSdk != null || options.targetSdk != null) {
                manifestFile = File.createTempFile("aapt2-manifest-", ".xml", cacheDir);
                writeManifest(input, manifestFile, options);
                prepared = File.createTempFile("aapt2-input-", ".apk", cacheDir);
                Map<String, File> replacements = new HashMap<>();
                replacements.put("AndroidManifest.xml", manifestFile);
                ApkZipAlignUtil.rebuildApk(input, prepared, replacements, null, null, null);
                aaptInput = prepared;
            }
            List<String> command = new ArrayList<>();
            command.add(binary.getAbsolutePath());
            command.add("optimize");
            command.add("-o");
            command.add(optimized.getAbsolutePath());
            if (!options.densities.isEmpty()) {
                command.add("--target-densities");
                command.add(options.densities);
            }
            if (!options.configurations.isEmpty()) {
                command.add("-c");
                command.add(options.configurations);
            }
            command.add(aaptInput.getAbsolutePath());
            run(command);
            if (!optimized.isFile() || optimized.length() == 0) throw new IOException("AAPT2 produced no output");

            String name = input.getName();
            int dot = name.toLowerCase(Locale.ROOT).endsWith(".apk") ? name.length() - 4 : name.length();
            output = FileUtils.getUnusedFile(new File(input.getParentFile(), name.substring(0, dot) + "_aapt2.apk"));
            if (!output.createNewFile()) throw new IOException("Output file already exists: " + output);
            outputCreated = true;
            ApkZipAlignUtil.rebuildApk(optimized, output, null, null, null, entriesToRemove(optimized, options.abis));
            complete = true;
            return output;
        } finally {
            if (!complete && outputCreated) output.delete();
            optimized.delete();
            if (prepared != null) prepared.delete();
            if (manifestFile != null) manifestFile.delete();
        }
    }

    /** Signs an optimizer-owned output without overwriting unrelated .tmp or .idsig files. */
    static void signOutput(Context context, File output, SignWrapper signer) throws Exception {
        File dir = new File(context.getCacheDir(), "aapt2-sign-" + UUID.randomUUID());
        if (!dir.mkdir()) throw new IOException("Cannot create signing workspace");
        File sidecar = null;
        boolean sidecarCreated = false;
        boolean complete = false;
        try {
            File signed = new File(dir, "signed.apk");
            signer.signApk(output, signed);
            String sidecarName = output.getName().substring(0, output.getName().length() - 4) + ".idsig";
            File generatedSidecar = new File(dir, sidecarName);
            if (generatedSidecar.isFile()) {
                sidecar = new File(output.getParentFile(), sidecarName);
                if (!sidecar.createNewFile()) throw new IOException("Signature sidecar already exists: " + sidecar);
                sidecarCreated = true;
                copyFile(generatedSidecar, sidecar);
            }
            copyFile(signed, output);
            complete = true;
        } finally {
            if (!complete && sidecarCreated) sidecar.delete();
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) file.delete();
            dir.delete();
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    private static void writeManifest(File input, File output, Options options) throws IOException {
        try (ZipFile zip = new ZipFile(input)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) throw new IOException("APK has no AndroidManifest.xml");
            AndroidManifestBlock manifest;
            try (InputStream in = zip.getInputStream(entry)) {
                manifest = AndroidManifestBlock.load(in);
            }
            Integer min = options.minSdk != null ? options.minSdk : manifest.getMinSdkVersion();
            Integer target = options.targetSdk != null ? options.targetSdk : manifest.getTargetSdkVersion();
            if (min != null && target != null && min > target) {
                throw new IOException("Minimum SDK cannot be greater than target SDK");
            }
            if (options.minSdk != null) manifest.setMinSdkVersion(options.minSdk);
            if (options.targetSdk != null) manifest.setTargetSdkVersion(options.targetSdk);
            manifest.refreshFull();
            manifest.writeBytes(output);
        } catch (RuntimeException e) {
            throw new IOException("Cannot update the APK manifest", e);
        }
    }

    private static Set<String> entriesToRemove(File apk, Set<String> abis) throws IOException {
        Set<String> skip = new HashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                String upper = name.toUpperCase(Locale.ROOT);
                if ((upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                        || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF")))
                        || AbiFilter.shouldPruneAbi(name, abis)) skip.add(name);
            }
        }
        return skip;
    }

    private static void run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 65536) output.append(line).append('\n');
            }
            int exit = process.waitFor();
            if (exit != 0) throw new IOException("AAPT2 failed (exit " + exit + "): " + output.toString().trim());
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException("AAPT2 was interrupted", e);
        }
    }
}
