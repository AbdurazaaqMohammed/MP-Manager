package io.github.abdurazaaqmohammed.feature.apk.privacy;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.feature.apk.DexRepairEngine;

/**
 * Static semantic scanner for privacy-invasive capabilities, device fingerprinting,
 * surveillance checks, and third-party tracking telemetry across Dalvik bytecode.
 */
public final class PrivacyScannerEngine {

    private PrivacyScannerEngine() {}

    private static final String TELEPHONY_MANAGER = "Landroid/telephony/TelephonyManager;";
    private static final Set<String> TELEPHONY_GETTERS = new HashSet<>(Arrays.asList(
            "getDeviceId", "getImei", "getMeid", "getSubscriberId",
            "getLine1Number", "getSimSerialNumber", "getDeviceSoftwareVersion"
    ));

    private static final String SETTINGS_SECURE = "Landroid/provider/Settings$Secure;";
    private static final String SETTINGS_GLOBAL = "Landroid/provider/Settings$Global;";
    private static final String SETTINGS_SYSTEM = "Landroid/provider/Settings$System;";
    private static final String SYSTEM_PROPERTIES = "Landroid/os/SystemProperties;";
    private static final String ADVERTISING_INFO = "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;";
    private static final String LOG_CLASS = "Landroid/util/Log;";

    private static final Set<String> ADB_SETTINGS_KEYS = new HashSet<>(Arrays.asList(
            "adb_enabled", "development_settings_enabled", "adb_wifi_enabled", "wireless_debugging_enabled"
    ));

    private static final Set<String> ADB_PROPERTY_KEYS = new HashSet<>(Arrays.asList(
            "ro.debuggable", "service.adb.root"
    ));

    private static final Set<String> TRACKER_PREFIXES = new HashSet<>(Arrays.asList(
            "com/appsflyer",
            "com/adjust/sdk",
            "com/google/firebase/analytics",
            "com/facebook/appevents",
            "com/segment",
            "com/amplitude",
            "com/mixpanel",
            "io/branch",
            "com/kochava",
            "com/flurry",
            "com/clevertap",
            "com/webengage",
            "com/unity3d/services/analytics"
    ));

    /**
     * Scans an entire APK and returns a detailed PrivacyAuditReport.
     */
    public static PrivacyAuditReport scanApk(File apkFile) throws Exception {
        ApkModule module = ApkModule.loadApkFile(apkFile);
        module.setLoadDefaultFramework(false);
        PrivacyAuditReport report = new PrivacyAuditReport();

        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (!name.matches("classes\\d*\\.dex")) continue;

            byte[] dexBytes = readAll(source.openStream());
            DexBackedDexFile dex = new DexBackedDexFile(PrivacyBytecodeEngine.opcodesForDex(dexBytes), dexBytes);
            scanDexInternal(dex, report);
        }

