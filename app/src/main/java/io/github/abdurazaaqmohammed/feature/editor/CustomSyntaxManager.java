package io.github.abdurazaaqmohammed.feature.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;

/**
 * Manages user-imported syntax definitions (.tmLanguage.json, .json, and MT Manager .mtsx).
 * Converts .mtsx grammars into TextMate JSON grammars and exposes them to UnifiedEditorFragment.
 */
public final class CustomSyntaxManager {

    private static final String TAG = "CustomSyntaxManager";
    private static final String SYNTAX_DIR_NAME = "syntaxes";
    private static final String PREFS_NAME = "custom_syntax_prefs";
    private static final String KEY_HIDDEN_BUILTINS = "hidden_builtin_syntaxes";
    private static final int MAX_SYNTAX_BYTES = 2 * 1024 * 1024;

    private static volatile List<CustomSyntaxInfo> cachedSyntaxes;

    public static final class CustomSyntaxInfo {
        public final String displayName;
        public final String scopeName;
        public final List<String> extensions;
        public final File file;

        CustomSyntaxInfo(String displayName, String scopeName, List<String> extensions, File file) {
            this.displayName = displayName;
            this.scopeName = scopeName;
            this.extensions = extensions;
            this.file = file;
        }
    }

    private CustomSyntaxManager() {
    }

    public static void invalidateCache() {
        cachedSyntaxes = null;
    }

