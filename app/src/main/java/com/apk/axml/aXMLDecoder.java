package com.apk.axml;

import android.util.TypedValue;

import com.apk.axml.aXMLUtils.AXmlResourceParser;
import com.apk.axml.aXMLUtils.AttrValueResolver;
import com.apk.axml.aXMLUtils.StringPoolChunk;
import com.apk.axml.serializableItems.ResEntry;
import com.apk.axml.aXMLUtils.Utils;
import com.apk.axml.serializableItems.XMLEntry;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
 * Created by APK Explorer & Editor <apkeditor@protonmail.com> on Sept. 06, 2025
 */
public class aXMLDecoder {

	/** First line of the editable text form; mirrors a normal XML declaration. */
	private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
	/** One indent level, used both for nested elements and for their attributes. */
	private static final String INDENT_UNIT = "\t";
	/** Separator before the root element's namespace declarations, which share its line. */
	private static final String ATTR_INLINE = " ";
	/** Closes a childless element, e.g. {@code android:name="..." />}. */
	private static final String SELF_CLOSING = " />";
	/**
	 * Longest element that keeps all of its attributes on the tag line. Anything
	 * longer is written one attribute per line, which stays readable in an editor
	 * without horizontal scrolling.
	 */
	private static final int MAX_INLINE_TAG_LENGTH = 120;
	/** Namespace URI of framework attributes; only those have a symbolic value table. */
	private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";

	private final InputStream inputStream;
	private final List<ResEntry> resourceEntries;
	private StringPoolChunk.Encoding poolEncoding = StringPoolChunk.Encoding.UNICODE;

	public aXMLDecoder(InputStream inputStream) {
		this.inputStream = inputStream;
		this.resourceEntries = null;
	}

	public aXMLDecoder(InputStream inputStream, List<ResEntry> resourceEntries) {
		this.inputStream = inputStream;
		this.resourceEntries = resourceEntries;
	}

	public StringPoolChunk.Encoding getPoolEncoding() {
		return poolEncoding;
	}

