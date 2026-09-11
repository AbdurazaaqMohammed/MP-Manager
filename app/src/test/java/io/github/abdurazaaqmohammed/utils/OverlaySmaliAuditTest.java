package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverlaySmaliAuditTest {

    private static final String CLS = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity;";
    private static final String URL = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpUrl;";
    private static final String URLVIEW = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpUrlView;";
    private static final String NOSHOW = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpNoShow;";
    private static final String BLINK = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpBlink;";
    private static final String DISMISS = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpDismiss;";
    private static final String RGB = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpRgb;";
    private static final String TICK = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpRgbTick;";
    private static final String FONTWALK = "Lcom/abdurazaagmohammed/AntiSplit/main/MainActivity$mpFontWalk;";

    @Test
    public void regularDialogAnimatedBorder() {
        OverlayInjectorUtil.DialogOptions o = new OverlayInjectorUtil.DialogOptions();
        o.title = "T";
        o.message = "M";
        o.positive = "OK";
        o.positiveUrl = "https://example.com";
        o.cancelable = false;
        o.dontShowAgain = true;
        o.bgEnabled = true;
        o.bgColor1 = 0xFF112233;
        o.bgGradient = true;
        o.bgColor2 = 0xFF445566;
        o.cornerRadiusDp = 16f;
        o.borderWidthDp = 0f;
        o.borderColor = 0xFFFFFFFF;
        o.animBorder = true;
        o.animColorA = 0xFF0000FF;
        o.animColorB = 0xFFFF0000;
        o.animSpeedMs = 300;
        String smali = OverlayInjectorUtil.dialogHelperSmali(
                CLS, o, URL, null, NOSHOW, BLINK, DISMISS, new HashMap<String, String>(), null);
        auditMethod(smali, "regularDialogAnimatedBorder");
        assertFalse(smali, smali.contains(".catch"));
    }

    @Test
    public void regularDialogRainbowBorder() {
        OverlayInjectorUtil.DialogOptions o = new OverlayInjectorUtil.DialogOptions();
        o.title = "T";
        o.message = "M";
        o.positive = "OK";
        o.bgEnabled = true;
        o.bgColor1 = 0xFF112233;
        o.cornerRadiusDp = 16f;
        o.animBorder = true;
        o.rainbowAnim = true;
        o.animSpeedMs = 200;
        String smali = OverlayInjectorUtil.dialogHelperSmali(
                CLS, o, null, null, null, BLINK, null, new HashMap<String, String>(), null);
        auditMethod(smali, "regularDialogRainbowBorder");
        assertTrue(smali, smali.contains("0xffff0000"));
        assertTrue(smali, smali.contains("0xff00ff00"));
        assertTrue(smali, smali.contains("0xff0000ff"));
    }

    @Test
    public void regularDialogWithFontAndImage() {
        OverlayInjectorUtil.DialogOptions o = new OverlayInjectorUtil.DialogOptions();
        o.title = "T";
        o.message = "M";
        o.positive = "OK";
        o.negative = "No";
        o.neutral = "Later";
        o.imageBase64 = "aGk=";
        o.bgEnabled = true;
        o.bgColor1 = 0xFF112233;
        o.cornerRadiusDp = 8f;
        o.borderWidthDp = 2f;
        o.borderColor = 0xFF000000;
        o.dlgFont = "serif";
        o.titleColor = 0xFFFF0000;
        o.msgColor = 0xFF00FF00;
        o.btnColor = 0xFF0000FF;
        o.html = true;
        String smali = OverlayInjectorUtil.dialogHelperSmali(
                CLS, o, null, null, null, null, null, new HashMap<String, String>(), null);
        auditMethod(smali, "regularDialogWithFontAndImage");
        assertFalse(smali, smali.contains("createFromFile"));
        assertFalse(smali, smali.contains(".catch"));
        assertTrue(smali, smali.contains("$mpFontWalk;"));
        assertTrue(smali, smali.contains("applyAll(Landroid/view/View;Landroid/graphics/Typeface;)V"));
    }

    @Test
    public void regularDialogWithFontFileUsesAsset() throws Exception {
        File font = File.createTempFile("testfont", ".ttf");
        try {
            FileOutputStream fos = new FileOutputStream(font);
            fos.write(new byte[]{0, 1, 0, 0, 0, 10});
            fos.close();
            OverlayInjectorUtil.DialogOptions o = new OverlayInjectorUtil.DialogOptions();
            o.title = "T";
            o.message = "M";
            o.positive = "OK";
            o.dlgFontPath = font.getAbsolutePath();
            Map<String, byte[]> bytes = OverlayInjectorUtil.collectFontAssets(o);
            assertTrue("font bytes collected", bytes.containsKey(font.getAbsolutePath()));
            Map<String, String> assets = new LinkedHashMap<>();
            assets.put(font.getAbsolutePath(), "mpfont_0.ttf");
            String smali = OverlayInjectorUtil.dialogHelperSmali(
                    CLS, o, null, null, null, null, null, assets, null);
            auditMethod(smali, "regularDialogWithFontFileUsesAsset");
            assertTrue(smali, smali.contains("createFromAsset"));
            assertTrue(smali, smali.contains("mpfont_0.ttf"));
            assertFalse(smali, smali.contains("createFromFile"));
        } finally {
            font.delete();
        }
    }

    @Test
    public void advancedDialogEverything() {
        OverlayInjectorUtil.DialogOptions o = new OverlayInjectorUtil.DialogOptions();
        o.advanced = true;
        o.cancelable = true;
        o.dontShowAgain = true;
        o.bgEnabled = true;
        o.bgColor1 = 0xFF000000;
        o.cornerRadiusDp = 12f;
        o.animBorder = true;
        o.animColorA = 0xFF00FF00;
        o.animColorB = 0xFFFFFF00;
        o.rainbowAnim = true;
        o.animSpeedMs = 500;
        o.dlgFont = "monospace";
        o.widgets = new ArrayList<>();
        OverlayInjectorUtil.AdvWidget text = new OverlayInjectorUtil.AdvWidget();
        text.kind = "text";
        text.text = "<b>Hello</b>";
        text.textSizeSp = 18f;
        text.textColor = 0xFFFF0000;
        text.fontStyle = 3;
        text.leftDp = 8;
        text.topDp = 8;
        o.widgets.add(text);
        OverlayInjectorUtil.AdvWidget plain = new OverlayInjectorUtil.AdvWidget();
        plain.kind = "text";
        plain.fontStyle = 1;
        plain.leftDp = 4;
        plain.topDp = 60;
        o.widgets.add(plain);
        OverlayInjectorUtil.AdvWidget btn = new OverlayInjectorUtil.AdvWidget();
        btn.kind = "button";
        btn.text = "Visit";
        btn.textSizeSp = 14f;
        btn.textColor = 0xFFFFFFFF;
        btn.btnBg = 0xFF2196F3;
        btn.btnBg2 = 0xFF0D47A1;
        btn.btnCornerRadiusDp = 12f;
        btn.btnBorderWidthDp = 2f;
        btn.btnBorderColor = 0xFFFFFFFF;
        btn.btnPaddingDp = 8;
        btn.btnAnim = true;
        btn.url = "https://example.com";
        btn.leftDp = 16;
        btn.topDp = 100;
        o.widgets.add(btn);
        OverlayInjectorUtil.AdvWidget dismiss = new OverlayInjectorUtil.AdvWidget();
        dismiss.kind = "button";
        dismiss.text = "Close";
        dismiss.leftDp = 16;
        dismiss.topDp = 160;
        o.widgets.add(dismiss);
        OverlayInjectorUtil.AdvWidget img = new OverlayInjectorUtil.AdvWidget();
        img.kind = "image";
        img.imageB64 = "aGk=";
        img.leftDp = 0;
        img.topDp = 200;
        o.widgets.add(img);
        String smali = OverlayInjectorUtil.dialogHelperSmali(
                CLS, o, URL, URLVIEW, NOSHOW, BLINK, DISMISS, new HashMap<String, String>(), null);
        auditMethod(smali, "advancedDialogEverything");
        assertFalse(smali, smali.contains("fill-array-data"));
        assertFalse(smali, smali.contains("createFromFile"));
        assertFalse(smali, smali.contains("create(Ljava/lang/String;)"));
        assertFalse(smali, smali.contains("mpFontWalk"));
    }

    @Test
    public void waveRgbBorder() {
        OverlayInjectorUtil.DialogOptions o = new OverlayInjectorUtil.DialogOptions();
        o.title = "T";
        o.message = "M";
        o.positive = "OK";
        o.bgEnabled = true;
        o.bgColor1 = 0xFF101010;
        o.cornerRadiusDp = 14f;
        o.animBorder = true;
        o.rainbowAnim = true;
        o.animSpeedMs = 300;
        o.advanced = true;
        o.widgets = new ArrayList<>();
        OverlayInjectorUtil.AdvWidget btn = new OverlayInjectorUtil.AdvWidget();
        btn.kind = "button";
        btn.text = "Go";
        btn.textSizeSp = 14f;
        btn.btnBg = 0xFF222222;
        btn.btnCornerRadiusDp = 10f;
        btn.btnAnim = true;
        btn.url = "https://example.com";
        btn.leftDp = 16;
        btn.topDp = 40;
        o.widgets.add(btn);
        String smali = OverlayInjectorUtil.dialogHelperSmali(
                CLS, o, null, URLVIEW, null, null, DISMISS, new HashMap<String, String>(), RGB);
        auditMethod(smali, "waveRgbBorderDialog");
        assertTrue(smali, smali.contains("$mpRgb;"));
        assertTrue(smali, smali.contains("-><init>(IFF)V"));
        assertTrue(smali, smali.contains("$mpUrlView;"));
        assertTrue(smali, smali.contains("rainbow:Z"));
        assertTrue(smali, smali.contains("move-object v2, v7"));
        auditMethod(OverlayInjectorUtil.waveRgbSmali(RGB, TICK), "waveRgbClass");
        assertTrue(OverlayInjectorUtil.waveRgbSmali(RGB, TICK).contains(":mp_rgb_custom"));
        assertTrue(OverlayInjectorUtil.waveRgbSmali(RGB, TICK).contains(":mp_rgb_seven"));
        assertTrue(OverlayInjectorUtil.waveRgbSmali(RGB, TICK).contains(":mp_rgb_grad"));
        assertTrue(OverlayInjectorUtil.waveRgbSmali(RGB, TICK).contains("colors:[I"));
        auditMethod(OverlayInjectorUtil.waveTickSmali(TICK, RGB), "waveTickClass");
        auditMethod(OverlayInjectorUtil.urlViewListenerSmali(URLVIEW), "urlViewListener");
        o.rainbowAnim = false;
        o.animColorA = 0xFF123456;
        o.animColorB = 0xFF654321;
        o.animExtraColors = new ArrayList<>(java.util.Arrays.asList(0xFF111111, 0xFF222222, 0xFF333333));
        btn.btnAnimColors = new ArrayList<>(java.util.Arrays.asList(0xFF445566, 0xFF778899));
        btn.btnAnimSpeedMs = 111;
        btn.btnAnimRainbow = false;
        String smali2 = OverlayInjectorUtil.dialogHelperSmali(
                CLS, o, null, URLVIEW, null, null, DISMISS, new HashMap<String, String>(), RGB);
        auditMethod(smali2, "waveMultiColorBorderDialog");
        assertTrue(smali2, smali2.contains("0xff123456"));
        assertTrue(smali2, smali2.contains("0xff654321"));
        assertTrue(smali2, smali2.contains("0xff111111"));
        assertTrue(smali2, smali2.contains("0xff222222"));
        assertTrue(smali2, smali2.contains("0xff333333"));
        assertTrue(smali2, smali2.contains("colors:[I"));
        assertTrue(smali2, smali2.contains("0xff445566"));
        assertTrue(smali2, smali2.contains("0xff778899"));
        assertTrue(smali2, smali2.contains("0x6fL"));
    }

    @Test
    public void copyWidgetKeepsAllStyle() {
        OverlayInjectorUtil.AdvWidget w = new OverlayInjectorUtil.AdvWidget();
        w.kind = "button";
        w.text = "Hi";
        w.textSizeSp = 15f;
        w.textColor = 0xFF112233;
        w.fontStyle = 3;
        w.font = "serif";
        w.fontPath = "/x/y.ttf";
        w.imageB64 = "aGk=";
        w.btnAction = "Open URL";
        w.url = "https://example.com";
        w.btnBg = 1;
        w.btnBg2 = 2;
        w.btnCornerRadiusDp = 3f;
        w.btnBorderWidthDp = 4f;
        w.btnBorderColor = 5;
        w.btnPaddingDp = 6;
        w.btnAnim = true;
        w.btnAnimColors = new ArrayList<>(java.util.Arrays.asList(7, 8));
        w.btnAnimSpeedMs = 111;
        w.btnAnimRainbow = true;
        w.leftDp = 9;
        w.topDp = 10;
        OverlayInjectorUtil.AdvWidget c = OverlayInjectorUtil.copyWidget(w);
        assertTrue(c != w);
        assertTrue(c.kind.equals("button"));
        assertTrue(c.text.equals("Hi"));
        assertTrue(c.textSizeSp == 15f);
        assertTrue(c.textColor == 0xFF112233);
        assertTrue(c.fontStyle == 3);
        assertTrue(c.font.equals("serif"));
        assertTrue(c.fontPath.equals("/x/y.ttf"));
        assertTrue(c.imageB64.equals("aGk="));
        assertTrue(c.btnAction.equals("Open URL"));
        assertTrue(c.url.equals("https://example.com"));
        assertTrue(c.btnBg == 1 && c.btnBg2 == 2);
        assertTrue(c.btnCornerRadiusDp == 3f);
        assertTrue(c.btnBorderWidthDp == 4f);
        assertTrue(c.btnBorderColor == 5);
        assertTrue(c.btnPaddingDp == 6);
        assertTrue(c.btnAnim && c.btnAnimRainbow && c.btnAnimSpeedMs == 111);
        assertTrue(c.btnAnimColors.equals(w.btnAnimColors));
        w.btnAnimColors.add(9);
        assertTrue(c.btnAnimColors.size() == 2);
        assertTrue(c.leftDp == 9 && c.topDp == 10);
        assertTrue(OverlayInjectorUtil.copyWidget(null) == null);
    }

    @Test
    public void rainbowColorsSane() {
        int[] c = OverlayInjectorUtil.rainbowColors();
        assertTrue(c.length == 12);
        assertTrue(Integer.toHexString(c[0]), c[0] == 0xFFFF0000);
        assertTrue(Integer.toHexString(c[4]), c[4] == 0xFF00FF00);
        assertTrue(Integer.toHexString(c[8]), c[8] == 0xFF0000FF);
    }

    @Test
    public void toastAndListeners() {
        OverlayInjectorUtil.ToastOptions t = new OverlayInjectorUtil.ToastOptions();
        t.message = "hi";
        t.longDuration = true;
        t.gravity = 17;
        t.xOffset = 5;
        t.yOffset = 10;
        auditMethod(OverlayInjectorUtil.toastHelperSmali(t), "toast");
        auditMethod(OverlayInjectorUtil.urlListenerSmali(URL), "urlListener");
        auditMethod(OverlayInjectorUtil.noshowListenerSmali(NOSHOW), "noshowListener");
        auditMethod(OverlayInjectorUtil.dismissSmali(DISMISS), "dismissListener");
        auditMethod(OverlayInjectorUtil.blinkSmali(BLINK), "blinkListener");
        auditMethod(OverlayInjectorUtil.fontWalkSmali(FONTWALK), "fontWalk");
    }

    // ---------- audit engine ----------

    private static final Pattern METHOD_LINE =
            Pattern.compile("^\\.method\\s.*?(<init>|\\w+)\\(([^)]*)\\)(\\S+)\\s*$");
    private static final Pattern INVOKE =
            Pattern.compile("^\\s*invoke-(\\S+)\\s+\\{([^}]*)\\},\\s*(\\S+)->(\\S+)\\(([^)]*)\\)(\\S+)\\s*$");
    private static final Pattern LABEL_DEF = Pattern.compile("^\\s+:([A-Za-z0-9_$]+)\\s*$");
    private static final Pattern LABEL_REF = Pattern.compile("(?:[:,\\{]\\s*|^\\s*goto(?:/[0-9a-f]+)?\\s+):([A-Za-z0-9_$]+)");

    private static final int T_UNDEF = 0;
    private static final int T_ZERO = 1;
    private static final int T_INT = 2;
    private static final int T_FLOAT = 3;
    private static final int T_NUM = 4;
    private static final int T_REF = 5;
    private static final int T_LONG = 6;
    private static final int T_LONG2 = 7;
    private static final int T_ANY = 8;

    private static int mergeType(int a, int b) {
        if (a == b) return a;
        if (a == T_UNDEF) return b;
        if (b == T_UNDEF) return a;
        return T_ANY;
    }

    private static boolean needRef(int t) {
        return t == T_REF || t == T_ZERO || t == T_ANY;
    }

    private static boolean needI32(int t) {
        return t == T_INT || t == T_ZERO || t == T_NUM || t == T_ANY;
    }

    private static boolean needF32(int t) {
        return t == T_FLOAT || t == T_ZERO || t == T_NUM || t == T_ANY;
    }

    private static boolean needN32(int t) {
        return t == T_INT || t == T_FLOAT || t == T_ZERO || t == T_NUM || t == T_ANY;
    }

    private static List<Integer> paramCats(String params) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == 'L') {
                out.add(T_REF);
                while (i < params.length() && params.charAt(i) != ';') i++;
            } else if (c == '[') {
                out.add(T_REF);
                while (i + 1 < params.length() && params.charAt(i + 1) == '[') i++;
                if (i + 1 < params.length() && params.charAt(i + 1) == 'L') {
                    i++;
                    while (i < params.length() && params.charAt(i) != ';') i++;
                } else {
                    i++;
                }
            } else if (c == 'J' || c == 'D') {
                out.add(T_LONG);
            } else if (c == 'F') {
                out.add(T_FLOAT);
            } else {
                out.add(T_INT);
            }
        }
        return out;
    }

    private static int fieldNeed(String ref) {
        int ci = ref.lastIndexOf(':');
        if (ci < 0 || ci + 1 >= ref.length()) return T_INT;
        char c = ref.charAt(ci + 1);
        if (c == 'L' || c == '[') return T_REF;
        if (c == 'J' || c == 'D') return T_LONG;
        if (c == 'F') return T_FLOAT;
        return T_INT;
    }

    private static boolean checkNeed(String name, int lineNo, String line, String reg, int type, int need) {
        boolean ok = need == T_REF ? needRef(type) : need == T_FLOAT ? needF32(type) : needI32(type);
        if (!ok) {
            fail(name + ":" + lineNo + " register " + reg + " has wrong type for " + line.trim());
        }
        return ok;
    }

    private static void auditMethod(String smali, String name) {
        assertTrue(name + ": empty smali", smali != null && smali.contains(".method"));
        String[] lines = smali.split("\n");
        int locals = -1;
        int params = 0;
        boolean inMethod = false;
        Set<String> defined = new HashSet<>();
        Map<String, Integer> defCount = new HashMap<>();
        List<String> referenced = new ArrayList<>();
        String prevInvokeRet = null;
        Map<String, Integer> types = new HashMap<>();
        Map<String, Map<String, Integer>> labelStates = new HashMap<>();
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String line = raw.trim();
            if (line.startsWith(".method")) {
                inMethod = true;
                Matcher m = METHOD_LINE.matcher(line);
                if (m.matches()) {
                    params = countSlots(m.group(2)) + 1;
                    if (line.contains(" static ")) params--;
                }
                prevInvokeRet = null;
                types.clear();
                labelStates.clear();
                types.put("p0", T_REF);
                for (int i = 1; i < params; i++) types.put("p" + i, T_ANY);
                continue;
            }
            if (!inMethod) continue;
            if (line.startsWith(".end method")) {
                inMethod = false;
                continue;
            }
            if (line.startsWith(".locals")) {
                locals = Integer.parseInt(line.substring(7).trim());
                continue;
            }
            Matcher ld = LABEL_DEF.matcher(raw);
            if (ld.matches()) {
                defined.add(ld.group(1));
                defCount.put(ld.group(1), defCount.getOrDefault(ld.group(1), 0) + 1);
                Map<String, Integer> saved = labelStates.get(ld.group(1));
                if (saved != null) {
                    Map<String, Integer> merged = new HashMap<>(types);
                    for (Map.Entry<String, Integer> e : saved.entrySet()) {
                        merged.put(e.getKey(), mergeType(merged.getOrDefault(e.getKey(), T_UNDEF), e.getValue()));
                    }
                    types = merged;
                }
            }
            if (line.startsWith("goto") || line.startsWith("if-")) {
                int ci = line.lastIndexOf(':');
                if (ci >= 0) {
                    String target = line.substring(ci + 1).trim().split("\\s+")[0];
                    Map<String, Integer> saved = labelStates.get(target);
                    if (saved == null) {
                        saved = new HashMap<>();
                        labelStates.put(target, saved);
                    }
                    for (Map.Entry<String, Integer> e : types.entrySet()) {
                        saved.put(e.getKey(), mergeType(saved.getOrDefault(e.getKey(), T_UNDEF), e.getValue()));
                    }
                }
            }
            Matcher lr = LABEL_REF.matcher(raw);
            while (lr.find()) {
                if (!raw.trim().startsWith(".array-data")
                        && !raw.trim().startsWith(".end array-data")) {
                    referenced.add(lr.group(1) + "@" + lineNo);
                }
            }
            if (line.startsWith("const-wide") || line.startsWith("const-string")
                    || line.startsWith("const/") || line.startsWith("const ")) {
                checkConstLiteral(smali, name, lineNo, line);
                trackConst(types, line);
                prevInvokeRet = null;
            } else if (line.startsWith("move-result")) {
                if (prevInvokeRet == null) {
                    fail(name + ":" + lineNo + " move-result without invoke: " + line);
                }
                if (prevInvokeRet.equals("V")) {
                    fail(name + ":" + lineNo + " move-result after void invoke: " + line);
                }
                boolean obj = prevInvokeRet.startsWith("L") || prevInvokeRet.startsWith("[");
                if (line.startsWith("move-result-object") != obj) {
                    fail(name + ":" + lineNo + " wrong move-result kind for "
                            + prevInvokeRet + ": " + line);
                }
                trackMoveResult(types, line, prevInvokeRet);
                prevInvokeRet = null;
            } else if (line.startsWith("invoke-")) {
                // NOTE: deliberately not failing when a previous non-void result was
                // ignored (no move-result): dropping a return value is legal Dalvik.
                Matcher m = INVOKE.matcher(raw);
                if (!m.matches()) fail(name + ":" + lineNo + " unparsable invoke: " + line);
                String kind = m.group(1);
                String regs = m.group(2).trim();
                String sig = m.group(5);
                String ret = m.group(6);
                int expected = countSlots(sig) + (kind.equals("static") ? 0 : 1);
                List<String> regList = expandRegs(regs, name, lineNo, line);
                int actual = regList.size();
                if (expected != actual) {
                    fail(name + ":" + lineNo + " invoke-" + kind + " has " + actual
                            + " regs but signature needs " + expected + ": " + line);
                }
                for (String r : regList) {
                    r = r.trim();
                    if (r.startsWith("v")) {
                        int idx = Integer.parseInt(r.substring(1));
                        if (locals >= 0 && idx >= locals) {
                            fail(name + ":" + lineNo + " register " + r
                                    + " out of range (.locals " + locals + "): " + line);
                        }
                    } else if (r.startsWith("p")) {
                        int idx = Integer.parseInt(r.substring(1));
                        if (idx >= params) {
                            fail(name + ":" + lineNo + " param " + r
                                    + " out of range (" + params + " params): " + line);
                        }
                    }
                }
                checkInvokeTypes(name, lineNo, line, kind, sig, regList, types);
                prevInvokeRet = ret;
            } else if (!line.isEmpty() && !line.startsWith(".") && !line.startsWith("#")
                    && !line.startsWith(":") && !line.startsWith("0x")) {
                trackOther(types, name, lineNo, line);
                // Ignoring a return value is legal; just stop tracking it.
                prevInvokeRet = null;
            }
        }
        for (Map.Entry<String, Integer> e : defCount.entrySet()) {
            if (e.getValue() > 1) fail(name + ": duplicate label :" + e.getKey());
        }
        for (String ref : referenced) {
            String label = ref.substring(0, ref.indexOf('@'));
            if (!defined.contains(label)) {
                fail(name + ": undefined label :" + label + " (referenced at line " + ref + ")");
            }
        }
    }

    private static void checkConstLiteral(String smali, String name, int lineNo, String line) {
        try {
            if (line.startsWith("const/4 ")) {
                long v = parseSmaliInt(line.substring(line.lastIndexOf(',') + 1).trim());
                if (v < -8 || v > 7) fail(name + ":" + lineNo + " const/4 out of range: " + line);
            } else if (line.startsWith("const/16 ")) {
                long v = parseSmaliInt(line.substring(line.lastIndexOf(',') + 1).trim());
                if (v < -32768 || v > 32767) fail(name + ":" + lineNo + " const/16 out of range: " + line);
            } else if (line.startsWith("const/high16 ")) {
                long v = parseSmaliInt(line.substring(line.lastIndexOf(',') + 1).trim());
                if ((v & 0xFFFFL) != 0) fail(name + ":" + lineNo + " const/high16 low bits set: " + line);
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            fail(name + ":" + lineNo + " unparsable const literal: " + line);
        }
    }

    private static long parseSmaliInt(String lit) {
        lit = lit.trim();
        boolean neg = lit.startsWith("-");
        if (neg) lit = lit.substring(1);
        long v;
        if (lit.startsWith("0x") || lit.startsWith("0X")) v = Long.parseLong(lit.substring(2), 16);
        else v = Long.parseLong(lit);
        return neg ? -v : v;
    }

    private static int regType(Map<String, Integer> types, String reg) {
        Integer t = types.get(reg.trim());
        return t == null ? T_UNDEF : t;
    }

    private static String destReg(String line) {
        String[] parts = line.split("[,\\s]+");
        for (String p : parts) {
            if (p.matches("[vp]\\d+")) return p;
        }
        return null;
    }

    private static void trackConst(Map<String, Integer> types, String line) {
        String d = destReg(line);
        if (d == null) return;
        if (line.startsWith("const-string")) {
            types.put(d, T_REF);
            return;
        }
        if (line.startsWith("const-wide")) {
            types.put(d, T_LONG);
            int n = Integer.parseInt(d.substring(1));
            types.put("v" + (n + 1), T_LONG2);
            return;
        }
        long v = 0;
        try {
            v = parseSmaliInt(line.substring(line.lastIndexOf(',') + 1).trim());
        } catch (Exception ignored) {
        }
        if (v == 0) {
            types.put(d, T_ZERO);
            return;
        }
        if (line.startsWith("const/4 ") || line.startsWith("const/16 ")) {
            types.put(d, T_INT);
        } else if (line.startsWith("const/high16 ")) {
            types.put(d, T_FLOAT);
        } else {
            types.put(d, T_NUM);
        }
    }

    private static void trackMoveResult(Map<String, Integer> types, String line, String ret) {
        String d = destReg(line);
        if (d == null) return;
        if (line.startsWith("move-result-object")) {
            types.put(d, T_REF);
            return;
        }
        char c = ret.charAt(0);
        if (c == 'J' || c == 'D') {
            types.put(d, T_LONG);
            int n = Integer.parseInt(d.substring(1));
            types.put("v" + (n + 1), T_LONG2);
        } else if (c == 'F') {
            types.put(d, T_FLOAT);
        } else {
            types.put(d, T_INT);
        }
    }

    private static void checkInvokeTypes(String name, int lineNo, String line, String kind,
                                         String sig, List<String> regs, Map<String, Integer> types) {
        List<Integer> cats = paramCats(sig);
        int ri = kind.equals("static") ? 0 : 1;
        if (!kind.equals("static")) {
            checkNeed(name, lineNo, line, regs.get(0), regType(types, regs.get(0)), T_REF);
        }
        int ci = 0;
        while (ci < cats.size() && ri < regs.size()) {
            int cat = cats.get(ci);
            String reg = regs.get(ri);
            int t = regType(types, reg);
            if (cat == T_LONG) {
                if (t != T_LONG) {
                    fail(name + ":" + lineNo + " register " + reg + " is not a wide pair for " + line.trim());
                }
                if (ri + 1 >= regs.size()) {
                    fail(name + ":" + lineNo + " wide arg missing second register for " + line.trim());
                }
                int n = Integer.parseInt(reg.substring(1));
                String cont = regs.get(ri + 1).trim();
                if (!cont.equals("v" + (n + 1)) || regType(types, cont) != T_LONG2) {
                    fail(name + ":" + lineNo + " broken wide pair " + reg + "," + cont + " for " + line.trim());
                }
                ri += 2;
            } else if (cat == T_REF) {
                checkNeed(name, lineNo, line, reg, t, T_REF);
                ri++;
            } else if (cat == T_FLOAT) {
                checkNeed(name, lineNo, line, reg, t, T_FLOAT);
                ri++;
            } else {
                checkNeed(name, lineNo, line, reg, t, T_INT);
                ri++;
            }
            ci++;
        }
    }

    private static void trackOther(Map<String, Integer> types, String name, int lineNo, String line) {
        if (line.startsWith("move-exception") || line.startsWith("move ")
                || line.startsWith("move-object") || line.startsWith("move-wide")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 2) types.put(regs.get(0), regType(types, regs.get(1)));
            return;
        }
        if (line.startsWith("new-instance ")) {
            String d = destReg(line);
            if (d != null) types.put(d, T_REF);
            return;
        }
        if (line.startsWith("new-array ")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 2) {
                checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_INT);
                types.put(regs.get(0), T_REF);
            }
            return;
        }
        if (line.startsWith("check-cast ")) {
            String d = destReg(line);
            if (d != null) {
                checkNeed(name, lineNo, line, d, regType(types, d), T_REF);
                types.put(d, T_REF);
            }
            return;
        }
        if (line.startsWith("instance-of ")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 2) {
                checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_REF);
                types.put(regs.get(0), T_INT);
            }
            return;
        }
        if (line.startsWith("array-length ")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 2) {
                checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_REF);
                types.put(regs.get(0), T_INT);
            }
            return;
        }
        if (line.startsWith("aget-object ")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 3) {
                checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_REF);
                checkNeed(name, lineNo, line, regs.get(2), regType(types, regs.get(2)), T_INT);
                types.put(regs.get(0), T_REF);
            }
            return;
        }
        if (line.startsWith("aget ")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 3) {
                checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_REF);
                checkNeed(name, lineNo, line, regs.get(2), regType(types, regs.get(2)), T_INT);
                types.put(regs.get(0), T_NUM);
            }
            return;
        }
        if (line.startsWith("aput-object ")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 3) {
                checkNeed(name, lineNo, line, regs.get(0), regType(types, regs.get(0)), T_REF);
                checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_REF);
                checkNeed(name, lineNo, line, regs.get(2), regType(types, regs.get(2)), T_INT);
            }
            return;
        }
        if (line.startsWith("aput ")) {
            String[] parts = line.split("[,\\s]+");
            List<String> regs = new ArrayList<>();
            for (String p : parts) {
                if (p.matches("[vp]\\d+")) regs.add(p);
            }
            if (regs.size() >= 3) {
                checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_REF);
                checkNeed(name, lineNo, line, regs.get(2), regType(types, regs.get(2)), T_INT);
            }
            return;
        }
        if (line.startsWith("iget-object ") || line.startsWith("sget-object ")) {
            String d = destReg(line);
            if (d != null) types.put(d, T_REF);
            return;
        }
        if (line.startsWith("iget-wide ") || line.startsWith("sget-wide ")) {
            String d = destReg(line);
            if (d != null) {
                types.put(d, T_LONG);
                types.put("v" + (Integer.parseInt(d.substring(1)) + 1), T_LONG2);
            }
            return;
        }
        if (line.startsWith("iget ") || line.startsWith("sget ")) {
            String d = destReg(line);
            if (d != null) types.put(d, fieldNeed(line));
            return;
        }
        if (line.startsWith("iget-") || line.startsWith("sget-")) {
            String d = destReg(line);
            if (d != null) types.put(d, T_INT);
            return;
        }
        if (line.startsWith("iput-object ") || line.startsWith("sput-object ")) {
            String src = firstReg(line);
            if (src != null) checkNeed(name, lineNo, line, src, regType(types, src), T_REF);
            return;
        }
        if (line.startsWith("iput-wide ") || line.startsWith("sput-wide ")) {
            String src = firstReg(line);
            if (src != null && regType(types, src) != T_LONG) {
                fail(name + ":" + lineNo + " register " + src + " is not wide for " + line.trim());
            }
            return;
        }
        if (line.startsWith("iput-boolean ") || line.startsWith("sput-boolean ")) {
            String src = firstReg(line);
            if (src != null) checkNeed(name, lineNo, line, src, regType(types, src), T_INT);
            return;
        }
        if (line.startsWith("iput ") || line.startsWith("sput ")) {
            String src = firstReg(line);
            if (src != null) {
                int need = fieldNeed(line);
                if (need == T_REF) checkNeed(name, lineNo, line, src, regType(types, src), T_REF);
                else if (need == T_FLOAT) checkNeed(name, lineNo, line, src, regType(types, src), T_FLOAT);
                else checkNeed(name, lineNo, line, src, regType(types, src), T_INT);
            }
            return;
        }
        if (line.startsWith("return-object ")) {
            String src = firstReg(line);
            if (src != null) checkNeed(name, lineNo, line, src, regType(types, src), T_REF);
            return;
        }
        String op = line.split("\\s+")[0];
        if (op.equals("int-to-float")) {
            arithOp(types, name, lineNo, line, T_FLOAT, T_INT);
        } else if (op.equals("float-to-int")) {
            arithOp(types, name, lineNo, line, T_INT, T_FLOAT);
        } else if (op.equals("int-to-long")) {
            arithWide(types, name, lineNo, line);
        } else if (op.endsWith("-float")) {
            arithOp(types, name, lineNo, line, T_FLOAT, T_FLOAT);
        } else if (op.contains("-int")) {
            arithOp(types, name, lineNo, line, T_INT, T_INT);
        }
    }

    private static String firstReg(String line) {
        String[] parts = line.split("[,\\s]+");
        for (String p : parts) {
            if (p.matches("[vp]\\d+")) return p;
        }
        return null;
    }

    private static String secondReg(String line) {
        String[] parts = line.split("[,\\s]+");
        List<String> regs = new ArrayList<>();
        for (String p : parts) {
            if (p.matches("[vp]\\d+")) regs.add(p);
        }
        return regs.size() >= 2 ? regs.get(1) : null;
    }

    private static void arithOp(Map<String, Integer> types, String name, int lineNo, String line,
                                int destType, int srcNeed) {
        String[] parts = line.split("[,\\s]+");
        List<String> regs = new ArrayList<>();
        for (String p : parts) {
            if (p.matches("[vp]\\d+")) regs.add(p);
        }
        if (regs.isEmpty()) return;
        for (int i = 1; i < regs.size(); i++) {
            if (srcNeed == T_FLOAT) checkNeed(name, lineNo, line, regs.get(i), regType(types, regs.get(i)), T_FLOAT);
            else checkNeed(name, lineNo, line, regs.get(i), regType(types, regs.get(i)), T_INT);
        }
        types.put(regs.get(0), destType);
    }

    private static void arithWide(Map<String, Integer> types, String name, int lineNo, String line) {
        String[] parts = line.split("[,\\s]+");
        List<String> regs = new ArrayList<>();
        for (String p : parts) {
            if (p.matches("[vp]\\d+")) regs.add(p);
        }
        if (regs.isEmpty()) return;
        if (regs.size() >= 2) {
            checkNeed(name, lineNo, line, regs.get(1), regType(types, regs.get(1)), T_INT);
        }
        types.put(regs.get(0), T_LONG);
        int n = Integer.parseInt(regs.get(0).substring(1));
        types.put("v" + (n + 1), T_LONG2);
    }

    private static List<String> expandRegs(String regs, String name, int lineNo, String line) {
        List<String> out = new ArrayList<>();
        if (regs.isEmpty()) return out;
        Matcher range = Pattern.compile("v(\\d+) \\.\\. v(\\d+)").matcher(regs);
        if (range.find()) {
            int from = Integer.parseInt(range.group(1));
            int to = Integer.parseInt(range.group(2));
            if (to < from) fail(name + ":" + lineNo + " reversed range: " + line);
            for (int i = from; i <= to; i++) out.add("v" + i);
            return out;
        }
        for (String r : regs.split(",")) out.add(r.trim());
        return out;
    }

    private static int countSlots(String params) {
        int slots = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == 'J' || c == 'D') {
                slots += 2;
            } else if (c == 'L') {
                slots += 1;
                while (i < params.length() && params.charAt(i) != ';') i++;
            } else if (c == '[') {
                slots += 1;
                while (i + 1 < params.length() && params.charAt(i + 1) == '[') i++;
                if (i + 1 < params.length() && params.charAt(i + 1) == 'L') {
                    i++;
                    while (i < params.length() && params.charAt(i) != ';') i++;
                } else {
                    i++;
                }
            } else {
                slots += 1;
            }
        }
        return slots;
    }
}

