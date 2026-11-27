package io.github.abdurazaaqmohammed.feature.apk;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;

import io.github.abdurazaaqmohammed.util.misc.AppLogs;
import io.github.abdurazaaqmohammed.feature.apk.FastDexPatch;

import io.github.abdurazaaqmohammed.util.io.FileUtils;

import android.content.Context;
import android.util.Base64;

import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.VersionMap;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.reandroid.apk.APKLogger;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.R;

public class OverlayInjectorUtil {

    public static final String TOAST_HELPER = "showMPManagerToast";
    public static final String DIALOG_HELPER = "showMPManagerDialog";
    public static final String DONTSHOW_KEY = "mpmanager_overlay_dontshow";
    public static final String SHOWONCE_KEY = "mpmanager_overlay_shown_once";
    public static final String VIDEO_ASSET_NAME = "mpvid.mp4";
    public static final long MAX_VIDEO_BYTES = 25L * 1024L * 1024L;

    public static class ToastOptions {
        public String message = "";
        public boolean longDuration = true;
        public int gravity = -1;
        public int xOffset;
        public int yOffset;
        public boolean html;
        public boolean base64;
        public String hookMethod = "onCreate";
        public boolean protectCode;
    }

    public static class AdvWidget {
        public String kind = "text";
        public String text = "";
        public float textSizeSp = 16f;
        public int textColor;
        public int fontStyle;
        public String font = "";
        public String fontPath = "";
        public String imageB64 = "";
        public String btnAction = "Dismiss";
        public String url = "";
        public int btnBg;
        public int btnBg2;
        public float btnCornerRadiusDp;
        public float btnBorderWidthDp;
        public int btnBorderColor = -1;
        public int btnPaddingDp;
        public boolean btnAnim;
        public List<Integer> btnAnimColors;
        public int btnAnimSpeedMs;
        public boolean btnAnimRainbow;
        public int leftDp;
        public int topDp;
    }

    public static class DialogOptions {
        public String title = "";
        public String message = "";
        public String positive = "OK";
        public String negative = "";
        public String neutral = "";
        public String positiveUrl;
        public String negativeUrl;
        public String neutralUrl;
        public boolean cancelable = true;
        public boolean html;
        public boolean base64;
        public boolean dontShowAgain;
        public boolean showOnce;
        public boolean draggable;
        public String imageBase64;
        public String videoPath;
        public boolean bgEnabled;
        public int bgColor1 = -1;
        public int bgColor2;
        public boolean bgGradient;
        public int bgOrientation;
        public float cornerRadiusDp;
        public float borderWidthDp;
        public int borderColor = -1;
        public boolean animBorder;
        public int animColorA = -1;
        public int animColorB = -1;
        public List<Integer> animExtraColors;
        public boolean rainbowAnim;
        public int animSpeedMs = 2500;
        public int titleColor;
        public int msgColor;
        public int btnColor;
        public boolean advanced;
        public String dlgFont = "";
        public String dlgFontPath = "";
        public List<AdvWidget> widgets;
        public boolean countdown;
        public int countdownSeconds = 3;
        public String hookMethod = "onCreate";
        public boolean protectCode;
    }

    public static AdvWidget copyWidget(AdvWidget w) {
        if (w == null) return null;
        AdvWidget c = new AdvWidget();
        c.kind = w.kind;
        c.text = w.text;
        c.textSizeSp = w.textSizeSp;
        c.textColor = w.textColor;
        c.fontStyle = w.fontStyle;
        c.font = w.font;
        c.fontPath = w.fontPath;
        c.imageB64 = w.imageB64;
        c.btnAction = w.btnAction;
        c.url = w.url;
        c.btnBg = w.btnBg;
        c.btnBg2 = w.btnBg2;
        c.btnCornerRadiusDp = w.btnCornerRadiusDp;
        c.btnBorderWidthDp = w.btnBorderWidthDp;
        c.btnBorderColor = w.btnBorderColor;
        c.btnPaddingDp = w.btnPaddingDp;
        c.btnAnim = w.btnAnim;
        c.btnAnimColors = w.btnAnimColors == null ? null : new ArrayList<>(w.btnAnimColors);
        c.btnAnimSpeedMs = w.btnAnimSpeedMs;
        c.btnAnimRainbow = w.btnAnimRainbow;
        c.leftDp = w.leftDp;
        c.topDp = w.topDp;
        return c;
    }

