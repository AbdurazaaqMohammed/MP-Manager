package com.apk.axml;

import android.content.Context;

import com.apk.axml.aXMLUtils.Chunk;
import com.apk.axml.aXMLUtils.IntWriter;
import com.apk.axml.aXMLUtils.StringPoolChunk;
import com.apk.axml.aXMLUtils.TagChunk;
import com.apk.axml.aXMLUtils.Utils;
import com.apk.axml.aXMLUtils.XmlChunk;
import com.apk.axml.serializableItems.ResEntry;
import com.apk.axml.serializableItems.XMLEntry;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/*
 * Created by APK Explorer & Editor <apkeditor@protonmail.com> on January 22, 2023
 * Based on the original work of @hzw1199 (https://github.com/hzw1199/xml2axml/)
 * & @WindySha (https://github.com/WindySha/Xpatch)
 */
public class aXMLEncoder {

    public static class Config {
        public static StringPoolChunk.Encoding encoding = StringPoolChunk.Encoding.UNICODE;
        public static int defaultReferenceRadix = 16;
    }

    public byte[] encodeString(List<XMLEntry> xmlEntries, Context context) throws XmlPullParserException, IOException {
        return encodeString(xmlEntries, context, null);
    }

    public byte[] encodeString(List<XMLEntry> xmlEntries, Context context, List<ResEntry> resourceEntries) throws XmlPullParserException, IOException {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        XmlPullParser p = f.newPullParser();
        p.setInput(new StringReader(Utils.decodeAsString(xmlEntries)));
        return encode(p, context, resourceEntries);
    }

    public byte[] encodeString(String xml, Context context) throws XmlPullParserException, IOException {
        return encodeString(xml, context, null);
    }

    public byte[] encodeString(String xml, Context context, List<ResEntry> resourceEntries) throws XmlPullParserException, IOException {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        XmlPullParser p = f.newPullParser();
        p.setInput(new StringReader(xml));
        return encode(p, context, resourceEntries);
    }

    private static byte[] encode(XmlPullParser p, Context context, List<ResEntry> resourceEntries) throws XmlPullParserException, IOException {
        XmlChunk chunk = new XmlChunk(context, resourceEntries);
        TagChunk current = null;
        for (int i=p.getEventType(); i!=XmlPullParser.END_DOCUMENT; i=p.next()) {
            switch (i){
                case XmlPullParser.START_TAG:
                    current = new TagChunk(current == null ? chunk : current, p);
                    break;
                case XmlPullParser.END_TAG:
                    Chunk c = current.getParent();
                    current = c instanceof TagChunk?(TagChunk)c:null;
                    break;
                default:
                    break;

            }
        }
        ByteArrayOutputStream os=new ByteArrayOutputStream();
        IntWriter w = new IntWriter(os);
        chunk.write(w);
        w.close();
        return os.toByteArray();
    }

}