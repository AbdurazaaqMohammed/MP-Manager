package io.github.abdurazaaqmohammed.features.dumper;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Universal Android partition discovery and intelligent metadata resolver.
 * Handles Qualcomm, MediaTek, Tensor, Exynos, and all A/B and non-A/B layouts.
 */
public final class PartitionResolver {

    private PartitionResolver() {
    }

    /**
     * Resolves the current boot slot ("a", "b", or "" if non-A/B).
     */
    public static String getActiveSlot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "ro.boot.slot_suffix"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    String s = line.trim().toLowerCase(Locale.ROOT);
                    if (s.startsWith("_")) s = s.substring(1);
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "ro.boot.slot"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    String s = line.trim().toLowerCase(Locale.ROOT);
                    if (s.startsWith("_")) s = s.substring(1);
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Compares block device headers between Slot A and Slot B partitions to detect differences.
     * Returns a list of base partition names that differ (e.g. ["bluetooth", "dsp", "modem"]).
     */
    public static List<String> findSlotDifferences() {
        List<String> diffs = new ArrayList<>();
        String cmd = "sh -c '"
                + "for dir in /dev/block/bootdevice/by-name /dev/block/by-name /dev/block/mapper; do "
                + "  [ -d \"$dir\" ] || continue; "
                + "  for a in \"$dir\"/*_a; do "
                + "    [ -e \"$a\" ] || continue; "
                + "    b=\"${a%_a}_b\"; "
                + "    [ -e \"$b\" ] || continue; "
                + "    pname=$(basename \"$a\" _a); "
                + "    if ! cmp -n 65536 \"$a\" \"$b\" >/dev/null 2>&1; then "
                + "      echo \"$pname\"; "
                + "    fi; "
                + "  done; "
                + "done'";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            java.util.Set<String> seen = new java.util.HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !seen.contains(line)) {
                        seen.add(line);
                        diffs.add(line);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return diffs;
    }

