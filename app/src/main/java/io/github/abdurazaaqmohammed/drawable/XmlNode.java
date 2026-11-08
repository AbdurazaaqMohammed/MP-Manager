package io.github.abdurazaaqmohammed.drawable;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free XML tree used by {@link XmlDrawable}.
 *
 * <p>The framework's {@code Drawable.createFromXml()} only accepts an
 * {@code XmlBlock.Parser} (compiled binary XML). A {@code StringReader}-backed
 * pull parser is cast to that class internally and blows up with a
 * ClassCastException, so plain/decoded XML has to be inflated by hand. This
 * class is the read side of that: it keeps local attribute names (the
 * {@code android:} prefix is stripped) because drawable XML only ever uses
 * framework attributes.</p>
 */
final class XmlNode {

    final String tag;
    final Map<String, String> attrs = new LinkedHashMap<>();
    final List<XmlNode> children = new ArrayList<>();
    String text;

    XmlNode(String tag) {
        this.tag = tag;
    }

    String attr(String name) {
        return attrs.get(name);
    }

    String attr(String name, String fallback) {
        String v = attrs.get(name);
        return v == null ? fallback : v;
    }

    XmlNode first(String childTag) {
        for (XmlNode c : children) {
            if (childTag.equals(c.tag)) return c;
        }
        return null;
    }

    List<XmlNode> all(String childTag) {
        List<XmlNode> out = new ArrayList<>();
        for (XmlNode c : children) {
            if (childTag.equals(c.tag)) out.add(c);
        }
        return out;
    }

    static XmlNode parse(String xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        try {
            // Be tolerant of hand written XML that forgot to declare xmlns:android.
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        } catch (Throwable ignored) {
        }
        parser.setInput(new StringReader(xml));
        Deque<XmlNode> stack = new ArrayDeque<>();
        XmlNode root = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                XmlNode node = new XmlNode(local(parser.getName()));
                int count = parser.getAttributeCount();
                for (int i = 0; i < count; i++) {
                    String name = local(parser.getAttributeName(i));
                    if (name.equals("xmlns") || name.startsWith("xmlns:")) continue;
                    node.attrs.put(name, parser.getAttributeValue(i));
                }
                if (!stack.isEmpty()) stack.peek().children.add(node);
                else if (root == null) root = node;
                stack.push(node);
            } else if (event == XmlPullParser.END_TAG) {
                if (!stack.isEmpty()) stack.pop();
            } else if (event == XmlPullParser.TEXT) {
                if (!stack.isEmpty()) {
                    String t = parser.getText();
                    if (t != null) stack.peek().text = (stack.peek().text == null ? "" : stack.peek().text) + t;
                }
            }
        }
        return root;
    }

    private static String local(String name) {
        if (name == null) return "";
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
