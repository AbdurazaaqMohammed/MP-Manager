package com.apk.axml.aXMLUtils;

import android.annotation.SuppressLint;

import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultReferenceResolver implements ReferenceResolver {

    static Pattern pat = Pattern.compile("^[@?]\\+?(?:(\\w+):)?(?:(\\w+)/)?(\\w+)$");

    private final List<ResEntry> resourceEntries;

    public DefaultReferenceResolver() {
        this(null);
    }

    public DefaultReferenceResolver(List<ResEntry> resourceEntries) {
        this.resourceEntries = resourceEntries;
    }

    public int resolve(ValueChunk value, String ref) {
        Matcher m = pat.matcher(ref);
        if (!m.matches()) throw new RuntimeException("invalid reference");
        String pkg = m.group(1);
        String type = m.group(2);
        String name = m.group(3);

        try {
            return Integer.parseInt(Objects.requireNonNull(name), aXMLEncoder.Config.defaultReferenceRadix);
        } catch (Exception ignored) {
        }

        if (resourceEntries != null && type != null) {
            String targetName = "@" + type + "/" + name;
            for (ResEntry e : resourceEntries) {
                if (targetName.equals(e.getName())) {
                    return e.getResourceId();
                }
            }
        }

        @SuppressLint("DiscouragedApi")
        int id = value.getContext().getResources().getIdentifier(name,type,pkg);
        return id;
    }

}