    public List<XMLEntry> decode() throws XmlPullParserException, IOException {
		byte[] bytes = Utils.toByteArray(inputStream);
		List<XMLEntry> result = new ArrayList<>();
		Deque<OpenElem> stack = new ArrayDeque<>();

		// One lookup per attribute, so index the resource table instead of scanning all of
		// it (~10k entries) for every attribute of every element.
		Map<Integer, ResEntry> byId = null;
		if (resourceEntries != null && !resourceEntries.isEmpty()) {
			byId = new HashMap<>(resourceEntries.size() * 2);
			for (ResEntry e : resourceEntries) byId.putIfAbsent(e.getResourceId(), e);
		}

		// Namespace prefixes are only known once every attribute has been seen, so the root's
		// xmlns entries are inserted after the single pass that reads the document.
		Set<String> usedPrefixes = new LinkedHashSet<>();
		int xmlnsInsertIndex = -1;

		AXmlResourceParser parser = new AXmlResourceParser();
		parser.open(new ByteArrayInputStream(bytes));

		result.add(new XMLEntry(XML_DECLARATION, "", "", ""));

		while (true) {
			int type = parser.next();
			if (type == XmlPullParser.END_DOCUMENT) break;

		if (type == XmlPullParser.START_TAG) {
			String tag = parser.getName();
			if (!stack.isEmpty()) Objects.requireNonNull(stack.peek()).hadChildren = true;

			int depth = stack.size();
			String indent = indentFor(depth);

			int attrCount = parser.getAttributeCount();
			List<XMLEntry> attrEntries = new ArrayList<>(attrCount);
			List<String> attrTexts = new ArrayList<>(attrCount);
			int inlineLength = indent.length() + 1 + tag.length() + 2;
			for (int i = 0; i < attrCount; i++) {
				String prefix = parser.getAttributePrefix(i);
				String name = parser.getAttributeName(i);
				String fullName = (prefix != null && !prefix.isEmpty()) ? prefix + ":" + name : name;
				String value = escapeAttr(getAttributeValue(parser, byId, i, name,
						parser.getAttributeNamespace(i)));
				attrEntries.add(new XMLEntry(indentFor(depth + 1) + fullName, "=\"", value, "\""));
				attrTexts.add(fullName + "=\"" + value + "\"");
				inlineLength += 1 + attrTexts.get(i).length();
				if (prefix != null && !prefix.isEmpty()) usedPrefixes.add(prefix);
			}

			result.add(new XMLEntry(indent + "<" + tag, "", "", ""));
			// Namespace declarations are inserted right after the opening tag entry, before
			// the attributes, so that they stay on the tag line and never end up on the
			// previous line, where the pull parser rejects them.
			if (xmlnsInsertIndex < 0) xmlnsInsertIndex = result.size();
			// The root element always carries the xmlns declarations, so it must stay in
			// the multi-entry form: an inlined root would push them after the tag line.
			if (attrCount > 0 && depth > 0 && inlineLength <= MAX_INLINE_TAG_LENGTH) {
				StringBuilder sb = new StringBuilder(inlineLength).append(indent).append('<').append(tag);
				for (String attr : attrTexts) sb.append(' ').append(attr);
				result.set(result.size() - 1, new XMLEntry(sb.toString(), "", "", ""));
			} else {
				result.addAll(attrEntries);
			}

			int closeIndex = result.size() - 1;
			stack.push(new OpenElem(tag, closeIndex));

		} else if (type == XmlPullParser.END_TAG) {
			String tag = parser.getName();

			OpenElem open = stack.pop();
			XMLEntry lastItem = result.get(open.closeIndex);

			if (!open.hadChildren) {
				if ("=\"".equals(lastItem.getMiddleTag()))
					result.set(open.closeIndex,
							new XMLEntry(lastItem.getTag(), lastItem.getMiddleTag(), lastItem.getValue(), lastItem.getEndTag() + SELF_CLOSING));
				else
					result.set(open.closeIndex,
							new XMLEntry(lastItem.getTag() + SELF_CLOSING, lastItem.getMiddleTag(), lastItem.getValue(), lastItem.getEndTag()));
			} else {
				if ("=\"".equals(lastItem.getMiddleTag()))
					result.set(open.closeIndex,
							new XMLEntry(lastItem.getTag(), lastItem.getMiddleTag(), lastItem.getValue(), lastItem.getEndTag() + ">"));
				else
					result.set(open.closeIndex,
							new XMLEntry(lastItem.getTag() + ">", lastItem.getMiddleTag(), lastItem.getValue(), lastItem.getEndTag()));
				result.add(new XMLEntry(indentFor(stack.size()) + "</" + tag + ">", "", "", ""));
			}

		} else if (type == XmlPullParser.TEXT) {
			if (!stack.isEmpty()) Objects.requireNonNull(stack.peek()).hadChildren = true;
			String text = parser.getText();
			if (text != null && !text.isEmpty())
				result.add(new XMLEntry(indentFor(stack.size() + 1) + escapeText(text),"","",""));
		}
		}

		if (xmlnsInsertIndex >= 0) {
			int at = xmlnsInsertIndex;
			if (usedPrefixes.contains("android"))
				result.add(at++, new XMLEntry(ATTR_INLINE + "xmlns:android","=\"","http://schemas.android.com/apk/res/android","\""));
			if (usedPrefixes.contains("tools"))
				result.add(at++, new XMLEntry(ATTR_INLINE + "xmlns:tools","=\"","http://schemas.android.com/apk/res-auto","\""));
			for (String p : usedPrefixes) {
				if (p.equals("android") || p.equals("tools")) continue;
				result.add(at++, new XMLEntry(ATTR_INLINE + "xmlns:" + p,"=\"","http://schemas.android.com/apk/res-auto","\""));
			}
		}

		parser.close();
		poolEncoding = parser.isStringPoolUtf8() ? StringPoolChunk.Encoding.UTF8
				: StringPoolChunk.Encoding.UNICODE;
		return result;
    }

    public String decodeAsString() throws XmlPullParserException, IOException {
		return Utils.decodeAsString(decode());
	}

	private static String getAttributeValue(AXmlResourceParser parser, Map<Integer, ResEntry> byId, int index) {
		return getAttributeValue(parser, byId, index, null, null);
	}

