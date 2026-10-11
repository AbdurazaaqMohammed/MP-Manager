package com.apk.axml.aXMLUtils;

import com.apk.axml.serializableItems.XMLEntry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/*
 * Created by APK Explorer & Editor <apkeditor@protonmail.com> on Sept. 06, 2025
 */
public class Utils {

    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) != -1) baos.write(buf,0,n);
        return baos.toByteArray();
    }

    /**
     * Renders the decoded entries as editable text.
     *
     * <p>Entries whose text starts with a space are continuations of the previous line, which is
     * how the root element's {@code xmlns:*} declarations end up on the {@code <manifest ...>}
     * line instead of each on its own line.
     */
    public static String decodeAsString(List<XMLEntry> xmlEntries) {
        StringBuilder sb = new StringBuilder();
        for (XMLEntry xmlEntry : xmlEntries) {
            String text = xmlEntry.getText();
            if (sb.length() > 0 && !text.startsWith(" ")) sb.append("\n");
            sb.append(text);
        }
        return sb.toString().trim();
    }

}