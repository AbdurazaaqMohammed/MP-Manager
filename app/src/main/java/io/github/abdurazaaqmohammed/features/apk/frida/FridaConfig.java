package io.github.abdurazaaqmohammed.features.apk.frida;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * The Frida Hook Engine runtime configuration: the 21 module flags plus their
 * companion value objects, stored as the same JSON "parameters" object that
 * {@code libfrida-gadget.config.so} passes to the engine's init() (Path B) and
 * that the root injector hands to {@code rpc.exports.init()} (Path C).
 *
 * <p>Every flag is off by default, mirroring the engine's own source defaults;
 * a missing key always falls back to those defaults inside the engine, so the
 * stored object only ever needs to carry keys the UI has touched.</p>
 */
public final class FridaConfig {

    /** How a module's companion value object is edited in the UI. */
    public enum Editor { NONE, MAP_FIXED, MAP_OPEN, LIST, JSON, DEVICE_ID, REGISTER_NATIVES }

    /** One hook module: its engine flag, its value key, and how to edit it. */
    public static final class Module {
        public final String flag;
        public final int titleRes;
        public final int descRes;
        public final String valuesKey;
        public final Editor editor;

        Module(String flag, int titleRes, int descRes, String valuesKey, Editor editor) {
            this.flag = flag;
            this.titleRes = titleRes;
            this.descRes = descRes;
            this.valuesKey = valuesKey;
            this.editor = editor;
        }
    }

    /** Every engine module in UI order (README's Modules table). */
    public static final Module[] MODULES = {
            new Module("sslPinningBypassEnabled", R.string.frida_mod_ssl, R.string.frida_mod_ssl_desc, null, Editor.NONE),
            new Module("vpnProxyBypassEnabled", R.string.frida_mod_vpn, R.string.frida_mod_vpn_desc, null, Editor.NONE),
            new Module("pairipLicenseBypassEnabled", R.string.frida_mod_pairip, R.string.frida_mod_pairip_desc, null, Editor.NONE),
            new Module("flagSecureBypassEnabled", R.string.frida_mod_flag_secure, R.string.frida_mod_flag_secure_desc, null, Editor.NONE),
            new Module("installerSpoofEnabled", R.string.frida_mod_installer, R.string.frida_mod_installer_desc, null, Editor.NONE),
            new Module("adbDevSettingsBypassEnabled", R.string.frida_mod_adb, R.string.frida_mod_adb_desc, null, Editor.NONE),
            new Module("mockLocationBypassEnabled", R.string.frida_mod_mock_location, R.string.frida_mod_mock_location_desc, null, Editor.NONE),
            new Module("autoTimeSpoofEnabled", R.string.frida_mod_auto_time, R.string.frida_mod_auto_time_desc, null, Editor.NONE),
            new Module("geoRestrictionBypassEnabled", R.string.frida_mod_geo, R.string.frida_mod_geo_desc,
                    "geoSpoofValues", Editor.MAP_FIXED),
            new Module("localeSpoofEnabled", R.string.frida_mod_locale, R.string.frida_mod_locale_desc,
                    "localeSpoofValues", Editor.MAP_FIXED),
            new Module("suspiciousPackageSpoofEnabled", R.string.frida_mod_suspicious, R.string.frida_mod_suspicious_desc,
                    "suspiciousPackagesCustom", Editor.LIST),
            new Module("installedPackageSpoofEnabled", R.string.frida_mod_installed, R.string.frida_mod_installed_desc,
                    "installedPackageSpoofValues", Editor.JSON),
            new Module("deviceIdSpoofEnabled", R.string.frida_mod_device_id, R.string.frida_mod_device_id_desc,
                    "deviceIdSpoofValues", Editor.DEVICE_ID),
            new Module("buildPropSpoofEnabled", R.string.frida_mod_build_prop, R.string.frida_mod_build_prop_desc,
                    "buildPropSpoofValues", Editor.MAP_FIXED),
            new Module("systemPropertySpoofEnabled", R.string.frida_mod_sys_prop, R.string.frida_mod_sys_prop_desc,
                    "systemPropertySpoofValues", Editor.MAP_OPEN),
            new Module("hardwareFeatureSpoofEnabled", R.string.frida_mod_hw_feature, R.string.frida_mod_hw_feature_desc,
                    "spoofedHardwareFeatures", Editor.LIST),
            new Module("contentProviderSpoofEnabled", R.string.frida_mod_content_provider, R.string.frida_mod_content_provider_desc,
                    "contentProviderSpoofValues", Editor.JSON),
            new Module("javaHooksEnabled", R.string.frida_mod_java_hooks, R.string.frida_mod_java_hooks_desc,
                    "javaHooks", Editor.JSON),
            new Module("nativeHooksEnabled", R.string.frida_mod_native_hooks, R.string.frida_mod_native_hooks_desc,
                    "nativeHooks", Editor.JSON),
            new Module("nativePatchesEnabled", R.string.frida_mod_native_patches, R.string.frida_mod_native_patches_desc,
                    "nativePatches", Editor.JSON),
            new Module("registerNativesLogEnabled", R.string.frida_mod_register_natives, R.string.frida_mod_register_natives_desc,
                    null, Editor.REGISTER_NATIVES),
    };

