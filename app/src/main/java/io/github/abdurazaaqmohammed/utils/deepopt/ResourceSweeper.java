package io.github.abdurazaaqmohammed.utils.deepopt;

import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ResFile;
import com.reandroid.arsc.array.ResValueMapArray;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValueMap;
import com.reandroid.arsc.value.ValueType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resource sweep, Phase A: BFS over the used-ID set from the manifest/XML/code roots,
 * plus the name-match keep set when the app resolves resources by name at runtime, then
 * {@code setNull} on every unused entry and deletion of {@code res/} files whose entries
 * are all unused. Resource IDs are never renumbered (code has them inlined as constants),
 * so Phase A keeps IDs stable. {@code ValueType.isReference()} covers
 * ATTRIBUTE/DYNAMIC_ATTRIBUTE theme/styleable references.
 *
 * Structural table refreshes ({@code refreshFull/removeUnusedSpecs/refreshTable}) are NOT
 * swallowed: a half-refreshed resources.arsc used to be written out and crash at runtime.
 */
public final class ResourceSweeper {

    private static final Pattern RESOURCE_REF = Pattern.compile(
            "^@[a-zA-Z0-9_]+:[a-zA-Z0-9_]+/[a-zA-Z0-9_$.-]+|^@[a-zA-Z0-9_]+/[a-zA-Z0-9_$.-]+");
    private static final int MAX_VALUE_ERRORS = 10;

    private ResourceSweeper() {
    }

    /**
     * @param keepNames resource entry names that must be kept (dex string constants when
     *                  the app looks resources up by name); null/empty to skip
     * @param allKeep   hard-conservative mode: keep every entry untouched
     * @return number of entries nulled
     */
    public static int sweep(ApkModule module, ResourceTableIndex index, Set<Integer> resourceRoots,
                            Set<String> nameRefKeys, Set<String> keepNames, boolean allKeep,
                            OptimizerReport report) throws Exception {
        if (!module.hasTableBlock() || index.isEmpty()) return 0;

        if (allKeep) {
            report.allResourcesKept = true;
            report.log("Conservative triggers present - keeping all resources");
            return 0;
        }

        Set<Integer> used = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>(resourceRoots);
        for (String key : nameRefKeys) {
            ResourceEntry entry = index.byName(typeOf(key), nameOf(key));
            if (entry != null) queue.add(entry.getResourceId());
        }
        for (int id : index.idsForNames(keepNames)) {
            if (!used.contains(id)) queue.add(id);
        }

        int valueErrors = 0;
        while (!queue.isEmpty()) {
            int id = queue.poll();
            ResourceEntry entry = index.byId(id);
            if (entry == null || !used.add(id)) continue;
            for (Entry config : entry) {
                if (config == null || config.isNull()) continue;
                try {
                    if (config.isComplex()) {
                        // Style entries: the parent style lives in the entry header,
                        // not in the value maps - the original sweep never followed it,
                        // which cut Theme.AppCompat parent chains and crashed apps.
                        if (config.getResTableMapEntry() != null) {
                            int parentId = config.getResTableMapEntry().getParentId();
                            if (parentId != 0) queue.add(parentId);
                        }
                        ResValueMapArray maps = config.getResValueMapArray();
                        if (maps == null) continue;
                        for (int mi = 0; mi < maps.size(); mi++) {
                            ResValueMap item = maps.get(mi);
                            if (item.getValueType().isReference()) {
                                queue.add(item.getData());
                            } else if (item.getValueType() == ValueType.STRING) {
                                ResourceEntry ref = refByName(index, item.getValueAsString());
                                if (ref != null) queue.add(ref.getResourceId());
                            }
                        }
                    } else if (config.getValueType().isReference()) {
                        ResourceEntry ref = config.getValueAsReference();
                        if (ref != null) queue.add(ref.getResourceId());
                    } else if (config.getValueType() == ValueType.STRING) {
                        ResourceEntry ref = refByName(index, config.getValueAsString());
                        if (ref != null) queue.add(ref.getResourceId());
                    }
                } catch (Exception e) {
                    if (valueErrors++ < MAX_VALUE_ERRORS)
                        report.log("Value read failed for 0x" + Integer.toHexString(id) + ": " + e);
                }
            }
        }
        if (valueErrors > MAX_VALUE_ERRORS)
            report.log("Value read failures total: " + valueErrors);

        int removedEntries = 0;
        for (Integer id : index.byId.keySet()) {
            if (used.contains(id)) continue;
            ResourceEntry entry = index.byId.get(id);
            for (Entry config : entry) {
                if (config != null && !config.isNull()) config.setNull(true);
            }
            removedEntries++;
        }
        report.entriesRemoved = removedEntries;

        int removedFiles = 0;
        for (ResFile resFile : module.listResFiles()) {
            boolean hasEntries = false;
            boolean allUnused = true;
            for (Entry entry : resFile) {
                if (entry == null) continue;
                hasEntries = true;
                int id = entry.getResourceId();
                if (id != 0 && used.contains(id)) {
                    allUnused = false;
                    break;
                }
            }
            if (hasEntries && allUnused) {
                module.getZipEntryMap().remove(resFile.getInputSource());
                removedFiles++;
            }
        }
        report.filesRemoved = removedFiles;

        tableRefresh(index, report);
        report.log("Removed unused resources: " + removedEntries + " entries, " + removedFiles + " files");
        return removedEntries;
    }

    /** Never swallow: a broken refresh used to produce a resources.arsc that crashed apps. */
    private static void tableRefresh(ResourceTableIndex index, OptimizerReport report) throws Exception {
        try {
            index.table.refreshFull();
            for (PackageBlock pkg : index.table.listPackages())
                pkg.removeUnusedSpecs();
        } catch (Exception e) {
            throw new Exception("Resource table refresh failed; aborting deep optimization: " + e, e);
        }
    }

    private static ResourceEntry refByName(ResourceTableIndex index, String value) {
        String key = resourceRefKey(value);
        if (key == null) return null;
        return index.byName(typeOf(key), nameOf(key));
    }

    private static String typeOf(String key) {
        int colon = key.indexOf(':');
        int slash = key.indexOf('/');
        if (colon >= 0 && (slash < 0 || colon < slash)) return key.substring(0, colon);
        if (slash > 0) return key.substring(0, slash);
        return key;
    }

    private static String nameOf(String key) {
        int slash = key.indexOf('/');
        if (slash >= 0) return key.substring(slash + 1);
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    /** "@type/name" or "@android:type/name" -> "type:name" key, else null. */
    public static String resourceRefKey(String value) {
        if (value == null) return null;
        Matcher matcher = RESOURCE_REF.matcher(value);
        if (!matcher.find()) return null;
        String body = matcher.group().substring(1);
        int slash = body.indexOf('/');
        if (slash <= 0) return null;
        String type = body.contains(":")
                ? body.substring(body.indexOf(':') + 1, slash) : body.substring(0, slash);
        return type + ":" + body.substring(slash + 1);
    }
}