	/**
	 * @param attrName      local attribute name, used to look up a symbolic value
	 *                      table for enums and flag sets (layout_gravity and friends)
	 * @param attrNamespace namespace URI, only the Android framework one qualifies
	 */
	private static String getAttributeValue(AXmlResourceParser parser, Map<Integer, ResEntry> byId, int index,
										   String attrName, String attrNamespace) {
		final int type = parser.getAttributeValueType(index);
		final int data = parser.getAttributeValueData(index);
		final String symbolic = symbolic(attrName, attrNamespace, type, data);

		return switch (type) {
            case TypedValue.TYPE_STRING -> parser.getAttributeValue(index);
            case TypedValue.TYPE_REFERENCE -> {
                ResEntry e = byId == null ? null : byId.get(data);
                if (e != null) {
                    yield e.getName() != null ? e.getName() : e.getValue() != null ? e.getValue() : e.getResAttr();
                }
                yield String.format("@%08X", data);
            }
            case TypedValue.TYPE_ATTRIBUTE -> {
                ResEntry e = byId == null ? null : byId.get(data);
                if (e != null) {
                    yield e.getName() != null ? "?" + e.getName().substring(1) : e.getValue() != null ? e.getValue() : String.format("?%08X", data);
                }
                yield String.format("?%08X", data);
            }
            case TypedValue.TYPE_INT_BOOLEAN -> Boolean.toString(data != 0);
            case TypedValue.TYPE_INT_HEX -> symbolic != null ? symbolic : String.format("0x%08X", data);
            case TypedValue.TYPE_INT_DEC -> symbolic != null ? symbolic : Integer.toString(data);
            case TypedValue.TYPE_DIMENSION ->
                    trimTrailingZero(complexToFloat(data)) + DIMENSION_UNITS[data & TypedValue.COMPLEX_UNIT_MASK];
            case TypedValue.TYPE_FRACTION ->
                    trimTrailingZero(complexToFloat(data)) + FRACTION_UNITS[data & TypedValue.COMPLEX_UNIT_MASK];
            case TypedValue.TYPE_FLOAT -> trimTrailingZero(Float.intBitsToFloat(data));
            case TypedValue.TYPE_INT_COLOR_ARGB8 -> String.format("#%08X", data); // AARRGGBB

            case TypedValue.TYPE_INT_COLOR_RGB8 ->
                    String.format("#%06X", data & 0x00FFFFFF); // RRGGBB

            case TypedValue.TYPE_INT_COLOR_ARGB4 ->
                    String.format("#%04X", data & 0x0000FFFF); // ARGB (4 bits each)

            case TypedValue.TYPE_INT_COLOR_RGB4 ->
                    String.format("#%03X", data & 0x00000FFF); // RGB (4 bits each)

            default -> {
                String v = parser.getAttributeValue(index);
                yield v != null ? v : String.format("<0x%X, type 0x%02X>", data, type);
            }
        };
	}

	/**
	 * Symbolic name for an integer attribute whose value set has a table, or null
	 * when it should stay a plain number. Only framework attributes qualify: an
	 * app-defined {@code layout_gravity} in another namespace means nothing here.
	 */
	private static String symbolic(String attrName, String attrNamespace, int type, int data) {
		if (attrName == null) return null;
		if (attrNamespace != null && !attrNamespace.isEmpty()
				&& !ANDROID_NAMESPACE.equals(attrNamespace)) return null;
		if (type != TypedValue.TYPE_INT_HEX && type != TypedValue.TYPE_INT_DEC) return null;
		if (!AttrValueResolver.isKnown(attrName)) return null;
		return AttrValueResolver.resolve(attrName, data);
	}

	private static String indentFor(int depth) {
		if (depth <= 0) return "";
		StringBuilder sb = new StringBuilder(depth * INDENT_UNIT.length());
		for (int i = 0; i < depth; i++) sb.append(INDENT_UNIT);
		return sb.toString();
	}

	private static String escapeText(String s) {
		return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
	}

	private static String escapeAttr(String s) {
		return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
	}

	private static String trimTrailingZero(float f) {
		float rounded = Math.round(f);
		if (Math.abs(f - rounded) < 0.0005f) {
			return Integer.toString((int) rounded);
		}
		String s = new DecimalFormat("0.####", new DecimalFormatSymbols(Locale.US)).format(f);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static float complexToFloat(int complex) {
		return (complex & 0xFFFFFF00) * RADIX_MULTS[(complex>>4)&3];
	}

	private static final float[] RADIX_MULTS = {
			0.00390625f,3.051758E-05f,1.192093E-07f,4.656613E-10f
	};

	private static final String[] DIMENSION_UNITS = {
			"px","dp","sp","pt","in","mm","",""
	};

	private static final String[] FRACTION_UNITS = {
			"%","%p","","","","","",""
	};

	private static class OpenElem {

		final String name;
		final int closeIndex;
		boolean hadChildren = false;

		OpenElem(String name,int closeIndex) {
			this.name = name;
			this.closeIndex = closeIndex;
		}
	}

}
