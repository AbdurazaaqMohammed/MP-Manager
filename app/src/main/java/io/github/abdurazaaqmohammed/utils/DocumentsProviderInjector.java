package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Adds MP-Manager's {@link mpmanager.dump.MPManagerDocumentsProvider} to an
 * APK so its private data can be browsed through the Storage Access Framework
 * without root — the same idea as LSPatch's "inject documents provider"
 * option.
 *
 * <p>Two things have to happen for the provider to work, and both are done
 * here:</p>
 * <ol>
 *   <li>the provider class must exist in the target's code. Rather than
 *   shipping a prebuilt dex that silently drifts from the source, the class
 *   definition is lifted straight out of MP-Manager's own installed APK and
 *   written into a brand new {@code classesN.dex};</li>
 *   <li>the {@code <provider>} must be declared in the binary manifest, with
 *   the {@code DOCUMENTS_PROVIDER} action so the system picker lists it,
 *   {@code exported}/{@code grantUriPermissions} as real booleans, and
 *   {@code android.permission.MANAGE_DOCUMENTS} so only the system Documents
 *   UI can bind it. Other apps then reach the data only through a grant the
 *   user makes in the picker.</li>
 * </ol>
 */
public final class DocumentsProviderInjector {

    /** Class carried by MP-Manager and copied into the patched APK. */
    public static final String PROVIDER_CLASS = "mpmanager.dump.MPManagerDocumentsProvider";
    private static final String PROVIDER_DESCRIPTOR = "Lmpmanager/dump/MPManagerDocumentsProvider;";
    /** Every class of this package is copied, nested ones included. */
    private static final String PROVIDER_PACKAGE_PREFIX = "Lmpmanager/dump/";
    /** Per-package authority suffix, so two patched apps never collide. */
    public static final String AUTHORITY_SUFFIX = ".mpmanager.dump";
    public static final String MANAGE_DOCUMENTS = "android.permission.MANAGE_DOCUMENTS";
    private static final String ACTION_DOCUMENTS_PROVIDER = "android.content.action.DOCUMENTS_PROVIDER";

    private static final String MANIFEST_ENTRY = "AndroidManifest.xml";

    // android.R.attr ids — a binary manifest stores attributes by resource id,
    // so they have to be created with the right one to be read back as the
    // framework attributes they are.
    private static final int ATTR_NAME = 0x01010003;
    private static final int ATTR_PERMISSION = 0x01010006;
    private static final int ATTR_EXPORTED = 0x01010010;
    private static final int ATTR_AUTHORITIES = 0x01010018;
    private static final int ATTR_GRANT_URI_PERMISSIONS = 0x0101001b;

    private DocumentsProviderInjector() {
    }

    public static String authorityFor(String packageName) {
        return packageName + AUTHORITY_SUFFIX;
    }

    /**
     * True when the APK already declares a provider with this authority, so a
     * second patch of an already patched file does not stack duplicates.
     */
    public static boolean hasProvider(File apk, String authority) {
        try {
            return hasProvider(loadManifest(apk), authority);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasProvider(AndroidManifestBlock manifest, String authority) {
        for (ResXmlElement provider : providers(manifest)) {
            ResXmlAttribute attribute = attribute(provider, ATTR_AUTHORITIES);
            String authorities = attribute == null ? null : attribute.getValueAsString();
            if (authorities == null) continue;
            for (String one : authorities.split(";")) {
                if (authority.equals(one.trim())) return true;
            }
        }
        return false;
    }

    /**
     * Writes a dex holding the provider — plus every other class of the
     * {@code mpmanager/dump/} package, because a nested or helper class is a
     * separate class file and the target app crashes while attaching the
     * provider if one is left behind.
     */
    public static File buildProviderDex(Context context, File workDir) throws IOException {
        String self = context.getApplicationInfo().sourceDir;
        if (self == null) throw new IOException("Cannot locate MP-Manager's own APK");
        return buildProviderDex(new File(self), workDir);
    }

    /** {@link #buildProviderDex(Context, File)} against an explicit APK file. */
    public static File buildProviderDex(File ownApk, File workDir) throws IOException {
        List<ClassDef> providerClasses = findProviderClasses(ownApk);
        DexPool pool = new DexPool(Opcodes.getDefault());
        for (ClassDef classDef : providerClasses) pool.internClass(classDef);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        File out = new File(workDir, "provider.dex");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(Arrays.copyOf(store.getData(), store.getSize()));
        }
        return out;
    }

    /** Every class compiled from the provider's package, outer class first. */
    private static List<ClassDef> findProviderClasses(File ownApk) throws IOException {
        List<ClassDef> found = new ArrayList<>();
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(ownApk, Opcodes.getDefault());
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
            if (entry == null) continue;
            for (ClassDef classDef : entry.getDexFile().getClasses()) {
                if (classDef.getType().startsWith(PROVIDER_PACKAGE_PREFIX)) found.add(classDef);
            }
        }
        boolean hasOuter = false;
        for (ClassDef classDef : found) {
            if (PROVIDER_DESCRIPTOR.equals(classDef.getType())) hasOuter = true;
        }
        if (!hasOuter) {
            throw new IOException(PROVIDER_CLASS + " is missing from MP-Manager's own dex");
        }
        return found;
    }