        NativeLibAuditor.auditNativeLibs(module, report);
        report.computeScores();
        return report;
    }

    /**
     * Scans a single DEX file and returns a detailed PrivacyAuditReport.
     */
    public static PrivacyAuditReport scanDex(File dexFile) throws Exception {
        DexRepairEngine.repairHeader(dexFile);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, null);
        PrivacyAuditReport report = new PrivacyAuditReport();
        scanDexInternal(dex, report);
        report.computeScores();
        return report;
    }

    /**
     * Scans a collection of ClassDef objects.
     */
    public static PrivacyAuditReport scanClasses(Iterable<? extends ClassDef> classes) {
        PrivacyAuditReport report = new PrivacyAuditReport();
        for (ClassDef cd : classes) {
            scanClass(cd, report);
        }
        report.computeScores();
        return report;
    }

    private static void scanDexInternal(DexBackedDexFile dex, PrivacyAuditReport report) {
        for (ClassDef cd : dex.getClasses()) {
            scanClass(cd, report);
        }
    }

    private static void scanClass(ClassDef classDef, PrivacyAuditReport report) {
        report.totalClassesScanned++;
        String classType = classDef.getType();
        boolean classHasThreat = false;

        // Check if class itself is a tracking SDK
        for (String prefix : TRACKER_PREFIXES) {
            if (classType.contains(prefix)) {
                report.addThreat(new DetectedThreat(
                        DetectedThreat.Category.TRACKER_SDK,
                        "Third-Party Telemetry SDK: " + extractSdkName(prefix),
                        "App embeds third-party user behavior and analytics tracking SDK.",
                        classType,
                        "<class>",
                        DetectedThreat.ExcisionType.MANIFEST_STRIP,
                        false
                ));
                classHasThreat = true;
                break;
            }
        }

        List<Method> methods = new ArrayList<>();
        for (Method m : classDef.getDirectMethods()) methods.add(m);
        for (Method m : classDef.getVirtualMethods()) methods.add(m);

        for (Method method : methods) {
            MethodImplementation impl = method.getImplementation();
            if (impl == null) continue;

            List<Instruction> instructions = new ArrayList<>();
            for (Instruction inst : impl.getInstructions()) {
                instructions.add(inst);
            }

            Map<Integer, String> stringRegisters = new HashMap<>();

            for (int i = 0; i < instructions.size(); i++) {
                Instruction inst = instructions.get(i);
                Opcode op = inst.getOpcode();

                // String register tracking
                if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                    if (inst instanceof OneRegisterInstruction && inst instanceof ReferenceInstruction) {
                        int reg = ((OneRegisterInstruction) inst).getRegisterA();
                        Object ref = ((ReferenceInstruction) inst).getReference();
                        if (ref instanceof StringReference) {
                            stringRegisters.put(reg, ((StringReference) ref).getString());
                        }
                    }
                }

                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        String defClass = mr.getDefiningClass();
                        String mName = mr.getName();

                        // 1. Hardware Fingerprinting: TelephonyManager
                        if (TELEPHONY_MANAGER.equals(defClass) && TELEPHONY_GETTERS.contains(mName)) {
                            report.addThreat(new DetectedThreat(
                                    DetectedThreat.Category.HARDWARE_FINGERPRINT,
                                    "Telephony Identifier Read (" + mName + ")",
                                    "Queries hardware identifier (IMEI, SIM serial, or phone number).",
                                    classType,
                                    method.getName(),
                                    DetectedThreat.ExcisionType.BLANK_RETURN_STRING,
                                    false
                            ));
                            classHasThreat = true;
                        }

                        // 2. Hardware Fingerprinting: Advertising ID
                        else if (ADVERTISING_INFO.equals(defClass) && "getId".equals(mName)) {
                            report.addThreat(new DetectedThreat(
                                    DetectedThreat.Category.HARDWARE_FINGERPRINT,
                                    "Google Advertising ID Query",
                                    "Fetches persistent advertising identifier used for ad targeting.",
                                    classType,
                                    method.getName(),
                                    DetectedThreat.ExcisionType.BLANK_RETURN_STRING,
                                    false
                            ));
                            classHasThreat = true;
                        }

                        // 3. Hardware Fingerprinting: Settings.Secure.getString(android_id)
                        else if (SETTINGS_SECURE.equals(defClass) && ("getString".equals(mName) || "getStringForUser".equals(mName))) {
                            int keyReg = getArgumentRegister(inst, 1);
                            if (keyReg >= 0) {
                                String key = resolveKeyString(instructions, i, keyReg, stringRegisters);
                                if ("android_id".equalsIgnoreCase(key)) {
                                    report.addThreat(new DetectedThreat(
                                            DetectedThreat.Category.HARDWARE_FINGERPRINT,
                                            "Android ID Query (Settings.Secure)",
                                            "Reads permanent Android hardware device ID.",
                                            classType,
                                            method.getName(),
                                            DetectedThreat.ExcisionType.BLANK_RETURN_STRING,
                                            false
                                    ));
                                    classHasThreat = true;
                                }
                            }
                        }

                        // 4. Hardware Fingerprinting: MediaDrm / Widevine ID
                        else if ("Landroid/media/MediaDrm;".equals(defClass)
                                && ("getPropertyByteArray".equals(mName) || "getPropertyString".equals(mName))) {
                            report.addThreat(new DetectedThreat(
                                    DetectedThreat.Category.HARDWARE_FINGERPRINT,
                                    "DRM / Widevine Device Fingerprinting",
                                    "Queries DRM security level or hardware-backed device unique ID.",
                                    classType,
                                    method.getName(),
                                    DetectedThreat.ExcisionType.BLANK_RETURN_STRING,
                                    false
                            ));
                            classHasThreat = true;
                        }

                        // 5. Surveillance: Installed Packages Enumeration
                        else if ("Landroid/content/pm/PackageManager;".equals(defClass)
                                && ("getInstalledPackages".equals(mName) || "getInstalledApplications".equals(mName))) {
                            report.addThreat(new DetectedThreat(
                                    DetectedThreat.Category.SURVEILLANCE,
                                    "Installed Packages Enumeration",
                                    "Snoops on other installed applications on the user's device.",
                                    classType,
                                    method.getName(),
                                    DetectedThreat.ExcisionType.NOP_INVOKE,
                                    false
                            ));
                            classHasThreat = true;
                        }

                        // 6. Surveillance: ADB / Developer Options Detection
                        else if ((SETTINGS_GLOBAL.equals(defClass) || SETTINGS_SECURE.equals(defClass) || SETTINGS_SYSTEM.equals(defClass))
                                && ("getInt".equals(mName) || "getLong".equals(mName))) {
                            int keyReg = getArgumentRegister(inst, 1);
                            if (keyReg >= 0) {
                                String key = resolveKeyString(instructions, i, keyReg, stringRegisters);
                                if (key != null && ADB_SETTINGS_KEYS.contains(key)) {
                                    report.addThreat(new DetectedThreat(
                                            DetectedThreat.Category.SURVEILLANCE,
                                            "Developer Mode / ADB Detection (" + key + ")",
                                            "Checks if ADB debugging or developer settings are active.",
                                            classType,
                                            method.getName(),
                                            DetectedThreat.ExcisionType.FORCE_INT_ZERO,
                                            false
                                    ));
                                    classHasThreat = true;
                                }
                            }
                        }

                        // 7. Surveillance: SystemProperties.get(ro.debuggable / service.adb.root)
                        else if (SYSTEM_PROPERTIES.equals(defClass) && "get".equals(mName)) {
                            int keyReg = getArgumentRegister(inst, 0);
                            if (keyReg >= 0) {
                                String key = resolveKeyString(instructions, i, keyReg, stringRegisters);
                                if (key != null && ADB_PROPERTY_KEYS.contains(key)) {
                                    report.addThreat(new DetectedThreat(
                                            DetectedThreat.Category.SURVEILLANCE,
                                            "System Property Root/Debug Probe (" + key + ")",
                                            "Inspects system build properties to detect rooted or debuggable ROM.",
                                            classType,
                                            method.getName(),
                                            DetectedThreat.ExcisionType.BLANK_RETURN_STRING,
                                            false
                                    ));
                                    classHasThreat = true;
                                }
                            }
                        }

                        // 8. Surveillance: Root command execution (Runtime.exec / ProcessBuilder)
                        else if (("Ljava/lang/Runtime;".equals(defClass) && "exec".equals(mName))
                                || ("Ljava/lang/ProcessBuilder;".equals(defClass) && "start".equals(mName))) {
                            report.addThreat(new DetectedThreat(
                                    DetectedThreat.Category.SURVEILLANCE,
                                    "Command Execution / Shell Probe",
                                    "Spawns Linux shell process, commonly used to probe su/mount/getprop.",
                                    classType,
                                    method.getName(),
                                    DetectedThreat.ExcisionType.NOP_INVOKE,
                                    false
                            ));
                            classHasThreat = true;
                        }

                        // 9. Information Leakage: android.util.Log
                        else if (LOG_CLASS.equals(defClass)) {
                            report.addThreat(new DetectedThreat(
                                    DetectedThreat.Category.LOG_LEAK,
                                    "Debug Log Leak (android.util.Log." + mName + ")",
                                    "Logs debug/PII information to system logcat in production.",
                                    classType,
                                    method.getName(),
                                    DetectedThreat.ExcisionType.NOP_INVOKE,
                                    false
                            ));
                            classHasThreat = true;
                        }
                    }
                }
            }
        }

        if (classHasThreat) {
            report.invasiveClassesCount++;
        }
    }

    private static String extractSdkName(String prefix) {
        String[] parts = prefix.split("/");
        return parts[parts.length - 1];
    }

    private static int getArgumentRegister(Instruction inst, int argIndex) {
        if (inst instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction fri = (FiveRegisterInstruction) inst;
            return switch (argIndex) {
                case 0 -> fri.getRegisterC();
                case 1 -> fri.getRegisterD();
                case 2 -> fri.getRegisterE();
                case 3 -> fri.getRegisterF();
                case 4 -> fri.getRegisterG();
                default -> -1;
            };
        } else if (inst instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction rri = (RegisterRangeInstruction) inst;
            if (argIndex < rri.getRegisterCount()) {
                return rri.getStartRegister() + argIndex;
            }
        }
        return -1;
    }

    private static String resolveKeyString(List<Instruction> instructions, int invokeIdx, int keyReg, Map<Integer, String> stringRegisters) {
        if (stringRegisters.containsKey(keyReg)) {
            return stringRegisters.get(keyReg);
        }
        int start = Math.max(0, invokeIdx - 10);
        for (int j = invokeIdx - 1; j >= start; j--) {
            Instruction prev = instructions.get(j);
            Opcode op = prev.getOpcode();
            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                if (prev instanceof OneRegisterInstruction && prev instanceof ReferenceInstruction) {
                    if (((OneRegisterInstruction) prev).getRegisterA() == keyReg) {
                        Object ref = ((ReferenceInstruction) prev).getReference();
                        if (ref instanceof StringReference) {
                            return ((StringReference) ref).getString();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        try (is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }
}
