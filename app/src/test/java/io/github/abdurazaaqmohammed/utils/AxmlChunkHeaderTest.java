package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertNotNull;

import com.apk.axml.aXMLUtils.AttrChunk;
import com.apk.axml.aXMLUtils.EndNameSpaceChunk;
import com.apk.axml.aXMLUtils.EndTagChunk;
import com.apk.axml.aXMLUtils.ResourceMapChunk;
import com.apk.axml.aXMLUtils.StartNameSpaceChunk;
import com.apk.axml.aXMLUtils.StringPoolChunk;
import com.apk.axml.aXMLUtils.XmlChunk;

import org.junit.Test;

public class AxmlChunkHeaderTest {

    @Test
    public void attrAndValueHeadersInit() {
        AttrChunk attr = new AttrChunk(null);
        assertNotNull(attr.header);
        assertNotNull(attr.value);
        assertNotNull(attr.value.header);
    }

    @Test
    public void otherChunkHeadersInit() {
        assertNotNull(new EndTagChunk(null, null).header);
        assertNotNull(new EndNameSpaceChunk(null, null).header);
        assertNotNull(new ResourceMapChunk(null).header);
        assertNotNull(new StartNameSpaceChunk(null).header);
        assertNotNull(new StringPoolChunk(null).header);
        assertNotNull(new XmlChunk(null).header);
    }
}
