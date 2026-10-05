/*
 * [The "BSD licence"]
 * Copyright (c) 2010 Ben Gruver
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.tools.smali.util;

import java.io.IOException;
import java.io.Writer;

public class IndentingWriter extends Writer {
    //Precomputed indent strings, so that a whole indent can be emitted with a single
    //writer.write() call instead of one call per indent level. INDENTS[i] is a string
    //containing exactly i spaces. The table is immutable after class initialization; deeper
    //indentation than this falls back to writing the spaces one at a time.
    private static final int MAX_CACHED_INDENT = 32;
    private static final String[] INDENTS = new String[MAX_CACHED_INDENT + 1];

    static {
        StringBuilder sb = new StringBuilder(MAX_CACHED_INDENT);
        INDENTS[0] = "";
        for (int i = 1; i <= MAX_CACHED_INDENT; i++) {
            sb.append(' ');
            INDENTS[i] = sb.toString();
        }
    }

    protected final Writer writer;
    protected final char[] buffer = new char[24];
    protected int indentLevel = 0;
    private boolean beginningOfLine = true;
    private static final String newLine = System.lineSeparator();

    public IndentingWriter(Writer writer) {
        this.writer = writer;
    }

    protected void writeIndent() throws IOException {
        int level = indentLevel;
        if (level < 0) {
            level = 0;
        }
        if (level <= MAX_CACHED_INDENT) {
            if (level > 0) {
                writer.write(INDENTS[level]);
            }
        } else {
            for (int i = 0; i < level; i++) {
                writer.write(' ');
            }
        }
    }

    @Override
    public void write(int chr) throws IOException {
        if (chr == '\n') {
            writer.write(newLine);
            beginningOfLine = true;
        } else {
            if (beginningOfLine) {
                writeIndent();
            }
            beginningOfLine = false;
            writer.write(chr);
        }
    }

    /**
     * Writes out a block of text that contains no newlines
     */
    private void writeLine(char[] chars, int start, int len) throws IOException {
        if (beginningOfLine && len > 0) {
            writeIndent();
            beginningOfLine = false;
        }
        writer.write(chars, start, len);
    }


    /**
     * Writes out a block of text that contains no newlines
     */
    private void writeLine(String str, int start, int len) throws IOException {
        if (beginningOfLine && len > 0) {
            writeIndent();
            beginningOfLine = false;
        }
        writer.write(str, start, len);
    }

    @Override
    public void write(char[] chars) throws IOException {
        write(chars, 0, chars.length);
    }

    @Override
    public void write(char[] chars, int start, int len) throws IOException {
        final int end = start+len;
        int pos = start;
        while (pos < end) {
            if (chars[pos] == '\n') {
                writeLine(chars, start, pos-start);

                writer.write(newLine);
                beginningOfLine = true;
                pos++;
                start = pos;
            } else {
                pos++;
            }
        }
        writeLine(chars, start, pos-start);
    }

    @Override
    public void write(String s) throws IOException {
        write(s, 0, s.length());
    }

    @Override
    public void write(String str, int start, int len) throws IOException {
        final int end = start+len;
        int pos = start;
        while (pos < end) {
            //the search must continue from the current position, not from the start of the
            //current line, otherwise the same prefix gets rescanned on every iteration
            int newLinePos = str.indexOf('\n', pos);
            if (newLinePos < 0 || newLinePos >= end) {
                break;
            }
            writeLine(str, start, newLinePos-start);
            writer.write(newLine);
            beginningOfLine = true;
            pos = newLinePos+1;
            start = pos;
        }
        if (start < end) {
            writeLine(str, start, end-start);
        }
    }

    @Override
    public Writer append(CharSequence charSequence) throws IOException {
        write(charSequence.toString());
        return this;
    }

    @Override
    public Writer append(CharSequence charSequence, int start, int len) throws IOException {
        write(charSequence.subSequence(start, len).toString());
        return this;
    }

    @Override
    public Writer append(char c) throws IOException {
        write(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    public void indent(int indentAmount) {
        this.indentLevel += indentAmount;
        if (indentLevel < 0) {
            indentLevel = 0;
        }
    }

    public void deindent(int indentAmount) {
        this.indentLevel -= indentAmount;
        if (indentLevel < 0) {
            indentLevel = 0;
        }
    }
}
