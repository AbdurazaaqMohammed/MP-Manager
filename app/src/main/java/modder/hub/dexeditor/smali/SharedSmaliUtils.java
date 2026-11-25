package modder.hub.dexeditor.smali;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.smali.SmaliOptions;

import java.io.StringWriter;

public final class SharedSmaliUtils {

    public static final BaksmaliOptions OPTIONS = new BaksmaliOptions();

    public static final SmaliOptions ASSEMBLE_OPTIONS = new SmaliOptions();

    private SharedSmaliUtils() {
    }

    /**
     * Disassembles a {@link ClassDef} with Baksmali and applies hierarchical Smali
     * indentation so editors, search, and diffs render consistent block structure.
     */
    public static String disassembleClass(ClassDef classDef) throws Exception {
        if (classDef == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter(16 * 1024);
        BaksmaliWriter baksmaliWriter = new BaksmaliWriter(stringWriter);
        new ClassDefinition(OPTIONS, classDef).writeTo(baksmaliWriter);
        baksmaliWriter.close();
        return SmaliIndentFormatter.format(stringWriter.toString());
    }
}
