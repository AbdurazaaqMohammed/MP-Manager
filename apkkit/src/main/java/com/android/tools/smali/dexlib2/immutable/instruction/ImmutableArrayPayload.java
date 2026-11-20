/*
 * Copyright 2012, Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.tools.smali.dexlib2.immutable.instruction;

import static java.util.Collections.unmodifiableList;

import com.android.tools.smali.dexlib2.Format;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.util.Preconditions;

import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class ImmutableArrayPayload extends ImmutableInstruction implements ArrayPayload {
    public static final Opcode OPCODE = Opcode.ARRAY_PAYLOAD;

    protected final int elementWidth;
    @Nonnull protected final List<Number> arrayElements;

    public ImmutableArrayPayload(int elementWidth, @Nullable List<Number> arrayElements) {
        super(OPCODE);
        this.elementWidth = Preconditions.checkArrayPayloadElementWidth(elementWidth);
        this.arrayElements = Preconditions.checkArrayPayloadElements(elementWidth,
                arrayElements == null ? Collections.emptyList()
                    : unmodifiableList(new ArrayList<>(arrayElements)));
    }

    /**
     * Copy constructor for payloads with a non-standard element width. Such widths have no
     * typed element encoding and cannot be authored: they only arrive from an existing dex
     * (dex "confuse"/anti-tamper tools append junk payloads with odd widths to method bodies),
     * and the elements are the opaque little-endian value of each element's width bytes, which
     * {@code InstructionWriter} serializes back losslessly. The validation the public
     * constructor applies to authored payloads is deliberately not applied here — a width that
     * exists in the input must survive the rewrite — but the width still has to be encodable
     * at all.
     */
    private ImmutableArrayPayload(int elementWidth, @Nullable List<Number> arrayElements,
                                  boolean preservedFromInput) {
        super(OPCODE);
        if (elementWidth < 1 || elementWidth > 0xFFFF) {
            throw new IllegalArgumentException(
                    String.format("Not a valid element width: %d", elementWidth));
        }
        this.elementWidth = elementWidth;
        this.arrayElements = arrayElements == null ? Collections.emptyList()
                : unmodifiableList(new ArrayList<>(arrayElements));
    }

    @Nonnull
    public static ImmutableArrayPayload of(ArrayPayload instruction) {
        if (instruction instanceof ImmutableArrayPayload) {
            return (ImmutableArrayPayload)instruction;
        }
        int elementWidth = instruction.getElementWidth();
        if (elementWidth != 1 && elementWidth != 2 && elementWidth != 4 && elementWidth != 8) {
            // Preserve the payload of an existing dex losslessly instead of rejecting it; the
            // public constructor keeps rejecting non-standard widths for authored payloads.
            return new ImmutableArrayPayload(elementWidth, instruction.getArrayElements(), true);
        }
        return new ImmutableArrayPayload(elementWidth, instruction.getArrayElements());
    }

    @Override public int getElementWidth() { return elementWidth; }
    @Nonnull @Override public List<Number> getArrayElements() { return arrayElements; }

    @Override public int getCodeUnits() { return 4 + (elementWidth * arrayElements.size() + 1) / 2; }
    @Override public Format getFormat() { return OPCODE.format; }
}
