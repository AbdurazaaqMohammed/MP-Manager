package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertNotNull;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali2.Smali;

import org.junit.Test;

public class SmaliLexerRegressionTest {

    private static final String SMALI =
            ".class public Ltest/B;\n" +
            ".super Ljava/lang/Object;\n" +
            ".source \"B.java\"\n" +
            ".method public constructor <init>()V\n" +
            "    .registers 1\n" +
            "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
            "    return-void\n" +
            ".end method\n" +
            ".method public mixed(Ljava/lang/String;I)Ljava/lang/Object;\n" +
            "    .registers 4\n" +
            "    const/4 v0, 0x0\n" +
            "    return v0\n" +
            ".end method\n" +
            ".method public cleared()V\n" +
            "    .registers 1\n" +
            "    return-void\n" +
            ".end method\n" +
            ".method public wide(J)D\n" +
            "    .registers 4\n" +
            "    const-wide v0, 0x0\n" +
            "    return-wide v0\n" +
            ".end method\n" +
            ".method public obj()Ljava/lang/String;\n" +
            "    .registers 2\n" +
            "    const v0, 0x0\n" +
            "    return-object v0\n" +
            ".end method\n" +
            ".method public str()V\n" +
            "    .registers 1\n" +
            "    const-string v0, \"hi (there)\"\n" +
            "    return-void\n" +
            ".end method\n";

    @Test
    public void assembleMethodWithParamList() throws Exception {
        ClassDef def = Smali.assemble(SMALI, new SmaliOptions(), 35);
        assertNotNull(def);
    }
}
