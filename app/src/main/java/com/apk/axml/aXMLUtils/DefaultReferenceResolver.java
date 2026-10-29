package com.apk.axml.aXMLUtils;

import android.annotation.SuppressLint;

import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultReferenceResolver implements ReferenceResolver {

    // Names allow '$' (AGP intermediate resources) and '.' (style names like
    // Theme.App); the package segment may contain dots as well.
    static final Pattern pat = Pattern.compile("^[@?]\\+?(?:([\\w.]+):)?(?:(\\w+)/)?([\\w.$]+)$");

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

        // A bare 8-hex-digit name is the decoder's fallback for references
        // that had no resolvable name. Hex-looking real names (e.g.
        // @string/def) must go through the resource tables instead, or they
        // silently become bogus literal ids.
        if (type == null && name.length() == 8 && name.matches("[0-9a-fA-F]{8}")) {
            try {
                return Integer.parseInt(Objects.requireNonNull(name), aXMLEncoder.Config.defaultReferenceRadix);
            } catch (Exception ignored) {
            }
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
