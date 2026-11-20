package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.AnnotationVisibility;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * Unit test verifying that DexDebugStripper removes debug symbols (.param names, source file, line numbers)
 * while strictly preserving runtime parameter annotations (critical for Retrofit, Room, Moshi, etc.).
 */
public class DexDebugStripperTest {

    @Test
    public void testParameterAnnotationsPreservedWhenStrippingDebugInfo() throws Exception {
        Set<ImmutableAnnotation> paramAnnotations = Collections.singleton(
                new ImmutableAnnotation(AnnotationVisibility.RUNTIME, "Lretrofit2/http/Query;", Collections.emptySet())
        );
        ImmutableMethodParameter param = new ImmutableMethodParameter("Ljava/lang/String;", paramAnnotations, "namedParameter");
        ImmutableMethod method = new ImmutableMethod(
                "Lcim/comcast/com/xal/core/network/api/cmfa/servicesv3/authenticatorId/AuthenticatorIdService;",
                "getAuthenticatorIdStatus",
                Collections.singletonList(param),
                "Lretrofit2/Call;",
                AccessFlags.PUBLIC.getValue() | AccessFlags.ABSTRACT.getValue(),
                Collections.emptySet(),
                null,
                null
        );
        ImmutableClassDef classDef = new ImmutableClassDef(
                "Lcim/comcast/com/xal/core/network/api/cmfa/servicesv3/authenticatorId/AuthenticatorIdService;",
                AccessFlags.PUBLIC.getValue() | AccessFlags.INTERFACE.getValue() | AccessFlags.ABSTRACT.getValue(),
                "Ljava/lang/Object;",
                Collections.emptyList(),
                "AuthenticatorIdService.kt",
                Collections.emptySet(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(method)
        );

        ClassDef stripped = DexDebugStripper.strip(classDef);
        assertNotNull(stripped);
        assertNull("Source file debug info should be stripped", stripped.getSourceFile());

        Iterator<? extends Method> vMethods = stripped.getVirtualMethods().iterator();
        assertTrue(vMethods.hasNext());
        Method strippedMethod = vMethods.next();
        assertEquals(1, strippedMethod.getParameters().size());

        MethodParameter strippedParam = strippedMethod.getParameters().get(0);
        assertNull("Parameter name debug info should be stripped", strippedParam.getName());
        assertEquals("Parameter type must be preserved", "Ljava/lang/String;", strippedParam.getType());
        assertEquals("Parameter annotations must NOT be erased by debug stripper", 1, strippedParam.getAnnotations().size());
        Annotation ann = strippedParam.getAnnotations().iterator().next();
        assertEquals("Lretrofit2/http/Query;", ann.getType());

        // Also test round-trip serialization through DexPool to ensure dexlib2 serializes parameter annotations
        DexPool pool = new DexPool(Opcodes.getDefault());
        pool.internClass(stripped);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);

        DexBackedDexFile backedDex = new DexBackedDexFile(Opcodes.getDefault(), store.getData());
        ClassDef backedClass = backedDex.getClasses().iterator().next();
        Method backedMethod = backedClass.getVirtualMethods().iterator().next();
        MethodParameter backedParam = backedMethod.getParameters().get(0);

        assertEquals("Round-trip written parameter annotations must be preserved", 1, backedParam.getAnnotations().size());
        assertEquals("Lretrofit2/http/Query;", backedParam.getAnnotations().iterator().next().getType());
    }
}
