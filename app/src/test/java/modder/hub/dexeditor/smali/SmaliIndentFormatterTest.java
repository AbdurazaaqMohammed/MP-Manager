package modder.hub.dexeditor.smali;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SmaliIndentFormatterTest {

    @Test
    public void formatsMethodHeadersBodyAndNestedTryCatch() {
        String raw = ""
                + ".class public final Ls/h/e/l/VN;\n"
                + ".super Ljava/lang/Object;\n"
                + "\n"
                + "# static fields\n"
                + ".field static la Z\n"
                + "\n"
                + "# direct methods\n"
                + ".method static constructor <clinit>()V\n"
                + "    .registers 5\n"
                + "\n"
                + "    const/4 v1, 0x0\n"
                + "    :try_start_7\n"
                + "    new-instance v0, Ljava/lang/Object;\n"
                + "    invoke-direct {v0}, Ljava/lang/Object;-><init>()V\n"
                + "    :try_end_c\n"
                + "    .catch Ljava/lang/Exception; {:try_start_7 .. :try_end_c} :catch_54\n"
                + "    .catchall {:try_start_7 .. :try_end_c} :catchall_63\n"
                + "\n"
                + "    if-eqz v0, :cond_16\n"
                + "    :cond_16\n"
                + "    return-void\n"
                + ".end method\n";

        String expected = ""
                + ".class public final Ls/h/e/l/VN;\n"
                + ".super Ljava/lang/Object;\n"
                + "\n"
                + "# static fields\n"
                + ".field static la Z\n"
                + "\n"
                + "# direct methods\n"
                + ".method static constructor <clinit>()V\n"
                + "    .registers 5\n"
                + "\n"
                + "        const/4 v1, 0x0\n"
                + "        :try_start_7\n"
                + "            new-instance v0, Ljava/lang/Object;\n"
                + "            invoke-direct {v0}, Ljava/lang/Object;-><init>()V\n"
                + "        :try_end_c\n"
                + "        .catch Ljava/lang/Exception; {:try_start_7 .. :try_end_c} :catch_54\n"
                + "        .catchall {:try_start_7 .. :try_end_c} :catchall_63\n"
                + "\n"
                + "        if-eqz v0, :cond_16\n"
                + "        :cond_16\n"
                + "        return-void\n"
                + ".end method\n";

        String formatted = SmaliIndentFormatter.format(raw);
        assertEquals(expected, formatted);
        assertEquals("Formatting must be idempotent", expected, SmaliIndentFormatter.format(formatted));
    }

    @Test
    public void formatsNestedTryBlocksAndSharedTryEndWithoutDrift() {
        String raw = ""
                + ".method public nested(I)V\n"
                + ".locals 2\n"
                + ":try_start_outer\n"
                + "const/4 v0, 0x1\n"
                + ":try_start_inner\n"
                + "invoke-static {}, LFoo;->bar()V\n"
                + ":try_end_shared\n"
                + ".catch Ljava/io/IOException; {:try_start_inner .. :try_end_shared} :h1\n"
                + ".catchall {:try_start_outer .. :try_end_shared} :h2\n"
                + "return-void\n"
                + ".end method";

        String expected = ""
                + ".method public nested(I)V\n"
                + "    .locals 2\n"
                + "        :try_start_outer\n"
                + "            const/4 v0, 0x1\n"
                + "            :try_start_inner\n"
                + "                invoke-static {}, LFoo;->bar()V\n"
                + "        :try_end_shared\n"
                + "        .catch Ljava/io/IOException; {:try_start_inner .. :try_end_shared} :h1\n"
                + "        .catchall {:try_start_outer .. :try_end_shared} :h2\n"
                + "        return-void\n"
                + ".end method";

        assertEquals(expected, SmaliIndentFormatter.format(raw));
    }

    @Test
    public void formatsSwitchesArraysParamsFieldsAndNestedAnnotations() {
        String raw = ""
                + ".class public LTest;\n"
                + ".super Ljava/lang/Object;\n"
                + "\n"
                + ".field private s Ljava/lang/String;\n"
                + ".annotation runtime LAnno;\n"
                + "values = {\n"
                + "\"a\",\n"
                + ".subannotation LSub;\n"
                + "k = \"}\"\n"
                + ".end subannotation\n"
                + "}\n"
                + ".end annotation\n"
                + ".end field\n"
                + "\n"
                + ".field public single I = 0x1\n"
                + "\n"
                + ".method public run(I)V\n"
                + ".registers 3\n"
                + ".param p1, \"arg\"\n"
                + ".annotation build LNonNull;\n"
                + ".end annotation\n"
                + ".end param\n"
                + ".annotation runtime LMeth;\n"
                + "x = 0x2\n"
                + ".end annotation\n"
                + ".prologue\n"
                + "packed-switch p1, :pswitch_data_0\n"
                + ":pswitch_data_0\n"
                + ".packed-switch 0x0\n"
                + ":pswitch_0\n"
                + ".end packed-switch\n"
                + ":array_0\n"
                + ".array-data 4\n"
                + "0x1\n"
                + "0x2\n"
                + ".end array-data\n"
                + ".end method\n";

        String expected = ""
                + ".class public LTest;\n"
                + ".super Ljava/lang/Object;\n"
                + "\n"
                + ".field private s Ljava/lang/String;\n"
                + "    .annotation runtime LAnno;\n"
                + "        values = {\n"
                + "            \"a\",\n"
                + "            .subannotation LSub;\n"
                + "                k = \"}\"\n"
                + "            .end subannotation\n"
                + "        }\n"
                + "    .end annotation\n"
                + ".end field\n"
                + "\n"
                + ".field public single I = 0x1\n"
                + "\n"
                + ".method public run(I)V\n"
                + "    .registers 3\n"
                + "    .param p1, \"arg\"\n"
                + "        .annotation build LNonNull;\n"
                + "        .end annotation\n"
                + "    .end param\n"
                + "    .annotation runtime LMeth;\n"
                + "        x = 0x2\n"
                + "    .end annotation\n"
                + "    .prologue\n"
                + "        packed-switch p1, :pswitch_data_0\n"
                + "        :pswitch_data_0\n"
                + "        .packed-switch 0x0\n"
                + "            :pswitch_0\n"
                + "        .end packed-switch\n"
                + "        :array_0\n"
                + "        .array-data 4\n"
                + "            0x1\n"
                + "            0x2\n"
                + "        .end array-data\n"
                + ".end method\n";

        String formatted = SmaliIndentFormatter.format(raw);
        assertEquals(expected, formatted);
        assertEquals(expected, SmaliIndentFormatter.format(formatted));
        assertNull(SmaliIndentFormatter.format(null));
    }

    @Test
    public void orphanTryStartAndStringsWithDirectivesNeverCorruptIndentation() {
        String raw = ""
                + ".method public weird()V\n"
                + ".registers 1\n"
                + ":try_start_unmatched\n"
                + "const-string v0, \".end method .subannotation {\"\n"
                + "return-void\n"
                + ".end method\n";

        String expected = ""
                + ".method public weird()V\n"
                + "    .registers 1\n"
                + "        :try_start_unmatched\n"
                + "        const-string v0, \".end method .subannotation {\"\n"
                + "        return-void\n"
                + ".end method\n";

        assertEquals(expected, SmaliIndentFormatter.format(raw));
    }
}