    /**
     * Queries all partitions available on the device using elevated root commands.
     */
    public static List<PartitionItem> queryPartitions(Context context) throws Exception {
        String activeSlot = getActiveSlot();
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");

        String cmd = "sh -c '"
                + "slot=$(getprop ro.boot.slot_suffix 2>/dev/null); "
                + "[ -z \"$slot\" ] && slot=$(getprop ro.boot.slot 2>/dev/null); "
                + "echo \"ACTIVE_SLOT|$slot\"; "
                + "for dir in /dev/block/bootdevice/by-name /dev/block/by-name /dev/block/platform/*/by-name /dev/block/platform/*/*/by-name; do "
                + "  if [ -d \"$dir\" ]; then "
                + "    for f in \"$dir\"/*; do "
                + "      [ -e \"$f\" ] || continue; "
                + "      b=$(basename \"$f\"); "
                + "      case \"$b\" in "
                + "        com.android.*|by-uuid|userdata-verity|*-verity) continue ;; "
                + "      esac; "
                + "      target=$(readlink -f \"$f\" 2>/dev/null || echo \"$f\"); "
                + "      tbase=$(basename \"$target\"); "
                + "      sec=0; "
                + "      if [ -f \"/sys/class/block/$tbase/size\" ]; then "
                + "        sec=$(cat \"/sys/class/block/$tbase/size\" 2>/dev/null || echo 0); "
                + "      fi; "
                + "      echo \"BYNAME|$b|$f|$target|$sec\"; "
                + "    done; "
                + "  fi; "
                + "done; "
                + "for u in /sys/class/block/*/uevent; do "
                + "  if grep -q \"PARTNAME=\" \"$u\" 2>/dev/null; then "
                + "    devname=$(grep \"^DEVNAME=\" \"$u\" | cut -d= -f2); "
                + "    partname=$(grep \"^PARTNAME=\" \"$u\" | cut -d= -f2); "
                + "    sec=0; "
                + "    if [ -f \"/sys/class/block/$devname/size\" ]; then "
                + "      sec=$(cat \"/sys/class/block/$devname/size\" 2>/dev/null || echo 0); "
                + "    fi; "
                + "    echo \"SYSFS|$partname|/dev/block/$devname|/dev/block/$devname|$sec\"; "
                + "  fi; "
                + "done; "
                + "if [ -d /dev/block/mapper ]; then "
                + "  for f in /dev/block/mapper/*; do "
                + "    [ -e \"$f\" ] || continue; "
                + "    b=$(basename \"$f\"); "
                + "    case \"$b\" in "
                + "      com.android.*|by-uuid|userdata-verity|*-verity) continue ;; "
                + "    esac; "
                + "    target=$(readlink -f \"$f\" 2>/dev/null || echo \"$f\"); "
                + "    tbase=$(basename \"$target\"); "
                + "    sec=0; "
                + "    if [ -f \"/sys/class/block/$tbase/size\" ]; then "
                + "      sec=$(cat \"/sys/class/block/$tbase/size\" 2>/dev/null || echo 0); "
                + "    fi; "
                + "    echo \"MAPPER|$b|$f|$target|$sec\"; "
                + "  done; "
                + "fi'";

        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        Map<String, PartitionItem> itemMap = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains("|")) continue;
                String[] parts = line.split("\\|");
                if (parts[0].equals("ACTIVE_SLOT")) {
                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        String s = parts[1].trim().toLowerCase(Locale.ROOT);
                        if (s.startsWith("_")) s = s.substring(1);
                        activeSlot = s;
                    }
                    continue;
                }
                if (parts.length < 5) continue;

                String source = parts[0];
                String rawName = parts[1];
                String blockPath = parts[2];
                String targetDev = parts[3];
                long sectors = 0;
                try {
                    sectors = Long.parseLong(parts[4]);
                } catch (Exception ignored) {
                }
                long sizeBytes = sectors * 512L;

                // Prefer MAPPER & BYNAME over SYSFS raw block devices
                if (itemMap.containsKey(rawName) && "SYSFS".equals(source)) {
                    continue;
                }

                PartitionItem.Slot slot = PartitionItem.Slot.SHARED;
                String lowerRaw = rawName.toLowerCase(Locale.ROOT);
                if (lowerRaw.endsWith("_a")) slot = PartitionItem.Slot.A;
                else if (lowerRaw.endsWith("_b")) slot = PartitionItem.Slot.B;

                boolean isActiveSlot = false;
                if (!activeSlot.isEmpty()) {
                    if ((slot == PartitionItem.Slot.A && "a".equals(activeSlot))
                            || (slot == PartitionItem.Slot.B && "b".equals(activeSlot))) {
                        isActiveSlot = true;
                    }
                }

                boolean isDynamic = "MAPPER".equals(source) || blockPath.contains("/mapper");

                PartitionMeta meta = resolveMetadata(rawName, slot, isAr);

                PartitionItem item = new PartitionItem(
                        rawName,
                        meta.friendlyName,
                        meta.description,
                        blockPath,
                        targetDev,
                        sizeBytes,
                        slot,
                        isActiveSlot,
                        isDynamic,
                        meta.outputFileName,
                        meta.group
                );

                itemMap.put(rawName, item);
            }
        }

        List<PartitionItem> result = new ArrayList<>(itemMap.values());

        // Sort: Common first, then Dynamic, then Firmware, then Other; and Active Slot first
        Collections.sort(result, (a, b) -> {
            int scoreA = getPriorityScore(a);
            int scoreB = getPriorityScore(b);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            return a.rawName.compareToIgnoreCase(b.rawName);
        });

        return result;
    }

    private static int getPriorityScore(PartitionItem item) {
        int score = 0;
        if (item.isActiveSlot) score += 50;
        if (item.group == PartitionItem.Group.COMMON) score += 40;
        else if (item.group == PartitionItem.Group.DYNAMIC) score += 30;
        else if (item.group == PartitionItem.Group.FIRMWARE) score += 20;
        else if (item.group == PartitionItem.Group.BOOTLOADER) score += 10;
        return score;
    }

    private record PartitionMeta(String friendlyName, String description,
                                 String outputFileName, PartitionItem.Group group) {
    }

    private static PartitionMeta resolveMetadata(String rawName, PartitionItem.Slot slot, boolean isAr) {
        String lower = rawName.toLowerCase(Locale.ROOT);
        String base = lower;
        if (base.endsWith("_a") || base.endsWith("_b")) {
            base = base.substring(0, base.length() - 2);
        }

        String cleanOut = base + (slot == PartitionItem.Slot.B ? "_b.img" : ".img");

        switch (base) {
            case "boot":
                return new PartitionMeta(
                        isAr ? "البوت / الكيرنل (Boot)" : "Boot (Kernel & Ramdisk)",
                        isAr ? "نواة نظام التشغيل ورمديسك الإقلاع" : "Android Kernel & Ramdisk",
                        cleanOut,
                        PartitionItem.Group.COMMON
                );
            case "recovery":
                return new PartitionMeta(
                        isAr ? "الريكفري (Recovery)" : "Recovery (TWRP / OrangeFox)",
                        isAr ? "بيئة الاسترداد وتفليش الرومات" : "Recovery Environment & ROM Flasher",
                        cleanOut,
                        PartitionItem.Group.COMMON
                );
            case "init_boot":
                return new PartitionMeta(
                        isAr ? "البوت الأولي (Init Boot)" : "Init Boot (Android 13+ Ramdisk)",
                        isAr ? "رمديسك الإقلاع الأولي وحقن الروت (Magisk / KSU)" : "Generic Ramdisk & Root Injection",
                        cleanOut,
                        PartitionItem.Group.COMMON
                );
            case "vendor_boot":
                return new PartitionMeta(
                        isAr ? "بوت الفيندور (Vendor Boot)" : "Vendor Boot (OEM Drivers)",
                        isAr ? "رمديسك تعريفات الشركة المصنعة للهاتف" : "OEM Hardware Drivers Ramdisk",
                        cleanOut,
                        PartitionItem.Group.COMMON
                );
            case "vbmeta":
                return new PartitionMeta(
                        isAr ? "حماية النظام (VBMeta)" : "VBMeta (AVB Signature)",
                        isAr ? "توقيع التحقق من إقلاع أندرويد (AVB / dm-verity)" : "Android Verified Boot Metadata",
                        cleanOut,
                        PartitionItem.Group.COMMON
                );
            case "vbmeta_system":
                return new PartitionMeta(
                        isAr ? "حماية قسم النظام (VBMeta System)" : "VBMeta System",
                        isAr ? "التحقق من سلامة قسم النظام (Verity)" : "AVB Verity for System Partition",
                        cleanOut,
                        PartitionItem.Group.COMMON
                );
            case "system":
                return new PartitionMeta(
                        isAr ? "النظام الأساسي (System)" : "System (Android OS Core)",
                        isAr ? "نظام أندرويد الأساسي وتطبيقات النظام" : "Core Android OS Filesystem & Apps",
                        cleanOut,
                        PartitionItem.Group.DYNAMIC
                );
            case "vendor":
                return new PartitionMeta(
                        isAr ? "الفيندور (Vendor)" : "Vendor (Hardware HAL & Drivers)",
                        isAr ? "تعريفات ومكتبات الهاردوير والشرائح" : "Device Hardware Abstraction & Proprietary Blobs",
                        cleanOut,
                        PartitionItem.Group.DYNAMIC
                );
            case "product":
                return new PartitionMeta(
                        isAr ? "البرودكت (Product)" : "Product (OEM Apps & Overlays)",
                        isAr ? "تطبيقات وتخصيصات الشركة المصنعة" : "OEM Specific Features, Overlays & Apps",
                        cleanOut,
                        PartitionItem.Group.DYNAMIC
                );
            case "system_ext":
                return new PartitionMeta(
                        isAr ? "امتداد النظام (System Ext)" : "System Ext (Framework Addons)",
                        isAr ? "امتدادات فريم وورك نظام أندرويد" : "Android Framework Extensions",
                        cleanOut,
                        PartitionItem.Group.DYNAMIC
                );
            case "odm":
                return new PartitionMeta(
                        isAr ? "تخصيصات المصنع (ODM)" : "ODM (Original Design Manufacturer)",
                        isAr ? "تخصيصات مصنع العتاد الأصلي" : "Original Design Manufacturer Customizations",
                        cleanOut,
                        PartitionItem.Group.DYNAMIC
                );
            case "mi_ext":
                return new PartitionMeta(
                        isAr ? "امتدادات شاومي (Mi Ext)" : "Mi Ext (Xiaomi / HyperOS)",
                        isAr ? "تخصيصات واجهة شاومي وهايبر أو إس" : "HyperOS / MIUI Vendor Framework Addons",
                        cleanOut,
                        PartitionItem.Group.DYNAMIC
                );
            case "super":
                return new PartitionMeta(
                        isAr ? "قسم السوبر (Super Container)" : "Super (Dynamic Partitions Container)",
                        isAr ? "حاوية الأقسام الديناميكية (system, vendor, product...)" : "Host Container for Dynamic Partitions",
                        cleanOut,
                        PartitionItem.Group.COMMON
                );
            case "dtbo":
                return new PartitionMeta(
                        isAr ? "شجرة العتاد (DTBO)" : "DTBO (Device Tree Blob Overlay)",
                        isAr ? "مخطط عتاد وتوصيلات المعالج والهاتف" : "Hardware Device Tree Overlay",
                        cleanOut,
                        PartitionItem.Group.FIRMWARE
                );
            case "modem":
            case "radio":
                return new PartitionMeta(
                        isAr ? "المودم والشبكة (Modem / Baseband)" : "Modem (Cellular Firmware)",
                        isAr ? "فيرموير شبكة الاتصال والبيانات والـ GPS" : "Cellular Baseband & Radio Firmware",
                        cleanOut,
                        PartitionItem.Group.FIRMWARE
                );
            case "persist":
                return new PartitionMeta(
                        isAr ? "البيانات الدائمة (Persist)" : "Persist (Sensors & Calibration)",
                        isAr ? "معايرة الحساسات وماك الوايفاي والبلوتوث" : "Sensor Calibration & Device Unique Config",
                        cleanOut,
                        PartitionItem.Group.FIRMWARE
                );
            case "misc":
                return new PartitionMeta(
                        isAr ? "أوامر الإقلاع (Misc)" : "Misc (Bootloader & Recovery Flags)",
                        isAr ? "أوامر التوجيه للريكفري والبوتلودر" : "Recovery Directives & Bootloader Controls",
                        cleanOut,
                        PartitionItem.Group.BOOTLOADER
                );
            case "userdata":
            case "data":
                return new PartitionMeta(
                        isAr ? "بيانات المستخدم (Userdata)" : "Userdata (Internal Storage)",
                        isAr ? "وحدة التخزين الداخلية وتطبيقات المستخدم" : "Apps, Cache & Internal User Storage",
                        cleanOut,
                        PartitionItem.Group.OTHER
                );
            case "abl":
            case "xbl":
            case "uefi":
            case "tz":
                return new PartitionMeta(
                        base.toUpperCase(Locale.ROOT) + " (Bootloader)",
                        isAr ? "مكون من مكونات البوتلودر منخفض المستوى" : "Low-Level Bootloader Component",
                        cleanOut,
                        PartitionItem.Group.BOOTLOADER
                );
            default:
                return new PartitionMeta(
                        base,
                        isAr ? "قسم نظام فرعي (" + rawName + ")" : "Device storage partition (" + rawName + ")",
                        cleanOut,
                        PartitionItem.Group.OTHER
                );
        }
    }
}
