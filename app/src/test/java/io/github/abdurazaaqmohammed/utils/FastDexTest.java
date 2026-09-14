package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verifies the DexPatcher-style fast pipeline: assemble a tiny dex, describe
 * one class, text-patch it, assemble only that file, merge back with DexPool.
 * Also covers the binary toast-call prescan and dex version preservation.
 */
public class FastDexTest {

    private static final String A_SMALI =
            ".class public Ltest/A;\n"
                    + ".super Ljava/lang/Object;\n"
                    + ".source \"A.java\"\n"
                    + ".method public constructor <init>()V\n"
                    + "    .locals 1\n"
                    + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                    + "    return-void\n"
                    + ".end method\n"
                    + ".method public greet()Ljava/lang/String;\n"
                    + "    .locals 1\n"
                    + "    const-string v0, \"hi\"\n"
                    + "    return-object v0\n"
                    + ".end method\n";

    private static final String B_SMALI =
            ".class public Ltest/B;\n"
                    + ".super Landroid/app/Activity;\n"
                    + ".source \"B.java\"\n"
                    + ".method protected onCreate(Landroid/os/Bundle;)V\n"
                    + "    .locals 1\n"
                    + "    invoke-direct {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n"
                    + "    return-void\n"
                    + ".end method\n"
                    + ".method public usesToast()V\n"
                    + "    .locals 3\n"
                    + "    const/4 v0, 0x0\n"
                    + "    const-string v1, \"t\"\n"
                    + "    const/4 v2, 0x0\n"
                    + "    invoke-static {v0, v1, v2}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;\n"
                    + "    move-result-object v0\n"
                    + "    invoke-virtual {v0}, Landroid/widget/Toast;->show()V\n"
                    + "    return-void\n"
                    + ".end method\n";

    private File writeSmaliDir(String aSmali, String bSmali) throws Exception {
        File dir = Files.createTempDirectory("fastdex").toFile();
        File pkg = new File(dir, "test");
        assertTrue(pkg.mkdirs());
        try (FileWriter w = new FileWriter(new File(pkg, "A.smali"))) {
            w.write(aSmali);
        }
        if (bSmali != null) {
            try (FileWriter w = new FileWriter(new File(pkg, "B.smali"))) {
                w.write(bSmali);
            }
        }
        return dir;
    }

    private File assembleDir(File smaliDir, int api) throws Exception {
        File outDex = new File(smaliDir.getParentFile(), "out_" + System.nanoTime() + ".dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = api;
        assertTrue("assemble failed for " + smaliDir, Smali.assemble(options, smaliDir.getPath()));
        return outDex;
    }

    private DexBackedDexFile loadDex(File dexFile) throws Exception {
        return new DexBackedDexFile(Opcodes.getDefault(), Files.readAllBytes(dexFile.toPath()));
    }

    private static ClassDef findClass(DexBackedDexFile dex, String type) {
        for (ClassDef c : dex.getClasses()) {
            if (type.equals(c.getType())) return c;
        }
        return null;
    }

    private static boolean hasMethod(ClassDef c, String name) {
        for (Method m : c.getDirectMethods()) {
            if (name.equals(m.getName())) return true;
        }
        for (Method m : c.getVirtualMethods()) {
            if (name.equals(m.getName())) return true;
        }
        return false;
    }

    @Test
    public void singleClassRoundTripAndMerge() throws Exception {
        File smaliDir = writeSmaliDir(A_SMALI, B_SMALI);
        File origDexFile = assembleDir(smaliDir, 28);
        DexBackedDexFile orig = loadDex(origDexFile);
        assertNotNull(findClass(orig, "Ltest/A;"));
        assertNotNull(findClass(orig, "Ltest/B;"));

        File patchDir = Files.createTempDirectory("fastdexpatch").toFile();
        Map<String, File> files = FastDexPatch.disassembleClasses(orig,
                new LinkedHashSet<>(Arrays.asList("Ltest/B;")),
                patchDir, FastDexPatch.defaultBaksmaliOptions(), null);
        assertEquals(1, files.size());
        File bSmali = files.get("Ltest/B;");
        assertNotNull(bSmali);
        String content = new String(Files.readAllBytes(bSmali.toPath()), "UTF-8");
        assertTrue(content.contains("onCreate(Landroid/os/Bundle;)V"));
        assertFalse(content.contains("greet()"));

        String patched = content.replaceFirst(
                "\\.method protected onCreate\\(Landroid/os/Bundle;\\)V",
                java.util.regex.Matcher.quoteReplacement(
                        ".method private onCreate$mpmanager(Landroid/os/Bundle;)V"));
        assertFalse(patched.equals(content));
        try (FileWriter w = new FileWriter(bSmali)) {
            w.write(patched);
        }

        File miniDex = FastDexPatch.assembleMiniDex(patchDir, 28, null);
        byte[] merged = FastDexPatch.mergeDex(orig, miniDex, 28);
        DexBackedDexFile mergedDex = new DexBackedDexFile(Opcodes.getDefault(), merged);

        ClassDef b = findClass(mergedDex, "Ltest/B;");
        assertNotNull(b);
        assertTrue(hasMethod(b, "onCreate$mpmanager"));
        assertFalse(hasMethod(b, "onCreate"));
        assertTrue(hasMethod(b, "usesToast"));
        ClassDef a = findClass(mergedDex, "Ltest/A;");
        assertNotNull(a);
        assertTrue(hasMethod(a, "greet"));

        byte[] origBytes = Files.readAllBytes(origDexFile.toPath());
        assertEquals(new String(origBytes, 0, 8, "US-ASCII").substring(0, 7),
                new String(merged, 0, 8, "US-ASCII").substring(0, 7));
    }

    @Test
    public void toastPrescanFindsOnlyToastUsers() throws Exception {
        File smaliDir = writeSmaliDir(A_SMALI, B_SMALI);
        DexBackedDexFile orig = loadDex(assembleDir(smaliDir, 28));
        Set<String> found = FastDexPatch.findClassesWithMethodCalls(orig,
                "Landroid/widget/Toast;", new HashSet<>(Arrays.asList("makeText", "show")));
        assertEquals(1, found.size());
        assertTrue(found.contains("Ltest/B;"));
    }

    @Test
    public void descriptorToClassNameKeepsInnerClasses() {
        assertEquals("com.a.B",
                FastDexPatch.descriptorToClassName("Lcom/a/B;"));
        assertEquals("com.a.B$Inner",
                FastDexPatch.descriptorToClassName("Lcom/a/B$Inner;"));
    }
}
