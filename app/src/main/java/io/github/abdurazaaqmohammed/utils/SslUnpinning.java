package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.InputSource;

import java.io.File;
import io.github.abdurazaaqmohammed.utils.deepopt.DexIndex;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline SSL certificate pinning bypass for APKs (the engine behind the SSL
 * Unpinning tool and the mp_apk_ssl_unpin MCP tool). Inspired by the hook list
 * of Frida unpinning script collections (e.g. frida-hook-engine), but it does
 * not need root, Frida or a runtime hooking framework: the pinning checks are
 * neutralized directly in the dex bytecode and the result is a normal,
 * installable, standalone APK.
 *
 * What gets patched:
 * - every app class implementing javax.net.ssl.X509TrustManager: the
 *   checkServerTrusted / checkClientTrusted bodies become no-ops (covers all
 *   "trust-all" trust managers and pinning trust managers, including renamed
 *   classes, because detection goes through the interface type);
 * - okhttp3.CertificatePinner (and the legacy com.squareup.okhttp one):
 *   the check / check$okhttp bodies become no-ops;
 * - OkHttp's OkHostnameVerifier and every app class implementing
 *   javax.net.ssl.HostnameVerifier: verify(...) returns true;
 * - every WebViewClient subclass: onReceivedSslError now calls
 *   SslErrorHandler.proceed() instead of cancelling the load.
 *
 * Known limitations (documented in the README): framework-level validation
 * (TrustManagerImpl) cannot be patched from inside the APK, so user-CA trust
 * for HTTPS interception still requires the app to trust user certificates;
 * pinning done inside native .so code is out of scope.
 */
public final class SslUnpinning {

    private static final String X509_TRUST_MANAGER = "Ljavax/net/ssl/X509TrustManager;";
    private static final String HOSTNAME_VERIFIER = "Ljavax/net/ssl/HostnameVerifier;";
    private static final String WEB_VIEW_CLIENT = "Landroid/webkit/WebViewClient;";
    private static final String[] CERT_PINNERS = {
            "Lokhttp3/CertificatePinner;",
            "Lcom/squareup/okhttp/CertificatePinner;",
    };
    private static final String[] OK_HOSTNAME_VERIFIERS = {
            "Lokhttp3/internal/tls/OkHostnameVerifier;",
            "Lcom/squareup/okhttp/internal/tls/OkHostnameVerifier;",
    };

    private SslUnpinning() {
    }

    public static final class Report {
        public final List<String> patches = new ArrayList<>();
        public int classesPatched;
        public int methodsPatched;
        public final List<String> log = new ArrayList<>();

        public void log(String line) {
            log.add(line);
        }
    }

    /**
     * Reads {@code input}, neutralizes pinning code, writes the patched APK to
     * {@code output} and signs it when {@code signer} is non-null.
     */
    public static Report unpinApk(File input, File output, SignWrapper signer) throws Exception {
        Report report = new Report();
        ApkModule module = ApkModule.loadApkFile(input);

        DexIndex index = new DexIndex();
        Map<String, DexBackedDexFile> dexFiles = new LinkedHashMap<>();
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (!name.matches("classes\\d*\\.dex")) continue;
            byte[] bytes = readAll(source.openStream());
            dexFiles.put(name, new DexBackedDexFile(DexIndex.opcodesForDex(bytes), bytes));
        }
        if (dexFiles.isEmpty()) {
            throw new IOException("No dex files found in " + input.getName());
        }
        index.build(dexFiles);
        report.log("Loaded " + index.classesTotal + " classes from " + dexFiles.size() + " dex file(s)");

        Set<String> modifiedClasses = new LinkedHashSet<>();
        for (ClassDef classDef : new ArrayList<>(index.classByType.values())) {
            patchClass(index, classDef, modifiedClasses, report);
        }

        Set<String> modifiedDexes = new LinkedHashSet<>();
        for (String type : modifiedClasses) {
            modifiedDexes.add(index.classToDex.get(type));
        }
        for (Map.Entry<String, List<String>> dexEntry : index.typesByDex.entrySet()) {
            String dexName = dexEntry.getKey();
            if (!modifiedDexes.contains(dexName)) continue;
            DexPool pool = new DexPool(index.dexOpcodes.getOrDefault(dexName, Opcodes.forDexVersion(35)));
            for (String type : dexEntry.getValue()) {
                ClassDef classDef = index.classByType.get(type);
                if (classDef != null) pool.internClass(classDef);
            }
            MemoryDataStore store = new MemoryDataStore();
            pool.writeTo(store);
            module.add(new ByteInputSource(Arrays.copyOf(store.getData(), store.getSize()), dexName));
            report.log("Rewrote " + dexName);
        }

        module.writeApk(output, (path, method, length) -> {
        });
        ApkZipAlignUtil.ensureInstallable(output);
        report.log("Patched APK written: " + output.getName());

