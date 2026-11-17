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
import java.util.regex.Pattern;

/**
 * Neutralizes telemetry, tracking, and analytics endpoints by sinkholing
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
            "telemetry.",
            "analytics."
    );

    private static final Pattern TELEMETRY_URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9.-]*(telemetry|analytics|metrics|tracking|crashlytics|app-measurement|appsflyer|adjust\\.com|branch\\.io|kochava|mixpanel|amplitude|segment\\.io)[a-zA-Z0-9./_?=-]*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Checks if a string URL or domain points to a telemetry or tracking collector.
     */
    public static boolean isTelemetryEndpoint(String str) {
        if (str == null || str.length() < 10) return false;
        for (String domain : KNOWN_TELEMETRY_DOMAINS) {
            if (str.contains(domain)) return true;
        }
        return TELEMETRY_URL_PATTERN.matcher(str).matches();
    }

    /**
     * Sinkholes all telemetry URLs in a method to local loopback.
     */
    public static PrivacyBytecodeEngine.MethodTransformationResult sinkholeMethod(Method method) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return new PrivacyBytecodeEngine.MethodTransformationResult(false, new PrivacyStats(), method);
        }

        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<BuilderInstruction> instructions = mutable.getInstructions();
        PrivacyStats stats = new PrivacyStats();
        boolean modified = false;

        for (int i = 0; i < instructions.size(); i++) {
            BuilderInstruction inst = instructions.get(i);
            Opcode op = inst.getOpcode();

            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                if (inst instanceof OneRegisterInstruction && inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof StringReference) {
                        String str = ((StringReference) ref).getString();
                        if (isTelemetryEndpoint(str)) {
                            int reg = ((OneRegisterInstruction) inst).getRegisterA();
                            mutable.replaceInstruction(i, new BuilderInstruction21c(
                                    Opcode.CONST_STRING,
                                    reg,
                                    new ImmutableStringReference(SINKHOLE_DESTINATION)
                            ));
                            stats.adIdsNeutralized++;
                            modified = true;
                        }
                    }
                }
            }
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
            return new PrivacyBytecodeEngine.MethodTransformationResult(true, stats, transformed);
        }

        return new PrivacyBytecodeEngine.MethodTransformationResult(false, stats, method);
    }

    /**
     * Sinkholes all telemetry URLs across all methods in a class.
     */
    public static ClassDef sinkholeClass(ClassDef classDef, PrivacyStats outStats) {
        List<Method> directMethods = new ArrayList<>();
        List<Method> virtualMethods = new ArrayList<>();
        boolean classModified = false;

        for (Method m : classDef.getDirectMethods()) {
            PrivacyBytecodeEngine.MethodTransformationResult result = sinkholeMethod(m);
            if (result.modified) {
                classModified = true;
                if (outStats != null) outStats.merge(result.stats);
                directMethods.add(result.method);
            } else {
                directMethods.add(m);
            }
        }

        for (Method m : classDef.getVirtualMethods()) {
            PrivacyBytecodeEngine.MethodTransformationResult result = sinkholeMethod(m);
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
