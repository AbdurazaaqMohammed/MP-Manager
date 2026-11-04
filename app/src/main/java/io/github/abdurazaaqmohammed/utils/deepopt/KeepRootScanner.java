package io.github.abdurazaaqmohammed.utils.deepopt;

import com.reandroid.apk.ApkModule;
import com.reandroid.archive.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extended keep roots scanned from the raw APK entries - sources the code analysis cannot
 * see and the classic crash sources when missed:
 * <ul>
 *   <li>{@code META-INF/services/*} - ServiceLoader provider lists; a removed provider
 *       class crashes the app the first time the service is loaded.</li>
 *   <li>Native libraries - JNI {@code FindClass}/{@code GetFieldID} names live as plain
 *       strings inside {@code .so} files; classes found there are kept.</li>
 *   <li>Plugin loaders - {@code assets/*.dex|*.jar} (DexClassLoader payloads) reference
 *       host classes; protected/packed APKs ship known packer {@code .so} names. Both make
 *       the analysis under-informed, so resources are kept entirely and class keeps go
 *       conservative.</li>
 * </ul>
 */
public final class KeepRootScanner {

    private static final Set<String> PACKER_SO_NAMES = new HashSet<>(Arrays.asList(
            "libjiagu.so", "libjiagu_art.so", "libjiagu_x86.so", "libjiagu_x64.so",
            "libsecexe.so", "libsecmain.so", "libshell.so", "libshella.so", "libshellx.so",
            "libtup.so", "libtospro.so", "libexecmain.so", "libexec.so", "libdexhelper.so",
            "libdexprotector.so", "libsecshell.so", "libsecshell_x.so", "libchaosvmp.so",
            "libddog.so", "libfdog.so", "libkwsmain.so", "libnqshield.so",
            "libbaiduprotect.so", "libmobisec.so", "libmobisecpro.so", "libnesec.so",
            "libtprt.so", "libDexHelper-x86.so", "libexecmain_x86.so"));

    private static final Pattern JNI_CLASS = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9_$]*(?:/[a-zA-Z][a-zA-Z0-9_$]*)+");
    private static final Pattern DOTTED_CLASS = Pattern.compile(
            "[a-z][a-z0-9_]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*){2,}");
    private static final int ASCII_RUN_MIN = 6;
    private static final int MAX_RUN = 512;

    public static final class Result {
        public final Set<String> classRoots = new HashSet<>();
        /** Classes whose names appear inside native libraries - JNI may call ANY of
         *  their members, including private ones (GetMethodID on private methods). */
        public final Set<String> nativeClasses = new HashSet<>();
        /** Raw printable strings from native libraries - names for reflection-style keeps. */
        public final Set<String> nativeStrings = new HashSet<>();
        public boolean keepAllResources;
        public boolean reflectionConservative;
    }

    private final ApkModule module;
    private final DexIndex index;
    private final OptimizerReport report;
    private final Result result = new Result();

    private KeepRootScanner(ApkModule module, DexIndex index, OptimizerReport report) {
        this.module = module;
        this.index = index;
        this.report = report;
    }

    public static Result scan(ApkModule module, DexIndex index, OptimizerReport report) throws IOException {
        KeepRootScanner scanner = new KeepRootScanner(module, index, report);
        scanner.doScan();
        return scanner.result;
    }

    private void doScan() throws IOException {
        int services = 0;
        int nativeClasses = 0;
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.US);
            if (name.startsWith("META-INF/services/") && !lower.endsWith("/")) {
                services += scanServices(source, name);
            } else if (lower.endsWith(".so")) {
                if (isPacker(name)) {
                    report.log("Packer detected: " + name + " - conservative keeps engaged");
                    result.keepAllResources = true;
                    result.reflectionConservative = true;
                }
                nativeClasses += scanNativeLib(source, name);
            } else if (name.startsWith("assets/") && (lower.endsWith(".dex") || lower.endsWith(".jar"))) {
                report.log("Plugin payload detected: " + name + " - keeping all resources");
                result.keepAllResources = true;
                result.reflectionConservative = true;
            } else if (name.startsWith("assets/") && lower.endsWith(".apk")) {
                result.reflectionConservative = true;
            }
        }
        if (services > 0) report.log("ServiceLoader providers kept: " + services);
        if (nativeClasses > 0) report.log("Classes kept from native libraries: " + nativeClasses);
    }

    private int scanServices(InputSource source, String name) throws IOException {
        String text = new String(readAll(source), StandardCharsets.UTF_8);
        int count = 0;
        for (String line : text.split("\n")) {
            line = line.trim();
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash).trim();
            if (line.isEmpty()) continue;
            String type = DexIndex.toType(line);
            if (type != null && index.classByType.containsKey(type)) {
                result.classRoots.add(type);
                count++;
            }
        }
        if (count == 0) report.log("No app classes in " + name);
        return count;
    }

    private int scanNativeLib(InputSource source, String name) throws IOException {
        Set<String> found = new HashSet<>();
        InputStream is = source.openStream();
        try {
            byte[] buf = new byte[65536];
            byte[] tail = new byte[0];
            int len;
            while ((len = is.read(buf)) != -1) {
                byte[] chunk = new byte[tail.length + len];
                System.arraycopy(tail, 0, chunk, 0, tail.length);
                System.arraycopy(buf, 0, chunk, tail.length, len);
                collectClassRuns(chunk, found);
                int keep = Math.min(chunk.length, MAX_RUN);
                tail = new byte[keep];
                System.arraycopy(chunk, chunk.length - keep, tail, 0, keep);
            }
        } finally {
            is.close();
        }
        int count = 0;
        for (String run : found) {
            result.nativeStrings.add(run);
            String type = toAppType(run);
            if (type != null && index.classByType.containsKey(type)) {
                result.classRoots.add(type);
                result.nativeClasses.add(type);
                count++;
            }
        }
        return count;
    }

    private void collectClassRuns(byte[] chunk, Set<String> out) {
        int start = -1;
        for (int i = 0; i < chunk.length; i++) {
            boolean printable = chunk[i] >= 0x20 && chunk[i] < 0x7F;
            if (printable && start < 0) {
                start = i;
            } else if (!printable && start >= 0) {
                addRun(chunk, start, i, out);
                start = -1;
            } else if (start >= 0 && i - start > MAX_RUN) {
                addRun(chunk, start, i, out);
                start = -1;
            }
        }
        if (start >= 0) addRun(chunk, start, chunk.length, out);
    }

    private void addRun(byte[] chunk, int start, int end, Set<String> out) {
        int len = end - start;
        if (len < ASCII_RUN_MIN) return;
        String run = new String(chunk, start, len, StandardCharsets.US_ASCII);
        if (run.contains("/")) {
            Matcher m = JNI_CLASS.matcher(run);
            while (m.find()) {
                String candidate = m.group();
                if (candidate.length() >= ASCII_RUN_MIN) out.add(candidate);
            }
        } else if (run.contains(".")) {
            Matcher m = DOTTED_CLASS.matcher(run);
            while (m.find()) out.add(m.group());
        }
    }

    /** "com/foo/Bar", "[Lcom/foo/Bar;", "com.foo.Bar" -> "Lcom/foo/Bar;" when it is an app class. */
    private String toAppType(String run) {
        if (run.startsWith("[L") && run.endsWith(";")) run = run.substring(2, run.length() - 1);
        if (run.contains("/")) {
            if (!run.contains(".")) return "L" + run + ";";
            return null;
        }
        return DexIndex.toType(run);
    }

    private boolean isPacker(String name) {
        String base = name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.US);
        return PACKER_SO_NAMES.contains(base);
    }

    private static byte[] readAll(InputSource source) throws IOException {
        InputStream is = source.openStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int len;
        while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
        is.close();
        return bos.toByteArray();
    }
}
