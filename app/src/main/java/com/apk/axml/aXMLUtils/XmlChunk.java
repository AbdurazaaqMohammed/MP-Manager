package com.apk.axml.aXMLUtils;

import android.content.Context;

import com.apk.axml.serializableItems.ResEntry;

import java.io.IOException;
import java.util.List;

public class XmlChunk extends Chunk<XmlChunk.H> {

    private final ResourceMapChunk resourceMap = new ResourceMapChunk(this);
    StringPoolChunk stringPool = new StringPoolChunk(this);
    TagChunk content;
    private final List<ResEntry> resourceEntries;

    public XmlChunk(Context context) {
        this(context, null);
    }

    public XmlChunk(Context context, List<ResEntry> resourceEntries) {
        super(null);
        this.context=context;
        this.resourceEntries = resourceEntries;
    }

    public class H extends Chunk.Header {

        public H() {
            super(ChunkType.Xml);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {

        }
    }

    @Override
    public void preWrite() {
        header.size=header.headerSize + content.calc()+stringPool.calc() + resourceMap.calc();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        stringPool.write(w);
        resourceMap.write(w);
        content.write(w);
    }

    @Override
    public XmlChunk root() {
        return this;
    }

    private ReferenceResolver referenceResolver;
    @Override
    public ReferenceResolver getReferenceResolver() {
        if (referenceResolver == null) referenceResolver= new DefaultReferenceResolver(resourceEntries);
        return referenceResolver;
    }

}