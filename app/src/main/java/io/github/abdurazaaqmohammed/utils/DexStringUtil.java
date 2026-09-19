package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.VersionMap;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DexStringUtil {

    private DexStringUtil() {
    }

    public static int replaceStrings(File dexIn, File dexOut, String find, String replace, boolean matchCase) throws IOException {
        if (find == null || find.isEmpty()) {
            return 0;
        }
        if (replace == null) {
            replace = "";
        }
        byte[] dexBytes = Files.readAllBytes(dexIn.toPath());
        int api = VersionMap.mapDexVersionToApi(HeaderItem.getVersion(dexBytes, 0));
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexIn, null);
        Set<String> descriptors = new LinkedHashSet<>();
        for (ClassDef c : dex.getClasses()) {
            descriptors.add(c.getType());
        }
        File tmpRoot = Files.createTempDirectory("dexstr").toFile();
        try {
            File smaliDir = new File(tmpRoot, "smali");
            smaliDir.mkdirs();
            BaksmaliOptions options = FastDexPatch.defaultBaksmaliOptions();
            options.apiLevel = api;
            FastDexPatch.disassembleClasses(dex, descriptors, smaliDir, options, null);
            Pattern quoted = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
            Pattern needle = matchCase ? Pattern.compile(Pattern.quote(find)) : Pattern.compile(Pattern.quote(find), Pattern.CASE_INSENSITIVE);
            int total = 0;
            List<File> smaliFiles = new ArrayList<>();
            collectSmaliFiles(smaliDir, smaliFiles);
            for (File smaliFile : smaliFiles) {
                String text = new String(Files.readAllBytes(smaliFile.toPath()), StandardCharsets.UTF_8);
                Matcher quotedMatcher = quoted.matcher(text);
                StringBuffer rebuilt = new StringBuffer();
                boolean changed = false;
                while (quotedMatcher.find()) {
                    String inner = quotedMatcher.group(1);
                    Matcher needleMatcher = needle.matcher(inner);
                    StringBuffer innerBuf = new StringBuffer();
                    boolean innerChanged = false;
                    while (needleMatcher.find()) {
                        needleMatcher.appendReplacement(innerBuf, Matcher.quoteReplacement(replace));
                        total++;
                        innerChanged = true;
                    }
                    needleMatcher.appendTail(innerBuf);
                    if (innerChanged) {
                        quotedMatcher.appendReplacement(rebuilt, Matcher.quoteReplacement("\"" + innerBuf + "\""));
                        changed = true;
                    } else {
                        quotedMatcher.appendReplacement(rebuilt, Matcher.quoteReplacement(quotedMatcher.group(0)));
                    }
                }
                quotedMatcher.appendTail(rebuilt);
                if (changed) {
                    Files.write(smaliFile.toPath(), rebuilt.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            SmaliOptions smaliOptions = new SmaliOptions();
            smaliOptions.outputDexFile = dexOut.getPath();
            smaliOptions.jobs = 1;
            smaliOptions.apiLevel = api;
            boolean ok = Smali.assemble(smaliOptions, smaliDir.getPath());
            if (!ok) {
                throw new IOException("Failed to assemble patched dex");
            }
            return total;
        } finally {
            deleteRecursive(tmpRoot);
        }
    }

    private static void collectSmaliFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collectSmaliFiles(f, out);
            } else if (f.getName().endsWith(".smali")) {
                out.add(f);
            }
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        f.delete();
    }
}
