package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Verifies the documents-provider injection: the {@code <provider>} written
 * into a real binary manifest, the dex slot it is given, and the provider
 * class being liftable out of MP-Manager's own dex.
 */
public class DocumentsProviderInjectTest {

    private static final String AUTHORITY = "com.example.game.mpmanager.dump";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A framework resource APK from res/raw: binary manifest, no dex. */
    private File rawApk() throws Exception {
        File source = new File("src/main/res/raw/android_28.apk");
        assumeTrue("android_28.apk fixture is missing", source.isFile());
        File apk = tmp.newFile("framework.apk");
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = new FileOutputStream(apk)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        return apk;
    }

    private static ResXmlElement findProvider(AndroidManifestBlock manifest) {
        ResXmlElement application = manifest.getApplicationElement();
        if (application == null) return null;
        for (Iterator<ResXmlElement> it = application.getElements(); it.hasNext(); ) {
            ResXmlElement child = it.next();
            if (!"provider".equals(child.getName())) continue;
            ResXmlAttribute authorities = child.searchAttributeByName("authorities");
            if (authorities != null && AUTHORITY.equals(authorities.getValueAsString())) return child;
        }
        return null;
    }

    private static String attribute(ResXmlElement element, String name) {
        ResXmlAttribute attribute = element.searchAttributeByName(name);
        return attribute == null ? null : attribute.getValueAsString();
    }

    /** exported / grantUriPermissions are stored as real booleans, not strings. */
    private static Boolean boolAttribute(ResXmlElement element, String name) {
        ResXmlAttribute attribute = element.searchAttributeByName(name);
        return attribute == null ? null : attribute.getValueAsBoolean();
    }

    @Test
    public void addsDeclaredProviderToBinaryManifest() throws Exception {
        File apk = rawApk();
        File patched = new File(tmp.getRoot(), "AndroidManifest.xml");

        assertNotNull(DocumentsProviderInjector.patchManifest(apk, AUTHORITY, patched));
        assertTrue(patched.length() > 0);

        // Re-read what was written: the binary manifest has to stay parseable.
        AndroidManifestBlock manifest = AndroidManifestBlock.load(patched);
        assertNotNull("patched manifest lost its package name", manifest.getPackageName());

        ResXmlElement provider = findProvider(manifest);
        assertNotNull("provider was not added", provider);
        assertEquals(DocumentsProviderInjector.PROVIDER_CLASS, attribute(provider, "name"));
        assertEquals(DocumentsProviderInjector.MANAGE_DOCUMENTS, attribute(provider, "permission"));
        assertEquals(Boolean.TRUE, boolAttribute(provider, "exported"));
        assertEquals(Boolean.TRUE, boolAttribute(provider, "grantUriPermissions"));

        // The system picker only discovers providers that declare this action.
        boolean hasAction = false;
        for (Iterator<ResXmlElement> it = provider.getElements(); it.hasNext(); ) {
            ResXmlElement child = it.next();
            if (!"intent-filter".equals(child.getName())) continue;
            for (Iterator<ResXmlElement> actions = child.getElements(); actions.hasNext(); ) {
                ResXmlElement action = actions.next();
                if ("android.content.action.DOCUMENTS_PROVIDER".equals(attribute(action, "name"))) {
                    hasAction = true;
                }
            }
        }
        assertTrue("DOCUMENTS_PROVIDER action missing", hasAction);
    }

    @Test
    public void skipsAnAlreadyPatchedManifest() throws Exception {
        File apk = rawApk();
        File first = new File(tmp.getRoot(), "first.xml");
        assertNotNull(DocumentsProviderInjector.patchManifest(apk, AUTHORITY, first));

        // Rebuilding the APK with that manifest and patching again must be a
        // no-op instead of stacking a second <provider>.
        File repacked = new File(tmp.getRoot(), "repacked.apk");
        try (ZipFile source = new ZipFile(apk);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(repacked))) {
            java.util.Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                out.putNextEntry(new ZipEntry(entry.getName()));
                if ("AndroidManifest.xml".equals(entry.getName())) {
                    java.nio.file.Files.copy(first.toPath(), out);
                } else {
                    try (InputStream in = source.getInputStream(entry)) {
                        byte[] buffer = new byte[16384];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
                out.closeEntry();
            }
        }
        assertTrue(DocumentsProviderInjector.hasProvider(repacked, AUTHORITY));
        assertNull(DocumentsProviderInjector.patchManifest(repacked, AUTHORITY,
                new File(tmp.getRoot(), "second.xml")));
    }

    @Test
    public void picksTheNextFreeDexSlot() throws Exception {
        File apk = tmp.newFile("slots.apk");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(apk))) {
            for (String name : new String[]{"AndroidManifest.xml", "classes.dex", "classes2.dex",
                    "classes3.dex", "classes4.dex", "classes5.dex", "classes6.dex"}) {
                out.putNextEntry(new ZipEntry(name));
                out.write(0);
                out.closeEntry();
            }
        }
        assertEquals("classes7.dex", DocumentsProviderInjector.nextDexName(apk));

        // A resource-only split has no dex at all and must still get classes.dex.
        File split = tmp.newFile("split.apk");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(split))) {
            out.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            out.write(0);
            out.closeEntry();
        }
        assertEquals("classes.dex", DocumentsProviderInjector.nextDexName(split));
    }

    /**
     * Lifts the provider classes out of a real MP-Manager build. Only runs
     * when a built APK is present, so the suite still passes on a clean
     * checkout.
     */
    @Test
    public void buildsProviderDexFromOwnApk() throws Exception {
        File apk = new File("build/outputs/apk/full/release/app-full-release.apk");
        if (!apk.isFile()) apk = new File("full/release/app-full-release.apk");
        assumeTrue("no built MP-Manager APK to lift the provider from", apk.isFile());

        File workDir = tmp.newFolder("dex");
        File dex;
        try {
            dex = DocumentsProviderInjector.buildProviderDex(apk, workDir);
        } catch (java.io.IOException e) {
            // An APK built before the provider class existed: nothing to lift.
            assumeTrue("APK predates the provider class: " + e.getMessage(), false);
            return;
        }
        assertTrue(dex.length() > 0);

        // Every class of the provider package has to be in the generated dex:
        // a nested class left behind makes the patched app crash with
        // NoClassDefFoundError while the provider is being attached.
        Set<String> expected = providerClasses(apk);
        Set<String> generated = providerClasses(dex);
        assertEquals(expected, generated);
        assertTrue(generated.contains(
                "L" + DocumentsProviderInjector.PROVIDER_CLASS.replace('.', '/') + ";"));
    }

    /** All {@code mpmanager/dump/**} class descriptors in an APK or a raw dex. */
    private static Set<String> providerClasses(File file) throws Exception {
        Set<String> result = new HashSet<>();
        if (file.getName().endsWith(".dex")) {
            for (ClassDef classDef : DexFileFactory.loadDexFile(file, Opcodes.getDefault()).getClasses()) {
                if (classDef.getType().startsWith("Lmpmanager/dump/")) result.add(classDef.getType());
            }
            return result;
        }
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(file, Opcodes.getDefault());
        for (String entry : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entry);
            if (dexEntry == null) continue;
            for (ClassDef classDef : dexEntry.getDexFile().getClasses()) {
                if (classDef.getType().startsWith("Lmpmanager/dump/")) result.add(classDef.getType());
            }
        }
        return result;
    }
}
