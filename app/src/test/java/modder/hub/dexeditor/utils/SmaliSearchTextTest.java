package modder.hub.dexeditor.utils;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SmaliSearchTextTest {
    @Test public void strategyEscapesDuplicatesFieldsAndAnnotationsHaveSourceLocations() {
        String source = "# classes.dex\n\n.class public LTest;\n.super Ljava/lang/Object;\n"
                + ".source \"Strategy=\"\n"
                + ".field public static Strategy:Ljava/lang/String; = \"Strategy=\"\n"
                + ".annotation runtime LAnnotation;\n value = \"\\nStrategy=\\\"quoted\\\"\\\\end\"\n.end annotation\n"
                + ".method public static z()V\n .registers 1\n const-string v0, \"Strategy=\"\n"
                + " const-string/jumbo v0, \"\\nStrategy=\" # \"not a literal\"\n return-void\n.end method\n"
                + ".method public static a()V\n .registers 1\n const-string v0, \"Strategy=\"\n return-void\n.end method";
        List<SmaliSearchText.Literal> hits = SmaliSearchText.literals(source);
        assertEquals(5, hits.size());
        assertEquals(List.of(5, 7, 11, 12, 17), hits.stream().map(SmaliSearchText.Literal::line).toList());
        assertEquals("\nStrategy=\"quoted\"\\end", hits.get(1).value());
        assertEquals("\nStrategy=", hits.get(3).value());
        assertEquals("Strategy=", hits.get(4).value());
        assertNull(hits.get(0).enclosingMethod());
        assertEquals("z()V", hits.get(2).enclosingMethod());
        assertEquals(9, hits.get(2).methodStartLine());
        assertEquals("a()V", hits.get(4).enclosingMethod());
        assertEquals(15, hits.get(4).methodStartLine());
    }

    @Test public void declarationLookupDoesNotConfuseOverloadsOrUsages() {
        String source = "# .method public a()V\n.field public aField:I\n.field public a:I\n"
                + ".method public a(I)V\n.end method\n.method public a()V\n.end method";
        assertEquals(5, SmaliSearchText.declarationLine(source, ".method", "a()V"));
        assertEquals(2, SmaliSearchText.declarationLine(source, ".field", "a:I"));
        assertEquals(-1, SmaliSearchText.declarationLine(source, ".method", "absent()V"));
        assertEquals("a\n\t\"\\\u1234", SmaliSearchText.unescape("a\\n\\t\\\"\\\\\\u1234"));
    }

    @Test public void nestedAnnotationArraysRetainAllStringLocations() {
        String source = ".annotation runtime LOuter;\n values = {\n \"one\",\n .subannotation LInner;\n"
                + " value = \"two\"\n .end subannotation\n }\n.end annotation\n"
                + ".annotation runtime LOuter;\n child = .subannotation LInner;\n value = \"three\"\n"
                + ".end subannotation\n.end annotation\n.source \"ignored\"\n";
        var literals = SmaliSearchText.literals(source);
        assertEquals(List.of("one", "two", "three"), literals.stream().map(SmaliSearchText.Literal::value).toList());
        assertEquals(List.of(2, 4, 10), literals.stream().map(SmaliSearchText.Literal::line).toList());
    }

    @Test public void whitespaceSeparatedLiteralsKeepTheirOriginalLinesColumnsAndValues() {
        for (String whitespace : List.of("\t", " \t  \t", "\f", " ")) {
            String source = "# classes.dex\n\n.class" + whitespace + "public LTest;\n.super Ljava/lang/Object;\n"
                    + ".source" + whitespace + "\"ignored\"\n"
                    + ".field" + whitespace + "public static text:Ljava/lang/String; = \"Strategy=\"\n"
                    + ".annotation" + whitespace + "runtime LOuter;\n"
                    + " child\t=" + whitespace + ".subannotation" + whitespace + "LInner;\n"
                    + " value = \"\\nStrategy=\"\n.end" + whitespace + "subannotation # comment\n"
                    + " value = \"Strategy=\"\n.end" + whitespace + "annotation # comment\n"
                    + ".source" + whitespace + "\"ignored after annotation\"\n"
                    + ".method" + whitespace + "public static a()V\n .registers 1\n"
                    + " const-string" + whitespace + "v0, \"Strategy=\"\n"
                    + " const-string/jumbo" + whitespace + "v0, \"\\nStrategy=\"\n"
                    + " return-void\n.end" + whitespace + "method\n";
            var literals = SmaliSearchText.literals(source);
            assertEquals("All literals found with separator " + whitespace, 5, literals.size());
            assertEquals(List.of(5, 8, 10, 15, 16), literals.stream().map(SmaliSearchText.Literal::line).toList());
            assertEquals(List.of("Strategy=", "\nStrategy=", "Strategy=", "Strategy=", "\nStrategy="),
                    literals.stream().map(SmaliSearchText.Literal::value).toList());
            String[] lines = source.split("\n", -1);
            for (var literal : literals) {
                assertEquals(lines[literal.line()].indexOf('"') + 1, literal.column());
                assertEquals(lines[literal.line()].trim(), literal.source());
            }
        }
    }

    @Test public void tabSeparatedDeclarationsMatchExactSignaturesInsteadOfLineZero() {
        for (String whitespace : List.of("\t", "\t \t", "\f", " ")) {
            String source = "# classes.dex\n\n.fieldish text:Ljava/lang/String;\n"
                    + ".field" + whitespace + "public static textSuffix:Ljava/lang/String;\n"
                    + ".field" + whitespace + "public static text:Ljava/lang/String; = \"Strategy=\"\n"
                    + ".methodical a()V\n.method" + whitespace + "public a(I)V\n.end" + whitespace + "method\n"
                    + ".method" + whitespace + "public a()V\n.end" + whitespace + "method";
            assertEquals(4, SmaliSearchText.declarationLine(source, ".field", "text:Ljava/lang/String;"));
            assertEquals(8, SmaliSearchText.declarationLine(source, ".method", "a()V"));
            assertEquals(6, SmaliSearchText.declarationLine(source, ".method", "a(I)V"));
            assertEquals(-1, SmaliSearchText.declarationLine(source, ".method", "absent()V"));
        }
    }

    @Test public void directiveAndOpcodePrefixesOrQuotedTextDoNotChangeAnnotationDepth() {
        String source = "const-string-extra\tv0, \"ignored opcode\"\n"
                + ".fieldish\ttext:Ljava/lang/String; = \"ignored field\"\n"
                + ".annotation-extra\truntime LWrong;\nvalue = \"ignored annotation\"\n"
                + "# child =\t.subannotation\tLWrong;\n.source \"ignored comment\"\n"
                + ".field\tpublic text:Ljava/lang/String; = \"literal = .subannotation LWrong;\"\n"
                + ".source \"ignored after quoted directive\"\n"
                + "const-string/jumbo\tv0, \"Strategy= \\tvalue\"";
        var literals = SmaliSearchText.literals(source);
        assertEquals(List.of(6, 8), literals.stream().map(SmaliSearchText.Literal::line).toList());
        assertEquals(List.of("literal = .subannotation LWrong;", "Strategy= \tvalue"),
                literals.stream().map(SmaliSearchText.Literal::value).toList());
    }

    @Test public void fieldDeclarationIgnoresSignaturesInCommentsAndQuotedInitializers() {
        String decoys = "# classes.dex\n\n"
                + ".field\tpublic static commentDecoy:I # text:Ljava/lang/String; ignored\n"
                + ".field public static quotedDecoy:Ljava/lang/String; = \"prefix text:Ljava/lang/String; suffix\"\n"
                + ".field public static escapedDecoy:Ljava/lang/String; = \"prefix \\\" text:Ljava/lang/String; \\\" suffix\"\n"
                + ".field public static textSuffix:Ljava/lang/String; = \"not the target\"\n";
        assertEquals(-1, SmaliSearchText.declarationLine(decoys, ".field", "text:Ljava/lang/String;"));
        String source = decoys + ".field\tpublic\tstatic\ttext:Ljava/lang/String;=\"Strategy=\" # actual declaration\n";
        assertEquals(6, SmaliSearchText.declarationLine(source, ".field", "text:Ljava/lang/String;"));
        assertEquals(3, SmaliSearchText.declarationLine(source, ".field", "quotedDecoy:Ljava/lang/String;"));
    }

    @Test public void methodDeclarationIgnoresCommentSignaturesAndOtherOverloads() {
        String decoys = "# classes.dex\n\n"
                + ".method\tpublic static unrelated()V # a()V z()V ignored\n"
                + ".method public static a(I)V # a()V ignored overload\n"
                + ".method public static other()V # \" a()V \" quoted comment\n";
        assertEquals(-1, SmaliSearchText.declarationLine(decoys, ".method", "a()V"));
        assertEquals(-1, SmaliSearchText.declarationLine(decoys, ".method", "z()V"));
        String source = decoys + ".method\tpublic\tstatic\ta()V # actual method\n";
        assertEquals(5, SmaliSearchText.declarationLine(source, ".method", "a()V"));
        assertEquals(3, SmaliSearchText.declarationLine(source, ".method", "a(I)V"));
    }

    @Test public void declarationLookupReadsNamesModifiersAndTypesBeforeTrailingValues() {
        String source = ".field public static greylist-max-o text : [Ljava/lang/String; = null # text:I decoy\n"
                + ".method private constructor <init> ( [I ) V # a()V decoy\n"
                + ".method public bridge synthetic a ( I [Ljava/lang/String; ) Ljava/lang/String; # a()V decoy\n"
                + ".field `two words`:Lpkg/`Class Name`; = null\n"
                + ".method public `two words` ( ) V\n"
                + ".field public:I\n";
        assertEquals(0, SmaliSearchText.declarationLine(source, ".field", "text:[Ljava/lang/String;"));
        assertEquals(1, SmaliSearchText.declarationLine(source, ".method", "<init>([I)V"));
        assertEquals(2, SmaliSearchText.declarationLine(source, ".method", "a(I[Ljava/lang/String;)Ljava/lang/String;"));
        assertEquals(3, SmaliSearchText.declarationLine(source, ".field", "two words:Lpkg/Class Name;"));
        assertEquals(4, SmaliSearchText.declarationLine(source, ".method", "two words()V"));
        assertEquals(5, SmaliSearchText.declarationLine(source, ".field", "public:I"));
        assertEquals(-1, SmaliSearchText.declarationLine(source, ".method", "a()V"));
        assertEquals(-1, SmaliSearchText.declarationLine(source, ".field", "text:I"));
    }

    @Test public void malformedHeadersCannotMatchSignaturesFromTheirRemainingText() {
        String source = ".field public # text:I decoy\n"
                + ".field public holder:I = text:I # encoded initializer decoy\n"
                + ".method private # a()V decoy\n"
                + ".method public malformed()Vextra # a()V decoy\n";
        assertEquals(-1, SmaliSearchText.declarationLine(source, ".field", "text:I"));
        assertEquals(-1, SmaliSearchText.declarationLine(source, ".method", "a()V"));
        assertEquals(-1, SmaliSearchText.declarationLine(source, ".method", "malformed()V"));
    }
}
