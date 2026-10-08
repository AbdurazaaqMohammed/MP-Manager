package io.github.abdurazaaqmohammed.features.dex;

import com.reandroid.apk.ApkModule;
import com.reandroid.dex.key.FieldKey;
import com.reandroid.dex.key.KeyPair;
import com.reandroid.dex.key.MethodKey;
import com.reandroid.dex.key.PackageKey;
import com.reandroid.dex.key.TypeKey;
import com.reandroid.dex.model.DexDirectory;
import com.reandroid.dex.refactor.Rename;
import com.reandroid.dex.refactor.RenameBatch;
import com.reandroid.dex.refactor.RenameFields;
import com.reandroid.dex.refactor.RenameMethods;
import com.reandroid.dex.refactor.RenameTypes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MT's "global rename": rewrites every class, field and method name in an APK in one pass.
 *
 * <p>The renaming itself is not implemented here. {@code com.reandroid.dex.refactor} already
 * ships a complete, position-aware implementation (it walks string ids, annotations, signatures
 * and every reference, not just the declarations), but nothing in the app ever called it, so the
 * whole package was dead code. This class is the missing caller: it turns the plain-text rules
 * the dialog collects into a {@link RenameBatch}, applies it to the dex files of the APK and
 * writes the result back.
 *
 * <p>Rules are one per line:
 * <pre>
 *   pkg   com.example        com.acme            rename a package, sub-packages included
 *   pkg   com.example        com.acme  exact     rename only that exact package
 *   type  Lcom/example/A;    Lcom/acme/A;
 *   field Lcom/example/A;-&gt;count:I  total
 *   method Lcom/example/A;-&gt;get(I)I  read
 * </pre>
 * Blank lines and lines starting with {@code #} are ignored. A rule whose key does not parse is
 * reported and skipped rather than aborting the batch, because one typo should not throw away the
 * rules around it.
 *
 * <p>Field and method keys are matched against every equivalent declaration, so renaming
 * {@code Lcom/example/A;->get(I)I} also renames the override in a subclass.
 */
public final class DexRefactor {

    /** One line of the rename script. */
    public static final class Rule {
        public enum Kind { PKG, TYPE, FIELD, METHOD }

        public final Kind kind;
        public final String search;
        public final String replace;
        public final boolean deep;
        /** 1-based line in the script, so a rejection can point at what the user typed. */
        final int line;

        Rule(Kind kind, String search, String replace, boolean deep, int line) {
            this.kind = kind;
            this.search = search;
            this.replace = replace;
            this.deep = deep;
            this.line = line;
        }
    }

    /** A line that could not be turned into a {@link Rule}. */
    public static final class Rejected {
        public final int line;
        public final String text;
        public final String reason;

        Rejected(int line, String text, String reason) {
            this.line = line;
            this.text = text;
            this.reason = reason;
        }
    }

    /** Parsed script: the usable rules plus everything that was dropped, with a reason. */
    public static final class Script {
        final List<Rule> rules = new ArrayList<>();
        final List<Rejected> rejected = new ArrayList<>();
        /** Script-wide: also rename the inner classes of a renamed outer class. */
        boolean renameInnerClasses;
        /** Script-wide: repair member accesses whose visibility the rename would break. */
        boolean fixAccessibility = true;

        public int size() {
            return rules.size();
        }
    }

    /** What actually landed in the dex files. */
    public static final class Result {
        public final int applied;
        public final int locked;
        public final int declared;

        Result(int applied, int locked, int declared) {
            this.applied = applied;
            this.locked = locked;
            this.declared = declared;
        }
    }

    public interface Log {
        void log(String message);
    }

    private DexRefactor() {
    }

    /**
     * Applies {@code script} to every dex file of {@code apk} and writes a new APK to
     * {@code out}. The input is never touched, so the caller decides what to do with the result
     * (sign it, then swap it over the original).
     */
    public static Result apply(File apk, File out, Script script, Log log) throws Exception {
        ApkModule module = ApkModule.loadApkFile(apk);
        DexDirectory directory = DexDirectory.fromZip(module.getZipEntryMap());
        try {
            RenameBatch batch = build(directory, script, log);
            if (batch.isEmpty()) {
                throw new IllegalArgumentException("No usable rename rule");
            }
            int declared = batch.totalSize();
            int locked = batch.totalLockedSize();
            // merge() folds class renames into the field and method keys, so a member rule
            // written against a pre-rename class name still matches after its class moved.
            RenameBatch merged = batch.merge();
            // merge() builds its renames through RenameFactory.DEFAULT_FACTORY, which resets the
            // two type options to their defaults, so the script's choices have to be re-applied.
            for (Rename<?> rename : merged) {
                if (rename instanceof RenameTypes renamed) {
                    renamed.setRenameInnerClasses(script.renameInnerClasses);
                    renamed.setFixAccessibility(script.fixAccessibility);
                }
            }
            int applied = merged.apply(directory);
            directory.save();
            module.writeApk(out, (path, method, length) -> {
            });
            if (log != null) {
                log.log("applied=" + applied + " locked=" + locked + " declared=" + declared);
            }
            return new Result(applied, locked, declared);
        } finally {
            directory.close();
        }
    }

    /**
     * Turns the script into a batch. Rules are added in script order; {@link RenameBatch#merge()}
     * later reconciles the three directives with each other.
     */
    private static RenameBatch build(DexDirectory directory, Script script, Log log) {
        RenameBatch batch = new RenameBatch();
        RenameTypes types = new RenameTypes();
        RenameFields fields = new RenameFields();
        RenameMethods methods = new RenameMethods();
        types.setRenameInnerClasses(script.renameInnerClasses);
        types.setFixAccessibility(script.fixAccessibility);
        for (Rule rule : script.rules) {
            try {
                add(directory, rule, types, fields, methods);
                if (log != null) {
                    log.log("rule ok: " + rule.kind.name().toLowerCase(Locale.ROOT)
                            + " " + rule.search);
                }
            } catch (Exception e) {
                script.rejected.add(new Rejected(rule.line, rule.search + " " + rule.replace,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                if (log != null) {
                    log.log("rule skipped: " + rule.search + " (" + e.getMessage() + ")");
                }
            }
        }
        // Rename.close() clears the key pair maps, so it must not be called here: the batch still
        // needs everything that was just added.
        batch.add(types);
        batch.add(fields);
        batch.add(methods);
        batch.removeEmptyRenames();
        return batch;
    }

    private static void add(DexDirectory directory, Rule rule,
                            RenameTypes types, RenameFields fields, RenameMethods methods) {
        switch (rule.kind) {
            case PKG: {
                PackageKey search = packageKey(rule.search);
                PackageKey replace = packageKey(rule.replace);
                if (search == null || replace == null) {
                    throw new IllegalArgumentException("bad package name");
                }
                types.addPackage(directory, search, replace, rule.deep);
                return;
            }
            case TYPE: {
                TypeKey search = TypeKey.of(rule.search);
                TypeKey replace = TypeKey.of(rule.replace);
                if (search == null || replace == null) {
                    throw new IllegalArgumentException("bad type name");
                }
                types.add(directory, new KeyPair<>(search, replace));
                return;
            }
            case FIELD: {
                FieldKey search = FieldKey.parse(rule.search);
                if (search == null) {
                    throw new IllegalArgumentException("bad field key");
                }
                fields.add(directory, search, rule.replace);
                return;
            }
            case METHOD: {
                MethodKey search = MethodKey.parse(rule.search);
                if (search == null) {
                    throw new IllegalArgumentException("bad method key");
                }
                methods.add(directory, search, rule.replace);
                return;
            }
            default:
                throw new IllegalStateException(rule.kind.name());
        }
    }

    /**
     * Accepts both the source form ({@code com.example}) and the descriptor form
     * ({@code Lcom/example/}), because {@link PackageKey#of(String)} only understands the latter.
     */
    static PackageKey packageKey(String name) {
        if (name == null) return null;
        String text = name.trim();
        if (text.isEmpty()) return null;
        if (text.startsWith("L")) return PackageKey.of(text);
        text = text.replace('.', '/');
        if ("/".equals(text)) return PackageKey.of("L");
        if (!text.endsWith("/")) text = text + "/";
        return PackageKey.of("L" + text);
    }

    /**
     * Parses the rename script. Only syntax is checked here: whether a name actually exists in
     * the dex files is answered later, when the batch is applied.
     */
    public static Script parse(String text) {
        Script script = new Script();
        if (text == null) return script;
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 3) {
                script.rejected.add(new Rejected(i + 1, line, "expected: <kind> <search> <replace>"));
                continue;
            }
            if (parts.length > 4) {
                script.rejected.add(new Rejected(i + 1, line, "too many tokens"));
                continue;
            }
            Rule.Kind kind = kindOf(parts[0]);
            if (kind == null) {
                script.rejected.add(new Rejected(i + 1, line, "unknown kind: " + parts[0]));
                continue;
            }
            boolean deep = true;
            if (parts.length == 4) {
                String modifier = parts[3].toLowerCase(Locale.ROOT);
                if ("exact".equals(modifier)) {
                    deep = false;
                } else if (!"deep".equals(modifier)) {
                    script.rejected.add(new Rejected(i + 1, line,
                            "unknown modifier: " + parts[3]));
                    continue;
                }
            }
            script.rules.add(new Rule(kind, parts[1], parts[2], deep, i + 1));
        }
        return script;
    }

    private static Rule.Kind kindOf(String token) {
        switch (token.toLowerCase(Locale.ROOT)) {
            case "pkg":
            case "package":
                return Rule.Kind.PKG;
            case "type":
            case "class":
                return Rule.Kind.TYPE;
            case "field":
                return Rule.Kind.FIELD;
            case "method":
                return Rule.Kind.METHOD;
            default:
                return null;
        }
    }
}