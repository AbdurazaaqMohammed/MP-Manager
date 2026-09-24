package modder.hub.dexeditor.utils;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DexUsageHelper {

    private DexUsageHelper() {
    }

    public static class OverrideEntry {
        public final ClassDef holder;
        public final Method method;

        public OverrideEntry(ClassDef holder, Method method) {
            this.holder = holder;
            this.method = method;
        }
    }

    public static String toSlash(String type) {
        if (type == null) return "";
        if (type.startsWith("L") && type.endsWith(";")) return type.substring(1, type.length() - 1);
        return type;
    }

    public static String toType(String slash) {
        if (slash == null) return "";
        if (slash.startsWith("L") && slash.endsWith(";")) return slash;
        return "L" + slash + ";";
    }

    public static String methodProto(Method m) {
        StringBuilder sb = new StringBuilder("(");
        try {
            for (Object p : m.getParameters()) {
                sb.append(((com.android.tools.smali.dexlib2.iface.MethodParameter) p).getType());
            }
        } catch (Exception ignored) {
        }
        try {
            sb.append(")").append(m.getReturnType());
        } catch (Exception ignored) {
            sb.append(")V");
        }
        return sb.toString();
    }

    public static boolean isSubclassOf(ClassDef candidate, String targetType, Map<String, ClassDef> map) {
        if (candidate == null || targetType == null || map == null) return false;
        try {
            if (targetType.equals(candidate.getType())) return false;
        } catch (Exception ignored) {
            return false;
        }
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        try {
            if (candidate.getSuperclass() != null) queue.add(candidate.getSuperclass());
            for (String iface : candidate.getInterfaces()) queue.add(iface);
        } catch (Exception ignored) {
            return false;
        }
        while (!queue.isEmpty()) {
            String t = queue.poll();
            if (t == null || !visited.add(t)) continue;
            if (t.equals(targetType)) return true;
            ClassDef def = map.get(toSlash(t));
            if (def == null) continue;
            try {
                if (def.getSuperclass() != null) queue.add(def.getSuperclass());
                for (String iface : def.getInterfaces()) queue.add(iface);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static List<ClassDef> findSubclasses(String targetType, Collection<ClassDef> all, Map<String, ClassDef> map) {
        List<ClassDef> out = new ArrayList<>();
        if (targetType == null || all == null || map == null) return out;
        for (ClassDef c : all) {
            try {
                if (isSubclassOf(c, targetType, map)) out.add(c);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static List<OverrideEntry> findOverrides(String declaringType, String name, String proto, Collection<ClassDef> all, Map<String, ClassDef> map) {
        List<OverrideEntry> out = new ArrayList<>();
        if (declaringType == null || name == null || proto == null || all == null || map == null) return out;
        for (ClassDef c : all) {
            try {
                if (!isSubclassOf(c, declaringType, map)) continue;
                for (Method m : c.getMethods()) {
                    if (name.equals(m.getName()) && proto.equals(methodProto(m))) {
                        out.add(new OverrideEntry(c, m));
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static Method findMethod(ClassDef def, String name, String proto) {
        if (def == null || name == null || proto == null) return null;
        try {
            for (Method m : def.getMethods()) {
                if (name.equals(m.getName()) && proto.equals(methodProto(m))) return m;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
