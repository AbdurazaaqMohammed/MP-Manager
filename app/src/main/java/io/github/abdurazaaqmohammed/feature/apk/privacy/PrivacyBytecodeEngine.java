package io.github.abdurazaaqmohammed.feature.apk.privacy;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21s;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31i;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction51l;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.feature.apk.DexDebugStripper;
import io.github.abdurazaaqmohammed.feature.apk.DexRepairEngine;

/**
 * High-performance in-memory Dalvik bytecode mutation engine for privacy hardening.
 * Applies surgical opcode transformations to strip telemetry, blank hardware identifiers,
 * neutralize ad tracking, and mask developer/root environment checks without requiring device root.
 */
public final class PrivacyBytecodeEngine {

    private PrivacyBytecodeEngine() {}

    private static final String LOG_CLASS = "Landroid/util/Log;";
    private static final Set<String> LOG_METHODS = new HashSet<>(Arrays.asList(
            "v", "d", "i", "w", "e", "wtf", "println"
    ));

    private static final String TELEPHONY_MANAGER = "Landroid/telephony/TelephonyManager;";
    private static final Set<String> TELEPHONY_GETTERS = new HashSet<>(Arrays.asList(
            "getDeviceId", "getImei", "getMeid", "getSubscriberId",
            "getLine1Number", "getSimSerialNumber", "getDeviceSoftwareVersion"
    ));

    private static final String ADVERTISING_INFO = "Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;";
    private static final String SETTINGS_SECURE = "Landroid/provider/Settings$Secure;";
    private static final String SETTINGS_GLOBAL = "Landroid/provider/Settings$Global;";
    private static final String SETTINGS_SYSTEM = "Landroid/provider/Settings$System;";
    private static final String SYSTEM_PROPERTIES = "Landroid/os/SystemProperties;";
    private static final String BUILD_CLASS = "Landroid/os/Build;";
    private static final String WIFI_INFO_CLASS = "Landroid/net/wifi/WifiInfo;";
    private static final String NETWORK_INTERFACE_CLASS = "Ljava/net/NetworkInterface;";
    private static final String WORK_MANAGER_CLASS = "Landroidx/work/WorkManager;";

    private static final String[] PURE_TRACKER_PACKAGES = {
            "Lcom/cujo/otm/telemetry/",
            "Lcom/appsflyer/",
            "Lcom/adjust/sdk/",
            "Lcom/google/firebase/analytics/",
            "Lcom/google/firebase/crashlytics/",
            "Lcom/branch/io/",
            "Lio/branch/",
            "Lcom/kochava/base/",
            "Lcom/kochava/",
            "Lcom/singular/sdk/",
            "Lcom/threatmetrix/",
            "Lcom/facebook/appevents/",
            "Lcom/segment/",
            "Lcom/amplitude/",
            "Lcom/mixpanel/",
            "Lcom/flurry/",
            "Lcom/clevertap/",
            "Lcom/webengage/",
            "Lcom/unity3d/services/analytics/"
    };

    private static final Set<String> ADB_SETTINGS_KEYS = new HashSet<>(Arrays.asList(
            "adb_enabled", "development_settings_enabled", "adb_wifi_enabled", "wireless_debugging_enabled"
    ));

    private static final Set<String> ADB_PROPERTY_KEYS = new HashSet<>(Arrays.asList(
            "ro.debuggable", "service.adb.root"
    ));

    private static final Pattern TELEMETRY_WORKER_PATTERN = Pattern.compile(
            ".*(analytics|telemetry|tracker|tracking|metric|cujo|otm).*",
            Pattern.CASE_INSENSITIVE
    );

    public static final String WORKER_RESULT_CLASS = "Landroidx/work/ListenableWorker$Result;";

    public static class MethodTransformationResult {
        public final boolean modified;
        public final PrivacyStats stats;
        public final Method method;

        public MethodTransformationResult(boolean modified, PrivacyStats stats, Method method) {
            this.modified = modified;
            this.stats = stats;
            this.method = method;
        }
    }

    /**
     * Sanitizes a single MethodImplementation according to the supplied PrivacyOptions.
     */
    public static MethodTransformationResult sanitizeMethod(Method method, PrivacyOptions options) {
        if (options == null) options = PrivacyOptions.all();
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return new MethodTransformationResult(false, new PrivacyStats(), method);
        }

        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<BuilderInstruction> instructions = mutable.getInstructions();
        PrivacyStats stats = new PrivacyStats();
        boolean modified = false;

        boolean blankHardwareSerial = options.blankHardwareSerial || options.mode == SanitizationMode.AGGRESSIVE;
        boolean blankWifiMac = options.blankWifiMac || options.mode == SanitizationMode.AGGRESSIVE;
        boolean suppressPeriodicWork = options.suppressPeriodicWorkEnqueue || options.mode == SanitizationMode.AGGRESSIVE;

