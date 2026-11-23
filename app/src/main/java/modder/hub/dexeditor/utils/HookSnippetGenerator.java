package modder.hub.dexeditor.utils;

import java.util.ArrayList;
import java.util.List;

public final class HookSnippetGenerator {

    private HookSnippetGenerator() {
    }

    public static String toDotClass(String smaliClass) {
        if (smaliClass == null) return "";
        String s = smaliClass.trim();
        if (s.startsWith("L") && s.endsWith(";")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace('/', '.');
    }

    public static String smaliTypeToJavaType(String desc) {
        if (desc == null || desc.isEmpty()) return "";
        int arrayDim = 0;
        int i = 0;
        while (i < desc.length() && desc.charAt(i) == '[') {
            arrayDim++;
            i++;
        }
        String pure = desc.substring(i);
        String base;
        switch (pure) {
            case "V": base = "void"; break;
            case "Z": base = "boolean"; break;
            case "B": base = "byte"; break;
            case "S": base = "short"; break;
            case "C": base = "char"; break;
            case "I": base = "int"; break;
            case "J": base = "long"; break;
            case "F": base = "float"; break;
            case "D": base = "double"; break;
            default:
                if (pure.startsWith("L") && pure.endsWith(";")) {
                    base = pure.substring(1, pure.length() - 1).replace('/', '.');
                } else {
                    base = pure.replace('/', '.');
                }
                break;
        }
        StringBuilder sb = new StringBuilder(base);
        for (int d = 0; d < arrayDim; d++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    public static List<String> parseMethodParameterJavaTypes(String proto) {
        List<String> list = new ArrayList<>();
        if (proto == null || !proto.startsWith("(")) return list;
        int endParen = proto.indexOf(')');
        if (endParen == -1) return list;
        String params = proto.substring(1, endParen);
        int i = 0;
        while (i < params.length()) {
            int start = i;
            while (i < params.length() && params.charAt(i) == '[') i++;
            if (i < params.length()) {
                char c = params.charAt(i);
                if (c == 'L') {
                    int semi = params.indexOf(';', i);
                    if (semi != -1) {
                        i = semi + 1;
                    } else {
                        i = params.length();
                    }
                } else {
                    i++;
                }
            }
            String desc = params.substring(start, i);
            list.add(smaliTypeToJavaType(desc));
        }
        return list;
    }

    public static String getReturnTypeDesc(String proto) {
        if (proto == null) return "V";
        int endParen = proto.indexOf(')');
        if (endParen != -1 && endParen + 1 < proto.length()) {
            return proto.substring(endParen + 1);
        }
        return "V";
    }

    public static String generateFridaClass(String smaliClass) {
        String dotClass = toDotClass(smaliClass);
        return "Java.perform(function() {\n" +
                "    let targetClass = Java.use(\"" + dotClass + "\");\n" +
                "    console.log(\"Hooked " + dotClass + "\");\n" +
                "});\n";
    }

    public static String generateFridaField(String smaliClass, String methodOrFieldName) {
        String dotClass = toDotClass(smaliClass);
        String fieldName = methodOrFieldName != null && methodOrFieldName.contains(":")
                ? methodOrFieldName.substring(0, methodOrFieldName.indexOf(':'))
                : (methodOrFieldName != null ? methodOrFieldName : "field");
        return "Java.perform(function() {\n" +
                "    let targetClass = Java.use(\"" + dotClass + "\");\n" +
                "    // Read field:\n" +
                "    // let val = targetClass[\"" + fieldName + "\"].value;\n" +
                "    // Set field:\n" +
                "    // targetClass[\"" + fieldName + "\"].value = ...;\n" +
                "});\n";
    }

    public static String generateFridaMethod(String smaliClass, String methodOrFieldName) {
        String dotClass = toDotClass(smaliClass);
        String methodName = methodOrFieldName != null && methodOrFieldName.contains("(")
                ? methodOrFieldName.substring(0, methodOrFieldName.indexOf('('))
                : (methodOrFieldName != null ? methodOrFieldName : "method");
        String proto = methodOrFieldName != null && methodOrFieldName.contains("(")
                ? methodOrFieldName.substring(methodOrFieldName.indexOf('('))
                : "()V";

        List<String> params = parseMethodParameterJavaTypes(proto);
        String returnType = smaliTypeToJavaType(getReturnTypeDesc(proto));
        boolean isConstructor = "<init>".equals(methodName);
        String fridaMethodName = isConstructor ? "$init" : methodName;

        StringBuilder overloadArgs = new StringBuilder();
        StringBuilder funcArgs = new StringBuilder();
        StringBuilder logArgs = new StringBuilder();

        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                overloadArgs.append(", ");
                funcArgs.append(", ");
                logArgs.append(" + \", \" + ");
            }
            overloadArgs.append("'").append(params.get(i)).append("'");
            funcArgs.append("arg").append(i);
            logArgs.append("arg").append(i);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Java.perform(function() {\n");
        sb.append("    let targetClass = Java.use(\"").append(dotClass).append("\");\n");
        sb.append("    targetClass[\"").append(fridaMethodName).append("\"]");
        if (overloadArgs.length() > 0) {
            sb.append(".overload(").append(overloadArgs).append(")");
        } else {
            sb.append(".overload()");
        }
        sb.append(".implementation = function(").append(funcArgs).append(") {\n");

        if (funcArgs.length() > 0) {
            sb.append("        console.log(\"").append(dotClass).append(".").append(methodName)
                    .append(" called with: \" + ").append(logArgs).append(");\n");
        } else {
            sb.append("        console.log(\"").append(dotClass).append(".").append(methodName).append(" called\");\n");
        }

        if (isConstructor || "void".equals(returnType)) {
            sb.append("        this[\"").append(fridaMethodName).append("\"](").append(funcArgs).append(");\n");
        } else {
            sb.append("        let ret = this[\"").append(fridaMethodName).append("\"](").append(funcArgs).append(");\n");
            sb.append("        console.log(\"").append(dotClass).append(".").append(methodName).append(" returned: \" + ret);\n");
            sb.append("        return ret;\n");
        }
        sb.append("    };\n");
        sb.append("});\n");
        return sb.toString();
    }

    public static String generateXposedClass(String smaliClass) {
        String dotClass = toDotClass(smaliClass);
        return "Class<?> clazz = XposedHelpers.findClass(\"" + dotClass + "\", lpparam.classLoader);\n";
    }

    public static String generateXposedField(String smaliClass, String methodOrFieldName) {
        String fieldName = methodOrFieldName != null && methodOrFieldName.contains(":")
                ? methodOrFieldName.substring(0, methodOrFieldName.indexOf(':'))
                : (methodOrFieldName != null ? methodOrFieldName : "field");
        return "// Field: " + fieldName + "\n" +
                "Object fieldValue = XposedHelpers.getObjectField(param.thisObject, \"" + fieldName + "\");\n" +
                "// XposedHelpers.setObjectField(param.thisObject, \"" + fieldName + "\", /* newValue */);\n";
    }

    public static String generateXposedMethod(String smaliClass, String methodOrFieldName) {
        String dotClass = toDotClass(smaliClass);
        String methodName = methodOrFieldName != null && methodOrFieldName.contains("(")
                ? methodOrFieldName.substring(0, methodOrFieldName.indexOf('('))
                : (methodOrFieldName != null ? methodOrFieldName : "method");
        String proto = methodOrFieldName != null && methodOrFieldName.contains("(")
                ? methodOrFieldName.substring(methodOrFieldName.indexOf('('))
                : "()V";

        List<String> params = parseMethodParameterJavaTypes(proto);
        boolean isConstructor = "<init>".equals(methodName);

        StringBuilder paramClasses = new StringBuilder();
        for (String p : params) {
            paramClasses.append(toJavaClassLiteral(p)).append(", ");
        }

        StringBuilder sb = new StringBuilder();
        if (isConstructor) {
            sb.append("XposedHelpers.findAndHookConstructor(\"").append(dotClass).append("\", lpparam.classLoader, ")
                    .append(paramClasses).append("new XC_MethodHook() {\n")
                    .append("    @Override\n")
                    .append("    protected void afterHookedMethod(MethodHookParam param) throws Throwable {\n")
                    .append("        super.afterHookedMethod(param);\n")
                    .append("    }\n")
                    .append("});\n");
        } else {
            sb.append("XposedHelpers.findAndHookMethod(\"").append(dotClass).append("\", lpparam.classLoader, \"")
                    .append(methodName).append("\", ").append(paramClasses).append("new XC_MethodHook() {\n")
                    .append("    @Override\n")
                    .append("    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {\n")
                    .append("        super.beforeHookedMethod(param);\n")
                    .append("    }\n\n")
                    .append("    @Override\n")
                    .append("    protected void afterHookedMethod(MethodHookParam param) throws Throwable {\n")
                    .append("        super.afterHookedMethod(param);\n")
                    .append("    }\n")
                    .append("});\n");
        }
        return sb.toString();
    }

    private static String toJavaClassLiteral(String type) {
        if (type == null || type.isEmpty()) return "Object.class";
        switch (type) {
            case "boolean": return "boolean.class";
            case "byte": return "byte.class";
            case "short": return "short.class";
            case "char": return "char.class";
            case "int": return "int.class";
            case "long": return "long.class";
            case "float": return "float.class";
            case "double": return "double.class";
            case "void": return "void.class";
            default:
                if (type.endsWith("[]")) {
                    return "\"" + type + "\"";
                }
                return type + ".class";
        }
    }
}
