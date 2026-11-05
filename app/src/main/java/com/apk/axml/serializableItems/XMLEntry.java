package com.apk.axml.serializableItems;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/*
 * Created by APK Explorer & Editor <apkeditor@protonmail.com> on Sept. 06, 2025
 */
public class XMLEntry implements Serializable {

    // mTag is mutable: inlined element lines (the decoder merges short
    // elements such as <uses-sdk android:minSdkVersion="21" ... /> into a
    // single entry) carry their attributes in the tag text, and editors
    // rewrite that text in place.
    private String mTag, mID = UUID.randomUUID().toString(), mMiddleTag, mEndTag;
    private String mValue;

    public XMLEntry(String tag, String middleTag, String value, String endTag) {
        this.mTag = tag;
        this.mMiddleTag = middleTag;
        this.mValue = value;
        this.mEndTag = endTag;
    }

    public boolean isBoolean() {
        return mValue.equalsIgnoreCase("true") || mValue.equalsIgnoreCase("false");
    }

    public boolean isChecked() {
        return isBoolean() && mValue.equalsIgnoreCase("true");
    }

    public boolean isEmpty() {
        return mTag.trim().isEmpty()
                && mMiddleTag.trim().isEmpty()
                && mValue.trim().isEmpty()
                && mEndTag.trim().isEmpty();
    }

    private boolean isDecoded(String stringResource) {
        if (stringResource == null || stringResource.trim().isEmpty()) {
            return false;
        }
        return stringResource.startsWith("@") || stringResource.startsWith("?@") || stringResource.startsWith("res/");
    }

    public String getText() {
        return mTag + mMiddleTag + mValue + mEndTag;
    }

    public String getText(List<ResEntry> resourceMap) {
        return mTag + mMiddleTag + getAttrValue(resourceMap, mValue) + mEndTag;
    }

    public String getAttrValue(List<ResEntry> resourceMap, String stringResource) {
        if (!isDecoded(stringResource)) return stringResource;

        for (ResEntry entry : resourceMap)
            if (stringResource.equals(entry.getName()) || stringResource.equals(entry.getValue()))
                return entry.getResAttr();

        return stringResource;
    }

    public String getId() {
        return mID;
    }

    public String getTag() {
        return mTag;
    }

    public void setTag(String tag) {
        mTag = tag;
    }

    public String getMiddleTag() {
        return mMiddleTag;
    }

    public String getValue() {
        return mValue;
    }

    public String getEndTag() {
        return mEndTag;
    }

    public void setValue(String value) {
        mValue = value;
    }

}