    @NonNull
    public static File getSyntaxDir(@NonNull Context context) {
        File dir = new File(context.getFilesDir(), SYNTAX_DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public static CustomSyntaxInfo importSyntax(@NonNull Context context, @NonNull Uri uri) throws Exception {
        String rawName = resolveDisplayName(context, uri);
        String content = readTextFromUri(context, uri);
        if (content.trim().isEmpty()) {
            throw new IllegalArgumentException("Syntax file is empty");
        }

        String lowerName = rawName.toLowerCase(Locale.ROOT);
        boolean fromMtsx = lowerName.endsWith(".mtsx") || looksLikeMtsx(content);
        JSONObject tmGrammar;
        JSONObject mtColors = null;
        String baseSlug;

        if (fromMtsx) {
            tmGrammar = convertMtsxToTextMate(content, rawName);
            baseSlug = sanitizeSlug(tmGrammar.optString("name", stripExtension(rawName)));
            mtColors = extractMtsxThemeColors(content, baseSlug, tmGrammar.getString("scopeName"));
        } else if (lowerName.endsWith(".json") || content.trim().startsWith("{")) {
            tmGrammar = new JSONObject(content);
            if (!tmGrammar.has("scopeName") || (!tmGrammar.has("patterns") && !tmGrammar.has("repository"))) {
                throw new IllegalArgumentException("Invalid TextMate JSON: missing 'scopeName' or 'patterns'");
            }
            baseSlug = sanitizeSlug(tmGrammar.optString("name", stripExtension(rawName)));
        } else {
            throw new IllegalArgumentException("Unsupported syntax format. Use .tmLanguage.json or .mtsx");
        }

        File syntaxDir = getSyntaxDir(context);
        File outFile = new File(syntaxDir, baseSlug + ".tmLanguage.json");
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            out.write(tmGrammar.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        File colorFile = new File(syntaxDir, baseSlug + ".mtcolors");
        if (mtColors != null) {
            try (FileOutputStream out = new FileOutputStream(colorFile)) {
                out.write(mtColors.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } else if (colorFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            colorFile.delete();
        }
        File mtsxFile = new File(syntaxDir, baseSlug + ".mtsx");
        if (fromMtsx) {
            try (FileOutputStream out = new FileOutputStream(mtsxFile)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
        } else if (mtsxFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mtsxFile.delete();
        }

        invalidateCache();
        List<String> exts = extractExtensions(tmGrammar);
        return new CustomSyntaxInfo(
                tmGrammar.optString("name", baseSlug),
                tmGrammar.getString("scopeName"),
                exts,
                outFile
        );
    }

    @NonNull
    public static List<CustomSyntaxInfo> listCustomSyntaxes(@NonNull Context context) {
        List<CustomSyntaxInfo> cached = cachedSyntaxes;
        if (cached != null) return cached;

        File dir = getSyntaxDir(context);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".tmLanguage.json") || name.endsWith(".json"));
        if (files == null || files.length == 0) {
            cachedSyntaxes = Collections.emptyList();
            return cachedSyntaxes;
        }

        List<CustomSyntaxInfo> result = new ArrayList<>();
        for (File f : files) {
            try {
                String jsonStr = readTextFromFile(f);
                JSONObject obj = new JSONObject(jsonStr);
                String scopeName = obj.optString("scopeName", "");
                if (scopeName.isEmpty()) continue;
                String name = obj.optString("name", stripExtension(f.getName()));
                result.add(new CustomSyntaxInfo(name, scopeName, extractExtensions(obj), f));
            } catch (Exception ignored) {
            }
        }
        cachedSyntaxes = Collections.unmodifiableList(result);
        return cachedSyntaxes;
    }

    @Nullable
    public static String findScopeForFileName(@NonNull Context context, @Nullable String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) return null;
        String ext = lower.substring(dot + 1);
        for (CustomSyntaxInfo info : listCustomSyntaxes(context)) {
            for (String supported : info.extensions) {
                if (ext.equalsIgnoreCase(supported)) {
                    return info.scopeName;
                }
            }
        }
        return null;
    }

    @NonNull
    public static Map<String, String> loadThemeColors(
            @NonNull Context context,
            @Nullable String scopeName,
            boolean dark) {
        if (scopeName == null || scopeName.isEmpty()) return Collections.emptyMap();
        for (CustomSyntaxInfo info : listCustomSyntaxes(context)) {
            if (scopeName.equals(info.scopeName) && info.file.isFile()) {
                File colorFile = new File(info.file.getParentFile(), baseNameFor(info.file) + ".mtcolors");
                if (!colorFile.isFile()) return Collections.emptyMap();
                try {
                    JSONObject root = new JSONObject(readTextFromFile(colorFile));
                    JSONObject modeObj = root.optJSONObject(dark ? "dark" : "light");
                    if (modeObj == null) return Collections.emptyMap();
                    Map<String, String> map = new LinkedHashMap<>();
                    java.util.Iterator<String> keys = modeObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        String v = modeObj.optString(k, "");
                        if (!v.isEmpty()) map.put(k, v);
                    }
                    return map;
                } catch (Exception ignored) {
                    return Collections.emptyMap();
                }
            }
        }
        return Collections.emptyMap();
    }

    @Nullable
    public static TextMateLanguage loadLanguage(
            @NonNull Context context,
            @NonNull String scopeName,
            @Nullable ThemeRegistry registry) {
        for (CustomSyntaxInfo info : listCustomSyntaxes(context)) {
            if (scopeName.equals(info.scopeName) && info.file.isFile()) {
                try {
                    IGrammarSource source = IGrammarSource.fromFile(info.file);
                    DefaultGrammarDefinition def = DefaultGrammarDefinition.withGrammarSource(source, info.displayName, info.scopeName);
                    return registry == null
                            ? TextMateLanguage.create(def, true)
                            : TextMateLanguage.create(def, GrammarRegistry.getInstance(), registry, true);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load custom syntax: " + scopeName, e);
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeMtsx(String content) {
        return content.contains("contains:") || content.contains("keywordsToRegex(") || content.contains("lineBackground:");
    }

    /**
     * Converts MT Manager's .mtsx syntax DSL into a TextMate .tmLanguage.json grammar.
     */
    static JSONObject convertMtsxToTextMate(String mtsx, String rawFileName) throws Exception {
        String clean = stripLineCommentsOutsideStringsAndRegex(mtsx);

        String displayName = stripExtension(rawFileName);
        List<String> fileTypes = new ArrayList<>();

        Matcher nameArrayM = Pattern.compile("\\bname\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(clean);
        if (nameArrayM.find()) {
            List<String> items = extractQuotedStrings(nameArrayM.group(1));
            if (!items.isEmpty()) {
                displayName = items.get(0);
                for (int i = 1; i < items.size(); i++) {
                    String ext = items.get(i).trim();
                    if (ext.startsWith(".")) ext = ext.substring(1);
                    if (!ext.isEmpty()) fileTypes.add(ext);
                }
            }
        } else {
            Matcher nameStrM = Pattern.compile("\\bname\\s*:\\s*\"([^\"]+)\"").matcher(clean);
            if (nameStrM.find()) {
                displayName = nameStrM.group(1);
            }
        }

        String slug = sanitizeSlug(displayName);
        String scopeName = "source.custom." + slug;

        JSONArray patterns = new JSONArray();

        // Line comment rule: comment: {startsWith: "#"}
        Matcher commentM = Pattern.compile("\\bcomment\\s*:\\s*\\{[^}]*startsWith\\s*:\\s*\"([^\"]+)\"").matcher(clean);
        if (commentM.find()) {
            String prefix = Pattern.quote(commentM.group(1));
            JSONObject rule = new JSONObject();
            rule.put("name", "comment.line." + slug);
            rule.put("match", prefix + ".*$");
            patterns.put(rule);
        }

        // Parse blocks inside contains: [ ... ]
        int containsIdx = clean.indexOf("contains:");
        if (containsIdx >= 0) {
            int openBracket = clean.indexOf('[', containsIdx);
            int closeBracket = findMatchingBracket(clean, openBracket, '[', ']');
            if (openBracket >= 0 && closeBracket > openBracket) {
                String body = clean.substring(openBracket + 1, closeBracket);
                List<String> entries = splitTopLevelBraces(body);
                for (String entry : entries) {
                    JSONObject rule = convertMtsxEntry(entry, slug);
                    if (rule != null) patterns.put(rule);
                }
            }
        }

        if (patterns.length() == 0) {
            throw new IllegalArgumentException("No valid syntax rules found in .mtsx file");
        }

        JSONObject root = new JSONObject();
        root.put("name", displayName);
        root.put("scopeName", scopeName);
        JSONArray ftArr = new JSONArray();
        for (String ft : fileTypes) ftArr.put(ft);
        root.put("fileTypes", ftArr);
        root.put("patterns", patterns);
        return root;
    }

    @Nullable
    private static JSONObject convertMtsxEntry(String entry, String slug) throws Exception {
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) return null;

        // Builtin string rules
        if (trimmed.contains("builtin:")) {
            if (trimmed.contains("QUOTED_STRING") || trimmed.contains("STRING")) {
                JSONObject rule = new JSONObject();
                rule.put("name", "string.quoted.double." + slug);
                rule.put("match", "\"(?:\\\\.|[^\"\\\\])*\"");
                return rule;
            }
            return null;
        }

        // Builtin number rule: {number: "10|16", iSuffixes: "t|s|l"}
        if (trimmed.contains("number:")) {
            JSONObject rule = new JSONObject();
            rule.put("name", "constant.numeric." + slug);
            rule.put("match", "(?<![\\w.])(?:-?0x[0-9a-fA-F]+[tTsSlL]?|-?\\d+(?:\\.\\d+)?[fFdDlLsStT]?)(?![\\w.])");
            return rule;
        }

        // Match rule: {match: <expr>, 0: "keyword", ...}
        int matchIdx = trimmed.indexOf("match:");
        if (matchIdx < 0) return null;

        String afterMatch = trimmed.substring(matchIdx + "match:".length());
        String regexExpr = extractRegexExpression(afterMatch);
        if (regexExpr == null || regexExpr.isEmpty()) return null;

        // Extract capture group -> scope mappings
        Map<Integer, String> captures = new LinkedHashMap<>();
        Matcher capM = Pattern.compile("(?:^|[,\\s])(\\d+)\\s*:\\s*\"([^\"]+)\"").matcher(trimmed);
        while (capM.find()) {
            int group = Integer.parseInt(capM.group(1));
            String mtsxColor = capM.group(2);
            captures.put(group, mapMtsxColorToTextMateScope(mtsxColor, slug));
        }

        JSONObject rule = new JSONObject();
        rule.put("match", regexExpr);

        if (captures.size() == 1 && captures.containsKey(0)) {
            rule.put("name", captures.get(0));
        } else if (!captures.isEmpty()) {
            JSONObject capObj = new JSONObject();
            for (Map.Entry<Integer, String> e : captures.entrySet()) {
                JSONObject inner = new JSONObject();
                inner.put("name", e.getValue());
                capObj.put(String.valueOf(e.getKey()), inner);
            }
            rule.put("captures", capObj);
        } else {
            rule.put("name", "keyword.other." + slug);
        }
        return rule;
    }

    @Nullable
    private static String extractRegexExpression(String expr) {
        // An expression can concatenate /regex/, "string", and keywordsToRegex("...") with '+'
        StringBuilder combined = new StringBuilder();
        int i = 0;
        int n = expr.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(expr.charAt(i))) i++;
            if (i >= n) break;
            char ch = expr.charAt(i);
            if (ch == ',' || ch == '}') break;
            if (ch == '+') {
                i++;
                continue;
            }
            if (ch == '/') {
                int end = findClosingSlash(expr, i + 1);
                if (end < 0) break;
                combined.append(expr, i + 1, end);
                i = end + 1;
            } else if (ch == '"') {
                int end = findClosingQuote(expr, i + 1);
                if (end < 0) break;
                combined.append(unescapeJavaString(expr.substring(i + 1, end)));
                i = end + 1;
            } else if (expr.startsWith("keywordsToRegex", i)) {
                int openP = expr.indexOf('(', i);
                int closeP = findMatchingBracket(expr, openP, '(', ')');
                if (openP < 0 || closeP < 0) break;
                String inner = expr.substring(openP + 1, closeP);
                List<String> chunks = extractQuotedStrings(inner);
                String kwRegex = buildKeywordsRegex(chunks);
                combined.append(kwRegex);
                i = closeP + 1;
            } else {
                break;
            }
        }
        return combined.length() == 0 ? null : combined.toString();
    }

    private static String buildKeywordsRegex(List<String> chunks) {
        List<String> words = new ArrayList<>();
        for (String chunk : chunks) {
            for (String token : chunk.trim().split("\\s+")) {
                if (!token.isEmpty()) words.add(token);
            }
        }
        if (words.isEmpty()) return "";
        // Sort longest first so prefixes like "const" don't shadow "const-string/jumbo"
        words.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder sb = new StringBuilder("(?<![\\w-])(?:");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(escapeRegex(words.get(i)));
        }
        sb.append(")(?![\\w/-])");
        return sb.toString();
    }

    private static String escapeRegex(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("\\.^$|?*+()[]{}".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String mapMtsxColorToTextMateScope(String mtsxColor, String slug) {
        String c = mtsxColor.toLowerCase(Locale.ROOT);
        if (c.contains("comment")) return "comment.line." + slug;
        if (c.contains("string")) return "string.quoted.double." + slug;
        if (c.contains("keyword2")) return "keyword.control." + slug;
        if (c.contains("keyword")) return "keyword.other." + slug;
        if (c.contains("variable")) return "variable.parameter." + slug;
        if (c.contains("number")) return "constant.numeric." + slug;
        if (c.contains("type")) return "entity.name.type." + slug;
        if (c.contains("operator")) return "keyword.operator." + slug;
        if (c.contains("label")) return "entity.name.label." + slug;
        return "entity.name." + c + "." + slug;
    }

    @Nullable
    static JSONObject extractMtsxThemeColors(String mtsx, String slug, String scopeName) {
        try {
            String clean = stripLineCommentsOutsideStringsAndRegex(mtsx);
            Matcher colorsM = Pattern.compile("(?m)(?:^|[,\\s])colors\\s*:\\s*\\[").matcher(clean);
            if (!colorsM.find()) return null;
            int openIdx = colorsM.end() - 1;
            int closeIdx = findMatchingBracket(clean, openIdx, '[', ']');
            if (closeIdx <= openIdx) return null;

            String lineBgKey = null;
            Matcher lbM = Pattern.compile("\\blineBackground\\s*:\\s*\\{[^}]*\\bcolor\\s*:\\s*\"([^\"]+)\"").matcher(clean);
            if (lbM.find()) {
                lineBgKey = lbM.group(1);
            }

            String body = clean.substring(openIdx + 1, closeIdx);
            JSONObject lightObj = new JSONObject();
            JSONObject darkObj = new JSONObject();
            Matcher tokM = Pattern.compile("\"([^\"]+)\"|#([0-9a-fA-F]{6,8})").matcher(body);
            String currentKey = null;
            List<String> currentColors = new ArrayList<>();
            while (tokM.find()) {
                if (tokM.group(1) != null) {
                    recordMtsxColorEntry(currentKey, lineBgKey, currentColors, slug, lightObj, darkObj);
                    currentKey = tokM.group(1);
                    currentColors.clear();
                } else if (currentKey != null) {
                    String hex = normalizeMtsxHex(tokM.group(2));
                    if (hex != null) currentColors.add(hex);
                }
            }
            recordMtsxColorEntry(currentKey, lineBgKey, currentColors, slug, lightObj, darkObj);
            if (lightObj.length() == 0 && darkObj.length() == 0) return null;

            JSONObject root = new JSONObject();
            root.put("scopeName", scopeName);
            root.put("light", lightObj);
            root.put("dark", darkObj);
            return root;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void recordMtsxColorEntry(
            @Nullable String key,
            @Nullable String lineBgKey,
            List<String> colors,
            String slug,
            JSONObject lightObj,
            JSONObject darkObj) throws Exception {
        if (key == null || colors.isEmpty() || key.equals(lineBgKey)) return;
        String tmScope = mapMtsxColorToTextMateScope(key, slug);
        String lightHex = colors.get(0);
        String darkHex = colors.size() >= 2 ? colors.get(1) : lightHex;
        lightObj.put(tmScope, lightHex);
        darkObj.put(tmScope, darkHex);
    }

    @Nullable
    private static String normalizeMtsxHex(String rawHex) {
        if (rawHex == null) return null;
        String upper = rawHex.toUpperCase(Locale.ROOT);
        if (upper.length() == 6) return "#" + upper;
        if (upper.length() == 8) return "#" + upper.substring(2) + upper.substring(0, 2);
        return null;
    }

    private static String stripLineCommentsOutsideStringsAndRegex(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inQuote = false;
        boolean inRegex = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (inQuote) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(text.charAt(++i));
                } else if (c == '"') {
                    inQuote = false;
                }
            } else if (inRegex) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(text.charAt(++i));
                } else if (c == '/') {
                    inRegex = false;
                } else if (c == '\n') {
                    inRegex = false;
                }
            } else {
                if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                    while (i < n && text.charAt(i) != '\n') i++;
                    if (i < n) out.append('\n');
                } else if (c == '"') {
                    inQuote = true;
                    out.append(c);
                } else if (c == '/') {
                    inRegex = true;
                    out.append(c);
                } else {
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static List<String> splitTopLevelBraces(String body) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inQuote = false;
        boolean inRegex = false;
        int n = body.length();
        for (int i = 0; i < n; i++) {
            char c = body.charAt(i);
            if (inQuote) {
                if (c == '\\') i++;
                else if (c == '"') inQuote = false;
                continue;
            }
            if (inRegex) {
                if (c == '\\') i++;
                else if (c == '/' || c == '\n') inRegex = false;
                continue;
            }
            if (c == '"') { inQuote = true; continue; }
            if (c == '/') { inRegex = true; continue; }
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    list.add(body.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return list;
    }

    private static int findMatchingBracket(String s, int openIdx, char openCh, char closeCh) {
        if (openIdx < 0 || openIdx >= s.length()) return -1;
        int depth = 0;
        boolean inQuote = false;
        boolean inRegex = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuote) {
                if (c == '\\') i++;
                else if (c == '"') inQuote = false;
                continue;
            }
            if (inRegex) {
                if (c == '\\') i++;
                else if (c == '/' || c == '\n') inRegex = false;
                continue;
            }
            if (c == '"') inQuote = true;
            else if (c == '/') inRegex = true;
            else if (c == openCh) depth++;
            else if (c == closeCh) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findClosingSlash(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') i++;
            else if (c == '/') return i;
            else if (c == '\n') return -1;
        }
        return -1;
    }

    private static int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') i++;
            else if (c == '"') return i;
        }
        return -1;
    }

    private static List<String> extractQuotedStrings(String s) {
        List<String> list = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '"') {
                int end = findClosingQuote(s, i + 1);
                if (end < 0) break;
                list.add(unescapeJavaString(s.substring(i + 1, end)));
                i = end + 1;
            } else {
                i++;
            }
        }
        return list;
    }

    private static String unescapeJavaString(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static List<String> extractExtensions(JSONObject obj) {
        List<String> exts = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("fileTypes");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String ext = arr.optString(i, "").trim();
                if (ext.startsWith(".")) ext = ext.substring(1);
                if (!ext.isEmpty()) exts.add(ext);
            }
        }
        return exts;
    }