        if (signer != null) {
            signer.signApk(output);
            report.log("Signed " + output.getName());
        }
        report.classesPatched = modifiedClasses.size();
        return report;
    }

    private static void patchClass(DexIndex index, ClassDef classDef, Set<String> modifiedClasses, Report report) {
        String type = classDef.getType();
        Set<String> ancestors = index.allAncestors.getOrDefault(type, Collections.emptySet());
        boolean isTrustManager = ancestors.contains(X509_TRUST_MANAGER);
        boolean isHostnameVerifier = ancestors.contains(HOSTNAME_VERIFIER);
        boolean isWebViewClient = ancestors.contains(WEB_VIEW_CLIENT);
        boolean isCertPinner = false;
        boolean isOkHostnameVerifier = false;
        for (String name : CERT_PINNERS) {
            if (name.equals(type)) isCertPinner = true;
        }
        for (String name : OK_HOSTNAME_VERIFIERS) {
            if (name.equals(type)) isOkHostnameVerifier = true;
        }
        if (!isTrustManager && !isHostnameVerifier && !isWebViewClient && !isCertPinner && !isOkHostnameVerifier) {
            return;
        }

        List<Method> direct = new ArrayList<>();
        List<Method> virtual = new ArrayList<>();
        boolean modified = false;
        for (Method method : classDef.getDirectMethods()) {
            Method patched = patchMethod(index, classDef, method, isTrustManager, isHostnameVerifier,
                    isCertPinner, isOkHostnameVerifier, isWebViewClient, report);
            if (patched != method) modified = true;
            direct.add(patched);
        }
        for (Method method : classDef.getVirtualMethods()) {
            Method patched = patchMethod(index, classDef, method, isTrustManager, isHostnameVerifier,
                    isCertPinner, isOkHostnameVerifier, isWebViewClient, report);
            if (patched != method) modified = true;
            virtual.add(patched);
        }
        if (!modified) return;
        index.classByType.put(type, new ImmutableClassDef(type, classDef.getAccessFlags(),
                classDef.getSuperclass(), classDef.getInterfaces(), classDef.getSourceFile(),
                classDef.getAnnotations(), classDef.getStaticFields(), classDef.getInstanceFields(),
                direct, virtual));
        modifiedClasses.add(type);
        report.classesPatched++;
    }

    private static Method patchMethod(DexIndex index, ClassDef classDef, Method method,
                                      boolean isTrustManager, boolean isHostnameVerifier,
                                      boolean isCertPinner, boolean isOkHostnameVerifier,
                                      boolean isWebViewClient, Report report) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return method; // abstract/native
        String name = method.getName();
        String returnType = method.getReturnType();

        if (isTrustManager && ("checkServerTrusted".equals(name) || "checkClientTrusted".equals(name))
                && "V".equals(returnType)) {
            report.patches.add(classDef.getType() + "->" + name + " (TrustManager no-op)");
            report.methodsPatched++;
            return withInstructions(method, impl,
                    Collections.singletonList(new ImmutableInstruction10x(Opcode.RETURN_VOID)));
        }
        if ((isCertPinner || isOkHostnameVerifier) && name.startsWith("check") && "V".equals(returnType)) {
            report.patches.add(classDef.getType() + "->" + name + " (pinner no-op)");
            report.methodsPatched++;
            return withInstructions(method, impl,
                    Collections.singletonList(new ImmutableInstruction10x(Opcode.RETURN_VOID)));
        }
        if ((isOkHostnameVerifier || (isHostnameVerifier && "verify".equals(name)))
                && "Z".equals(returnType)) {
            report.patches.add(classDef.getType() + "->" + name + " (hostname verify -> true)");
            report.methodsPatched++;
            int insSize = paramCount(method) + 1;
            int v0 = insSize; // first local register
            return withRegisters(method, insSize + 1, Arrays.asList(
                    new ImmutableInstruction11n(Opcode.CONST_4, v0, 1),
                    new ImmutableInstruction11x(Opcode.RETURN, v0)));
        }
        if (isWebViewClient && "onReceivedSslError".equals(name) && "V".equals(returnType)) {
            List<? extends CharSequence> params = method.getParameterTypes();
            if (params.size() < 2) return method;
            // (WebView, SslErrorHandler, SslError) -> call p1.proceed()
            int insSize = paramCount(method) + 1;
            int registerCount = Math.max(impl.getRegisterCount(), insSize);
            int handlerReg = registerCount - insSize + 1; // p1
            ImmutableMethodReference proceed = new ImmutableMethodReference(
                    "Landroid/webkit/SslErrorHandler;", "proceed",
                    Collections.emptyList(), "V");
            report.patches.add(classDef.getType() + "->onReceivedSslError (proceed())");
            report.methodsPatched++;
            return withRegisters(method, registerCount, Arrays.asList(
                    new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, handlerReg, 0, 0, 0, 0, proceed),
                    new ImmutableInstruction10x(Opcode.RETURN_VOID)));
        }
        return method;
    }

    private static int paramCount(Method method) {
        int words = 0;
        for (CharSequence type : method.getParameterTypes()) {
            String t = type.toString();
            if ("J".equals(t) || "D".equals(t)) words += 2;
            else words += 1;
        }
        return words;
    }

    /** Same registers and debug info as the original, new instruction list, no try blocks. */
    private static Method withInstructions(Method method, MethodImplementation impl,
                                           List<ImmutableInstruction> instructions) {
        return withRegisters(method, impl.getRegisterCount(), instructions);
    }

    private static Method withRegisters(Method method, int registerCount,
                                        List<ImmutableInstruction> instructions) {
        return new ImmutableMethod(method.getDefiningClass(), method.getName(),
                method.getParameters(), method.getReturnType(), method.getAccessFlags(),
                method.getAnnotations(), Collections.emptySet(),
                new ImmutableMethodImplementation(registerCount, instructions,
                        Collections.emptyList(), Collections.emptyList()));
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
}
