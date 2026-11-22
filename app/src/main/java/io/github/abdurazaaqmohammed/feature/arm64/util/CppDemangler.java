package io.github.abdurazaaqmohammed.feature.arm64.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight pure-Java Itanium C++ ABI and JNI Symbol Demangler.
 * Demangles symbols like:
 *   _ZN7android4base10LogMessageD1Ev -> android::base::LogMessage::~LogMessage()
 *   _ZN7android4base10LogMessageC1EPKci -> android::base::LogMessage::LogMessage(const char*, int)
 *   _Z13native_init_api -> native_init_api()
 *   Java_com_example_app_MainActivity_testMethod -> [JNI] com.example.app.MainActivity.testMethod
 */
public final class CppDemangler {

    private CppDemangler() {}

    public static String demangle(String mangled) {
        if (mangled == null || mangled.isEmpty()) return mangled;

        if (mangled.startsWith("Java_")) {
            return demangleJni(mangled);
        }

        if (mangled.startsWith("_Z")) {
            try {
                String demangled = demangleItanium(mangled);
                if (demangled != null && !demangled.isEmpty()) {
                    return demangled;
                }
            } catch (Exception ignored) {
                // fallback to original if exotic syntax encountered
            }
        }

        return mangled;
    }

    private static String demangleJni(String s) {
        // Remove Java_ prefix
        String body = s.substring(5);
        // Replace JNI escape sequences: _1 -> _, _2 -> ;, _3 -> [
        body = body.replace("_1", "\u0001")
                   .replace("_2", ";")
                   .replace("_3", "[");

        // Split by single underscore
        String[] parts = body.split("_");
        StringBuilder sb = new StringBuilder("[JNI] ");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replace("\u0001", "_");
            sb.append(part);
            if (i < parts.length - 2) {
                sb.append('.');
            } else if (i == parts.length - 2) {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    private static String demangleItanium(String s) {
        int idx = 2; // skip _Z
        if (idx >= s.length()) return null;

        // Nested name: _ZN...E
        if (s.charAt(idx) == 'N') {
            idx++; // skip N
            List<String> components = new ArrayList<>();
            String className = null;

            while (idx < s.length() && s.charAt(idx) != 'E') {
                char c = s.charAt(idx);

                // Check constructor / destructor
                if (c == 'C' && idx + 1 < s.length()) {
                    char cType = s.charAt(idx + 1);
                    if (cType >= '1' && cType <= '4') {
                        components.add(className != null ? className : "Constructor");
                        idx += 2;
                        continue;
                    }
                } else if (c == 'D' && idx + 1 < s.length()) {
                    char dType = s.charAt(idx + 1);
                    if (dType >= '0' && dType <= '2') {
                        components.add("~" + (className != null ? className : "Destructor"));
                        idx += 2;
                        continue;
                    }
                } else if (c == 'I') {
                    // Template args: skip to E
                    idx++;
                    while (idx < s.length() && s.charAt(idx) != 'E') idx++;
                    if (idx < s.length()) idx++;
                    continue;
                } else if (c == 'K' || c == 'V') {
                    // cv-qualifiers
                    idx++;
                    continue;
                }

                // Parse length
                if (Character.isDigit(c)) {
                    int len = 0;
                    while (idx < s.length() && Character.isDigit(s.charAt(idx))) {
                        len = len * 10 + (s.charAt(idx) - '0');
                        idx++;
                    }
                    if (len > 0 && idx + len <= s.length()) {
                        String comp = s.substring(idx, idx + len);
                        components.add(comp);
                        className = comp;
                        idx += len;
                    } else {
                        break;
                    }
                } else {
                    idx++;
                }
            }

            if (idx < s.length() && s.charAt(idx) == 'E') {
                idx++; // skip E
            }

            if (components.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < components.size(); i++) {
                if (i > 0) sb.append("::");
                sb.append(components.get(i));
            }

            // Parse parameter types
            List<String> params = parseParamTypes(s, idx);
            sb.append('(');
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(params.get(i));
            }
            sb.append(')');
            return sb.toString();
        } else {
            // Non-nested name: _Z<len><name><params>
            if (Character.isDigit(s.charAt(idx))) {
                int len = 0;
                while (idx < s.length() && Character.isDigit(s.charAt(idx))) {
                    len = len * 10 + (s.charAt(idx) - '0');
                    idx++;
                }
                if (len > 0 && idx + len <= s.length()) {
                    String funcName = s.substring(idx, idx + len);
                    idx += len;
                    List<String> params = parseParamTypes(s, idx);
                    StringBuilder sb = new StringBuilder(funcName);
                    sb.append('(');
                    for (int i = 0; i < params.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(params.get(i));
                    }
                    sb.append(')');
                    return sb.toString();
                }
            }
        }
        return null;
    }

    private static List<String> parseParamTypes(String s, int startIdx) {
        List<String> params = new ArrayList<>();
        int i = startIdx;
        boolean isConst = false;
        boolean isPtr = false;
        boolean isRef = false;

        while (i < s.length()) {
            char c = s.charAt(i);

            if (c == 'K') {
                isConst = true;
                i++;
                continue;
            }
            if (c == 'P') {
                isPtr = true;
                i++;
                continue;
            }
            if (c == 'R') {
                isRef = true;
                i++;
                continue;
            }

            String type = null;
            switch (c) {
                case 'v': type = "void"; i++; break;
                case 'b': type = "bool"; i++; break;
                case 'c': type = "char"; i++; break;
                case 'h': type = "unsigned char"; i++; break;
                case 's': type = "short"; i++; break;
                case 't': type = "unsigned short"; i++; break;
                case 'i': type = "int"; i++; break;
                case 'j': type = "unsigned int"; i++; break;
                case 'l': type = "long"; i++; break;
                case 'm': type = "unsigned long"; i++; break;
                case 'x': type = "long long"; i++; break;
                case 'y': type = "unsigned long long"; i++; break;
                case 'f': type = "float"; i++; break;
                case 'd': type = "double"; i++; break;
                case 'z': type = "..."; i++; break;
                default:
                    // Check user-defined type length
                    if (Character.isDigit(c)) {
                        int len = 0;
                        while (i < s.length() && Character.isDigit(s.charAt(i))) {
                            len = len * 10 + (s.charAt(i) - '0');
                            i++;
                        }
                        if (len > 0 && i + len <= s.length()) {
                            type = s.substring(i, i + len);
                            i += len;
                        } else {
                            i++;
                        }
                    } else {
                        i++;
                    }
                    break;
            }

            if (type != null) {
                if ("void".equals(type) && params.isEmpty() && i >= s.length() && !isPtr && !isRef && !isConst) {
                    // Empty param list foo(void) -> foo()
                    break;
                }
                StringBuilder fullType = new StringBuilder();
                if (isConst) fullType.append("const ");
                fullType.append(type);
                if (isPtr) fullType.append('*');
                if (isRef) fullType.append('&');
                params.add(fullType.toString());

                isConst = false;
                isPtr = false;
                isRef = false;
            }
        }
        return params;
    }
}