    private static String resolveDisplayName(Context ctx, Uri uri) {
        String name = "syntax_" + System.currentTimeMillis() + ".tmLanguage.json";
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && !n.trim().isEmpty()) name = n.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private static String readTextFromUri(Context ctx, Uri uri) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Cannot open input stream");
            return readAll(in);
        }
    }

    private static String readTextFromFile(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int len;
        while ((len = in.read(buf)) > 0) {
            total += len;
            if (total > MAX_SYNTAX_BYTES) {
                throw new IllegalArgumentException("Syntax file exceeds 2MB limit");
            }
            out.write(buf, 0, len);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String baseNameFor(File file) {
        String fileName = file.getName();
        return fileName.endsWith(".tmLanguage.json")
                ? fileName.substring(0, fileName.length() - ".tmLanguage.json".length())
                : stripExtension(fileName);
    }

    public static boolean isBuiltInHidden(@NonNull Context context, @NonNull String label) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> hidden = prefs.getStringSet(KEY_HIDDEN_BUILTINS, null);
        return hidden != null && hidden.contains(label);
    }

    public static void hideBuiltInSyntax(@NonNull Context context, @NonNull String label) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> prev = prefs.getStringSet(KEY_HIDDEN_BUILTINS, null);
        Set<String> next = prev == null ? new HashSet<>() : new HashSet<>(prev);
        next.add(label);
        prefs.edit().putStringSet(KEY_HIDDEN_BUILTINS, next).apply();
    }

    public static boolean deleteCustomSyntax(@NonNull Context context, @NonNull String scopeName) {
        for (CustomSyntaxInfo info : listCustomSyntaxes(context)) {
            if (scopeName.equals(info.scopeName)) {
                String base = baseNameFor(info.file);
                File dir = info.file.getParentFile();
                boolean deleted = info.file.delete();
                if (dir != null) {
                    File colors = new File(dir, base + ".mtcolors");
                    if (colors.exists()) colors.delete();
                    File rawMtsx = new File(dir, base + ".mtsx");
                    if (rawMtsx.exists()) rawMtsx.delete();
                }
                invalidateCache();
                return deleted;
            }
        }
        return false;
    }

    @NonNull
    public static String suggestedExportFileName(@Nullable String displayName, boolean asMtsx) {
        String safe = (displayName == null ? "syntax" : displayName.trim())
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (safe.isEmpty()) safe = "syntax";
        return safe + (asMtsx ? ".mtsx" : ".tmLanguage.json");
    }

    public static void exportSyntaxToUri(
            @NonNull Context context,
            @NonNull String scopeName,
            @NonNull String displayName,
            boolean isCustom,
            boolean asMtsx,
            @NonNull Uri destUri) throws Exception {
        String outputContent;
        if (isCustom) {
            CustomSyntaxInfo target = null;
            for (CustomSyntaxInfo info : listCustomSyntaxes(context)) {
                if (scopeName.equals(info.scopeName)) {
                    target = info;
                    break;
                }
            }
            if (target == null || !target.file.isFile()) {
                throw new IllegalStateException("Custom syntax file not found: " + scopeName);
            }
            File parent = target.file.getParentFile();
            String base = baseNameFor(target.file);
            File rawMtsx = parent == null ? null : new File(parent, base + ".mtsx");
            if (asMtsx && rawMtsx != null && rawMtsx.isFile()) {
                outputContent = readTextFromFile(rawMtsx);
            } else {
                String jsonStr = readTextFromFile(target.file);
                if (!asMtsx) {
                    outputContent = jsonStr;
                } else {
                    JSONObject grammar = new JSONObject(jsonStr);
                    Map<String, String> light = loadThemeColors(context, scopeName, false);
                    Map<String, String> dark = loadThemeColors(context, scopeName, true);
                    outputContent = convertTextMateToMtsx(grammar, displayName, light, dark);
                }
            }
        } else {
            String assetPath = findBuiltInGrammarAsset(context, scopeName);
            if (assetPath == null) {
                throw new IllegalStateException("Built-in grammar asset not found: " + scopeName);
            }
            String jsonStr;
            try (InputStream in = context.getAssets().open(assetPath)) {
                jsonStr = readAll(in);
            }
            if (!asMtsx) {
                outputContent = jsonStr;
            } else {
                outputContent = convertTextMateToMtsx(
                        new JSONObject(jsonStr), displayName,
                        Collections.emptyMap(), Collections.emptyMap());
            }
        }

        try (OutputStream out = context.getContentResolver().openOutputStream(destUri)) {
            if (out == null) throw new IllegalStateException("Cannot open output stream");
            out.write(outputContent.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    @Nullable
    private static String findBuiltInGrammarAsset(Context context, String scopeName) {
        try (InputStream in = context.getAssets().open("languages.json")) {
            JSONObject root = new JSONObject(readAll(in));
            JSONArray langs = root.optJSONArray("languages");
            if (langs == null) return null;
            for (int i = 0; i < langs.length(); i++) {
                JSONObject item = langs.optJSONObject(i);
                if (item != null && scopeName.equals(item.optString("scopeName", ""))) {
                    return item.optString("grammar", null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed reading languages.json", e);
        }
        return null;
    }

    @NonNull
    static String convertTextMateToMtsx(
            JSONObject grammar,
            String displayName,
            Map<String, String> lightColors,
            Map<String, String> darkColors) {
        String name = displayName != null && !displayName.trim().isEmpty()
                ? displayName.trim()
                : grammar.optString("name", "Syntax");
        List<String> exts = extractExtensions(grammar);
        if (exts.isEmpty()) {
            String scope = grammar.optString("scopeName", "");
            int dot = scope.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < scope.length()) {
                exts.add(scope.substring(dot + 1));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n    name: [\"").append(escapeJavaString(name)).append('"');
        for (String ext : exts) {
            sb.append(", \".").append(escapeJavaString(ext)).append('"');
        }
        sb.append("]\n");

        Map<String, String[]> colorPairs = new LinkedHashMap<>();
        if (lightColors != null) {
            for (Map.Entry<String, String> e : lightColors.entrySet()) {
                String mt = reverseMapTextMateScopeToMtColor(e.getKey());
                colorPairs.put(mt, new String[]{e.getValue(), darkColors != null ? darkColors.getOrDefault(e.getKey(), e.getValue()) : e.getValue()});
            }
        }
        if (darkColors != null) {
            for (Map.Entry<String, String> e : darkColors.entrySet()) {
                String mt = reverseMapTextMateScopeToMtColor(e.getKey());
                if (!colorPairs.containsKey(mt)) {
                    colorPairs.put(mt, new String[]{e.getValue(), e.getValue()});
                }
            }
        }
        if (!colorPairs.isEmpty()) {
            sb.append("    colors: [\n");
            for (Map.Entry<String, String[]> e : colorPairs.entrySet()) {
                sb.append("        \"").append(escapeJavaString(e.getKey())).append("\", ")
                        .append(e.getValue()[0]).append(", ").append(e.getValue()[1]).append('\n');
            }
            sb.append("    ]\n");
        }

        JSONObject repo = grammar.optJSONObject("repository");
        List<String> ruleLines = new ArrayList<>();
        String[] lineCommentPrefix = new String[1];
        collectMtsxRules(grammar.optJSONArray("patterns"), repo, new HashSet<>(), ruleLines, lineCommentPrefix);

        if (lineCommentPrefix[0] != null && !lineCommentPrefix[0].isEmpty()) {
            sb.append("    comment: {startsWith: \"")
                    .append(escapeJavaString(lineCommentPrefix[0])).append("\"}\n");
        }
        sb.append("    contains: [\n");
        for (String rule : ruleLines) {
            sb.append("        ").append(rule).append('\n');
        }
        sb.append("    ]\n}\n");
        return sb.toString();
    }

    private static void collectMtsxRules(
            @Nullable JSONArray patterns,
            @Nullable JSONObject repo,
            Set<String> visitedIncludes,
            List<String> out,
            String[] lineCommentPrefix) {
        if (patterns == null) return;
        for (int i = 0; i < patterns.length(); i++) {
            JSONObject obj = patterns.optJSONObject(i);
            if (obj == null) continue;
            String include = obj.optString("include", "");
            if (include.startsWith("#")) {
                String key = include.substring(1);
                if (repo != null && visitedIncludes.add(key)) {
                    JSONObject ref = repo.optJSONObject(key);
                    if (ref != null) {
                        JSONArray sub = new JSONArray();
                        sub.put(ref);
                        collectMtsxRules(sub, repo, visitedIncludes, out, lineCommentPrefix);
                    }
                }
                continue;
            }
            JSONArray nested = obj.optJSONArray("patterns");
            if (nested != null && !obj.has("match") && !obj.has("begin")) {
                collectMtsxRules(nested, repo, visitedIncludes, out, lineCommentPrefix);
                continue;
            }
            String match = obj.optString("match", "");
            String scopeName = obj.optString("name", "");
            if (!match.isEmpty()) {
                if (lineCommentPrefix[0] == null && scopeName.contains("comment.line")) {
                    String prefix = inferLineCommentPrefix(match);
                    if (prefix != null) lineCommentPrefix[0] = prefix;
                }
                StringBuilder rule = new StringBuilder("{match: ");
                rule.append(formatMtsxRegex(match));
                JSONObject captures = obj.optJSONObject("captures");
                if (captures != null && captures.length() > 0) {
                    java.util.Iterator<String> capKeys = captures.keys();
                    while (capKeys.hasNext()) {
                        String group = capKeys.next();
                        JSONObject cap = captures.optJSONObject(group);
                        String capScope = cap != null ? cap.optString("name", scopeName) : scopeName;
                        rule.append(", ").append(group).append(": \"")
                                .append(reverseMapTextMateScopeToMtColor(capScope)).append('"');
                    }
                } else {
                    rule.append(", 0: \"").append(reverseMapTextMateScopeToMtColor(scopeName)).append('"');
                }
                rule.append('}');
                out.add(rule.toString());
            } else {
                String begin = obj.optString("begin", "");
                String end = obj.optString("end", "");
                if (!begin.isEmpty() && !end.isEmpty()) {
                    out.add("{start: {match: " + formatMtsxRegex(begin)
                            + "}, end: {match: " + formatMtsxRegex(end)
                            + "}, color: \"" + reverseMapTextMateScopeToMtColor(scopeName) + "\"}");
                }
            }
        }
    }

    private static String formatMtsxRegex(String regex) {
        if (!regex.contains("\n") && !regex.contains("\r")) {
            return "/" + regex.replace("/", "\\/") + "/";
        }
        return "\"" + escapeJavaString(regex) + "\"";
    }

    @Nullable
    private static String inferLineCommentPrefix(String match) {
        String clean = match.replace("\\s*", "").replace("^[ \\t]*", "").replace("^", "");
        for (String candidate : new String[]{"//", "#", "--", ";"}) {
            if (clean.startsWith(candidate) || clean.startsWith(Pattern.quote(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static String reverseMapTextMateScopeToMtColor(@Nullable String scope) {
        if (scope == null || scope.isEmpty()) return "default";
        String s = scope.toLowerCase(Locale.ROOT);
        if (s.contains("comment")) return "comment";
        if (s.contains("string")) return "string";
        if (s.contains("numeric")) return "number";
        if (s.contains("operator")) return "operator";
        if (s.contains("label")) return "label";
        if (s.contains("variable")) return "variable";
        if (s.contains("type") || s.contains("tag")) return "type";
        if (s.contains("keyword.control")) return "keyword2";
        if (s.contains("keyword") || s.contains("storage")) return "keyword";
        return "default";
    }

    private static String escapeJavaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String stripExtension(String name) {
        int idx = name.indexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String sanitizeSlug(String s) {
        String slug = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "custom_syntax" : slug;
    }
}