    /** Field labels for the fixed-shape value maps (shown as input hints). */
    public static final String[] GEO_KEYS = {"operatorName", "plmn", "countryIso"};
    public static final String[] LOCALE_KEYS = {"country", "language", "timezoneId"};
    public static final String[] DEVICE_ID_KEYS = {
            "androidId", "imei", "meid", "iccid", "imsi",
            "phoneNumber", "wifiMac", "bluetoothMac", "advertisingId", "internalIp"};
    public static final String[] BUILD_PROP_KEYS = {
            "manufacturer", "model", "brand", "product", "device", "hardware", "board",
            "bootloader", "host", "id", "fingerprint", "tags", "type", "user", "display",
            "release", "securityPatch", "incremental", "codename"};
    public static final String[] REGISTER_NATIVES_KEYS = {"symbolName", "offset"};
    public static final String DEVICE_ID_RANDOM_KEY = "deviceIdSpoofUseRandom";
    public static final String REGISTER_NATIVES_FILTER_KEY = "registerNativesLogClassFilter";
    public static final String REGISTER_NATIVES_LOCATOR_KEY = "registerNativesLogLocator";
    public static final String DEBUG_LOG_KEY = "debugLog";

    private static final String PREF_KEY = "frida_gadget_config";

    /** Engine defaults from frida-hook-engine's config.json, used to prefill editors. */
    public static JSONObject defaultValues(String valuesKey) {
        try {
            JSONObject defaults = new JSONObject()
                    .put("geoSpoofValues", new JSONObject()
                            .put("operatorName", "AT&T").put("plmn", "310410").put("countryIso", "us"))
                    .put("localeSpoofValues", new JSONObject()
                            .put("country", "US").put("language", "en").put("timezoneId", "America/New_York"))
                    .put("suspiciousPackagesCustom", new JSONArray()
                            .put("bin.mt.plus").put("com.network.proxy").put("com.termux"))
                    .put("deviceIdSpoofValues", new JSONObject()
                            .put("androidId", "a1b2c3d4e5f6a7b8")
                            .put("imei", "356938035643809")
                            .put("meid", "A1000012B3C456")
                            .put("iccid", "89014103211118510720")
                            .put("imsi", "310260123456789")
                            .put("phoneNumber", "+15555215556")
                            .put("wifiMac", "3C:5A:B4:1E:2F:8D")
                            .put("bluetoothMac", "AC:37:43:2B:9C:14")
                            .put("advertisingId", "38400000-8cf0-11bd-b23e-10b96e40000d")
                            .put("internalIp", "192.168.1.42"))
                    .put("buildPropSpoofValues", new JSONObject()
                            .put("manufacturer", "Google").put("model", "Pixel 7 Pro")
                            .put("brand", "google").put("product", "cheetah").put("device", "cheetah")
                            .put("hardware", "cheetah").put("board", "cheetah").put("bootloader", "unknown")
                            .put("host", "android-build").put("id", "AP4A.241205.013")
                            .put("fingerprint", "google/cheetah/cheetah:15/AP4A.241205.013/12621605:user/release-keys")
                            .put("tags", "release-keys").put("type", "user").put("user", "android-build")
                            .put("display", "AP4A.241205.013").put("release", "15")
                            .put("securityPatch", "2024-12-05").put("incremental", "12621605")
                            .put("codename", "REL"))
                    .put("systemPropertySpoofValues", new JSONObject()
                            .put("ro.boot.verifiedbootstate", "green")
                            .put("ro.boot.vbmeta.device_state", "locked")
                            .put("ro.boot.veritymode", "enforcing")
                            .put("ro.boot.flash.locked", "1")
                            .put("ro.oem_unlock_supported", "0")
                            .put("ro.secureboot.lockstate", "locked")
                            .put("ro.kernel.qemu", "0").put("ro.boot.qemu", "0")
                            .put("ro.secure", "1").put("ro.debuggable", "0").put("ro.force.debuggable", "0")
                            .put("ro.adb.secure", "0").put("ro.allow.mock.location", "0")
                            .put("qemu.hw.mainkeys", "1"))
                    .put("spoofedHardwareFeatures", new JSONArray()
                            .put("android.hardware.telephony").put("android.hardware.camera")
                            .put("android.hardware.camera.any").put("android.hardware.camera.front")
                            .put("android.hardware.sensor.accelerometer").put("android.hardware.sensor.gyroscope")
                            .put("android.hardware.sensor.proximity").put("android.hardware.bluetooth")
                            .put("android.hardware.nfc").put("android.hardware.fingerprint")
                            .put("android.hardware.wifi"))
                    .put("contentProviderSpoofValues", new JSONObject()
                            .put("settings", new JSONObject()
                                    .put("development_settings_enabled", "0")
                                    .put("adb_enabled", "0")
                                    .put("mock_location", "0")
                                    .put("android_id", "a1b2c3d4e5f6a7b8"))
                            .put("com.google.android.gsf.gservices", new JSONObject()
                                    .put("android_id", "3f9d0a2b4c5e6f71")));
            return defaults.optJSONObject(valuesKey) != null
                    ? new JSONObject(defaults.getJSONObject(valuesKey).toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private final JSONObject params;

    private FridaConfig(JSONObject params) {
        this.params = params;
    }

    public static FridaConfig load(Context context) {
        JSONObject params = new JSONObject();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String saved = prefs.getString(PREF_KEY, null);
            if (saved != null) params = new JSONObject(saved);
        } catch (Exception ignored) {
        }
        if (!params.has(DEBUG_LOG_KEY)) {
            try {
                params.put(DEBUG_LOG_KEY, true);
            } catch (Exception ignored) {
            }
        }
        return new FridaConfig(params);
    }

    public void save(Context context) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(PREF_KEY, params.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public JSONObject params() {
        return params;
    }

    /** Restores the engine defaults: every module off, debug log on. */
    public void reset(Context context) {
        java.util.Iterator<String> keys = params.keys();
        java.util.List<String> names = new java.util.ArrayList<>();
        while (keys.hasNext()) names.add(keys.next());
        for (String key : names) {
            params.remove(key);
        }
        try {
            params.put(DEBUG_LOG_KEY, true);
        } catch (Exception ignored) {
        }
        save(context);
    }

    public boolean isEnabled(String flag) {
        return params.optBoolean(flag, false);
    }

    public void setEnabled(String flag, boolean enabled) {
        try {
            params.put(flag, enabled);
        } catch (Exception ignored) {
        }
    }

    public boolean getBoolean(String key, boolean def) {
        return params.optBoolean(key, def);
    }

    public void putBoolean(String key, boolean value) {
        try {
            params.put(key, value);
        } catch (Exception ignored) {
        }
    }

    public JSONObject getValues(String valuesKey) {
        return params.optJSONObject(valuesKey);
    }

    public void putValues(String valuesKey, JSONObject values) {
        try {
            params.put(valuesKey, values);
        } catch (Exception ignored) {
        }
    }

    public JSONArray getArray(String valuesKey) {
        return params.optJSONArray(valuesKey);
    }

    public void putArray(String valuesKey, JSONArray values) {
        try {
            params.put(valuesKey, values);
        } catch (Exception ignored) {
        }
    }

    /** Number of hook modules currently enabled, for the summary line. */
    public int enabledModuleCount() {
        int count = 0;
        for (Module module : MODULES) {
            if (isEnabled(module.flag)) count++;
        }
        return count;
    }

    /**
     * Full Gadget config file content (strict JSON, no comments — the exact
     * shape of the engine bundle's libfrida-gadget.config.so with our
     * parameters filled in). Written into the APK as libfrida-gadget.config.so.
     */
    public static String buildGadgetConfigJson(JSONObject params) {
        try {
            JSONObject interaction = new JSONObject()
                    .put("type", "script")
                    .put("path", "libfrida-gadget.script.so")
                    .put("on_change", "reload")
                    .put("parameters", params);
            return new JSONObject().put("interaction", interaction).toString(2);
        } catch (Exception e) {
            return "{\"interaction\":{\"type\":\"script\","
                    + "\"path\":\"libfrida-gadget.script.so\",\"on_change\":\"reload\","
                    + "\"parameters\":{}}}";
        }
    }

    /** Script snippet appended to the engine agent so Path C installs the hooks. */
    public static String buildInitCall(JSONObject params) {
        return "\nrpc.exports.init(null, " + params + ");\n";
    }
}
