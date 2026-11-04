package io.github.abdurazaaqmohammed.utils.deepopt;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.model.ResourceEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Package-aware index over the resource table. The previous maps were keyed by the raw
 * resource ID across all packages, so two packages sharing an ID byte swept each other's
 * entries. Here the app package (by manifest name) always wins ID collisions, and name
 * lookups can match any package.
 */
public final class ResourceTableIndex {

    public final TableBlock table;
    public final PackageBlock appPackage;
    public final Map<Integer, ResourceEntry> byId = new HashMap<>();
    private final Map<String, ResourceEntry> byName = new HashMap<>();
    private final Map<Integer, PackageBlock> packageByIdByte = new HashMap<>();

    private ResourceTableIndex(TableBlock table, PackageBlock appPackage) {
        this.table = table;
        this.appPackage = appPackage;
    }

    public static ResourceTableIndex build(TableBlock table, String manifestPackage) {
        PackageBlock app = null;
        if (manifestPackage != null) {
            for (PackageBlock pkg : table.listPackages()) {
                if (manifestPackage.equals(pkg.getName())) { app = pkg; break; }
            }
        }
        if (app == null) {
            for (PackageBlock pkg : table.listPackages()) {
                if (pkg.getId() == (byte) 0x7F) { app = pkg; break; }
            }
        }
        if (app == null && table.listPackages().iterator().hasNext())
            app = table.listPackages().iterator().next();
        ResourceTableIndex index = new ResourceTableIndex(table, app);
        // Non-app packages first so the app package wins ID collisions.
        for (PackageBlock pkg : table.listPackages()) {
            if (pkg == app) continue;
            index.collect(pkg);
        }
        if (app != null) index.collect(app);
        return index;
    }

    private void collect(PackageBlock pkg) {
        packageByIdByte.put(pkg.getId() & 0xFF, pkg);
        for (Iterator<ResourceEntry> it = pkg.getResources(); it.hasNext(); ) {
            ResourceEntry entry = it.next();
            byId.put(entry.getResourceId(), entry);
            byName.put(entry.getType() + ":" + entry.getName(), entry);
        }
    }

    /** Null for framework (0x01) references and for IDs absent from the table. */
    public ResourceEntry byId(int resId) {
        if ((resId >>> 24) == 0x01) return null;
        return byId.get(resId);
    }

    /** "type" + "name" in the app package first, then any package. */
    public ResourceEntry byName(String type, String name) {
        ResourceEntry entry = byName.get(type + ":" + name);
        return entry;
    }

    /** Every entry (any package) whose entry name is in {@code names}. */
    public Set<Integer> idsForNames(Set<String> names) {
        Set<Integer> out = new HashSet<>();
        if (names == null || names.isEmpty()) return out;
        for (Map.Entry<String, ResourceEntry> e : byName.entrySet()) {
            String key = e.getKey();
            String name = key.substring(key.indexOf(':') + 1);
            if (names.contains(name)) out.add(e.getValue().getResourceId());
        }
        return out;
    }

    public PackageBlock packageFor(int resId) {
        return packageByIdByte.get(resId >>> 24);
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