        Map<Integer, String> stringRegisters = new HashMap<>();

        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);
            Opcode op = inst.getOpcode();

            // Track string registers for key-matching
            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                if (inst instanceof OneRegisterInstruction && inst instanceof ReferenceInstruction) {
                    int reg = ((OneRegisterInstruction) inst).getRegisterA();
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof StringReference) {
                        String sVal = ((StringReference) ref).getString();
                        stringRegisters.put(reg, sVal);
                        if (options.sinkholeTelemetryDomains && DomainSinkholer.isTelemetryEndpoint(sVal)) {
                            mutable.replaceInstruction(i, new BuilderInstruction21c(
                                    Opcode.CONST_STRING,
                                    reg,
                                    new ImmutableStringReference(DomainSinkholer.SINKHOLE_DESTINATION)
                            ));
                            stats.adIdsNeutralized++;
                            modified = true;
                            stringRegisters.put(reg, DomainSinkholer.SINKHOLE_DESTINATION);
                        }
                    }
                }
            } else if (op == Opcode.MOVE_OBJECT || op == Opcode.MOVE_OBJECT_16 || op == Opcode.MOVE_OBJECT_FROM16) {
                if (inst instanceof TwoRegisterInstruction) {
                    int dest = ((TwoRegisterInstruction) inst).getRegisterA();
                    int src = ((TwoRegisterInstruction) inst).getRegisterB();
                    if (stringRegisters.containsKey(src)) {
                        stringRegisters.put(dest, stringRegisters.get(src));
                    } else {
                        stringRegisters.remove(dest);
                    }
                }
            }

            // 1. Log Stripping (android.util.Log)
            if (options.stripLogging && isInvokeStatic(op)) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        if (LOG_CLASS.equals(mr.getDefiningClass()) && LOG_METHODS.contains(mr.getName()) && "I".equals(mr.getReturnType())) {
                            mutable.replaceInstruction(i, new BuilderInstruction10x(Opcode.NOP));
                            stats.logCallsStripped++;
                            modified = true;

                            if (i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, createConstInt(resReg, 0));
                                }
                            }
                        }
                    }
                }
            }

            // 2. Empty Telephony Identifiers (IMEI, SIM serial, Phone number)
            if (options.emptyTelephonyIds && isInvokeVirtualOrInterface(op)) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        if (TELEPHONY_MANAGER.equals(mr.getDefiningClass())
                                && TELEPHONY_GETTERS.contains(mr.getName())
                                && "Ljava/lang/String;".equals(mr.getReturnType())) {
                            if (i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                            Opcode.CONST_STRING,
                                            resReg,
                                            new ImmutableStringReference("")
                                    ));
                                    stats.telephonyGettersBlanked++;
                                    modified = true;
                                    stringRegisters.put(resReg, "");
                                }
                            }
                        }
                    }
                }
            }

            // 3. Neutralize Advertising & Device Identifiers
            if (options.neutralizeAdIds) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        // AdvertisingIdClient$Info.getId() -> ""
                        if (ADVERTISING_INFO.equals(mr.getDefiningClass()) && "getId".equals(mr.getName()) && "Ljava/lang/String;".equals(mr.getReturnType())) {
                            if (i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                            Opcode.CONST_STRING,
                                            resReg,
                                            new ImmutableStringReference("")
                                    ));
                                    stats.adIdsNeutralized++;
                                    modified = true;
                                    stringRegisters.put(resReg, "");
                                }
                            }
                        }
                        // AdvertisingIdClient$Info.isLimitAdTrackingEnabled() -> 1 (true)
                        else if (ADVERTISING_INFO.equals(mr.getDefiningClass()) && "isLimitAdTrackingEnabled".equals(mr.getName()) && "Z".equals(mr.getReturnType())) {
                            if (i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, createConstInt(resReg, 1));
                                    stats.adIdsNeutralized++;
                                    modified = true;
                                }
                            }
                        }
                        // Settings.Secure.getString(..., "android_id") -> ""
                        else if (SETTINGS_SECURE.equals(mr.getDefiningClass())
                                && ("getString".equals(mr.getName()) || "getStringForUser".equals(mr.getName()))
                                && "Ljava/lang/String;".equals(mr.getReturnType())) {
                            int keyReg = getArgumentRegister(inst, 1);
                            if (keyReg >= 0) {
                                String key = resolveKeyString(instructions, i, keyReg, stringRegisters);
                                if ("android_id".equalsIgnoreCase(key)) {
                                    if (i + 1 < instructions.size()) {
                                        Instruction next = instructions.get(i + 1);
                                        if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                            int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                            mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                                    Opcode.CONST_STRING,
                                                    resReg,
                                                    new ImmutableStringReference("")
                                            ));
                                            stats.adIdsNeutralized++;
                                            modified = true;
                                            stringRegisters.put(resReg, "");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Developer Mode & ADB Masker
            if (options.maskDeveloperMode) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        String defClass = mr.getDefiningClass();

                        // Settings.Global/Secure/System.getInt/getLong
                        if (SETTINGS_GLOBAL.equals(defClass) || SETTINGS_SECURE.equals(defClass) || SETTINGS_SYSTEM.equals(defClass)) {
                            if ("getInt".equals(mr.getName()) || "getLong".equals(mr.getName())) {
                                int keyReg = getArgumentRegister(inst, 1);
                                if (keyReg >= 0) {
                                    String key = resolveKeyString(instructions, i, keyReg, stringRegisters);
                                    if (key != null && ADB_SETTINGS_KEYS.contains(key)) {
                                        if (i + 1 < instructions.size()) {
                                            Instruction next = instructions.get(i + 1);
                                            if (next.getOpcode() == Opcode.MOVE_RESULT && next instanceof OneRegisterInstruction) {
                                                int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                                mutable.replaceInstruction(i + 1, createConstInt(resReg, 0));
                                                stats.devModeChecksMasked++;
                                                modified = true;
                                            } else if (next.getOpcode() == Opcode.MOVE_RESULT_WIDE && next instanceof OneRegisterInstruction) {
                                                int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                                mutable.replaceInstruction(i + 1, createConstWide(resReg, 0L));
                                                stats.devModeChecksMasked++;
                                                modified = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // SystemProperties.get("ro.debuggable" | "service.adb.root") -> "0"
                        else if (SYSTEM_PROPERTIES.equals(defClass) && "get".equals(mr.getName()) && "Ljava/lang/String;".equals(mr.getReturnType())) {
                            int keyReg = getArgumentRegister(inst, 0);
                            if (keyReg >= 0) {
                                String key = resolveKeyString(instructions, i, keyReg, stringRegisters);
                                if (key != null && ADB_PROPERTY_KEYS.contains(key)) {
                                    if (i + 1 < instructions.size()) {
                                        Instruction next = instructions.get(i + 1);
                                        if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                            int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                            mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                                    Opcode.CONST_STRING,
                                                    resReg,
                                                    new ImmutableStringReference("0")
                                            ));
                                            stats.devModeChecksMasked++;
                                            modified = true;
                                            stringRegisters.put(resReg, "0");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Cloud Experiment Gates (WebLab / LaunchDarkly)
            if (options.nullifyAiAssistants && options.forceAiExperimentControl && isInvoke(op)) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        if (AiBytecodeRules.isExperimentGateInvocation(inst, mr, instructions, i, stringRegisters)) {
                            if (AiBytecodeRules.interceptWebLabTreatment(mutable, i, stats)) {
                                modified = true;
                                if (i + 1 < instructions.size()) {
                                    Instruction next = instructions.get(i + 1);
                                    if (next.getOpcode() == Opcode.CONST_STRING && next instanceof OneRegisterInstruction) {
                                        stringRegisters.put(((OneRegisterInstruction) next).getRegisterA(), "C");
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Aggressive Hardware Fingerprinting Nullification
            // Build.SERIAL static field -> const-string ""
            if (blankHardwareSerial && op == Opcode.SGET_OBJECT) {
                if (inst instanceof OneRegisterInstruction && inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof FieldReference) {
                        FieldReference fr = (FieldReference) ref;
                        if (BUILD_CLASS.equals(fr.getDefiningClass())
                                && "SERIAL".equals(fr.getName())
                                && "Ljava/lang/String;".equals(fr.getType())) {
                            int reg = ((OneRegisterInstruction) inst).getRegisterA();
                            mutable.replaceInstruction(i, new BuilderInstruction21c(
                                    Opcode.CONST_STRING,
                                    reg,
                                    new ImmutableStringReference("")
                            ));
                            stats.hardwareIdentifiersBlanked++;
                            modified = true;
                            stringRegisters.put(reg, "");
                        }
                    }
                }
            }

            // Build.getSerial() static method -> NOP invoke + const-string ""
            if (blankHardwareSerial && isInvokeStatic(op)) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        if (BUILD_CLASS.equals(mr.getDefiningClass())
                                && "getSerial".equals(mr.getName())
                                && "Ljava/lang/String;".equals(mr.getReturnType())) {
                            mutable.replaceInstruction(i, new BuilderInstruction10x(Opcode.NOP));
                            stats.hardwareIdentifiersBlanked++;
                            modified = true;

                            if (i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                            Opcode.CONST_STRING,
                                            resReg,
                                            new ImmutableStringReference("")
                                    ));
                                    stringRegisters.put(resReg, "");
                                }
                            }
                        }
                    }
                }
            }

            // WifiInfo (getMacAddress, getSSID, getBSSID) & NetworkInterface (getHardwareAddress)
            if (blankWifiMac && isInvokeVirtualOrInterface(op)) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        if (WIFI_INFO_CLASS.equals(mr.getDefiningClass()) && "Ljava/lang/String;".equals(mr.getReturnType())) {
                            String replacement = null;
                            if ("getMacAddress".equals(mr.getName())) {
                                replacement = "02:00:00:00:00:00";
                            } else if ("getSSID".equals(mr.getName())) {
                                replacement = "<unknown ssid>";
                            } else if ("getBSSID".equals(mr.getName())) {
                                replacement = "02:00:00:00:00:00";
                            }

                            if (replacement != null && i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                            Opcode.CONST_STRING,
                                            resReg,
                                            new ImmutableStringReference(replacement)
                                    ));
                                    stats.hardwareIdentifiersBlanked++;
                                    modified = true;
                                    stringRegisters.put(resReg, replacement);
                                }
                            }
                        } else if (NETWORK_INTERFACE_CLASS.equals(mr.getDefiningClass())
                                && "getHardwareAddress".equals(mr.getName())
                                && "[B".equals(mr.getReturnType())) {
                            if (i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, createConstInt(resReg, 0));
                                    stats.hardwareIdentifiersBlanked++;
                                    modified = true;
                                    stringRegisters.remove(resReg);
                                }
                            }
                        }
                    }
                }
            }

            // 7. Aggressive WorkManager Periodic Enqueue Suppression
            if (suppressPeriodicWork && isInvoke(op)) {
                if (inst instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mr = (MethodReference) ref;
                        if (WORK_MANAGER_CLASS.equals(mr.getDefiningClass())
                                && ("enqueueUniquePeriodicWork".equals(mr.getName()) || "enqueuePeriodicWork".equals(mr.getName()))) {
                            mutable.replaceInstruction(i, new BuilderInstruction10x(Opcode.NOP));
                            stats.periodicWorkEnqueueSuppressed++;
                            modified = true;

                            if (i + 1 < instructions.size()) {
                                Instruction next = instructions.get(i + 1);
                                if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                    int resReg = ((OneRegisterInstruction) next).getRegisterA();
                                    mutable.replaceInstruction(i + 1, createConstInt(resReg, 0));
                                    stringRegisters.remove(resReg);
                                }
                            }
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
            return new MethodTransformationResult(true, stats, transformed);
        }

        return new MethodTransformationResult(false, stats, method);
    }

    /**
     * Detects if a ClassDef extends androidx.work.Worker or ListenableWorker (or superclass contains Worker).
     */
    public static boolean isWorkManagerWorker(ClassDef classDef) {
        if (classDef == null) return false;
        String superclass = classDef.getSuperclass();
        if (superclass == null) return false;
        String normSuper = DexReachabilityIndex.normalizeClassType(superclass);
        return "Landroidx/work/Worker;".equals(normSuper)
                || "Landroidx/work/ListenableWorker;".equals(normSuper)
                || normSuper.contains("Worker");
    }

    /**
     * Checks if a class is a WorkManager worker matching telemetry naming patterns.
     */
    public static boolean isTelemetryWorker(ClassDef classDef) {
        if (classDef == null) return false;
        if (!isWorkManagerWorker(classDef)) return false;
        String type = classDef.getType();
        return type != null && TELEMETRY_WORKER_PATTERN.matcher(type).matches();
    }

    /**
     * Checks if a class descriptor belongs to a known pure tracker package.
     */
    public static boolean isPureTrackerClass(String type) {
        if (type == null) return false;
        String norm = DexReachabilityIndex.normalizeClassType(type);
        for (String prefix : PURE_TRACKER_PACKAGES) {
            if (norm.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Checks if a method is doWork() returning ListenableWorker$Result.
     */
    public static boolean isDoWorkMethod(Method method) {
        if (method == null) return false;
        return "doWork".equals(method.getName())
                && WORKER_RESULT_CLASS.equals(DexReachabilityIndex.normalizeClassType(method.getReturnType()))
                && (method.getParameters() == null || method.getParameters().isEmpty());
    }

    /**
     * Checks if a ClassDef defines a doWork() method.
     */
    public static boolean hasDoWorkMethod(ClassDef classDef) {
        if (classDef == null) return false;
        for (Method m : classDef.getVirtualMethods()) {
            if (isDoWorkMethod(m)) return true;
        }
        for (Method m : classDef.getDirectMethods()) {
            if (isDoWorkMethod(m)) return true;
        }
        return false;
    }

    /**
     * Neutralizes doWork() implementation by replacing instructions with:
     * invoke-static {} Landroidx/work/ListenableWorker$Result;->success()Landroidx/work/ListenableWorker$Result;
     * move-result-object v0
     * return-object v0
     * Ensures registerCount >= 1.
     */
    public static MutableMethodImplementation neutralizeWorkerClass(ClassDef classDef, MutableMethodImplementation mutable, PrivacyStats stats) {
        int regCount = mutable != null ? Math.max(1, mutable.getRegisterCount()) : 1;
        MutableMethodImplementation newImpl = new MutableMethodImplementation(regCount);

        BuilderInstruction invoke = new BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                0, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        WORKER_RESULT_CLASS,
                        "success",
                        Collections.emptyList(),
                        WORKER_RESULT_CLASS
                )
        );
        BuilderInstruction moveResult = new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0);
        BuilderInstruction returnObj = new BuilderInstruction11x(Opcode.RETURN_OBJECT, 0);

        newImpl.addInstruction(invoke);
        newImpl.addInstruction(moveResult);
        newImpl.addInstruction(returnObj);

        if (stats != null) {
            stats.backgroundWorkersNeutralized++;
        }
        return newImpl;
    }

    public static ClassDef neutralizeWorkerClass(ClassDef classDef, PrivacyOptions options, PrivacyStats stats, DexReachabilityIndex reachabilityIndex) {
        return sanitizeClass(classDef, options, stats, reachabilityIndex);
    }

    public static ClassDef neutralizeWorkerClass(ClassDef classDef, PrivacyOptions options, PrivacyStats stats) {
        return sanitizeClass(classDef, options, stats, null);
    }

    /**
     * Sanitizes all direct and virtual methods in a ClassDef.
     */
    public static ClassDef sanitizeClass(ClassDef classDef, PrivacyOptions options, PrivacyStats outStats) {
        return sanitizeClass(classDef, options, outStats, null);
    }

    /**
     * Sanitizes all direct and virtual methods in a ClassDef with optional reachability discovery.
     */
    public static ClassDef sanitizeClass(ClassDef classDef, PrivacyOptions options, PrivacyStats outStats, DexReachabilityIndex reachabilityIndex) {
        if (options == null) options = PrivacyOptions.all();
        List<Method> directMethods = new ArrayList<>();
        List<Method> virtualMethods = new ArrayList<>();
        boolean classModified = false;

        boolean isTelemetryWorker = options.neutralizeBackgroundWorkers && isTelemetryWorker(classDef);
        boolean hasWorker = isTelemetryWorker && hasDoWorkMethod(classDef);
        boolean isDeadWorker = hasWorker && (reachabilityIndex != null && !reachabilityIndex.isClassReferenced(classDef.getType()));

        boolean nullifyAi = options.nullifyAiAssistants;
        boolean isAiClass = nullifyAi && AiBytecodeRules.isAiControllerClass(classDef);
        boolean stubAiEligibility = nullifyAi && options.stubAiEligibilityManagers;
        boolean neutralizeTabIngress = nullifyAi && (options.neutralizeAiTabIngress || options.neutraliseAiTabIngress);

        boolean nullifyTrackerClinit = options.mode == SanitizationMode.AGGRESSIVE
                && (options.deepStructuralAnalysis || options.nullifyTrackerClinit)
                && isPureTrackerClass(classDef.getType());

        if (isDeadWorker) {
            if (outStats != null) {
                outStats.telemetryDeadCodeSkipped++;
                outStats.deadCodeSkipped++;
            }
        }

        for (Method m : classDef.getDirectMethods()) {
            if (nullifyTrackerClinit && "<clinit>".equals(m.getName())) {
                MutableMethodImplementation newImpl = new MutableMethodImplementation(0);
                newImpl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
                Method rewritten = new ImmutableMethod(
                        m.getDefiningClass(),
                        m.getName(),
                        m.getParameters(),
                        m.getReturnType(),
                        m.getAccessFlags(),
                        m.getAnnotations(),
                        m.getHiddenApiRestrictions(),
                        newImpl
                );
                directMethods.add(rewritten);
                if (outStats != null) {
                    outStats.trackerClinitSanitized++;
                }
                classModified = true;
            } else if (hasWorker && isDoWorkMethod(m)) {
                if (isDeadWorker) {
                    directMethods.add(m);
                } else {
                    MutableMethodImplementation mutable = m.getImplementation() != null
                            ? new MutableMethodImplementation(m.getImplementation())
                            : null;
                    MutableMethodImplementation newImpl = neutralizeWorkerClass(classDef, mutable, outStats);
                    int flags = m.getAccessFlags() & ~(AccessFlags.ABSTRACT.getValue() | AccessFlags.NATIVE.getValue());
                    Method rewritten = new ImmutableMethod(
                            m.getDefiningClass(),
                            m.getName(),
                            m.getParameters(),
                            m.getReturnType(),
                            flags,
                            m.getAnnotations(),
                            m.getHiddenApiRestrictions(),
                            newImpl
                    );
                    directMethods.add(rewritten);
                    classModified = true;
                }
            } else if (stubAiEligibility && AiBytecodeRules.isEligibilityMethod(m, isAiClass)) {
                Method stubbed = AiBytecodeRules.nullifyEligibilityMethod(m, reachabilityIndex, outStats, isAiClass);
                if (stubbed != m) {
                    classModified = true;
                    directMethods.add(stubbed);
                } else {
                    directMethods.add(m);
                }
            } else if (neutralizeTabIngress && AiBytecodeRules.isTabIngressMethod(m, classDef.getType())) {
                Method stubbed = AiBytecodeRules.nullifyTabIngressMethod(m, reachabilityIndex, outStats);
                if (stubbed != m) {
                    classModified = true;
                    directMethods.add(stubbed);
                } else {
                    directMethods.add(m);
                }
            } else {
                MethodTransformationResult result = sanitizeMethod(m, options);
                if (result.modified) {
                    classModified = true;
                    if (outStats != null) outStats.merge(result.stats);
                    directMethods.add(result.method);
                } else {
                    directMethods.add(m);
                }
            }
        }

        for (Method m : classDef.getVirtualMethods()) {
            if (hasWorker && isDoWorkMethod(m)) {
                if (isDeadWorker) {
                    virtualMethods.add(m);
                } else {
                    MutableMethodImplementation mutable = m.getImplementation() != null
                            ? new MutableMethodImplementation(m.getImplementation())
                            : null;
                    MutableMethodImplementation newImpl = neutralizeWorkerClass(classDef, mutable, outStats);
                    int flags = m.getAccessFlags() & ~(AccessFlags.ABSTRACT.getValue() | AccessFlags.NATIVE.getValue());
                    Method rewritten = new ImmutableMethod(
                            m.getDefiningClass(),
                            m.getName(),
                            m.getParameters(),
                            m.getReturnType(),
                            flags,
                            m.getAnnotations(),
                            m.getHiddenApiRestrictions(),
                            newImpl
                    );
                    virtualMethods.add(rewritten);
                    classModified = true;
                }
            } else if (stubAiEligibility && AiBytecodeRules.isEligibilityMethod(m, isAiClass)) {
                Method stubbed = AiBytecodeRules.nullifyEligibilityMethod(m, reachabilityIndex, outStats, isAiClass);
                if (stubbed != m) {
                    classModified = true;
                    virtualMethods.add(stubbed);
                } else {
                    virtualMethods.add(m);
                }
            } else if (neutralizeTabIngress && AiBytecodeRules.isTabIngressMethod(m, classDef.getType())) {
                Method stubbed = AiBytecodeRules.nullifyTabIngressMethod(m, reachabilityIndex, outStats);
                if (stubbed != m) {
                    classModified = true;
                    virtualMethods.add(stubbed);
                } else {
                    virtualMethods.add(m);
                }
            } else {
                MethodTransformationResult result = sanitizeMethod(m, options);
                if (result.modified) {
                    classModified = true;
                    if (outStats != null) outStats.merge(result.stats);
                    virtualMethods.add(result.method);
                } else {
                    virtualMethods.add(m);
                }
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

    /**
     * Sanitizes an entire DEX file in-place or to a new destination.
     */
    public static PrivacyStats sanitizeDex(File dexIn, File dexOut, PrivacyOptions options) throws Exception {
        return sanitizeDex(dexIn, dexOut, options, null);
    }

    /**
     * Sanitizes an entire DEX file with optional reachability index.
     */
    public static PrivacyStats sanitizeDex(File dexIn, File dexOut, PrivacyOptions options, DexReachabilityIndex reachabilityIndex) throws Exception {
        DexRepairEngine.repairHeader(dexIn);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexIn, null);
        DexPool pool = new DexPool(dex.getOpcodes());
        PrivacyStats stats = new PrivacyStats();

        for (ClassDef classDef : dex.getClasses()) {
            ClassDef sanitized = sanitizeClass(classDef, options, stats, reachabilityIndex);
            pool.internClass(DexDebugStripper.strip(sanitized));
        }

        if (stats.totalMutations() > 0) {
            MemoryDataStore store = new MemoryDataStore();
            pool.writeTo(store);
            byte[] data = Arrays.copyOf(store.getData(), store.getSize());
            File parent = dexOut.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStream os = new FileOutputStream(dexOut)) {
                os.write(data);
            }
        }

        return stats;
    }

    /**
     * Orchestrates multi-DEX streaming analysis and sanitization with granular audit reporting.
     * Pass 1: Streaming cross-DEX reachability indexing to detect active invocation paths.
     * Pass 2: Type-safe bytecode mutation, dead code elimination filtering, and dex assembly.
     * Generates a populated PrivacyAuditReport with module-level metrics.
     */
    public static PrivacyAuditReport sanitizeApkWithAudit(File apkIn, File apkOut, PrivacyOptions options, APKLogger logger) throws Exception {
        return sanitizeApkWithAudit(apkIn, apkOut, options, null, logger);
    }

    /**
     * Orchestrates multi-DEX streaming analysis and sanitization with granular audit reporting
     * using an optional pre-computed reachability index. If reachabilityIndex is null, builds it
     * on Pass 1 across all classes*.dex entries.
     */
    public static PrivacyAuditReport sanitizeApkWithAudit(File apkIn, File apkOut, PrivacyOptions options, DexReachabilityIndex reachabilityIndex, APKLogger logger) throws Exception {
        if (options == null) options = PrivacyOptions.all();

        try (ApkModule module = ApkModule.loadApkFile(apkIn)) {
            module.setLoadDefaultFramework(false);

            // Pass 1 (Reachability Scan): Stream through classes*.dex in ApkModule and build DexReachabilityIndex if needed
            DexReachabilityIndex reachability = reachabilityIndex;
            if (reachability == null) {
                reachability = new DexReachabilityIndex();
                for (InputSource source : module.getInputSources()) {
                    String name = source.getName();
                    if (!name.matches("classes\\d*\\.dex")) continue;

                    byte[] dexBytes = readAll(source.openStream());
                    DexBackedDexFile dex = new DexBackedDexFile(opcodesForDex(dexBytes), dexBytes);
                    reachability.indexDexFile(dex);
                }
            }

            // Pass 2 (Mutation & Dead Code Filtering): Stream through classes*.dex with reachability index
            PrivacyStats totalStats = new PrivacyStats();

            for (InputSource source : module.getInputSources()) {
                String name = source.getName();
                if (!name.matches("classes\\d*\\.dex")) continue;

                byte[] dexBytes = readAll(source.openStream());
                DexBackedDexFile dex = new DexBackedDexFile(opcodesForDex(dexBytes), dexBytes);
                List<ClassDef> transformedClasses = new ArrayList<>();
                PrivacyStats dexStats = new PrivacyStats();
                boolean anyClassChanged = false;

                for (ClassDef classDef : dex.getClasses()) {
                    ClassDef sanitized = sanitizeClass(classDef, options, dexStats, reachability);
                    if (sanitized != classDef) {
                        anyClassChanged = true;
                    }
                    transformedClasses.add(sanitized);
                }

                totalStats.merge(dexStats);

                if (anyClassChanged) {
                    DexPool pool = new DexPool(dex.getOpcodes());
                    for (ClassDef c : transformedClasses) {
                        pool.internClass(DexDebugStripper.strip(c));
                    }
                    MemoryDataStore store = new MemoryDataStore();
                    pool.writeTo(store);
                    module.add(new ByteInputSource(Arrays.copyOf(store.getData(), store.getSize()), name));
                    if (logger != null) {
                        logger.logMessage("Sanitized " + name + ": " + dexStats.totalMutations() + " privacy mutations");
                    }
                }
                transformedClasses.clear();
            }

            // Module A (Telemetry & Hardware Fingerprinting)
            int telemetryMutations = totalStats.telemetryTotalMutations();
            ModuleAuditResult.Status telemetryStatus;
            String telemetryMsg;
            int telemetryDeadCode = totalStats.telemetryDeadCodeSkipped;
            if (telemetryMutations > 0) {
                telemetryStatus = ModuleAuditResult.Status.SUCCESS;
                telemetryMsg = "Applied " + telemetryMutations + " telemetry mutations";
            } else if (telemetryDeadCode > 0) {
                telemetryStatus = ModuleAuditResult.Status.DEAD_CODE_SKIPPED;
                telemetryMsg = "All telemetry candidates were uninvoked dead code; skipped to avoid placebo patching";
            } else {
                telemetryStatus = ModuleAuditResult.Status.NOT_DETECTED;
                telemetryMsg = "No active telemetry targets detected";
            }
            ModuleAuditResult telemetryResult = new ModuleAuditResult(
                    "Module A: Telemetry",
                    telemetryStatus,
                    telemetryMutations,
                    telemetryDeadCode,
                    telemetryMsg
            );

            // Module B (AI & Assistant Nullification)
            int aiMutations = totalStats.aiTotalMutations();
            ModuleAuditResult.Status aiStatus;
            String aiMsg;
            int aiDeadCode = totalStats.aiDeadCodeSkipped;
            if (aiMutations > 0) {
                aiStatus = ModuleAuditResult.Status.SUCCESS;
                aiMsg = "Applied " + aiMutations + " AI assistant mutations";
            } else if (aiDeadCode > 0) {
                aiStatus = ModuleAuditResult.Status.DEAD_CODE_SKIPPED;
                aiMsg = "All AI candidates were uninvoked dead code; skipped to avoid placebo patching";
            } else {
                aiStatus = ModuleAuditResult.Status.NOT_DETECTED;
                aiMsg = "No AI assistant targets detected";
            }
            ModuleAuditResult aiResult = new ModuleAuditResult(
                    "Module B: AI & Assistants",
                    aiStatus,
                    aiMutations,
                    aiDeadCode,
                    aiMsg
            );

            // Compute overall status: If total mutations > 0, SUCCESS. If total mutations == 0, WARNING_ZERO_MUTATIONS.
            ModuleAuditResult.Status overallStatus;
            if (totalStats.totalMutations() > 0) {
                overallStatus = ModuleAuditResult.Status.SUCCESS;
            } else {
                overallStatus = ModuleAuditResult.Status.WARNING_ZERO_MUTATIONS;
            }

            // If apkOut != null and mutations applied (or always if caller requested apkOut copy), write APK
            if (apkOut != null) {
                if (totalStats.totalMutations() > 0 || !apkOut.equals(apkIn)) {
                    File parent = apkOut.getParentFile();
                    if (parent != null) parent.mkdirs();
                    module.writeApk(apkOut);
                }
            }

            PrivacyAuditReport report = new PrivacyAuditReport();
            report.sanitizationStats = totalStats;
            report.telemetryModuleResult = telemetryResult;
            report.aiModuleResult = aiResult;
            report.overallSanitizationStatus = overallStatus;

            return report;
        }
    }

    /**
     * Sanitizes all classes*.dex entries inside an APK, writing to a new output APK.
     * Automatically builds DexReachabilityIndex on Pass 1.
     */
    public static PrivacyStats sanitizeApk(File apkIn, File apkOut, PrivacyOptions options, APKLogger logger) throws Exception {
        return sanitizeApk(apkIn, apkOut, options, null, logger);
    }

    /**
     * Sanitizes all classes*.dex entries inside an APK with optional reachability index.
     * Automatically builds DexReachabilityIndex if null is passed.
     */
    public static PrivacyStats sanitizeApk(File apkIn, File apkOut, PrivacyOptions options, DexReachabilityIndex reachabilityIndex, APKLogger logger) throws Exception {
        return sanitizeApkWithAudit(apkIn, apkOut, options, reachabilityIndex, logger).getSanitizationStats();
    }

    // --- Helper Utilities ---

    public static BuilderInstruction createConstInt(int reg, int value) {
        if (reg <= 15 && value >= -8 && value <= 7) {
            return new BuilderInstruction11n(Opcode.CONST_4, reg, value);
        } else if (reg <= 255 && value >= -32768 && value <= 32767) {
            return new BuilderInstruction21s(Opcode.CONST_16, reg, value);
        } else {
            return new BuilderInstruction31i(Opcode.CONST, reg, value);
        }
    }

    public static BuilderInstruction createConstWide(int reg, long value) {
        if (reg <= 255 && value >= -32768 && value <= 32767) {
            return new BuilderInstruction21s(Opcode.CONST_WIDE_16, reg, (int) value);
        } else {
            return new BuilderInstruction51l(Opcode.CONST_WIDE, reg, value);
        }
    }

    public static boolean isInvoke(Opcode op) {
        return isInvokeStatic(op) || isInvokeVirtualOrInterface(op)
                || op == Opcode.INVOKE_DIRECT || op == Opcode.INVOKE_DIRECT_RANGE
                || op == Opcode.INVOKE_SUPER || op == Opcode.INVOKE_SUPER_RANGE;
    }

    private static boolean isInvokeStatic(Opcode op) {
        return op == Opcode.INVOKE_STATIC || op == Opcode.INVOKE_STATIC_RANGE;
    }

    private static boolean isInvokeVirtualOrInterface(Opcode op) {
        return op == Opcode.INVOKE_VIRTUAL || op == Opcode.INVOKE_VIRTUAL_RANGE
                || op == Opcode.INVOKE_INTERFACE || op == Opcode.INVOKE_INTERFACE_RANGE;
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

    private static String resolveKeyString(List<BuilderInstruction> instructions, int invokeIdx, int keyReg, Map<Integer, String> stringRegisters) {
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

    /**
     * Determines the appropriate Opcodes instance for a DEX file by inspecting its header version.
     * Preserves modern DEX versions (037, 038, 039, 040, 041) to ensure default/private interface
     * methods and modern opcodes do not trigger IncompatibleClassChangeError or ART verification errors.
     */
    public static Opcodes opcodesForDex(byte[] dexBytes) {
        try {
            if (dexBytes != null && dexBytes.length >= 8) {
                int version = HeaderItem.getVersion(dexBytes, 0);
                if (version >= 35) {
                    return Opcodes.forDexVersion(version);
                }
            }
        } catch (Exception ignored) {
        }
        return Opcodes.getDefault();
    }
}
