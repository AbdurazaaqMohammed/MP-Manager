package io.github.abdurazaaqmohammed.features.dumper;

import android.content.Context;
import io.github.abdurazaaqmohammed.MPManager.R;

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
                + "dir=\"\"; "
                + "for d in /dev/block/bootdevice/by-name /dev/block/by-name /dev/block/mapper; do "
                + "  if [ -d \"$d\" ]; then dir=\"$d\"; break; fi; "
                + "done; "
                + "[ -n \"$dir\" ] || exit 0; "
                + "for a in \"$dir\"/*_a; do "
                + "  [ -e \"$a\" ] || continue; "
                + "  b=\"${a%_a}_b\"; "
                + "  [ -e \"$b\" ] || continue; "
                + "  pname=$(basename \"$a\" _a); "
                + "  if ! cmp -n 65536 \"$a\" \"$b\" >/dev/null 2>&1; then "
                + "    echo \"$pname\"; "
                + "  fi; "
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
        String activeSlot = "";

        String cmd = "sh -c '"
                + "slot=$(getprop ro.boot.slot_suffix 2>/dev/null); "
                + "[ -z \"$slot\" ] && slot=$(getprop ro.boot.slot 2>/dev/null); "
                + "echo \"ACTIVE_SLOT|$slot\"; "
                + "awk \"NR>2 {print \\\"SIZE|\\\" \\$4 \\\"|\\\" (\\$3*2)}\" /proc/partitions 2>/dev/null; "
                + "for dir in /dev/block/bootdevice/by-name /dev/block/by-name /dev/block/platform/*/by-name /dev/block/platform/*/*/by-name /dev/block/mapper; do "
                + "  [ -d \"$dir\" ] || continue; "
                + "  ls -l \"$dir\" 2>/dev/null | awk -v d=\"$dir\" \"/->/ {print \\\"BYNAME|\\\" \\$(NF-2) \\\"|\\\" d \\\"/\\\" \\$(NF-2) \\\"|\\\" \\$NF}\"; "
                + "done; "
                + "grep -H \"PARTNAME=\" /sys/class/block/*/uevent 2>/dev/null | awk -F/ \"{dev=\\$5; sub(/.*PARTNAME=/, \\\"\\\", \\$0); print \\\"SYSFS|\\\" \\$0 \\\"|/dev/block/\\\" dev \\\"|/dev/block/\\\" dev}\""
                + "'";

        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        Map<String, PartitionItem> itemMap = new HashMap<>();
        Map<String, Long> devSizes = new HashMap<>();

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
                if (parts[0].equals("SIZE") && parts.length >= 3) {
                    try {
                        devSizes.put(parts[1], Long.parseLong(parts[2]));
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (parts.length < 4) continue;

                String source = parts[0];
                String rawName = parts[1];
                String blockPath = parts[2];
                String targetDev = parts[3];

                String devBase = targetDev.substring(targetDev.lastIndexOf('/') + 1);
                Long sec = devSizes.get(devBase);
                if (sec == null) sec = devSizes.get(rawName);
                long sectors = sec != null ? sec : 0;
                long sizeBytes = sectors * 512L;

                // Skip zero-sized partitions, APEX loop packages, and dummy placeholders
                if (sectors <= 0 || sizeBytes <= 0 || isDummyOrEmptyPartition(rawName)) {
                    continue;
                }

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

                PartitionMeta meta = resolveMetadata(context, rawName, slot);

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

    private static PartitionMeta resolveMetadata(Context context, String rawName, PartitionItem.Slot slot) {
        String lower = rawName.toLowerCase(Locale.ROOT);
        String base = lower;
        if (base.endsWith("_a") || base.endsWith("_b")) {
            base = base.substring(0, base.length() - 2);
        }

        int description = switch (base) {
            case "boot" -> R.string.partition_desc_boot;
            case "recovery" -> R.string.partition_desc_recovery;
            case "init_boot" -> R.string.partition_desc_init_boot;
            case "vendor_boot" -> R.string.partition_desc_vendor_boot;
            case "vbmeta", "vbmeta_system" -> R.string.partition_desc_vbmeta;
            case "system" -> R.string.partition_desc_system;
            case "vendor" -> R.string.partition_desc_vendor;
            case "product" -> R.string.partition_desc_product;
            case "system_ext" -> R.string.partition_desc_system_ext;
            case "odm" -> R.string.partition_desc_odm;
            case "mi_ext" -> R.string.partition_desc_mi_ext;
            case "super" -> R.string.partition_desc_super;
            case "dtbo" -> R.string.partition_desc_dtbo;
            case "modem", "radio" -> R.string.partition_desc_modem;
            case "persist" -> R.string.partition_desc_persist;
            case "misc" -> R.string.partition_desc_misc;
            case "userdata", "data" -> R.string.partition_desc_userdata;
            case "abl", "xbl", "uefi", "tz" -> R.string.partition_desc_bootloader;
            default -> R.string.partition_desc_other;
        };
        PartitionItem.Group group = switch (base) {
            case "boot", "recovery", "init_boot", "vendor_boot", "vbmeta", "vbmeta_system", "super" -> PartitionItem.Group.COMMON;
            case "system", "vendor", "product", "system_ext", "odm", "mi_ext" -> PartitionItem.Group.DYNAMIC;
            case "dtbo", "modem", "radio", "persist" -> PartitionItem.Group.FIRMWARE;
            case "misc", "abl", "xbl", "uefi", "tz" -> PartitionItem.Group.BOOTLOADER;
            default -> PartitionItem.Group.OTHER;
        };
        // Partition identifiers are technical names, not localized display labels.
        return new PartitionMeta(base, context.getString(description), rawName + ".img", group);
    }

    public static boolean isDummyOrEmptyPartition(String rawName) {
        if (rawName == null || rawName.isEmpty()) return true;
        String lower = rawName.toLowerCase(Locale.ROOT);
        return lower.startsWith("com.android.")
                || lower.startsWith("com.google.android.")
                || lower.startsWith("align_to_")
                || lower.matches("^loop\\d+.*")
                || lower.equals("by-uuid")
                || lower.equals("userdata-verity")
                || lower.endsWith("-verity")
                || lower.matches("^bk\\d+.*")
                || lower.equals("cdt")
                || lower.equals("connsec")
                || lower.equals("dbg")
                || lower.equals("dpm")
                || lower.equals("ffu")
                || lower.equals("fsc")
                || lower.equals("keystore")
                || lower.equals("mbnconfig")
                || lower.equals("mdcompress")
                || lower.equals("mem")
                || lower.equals("mtdblk")
                || lower.startsWith("multiimgoem")
                || lower.equals("opconfig")
                || lower.equals("ssd")
                || lower.equals("switch")
                || lower.equals("tzsc")
                || lower.startsWith("dummy")
                || lower.startsWith("unused");
    }
}
