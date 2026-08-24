package com.antik.DexPatcher.MethodT;
import static com.antik.DexPatcher.MethodT.patchLaunchMethod.patchLaunchMethod;
import static com.antik.DexPatcher.PairipMethodMake.CMethod.createPairipMethod;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;

import java.util.ArrayList;
import java.util.List;

public class patchLauncher {

    public static ClassDef patchStartupLauncher(ClassDef cd, List<String> j_ts) {
        System.out.println("[INFO] Patching StartupLauncher");
        List<Method> d_ms = new ArrayList<Method>();
        for (Method m : cd.getDirectMethods()) {
            if ("launch".equals(m.getName())) {
                d_ms.add(patchLaunchMethod(m));
            } else if (!"pairip".equals(m.getName())) {
                d_ms.add(m);
            }
        }
        d_ms.add(createPairipMethod(cd.getType(), j_ts));

        return new ImmutableClassDef(cd.getType(), cd.getAccessFlags(), cd.getSuperclass(), cd.getInterfaces(), cd.getSourceFile(), cd.getAnnotations(), cd.getStaticFields(), cd.getInstanceFields(), d_ms, cd.getVirtualMethods());
    }
}