    public static File addOverlayToActivities(Context context, File inputApk,
                                              List<String> activityClassNames,
                                              ToastOptions toast, DialogOptions dialog,
                                              APKLogger logger) throws Exception {
        if (toast == null && dialog == null) throw new IOException("Nothing to inject");
        debugInit(context);
        debug("input=" + inputApk.getAbsolutePath());
        debug("activities=" + activityClassNames);
        try {
            Set<String> targetEntries = findDexEntriesForClasses(context, inputApk, activityClassNames, logger);
            if (targetEntries.isEmpty()) {
                throw new IOException("Could not locate the selected activities in any dex file");
            }
            debug("dexEntries=" + targetEntries);
            boolean protectCode = isProtectCode(toast, dialog);
            ProtectShared shared = protectCode ? ProtectShared.create() : null;
            String videoAsset = shared != null ? shared.videoAssetName : VIDEO_ASSET_NAME;
            Map<String, byte[]> fontBytes = collectFontAssets(dialog);
            Map<String, String> fontAssets = new LinkedHashMap<>();
            int fi = 0;
            for (String path : fontBytes.keySet()) {
                fontAssets.put(path, shared != null ? shared.nextFontAssetName() : ("mpfont_" + (fi++) + ".ttf"));
            }
            if (!fontAssets.isEmpty() && logger != null) {
                logger.logMessage(context.getString(R.string.logger_embedding_fonts, fontAssets.size()));
            }
            Set<String> descriptors = new LinkedHashSet<>();
            for (String className : activityClassNames) {
                descriptors.add("L" + className.replace('.', '/') + ";");
            }
            File workDir = new File(context.getCacheDir(), "add_overlay_" + System.currentTimeMillis());
            workDir.mkdirs();
            try {
                Map<String, File> replacements = new LinkedHashMap<>();
                Map<String, File> additions = new LinkedHashMap<>();
                int ai = 0;
                for (Map.Entry<String, byte[]> fe : fontBytes.entrySet()) {
                    String assetName = fontAssets.get(fe.getKey());
                    if (assetName == null) continue;
                    File tmp = new File(workDir, "font_" + (ai++) + ".tmp");
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write(fe.getValue());
                    }
                    additions.put("assets/" + assetName, tmp);
                }
                if (hasVideo(dialog)) {
                    File vid = new File(dialog.videoPath);
                    if (!vid.isFile() || vid.length() <= 0) {
                        throw new IOException("Selected video file does not exist: " + dialog.videoPath);
                    }
                    if (vid.length() > MAX_VIDEO_BYTES) {
                        throw new IOException("Selected video exceeds 25 MB limit");
                    }
                    additions.put("assets/" + videoAsset, vid);
                    if (logger != null) {
                        logger.logMessage(context.getString(R.string.logger_embedding_video, vid.getName()));
                    }
                }
                BaksmaliOptions baksmaliOptions =
                        FastDexPatch.defaultBaksmaliOptions();
                for (String entry : targetEntries) {
                    byte[] origBytes = FastDexPatch.readDexBytes(inputApk, entry);
                    DexBackedDexFile dex =
                            new DexBackedDexFile(
                                    Opcodes.getDefault(), origBytes);
                    int api = detectDexApi(inputApk, entry);
                    File patchDir = new File(workDir, "patch_" + entry.replace('/', '_').replace('.', '_'));
                    Map<String, File> smaliFiles =
                            FastDexPatch.disassembleClasses(context, dex, descriptors, patchDir, baksmaliOptions, logger);
                    if (smaliFiles.isEmpty()) continue;
                    ProtectSpec ps = shared != null ? ProtectSpec.create(shared) : null;
                    boolean[] helperEmitted = new boolean[1];
                    List<File> touched = new ArrayList<>();
                    boolean changed = false;
                    for (Map.Entry<String, File> se : smaliFiles.entrySet()) {
                        String className = FastDexPatch.descriptorToClassName(se.getKey());
                        try {
                            if (patchOneSmali(context, se.getValue(), patchDir, className, toast, dialog, fontAssets, ps, helperEmitted, logger, touched)) {
                                changed = true;
                            } else {
                                se.getValue().delete();
                            }
                        } catch (IOException e) {
                            throw new IOException("While patching " + className + ": " + e.getMessage(), e);
                        }
                    }
                    if (!changed) continue;
                    File miniDex = FastDexPatch.assembleMiniDex(context, patchDir, api, logger);
                    byte[] merged = FastDexPatch.mergeDex(dex, miniDex, api);
                    // entry can contain '/' (assets/lspatch/*.dex) - flatten
                    // so workDir/<entry>.merged is writable.
                    File mergedFile = new File(workDir, entry.replaceAll("[^A-Za-z0-9]", "_") + ".merged");
                    try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
                        fos.write(merged);
                    }
                    replacements.put(entry, mergedFile);
                    if (logger != null) logger.logMessage(context.getString(R.string.logger_injected_overlay, entry));
                }
                if (replacements.isEmpty()) {
                    throw new IOException("Could not patch any dex file (activities may already be patched)");
                }
                File outputFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                        FilenameUtils.getBaseName(inputApk.getName()) + "_overlay.apk"));
                ApkZipAlignUtil.rebuildApk(inputApk, outputFile, replacements, null, additions, null);
                if (logger != null) logger.logMessage(context.getString(R.string.logger_saved_to, outputFile.getName()));
                return outputFile;
            } finally {
                deleteDirectory(workDir);
            }
        } catch (Exception e) {
            debug("FAILED: " + e);
            throw new IOException(e.getMessage() + "\nDebug log: " + debugPath(), e);
        }
    }

    private static File debugFile;

    private static void debugInit(Context context) {
        try {
            debugFile = new File(AppLogs.getLogsDir(),
                    "overlay_debug_" + System.currentTimeMillis() + ".log");
            FileWriter fw = new FileWriter(debugFile);
            fw.write("overlay injector debug log\n");
            fw.close();
        } catch (Exception ignored) {
            debugFile = null;
        }
    }

    private static void debug(String s) {
        if (debugFile == null) return;
        try {
            FileWriter fw = new FileWriter(debugFile, true);
            fw.write(s + "\n");
            fw.close();
        } catch (Exception ignored) {
        }
    }

    static String debugPath() {
        return debugFile == null ? "unavailable" : debugFile.getAbsolutePath();
    }

    private static String shorten(String smali) {
        String[] lines = smali.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("const-string") && line.length() > 220) {
                sb.append(line.substring(0, 200)).append("...<")
                        .append(line.length() - 200).append(" more chars>\n");
            } else {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    static String escapeSmali(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c == '\b') sb.append("\\b");
            else if (c == '\f') sb.append("\\f");
            else if (c < 0x20 || c > 0x7E) {
                sb.append(String.format("\\u%04x", (int) c));
            } else sb.append(c);
        }
        return sb.toString();
    }

    private static final char[] B64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    static boolean isProtectCode(ToastOptions toast, DialogOptions dialog) {
        return (dialog != null && dialog.protectCode) || (toast != null && toast.protectCode);
    }

    static String resolveHookMethod(ToastOptions toast, DialogOptions dialog) {
        if (dialog != null && "onResume".equals(dialog.hookMethod)) return "onResume";
        if (toast != null && "onResume".equals(toast.hookMethod)) return "onResume";
        return "onCreate";
    }

    static final class ProtectShared {
        final String dontShowKey;
        final String showOnceKey;
        final String videoAssetName;
        final java.security.SecureRandom rng = new java.security.SecureRandom();
        private final Set<String> used = new LinkedHashSet<>();
        private int seq;

        private ProtectShared(String dontShowKey, String showOnceKey, String videoAssetName) {
            this.dontShowKey = dontShowKey;
            this.showOnceKey = showOnceKey;
            this.videoAssetName = videoAssetName;
        }

        static ProtectShared create() {
            java.security.SecureRandom r = new java.security.SecureRandom();
            return new ProtectShared("k_" + randIdent(r, 8), "s_" + randIdent(r, 8), randIdent(r, 7) + ".dat");
        }

        String nextFontAssetName() {
            return nextIdent(7) + ".dat";
        }

        String nextIdent(int len) {
            for (;;) {
                String s = randIdent(rng, len) + Integer.toString(seq++, 36);
                if (used.add(s)) return s;
            }
        }

        private static String randIdent(java.security.SecureRandom r, int len) {
            final String alpha = "abcdefghijklmnopqrstuvwxyz";
            final String alnum = "abcdefghijklmnopqrstuvwxyz0123456789";
            StringBuilder sb = new StringBuilder(len);
            sb.append(alpha.charAt(r.nextInt(alpha.length())));
            // Ensure at least one digit so identifier never collides with Java/Dalvik keywords.
            sb.append((char) ('0' + r.nextInt(10)));
            for (int i = 2; i < len; i++) {
                sb.append(alnum.charAt(r.nextInt(alnum.length())));
            }
            return sb.toString();
        }
    }

    static final class ProtectSpec {
        final ProtectShared shared;
        final String packageInternal;
        final String helperClassDesc;
        final String helperClassDot;
        final String entryMethod;
        final String toastMethod;
        final String dialogMethod;
        final String decodeMethod;
        final String origRename;
        final String guardField;
        final String dontShowKey;
        final String showOnceKey;
        final String videoAssetName;
        final String fontWalkDesc;
        final int wrapperXorKey;

        private ProtectSpec(ProtectShared shared, String pkgInternal, String helperClsName) {
            this.shared = shared;
            this.packageInternal = pkgInternal;
            this.helperClassDesc = "L" + pkgInternal + "/" + helperClsName + ";";
            this.helperClassDot = (pkgInternal + "/" + helperClsName).replace('/', '.');
            this.entryMethod = shared.nextIdent(6);
            this.toastMethod = shared.nextIdent(6);
            this.dialogMethod = shared.nextIdent(6);
            this.decodeMethod = shared.nextIdent(6);
            this.origRename = shared.nextIdent(6);
            this.guardField = shared.nextIdent(6);
            this.dontShowKey = shared.dontShowKey;
            this.showOnceKey = shared.showOnceKey;
            this.videoAssetName = shared.videoAssetName;
            this.fontWalkDesc = nextType();
            this.wrapperXorKey = 0x11 + shared.rng.nextInt(0x6d);
        }

        static ProtectSpec create(ProtectShared shared) {
            return new ProtectSpec(shared, shared.nextIdent(4) + "/" + shared.nextIdent(5), shared.nextIdent(6));
        }

        static ProtectSpec createStandalone() {
            return create(ProtectShared.create());
        }

        String nextType() {
            return "L" + packageInternal + "/" + shared.nextIdent(6) + ";";
        }

        int nextKey() {
            return 0x11 + shared.rng.nextInt(0x6d);
        }
    }

    private static String encodeBase64NoWrap(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder out = new StringBuilder(((data.length + 2) / 3) * 4);
        for (int i = 0; i < data.length; i += 3) {
            int b0 = data[i] & 0xFF;
            int b1 = (i + 1 < data.length) ? (data[i + 1] & 0xFF) : -1;
            int b2 = (i + 2 < data.length) ? (data[i + 2] & 0xFF) : -1;
            out.append(B64_ALPHABET[b0 >>> 2]);
            out.append(B64_ALPHABET[((b0 & 0x03) << 4) | (b1 >= 0 ? (b1 >>> 4) : 0)]);
            out.append(b1 >= 0 ? B64_ALPHABET[((b1 & 0x0F) << 2) | (b2 >= 0 ? (b2 >>> 6) : 0)] : '=');
            out.append(b2 >= 0 ? B64_ALPHABET[b2 & 0x3F] : '=');
        }
        return out.toString();
    }

    static String b64(String text) {
        if (text == null) text = "";
        return encodeBase64NoWrap(text.getBytes(StandardCharsets.UTF_8));
    }

    static String xorB64(String raw, int key) {
        if (raw == null) raw = "";
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (bytes[i] ^ key);
        }
        return encodeBase64NoWrap(bytes);
    }

    static String stripMpSource(String smali) {
        if (smali == null) return null;
        return smali.replaceFirst("(?m)^\\.source\\s+\"mp[^\"]*\"\\r?\\n", "");
    }

    static String decodeHelperMethodSmali(ProtectSpec ps) {
        return ".method public static " + ps.decodeMethod + "(Ljava/lang/String;I)Ljava/lang/String;\n"
                + "    .locals 4\n"
                + "    const/4 v0, 0x0\n"
                + "    invoke-static {p0, v0}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B\n"
                + "    move-result-object v0\n"
                + "    array-length v1, v0\n"
                + "    const/4 v2, 0x0\n"
                + "    :mp_dec_loop\n"
                + "    if-ge v2, v1, :mp_dec_done\n"
                + "    aget-byte v3, v0, v2\n"
                + "    xor-int/2addr v3, p1\n"
                + "    int-to-byte v3, v3\n"
                + "    aput-byte v3, v0, v2\n"
                + "    add-int/lit8 v2, v2, 0x1\n"
                + "    goto :mp_dec_loop\n"
                + "    :mp_dec_done\n"
                + "    new-instance v1, Ljava/lang/String;\n"
                + "    invoke-direct {v1, v0}, Ljava/lang/String;-><init>([B)V\n"
                + "    return-object v1\n"
                + ".end method\n";
    }

    static String loadStringSmali(ProtectSpec ps, String raw, int reg, int tmpReg) {
        if (ps == null) {
            return "    const-string v" + reg + ", \"" + escapeSmali(raw) + "\"\n";
        }
        int key = ps.nextKey();
        String enc = xorB64(raw, key);
        return "    const-string v" + reg + ", \"" + enc + "\"\n"
                + "    const/16 v" + tmpReg + ", 0x" + Integer.toHexString(key) + "\n"
                + "    invoke-static {v" + reg + ", v" + tmpReg + "}, " + ps.helperClassDesc + "->"
                + ps.decodeMethod + "(Ljava/lang/String;I)Ljava/lang/String;\n"
                + "    move-result-object v" + reg + "\n";
    }

    static String loadTextSmali(String raw, boolean base64, boolean html, int textReg, int tmpReg) {
        return loadTextSmali(raw, base64, html, textReg, tmpReg, null);
    }

    static String loadTextSmali(String raw, boolean base64, boolean html, int textReg, int tmpReg, ProtectSpec ps) {
        StringBuilder sb = new StringBuilder();
        if (ps != null) {
            sb.append(loadStringSmali(ps, raw == null ? "" : raw, textReg, tmpReg));
        } else if (base64) {
            String content = b64(raw);
            sb.append("    const-string v").append(textReg).append(", \"").append(content).append("\"\n");
            sb.append("    const/4 v").append(tmpReg).append(", 0x0\n");
            sb.append("    invoke-static {v").append(textReg).append(", v").append(tmpReg)
                    .append("}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B\n");
            sb.append("    move-result-object v").append(textReg).append("\n");
            sb.append("    new-instance v").append(tmpReg).append(", Ljava/lang/String;\n");
            sb.append("    invoke-direct {v").append(tmpReg).append(", v").append(textReg)
                    .append("}, Ljava/lang/String;-><init>([B)V\n");
            sb.append("    move-object v").append(textReg).append(", v").append(tmpReg).append("\n");
        } else {
            sb.append("    const-string v").append(textReg).append(", \"").append(escapeSmali(raw)).append("\"\n");
        }
        if (html) {
            sb.append("    invoke-static {v").append(textReg)
                    .append("}, Landroid/text/Html;->fromHtml(Ljava/lang/String;)Landroid/text/Spanned;\n");
            sb.append("    move-result-object v").append(textReg).append("\n");
        }
        return sb.toString();
    }

    static String buildToastSmali(boolean longDuration, int gravity, int xOffset, int yOffset) {
        StringBuilder sb = new StringBuilder();
        sb.append("    const/4 v2, 0x").append(longDuration ? "1" : "0").append("\n");
        sb.append("    invoke-static {p0, v1, v2}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;\n");
        sb.append("    move-result-object v0\n");
        if (gravity >= 0) {
            sb.append("    const/16 v1, 0x").append(Integer.toHexString(gravity)).append("\n");
            sb.append("    const/16 v2, 0x").append(Integer.toHexString(xOffset & 0xFFFF)).append("\n");
            sb.append("    const/16 v3, 0x").append(Integer.toHexString(yOffset & 0xFFFF)).append("\n");
            sb.append("    invoke-virtual {v0, v1, v2, v3}, Landroid/widget/Toast;->setGravity(III)V\n");
        }
        sb.append("    invoke-virtual {v0}, Landroid/widget/Toast;->show()V\n");
        sb.append("    return-void\n");
        return sb.toString();
    }

    static String toastHelperSmali(ToastOptions opts) {
        return toastHelperSmali(opts, null, TOAST_HELPER);
    }

    static String toastHelperSmali(ToastOptions opts, ProtectSpec ps, String methodName) {
        String header = ps != null
                ? ".method public static " + methodName + "(Landroid/content/Context;)V\n"
                : ".method private " + methodName + "()V\n";
        return header +
                "    .locals 5\n" +
                loadTextSmali(opts.message == null ? "" : opts.message, opts.base64, opts.html, 1, 2, ps) +
                buildToastSmali(opts.longDuration, opts.gravity, opts.xOffset, opts.yOffset) +
                ".end method\n";
    }

    private static void appendDpToPx(StringBuilder sb, int dpFloatBits, int valueReg, int metricsReg, int unitReg) {
        sb.append("    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;\n");
        sb.append("    move-result-object v").append(unitReg).append("\n");
        sb.append("    invoke-virtual {v").append(unitReg).append("}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;\n");
        sb.append("    move-result-object v").append(metricsReg).append("\n");
        sb.append("    const/4 v").append(unitReg).append(", 0x1\n");
        sb.append("    const v").append(valueReg).append(", 0x").append(Integer.toHexString(dpFloatBits)).append("\n");
        sb.append("    invoke-static {v").append(unitReg).append(", v").append(valueReg).append(", v").append(metricsReg)
                .append("}, Landroid/util/TypedValue;->applyDimension(IFLandroid/util/DisplayMetrics;)F\n");
        sb.append("    move-result v").append(valueReg).append("\n");
    }

    private static String waveCreateSmali(DialogOptions opts, String waveRgb, int tmpReg) {
        int bg = opts.bgEnabled ? opts.bgColor1 : 0;
        float borderDp = opts.borderWidthDp > 0 ? opts.borderWidthDp : 2f;
        StringBuilder sb = new StringBuilder();
        appendDpToPx(sb, Float.floatToRawIntBits(opts.cornerRadiusDp), 2, 3, 1);
        sb.append("    move v").append(tmpReg).append(", v2\n");
        appendDpToPx(sb, Float.floatToRawIntBits(borderDp), 2, 3, 1);
        sb.append("    move v3, v2\n");
        sb.append("    move v2, v").append(tmpReg).append("\n");
        sb.append("    const v1, 0x").append(Integer.toHexString(bg)).append("\n");
        sb.append("    new-instance v4, ").append(waveRgb).append("\n");
        sb.append("    invoke-direct {v4, v1, v2, v3}, ").append(waveRgb).append("-><init>(IFF)V\n");
        sb.append("    const-wide v1, 0x").append(Long.toHexString(Math.max(100, opts.animSpeedMs))).append("L\n");
        sb.append("    iput-wide v1, v4, ").append(waveRgb).append("->durationMs:J\n");
        sb.append(waveColorsSmali(waveRgb, dialogAnimColors(opts), opts.rainbowAnim));
        return sb.toString();
    }

    private static String waveColorsSmali(String waveRgb, int[] colors, boolean rainbow) {
        StringBuilder sb = new StringBuilder();
        if (!rainbow) {
            appendAnimColors(sb, colors, 1, 3, 2);
            sb.append("    iput-object v3, v4, ").append(waveRgb).append("->colors:[I\n");
        }
        sb.append("    const/4 v1, 0x").append(rainbow ? "1" : "0").append("\n");
        sb.append("    iput-boolean v1, v4, ").append(waveRgb).append("->rainbow:Z\n");
        return sb.toString();
    }

    static String dialogHelperSmali(String classDescriptor, DialogOptions opts,
                                    String urlListener, String urlViewListener,
                                    String noshowListener, String blinkListener,
                                    String dismissListener, Map<String, String> fontAssets,
                                    String waveRgb) {
        String base = classDescriptor.substring(0, classDescriptor.length() - 1);
        String dragListener = opts != null && opts.draggable ? base + "$mpDrag;" : null;
        String videoHelper = hasVideo(opts) ? base + "$mpVideo;" : null;
        return dialogHelperSmali(classDescriptor, opts, urlListener, urlViewListener,
                noshowListener, blinkListener, dismissListener, fontAssets, waveRgb,
                dragListener, videoHelper);
    }

    static String dialogHelperSmali(String classDescriptor, DialogOptions opts,
                                    String urlListener, String urlViewListener,
                                    String noshowListener, String blinkListener,
                                    String dismissListener, Map<String, String> fontAssets,
                                    String waveRgb, String dragListener, String videoHelper) {
        String base = classDescriptor.substring(0, classDescriptor.length() - 1);
        String countdownHelper = opts != null && opts.countdown ? base + "$mpCountdown;" : null;
        return dialogHelperSmali(classDescriptor, opts, urlListener, urlViewListener,
                noshowListener, blinkListener, dismissListener, fontAssets, waveRgb,
                dragListener, videoHelper, countdownHelper);
    }

    static String dialogHelperSmali(String classDescriptor, DialogOptions opts,
                                    String urlListener, String urlViewListener,
                                    String noshowListener, String blinkListener,
                                    String dismissListener, Map<String, String> fontAssets,
                                    String waveRgb, String dragListener, String videoHelper,
                                    String countdownHelper) {
        return dialogHelperSmali(classDescriptor, opts, urlListener, urlViewListener,
                noshowListener, blinkListener, dismissListener, fontAssets, waveRgb,
                dragListener, videoHelper, countdownHelper, null, DIALOG_HELPER);
    }

    static String dialogHelperSmali(String classDescriptor, DialogOptions opts,
                                    String urlListener, String urlViewListener,
                                    String noshowListener, String blinkListener,
                                    String dismissListener, Map<String, String> fontAssets,
                                    String waveRgb, String dragListener, String videoHelper,
                                    String countdownHelper, ProtectSpec ps, String helperMethodName) {
        boolean titleColored = opts.titleColor != 0;
        boolean msgColored = opts.msgColor != 0;
        boolean btnColored = opts.btnColor != 0;
        String title = titleColored ? fontWrap(opts.title, opts.titleColor) : opts.title;
        String message = msgColored ? fontWrap(opts.message, opts.msgColor) : opts.message;
        boolean titleHtml = opts.html || titleColored;
        boolean msgHtml = opts.html || msgColored;
        boolean useBg = opts.bgEnabled || opts.borderWidthDp > 0 || opts.animBorder;
        float borderDp = opts.borderWidthDp;
        if (opts.animBorder && borderDp <= 0) borderDp = 2f;
        boolean useWave = opts.animBorder && borderDp > 0 && waveRgb != null;
        boolean useAnim = !useWave && opts.animBorder && blinkListener != null && borderDp > 0;
        boolean useVideo = hasVideo(opts) && videoHelper != null;
        boolean useImage = !useVideo && opts.imageBase64 != null && !opts.imageBase64.isEmpty();
        boolean useAdvanced = opts.advanced && opts.widgets != null && !opts.widgets.isEmpty();
        boolean useCountdown = !useAdvanced && opts.countdown && opts.countdownSeconds > 0 && countdownHelper != null;
        boolean useDlgFont = needsFontWalk(opts);
        boolean useDrag = opts.draggable && dragListener != null;
        StringBuilder sb = new StringBuilder();
        if (ps != null) {
            sb.append(".method public static ").append(helperMethodName).append("(Landroid/content/Context;)V\n");
        } else {
            sb.append(".method private ").append(helperMethodName).append("()V\n");
        }
        sb.append("    .locals ").append((useAdvanced || useCountdown) ? 8 : (useAnim || useDlgFont || useWave) ? 6 : 5).append("\n");
        if (opts.dontShowAgain) {
            sb.append("    invoke-static {p0}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;\n");
            sb.append("    move-result-object v0\n");
            sb.append(loadStringSmali(ps, ps != null ? ps.dontShowKey : DONTSHOW_KEY, 1, 2));
            sb.append("    const/4 v2, 0x0\n");
            sb.append("    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z\n");
            sb.append("    move-result v2\n");
            sb.append("    if-eqz v2, :show_dlg\n");
            sb.append("    return-void\n");
            sb.append("    :show_dlg\n");
        }
        if (opts.showOnce) {
            sb.append("    invoke-static {p0}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;\n");
            sb.append("    move-result-object v0\n");
            sb.append(loadStringSmali(ps, ps != null ? ps.showOnceKey : SHOWONCE_KEY, 1, 2));
            sb.append("    const/4 v2, 0x0\n");
            sb.append("    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z\n");
            sb.append("    move-result v2\n");
            sb.append("    if-eqz v2, :mp_show_once\n");
            sb.append("    return-void\n");
            sb.append("    :mp_show_once\n");
            sb.append("    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    const/4 v3, 0x1\n");
            sb.append("    invoke-interface {v2, v1, v3}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    invoke-interface {v2}, Landroid/content/SharedPreferences$Editor;->apply()V\n");
        }
        sb.append("    new-instance v0, Landroid/app/AlertDialog$Builder;\n");
        sb.append("    invoke-direct {v0, p0}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V\n");
        if (useAdvanced) {
            sb.append(advancedViewSmali(opts, urlViewListener, dismissListener, blinkListener, fontAssets, waveRgb, videoHelper, ps));
        } else {
            if (title != null && !title.isEmpty()) {
            sb.append(loadTextSmali(title, opts.base64, titleHtml, 1, 2, ps));
            sb.append("    invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;\n");
            sb.append("    move-result-object v0\n");
        }
        if (message != null && !message.isEmpty()) {
            sb.append(loadTextSmali(message, opts.base64, msgHtml, 1, 2, ps));
            sb.append("    invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setMessage(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;\n");
            sb.append("    move-result-object v0\n");
        }
        appendButtonSmali(sb, btnColored ? fontWrap(opts.positive, opts.btnColor) : opts.positive,
                opts.positiveUrl, "setPositiveButton", opts, urlListener, btnColored, ps);
        appendButtonSmali(sb, btnColored ? fontWrap(opts.negative, opts.btnColor) : opts.negative,
                opts.negativeUrl, "setNegativeButton", opts, urlListener, btnColored, ps);
        if (opts.dontShowAgain && noshowListener != null) {
            sb.append(loadStringSmali(ps, "Don't show again", 1, 2));
            sb.append("    new-instance v2, ").append(noshowListener).append("\n");
            sb.append("    invoke-direct {v2, p0}, ").append(noshowListener).append("-><init>(Landroid/content/Context;)V\n");
            sb.append("    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog$Builder;->setNeutralButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;\n");
            sb.append("    move-result-object v0\n");
        } else {
            appendButtonSmali(sb, btnColored ? fontWrap(opts.neutral, opts.btnColor) : opts.neutral,
                    opts.neutralUrl, "setNeutralButton", opts, urlListener, btnColored, ps);
        }
        if (!opts.cancelable) {
            sb.append("    const/4 v1, 0x0\n");
            sb.append("    invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setCancelable(Z)Landroid/app/AlertDialog$Builder;\n");
            sb.append("    move-result-object v0\n");
        }
        }
        if (useBg) {
            if (useWave) {
                sb.append(waveCreateSmali(opts, waveRgb, useAdvanced ? 7 : 5));
            } else {
            sb.append("    new-instance v4, Landroid/graphics/drawable/GradientDrawable;\n");
            sb.append("    invoke-direct {v4}, Landroid/graphics/drawable/GradientDrawable;-><init>()V\n");
            if (opts.cornerRadiusDp > 0) {
                appendDpToPx(sb, Float.floatToRawIntBits(opts.cornerRadiusDp), 2, 3, 1);
                sb.append("    invoke-virtual {v4, v2}, Landroid/graphics/drawable/GradientDrawable;->setCornerRadius(F)V\n");
            }
            if (opts.bgEnabled) {
                if (opts.bgGradient) {
                    sb.append("    const/4 v2, 0x2\n");
                    sb.append("    new-array v1, v2, [I\n");
                    sb.append("    const v2, 0x").append(Integer.toHexString(opts.bgColor1)).append("\n");
                    sb.append("    const/4 v3, 0x0\n");
                    sb.append("    aput v2, v1, v3\n");
                    sb.append("    const v2, 0x").append(Integer.toHexString(opts.bgColor2)).append("\n");
                    sb.append("    const/4 v3, 0x1\n");
                    sb.append("    aput v2, v1, v3\n");
                    sb.append("    sget-object v2, Landroid/graphics/drawable/GradientDrawable$Orientation;->")
                            .append(orientationName(opts.bgOrientation)).append(":Landroid/graphics/drawable/GradientDrawable$Orientation;\n");
                    sb.append("    invoke-virtual {v4, v2}, Landroid/graphics/drawable/GradientDrawable;->setOrientation(Landroid/graphics/drawable/GradientDrawable$Orientation;)V\n");
                    sb.append("    invoke-virtual {v4, v1}, Landroid/graphics/drawable/GradientDrawable;->setColors([I)V\n");
                } else {
                    sb.append("    const v1, 0x").append(Integer.toHexString(opts.bgColor1)).append("\n");
                    sb.append("    invoke-virtual {v4, v1}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V\n");
                }
            } else {
                sb.append("    const v1, 0x0\n");
                sb.append("    invoke-virtual {v4, v1}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V\n");
            }
            if (borderDp > 0) {
                appendDpToPx(sb, Float.floatToRawIntBits(borderDp), 2, 3, 1);
                sb.append("    float-to-int v2, v2\n");
                sb.append("    const v1, 0x").append(Integer.toHexString(opts.borderColor)).append("\n");
                sb.append("    invoke-virtual {v4, v2, v1}, Landroid/graphics/drawable/GradientDrawable;->setStroke(II)V\n");
            }
            }
        }
        if (useCountdown) {
            sb.append(buildCountdownContainerSmali(opts, useVideo ? videoHelper : null, useImage, ps));
        } else if (useVideo && !useAdvanced) {
            sb.append(loadStringSmali(ps, ps != null ? ps.videoAssetName : VIDEO_ASSET_NAME, 1, 2));
            sb.append("    invoke-static {p0, v1}, ").append(videoHelper).append("->create(Landroid/content/Context;Ljava/lang/String;)Landroid/view/View;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    invoke-virtual {v0, v2}, Landroid/app/AlertDialog$Builder;->setView(Landroid/view/View;)Landroid/app/AlertDialog$Builder;\n");
            sb.append("    move-result-object v0\n");
        } else if (useImage && !useAdvanced) {
            sb.append("    const-string v1, \"").append(opts.imageBase64).append("\"\n");
            sb.append("    const/4 v2, 0x0\n");
            sb.append("    invoke-static {v1, v2}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B\n");
            sb.append("    move-result-object v1\n");
            sb.append("    const/4 v2, 0x0\n");
            sb.append("    array-length v3, v1\n");
            sb.append("    invoke-static {v1, v2, v3}, Landroid/graphics/BitmapFactory;->decodeByteArray([BII)Landroid/graphics/Bitmap;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    new-instance v2, Landroid/widget/ImageView;\n");
            sb.append("    invoke-direct {v2, p0}, Landroid/widget/ImageView;-><init>(Landroid/content/Context;)V\n");
            sb.append("    const/4 v3, 0x1\n");
            sb.append("    invoke-virtual {v2, v3}, Landroid/widget/ImageView;->setAdjustViewBounds(Z)V\n");
            sb.append("    invoke-virtual {v2, v1}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V\n");
            sb.append("    invoke-virtual {v0, v2}, Landroid/app/AlertDialog$Builder;->setView(Landroid/view/View;)Landroid/app/AlertDialog$Builder;\n");
            sb.append("    move-result-object v0\n");
        }
        sb.append("    invoke-virtual {v0}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;\n");
        boolean resultTaken = false;
        if (useAdvanced && dismissListener != null) {
            sb.append("    move-result-object v0\n");
            sb.append("    move-object v5, v0\n");
            sb.append("    sput-object v5, ").append(dismissListener).append("->dlg:Landroid/content/DialogInterface;\n");
            resultTaken = true;
        }
        if ((useDlgFont || useBg || useDrag || useCountdown) && !resultTaken) {
            sb.append("    move-result-object v0\n");
        }
        if (useCountdown) {
            sb.append("    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    new-instance v2, Landroid/os/Handler;\n");
            sb.append("    invoke-direct {v2, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V\n");
            sb.append("    new-instance v3, ").append(countdownHelper).append("\n");
            appendConstInt(sb, 1, Math.max(1, opts.countdownSeconds));
            sb.append("    invoke-direct {v3, v0, v7, v2, v1}, ").append(countdownHelper)
                    .append("-><init>(Landroid/app/AlertDialog;Landroid/widget/TextView;Landroid/os/Handler;I)V\n");
            sb.append("    const-wide v5, 0x3e8L\n");
            sb.append("    invoke-virtual {v2, v3, v5, v6}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z\n");
            sb.append("    move-result v1\n");
        }
        if (useDlgFont) {
            String dlgAsset = fontAssetName(opts.dlgFontPath, fontAssets);
            if (dlgAsset != null) {
                sb.append(loadStringSmali(ps, dlgAsset, 3, 1));
                sb.append("    invoke-virtual {p0}, Landroid/content/Context;->getAssets()Landroid/content/res/AssetManager;\n");
                sb.append("    move-result-object v2\n");
                sb.append("    invoke-static {v2, v3}, Landroid/graphics/Typeface;->createFromAsset(Landroid/content/res/AssetManager;Ljava/lang/String;)Landroid/graphics/Typeface;\n");
                sb.append("    move-result-object v1\n");
                sb.append("    if-nez v1, :dlg_tf_ok\n");
                sb.append("    sget-object v1, Landroid/graphics/Typeface;->DEFAULT:Landroid/graphics/Typeface;\n");
                sb.append("    :dlg_tf_ok\n");
            } else if (opts.dlgFont != null && !opts.dlgFont.isEmpty()) {
                sb.append(loadStringSmali(ps, opts.dlgFont, 2, 3));
                sb.append("    const/4 v3, 0x0\n");
                sb.append("    invoke-static {v2, v3}, Landroid/graphics/Typeface;->create(Ljava/lang/String;I)Landroid/graphics/Typeface;\n");
                sb.append("    move-result-object v1\n");
            } else {
                sb.append("    sget-object v1, Landroid/graphics/Typeface;->DEFAULT:Landroid/graphics/Typeface;\n");
            }
            sb.append("    invoke-virtual {v0}, Landroid/app/AlertDialog;->getWindow()Landroid/view/Window;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    invoke-virtual {v2}, Landroid/view/Window;->getDecorView()Landroid/view/View;\n");
            sb.append("    move-result-object v2\n");
            String fwType = ps != null ? ps.fontWalkDesc : fontWalkType(classDescriptor);
            sb.append("    invoke-static {v2, v1}, ").append(fwType).append("->applyAll(Landroid/view/View;Landroid/graphics/Typeface;)V\n");
        }
        if (useDrag) {
            sb.append("    invoke-virtual {v0}, Landroid/app/AlertDialog;->getWindow()Landroid/view/Window;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    if-eqz v1, :mp_drag_skip\n");
            sb.append("    invoke-virtual {v1}, Landroid/view/Window;->getDecorView()Landroid/view/View;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    if-eqz v2, :mp_drag_skip\n");
            sb.append("    new-instance v3, ").append(dragListener).append("\n");
            sb.append("    invoke-direct {v3, v1}, ").append(dragListener).append("-><init>(Landroid/view/Window;)V\n");
            sb.append("    invoke-virtual {v2, v3}, Landroid/view/View;->setOnTouchListener(Landroid/view/View$OnTouchListener;)V\n");
            sb.append("    :mp_drag_skip\n");
        }
        if (useBg) {
            sb.append("    invoke-virtual {v0}, Landroid/app/AlertDialog;->getWindow()Landroid/view/Window;\n");
            sb.append("    move-result-object v0\n");
            sb.append("    invoke-virtual {v0, v4}, Landroid/view/Window;->setBackgroundDrawable(Landroid/graphics/drawable/Drawable;)V\n");
            sb.append("    invoke-virtual {v0}, Landroid/view/Window;->getAttributes()Landroid/view/WindowManager$LayoutParams;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    const/4 v2, -0x2\n");
            sb.append("    iput v2, v1, Landroid/view/WindowManager$LayoutParams;->width:I\n");
            sb.append("    iput v2, v1, Landroid/view/WindowManager$LayoutParams;->height:I\n");
            sb.append("    invoke-virtual {v0, v1}, Landroid/view/Window;->setAttributes(Landroid/view/WindowManager$LayoutParams;)V\n");
        }
        if (useAnim) {
            sb.append("    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    new-instance v5, Landroid/os/Handler;\n");
            sb.append("    invoke-direct {v5, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V\n");
            int[] animColors = dialogAnimColors(opts);
            appendAnimColors(sb, animColors, 2, 1, 3);
            sb.append("    const v0, 0x").append(Integer.toHexString(Math.max(100, opts.animSpeedMs))).append("\n");
            sb.append("    const v3, 0x").append(Integer.toHexString(Float.floatToRawIntBits(borderDp))).append("\n");
            sb.append("    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    invoke-virtual {v2}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    const/4 v0, 0x1\n");
            sb.append("    invoke-static {v0, v3, v2}, Landroid/util/TypedValue;->applyDimension(IFLandroid/util/DisplayMetrics;)F\n");
            sb.append("    move-result v3\n");
            sb.append("    float-to-int v3, v3\n");
            sb.append("    const v0, 0x").append(Integer.toHexString(Math.max(100, opts.animSpeedMs))).append("\n");
            sb.append("    new-instance v2, ").append(blinkListener).append("\n");
            sb.append("    invoke-direct {v2, v4, v5, v1}, ").append(blinkListener).append("-><init>(Landroid/graphics/drawable/GradientDrawable;Landroid/os/Handler;[I)V\n");
            sb.append("    const v0, 0x").append(Integer.toHexString(Math.max(100, opts.animSpeedMs))).append("\n");
            sb.append("    iput v0, v2, ").append(blinkListener).append("->ms:I\n");
            sb.append("    iput v3, v2, ").append(blinkListener).append("->w:I\n");
            sb.append("    const-wide v0, 0x").append(Long.toHexString(Math.max(100, opts.animSpeedMs))).append("L\n");
            sb.append("    invoke-virtual {v5, v2, v0, v1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z\n");
            sb.append("    move-result v0\n");
        }
        sb.append("    return-void\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    static String orientationName(int orientation) {
        if (orientation == 1) return "LEFT_RIGHT";
        if (orientation == 2) return "TL_BR";
        if (orientation == 3) return "BL_TR";
        return "TOP_BOTTOM";
    }

    static int[] rainbowColors() {
        int[] out = new int[12];
        for (int i = 0; i < out.length; i++) {
            out[i] = hsvToArgb((i * 30) % 360, 1f, 1f);
        }
        return out;
    }

    static int[] dialogAnimColors(DialogOptions opts) {
        if (opts.rainbowAnim) return rainbowColors();
        List<Integer> all = new ArrayList<>();
        all.add(opts.animColorA != -1 ? opts.animColorA : 0xFFFFFFFF);
        all.add(opts.animColorB != -1 ? opts.animColorB : 0xFF000000);
        if (opts.animExtraColors != null) all.addAll(opts.animExtraColors);
        int[] out = new int[all.size()];
        for (int i = 0; i < out.length; i++) out[i] = all.get(i);
        return out;
    }

    static int[] buttonAnimColors(DialogOptions opts, int borderColor) {
        if (opts.rainbowAnim) return rainbowColors();
        List<Integer> all = new ArrayList<>();
        all.add(opts.animColorA != -1 ? opts.animColorA : borderColor);
        all.add(opts.animColorB != -1 ? opts.animColorB : 0xFF000000);
        if (opts.animExtraColors != null) all.addAll(opts.animExtraColors);
        int[] out = new int[all.size()];
        for (int i = 0; i < out.length; i++) out[i] = all.get(i);
        return out;
    }

    static int[] widgetWaveColors(AdvWidget w) {
        if (w.btnAnimColors != null && !w.btnAnimColors.isEmpty()) {
            int[] out = new int[w.btnAnimColors.size()];
            for (int i = 0; i < out.length; i++) out[i] = w.btnAnimColors.get(i);
            return out;
        }
        return new int[]{0xFF0000FF, 0xFFFF0000};
    }

    private static int hsvToArgb(float hue, float sat, float val) {
        float h = ((hue % 360) + 360) % 360 / 60f;
        int i = (int) Math.floor(h) % 6;
        float f = h - (float) Math.floor(h);
        float p = val * (1 - sat);
        float q = val * (1 - f * sat);
        float t = val * (1 - (1 - f) * sat);
        float r;
        float g;
        float b;
        switch (i) {
            case 0: r = val; g = t; b = p; break;
            case 1: r = q; g = val; b = p; break;
            case 2: r = p; g = val; b = t; break;
            case 3: r = p; g = q; b = val; break;
            case 4: r = t; g = p; b = val; break;
            default: r = val; g = p; b = q; break;
        }
        return 0xFF000000 | ((int) (r * 255 + 0.5f) << 16)
                | ((int) (g * 255 + 0.5f) << 8) | (int) (b * 255 + 0.5f);
    }

    private static void appendConstInt(StringBuilder sb, int reg, int value) {
        if (value >= -8 && value <= 7) {
            sb.append("    const/4 v").append(reg).append(", ");
            if (value < 0) sb.append("-0x").append(Integer.toHexString(-value)).append("\n");
            else sb.append("0x").append(Integer.toHexString(value)).append("\n");
        } else if (value >= -32768 && value <= 32767) {
            sb.append("    const/16 v").append(reg).append(", 0x")
                    .append(Integer.toHexString(value & 0xFFFF)).append("\n");
        } else {
            sb.append("    const v").append(reg).append(", 0x")
                    .append(Integer.toHexString(value)).append("\n");
        }
    }

    private static void appendAnimColors(StringBuilder sb, int[] colors, int arrayReg, int tmpReg, int idxReg) {
        appendConstInt(sb, arrayReg, colors.length);
        sb.append("    new-array v").append(tmpReg).append(", v").append(arrayReg).append(", [I\n");
        for (int i = 0; i < colors.length; i++) {
            sb.append("    const v").append(arrayReg).append(", 0x").append(Integer.toHexString(colors[i])).append("\n");
            appendConstInt(sb, idxReg, i);
            sb.append("    aput v").append(arrayReg).append(", v").append(tmpReg).append(", v").append(idxReg).append("\n");
        }
    }

    static String colorHex(int color) {
        if (((color >> 24) & 0xFF) == 0xFF) {
            return String.format("#%06X", color & 0xFFFFFF);
        }
        return String.format("#%08X", color);
    }

    static String fontWrap(String text, int color) {
        if (text == null) text = "";
        return "<font color=\"" + colorHex(color) + "\">" + text + "</font>";
    }

    private static void appendButtonSmali(StringBuilder sb, String text, String url, String setter,
                                          DialogOptions opts, String urlListener, boolean forceHtml, ProtectSpec ps) {
        if (text == null || text.isEmpty()) return;
        sb.append(loadTextSmali(text, opts.base64, forceHtml || opts.html, 1, 2, ps));
        if (url != null && !url.trim().isEmpty() && urlListener != null) {
            sb.append(loadStringSmali(ps, url.trim(), 3, 2));
            sb.append("    new-instance v2, ").append(urlListener).append("\n");
            sb.append("    invoke-direct {v2, p0, v3}, ").append(urlListener).append("-><init>(Landroid/content/Context;Ljava/lang/String;)V\n");
        } else {
            sb.append("    const/4 v2, 0x0\n");
        }
        sb.append("    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog$Builder;->").append(setter)
                .append("(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;\n");
        sb.append("    move-result-object v0\n");
    }

    static String urlListenerSmali(String listenerType) {
        return urlListenerSmali(listenerType, null);
    }

    static String urlListenerSmali(String listenerType, ProtectSpec ps) {
        return ".class public " + listenerType + "\n"
                + ".super Ljava/lang/Object;\n"
                + (ps == null ? ".source \"mpUrl.java\"\n" : "")
                + ".implements Landroid/content/DialogInterface$OnClickListener;\n"
                + "\n"
                + ".field private ctx:Landroid/content/Context;\n"
                + ".field private url:Ljava/lang/String;\n"
                + "\n"
                + ".method public constructor <init>(Landroid/content/Context;Ljava/lang/String;)V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + listenerType + "->ctx:Landroid/content/Context;\n"
                + "    iput-object p2, p0, " + listenerType + "->url:Ljava/lang/String;\n"
                + "    return-void\n"
                + ".end method\n"
                + "\n"
                + ".method public onClick(Landroid/content/DialogInterface;I)V\n"
                + "    .locals 4\n"
                + "    iget-object v0, p0, " + listenerType + "->ctx:Landroid/content/Context;\n"
                + "    iget-object v1, p0, " + listenerType + "->url:Ljava/lang/String;\n"
                + "    invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;\n"
                + "    move-result-object v1\n"
                + loadStringSmali(ps, "android.intent.action.VIEW", 3, 2)
                + "    new-instance v2, Landroid/content/Intent;\n"
                + "    invoke-direct {v2, v3, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;Landroid/net/Uri;)V\n"
                + "    invoke-virtual {v0, v2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V\n"
                + "    return-void\n"
                + ".end method\n";
    }

    static String urlViewListenerSmali(String listenerType) {
        return urlViewListenerSmali(listenerType, null);
    }

    static String urlViewListenerSmali(String listenerType, ProtectSpec ps) {
        return ".class public " + listenerType + "\n"
                + ".super Ljava/lang/Object;\n"
                + (ps == null ? ".source \"mpUrlView.java\"\n" : "")
                + ".implements Landroid/view/View$OnClickListener;\n"
                + "\n"
                + ".field private ctx:Landroid/content/Context;\n"
                + ".field private url:Ljava/lang/String;\n"
                + "\n"
                + ".method public constructor <init>(Landroid/content/Context;Ljava/lang/String;)V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + listenerType + "->ctx:Landroid/content/Context;\n"
                + "    iput-object p2, p0, " + listenerType + "->url:Ljava/lang/String;\n"
                + "    return-void\n"
                + ".end method\n"
                + "\n"
                + ".method public onClick(Landroid/view/View;)V\n"
                + "    .locals 4\n"
                + "    iget-object v0, p0, " + listenerType + "->ctx:Landroid/content/Context;\n"
                + "    iget-object v1, p0, " + listenerType + "->url:Ljava/lang/String;\n"
                + "    invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;\n"
                + "    move-result-object v1\n"
                + loadStringSmali(ps, "android.intent.action.VIEW", 3, 2)
                + "    new-instance v2, Landroid/content/Intent;\n"
                + "    invoke-direct {v2, v3, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;Landroid/net/Uri;)V\n"
                + "    invoke-virtual {v0, v2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V\n"
                + "    return-void\n"
                + ".end method\n";
    }

    static String noshowListenerSmali(String listenerType) {
        return noshowListenerSmali(listenerType, null);
    }

    static String noshowListenerSmali(String listenerType, ProtectSpec ps) {
        return ".class public " + listenerType + "\n"
                + ".super Ljava/lang/Object;\n"
                + (ps == null ? ".source \"mpNoShow.java\"\n" : "")
                + ".implements Landroid/content/DialogInterface$OnClickListener;\n"
                + "\n"
                + ".field private ctx:Landroid/content/Context;\n"
                + "\n"
                + ".method public constructor <init>(Landroid/content/Context;)V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + listenerType + "->ctx:Landroid/content/Context;\n"
                + "    return-void\n"
                + ".end method\n"
                + "\n"
                + ".method public onClick(Landroid/content/DialogInterface;I)V\n"
                + "    .locals 3\n"
                + "    iget-object v0, p0, " + listenerType + "->ctx:Landroid/content/Context;\n"
                + "    invoke-static {v0}, Landroid/preference/PreferenceManager;->getDefaultSharedPreferences(Landroid/content/Context;)Landroid/content/SharedPreferences;\n"
                + "    move-result-object v0\n"
                + "    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;\n"
                + "    move-result-object v0\n"
                + loadStringSmali(ps, ps != null ? ps.dontShowKey : DONTSHOW_KEY, 1, 2)
                + "    const/4 v2, 0x1\n"
                + "    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;\n"
                + "    move-result-object v0\n"
                + "    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V\n"
                + "    invoke-interface {p1}, Landroid/content/DialogInterface;->dismiss()V\n"
                + "    return-void\n"
                + ".end method\n";
    }

    static String dismissSmali(String listenerType) {
        return ".class public " + listenerType + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpDismiss.java\"\n"
                + ".implements Landroid/view/View$OnClickListener;\n"
                + "\n"
                + ".field public static dlg:Landroid/content/DialogInterface;\n"
                + "\n"
                + ".method public constructor <init>()V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    return-void\n"
                + ".end method\n"
                + "\n"
                + ".method public onClick(Landroid/view/View;)V\n"
                + "    .locals 1\n"
                + "    sget-object v0, " + listenerType + "->dlg:Landroid/content/DialogInterface;\n"
                + "    invoke-interface {v0}, Landroid/content/DialogInterface;->dismiss()V\n"
                + "    return-void\n"
                + ".end method\n";
    }

    static String advancedViewSmali(DialogOptions opts, String urlViewListener, String dismissListener,
                                      String blinkListener, Map<String, String> fontAssets,
                                      String waveRgb) {
        return advancedViewSmali(opts, urlViewListener, dismissListener, blinkListener, fontAssets, waveRgb, null);
    }

    static String advancedViewSmali(DialogOptions opts, String urlViewListener, String dismissListener,
                                      String blinkListener, Map<String, String> fontAssets,
                                      String waveRgb, String videoHelper) {
        return advancedViewSmali(opts, urlViewListener, dismissListener, blinkListener, fontAssets, waveRgb, videoHelper, null);
    }

    static String advancedViewSmali(DialogOptions opts, String urlViewListener, String dismissListener,
                                      String blinkListener, Map<String, String> fontAssets,
                                      String waveRgb, String videoHelper, ProtectSpec ps) {
        StringBuilder sb = new StringBuilder();
        sb.append("    new-instance v6, Landroid/widget/FrameLayout;\n");
        sb.append("    invoke-direct {v6, p0}, Landroid/widget/FrameLayout;-><init>(Landroid/content/Context;)V\n");
        sb.append("    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;\n");
        sb.append("    move-result-object v5\n");
        sb.append("    invoke-virtual {v5}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;\n");
        sb.append("    move-result-object v5\n");
        if (hasVideo(opts) && videoHelper != null) {
            sb.append(loadStringSmali(ps, ps != null ? ps.videoAssetName : VIDEO_ASSET_NAME, 1, 2));
            sb.append("    invoke-static {p0, v1}, ").append(videoHelper).append("->create(Landroid/content/Context;Ljava/lang/String;)Landroid/view/View;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    new-instance v3, Landroid/widget/FrameLayout$LayoutParams;\n");
            sb.append("    const/4 v1, -0x1\n");
            sb.append("    const/4 v7, -0x2\n");
            sb.append("    invoke-direct {v3, v1, v7}, Landroid/widget/FrameLayout$LayoutParams;-><init>(II)V\n");
            sb.append("    invoke-virtual {v6, v2, v3}, Landroid/widget/FrameLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V\n");
        }
        int label = 0;
        if (opts.widgets != null) {
            for (AdvWidget w : opts.widgets) {
                if (w == null || w.kind == null) continue;
                if (w.kind.equals("image")) {
                    if (w.imageB64 == null || w.imageB64.isEmpty()) continue;
                    sb.append("    const-string v1, \"").append(w.imageB64).append("\"\n");
                    sb.append("    const/4 v2, 0x0\n");
                    sb.append("    invoke-static {v1, v2}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B\n");
                    sb.append("    move-result-object v1\n");
                    sb.append("    const/4 v2, 0x0\n");
                    sb.append("    array-length v3, v1\n");
                    sb.append("    invoke-static {v1, v2, v3}, Landroid/graphics/BitmapFactory;->decodeByteArray([BII)Landroid/graphics/Bitmap;\n");
                    sb.append("    move-result-object v1\n");
                    sb.append("    new-instance v2, Landroid/widget/ImageView;\n");
                    sb.append("    invoke-direct {v2, p0}, Landroid/widget/ImageView;-><init>(Landroid/content/Context;)V\n");
                    sb.append("    const/4 v3, 0x1\n");
                    sb.append("    invoke-virtual {v2, v3}, Landroid/widget/ImageView;->setAdjustViewBounds(Z)V\n");
                    sb.append("    invoke-virtual {v2, v1}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V\n");
                    appendChildSmali(sb, w, 2);
                } else if (w.kind.equals("button")) {
                    String text = w.text == null ? "" : w.text;
                    sb.append(loadTextSmali(text, opts.base64, opts.html, 1, 2, ps));
                    sb.append("    move-object v3, v1\n");
                    sb.append("    new-instance v2, Landroid/widget/Button;\n");
                    sb.append("    invoke-direct {v2, p0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V\n");
                    sb.append("    invoke-virtual {v2, v3}, Landroid/widget/Button;->setText(Ljava/lang/CharSequence;)V\n");
                    if (w.textColor != 0) {
                        sb.append("    const v3, 0x").append(Integer.toHexString(w.textColor)).append("\n");
                        sb.append("    invoke-virtual {v2, v3}, Landroid/widget/Button;->setTextColor(I)V\n");
                    }
                    if (w.textSizeSp > 0) {
                        sb.append("    const/4 v1, 0x2\n");
                        sb.append("    const v3, 0x").append(Integer.toHexString(Float.floatToRawIntBits(w.textSizeSp))).append("\n");
                        sb.append("    invoke-virtual {v2, v1, v3}, Landroid/widget/Button;->setTextSize(IF)V\n");
                    }
                    sb.append(widgetTypefaceSmali(w, opts, label++, fontAssets, ps));
                    sb.append(buttonDecorSmali(w, opts, blinkListener, waveRgb, label++));
                    if (w.url != null && !w.url.trim().isEmpty() && urlViewListener != null) {
                        sb.append(loadStringSmali(ps, w.url.trim(), 1, 3));
                        sb.append("    new-instance v3, ").append(urlViewListener).append("\n");
                        sb.append("    invoke-direct {v3, p0, v1}, ").append(urlViewListener).append("-><init>(Landroid/content/Context;Ljava/lang/String;)V\n");
                    } else if (dismissListener != null) {
                        sb.append("    new-instance v3, ").append(dismissListener).append("\n");
                        sb.append("    invoke-direct {v3}, ").append(dismissListener).append("-><init>()V\n");
                    } else {
                        sb.append("    const/4 v3, 0x0\n");
                    }
                    sb.append("    invoke-virtual {v2, v3}, Landroid/widget/Button;->setOnClickListener(Landroid/view/View$OnClickListener;)V\n");
                    appendChildSmali(sb, w, 2);
                } else {
                    String text = w.text == null ? "" : w.text;
                    sb.append(loadTextSmali(text, opts.base64, opts.html, 3, 1, ps));
                    sb.append("    new-instance v2, Landroid/widget/TextView;\n");
                    sb.append("    invoke-direct {v2, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V\n");
                    sb.append("    invoke-virtual {v2, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V\n");
                    if (w.textColor != 0) {
                        sb.append("    const v3, 0x").append(Integer.toHexString(w.textColor)).append("\n");
                        sb.append("    invoke-virtual {v2, v3}, Landroid/widget/TextView;->setTextColor(I)V\n");
                    }
                    if (w.textSizeSp > 0) {
                        sb.append("    const/4 v1, 0x2\n");
                        sb.append("    const v3, 0x").append(Integer.toHexString(Float.floatToRawIntBits(w.textSizeSp))).append("\n");
                        sb.append("    invoke-virtual {v2, v1, v3}, Landroid/widget/TextView;->setTextSize(IF)V\n");
                    }
                    sb.append(widgetTypefaceSmali(w, opts, label++, fontAssets, ps));
                    appendChildSmali(sb, w, 2);
                }
            }
        }
        sb.append("    invoke-virtual {v0, v6}, Landroid/app/AlertDialog$Builder;->setView(Landroid/view/View;)Landroid/app/AlertDialog$Builder;\n");
        sb.append("    move-result-object v0\n");
        return sb.toString();
    }

    private static String effectiveFont(AdvWidget w, DialogOptions opts) {
        if (w.font != null && !w.font.isEmpty()) return w.font;
        if (opts != null && opts.dlgFont != null && !opts.dlgFont.isEmpty()) return opts.dlgFont;
        return "";
    }

    private static String effectiveFontPath(AdvWidget w, DialogOptions opts) {
        if (w.fontPath != null && !w.fontPath.isEmpty()) return w.fontPath;
        if (opts != null && opts.dlgFontPath != null && !opts.dlgFontPath.isEmpty()) return opts.dlgFontPath;
        return "";
    }

    private static String fontAssetName(String path, Map<String, String> fontAssets) {
        if (path == null || path.isEmpty() || fontAssets == null) return null;
        return fontAssets.get(path);
    }

    private static String widgetTypefaceSmali(AdvWidget w, DialogOptions opts, int labelIdx,
                                              Map<String, String> fontAssets, ProtectSpec ps) {
        String font = effectiveFont(w, opts);
        String asset = fontAssetName(effectiveFontPath(w, opts), fontAssets);
        if (asset == null && font.isEmpty() && w.fontStyle == 0) return "";
        StringBuilder sb = new StringBuilder();
        if (asset != null) {
            sb.append(loadStringSmali(ps, asset, 3, 1));
            sb.append("    invoke-virtual {p0}, Landroid/content/Context;->getAssets()Landroid/content/res/AssetManager;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    invoke-static {v1, v3}, Landroid/graphics/Typeface;->createFromAsset(Landroid/content/res/AssetManager;Ljava/lang/String;)Landroid/graphics/Typeface;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    if-nez v1, :mptf_ok_").append(labelIdx).append("\n");
            sb.append("    sget-object v1, Landroid/graphics/Typeface;->DEFAULT:Landroid/graphics/Typeface;\n");
            sb.append("    :mptf_ok_").append(labelIdx).append("\n");
        } else if (!font.isEmpty()) {
            sb.append(loadStringSmali(ps, font, 1, 3));
            sb.append("    const/4 v3, 0x0\n");
            sb.append("    invoke-static {v1, v3}, Landroid/graphics/Typeface;->create(Ljava/lang/String;I)Landroid/graphics/Typeface;\n");
            sb.append("    move-result-object v1\n");
        } else {
            sb.append("    sget-object v1, Landroid/graphics/Typeface;->DEFAULT:Landroid/graphics/Typeface;\n");
        }
        if (w.fontStyle != 0) {
            sb.append("    const/4 v3, ").append(w.fontStyle).append("\n");
            sb.append("    invoke-virtual {v2, v1, v3}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;I)V\n");
        } else {
            sb.append("    invoke-virtual {v2, v1}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;)V\n");
        }
        return sb.toString();
    }

    private static String buttonDecorSmali(AdvWidget w, DialogOptions opts, String blinkListener,
                                           String waveRgb, int labelIdx) {
        boolean hasBg = w.btnBg != 0;
        boolean hasGrad = hasBg && w.btnBg2 != 0;
        boolean hasRadius = w.btnCornerRadiusDp > 0;
        boolean hasBorder = w.btnBorderWidthDp > 0;
        boolean hasPadding = w.btnPaddingDp > 0;
        boolean wave = w.btnAnim && waveRgb != null;
        boolean anim = w.btnAnim && blinkListener != null && !wave;
        if (!hasBg && !hasGrad && !hasRadius && !hasBorder && !hasPadding && !anim && !wave) return "";
        float borderDp = w.btnBorderWidthDp > 0 ? w.btnBorderWidthDp : 2f;
        int borderColor = w.btnBorderColor != -1 ? w.btnBorderColor
                : (hasBg ? w.btnBg : 0xFFFFFFFF);
        StringBuilder sb = new StringBuilder();
        if (wave) {
            sb.append("    move-object v7, v2\n");
            appendDpToPx(sb, Float.floatToRawIntBits(w.btnCornerRadiusDp), 2, 3, 1);
            appendDpToPx(sb, Float.floatToRawIntBits(borderDp), 3, 5, 1);
            sb.append("    const v1, 0x").append(Integer.toHexString(w.btnBg)).append("\n");
            sb.append("    new-instance v4, ").append(waveRgb).append("\n");
            sb.append("    invoke-direct {v4, v1, v2, v3}, ").append(waveRgb).append("-><init>(IFF)V\n");
            sb.append("    const-wide v1, 0x").append(Long.toHexString(w.btnAnimSpeedMs > 0 ? w.btnAnimSpeedMs : 2500)).append("L\n");
            sb.append("    iput-wide v1, v4, ").append(waveRgb).append("->durationMs:J\n");
            sb.append(waveColorsSmali(waveRgb, widgetWaveColors(w), w.btnAnimRainbow));
            sb.append("    invoke-virtual {v7, v4}, Landroid/widget/Button;->setBackground(Landroid/graphics/drawable/Drawable;)V\n");
            if (hasPadding) {
                appendDpToPx(sb, Float.floatToRawIntBits((float) w.btnPaddingDp), 3, 5, 1);
                sb.append("    float-to-int v3, v3\n");
                sb.append("    invoke-virtual {v7, v3, v3, v3, v3}, Landroid/widget/Button;->setPadding(IIII)V\n");
            }
            sb.append("    move-object v2, v7\n");
            return sb.toString();
        }
        sb.append("    new-instance v4, Landroid/graphics/drawable/GradientDrawable;\n");
        sb.append("    invoke-direct {v4}, Landroid/graphics/drawable/GradientDrawable;-><init>()V\n");
        if (hasGrad) {
            sb.append("    const/4 v3, 0x2\n");
            sb.append("    new-array v1, v3, [I\n");
            sb.append("    const v3, 0x").append(Integer.toHexString(w.btnBg)).append("\n");
            sb.append("    const/4 v7, 0x0\n");
            sb.append("    aput v3, v1, v7\n");
            sb.append("    const v3, 0x").append(Integer.toHexString(w.btnBg2)).append("\n");
            sb.append("    const/4 v7, 0x1\n");
            sb.append("    aput v3, v1, v7\n");
            sb.append("    sget-object v3, Landroid/graphics/drawable/GradientDrawable$Orientation;->TOP_BOTTOM:Landroid/graphics/drawable/GradientDrawable$Orientation;\n");
            sb.append("    invoke-virtual {v4, v3}, Landroid/graphics/drawable/GradientDrawable;->setOrientation(Landroid/graphics/drawable/GradientDrawable$Orientation;)V\n");
            sb.append("    invoke-virtual {v4, v1}, Landroid/graphics/drawable/GradientDrawable;->setColors([I)V\n");
        } else if (hasBg) {
            sb.append("    const v1, 0x").append(Integer.toHexString(w.btnBg)).append("\n");
            sb.append("    invoke-virtual {v4, v1}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V\n");
        } else {
            sb.append("    const v1, 0x0\n");
            sb.append("    invoke-virtual {v4, v1}, Landroid/graphics/drawable/GradientDrawable;->setColor(I)V\n");
        }
        if (hasRadius) {
            appendDpToPx(sb, Float.floatToRawIntBits(w.btnCornerRadiusDp), 3, 5, 1);
            sb.append("    invoke-virtual {v4, v3}, Landroid/graphics/drawable/GradientDrawable;->setCornerRadius(F)V\n");
        }
        if (hasBorder || anim) {
            appendDpToPx(sb, Float.floatToRawIntBits(borderDp), 3, 5, 1);
            sb.append("    float-to-int v3, v3\n");
            sb.append("    const v1, 0x").append(Integer.toHexString(borderColor)).append("\n");
            sb.append("    invoke-virtual {v4, v3, v1}, Landroid/graphics/drawable/GradientDrawable;->setStroke(II)V\n");
        }
        sb.append("    invoke-virtual {v2, v4}, Landroid/widget/Button;->setBackground(Landroid/graphics/drawable/Drawable;)V\n");
        if (hasPadding) {
            appendDpToPx(sb, Float.floatToRawIntBits((float) w.btnPaddingDp), 3, 5, 1);
            sb.append("    float-to-int v3, v3\n");
            sb.append("    invoke-virtual {v2, v3, v3, v3, v3}, Landroid/widget/Button;->setPadding(IIII)V\n");
        }
        if (anim) {
            int ms = Math.max(100, opts.animSpeedMs);
            int[] btnAnimColors = buttonAnimColors(opts, borderColor);
            appendAnimColors(sb, btnAnimColors, 1, 3, 7);
            sb.append("    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    new-instance v7, Landroid/os/Handler;\n");
            sb.append("    invoke-direct {v7, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V\n");
            sb.append("    new-instance v1, ").append(blinkListener).append("\n");
            sb.append("    invoke-direct {v1, v4, v7, v3}, ").append(blinkListener)
                    .append("-><init>(Landroid/graphics/drawable/GradientDrawable;Landroid/os/Handler;[I)V\n");
            sb.append("    const v3, 0x").append(Integer.toHexString(ms)).append("\n");
            sb.append("    iput v3, v1, ").append(blinkListener).append("->ms:I\n");
            appendDpToPx(sb, Float.floatToRawIntBits(borderDp), 4, 5, 3);
            sb.append("    float-to-int v4, v4\n");
            sb.append("    iput v4, v1, ").append(blinkListener).append("->w:I\n");
            sb.append("    const-wide v3, 0x").append(Long.toHexString(ms)).append("L\n");
            sb.append("    invoke-virtual {v7, v1, v3, v4}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z\n");
        }
        return sb.toString();
    }

    private static void appendChildSmali(StringBuilder sb, AdvWidget w, int viewReg) {
        sb.append("    new-instance v3, Landroid/widget/FrameLayout$LayoutParams;\n");
        sb.append("    const/4 v1, -0x2\n");
        sb.append("    invoke-direct {v3, v1, v1}, Landroid/widget/FrameLayout$LayoutParams;-><init>(II)V\n");
        if (w.leftDp != 0) {
            sb.append("    const/4 v1, 0x1\n");
            sb.append("    const v7, 0x").append(Integer.toHexString(Float.floatToRawIntBits((float) w.leftDp))).append("\n");
            sb.append("    invoke-static {v1, v7, v5}, Landroid/util/TypedValue;->applyDimension(IFLandroid/util/DisplayMetrics;)F\n");
            sb.append("    move-result v7\n");
            sb.append("    float-to-int v7, v7\n");
            sb.append("    iput v7, v3, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I\n");
        }
        if (w.topDp != 0) {
            sb.append("    const/4 v1, 0x1\n");
            sb.append("    const v7, 0x").append(Integer.toHexString(Float.floatToRawIntBits((float) w.topDp))).append("\n");
            sb.append("    invoke-static {v1, v7, v5}, Landroid/util/TypedValue;->applyDimension(IFLandroid/util/DisplayMetrics;)F\n");
            sb.append("    move-result v7\n");
            sb.append("    float-to-int v7, v7\n");
            sb.append("    iput v7, v3, Landroid/view/ViewGroup$MarginLayoutParams;->topMargin:I\n");
        }
        sb.append("    invoke-virtual {v6, v").append(viewReg).append(", v3}, Landroid/widget/FrameLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V\n");
    }

    static String blinkSmali(String listenerType) {        return ".class public " + listenerType + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpBlink.java\"\n"
                + ".implements Ljava/lang/Runnable;\n"
                + "\n"
                + ".field private bg:Landroid/graphics/drawable/GradientDrawable;\n"
                + ".field private h:Landroid/os/Handler;\n"
                + ".field private colors:[I\n"
                + ".field private idx:I\n"
                + ".field public ms:I\n"
                + ".field public w:I\n"
                + "\n"
                + ".method public constructor <init>(Landroid/graphics/drawable/GradientDrawable;Landroid/os/Handler;[I)V\n"
                + "    .locals 1\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + listenerType + "->bg:Landroid/graphics/drawable/GradientDrawable;\n"
                + "    iput-object p2, p0, " + listenerType + "->h:Landroid/os/Handler;\n"
                + "    iput-object p3, p0, " + listenerType + "->colors:[I\n"
                + "    const/4 v0, 0x0\n"
                + "    iput v0, p0, " + listenerType + "->idx:I\n"
                + "    return-void\n"
                + ".end method\n"
                + "\n"
                + ".method public run()V\n"
                + "    .locals 4\n"
                + "    iget v0, p0, " + listenerType + "->idx:I\n"
                + "    iget-object v1, p0, " + listenerType + "->colors:[I\n"
                + "    array-length v2, v1\n"
                + "    rem-int v0, v0, v2\n"
                + "    aget v1, v1, v0\n"
                + "    add-int/lit8 v0, v0, 0x1\n"
                + "    iput v0, p0, " + listenerType + "->idx:I\n"
                + "    iget v0, p0, " + listenerType + "->w:I\n"
                + "    iget-object v2, p0, " + listenerType + "->bg:Landroid/graphics/drawable/GradientDrawable;\n"
                + "    invoke-virtual {v2, v0, v1}, Landroid/graphics/drawable/GradientDrawable;->setStroke(II)V\n"
                + "    iget-object v0, p0, " + listenerType + "->h:Landroid/os/Handler;\n"
                + "    iget v1, p0, " + listenerType + "->ms:I\n"
                + "    int-to-long v1, v1\n"
                + "    invoke-virtual {v0, p0, v1, v2}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z\n"
                + "    move-result v0\n"
                + "    return-void\n"
                + ".end method\n";
    }

    static String wrapperSmali(String classDescriptor, String flags, boolean callToast, boolean callDialog) {
        return wrapperSmali(classDescriptor, flags, callToast, callDialog, null);
    }

    static String wrapperSmali(String classDescriptor, String flags, boolean callToast, boolean callDialog, ProtectSpec ps) {
        String origName = ps != null ? ps.origRename : "onCreate$mpmanager";
        StringBuilder sb = new StringBuilder();
        sb.append(".method ").append(flags).append(" onCreate(Landroid/os/Bundle;)V\n");
        sb.append("    .locals ").append(ps != null ? 6 : 1).append("\n");
        if (ps != null) {
            appendReflectCallSmali(sb, ps);
        } else {
            if (callToast) {
                sb.append("    invoke-direct {p0}, ").append(classDescriptor).append("->").append(TOAST_HELPER).append("()V\n");
            }
            if (callDialog) {
                sb.append("    invoke-direct {p0}, ").append(classDescriptor).append("->").append(DIALOG_HELPER).append("()V\n");
            }
        }
        sb.append("    invoke-direct {p0, p1}, ").append(classDescriptor).append("->").append(origName).append("(Landroid/os/Bundle;)V\n");
        sb.append("    return-void\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    static String resumeWrapperSmali(String classDescriptor, String flags,
                                     boolean hasOrigResume, String superDescriptor,
                                     boolean callToast, boolean callDialog, ProtectSpec ps) {
        String guardField = ps != null ? ps.guardField : "mpResumeOnce";
        String origName = ps != null ? ps.origRename : "onResume$mpmanager";
        StringBuilder sb = new StringBuilder();
        sb.append(".method ").append(flags).append(" onResume()V\n");
        sb.append("    .locals ").append(ps != null ? 6 : 1).append("\n");
        if (hasOrigResume) {
            sb.append("    invoke-direct {p0}, ").append(classDescriptor).append("->").append(origName).append("()V\n");
        } else {
            sb.append("    invoke-super {p0}, ").append(superDescriptor).append("->onResume()V\n");
        }
        sb.append("    sget-boolean v0, ").append(classDescriptor).append("->").append(guardField).append(":Z\n");
        sb.append("    if-nez v0, :mp_resume_done\n");
        sb.append("    const/4 v0, 0x1\n");
        sb.append("    sput-boolean v0, ").append(classDescriptor).append("->").append(guardField).append(":Z\n");
        if (ps != null) {
            appendReflectCallSmali(sb, ps);
        } else {
            if (callToast) {
                sb.append("    invoke-direct {p0}, ").append(classDescriptor).append("->").append(TOAST_HELPER).append("()V\n");
            }
            if (callDialog) {
                sb.append("    invoke-direct {p0}, ").append(classDescriptor).append("->").append(DIALOG_HELPER).append("()V\n");
            }
        }
        sb.append("    :mp_resume_done\n");
        sb.append("    return-void\n");
        sb.append(".end method\n");
        return sb.toString();
    }

    private static void appendReflectCallSmali(StringBuilder sb, ProtectSpec ps) {
        int key = ps.nextKey();
        String payload = ps.helperClassDot + ":android.content.Context:" + ps.entryMethod;
        String enc = xorB64(payload, key);
        sb.append("    :mp_try_start\n");
        sb.append("    const-string v0, \"").append(enc).append("\"\n");
        sb.append("    const/4 v1, 0x0\n");
        sb.append("    invoke-static {v0, v1}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B\n");
        sb.append("    move-result-object v0\n");
        sb.append("    array-length v1, v0\n");
        sb.append("    const/4 v2, 0x0\n");
        sb.append("    const/16 v3, 0x").append(Integer.toHexString(key)).append("\n");
        sb.append("    :mp_ref_loop\n");
        sb.append("    if-ge v2, v1, :mp_ref_done\n");
        sb.append("    aget-byte v4, v0, v2\n");
        sb.append("    xor-int v4, v4, v3\n");
        sb.append("    int-to-byte v4, v4\n");
        sb.append("    aput-byte v4, v0, v2\n");
        sb.append("    add-int/lit8 v2, v2, 0x1\n");
        sb.append("    goto :mp_ref_loop\n");
        sb.append("    :mp_ref_done\n");
        sb.append("    new-instance v1, Ljava/lang/String;\n");
        sb.append("    invoke-direct {v1, v0}, Ljava/lang/String;-><init>([B)V\n");
        sb.append("    const-string v0, \":\"\n");
        sb.append("    invoke-virtual {v1, v0}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;\n");
        sb.append("    move-result-object v0\n");
        sb.append("    const/4 v1, 0x0\n");
        sb.append("    aget-object v1, v0, v1\n");
        sb.append("    invoke-static {v1}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;\n");
        sb.append("    move-result-object v1\n");
        sb.append("    const/4 v2, 0x1\n");
        sb.append("    aget-object v3, v0, v2\n");
        sb.append("    invoke-static {v3}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;\n");
        sb.append("    move-result-object v3\n");
        sb.append("    new-array v4, v2, [Ljava/lang/Class;\n");
        sb.append("    const/4 v5, 0x0\n");
        sb.append("    aput-object v3, v4, v5\n");
        sb.append("    const/4 v3, 0x2\n");
        sb.append("    aget-object v0, v0, v3\n");
        sb.append("    invoke-virtual {v1, v0, v4}, Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;\n");
        sb.append("    move-result-object v0\n");
        sb.append("    invoke-virtual {v0, v2}, Ljava/lang/reflect/Method;->setAccessible(Z)V\n");
        sb.append("    new-array v1, v2, [Ljava/lang/Object;\n");
        sb.append("    aput-object p0, v1, v5\n");
        sb.append("    const/4 v2, 0x0\n");
        sb.append("    invoke-virtual {v0, v2, v1}, Ljava/lang/reflect/Method;->invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;\n");
        sb.append("    :mp_try_end\n");
        sb.append("    .catch Ljava/lang/Throwable; {:mp_try_start .. :mp_try_end} :mp_try_catch\n");
        sb.append("    :mp_try_catch\n");
    }

    static String insertGuardFieldSmali(String content, String fieldName) {
        int pos = 0;
        int len = content.length();
        while (pos < len) {
            int nl = content.indexOf('\n', pos);
            int end = nl >= 0 ? nl : len;
            String trimmed = content.substring(pos, end).trim();
            if (trimmed.isEmpty() || trimmed.startsWith(".class ") || trimmed.startsWith(".super ")
                    || trimmed.startsWith(".source ") || trimmed.startsWith(".implements ")) {
                pos = nl >= 0 ? nl + 1 : len;
            } else {
                break;
            }
        }
        return content.substring(0, pos) + ".field private static " + fieldName + ":Z\n\n" + content.substring(pos);
    }

    static String waveRgbSmali(String rgbType, String tickType) {
        StringBuilder s = new StringBuilder();
        s.append(".class public ").append(rgbType).append("\n");
        s.append(".super Landroid/graphics/drawable/Drawable;\n");
        s.append(".source \"mpRgb.java\"\n\n");
        s.append(".field private backgroundPaint:Landroid/graphics/Paint;\n");
        s.append(".field private borderPaint:Landroid/graphics/Paint;\n");
        s.append(".field private rect:Landroid/graphics/RectF;\n");
        s.append(".field private shaderMatrix:Landroid/graphics/Matrix;\n");
        s.append(".field private cornerRadius:F\n");
        s.append(".field private borderWidth:F\n");
        s.append(".field private gradient:Landroid/graphics/LinearGradient;\n");
        s.append(".field private animator:Landroid/animation/ValueAnimator;\n");
        s.append(".field public offset:F\n");
        s.append(".field private period:F\n");
        s.append(".field private bgColor:I\n");
        s.append(".field public colors:[I\n");
        s.append(".field public rainbow:Z\n");
        s.append(".field public durationMs:J\n\n");
        s.append(".method public constructor <init>(IFF)V\n");
        s.append("    .locals 2\n");
        s.append("    invoke-direct {p0}, Landroid/graphics/drawable/Drawable;-><init>()V\n");
        s.append("    iput p1, p0, ").append(rgbType).append("->bgColor:I\n");
        s.append("    iput p2, p0, ").append(rgbType).append("->cornerRadius:F\n");
        s.append("    iput p3, p0, ").append(rgbType).append("->borderWidth:F\n");
        s.append("    new-instance v0, Landroid/graphics/Paint;\n");
        s.append("    const/4 v1, 0x1\n");
        s.append("    invoke-direct {v0, v1}, Landroid/graphics/Paint;-><init>(I)V\n");
        s.append("    iput-object v0, p0, ").append(rgbType).append("->backgroundPaint:Landroid/graphics/Paint;\n");
        s.append("    new-instance v0, Landroid/graphics/Paint;\n");
        s.append("    const/4 v1, 0x1\n");
        s.append("    invoke-direct {v0, v1}, Landroid/graphics/Paint;-><init>(I)V\n");
        s.append("    iput-object v0, p0, ").append(rgbType).append("->borderPaint:Landroid/graphics/Paint;\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->backgroundPaint:Landroid/graphics/Paint;\n");
        s.append("    sget-object v1, Landroid/graphics/Paint$Style;->FILL:Landroid/graphics/Paint$Style;\n");
        s.append("    invoke-virtual {v0, v1}, Landroid/graphics/Paint;->setStyle(Landroid/graphics/Paint$Style;)V\n");
        s.append("    iget v1, p0, ").append(rgbType).append("->bgColor:I\n");
        s.append("    invoke-virtual {v0, v1}, Landroid/graphics/Paint;->setColor(I)V\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->borderPaint:Landroid/graphics/Paint;\n");
        s.append("    sget-object v1, Landroid/graphics/Paint$Style;->STROKE:Landroid/graphics/Paint$Style;\n");
        s.append("    invoke-virtual {v0, v1}, Landroid/graphics/Paint;->setStyle(Landroid/graphics/Paint$Style;)V\n");
        s.append("    iget v1, p0, ").append(rgbType).append("->borderWidth:F\n");
        s.append("    invoke-virtual {v0, v1}, Landroid/graphics/Paint;->setStrokeWidth(F)V\n");
        s.append("    new-instance v0, Landroid/graphics/RectF;\n");
        s.append("    invoke-direct {v0}, Landroid/graphics/RectF;-><init>()V\n");
        s.append("    iput-object v0, p0, ").append(rgbType).append("->rect:Landroid/graphics/RectF;\n");
        s.append("    new-instance v0, Landroid/graphics/Matrix;\n");
        s.append("    invoke-direct {v0}, Landroid/graphics/Matrix;-><init>()V\n");
        s.append("    iput-object v0, p0, ").append(rgbType).append("->shaderMatrix:Landroid/graphics/Matrix;\n");
        s.append("    return-void\n");
        s.append(".end method\n\n");
        s.append(".method protected onBoundsChange(Landroid/graphics/Rect;)V\n");
        s.append("    .locals 9\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->animator:Landroid/animation/ValueAnimator;\n");
        s.append("    if-eqz v0, :mp_rgb_new\n");
        s.append("    invoke-virtual {v0}, Landroid/animation/ValueAnimator;->cancel()V\n");
        s.append("    :mp_rgb_new\n");
        s.append("    invoke-virtual {p1}, Landroid/graphics/Rect;->width()I\n");
        s.append("    move-result v0\n");
        s.append("    int-to-float v0, v0\n");
        s.append("    const v1, 0x40000000\n");
        s.append("    mul-float v0, v0, v1\n");
        s.append("    iput v0, p0, ").append(rgbType).append("->period:F\n");
        s.append("    iget-boolean v1, p0, ").append(rgbType).append("->rainbow:Z\n");
        s.append("    if-eqz v1, :mp_rgb_custom\n");
        s.append("    :mp_rgb_seven\n");
        s.append("    const/4 v1, 0x7\n");
        s.append("    new-array v3, v1, [I\n");
        int[] waveColors = {0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000};
        for (int i = 0; i < waveColors.length; i++) {
            s.append("    const v1, 0x").append(Integer.toHexString(waveColors[i])).append("\n");
            appendConstInt(s, 2, i);
            s.append("    aput v1, v3, v2\n");
        }
        float[] wavePos = {0f, 0.16f, 0.33f, 0.5f, 0.66f, 0.83f, 1f};
        s.append("    const/4 v1, 0x7\n");
        s.append("    new-array v4, v1, [F\n");
        for (int i = 0; i < wavePos.length; i++) {
            s.append("    const v1, 0x").append(Integer.toHexString(Float.floatToRawIntBits(wavePos[i]))).append("\n");
            appendConstInt(s, 2, i);
            s.append("    aput v1, v4, v2\n");
        }
        s.append("    goto :mp_rgb_grad\n");
        s.append("    :mp_rgb_custom\n");
        s.append("    iget-object v3, p0, ").append(rgbType).append("->colors:[I\n");
        s.append("    if-eqz v3, :mp_rgb_seven\n");
        s.append("    array-length v5, v3\n");
        s.append("    const/4 v1, 0x2\n");
        s.append("    if-lt v5, v1, :mp_rgb_seven\n");
        s.append("    new-array v4, v5, [F\n");
        s.append("    add-int/lit8 v1, v5, -0x1\n");
        s.append("    int-to-float v1, v1\n");
        s.append("    const/4 v2, 0x0\n");
        s.append("    :mp_rgb_posloop\n");
        s.append("    if-ge v2, v5, :mp_rgb_posdone\n");
        s.append("    int-to-float v6, v2\n");
        s.append("    div-float v6, v6, v1\n");
        s.append("    aput v6, v4, v2\n");
        s.append("    add-int/lit8 v2, v2, 0x1\n");
        s.append("    goto :mp_rgb_posloop\n");
        s.append("    :mp_rgb_posdone\n");
        s.append("    goto :mp_rgb_grad\n");
        s.append("    :mp_rgb_grad\n");
        s.append("    move-object v6, v3\n");
        s.append("    move-object v7, v4\n");
        s.append("    new-instance v1, Landroid/graphics/LinearGradient;\n");
        s.append("    const/4 v2, 0x0\n");
        s.append("    const/4 v3, 0x0\n");
        s.append("    move v4, v0\n");
        s.append("    const/4 v5, 0x0\n");
        s.append("    sget-object v8, Landroid/graphics/Shader$TileMode;->REPEAT:Landroid/graphics/Shader$TileMode;\n");
        s.append("    invoke-direct/range {v1 .. v8}, Landroid/graphics/LinearGradient;-><init>(FFFF[I[FLandroid/graphics/Shader$TileMode;)V\n");
        s.append("    iput-object v1, p0, ").append(rgbType).append("->gradient:Landroid/graphics/LinearGradient;\n");
        s.append("    iget-object v2, p0, ").append(rgbType).append("->borderPaint:Landroid/graphics/Paint;\n");
        s.append("    invoke-virtual {v2, v1}, Landroid/graphics/Paint;->setShader(Landroid/graphics/Shader;)Landroid/graphics/Shader;\n");
        s.append("    const/4 v1, 0x2\n");
        s.append("    new-array v2, v1, [F\n");

        s.append("    const v1, 0x00000000\n");
        s.append("    const/4 v3, 0x0\n");
        s.append("    aput v1, v2, v3\n");

        s.append("    move v1, v0\n");
        s.append("    const/4 v3, 0x1\n");
        s.append("    aput v1, v2, v3\n");

        s.append("    invoke-static {v2}, Landroid/animation/ValueAnimator;->ofFloat([F)Landroid/animation/ValueAnimator;\n");
        s.append("    move-result-object v1\n");

        s.append("    iput-object v1, p0, ").append(rgbType).append("->animator:Landroid/animation/ValueAnimator;\n");
        s.append("    iget-wide v2, p0, ").append(rgbType).append("->durationMs:J\n");
        s.append("    invoke-virtual {v1, v2, v3}, Landroid/animation/ValueAnimator;->setDuration(J)Landroid/animation/ValueAnimator;\n");
        s.append("    const/4 v2, -0x1\n");
        s.append("    invoke-virtual {v1, v2}, Landroid/animation/ValueAnimator;->setRepeatCount(I)V\n");
        s.append("    new-instance v2, Landroid/view/animation/LinearInterpolator;\n");
        s.append("    invoke-direct {v2}, Landroid/view/animation/LinearInterpolator;-><init>()V\n");
        s.append("    invoke-virtual {v1, v2}, Landroid/animation/ValueAnimator;->setInterpolator(Landroid/animation/TimeInterpolator;)V\n");
        s.append("    new-instance v2, Ljava/lang/ref/WeakReference;\n");
        s.append("    invoke-direct {v2, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V\n");
        s.append("    new-instance v3, ").append(tickType).append("\n");
        s.append("    invoke-direct {v3, v2}, ").append(tickType).append("-><init>(Ljava/lang/ref/WeakReference;)V\n");
        s.append("    invoke-virtual {v1, v3}, Landroid/animation/ValueAnimator;->addUpdateListener(Landroid/animation/ValueAnimator$AnimatorUpdateListener;)V\n");
        s.append("    invoke-virtual {v1}, Landroid/animation/ValueAnimator;->start()V\n");
        s.append("    return-void\n");
        s.append(".end method\n\n");
        s.append(".method public draw(Landroid/graphics/Canvas;)V\n");
        s.append("    .locals 5\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->gradient:Landroid/graphics/LinearGradient;\n");
        s.append("    if-nez v0, :mp_rgb_draw\n");
        s.append("    return-void\n");
        s.append("    :mp_rgb_draw\n");
        s.append("    iget v1, p0, ").append(rgbType).append("->borderWidth:F\n");
        s.append("    const v2, 0x3f000000\n");
        s.append("    mul-float v1, v1, v2\n");
        s.append("    invoke-virtual {p0}, Landroid/graphics/drawable/Drawable;->getBounds()Landroid/graphics/Rect;\n");
        s.append("    move-result-object v2\n");
        s.append("    invoke-virtual {v2}, Landroid/graphics/Rect;->width()I\n");
        s.append("    move-result v3\n");
        s.append("    int-to-float v3, v3\n");
        s.append("    sub-float v3, v3, v1\n");
        s.append("    invoke-virtual {v2}, Landroid/graphics/Rect;->height()I\n");
        s.append("    move-result v4\n");
        s.append("    int-to-float v4, v4\n");
        s.append("    sub-float v4, v4, v1\n");
        s.append("    iget-object v2, p0, ").append(rgbType).append("->rect:Landroid/graphics/RectF;\n");
        s.append("    invoke-virtual {v2, v1, v1, v3, v4}, Landroid/graphics/RectF;->set(FFFF)V\n");
        s.append("    iget v3, p0, ").append(rgbType).append("->cornerRadius:F\n");
        s.append("    iget-object v4, p0, ").append(rgbType).append("->backgroundPaint:Landroid/graphics/Paint;\n");
        s.append("    invoke-virtual {p1, v2, v3, v3, v4}, Landroid/graphics/Canvas;->drawRoundRect(Landroid/graphics/RectF;FFLandroid/graphics/Paint;)V\n");
        s.append("    iget-object v4, p0, ").append(rgbType).append("->shaderMatrix:Landroid/graphics/Matrix;\n");
        s.append("    iget v3, p0, ").append(rgbType).append("->offset:F\n");
        s.append("    neg-float v3, v3\n");
        s.append("    const/4 v2, 0x0\n");
        s.append("    invoke-virtual {v4, v3, v2}, Landroid/graphics/Matrix;->setTranslate(FF)V\n");
        s.append("    invoke-virtual {v0, v4}, Landroid/graphics/LinearGradient;->setLocalMatrix(Landroid/graphics/Matrix;)V\n");
        s.append("    iget-object v2, p0, ").append(rgbType).append("->borderPaint:Landroid/graphics/Paint;\n");
        s.append("    invoke-virtual {v2, v0}, Landroid/graphics/Paint;->setShader(Landroid/graphics/Shader;)Landroid/graphics/Shader;\n");
        s.append("    iget v3, p0, ").append(rgbType).append("->cornerRadius:F\n");
        s.append("    iget-object v4, p0, ").append(rgbType).append("->rect:Landroid/graphics/RectF;\n");
        s.append("    invoke-virtual {p1, v4, v3, v3, v2}, Landroid/graphics/Canvas;->drawRoundRect(Landroid/graphics/RectF;FFLandroid/graphics/Paint;)V\n");
        s.append("    return-void\n");
        s.append(".end method\n\n");
        s.append(".method public stopAnimation()V\n");
        s.append("    .locals 1\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->animator:Landroid/animation/ValueAnimator;\n");
        s.append("    if-eqz v0, :mp_rgb_stop\n");
        s.append("    invoke-virtual {v0}, Landroid/animation/ValueAnimator;->cancel()V\n");
        s.append("    :mp_rgb_stop\n");
        s.append("    return-void\n");
        s.append(".end method\n\n");
        s.append(".method public setAlpha(I)V\n");
        s.append("    .locals 1\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->backgroundPaint:Landroid/graphics/Paint;\n");
        s.append("    invoke-virtual {v0, p1}, Landroid/graphics/Paint;->setAlpha(I)V\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->borderPaint:Landroid/graphics/Paint;\n");
        s.append("    invoke-virtual {v0, p1}, Landroid/graphics/Paint;->setAlpha(I)V\n");
        s.append("    return-void\n");
        s.append(".end method\n\n");
        s.append(".method public setColorFilter(Landroid/graphics/ColorFilter;)V\n");
        s.append("    .locals 1\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->backgroundPaint:Landroid/graphics/Paint;\n");
        s.append("    invoke-virtual {v0, p1}, Landroid/graphics/Paint;->setColorFilter(Landroid/graphics/ColorFilter;)V\n");
        s.append("    iget-object v0, p0, ").append(rgbType).append("->borderPaint:Landroid/graphics/Paint;\n");
        s.append("    invoke-virtual {v0, p1}, Landroid/graphics/Paint;->setColorFilter(Landroid/graphics/ColorFilter;)V\n");
        s.append("    return-void\n");
        s.append(".end method\n\n");
        s.append(".method public getOpacity()I\n");
        s.append("    .locals 1\n");
        s.append("    const/4 v0, -0x3\n");
        s.append("    return v0\n");
        s.append(".end method\n");
        return s.toString();
    }

    static String waveTickSmali(String tickType, String rgbType) {
        return ".class public " + tickType + "\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"mpRgbTick.java\"\n" +
                ".implements Landroid/animation/ValueAnimator$AnimatorUpdateListener;\n\n" +
                ".field private ref:Ljava/lang/ref/WeakReference;\n\n" +
                ".method public constructor <init>(Ljava/lang/ref/WeakReference;)V\n" +
                "    .locals 0\n" +
                "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                "    iput-object p1, p0, " + tickType + "->ref:Ljava/lang/ref/WeakReference;\n" +
                "    return-void\n" +
                ".end method\n\n" +
                ".method public onAnimationUpdate(Landroid/animation/ValueAnimator;)V\n" +
                "    .locals 2\n" +
                "    iget-object v0, p0, " + tickType + "->ref:Ljava/lang/ref/WeakReference;\n" +
                "    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;\n" +
                "    move-result-object v0\n" +
                "    if-nez v0, :mp_tick_go\n" +
                "    invoke-virtual {p1}, Landroid/animation/ValueAnimator;->cancel()V\n" +
                "    return-void\n" +
                "    :mp_tick_go\n" +
                "    check-cast v0, " + rgbType + "\n" +
                "    invoke-virtual {p1}, Landroid/animation/ValueAnimator;->getAnimatedValue()Ljava/lang/Object;\n" +
                "    move-result-object v1\n" +
                "    check-cast v1, Ljava/lang/Float;\n" +
                "    invoke-virtual {v1}, Ljava/lang/Float;->floatValue()F\n" +
                "    move-result v1\n" +
                "    iput v1, v0, " + rgbType + "->offset:F\n" +
                "    invoke-virtual {v0}, " + rgbType + "->invalidateSelf()V\n" +
                "    return-void\n" +
                ".end method\n";
    }

    private static String buildCountdownContainerSmali(DialogOptions opts, String videoHelper, boolean useImage) {
        return buildCountdownContainerSmali(opts, videoHelper, useImage, null);
    }

    private static String buildCountdownContainerSmali(DialogOptions opts, String videoHelper, boolean useImage, ProtectSpec ps) {
        StringBuilder sb = new StringBuilder();
        sb.append("    new-instance v6, Landroid/widget/LinearLayout;\n");
        sb.append("    invoke-direct {v6, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V\n");
        sb.append("    const/4 v1, 0x1\n");
        sb.append("    invoke-virtual {v6, v1}, Landroid/widget/LinearLayout;->setOrientation(I)V\n");
        appendDpToPx(sb, Float.floatToRawIntBits(16f), 2, 3, 1);
        sb.append("    float-to-int v7, v2\n");
        appendDpToPx(sb, Float.floatToRawIntBits(10f), 2, 3, 1);
        sb.append("    float-to-int v2, v2\n");
        sb.append("    invoke-virtual {v6, v7, v2, v7, v2}, Landroid/widget/LinearLayout;->setPadding(IIII)V\n");
        if (videoHelper != null) {
            sb.append(loadStringSmali(ps, ps != null ? ps.videoAssetName : VIDEO_ASSET_NAME, 1, 2));
            sb.append("    invoke-static {p0, v1}, ").append(videoHelper).append("->create(Landroid/content/Context;Ljava/lang/String;)Landroid/view/View;\n");
            sb.append("    move-result-object v2\n");
            sb.append("    invoke-virtual {v6, v2}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V\n");
        } else if (useImage) {
            sb.append("    const-string v1, \"").append(opts.imageBase64).append("\"\n");
            sb.append("    const/4 v2, 0x0\n");
            sb.append("    invoke-static {v1, v2}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B\n");
            sb.append("    move-result-object v1\n");
            sb.append("    const/4 v2, 0x0\n");
            sb.append("    array-length v3, v1\n");
            sb.append("    invoke-static {v1, v2, v3}, Landroid/graphics/BitmapFactory;->decodeByteArray([BII)Landroid/graphics/Bitmap;\n");
            sb.append("    move-result-object v1\n");
            sb.append("    new-instance v2, Landroid/widget/ImageView;\n");
            sb.append("    invoke-direct {v2, p0}, Landroid/widget/ImageView;-><init>(Landroid/content/Context;)V\n");
            sb.append("    const/4 v3, 0x1\n");
            sb.append("    invoke-virtual {v2, v3}, Landroid/widget/ImageView;->setAdjustViewBounds(Z)V\n");
            sb.append("    invoke-virtual {v2, v1}, Landroid/widget/ImageView;->setImageBitmap(Landroid/graphics/Bitmap;)V\n");
            sb.append("    invoke-virtual {v6, v2}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V\n");
        }
        sb.append("    new-instance v1, Landroid/widget/LinearLayout;\n");
        sb.append("    invoke-direct {v1, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V\n");
        sb.append("    const/4 v2, 0x0\n");
        sb.append("    invoke-virtual {v1, v2}, Landroid/widget/LinearLayout;->setOrientation(I)V\n");
        sb.append("    const/16 v2, 0x11\n");
        sb.append("    invoke-virtual {v1, v2}, Landroid/widget/LinearLayout;->setGravity(I)V\n");
        float topPad = (videoHelper != null || useImage) ? 12f : 4f;
        appendDpToPx(sb, Float.floatToRawIntBits(topPad), 2, 3, 5);
        sb.append("    float-to-int v2, v2\n");
        sb.append("    const/4 v3, 0x0\n");
        sb.append("    invoke-virtual {v1, v3, v2, v3, v3}, Landroid/widget/LinearLayout;->setPadding(IIII)V\n");
        sb.append("    new-instance v2, Landroid/widget/ProgressBar;\n");
        sb.append("    invoke-direct {v2, p0}, Landroid/widget/ProgressBar;-><init>(Landroid/content/Context;)V\n");
        appendDpToPx(sb, Float.floatToRawIntBits(28f), 3, 5, 7);
        sb.append("    float-to-int v3, v3\n");
        sb.append("    new-instance v5, Landroid/widget/LinearLayout$LayoutParams;\n");
        sb.append("    invoke-direct {v5, v3, v3}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V\n");
        sb.append("    invoke-virtual {v1, v2, v5}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V\n");
        appendDpToPx(sb, Float.floatToRawIntBits(12f), 3, 5, 2);
        sb.append("    float-to-int v3, v3\n");
        sb.append("    new-instance v2, Landroid/widget/LinearLayout$LayoutParams;\n");
        sb.append("    const/4 v5, -0x2\n");
        sb.append("    invoke-direct {v2, v5, v5}, Landroid/widget/LinearLayout$LayoutParams;-><init>(II)V\n");
        sb.append("    iput v3, v2, Landroid/view/ViewGroup$MarginLayoutParams;->leftMargin:I\n");
        sb.append("    new-instance v7, Landroid/widget/TextView;\n");
        sb.append("    invoke-direct {v7, p0}, Landroid/widget/TextView;-><init>(Landroid/content/Context;)V\n");
        sb.append(loadStringSmali(ps, String.valueOf(Math.max(1, opts.countdownSeconds)), 3, 5));
        sb.append("    invoke-virtual {v7, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V\n");
        sb.append("    const/4 v3, 0x2\n");
        sb.append("    const v5, 0x").append(Integer.toHexString(Float.floatToRawIntBits(16f))).append("\n");
        sb.append("    invoke-virtual {v7, v3, v5}, Landroid/widget/TextView;->setTextSize(IF)V\n");
        sb.append("    sget-object v3, Landroid/graphics/Typeface;->DEFAULT_BOLD:Landroid/graphics/Typeface;\n");
        sb.append("    invoke-virtual {v7, v3}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;)V\n");
        if (opts.msgColor != 0) {
            sb.append("    const v3, 0x").append(Integer.toHexString(opts.msgColor)).append("\n");
            sb.append("    invoke-virtual {v7, v3}, Landroid/widget/TextView;->setTextColor(I)V\n");
        }
        sb.append("    invoke-virtual {v1, v7, v2}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V\n");
        sb.append("    invoke-virtual {v6, v1}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V\n");
        sb.append("    invoke-virtual {v0, v6}, Landroid/app/AlertDialog$Builder;->setView(Landroid/view/View;)Landroid/app/AlertDialog$Builder;\n");
        sb.append("    move-result-object v0\n");
        return sb.toString();
    }

    static String countdownSmali(String countdownType) {
        return ".class public " + countdownType + "\n"
                + ".super Ljava/lang/Object;\n"
                + ".source \"mpCountdown.java\"\n"
                + ".implements Ljava/lang/Runnable;\n\n"
                + ".field private d:Landroid/app/AlertDialog;\n"
                + ".field private t:Landroid/widget/TextView;\n"
                + ".field private h:Landroid/os/Handler;\n"
                + ".field private n:I\n\n"
                + ".method public constructor <init>(Landroid/app/AlertDialog;Landroid/widget/TextView;Landroid/os/Handler;I)V\n"
                + "    .locals 0\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    iput-object p1, p0, " + countdownType + "->d:Landroid/app/AlertDialog;\n"
                + "    iput-object p2, p0, " + countdownType + "->t:Landroid/widget/TextView;\n"
                + "    iput-object p3, p0, " + countdownType + "->h:Landroid/os/Handler;\n"
                + "    iput p4, p0, " + countdownType + "->n:I\n"
                + "    return-void\n"
                + ".end method\n\n"
                + ".method public run()V\n"
                + "    .locals 4\n"
                + "    iget-object v0, p0, " + countdownType + "->d:Landroid/app/AlertDialog;\n"
                + "    if-eqz v0, :mp_cd_end\n"
                + "    invoke-virtual {v0}, Landroid/app/AlertDialog;->isShowing()Z\n"
                + "    move-result v1\n"
                + "    if-eqz v1, :mp_cd_end\n"
                + "    iget v1, p0, " + countdownType + "->n:I\n"
                + "    if-gtz v1, :mp_cd_step\n"
                + "    :mp_cd_try_s\n"
                + "    invoke-virtual {v0}, Landroid/app/AlertDialog;->dismiss()V\n"
                + "    :mp_cd_try_e\n"
                + "    return-void\n"
                + "    :mp_cd_try_c\n"
                + "    move-exception v0\n"
                + "    return-void\n"
                + "    .catch Ljava/lang/Throwable; {:mp_cd_try_s .. :mp_cd_try_e} :mp_cd_try_c\n"
                + "    :mp_cd_step\n"
                + "    add-int/lit8 v1, v1, -0x1\n"
                + "    iput v1, p0, " + countdownType + "->n:I\n"
                + "    iget-object v2, p0, " + countdownType + "->t:Landroid/widget/TextView;\n"
                + "    invoke-static {v1}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;\n"
                + "    move-result-object v3\n"
                + "    invoke-virtual {v2, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V\n"
                + "    iget-object v0, p0, " + countdownType + "->h:Landroid/os/Handler;\n"
                + "    if-gtz v1, :mp_cd_sec\n"
                + "    const-wide v1, 0x12cL\n"
                + "    goto :mp_cd_post\n"
                + "    :mp_cd_sec\n"
                + "    const-wide v1, 0x3e8L\n"
                + "    :mp_cd_post\n"
                + "    invoke-virtual {v0, p0, v1, v2}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z\n"
                + "    move-result v0\n"
                + "    :mp_cd_end\n"
                + "    return-void\n"
                + ".end method\n";
    }

    static String dragListenerSmali(String dragType) {
        return ".class public " + dragType + "\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"mpDrag.java\"\n" +
                ".implements Landroid/view/View$OnTouchListener;\n\n" +
                ".field private w:Landroid/view/Window;\n" +
                ".field private lx:F\n" +
                ".field private ly:F\n" +
                ".field private sx:I\n" +
                ".field private sy:I\n\n" +
                ".method public constructor <init>(Landroid/view/Window;)V\n" +
                "    .locals 0\n" +
                "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                "    iput-object p1, p0, " + dragType + "->w:Landroid/view/Window;\n" +
                "    return-void\n" +
                ".end method\n\n" +
                ".method public onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z\n" +
                "    .locals 4\n" +
                "    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I\n" +
                "    move-result v0\n" +
                "    if-nez v0, :mp_drag_chk\n" +
                "    invoke-virtual {p2}, Landroid/view/MotionEvent;->getRawX()F\n" +
                "    move-result v1\n" +
                "    iput v1, p0, " + dragType + "->lx:F\n" +
                "    invoke-virtual {p2}, Landroid/view/MotionEvent;->getRawY()F\n" +
                "    move-result v1\n" +
                "    iput v1, p0, " + dragType + "->ly:F\n" +
                "    iget-object v1, p0, " + dragType + "->w:Landroid/view/Window;\n" +
                "    invoke-virtual {v1}, Landroid/view/Window;->getAttributes()Landroid/view/WindowManager$LayoutParams;\n" +
                "    move-result-object v1\n" +
                "    iget v2, v1, Landroid/view/WindowManager$LayoutParams;->x:I\n" +
                "    iput v2, p0, " + dragType + "->sx:I\n" +
                "    iget v2, v1, Landroid/view/WindowManager$LayoutParams;->y:I\n" +
                "    iput v2, p0, " + dragType + "->sy:I\n" +
                "    const/4 v0, 0x1\n" +
                "    return v0\n" +
                "    :mp_drag_chk\n" +
                "    const/4 v1, 0x2\n" +
                "    if-ne v0, v1, :mp_drag_ret0\n" +
                "    iget-object v1, p0, " + dragType + "->w:Landroid/view/Window;\n" +
                "    invoke-virtual {v1}, Landroid/view/Window;->getAttributes()Landroid/view/WindowManager$LayoutParams;\n" +
                "    move-result-object v2\n" +
                "    invoke-virtual {p2}, Landroid/view/MotionEvent;->getRawX()F\n" +
                "    move-result v0\n" +
                "    iget v3, p0, " + dragType + "->lx:F\n" +
                "    sub-float v0, v0, v3\n" +
                "    float-to-int v0, v0\n" +
                "    iget v3, p0, " + dragType + "->sx:I\n" +
                "    add-int v0, v0, v3\n" +
                "    iput v0, v2, Landroid/view/WindowManager$LayoutParams;->x:I\n" +
                "    invoke-virtual {p2}, Landroid/view/MotionEvent;->getRawY()F\n" +
                "    move-result v0\n" +
                "    iget v3, p0, " + dragType + "->ly:F\n" +
                "    sub-float v0, v0, v3\n" +
                "    float-to-int v0, v0\n" +
                "    iget v3, p0, " + dragType + "->sy:I\n" +
                "    add-int v0, v0, v3\n" +
                "    iput v0, v2, Landroid/view/WindowManager$LayoutParams;->y:I\n" +
                "    invoke-virtual {v1, v2}, Landroid/view/Window;->setAttributes(Landroid/view/WindowManager$LayoutParams;)V\n" +
                "    const/4 v0, 0x1\n" +
                "    return v0\n" +
                "    :mp_drag_ret0\n" +
                "    const/4 v0, 0x0\n" +
                "    return v0\n" +
                ".end method\n";
    }

    static String videoViewSmali(String videoType) {
        String heightHex = Integer.toHexString(Float.floatToRawIntBits(220f));
        return ".class public " + videoType + "\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"mpVideo.java\"\n" +
                ".implements Landroid/media/MediaPlayer$OnPreparedListener;\n\n" +
                ".method public constructor <init>()V\n" +
                "    .locals 0\n" +
                "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                "    return-void\n" +
                ".end method\n\n" +
                ".method public onPrepared(Landroid/media/MediaPlayer;)V\n" +
                "    .locals 1\n" +
                "    const/4 v0, 0x1\n" +
                "    invoke-virtual {p1, v0}, Landroid/media/MediaPlayer;->setLooping(Z)V\n" +
                "    invoke-virtual {p1}, Landroid/media/MediaPlayer;->start()V\n" +
                "    return-void\n" +
                ".end method\n\n" +
                ".method public static create(Landroid/content/Context;Ljava/lang/String;)Landroid/view/View;\n" +
                "    .locals 8\n" +
                "    new-instance v0, Ljava/io/File;\n" +
                "    invoke-virtual {p0}, Landroid/content/Context;->getCacheDir()Ljava/io/File;\n" +
                "    move-result-object v1\n" +
                "    invoke-direct {v0, v1, p1}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V\n" +
                "    invoke-virtual {p0}, Landroid/content/Context;->getAssets()Landroid/content/res/AssetManager;\n" +
                "    move-result-object v1\n" +
                "    invoke-virtual {v1, p1}, Landroid/content/res/AssetManager;->open(Ljava/lang/String;)Ljava/io/InputStream;\n" +
                "    move-result-object v1\n" +
                "    new-instance v2, Ljava/io/FileOutputStream;\n" +
                "    invoke-direct {v2, v0}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V\n" +
                "    const/16 v3, 0x4000\n" +
                "    new-array v3, v3, [B\n" +
                "    :mp_vid_loop\n" +
                "    invoke-virtual {v1, v3}, Ljava/io/InputStream;->read([B)I\n" +
                "    move-result v4\n" +
                "    if-ltz v4, :mp_vid_done\n" +
                "    const/4 v5, 0x0\n" +
                "    invoke-virtual {v2, v3, v5, v4}, Ljava/io/FileOutputStream;->write([BII)V\n" +
                "    goto :mp_vid_loop\n" +
                "    :mp_vid_done\n" +
                "    invoke-virtual {v2}, Ljava/io/FileOutputStream;->close()V\n" +
                "    invoke-virtual {v1}, Ljava/io/InputStream;->close()V\n" +
                "    new-instance v1, Landroid/widget/FrameLayout;\n" +
                "    invoke-direct {v1, p0}, Landroid/widget/FrameLayout;-><init>(Landroid/content/Context;)V\n" +
                "    new-instance v2, Landroid/widget/VideoView;\n" +
                "    invoke-direct {v2, p0}, Landroid/widget/VideoView;-><init>(Landroid/content/Context;)V\n" +
                "    new-instance v3, " + videoType + "\n" +
                "    invoke-direct {v3}, " + videoType + "-><init>()V\n" +
                "    invoke-virtual {v2, v3}, Landroid/widget/VideoView;->setOnPreparedListener(Landroid/media/MediaPlayer$OnPreparedListener;)V\n" +
                "    invoke-virtual {v0}, Ljava/io/File;->getAbsolutePath()Ljava/lang/String;\n" +
                "    move-result-object v0\n" +
                "    invoke-virtual {v2, v0}, Landroid/widget/VideoView;->setVideoPath(Ljava/lang/String;)V\n" +
                "    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;\n" +
                "    move-result-object v3\n" +
                "    invoke-virtual {v3}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;\n" +
                "    move-result-object v3\n" +
                "    const/4 v4, 0x1\n" +
                "    const v5, 0x" + heightHex + "\n" +
                "    invoke-static {v4, v5, v3}, Landroid/util/TypedValue;->applyDimension(IFLandroid/util/DisplayMetrics;)F\n" +
                "    move-result v5\n" +
                "    float-to-int v5, v5\n" +
                "    const/4 v4, -0x1\n" +
                "    new-instance v6, Landroid/widget/FrameLayout$LayoutParams;\n" +
                "    const/16 v7, 0x11\n" +
                "    invoke-direct {v6, v4, v5, v7}, Landroid/widget/FrameLayout$LayoutParams;-><init>(III)V\n" +
                "    invoke-virtual {v1, v2, v6}, Landroid/widget/FrameLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V\n" +
                "    invoke-virtual {v2}, Landroid/widget/VideoView;->requestFocus()Z\n" +
                "    invoke-virtual {v2}, Landroid/widget/VideoView;->start()V\n" +
                "    return-object v1\n" +
                ".end method\n";
    }

    static String fontWalkType(String classDescriptor) {
        return classDescriptor.substring(0, classDescriptor.length() - 1) + "$mpFontWalk;";
    }

    static boolean needsFontWalk(DialogOptions opts) {
        if (opts == null || opts.advanced) return false;
        return (opts.dlgFont != null && !opts.dlgFont.isEmpty())
                || (opts.dlgFontPath != null && !opts.dlgFontPath.isEmpty());
    }

    static String fontWalkSmali(String walkType) {
        return ".class public " + walkType + "\n" +
                ".super Ljava/lang/Object;\n" +
                ".source \"mpFontWalk.java\"\n\n" +
                ".method public constructor <init>()V\n" +
                "    .locals 0\n" +
                "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                "    return-void\n" +
                ".end method\n\n" +
                ".method public static applyAll(Landroid/view/View;Landroid/graphics/Typeface;)V\n" +
                "    .locals 3\n" +
                "    instance-of v0, p0, Landroid/widget/TextView;\n" +
                "    if-eqz v0, :mp_fw_kids\n" +
                "    check-cast p0, Landroid/widget/TextView;\n" +
                "    invoke-virtual {p0, p1}, Landroid/widget/TextView;->setTypeface(Landroid/graphics/Typeface;)V\n" +
                "    return-void\n" +
                "    :mp_fw_kids\n" +
                "    instance-of v0, p0, Landroid/view/ViewGroup;\n" +
                "    if-eqz v0, :mp_fw_end\n" +
                "    check-cast p0, Landroid/view/ViewGroup;\n" +
                "    invoke-virtual {p0}, Landroid/view/ViewGroup;->getChildCount()I\n" +
                "    move-result v0\n" +
                "    const/4 v1, 0x0\n" +
                "    :mp_fw_loop\n" +
                "    if-ge v1, v0, :mp_fw_end\n" +
                "    invoke-virtual {p0, v1}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;\n" +
                "    move-result-object v2\n" +
                "    if-eqz v2, :mp_fw_next\n" +
                "    invoke-static {v2, p1}, " + walkType + "->applyAll(Landroid/view/View;Landroid/graphics/Typeface;)V\n" +
                "    :mp_fw_next\n" +
                "    add-int/lit8 v1, v1, 0x1\n" +
                "    goto :mp_fw_loop\n" +
                "    :mp_fw_end\n" +
                "    return-void\n" +
                ".end method\n";
    }

    static Map<String, byte[]> collectFontAssets(DialogOptions dialog) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        if (dialog == null) return out;
        List<String> paths = new ArrayList<>();
        if (dialog.dlgFontPath != null && !dialog.dlgFontPath.isEmpty()) paths.add(dialog.dlgFontPath);
        if (dialog.widgets != null) {
            for (AdvWidget w : dialog.widgets) {
                if (w != null && w.fontPath != null && !w.fontPath.isEmpty() && !paths.contains(w.fontPath)) {
                    paths.add(w.fontPath);
                }
            }
        }
        for (String p : paths) {
            try {
                File f = new File(p);
                if (!f.isFile() || f.length() <= 0 || f.length() > 10 * 1024 * 1024) continue;
                byte[] b = readBytes(f);
                if (b.length > 0) out.put(p, b);
            } catch (Exception e) {
                debug("font skip " + p + ": " + e);
            }
        }
        return out;
    }

    private static byte[] readBytes(File f) throws IOException {
        try (InputStream is = new FileInputStream(f);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static File inferPatchRoot(File smaliFile, String className) {
        File dir = smaliFile.getParentFile();
        if (dir == null || className == null || !className.contains(".")) return dir;
        String[] parts = className.split("\\.");
        for (int i = parts.length - 2; i >= 0; i--) {
            if (dir != null && parts[i].equals(dir.getName())) {
                dir = dir.getParentFile();
            } else {
                break;
            }
        }
        return dir != null ? dir : smaliFile.getParentFile();
    }

    private static boolean patchOneSmali(Context context, File smaliFile, String className,
                                         ToastOptions toast, DialogOptions dialog,
                                         Map<String, String> fontAssets,
                                         APKLogger logger, List<File> patchedFiles) throws IOException {
        ProtectSpec ps = isProtectCode(toast, dialog) ? ProtectSpec.createStandalone() : null;
        return patchOneSmali(context, smaliFile, inferPatchRoot(smaliFile, className), className,
                toast, dialog, fontAssets, ps, new boolean[1], logger, patchedFiles);
    }

    static boolean patchOneSmali(Context context, File smaliFile, File patchDir, String className,
                                 ToastOptions toast, DialogOptions dialog,
                                 Map<String, String> fontAssets, ProtectSpec ps,
                                 boolean[] helperEmitted, APKLogger logger,
                                 List<File> patchedFiles) throws IOException {
        String content = readFile(smaliFile);
        debug("patching " + smaliFile.getAbsolutePath() + " (" + content.length() + " chars)");
        if (content.isEmpty()) throw new IOException("Empty smali file: " + smaliFile.getName());
        boolean protectCode = ps != null;
        if (!protectCode && (content.contains("onCreate$mpmanager")
                || content.contains("onResume$mpmanager")
                || content.contains("mpResumeOnce"))) {
            if (logger != null && context != null) logger.logMessage(context.getString(R.string.logger_skipped_patched, className));
            return false;
        }
        Matcher clsHeaderMatcher = Pattern.compile("(?m)^\\s*\\.class\\s+([^\\r\\n]+)").matcher(content);
        if (clsHeaderMatcher.find()) {
            String header = " " + clsHeaderMatcher.group(1) + " ";
            if (header.contains(" interface ") || header.contains(" abstract ")) {
                if (logger != null && context != null) logger.logMessage(context.getString(R.string.logger_skipped_abstract, className));
                return false;
            }
        }

        boolean resumeMode = "onResume".equals(resolveHookMethod(toast, dialog));
        String superDescriptor = "Landroid/app/Activity;";
        Matcher matcher;
        boolean hasOrigMethod;
        String flags;
        if (resumeMode) {
            Matcher superMatcher = Pattern.compile("(?m)^\\s*\\.super\\s+(L[^;\\s]+;)").matcher(content);
            if (superMatcher.find()) superDescriptor = superMatcher.group(1);
            Pattern resumePattern = Pattern.compile("(?m)^(\\s*\\.method\\s+)(.*)\\bonResume\\(\\)V\\s*$");
            matcher = resumePattern.matcher(content);
            hasOrigMethod = matcher.find();
            flags = hasOrigMethod ? matcher.group(2).trim() : "public";
        } else {
            Pattern methodPattern = Pattern.compile("(?m)^(\\s*\\.method\\s+)(.*)\\bonCreate\\(Landroid/os/Bundle;\\)V\\s*$");
            matcher = methodPattern.matcher(content);
            if (!matcher.find()) {
                if (logger != null && context != null) logger.logMessage(context.getString(R.string.logger_no_oncreate, className));
                debug("no onCreate(Bundle) in " + smaliFile.getName());
                return false;
            }
            hasOrigMethod = true;
            flags = matcher.group(2).trim();
        }
        if (hasOrigMethod && flags.contains("abstract")) {
            if (logger != null && context != null) logger.logMessage(context.getString(R.string.logger_skipped_abstract, className));
            return false;
        }

        String classDescriptor = "L" + className.replace('.', '/') + ";";
        String targetClassDesc = protectCode ? ps.helperClassDesc : classDescriptor;
        String urlListener = null;
        String urlViewListener = null;
        String noshowListener = null;
        String blinkListener = null;
        String dismissListener = null;
        String waveRgb = null;
        String dragListener = null;
        String videoHelper = null;
        String countdownHelper = null;

        boolean emitHelpersNow = !protectCode || (helperEmitted == null || !helperEmitted[0]);
        if (dialog != null && emitHelpersNow) {
            if (hasUrl(dialog)) {
                urlListener = targetTypeDesc(classDescriptor, "$mpUrl", ps);
                emitSmaliClass(context, patchDir, smaliFile, urlListener, urlListenerSmali(urlListener, ps), protectCode, logger, patchedFiles);
            }
            if (hasAdvButtonUrl(dialog)) {
                urlViewListener = targetTypeDesc(classDescriptor, "$mpUrlView", ps);
                emitSmaliClass(context, patchDir, smaliFile, urlViewListener, urlViewListenerSmali(urlViewListener, ps), protectCode, logger, patchedFiles);
            }
            if (needsFontWalk(dialog)) {
                String fontWalk = protectCode ? ps.fontWalkDesc : fontWalkType(classDescriptor);
                emitSmaliClass(context, patchDir, smaliFile, fontWalk, fontWalkSmali(fontWalk), protectCode, logger, patchedFiles);
            }
            if (dialog.dontShowAgain) {
                noshowListener = targetTypeDesc(classDescriptor, "$mpNoShow", ps);
                emitSmaliClass(context, patchDir, smaliFile, noshowListener, noshowListenerSmali(noshowListener, ps), protectCode, logger, patchedFiles);
            }
            float animBorderDp = dialog.borderWidthDp;
            if (dialog.animBorder && animBorderDp <= 0) animBorderDp = 2f;
            if ((dialog.animBorder && !dialog.rainbowAnim && animBorderDp > 0) || needsBlink(dialog)) {
                blinkListener = targetTypeDesc(classDescriptor, "$mpBlink", ps);
                emitSmaliClass(context, patchDir, smaliFile, blinkListener, blinkSmali(blinkListener), protectCode, logger, patchedFiles);
            }
            if (needsWave(dialog, animBorderDp)) {
                waveRgb = targetTypeDesc(classDescriptor, "$mpRgb", ps);
                String waveTick = targetTypeDesc(classDescriptor, "$mpRgbTick", ps);
                emitSmaliClass(context, patchDir, smaliFile, waveRgb, waveRgbSmali(waveRgb, waveTick), protectCode, logger, patchedFiles);
                emitSmaliClass(context, patchDir, smaliFile, waveTick, waveTickSmali(waveTick, waveRgb), protectCode, logger, patchedFiles);
            }
            if (needsDismiss(dialog)) {
                dismissListener = targetTypeDesc(classDescriptor, "$mpDismiss", ps);
                emitSmaliClass(context, patchDir, smaliFile, dismissListener, dismissSmali(dismissListener), protectCode, logger, patchedFiles);
            }
            if (dialog.draggable) {
                dragListener = targetTypeDesc(classDescriptor, "$mpDrag", ps);
                emitSmaliClass(context, patchDir, smaliFile, dragListener, dragListenerSmali(dragListener), protectCode, logger, patchedFiles);
            }
            if (hasVideo(dialog)) {
                videoHelper = targetTypeDesc(classDescriptor, "$mpVideo", ps);
                emitSmaliClass(context, patchDir, smaliFile, videoHelper, videoViewSmali(videoHelper), protectCode, logger, patchedFiles);
            }
            if (dialog.countdown && !dialog.advanced && dialog.countdownSeconds > 0) {
                countdownHelper = targetTypeDesc(classDescriptor, "$mpCountdown", ps);
                emitSmaliClass(context, patchDir, smaliFile, countdownHelper, countdownSmali(countdownHelper), protectCode, logger, patchedFiles);
            }
        }

        if (protectCode && emitHelpersNow) {
            StringBuilder helperClass = new StringBuilder();
            helperClass.append(".class public ").append(ps.helperClassDesc).append("\n");
            helperClass.append(".super Ljava/lang/Object;\n\n");
            helperClass.append(decodeHelperMethodSmali(ps)).append("\n");
            boolean hasToast = toast != null;
            boolean hasDialog = dialog != null;
            if (hasToast && hasDialog) {
                helperClass.append(toastHelperSmali(toast, ps, ps.toastMethod)).append("\n");
                helperClass.append(dialogHelperSmali(ps.helperClassDesc, dialog, urlListener, urlViewListener,
                        noshowListener, blinkListener, dismissListener, fontAssets, waveRgb, dragListener,
                        videoHelper, countdownHelper, ps, ps.dialogMethod)).append("\n");
                helperClass.append(".method public static ").append(ps.entryMethod).append("(Landroid/content/Context;)V\n");
                helperClass.append("    .locals 0\n");
                helperClass.append("    invoke-static {p0}, ").append(ps.helperClassDesc).append("->").append(ps.toastMethod).append("(Landroid/content/Context;)V\n");
                helperClass.append("    invoke-static {p0}, ").append(ps.helperClassDesc).append("->").append(ps.dialogMethod).append("(Landroid/content/Context;)V\n");
                helperClass.append("    return-void\n");
                helperClass.append(".end method\n");
            } else if (hasToast) {
                helperClass.append(toastHelperSmali(toast, ps, ps.entryMethod));
            } else if (hasDialog) {
                helperClass.append(dialogHelperSmali(ps.helperClassDesc, dialog, urlListener, urlViewListener,
                        noshowListener, blinkListener, dismissListener, fontAssets, waveRgb, dragListener,
                        videoHelper, countdownHelper, ps, ps.entryMethod));
            }
            emitSmaliClass(context, patchDir, smaliFile, ps.helperClassDesc, helperClass.toString(), true, logger, patchedFiles);
            if (helperEmitted != null && helperEmitted.length > 0) helperEmitted[0] = true;
        }

        StringBuilder insert = new StringBuilder();
        if (!protectCode) {
            if (toast != null) insert.append(toastHelperSmali(toast)).append("\n");
            if (dialog != null) {
                insert.append(dialogHelperSmali(targetClassDesc, dialog, urlListener, urlViewListener,
                        noshowListener, blinkListener, dismissListener, fontAssets, waveRgb, dragListener,
                        videoHelper, countdownHelper)).append("\n");
            }
        }
        String effectiveFlags = flags.isEmpty() ? "protected" : flags;
        if (resumeMode) {
            insert.append(resumeWrapperSmali(classDescriptor, effectiveFlags, hasOrigMethod, superDescriptor,
                    toast != null, dialog != null, ps));
        } else {
            insert.append(wrapperSmali(classDescriptor, effectiveFlags, toast != null, dialog != null, ps));
        }

        String patched = content;
        if (hasOrigMethod) {
            String origName = resumeMode
                    ? (protectCode ? ps.origRename : "onResume$mpmanager")
                    : (protectCode ? ps.origRename : "onCreate$mpmanager");
            String sig = resumeMode ? "()V" : "(Landroid/os/Bundle;)V";
            patched = matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + "private " + origName + sig));
        }
        if (resumeMode) {
            patched = insertGuardFieldSmali(patched, protectCode ? ps.guardField : "mpResumeOnce");
        }
        if (!patched.endsWith("\n")) patched += "\n";
        patched = patched + insert;
        writeFile(smaliFile, patched);
        patchedFiles.add(smaliFile);
        debug("patched " + smaliFile.getName() + ", generated smali:\n" + shorten(insert.toString()));
        if (logger != null && context != null) {
            logger.logMessage(context.getString(R.string.logger_injected_overlay, className));
        }
        return true;
    }

    private static String targetTypeDesc(String classDescriptor, String suffix, ProtectSpec ps) {
        if (ps != null) return ps.nextType();
        return classDescriptor.substring(0, classDescriptor.length() - 1) + suffix + ";";
    }

    private static void emitSmaliClass(Context context, File patchDir, File activitySmaliFile,
                                       String descriptor, String rawContent, boolean stripSource,
                                       APKLogger logger, List<File> patchedFiles) throws IOException {
        String content = stripSource ? stripMpSource(rawContent) : rawContent;
        File out;
        if (patchDir != null && descriptor.startsWith("L") && descriptor.endsWith(";")) {
            String rel = descriptor.substring(1, descriptor.length() - 1) + ".smali";
            out = new File(patchDir, rel);
        } else {
            String simple = descriptor.substring(descriptor.lastIndexOf('/') + 1, descriptor.length() - 1);
            out = new File(activitySmaliFile.getParentFile(), simple + ".smali");
        }
        writeFile(out, content);
        patchedFiles.add(out);
        if (logger != null && context != null) {
            logger.logMessage(context.getString(R.string.logger_generated, descriptor));
        }
    }

    private static boolean hasVideo(DialogOptions opts) {
        return opts != null && notEmpty(opts.videoPath);
    }

    private static boolean hasUrl(DialogOptions opts) {
        return notEmpty(opts.positiveUrl) || notEmpty(opts.negativeUrl) || notEmpty(opts.neutralUrl);
    }

    private static boolean hasAdvButtonUrl(DialogOptions opts) {
        if (opts.advanced && opts.widgets != null) {
            for (AdvWidget w : opts.widgets) {
                if (w != null && "button".equals(w.kind) && notEmpty(w.url)) return true;
            }
        }
        return false;
    }

    private static boolean needsDismiss(DialogOptions opts) {
        if (!opts.advanced || opts.widgets == null) return false;
        for (AdvWidget w : opts.widgets) {
            if (w != null && "button".equals(w.kind)
                    && (w.url == null || w.url.trim().isEmpty())) return true;
        }
        return false;
    }

    private static boolean needsBlink(DialogOptions opts) {
        if (!opts.advanced || opts.widgets == null) return false;
        for (AdvWidget w : opts.widgets) {
            if (w != null && w.btnAnim && !opts.rainbowAnim) return true;
        }
        return false;
    }

    private static boolean needsWave(DialogOptions opts, float animBorderDp) {
        if (opts == null) return false;
        if (opts.animBorder && animBorderDp > 0) return true;
        if (opts.advanced && opts.widgets != null) {
            for (AdvWidget w : opts.widgets) {
                if (w != null && "button".equals(w.kind) && w.btnAnim) return true;
            }
        }
        return false;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static Set<String> findDexEntriesForClasses(Context context, File inputApk,
                                                        List<String> classNames, APKLogger logger) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        if (classNames == null || classNames.isEmpty()) return result;
        Opcodes opcodes = Opcodes.getDefault();
        MultiDexContainer<? extends DexBackedDexFile> container = DexFileFactory.loadDexContainer(inputApk, opcodes);
        Set<String> classDescriptors = new LinkedHashSet<>();
        for (String className : classNames) classDescriptors.add("L" + className.replace('.', '/') + ";");
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entryName);
            if (dexEntry == null) continue;
            for (ClassDef classDef : dexEntry.getDexFile().getClasses()) {
                if (classDescriptors.contains(classDef.getType())) {
                    result.add(entryName);
                    break;
                }
            }
        }
        if (logger != null && context != null) logger.logMessage(context.getString(R.string.logger_target_dex, result.size()));
        return result;
    }

    private static int detectDexApi(File inputApk, String entryName) {
        try (ZipFile zin = new ZipFile(inputApk)) {
            FileHeader header = zin.getFileHeader(entryName);
            if (header == null) return 28;
            try (InputStream is = zin.getInputStream(header)) {
                byte[] magic = new byte[8];
                int read = 0;
                while (read < 8) {
                    int n = is.read(magic, read, 8 - read);
                    if (n < 0) break;
                    read += n;
                }
                String m = new String(magic, 0, read, StandardCharsets.US_ASCII);
                debug("dex magic for " + entryName + ": " + m.replace("\0", "\\0"));
                if (m.startsWith("dex\n") && m.length() >= 7) {
                    int ver = Integer.parseInt(m.substring(4, 7));
                    int api = VersionMap.mapDexVersionToApi(ver);
                    if (api > 0) return api;
                }
            }
        } catch (Exception e) {
            debug("dex version detect failed: " + e);
        }
        return 28;
    }

    private static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buf = new char[8192];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
        }
        return sb.toString();
    }

    private static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else //noinspection ResultOfMethodCallIgnored
                    f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }
}
