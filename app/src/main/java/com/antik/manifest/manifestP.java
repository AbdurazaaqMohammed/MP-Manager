package com.antik.manifest;

import com.reandroid.apk.ApkModule;
import com.reandroid.app.AndroidManifest;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class manifestP {

    public static void patch(ApkModule m) {

        AndroidManifestBlock mf = m.getAndroidManifest();

        if (mf == null) {
            return;
        }

        // Restore compressed native libs so repacked APKs stay installable
        Boolean extractNativeLibs = mf.isExtractNativeLibs();
        if (extractNativeLibs != null && !extractNativeLibs) {
            mf.setExtractNativeLibs(true);
        }

        ResXmlElement M_Element = mf.getManifestElement();

        if (M_Element != null) {

            M_Element.removeAttributesWithId(AndroidManifest.ID_requiredSplitTypes);
            M_Element.removeAttributesWithId(AndroidManifest.ID_splitTypes);
            M_Element.removeAttributesWithId(AndroidManifest.ID_isSplitRequired);
            M_Element.removeAttributesWithId(AndroidManifest.ID_isFeatureSplit);

            M_Element.removeAttributesWithName("requiredSplitTypes");
            M_Element.removeAttributesWithName("splitTypes");
            M_Element.removeAttributesWithName("isSplitRequired");
            M_Element.removeAttributesWithName("isFeatureSplit");
            M_Element.removeAttributesWithName("split");
        }

        List<String> metaDataToRemove = Arrays.asList(
                "com.android.stamp.source",
                "com.android.stamp.type",
                "com.android.vending.splits",
                "com.android.vending.derived.apk.id",
                "com.android.dynamic.apk.fused.modules",
                "com.android.vending.splits.required"
        );

        List<String> activitiesToRemove = Collections.singletonList(
                "com.pairip.licensecheck.LicenseActivity"
        );

        List<String> providersToRemove = Collections.singletonList(
                "com.pairip.licensecheck.LicenseContentProvider"
        );

        List<String> permissionsToRemove = Collections.singletonList(
                "com.android.vending.CHECK_LICENSE"
        );

        RElementsByName(mf, "meta-data", metaDataToRemove);

        RElementsByName(mf, "activity", activitiesToRemove);

        RElementsByName(mf, "provider", providersToRemove);

        RElementsByName(mf, "uses-permission", permissionsToRemove);

        m.setManifest(mf);
    }

    private static void RElementsByName(AndroidManifestBlock mf, String tag, List<String> namesToRemove) {

        Iterator<ResXmlElement> Y_rator;

        if ("uses-permission".equals(tag)) {
            Y_rator = mf.getManifestElement().getElements(tag);
        } else {
            Y_rator = mf.listApplicationElementsByTag(tag).iterator();
        }
        List<ResXmlElement> elementsToRemove = new ArrayList<>();

        while (Y_rator.hasNext()) {
            ResXmlElement element = Y_rator.next();
            String name = AndroidManifestBlock.getAndroidNameValue(element);
            if (namesToRemove.contains(name)) {
                elementsToRemove.add(element);
            }
        }

        for (ResXmlElement element : elementsToRemove) {
            element.removeSelf();
        }
    }
}
