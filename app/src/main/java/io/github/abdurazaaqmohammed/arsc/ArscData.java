package io.github.abdurazaaqmohammed.arsc;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ValueType;
import com.reandroid.graphics.AndroidColor;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArscData {

    public TableBlock table;
    public File arscFile;
    public File apkFile;
    public String zipEntryPath = "resources.arsc";
    public final List<HistoryEntry> history = new ArrayList<>();

    public static class Node {
        public String label;
        public String key;
        public int depth;
        public boolean dir;
        public boolean expanded;
        public boolean checked;
        public Object tag;
        public Node parent;
        public final List<Node> children = new ArrayList<>();
    }

    public static class HistoryEntry {
        public final long time = System.currentTimeMillis();
        public final String desc;
        public final Runnable revert;

        public HistoryEntry(String desc, Runnable revert) {
            this.desc = desc;
            this.revert = revert;
        }
    }

    public static ArscData load(File arsc, File apk, String entryPath) throws IOException {
        ArscData data = new ArscData();
        data.arscFile = arsc;
        data.apkFile = apk;
        if (entryPath != null) data.zipEntryPath = entryPath;
        data.table = TableBlock.load(arsc);
        return data;
    }

    public List<Node> buildTree() {
        List<Node> roots = new ArrayList<>();
        for (PackageBlock pkg : table.listPackages()) {
            Node pkgNode = new Node();
            pkgNode.label = pkg.getName() + " [" + hex(pkg.getId()) + "]";
            pkgNode.key = pkg.getName();
            pkgNode.depth = 0;
            pkgNode.dir = true;
            pkgNode.tag = pkg;
            for (SpecTypePair spec : pkg.listSpecTypePairs()) {
                Node typeNode = new Node();
                typeNode.label = spec.getTypeName() + " [" + hex(spec.getId()) + "]";
                typeNode.key = pkg.getName() + "/" + spec.getTypeName();
                typeNode.depth = 1;
                typeNode.dir = true;
                typeNode.tag = spec;
                typeNode.parent = pkgNode;
                Iterator<ResourceEntry> it = spec.getResources();
                while (it.hasNext()) {
                    ResourceEntry re = it.next();
                    Node fileNode = new Node();
                    fileNode.label = re.getName();
                    fileNode.key = pkg.getName() + "/" + spec.getTypeName() + "/" + re.getName();
                    fileNode.depth = 2;
                    fileNode.dir = false;
                    fileNode.tag = re;
                    fileNode.parent = typeNode;
                    typeNode.children.add(fileNode);
                }
                pkgNode.children.add(typeNode);
            }
            roots.add(pkgNode);
        }
        return roots;
    }

    public static String hex(int id) {
        return String.format(Locale.US, "%02X", id & 0xFF);
    }

    public String entryDisplay(ResourceEntry re) {
        StringBuilder sb = new StringBuilder();
        try {
            Iterator<Entry> it = re.iterator();
            boolean first = true;
            while (it.hasNext()) {
                Entry e = it.next();
                if (e == null || e.isNull()) continue;
                if (!first) sb.append('\n');
                first = false;
                String value = null;
                try {
                    value = e.getValueAsString();
                } catch (Exception ignored) {
                }
                if (value == null) {
                    try {
                        value = e.getResValue().decodeValue();
                    } catch (Exception ex) {
                        value = "?";
                    }
                }
                sb.append(value == null ? "?" : value);
            }
        } catch (Exception e) {
            sb.append("?");
        }
        return sb.toString();
    }

    public String entryXml(ResourceEntry re) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        try {
            Iterator<Entry> it = re.iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (e == null || e.isNull()) continue;
                String tag = "item";
                try {
                    tag = e.getXmlTag();
                } catch (Exception ignored) {
                }
                String value = "?";
                try {
                    value = e.getValueAsString();
                    if (value == null) value = e.getResValue().decodeValue();
                    if (value == null) value = "?";
                } catch (Exception ignored) {
                }
                sb.append("    <").append(tag).append(" name=\"").append(escapeXml(re.getName())).append("\">")
                        .append(escapeXml(value)).append("</").append(tag).append(">\n");
            }
        } catch (Exception ignored) {
        }
        sb.append("</resources>\n");
        return sb.toString();
    }

    public static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public Entry defaultEntry(ResourceEntry re) {
        try {
            Entry e = re.get();
            if (e != null && !e.isNull()) return e;
        } catch (Exception ignored) {
        }
        try {
            Iterator<Entry> it = re.iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (e != null && !e.isNull()) return e;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<ConfigValue> configValues(ResourceEntry re) {
        List<ConfigValue> out = new ArrayList<>();
        try {
            PackageBlock pkg = null;
            for (PackageBlock p : table.listPackages()) {
                if (p.getName().equals(re.getPackageName())) {
                    pkg = p;
                    break;
                }
            }
            if (pkg == null) return out;
            SpecTypePair spec = pkg.getSpecTypePair(re.getType());
            if (spec == null) return out;
            Iterator<TypeBlock> blocks = spec.getTypeBlocks();
            while (blocks.hasNext()) {
                TypeBlock tb = blocks.next();
                String q;
                try {
                    q = tb.getQualifiers();
                } catch (Exception e) {
                    q = "";
                }
                Entry e = null;
                try {
                    e = tb.getEntry(re.getName());
                } catch (Exception ignored) {
                }
                if (e == null || e.isNull()) continue;
                String value = null;
                try {
                    value = e.getValueAsString();
                } catch (Exception ignored) {
                }
                if (value == null) {
                    try {
                        value = e.getResValue().decodeValue();
                    } catch (Exception ignored) {
                    }
                }
                out.add(new ConfigValue(q == null ? "" : q, e, value == null ? "?" : value));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static class ConfigValue {
        public final String qualifiers;
        public final Entry entry;
        public final String value;

        public ConfigValue(String qualifiers, Entry entry, String value) {
            this.qualifiers = qualifiers;
            this.entry = entry;
            this.value = value;
        }
    }

    public boolean setEntryValue(Entry e, String newText) {
        if (e == null) return false;
        ValueType type = null;
        try {
            type = e.getValueType();
        } catch (Exception ignored) {
        }
        if (type == null) return false;
        try {
            if (type == ValueType.STRING) {
                e.setValueAsString(newText);
                return true;
            }
            if (type == ValueType.BOOLEAN) {
                e.setValueAsBoolean(newText.trim().equalsIgnoreCase("true"));
                return true;
            }
            if (type.isColor()) {
                try {
                    AndroidColor color = AndroidColor.decode(newText.trim());
                    if (color != null) {
                        e.setValueAsColor(color);
                        return true;
                    }
                } catch (Exception ignored) {
                }
                e.setValueAsRaw(ValueType.COLOR_ARGB8, parseNumber(newText.trim()));
                return true;
            }
            if (type.isReference()) {
                e.setValueAsReference(parseReference(e, newText.trim()));
                return true;
            }
            if (type == ValueType.FLOAT) {
                e.setValueAsRaw(type, Float.floatToRawIntBits(Float.parseFloat(newText.trim())));
                return true;
            }
            e.setValueAsRaw(type, parseNumber(newText.trim()));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static int parseNumber(String text) {
        String t = text.trim().replace("_", "").replace(" ", "");
        boolean negative = t.startsWith("-");
        if (negative) t = t.substring(1);
        long value;
        if (t.startsWith("0x") || t.startsWith("0X")) {
            value = Long.parseLong(t.substring(2), 16);
        } else if (t.startsWith("0b") || t.startsWith("0B")) {
            value = Long.parseLong(t.substring(2), 2);
        } else if (t.length() > 1 && t.startsWith("0")) {
            value = Long.parseLong(t, 8);
        } else {
            value = Long.parseLong(t, 10);
        }
        if (negative) value = -value;
        return (int) (value & 0xFFFFFFFFL);
    }

    private int parseReference(Entry context, String text) {
        String t = text.trim();
        if (t.startsWith("@")) t = t.substring(1);
        if (t.startsWith("?")) t = t.substring(1);
        if (t.startsWith("0x") || t.startsWith("0X")) return parseNumber(t);
        if (t.matches("[0-9a-fA-F]+") && (t.length() == 8 || t.startsWith("7f"))) {
            try {
                return (int) (Long.parseLong(t, 16) & 0xFFFFFFFFL);
            } catch (Exception ignored) {
            }
        }
        int slash = t.indexOf('/');
        if (slash > 0) {
            String type = t.substring(0, slash);
            String name = t.substring(slash + 1);
            if (type.startsWith("android:")) {
                type = type.substring(8);
            }
            try {
                for (PackageBlock pkg : table.listPackages()) {
                    int id = table.resolveResourceId(pkg.getName(), type, name);
                    if (id != 0) return id;
                }
            } catch (Exception ignored) {
            }
        }
        return parseNumber(t);
    }

    public ResourceEntry addEntry(String pkgName, String type, String name) {
        PackageBlock pkg = null;
        for (PackageBlock p : table.listPackages()) {
            if (p.getName().equals(pkgName)) {
                pkg = p;
                break;
            }
        }
        if (pkg == null) return null;
        Entry e;
        try {
            e = pkg.getOrCreate("", type, name);
        } catch (Exception ex) {
            return null;
        }
        if (e == null) return null;
        try {
            ValueType typeEnum = e.getValueType();
            if (typeEnum == ValueType.STRING || typeEnum == null) {
                e.setValueAsString("");
            } else if (typeEnum == ValueType.BOOLEAN) {
                e.setValueAsBoolean(false);
            } else {
                e.setValueAsRaw(typeEnum, 0);
            }
        } catch (Exception ignored) {
        }
        try {
            SpecTypePair spec = pkg.getSpecTypePair(type);
            if (spec != null) {
                ResourceEntry re = spec.getResource(name);
                if (re != null) {
                    pushHistory("Add " + pkgName + "/" + type + "/" + name, () -> {
                        try {
                            Entry created = null;
                            Iterator<Entry> it = re.iterator();
                            while (it.hasNext()) {
                                Entry x = it.next();
                                if (x != null && !x.isNull()) {
                                    created = x;
                                    break;
                                }
                            }
                            if (created != null) created.setNull(true);
                        } catch (Exception ignored) {
                        }
                    });
                    return re;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean copyEntry(ResourceEntry src, String newName) {
        try {
            PackageBlock pkg = null;
            for (PackageBlock p : table.listPackages()) {
                if (p.getName().equals(src.getPackageName())) {
                    pkg = p;
                    break;
                }
            }
            if (pkg == null) return false;
            Entry dst = pkg.getOrCreate("", src.getType(), newName);
            if (dst == null) return false;
            Entry s = defaultEntry(src);
            if (s == null) return false;
            ResValue rv = s.getResValue();
            if (rv == null) return false;
            dst.setValueAsRaw(s.getValueType(), rv.getData());
            pushHistory("Copy to " + newName, () -> {
                try {
                    dst.setNull(true);
                } catch (Exception ignored) {
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean renameEntry(ResourceEntry re, String newName) {
        try {
            String old = re.getName();
            re.setName(newName);
            pushHistory("Rename " + old + " to " + newName, () -> {
                try {
                    re.setName(old);
                } catch (Exception ignored) {
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteEntry(ResourceEntry re) {
        try {
            List<Entry> removed = new ArrayList<>();
            List<ValueType> types = new ArrayList<>();
            List<Integer> datas = new ArrayList<>();
            Iterator<Entry> it = re.iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (e == null || e.isNull()) continue;
                removed.add(e);
                ValueType t = null;
                int d = 0;
                try {
                    t = e.getValueType();
                    ResValue rv = e.getResValue();
                    if (rv != null) d = rv.getData();
                } catch (Exception ignored) {
                }
                types.add(t);
                datas.add(d);
                try {
                    e.setNull(true);
                } catch (Exception ignored) {
                }
            }
            if (removed.isEmpty()) return false;
            pushHistory("Delete " + re.getName(), () -> {
                for (int i = 0; i < removed.size(); i++) {
                    try {
                        if (types.get(i) != null) {
                            removed.get(i).setValueAsRaw(types.get(i), datas.get(i));
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int deleteType(String pkgName, String type) {
        int count = 0;
        try {
            PackageBlock pkg = null;
            for (PackageBlock p : table.listPackages()) {
                if (p.getName().equals(pkgName)) {
                    pkg = p;
                    break;
                }
            }
            if (pkg == null) return 0;
            SpecTypePair spec = pkg.getSpecTypePair(type);
            if (spec == null) return 0;
            List<ResourceEntry> all = new ArrayList<>();
            Iterator<ResourceEntry> it = spec.getResources();
            while (it.hasNext()) all.add(it.next());
            for (ResourceEntry re : all) {
                if (deleteEntry(re)) count++;
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    public void pushHistory(String desc, Runnable revert) {
        history.add(0, new HistoryEntry(desc, revert));
        while (history.size() > 200) history.remove(history.size() - 1);
    }

    public static class SearchHit {
        public ResourceEntry entry;
        public String line;
        public String detail;
    }

    public List<SearchHit> search(String query, String searchType, String pathFilter) {
        List<SearchHit> out = new ArrayList<>();
        if (query == null) query = "";
        String q = query.trim();
        String type = searchType == null ? "xml" : searchType;
        String path = pathFilter == null ? "" : pathFilter.trim();
        try {
            if (type.equals("resource id")) {
                ResourceEntry found = null;
                String t = q.startsWith("@") ? q.substring(1) : q;
                if (t.matches("(?i)(0x)?[0-9a-f]+")) {
                    String hex = t.startsWith("0x") || t.startsWith("0X") ? t.substring(2) : t;
                    if (hex.length() <= 8) {
                        try {
                            int id = (int) (Long.parseLong(hex, 16) & 0xFFFFFFFFL);
                            found = table.getResource(id);
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (found == null && t.contains("/")) {
                    String ref = t.startsWith("?") ? t.substring(1) : t;
                    int slash = ref.indexOf('/');
                    if (slash > 0) {
                        String rtype = ref.substring(0, slash);
                        String rname = ref.substring(slash + 1);
                        if (rtype.contains(":")) rtype = rtype.substring(rtype.indexOf(':') + 1);
                        for (PackageBlock pkg : table.listPackages()) {
                            try {
                                int id = table.resolveResourceId(pkg.getName(), rtype, rname);
                                if (id != 0) {
                                    found = table.getResource(id);
                                    if (found != null) break;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                if (found != null && matchPath(found, path)) {
                    SearchHit hit = new SearchHit();
                    hit.entry = found;
                    hit.line = found.getType() + "/" + found.getName();
                    hit.detail = found.getHexId();
                    out.add(hit);
                }
                return out;
            }
            Iterator<ResourceEntry> all = table.getResources();
            while (all.hasNext()) {
                ResourceEntry re;
                try {
                    re = all.next();
                } catch (Exception e) {
                    continue;
                }
                if (re == null || !matchPath(re, path)) continue;
                if (type.equals("xml")) {
                    if (q.isEmpty() || re.getName().toLowerCase(Locale.US).contains(q.toLowerCase(Locale.US))) {
                        SearchHit hit = new SearchHit();
                        hit.entry = re;
                        hit.line = re.getType() + "/" + re.getName();
                        hit.detail = re.getHexId();
                        out.add(hit);
                    }
                } else if (type.equals("string")) {
                    Iterator<Entry> it = re.iterator();
                    while (it.hasNext()) {
                        Entry e = it.next();
                        if (e == null || e.isNull()) continue;
                        ValueType vt = null;
                        try {
                            vt = e.getValueType();
                        } catch (Exception ignored) {
                        }
                        if (vt != ValueType.STRING) continue;
                        String v = null;
                        try {
                            v = e.getValueAsString();
                        } catch (Exception ignored) {
                        }
                        if (v != null && (q.isEmpty() || v.toLowerCase(Locale.US).contains(q.toLowerCase(Locale.US)))) {
                            SearchHit hit = new SearchHit();
                            hit.entry = re;
                            hit.line = re.getName();
                            hit.detail = v;
                            out.add(hit);
                            break;
                        }
                    }
                } else if (type.equals("integer")) {
                    int want;
                    try {
                        want = parseNumber(q);
                    } catch (Exception e) {
                        return out;
                    }
                    Iterator<Entry> it = re.iterator();
                    while (it.hasNext()) {
                        Entry e = it.next();
                        if (e == null || e.isNull()) continue;
                        ValueType vt = null;
                        try {
                            vt = e.getValueType();
                        } catch (Exception ignored) {
                        }
                        if (vt == null || !vt.isInteger()) continue;
                        int data = 0;
                        try {
                            data = e.getResValue().getData();
                        } catch (Exception ignored) {
                        }
                        if (data == want) {
                            SearchHit hit = new SearchHit();
                            hit.entry = re;
                            hit.line = re.getName();
                            hit.detail = String.valueOf(data);
                            out.add(hit);
                            break;
                        }
                    }
                } else if (type.equals("color")) {
                    int want;
                    try {
                        want = parseColor(q);
                    } catch (Exception e) {
                        return out;
                    }
                    Iterator<Entry> it = re.iterator();
                    while (it.hasNext()) {
                        Entry e = it.next();
                        if (e == null || e.isNull()) continue;
                        ValueType vt = null;
                        try {
                            vt = e.getValueType();
                        } catch (Exception ignored) {
                        }
                        if (vt == null || !vt.isColor()) continue;
                        int data = 0;
                        try {
                            data = e.getResValue().getData();
                        } catch (Exception ignored) {
                        }
                        if (data == want) {
                            SearchHit hit = new SearchHit();
                            hit.entry = re;
                            hit.line = re.getName();
                            hit.detail = String.format(Locale.US, "#%08X", data);
                            out.add(hit);
                            break;
                        }
                    }
                }
                if (out.size() >= 500) break;
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean matchPath(ResourceEntry re, String path) {
        if (path == null || path.isEmpty()) return true;
        String p = path.trim().replace("\\", "/");
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) return true;
        String full = re.getPackageName() + "/" + re.getType() + "/" + re.getName();
        String[] parts = p.split("/");
        if (parts.length == 1) {
            String s = parts[0];
            return re.getPackageName().equals(s) || re.getType().equals(s) || re.getName().equals(s);
        }
        if (parts.length == 2) {
            return re.getPackageName().equals(parts[0]) && re.getType().equals(parts[1]);
        }
        return full.equals(p);
    }

    public static int parseColor(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        t = t.replace("_", "").replace(" ", "");
        if (t.length() == 3) {
            char r = t.charAt(0);
            char g = t.charAt(1);
            char b = t.charAt(2);
            t = "FF" + r + r + g + g + b + b;
        } else if (t.length() == 4) {
            char a = t.charAt(0);
            char r = t.charAt(1);
            char g = t.charAt(2);
            char b = t.charAt(3);
            t = "" + a + a + r + r + g + g + b + b;
        } else if (t.length() == 6) {
            t = "FF" + t;
        } else if (t.length() != 8) {
            throw new IllegalArgumentException("Bad color");
        }
        return (int) (Long.parseLong(t, 16) & 0xFFFFFFFFL);
    }

    public List<ResourceEntry> stringEntries(String query) {
        List<ResourceEntry> out = new ArrayList<>();
        try {
            Iterator<ResourceEntry> all = table.getResources();
            while (all.hasNext()) {
                ResourceEntry re;
                try {
                    re = all.next();
                } catch (Exception e) {
                    continue;
                }
                if (re == null || !"string".equals(re.getType())) continue;
                if (query != null && !query.isEmpty()) {
                    String n = re.getName().toLowerCase(Locale.US);
                    String v = entryDisplay(re).toLowerCase(Locale.US);
                    String lq = query.toLowerCase(Locale.US);
                    if (!n.contains(lq) && !v.contains(lq)) continue;
                }
                out.add(re);
                if (out.size() >= 2000) break;
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void save() throws IOException {
        File tmp = new File(arscFile.getParentFile(), arscFile.getName() + ".tmp" + System.currentTimeMillis());
        try {
            table.writeBytes(tmp);
            copyFile(tmp, arscFile);
        } finally {
            try {
                tmp.delete();
            } catch (Exception ignored) {
            }
        }
        if (apkFile != null && apkFile.isFile()) {
            ZipFile zf = new ZipFile(apkFile);
            FileHeader existing = null;
            try {
                existing = zf.getFileHeader(zipEntryPath);
            } catch (Exception ignored) {
            }
            if (existing != null) {
                try {
                    zf.removeFile(existing);
                } catch (Exception e) {
                    throw new IOException("Cannot replace entry: " + e.getMessage());
                }
            }
            ZipParameters params = new ZipParameters();
            params.setFileNameInZip(zipEntryPath);
            try {
                zf.addFile(arscFile, params);
            } catch (Exception e) {
                throw new IOException("Cannot write entry: " + e.getMessage());
            }
        }
    }

    public File backup() throws IOException {
        File bak;
        if (apkFile != null && apkFile.isFile()) {
            bak = new File(apkFile.getParentFile(), apkFile.getName() + ".bak");
            copyFile(apkFile, bak);
        } else {
            bak = new File(arscFile.getParentFile(), arscFile.getName() + ".bak");
            copyFile(arscFile, bak);
        }
        return bak;
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    public void exportEntries(List<ResourceEntry> entries, File dir, String baseName) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new IOException("Cannot create dir");
        Map<String, List<ResourceEntry>> byType = new LinkedHashMap<>();
        for (ResourceEntry re : entries) {
            if (re == null) continue;
            List<ResourceEntry> list = byType.get(re.getType());
            if (list == null) {
                list = new ArrayList<>();
                byType.put(re.getType(), list);
            }
            list.add(re);
        }
        for (Map.Entry<String, List<ResourceEntry>> group : byType.entrySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
            for (ResourceEntry re : group.getValue()) {
                String tag = "item";
                String value = "?";
                try {
                    Entry e = defaultEntry(re);
                    if (e != null) {
                        try {
                            tag = e.getXmlTag();
                        } catch (Exception ignored) {
                        }
                        try {
                            value = e.getValueAsString();
                            if (value == null) value = e.getResValue().decodeValue();
                        } catch (Exception ignored) {
                        }
                    }
                    if (value == null) value = "?";
                } catch (Exception ignored) {
                }
                sb.append("    <").append(tag).append(" name=\"").append(escapeXml(re.getName())).append("\">")
                        .append(escapeXml(value)).append("</").append(tag).append(">\n");
            }
            sb.append("</resources>\n");
            File out = new File(dir, baseName + "_" + group.getKey() + ".xml");
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(sb.toString().getBytes("UTF-8"));
            }
        }
    }
    public int importFromArsc(File other, String pkgName, String typeName) throws IOException {
        TableBlock otherTable = TableBlock.load(other);
        int count = 0;
        for (PackageBlock pkg : otherTable.listPackages()) {
            if (pkgName != null && !pkg.getName().equals(pkgName)) continue;
            PackageBlock mine = null;
            for (PackageBlock p : table.listPackages()) {
                if (p.getName().equals(pkg.getName())) {
                    mine = p;
                    break;
                }
            }
            if (mine == null) continue;
            for (SpecTypePair spec : pkg.listSpecTypePairs()) {
                if (typeName != null && !spec.getTypeName().equals(typeName)) continue;
                SpecTypePair mySpec = mine.getSpecTypePair(spec.getTypeName());
                if (mySpec == null) continue;
                Iterator<ResourceEntry> it = spec.getResources();
                while (it.hasNext()) {
                    ResourceEntry src;
                    try {
                        src = it.next();
                    } catch (Exception e) {
                        continue;
                    }
                    if (src == null) continue;
                    boolean exists = false;
                    try {
                        ResourceEntry have = mySpec.getResource(src.getName());
                        if (have != null) {
                            Entry he = null;
                            try {
                                Iterator<Entry> hit = have.iterator();
                                while (hit.hasNext()) {
                                    Entry x = hit.next();
                                    if (x != null && !x.isNull()) {
                                        he = x;
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                            exists = he != null;
                        }
                    } catch (Exception ignored) {
                    }
                    if (exists) continue;
                    Entry s = null;
                    try {
                        Iterator<Entry> sit = src.iterator();
                        while (sit.hasNext()) {
                            Entry x = sit.next();
                            if (x != null && !x.isNull()) {
                                s = x;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (s == null) continue;
                    ResValue rv = null;
                    ValueType vt = null;
                    try {
                        rv = s.getResValue();
                        vt = s.getValueType();
                    } catch (Exception ignored) {
                    }
                    if (rv == null || vt == null) continue;
                    try {
                        Entry dst = mine.getOrCreate("", spec.getTypeName(), src.getName());
                        if (dst == null) continue;
                        if (vt == ValueType.STRING) {
                            String sv = null;
                            try {
                                sv = s.getValueAsString();
                            } catch (Exception ignored) {
                            }
                            dst.setValueAsString(sv == null ? "" : sv);
                        } else {
                            dst.setValueAsRaw(vt, rv.getData());
                        }
                        count++;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (count > 0) pushHistory("Import " + count + " entries", null);
        return count;
    }
}
