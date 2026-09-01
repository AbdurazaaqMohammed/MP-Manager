package io.github.abdurazaaqmohammed.utils.deepopt;

import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ResFile;
import com.reandroid.arsc.array.ResValueMapArray;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValueMap;
import com.reandroid.arsc.value.ValueType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resource sweep, Phase A: BFS over the used-ID set from the manifest/XML/code roots,
 * then {@code setNull} on every unused entry and deletion of {@code res/} files whose
 * entries are all unused. Resource IDs are never renumbered (code has them inlined as
 * constants), so Phase A keeps IDs stable. {@code ValueType.isReference()} covers
 * ATTRIBUTE/DYNAMIC_ATTRIBUTE theme/styleable references.
 */
public final class ResourceSweeper {

    private static final Pattern RESOURCE_REF = Pattern.compile(
            "^@[a-zA-Z0-9_]+:[a-zA-Z0-9_]+/[a-zA-Z0-9_$.]+|^@[a-zA-Z0-9_]+/[a-zA-Z0-9_$.]+");

    private ResourceSweeper() {
    }

    public static void sweep(ApkModule module, Set<Integer> resourceRoots, Set<String> nameRefKeys,
                             boolean allKeep, OptimizerReport report) {
        if (!module.hasTableBlock()) return;
        TableBlock table = module.getTableBlock();

        Map<Integer, ResourceEntry> byId = new LinkedHashMap<>();
        Map<String, ResourceEntry> byName = new HashMap<>();
        for (PackageBlock pkg : table.listPackages()) {
            for (java.util.Iterator<ResourceEntry> it = pkg.getResources(); it.hasNext(); ) {
                ResourceEntry entry = it.next();
                byId.put(entry.getResourceId(), entry);
                byName.put(entry.getType() + ":" + entry.getName(), entry);
            }
        }

        if (allKeep) {
            report.allResourcesKept = true;
            report.log("getIdentifier/getString usage detected - keeping all resources");
            return;
        }

        Set<Integer> used = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.addAll(resourceRoots);
        for (String key : nameRefKeys) {
            ResourceEntry entry = byName.get(key);
            if (entry != null) queue.add(entry.getResourceId());
        }

        while (!queue.isEmpty()) {
            int id = queue.poll();
            ResourceEntry entry = byId.get(id);
            if (entry == null || !used.add(id)) continue;
            for (Entry config : entry) {
                if (config == null || config.isNull()) continue;
                try {
                    if (config.isComplex()) {
                        ResValueMapArray maps = config.getResValueMapArray();
                        if (maps == null) continue;
                        for (int mi = 0; mi < maps.size(); mi++) {
                            ResValueMap item = maps.get(mi);
                            if (item.getValueType().isReference()) {
                                queue.add(item.getData());
                            } else if (item.getValueType() == ValueType.STRING) {
                                String key = resourceRefKey(item.getValueAsString());
                                if (key != null && byName.containsKey(key))
                                    queue.add(byName.get(key).getResourceId());
                            }
                        }
                    } else if (config.getValueType().isReference()) {
                        ResourceEntry ref = config.getValueAsReference();
                        if (ref != null) queue.add(ref.getResourceId());
                    } else {
                        String key = resourceRefKey(config.getValueAsString());
                        if (key != null && byName.containsKey(key))
                            queue.add(byName.get(key).getResourceId());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        int removedEntries = 0;
        for (Map.Entry<Integer, ResourceEntry> e : byId.entrySet()) {
            if (used.contains(e.getKey())) continue;
            ResourceEntry entry = e.getValue();
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

        try {
            table.refreshFull();
            for (PackageBlock pkg : table.listPackages()) pkg.removeUnusedSpecs();
            module.refreshTable();
        } catch (Exception ignored) {
        }
        report.log("Removed unused resources: " + removedEntries + " entries, " + removedFiles + " files");
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