package io.github.abdurazaaqmohammed.patcherfixture.legacy;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class FixtureModule implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam parameter) {
        if (!parameter.packageName.startsWith("io.github.abdurazaaqmohammed.patcherfixture.target")) return;
        XposedHelpers.findAndHookMethod("io.github.abdurazaaqmohammed.patcherfixture.target.FixtureValue",
                parameter.classLoader, "value", XC_MethodReplacement.returnConstant("LEGACY_HOOKED"));
    }
}
