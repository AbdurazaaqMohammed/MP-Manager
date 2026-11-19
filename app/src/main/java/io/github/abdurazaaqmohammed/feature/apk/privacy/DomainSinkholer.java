package io.github.abdurazaaqmohammed.feature.apk.privacy;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Neutralizes telemetry, tracking, analytics, and AI streaming endpoints by sinkholing
 * outbound URLs in DEX bytecode to local loopback (http://127.0.0.1:0/).
 * Outbound connections drop immediately without crashing or network timeouts.
 */
public final class DomainSinkholer {

    private DomainSinkholer() {}

    public static final String SINKHOLE_DESTINATION = "http://127.0.0.1:0/";

    private static final List<String> KNOWN_TELEMETRY_DOMAINS = Arrays.asList(
            "app-measurement.com",
            "t.appsflyer.com",
            "api2.branch.io",
            "api.segment.io",
            "api.amplitude.com",
            "app.adjust.com",
            "crashlytics.com",
            "siftscience.com",
            "datadoghq.com",
            "braze.com",
            "launchdarkly.com",
            "kochava.com",
            "telemetry.",
            "analytics."
    );

    private static final Pattern TELEMETRY_URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9.-]*(telemetry|analytics|metrics|tracking|crashlytics|app-measurement|appsflyer|adjust\\.com|branch\\.io|kochava|mixpanel|amplitude|segment\\.io|siftscience|datadoghq|braze\\.com|launchdarkly)[a-zA-Z0-9./_?=&%+:#;-]*",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> KNOWN_AI_DOMAINS = Arrays.asList(
            "andistreaminghub.amazon.com",
            "andistreaminghub",
            "aiq.xa.comcast.net",
            "aiq.xa",
            "api.openai.com",
            "anthropic.com",
            "generativelanguage.googleapis.com"
    );

    private static final Pattern AI_URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9.-]*(andistreaminghub|aiq\\.xa|openai\\.com|anthropic\\.com|generativelanguage\\.googleapis\\.com)[a-zA-Z0-9./_?=&%+:#;-]*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Checks if a string URL or domain points to a telemetry or tracking collector.
     */
    public static boolean isTelemetryEndpoint(String str) {
        if (str == null || str.length() < 8) return false;
        // Never touch class names, descriptors, method signatures, or package names
        if (str.contains("$") || str.contains(";") || str.contains("(") || str.contains(" ") || str.contains("\n")) {
            return false;
        }
        if (str.startsWith("com.") || str.startsWith("org.") || str.startsWith("android.")
                || str.startsWith("androidx.") || str.startsWith("io.") || str.startsWith("java.")
                || str.startsWith("javax.") || str.startsWith("kotlin.") || str.startsWith("dalvik.")) {
            return false;
        }

        String lower = str.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ws://") || lower.startsWith("wss://")) {
            for (String domain : KNOWN_TELEMETRY_DOMAINS) {
                if (lower.contains(domain)) return true;
            }
            return TELEMETRY_URL_PATTERN.matcher(str).matches();
        }

        for (String domain : KNOWN_TELEMETRY_DOMAINS) {
            if ("telemetry.".equals(domain) || "analytics.".equals(domain)) {
                if (lower.startsWith(domain) && lower.contains(".") && !lower.contains("/")) {
                    return true;
                }
            } else if (lower.equals(domain) || lower.endsWith("." + domain)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a string URL or domain points to an AI streaming or LLM API endpoint.
     */
    public static boolean isAiEndpoint(String str) {
        if (str == null || str.length() < 6) return false;
        if (str.contains("$") || str.contains(";") || str.contains("(") || str.contains(" ") || str.contains("\n")) {
            return false;
        }
        if (str.startsWith("com.") || str.startsWith("org.") || str.startsWith("android.")
                || str.startsWith("androidx.") || str.startsWith("io.") || str.startsWith("java.")
                || str.startsWith("javax.") || str.startsWith("kotlin.") || str.startsWith("dalvik.")) {
            return false;
        }

        String lower = str.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ws://") || lower.startsWith("wss://")) {
            for (String domain : KNOWN_AI_DOMAINS) {
                if (lower.contains(domain)) return true;
            }
            return AI_URL_PATTERN.matcher(str).matches();
        }

        for (String domain : KNOWN_AI_DOMAINS) {
            if (lower.equals(domain) || lower.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a string URL or domain points to either an AI streaming endpoint or a telemetry collector.
     */
    public static boolean isAiOrTelemetryEndpoint(String str) {
        return isAiEndpoint(str) || isTelemetryEndpoint(str);
    }

    /**
     * Sinkholes all telemetry and AI endpoints in a method using default options and fresh stats.
     */
    public static PrivacyBytecodeEngine.MethodTransformationResult sinkholeMethod(Method method) {
        return sinkholeMethod(method, new PrivacyStats(), PrivacyOptions.all());
    }

    /**
     * Sinkholes telemetry and/or AI URLs in a method according to the provided options,
     * recording mutation statistics into stats.
     */
    public static PrivacyBytecodeEngine.MethodTransformationResult sinkholeMethod(
            Method method, PrivacyStats stats, PrivacyOptions options) {
        PrivacyStats methodStats = new PrivacyStats();
        if (options == null) options = PrivacyOptions.all();

        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return new PrivacyBytecodeEngine.MethodTransformationResult(false, methodStats, method);
        }

        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<BuilderInstruction> instructions = mutable.getInstructions();
        boolean modified = false;

        for (int i = 0; i < instructions.size(); i++) {
            BuilderInstruction inst = instructions.get(i);
            Opcode op = inst.getOpcode();

            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                if (inst instanceof OneRegisterInstruction && inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof StringReference) {
                        String str = ((StringReference) ref).getString();
                        boolean shouldSinkhole = false;

                        if (options.nullifyAiAssistants && isAiEndpoint(str)) {
                            methodStats.aiEndpointsSinkholed++;
                            shouldSinkhole = true;
                        } else if (options.sinkholeTelemetryDomains && isTelemetryEndpoint(str)) {
                            methodStats.telemetryDomainsSinkholed++;
                            shouldSinkhole = true;
                        }

                        if (shouldSinkhole) {
                            int reg = ((OneRegisterInstruction) inst).getRegisterA();
                            String replacement = (str.startsWith("http://") || str.startsWith("https://") || str.startsWith("ws://") || str.startsWith("wss://"))
                                    ? SINKHOLE_DESTINATION
                                    : "127.0.0.1";
                            mutable.replaceInstruction(i, new BuilderInstruction21c(
                                    Opcode.CONST_STRING,
                                    reg,
                                    new ImmutableStringReference(replacement)
                            ));
                            modified = true;
                        }
                    }
                }
            }
        }

        if (stats != null) {
            stats.merge(methodStats);
        }

        if (modified) {
            Method transformed = new ImmutableMethod(
                    method.getDefiningClass(),
                    method.getName(),
                    method.getParameters(),
                    method.getReturnType(),
                    method.getAccessFlags(),
                    method.getAnnotations(),
                    method.getHiddenApiRestrictions(),
                    mutable
            );
            return new PrivacyBytecodeEngine.MethodTransformationResult(true, methodStats, transformed);
        }

        return new PrivacyBytecodeEngine.MethodTransformationResult(false, methodStats, method);
    }

    /**
     * Sinkholes all telemetry and AI URLs across all methods in a class with default options.
     */
    public static ClassDef sinkholeClass(ClassDef classDef, PrivacyStats outStats) {
        return sinkholeClass(classDef, outStats, PrivacyOptions.all());
    }

    /**
     * Sinkholes telemetry and/or AI URLs across all methods in a class according to options.
     */
    public static ClassDef sinkholeClass(ClassDef classDef, PrivacyStats outStats, PrivacyOptions options) {
        if (options == null) options = PrivacyOptions.all();
        List<Method> directMethods = new ArrayList<>();
        List<Method> virtualMethods = new ArrayList<>();
        boolean classModified = false;

        for (Method m : classDef.getDirectMethods()) {
            PrivacyStats methodStats = new PrivacyStats();
            PrivacyBytecodeEngine.MethodTransformationResult result = sinkholeMethod(m, methodStats, options);
            if (result.modified) {
                classModified = true;
                if (outStats != null) outStats.merge(result.stats);
                directMethods.add(result.method);
            } else {
                directMethods.add(m);
            }
        }

        for (Method m : classDef.getVirtualMethods()) {
            PrivacyStats methodStats = new PrivacyStats();
            PrivacyBytecodeEngine.MethodTransformationResult result = sinkholeMethod(m, methodStats, options);
            if (result.modified) {
                classModified = true;
                if (outStats != null) outStats.merge(result.stats);
                virtualMethods.add(result.method);
            } else {
                virtualMethods.add(m);
            }
        }

        if (classModified) {
            if (outStats != null) outStats.classesModified++;
            return new ImmutableClassDef(
                    classDef.getType(),
                    classDef.getAccessFlags(),
                    classDef.getSuperclass(),
                    classDef.getInterfaces(),
                    classDef.getSourceFile(),
                    classDef.getAnnotations(),
                    classDef.getStaticFields(),
                    classDef.getInstanceFields(),
                    directMethods,
                    virtualMethods
            );
        }

        return classDef;
    }
}
