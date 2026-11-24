package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali2.Smali;

import org.junit.Test;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Reproduction for the reported bug: stripping .param debug info must not drop the
 * annotations nested inside the .param block (e.g. Retrofit @Query runtime annotation).
 */
public class DexParamAnnotationRegressionTest {

    private static final String SMALI =
            ".class public interface abstract Lcom/test/Test;\n" +
            ".super Ljava/lang/Object;\n" +
            "\n" +
            ".method public abstract testAnnotation(Ljava/lang/String;)V\n" +
            "    .param p1    # Ljava/lang/String;\n" +
            "        .annotation build Landroidx/annotation/RecentlyNonNull;\n" +
            "        .end annotation\n" +
            "        .annotation runtime Lretrofit2/http/Query;\n" +
            "            value = \"test_param\"\n" +
            "        .end annotation\n" +
            "    .end param\n" +
            ".end method\n";

    @Test
    public void strippingParamDebugInfoKeepsNestedAnnotations() throws Exception {
        ClassDef assembled = Smali.assemble(SMALI, new com.android.tools.smali.smali.SmaliOptions(), 35);
        assertNotNull("smali must assemble", assembled);

        Method original = assembled.getVirtualMethods().iterator().next();
        assertEquals(2, original.getParameters().get(0).getAnnotations().size());

        ClassDef stripped = DexDebugStripper.strip(assembled);
        Method m = stripped.getVirtualMethods().iterator().next();
        MethodParameter p = m.getParameters().get(0);

        assertNull("param debug name must be stripped", p.getName());
        assertEquals("Ljava/lang/String;", p.getType());

        Set<String> types = new HashSet<>();
        for (Annotation a : p.getAnnotations()) types.add(a.getType());
        assertTrue("build annotation must survive: " + types, types.contains("Landroidx/annotation/RecentlyNonNull;"));
        assertTrue("runtime annotation must survive: " + types, types.contains("Lretrofit2/http/Query;"));

        DexPool pool = new DexPool(Opcodes.forDexVersion(35));
        pool.internClass(stripped);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        DexBackedDexFile dex = new DexBackedDexFile(Opcodes.forDexVersion(35), store.getData());
        Method backed = dex.getClasses().iterator().next().getVirtualMethods().iterator().next();
        Set<String> backedTypes = new HashSet<>();
        for (Annotation a : backed.getParameters().get(0).getAnnotations()) backedTypes.add(a.getType());
        assertTrue("round-trip build annotation: " + backedTypes, backedTypes.contains("Landroidx/annotation/RecentlyNonNull;"));
        assertTrue("round-trip runtime annotation: " + backedTypes, backedTypes.contains("Lretrofit2/http/Query;"));
    }

    private static final String SMALI_NAMED =
            ".class public interface abstract Lcom/test/Test;\n" +
            ".super Ljava/lang/Object;\n" +
            "\n" +
            ".method public abstract testAnnotation(Ljava/lang/String;)V\n" +
            "    .param p1, \"testParam\"    # Ljava/lang/String;\n" +
            "        .annotation build Landroidx/annotation/RecentlyNonNull;\n" +
            "        .end annotation\n" +
            "        .annotation runtime Lretrofit2/http/Query;\n" +
            "            value = \"test_param\"\n" +
            "        .end annotation\n" +
            "    .end param\n" +
            ".end method\n";

    @Test
    public void strippingNamedParamDebugInfoKeepsNestedAnnotations() throws Exception {
        ClassDef assembled = Smali.assemble(SMALI_NAMED, new com.android.tools.smali.smali.SmaliOptions(), 35);
        assertNotNull("smali must assemble", assembled);

        Method original = assembled.getVirtualMethods().iterator().next();
        assertEquals("testParam", original.getParameters().get(0).getName());

        ClassDef stripped = DexDebugStripper.strip(assembled);
        MethodParameter p = stripped.getVirtualMethods().iterator().next().getParameters().get(0);

        assertNull("param debug name must be stripped", p.getName());
        Set<String> types = new HashSet<>();
        for (Annotation a : p.getAnnotations()) types.add(a.getType());
        assertTrue("build annotation must survive: " + types, types.contains("Landroidx/annotation/RecentlyNonNull;"));
        assertTrue("runtime annotation must survive: " + types, types.contains("Lretrofit2/http/Query;"));
    }

    @Test
    public void disassemblingWithoutDebugInfoKeepsNestedAnnotations() throws Exception {
        ClassDef assembled = Smali.assemble(SMALI, new com.android.tools.smali.smali.SmaliOptions(), 35);

        BaksmaliOptions options = new BaksmaliOptions();
        options.debugInfo = false;
        StringWriter out = new StringWriter();
        BaksmaliWriter writer = new BaksmaliWriter(out);
        new ClassDefinition(options, assembled).writeTo(writer);
        writer.close();
        String smali = out.toString();

        assertTrue("disassembly should contain .param block:\n" + smali, smali.contains(".param"));
        assertTrue("build annotation must survive disassembly:\n" + smali,
                smali.contains("Landroidx/annotation/RecentlyNonNull;"));
        assertTrue("runtime annotation must survive disassembly:\n" + smali,
                smali.contains("Lretrofit2/http/Query;"));
    }
}