    /**
     * Writes a copy of the APK's binary manifest with the provider declaration
     * added, into {@code out}. Returns the file to feed to
     * {@link ApkZipAlignUtil#rebuildApk} as a replacement of AndroidManifest.xml.
     */
    public static File patchManifest(File apk, String authority, File out) throws IOException {
        AndroidManifestBlock manifest = loadManifest(apk);
        if (manifest.getPackageName() == null) {
            throw new IOException("APK has no readable AndroidManifest.xml");
        }
        if (hasProvider(manifest, authority)) return null;

        ResXmlElement application = manifest.getOrCreateApplicationElement();
        ResXmlElement provider = application.newElement("provider");
        androidAttribute(provider, "name", ATTR_NAME).setValueAsString(PROVIDER_CLASS);
        androidAttribute(provider, "authorities", ATTR_AUTHORITIES).setValueAsString(authority);
        // Real booleans: a string "true" is read back as false, which would
        // quietly leave the provider unexported and invisible to the picker.
        androidAttribute(provider, "exported", ATTR_EXPORTED).setValueAsBoolean(true);
        androidAttribute(provider, "grantUriPermissions", ATTR_GRANT_URI_PERMISSIONS)
                .setValueAsBoolean(true);
        androidAttribute(provider, "permission", ATTR_PERMISSION).setValueAsString(MANAGE_DOCUMENTS);

        // Without this intent-filter the system DocumentsUI never discovers
        // the provider: it lists providers by querying this action.
        ResXmlElement filter = provider.newElement("intent-filter");
        ResXmlElement action = filter.newElement("action");
        androidAttribute(action, "name", ATTR_NAME).setValueAsString(ACTION_DOCUMENTS_PROVIDER);

        manifest.refresh();
        manifest.writeBytes(out);
        return out;
    }

    /** Free dex slot for a new dex file, e.g. {@code classes3.dex}. */
    public static String nextDexName(File apk) throws IOException {
        int max = 0;
        boolean any = false;
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("classes") || !name.endsWith(".dex")) continue;
                String middle = name.substring("classes".length(), name.length() - ".dex".length());
                if (middle.isEmpty()) {
                    any = true;
                    max = Math.max(max, 1);
                    continue;
                }
                try {
                    max = Math.max(max, Integer.parseInt(middle));
                    any = true;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // Exactly one past the highest existing index: that is the slot both
        // the multidex support library and ART's archive scan look for. A
        // split with no dex at all gets classes.dex instead of classes1.dex.
        return any ? "classes" + (max + 1) + ".dex" : "classes.dex";
    }

    // ------------------------------------------------------------------
    // manifest helpers
    // ------------------------------------------------------------------

    private static AndroidManifestBlock loadManifest(File apk) throws IOException {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry(MANIFEST_ENTRY);
            if (entry == null) throw new IOException("APK has no " + MANIFEST_ENTRY);
            try (InputStream is = zip.getInputStream(entry)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                return AndroidManifestBlock.load(new ByteArrayInputStream(bos.toByteArray()));
            }
        }
    }

    private static List<ResXmlElement> providers(AndroidManifestBlock manifest) {
        List<ResXmlElement> result = new ArrayList<>();
        ResXmlElement application = manifest.getApplicationElement();
        if (application == null) return result;
        for (Iterator<ResXmlElement> it = application.getElements(); it.hasNext(); ) {
            ResXmlElement child = it.next();
            if ("provider".equals(child.getName())) result.add(child);
        }
        return result;
    }

    private static ResXmlAttribute attribute(ResXmlElement element, int resourceId) {
        ResXmlAttribute attribute = element.searchAttributeByResourceId(resourceId);
        if (attribute != null) return attribute;
        // Falls back to a plain-name lookup: manifests written by other tools
        // occasionally lose the resource id but keep the attribute name.
        switch (resourceId) {
            case ATTR_AUTHORITIES: return element.searchAttributeByName("authorities");
            case ATTR_NAME: return element.searchAttributeByName("name");
            default: return null;
        }
    }

    private static ResXmlAttribute androidAttribute(ResXmlElement element, String name, int resourceId) {
        return element.getOrCreateAndroidAttribute(name, resourceId);
    }
}
