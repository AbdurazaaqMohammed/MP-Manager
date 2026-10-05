package io.github.abdurazaaqmohammed.features.apk.translate;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.arsc.ArscData;

/**
 * Locale configurations of the {@code string} type, the way MT Manager presents them.
 *
 * <p>MT's model is config-centric rather than key-centric: a language pack is a <em>config</em>
 * inside {@code resources.arsc}, and its two creation routes are the ones reproduced here.
 * <ul>
 *   <li><b>复制配置</b> - long-press an existing config, copy it, type a new qualifier. Every
 *       entry is carried over with its <em>original</em> value, which is why the manual then
 *       says "the text under -zh-rCN is not Chinese yet, so go translate it".</li>
 *   <li><b>添加配置</b> - add a qualifier with no values at all.</li>
 * </ul>
 */
public final class ArscConfigManager {

    /** The resource type this screen is about; MT's translation mode is string-only too. */
    public static final String TYPE = "string";

    private final ArscData data;

    public ArscConfigManager(ArscData data) {
        this.data = data;
    }

    /** The package that owns the string resources, i.e. the app's own package. */
    public String packageName() {
        for (PackageBlock pkg : data.table.listPackages()) {
            try {
                if (pkg.getSpecTypePair(TYPE) != null) return pkg.getName();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public SpecTypePair spec() {
        String pkgName = packageName();
        return pkgName == null ? null : data.packageByName(pkgName).getSpecTypePair(TYPE);
    }

    /**
     * Qualifiers of every config that actually holds at least one value, in the order they sit
     * in the table. An APK that merely reserves a config without filling it in does not count,
     * otherwise it would show up as an existing translation.
     */
    public List<String> configs() {
        Set<String> out = new LinkedHashSet<>();
        for (TypeBlock block : blocks()) {
            try {
                if (!block.isEmpty()) out.add(Locales.normalize(block.getQualifiers()));
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(out);
    }

    public List<TypeBlock> blocks() {
        List<TypeBlock> out = new ArrayList<>();
        SpecTypePair spec = spec();
        if (spec == null) return out;
        java.util.Iterator<TypeBlock> it = spec.getTypeBlocks();
        while (it.hasNext()) {
            TypeBlock block = it.next();
            if (block != null) out.add(block);
        }
        return out;
    }

    public TypeBlock block(String qualifier) {
        String wanted = Locales.normalize(qualifier);
        for (TypeBlock block : blocks()) {
            try {
                if (Locales.normalize(block.getQualifiers()).equals(wanted)) return block;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public boolean exists(String qualifier) {
        return block(qualifier) != null;
    }

    /** @return how many entries were carried over, or -1 when the source config is unusable. */
    public int copyConfig(String from, String to) {
        String source = Locales.normalize(from);
        String target = Locales.normalize(to);
        if (target.isEmpty()) return -1;              // the default config is not a language pack
        if (source.equals(target)) return -1;
        SpecTypePair spec = spec();
        if (spec == null) return -1;
        TypeBlock src = block(source);
        if (src == null) return -1;
        TypeBlock dst;
        try {
            dst = spec.getOrCreateTypeBlock(target);
        } catch (Exception e) {
            return -1;
        }
        if (dst == null) return -1;

        int copied = 0;
        for (ResourceEntry re : data.stringEntries("")) {
            if (re == null) continue;
            String name = name(re);
            if (name.isEmpty()) continue;
            Entry from_entry;
            try {
                from_entry = src.getEntry(name);
            } catch (Exception e) {
                continue;
            }
            if (from_entry == null || from_entry.isNull()) continue;
            String value;
            try {
                value = from_entry.getValueAsString();
            } catch (Exception e) {
                continue;
            }
            if (value == null) continue;
            try {
                // The value is deliberately copied verbatim: MT leaves the new config in the
                // source language on purpose, and the translation mode is what rewrites it.
                dst.getOrCreateEntry(name).setValueAsString(value);
                copied++;
            } catch (Exception ignored) {
            }
        }
        return copied;
    }

    /**
     * Adds a qualifier with no values, MT's second route for creating a language pack.
     *
     * @return true when the config is present afterwards
     */
    public boolean addEmptyConfig(String qualifier) {
        String target = Locales.normalize(qualifier);
        if (target.isEmpty()) return false;
        SpecTypePair spec = spec();
        if (spec == null) return false;
        try {
            return spec.getOrCreateTypeBlock(target) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes a config and every value in it.
     *
     * <p>The default config is refused: dropping it would leave every string in the APK
     * unresolvable, which is not something a translation screen should be able to do by
     * accident.
     *
     * @return true when a config was removed
     */
    public boolean deleteConfig(String qualifier) {
        String target = Locales.normalize(qualifier);
        if (target.isEmpty()) return false;
        SpecTypePair spec = spec();
        if (spec == null) return false;
        TypeBlock block = block(target);
        if (block == null) return false;
        try {
            return spec.getTypeBlockArray().remove(block);
        } catch (Exception e) {
            return false;
        }
    }

    /** Entries of one config that carry a value, resolved to the arsc handles. */
    public List<ResourceEntry> entries(String qualifier) {
        List<ResourceEntry> out = new ArrayList<>();
        TypeBlock block = block(qualifier);
        if (block == null) {
            // A config that exists but has no values yet still deserves an editable list.
            for (ResourceEntry re : data.stringEntries("")) {
                if (re != null) out.add(re);
            }
            return out;
        }
        for (ResourceEntry re : data.stringEntries("")) {
            if (re == null) continue;
            String name = name(re);
            if (name.isEmpty()) continue;
            try {
                Entry entry = block.getEntry(name);
                if (entry != null && !entry.isNull()) out.add(re);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static String name(ResourceEntry re) {
        try {
            String name = re.getName();
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }
}
