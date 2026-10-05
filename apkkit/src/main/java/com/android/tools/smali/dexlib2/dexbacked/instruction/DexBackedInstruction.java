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

package com.android.tools.smali.dexlib2.dexbacked.instruction;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBuffer;
import com.android.tools.smali.util.ExceptionWithContext;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexReader;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class DexBackedInstruction implements Instruction {
    @Nonnull public final DexBackedDexFile dexFile;
    @Nonnull public final Opcode opcode;
    public final int instructionStart;

    public DexBackedInstruction(@Nonnull DexBackedDexFile dexFile,
                                @Nonnull Opcode opcode,
                                int instructionStart) {
        this.dexFile = dexFile;
        this.opcode = opcode;
        this.instructionStart = instructionStart;
    }

    @Nonnull public Opcode getOpcode() { return opcode; }
    @Override public int getCodeUnits() { return opcode.format.size / 2; }

    @Nonnull
    public static Instruction readFrom(DexBackedDexFile dexFile, @Nonnull DexReader<? extends DexBuffer> reader) {
        int opcodeValue = reader.peekUbyte();

        if (opcodeValue == 0) {
            opcodeValue = reader.peekUshort();
        }

        Opcode opcode = dexFile.getOpcodes().getOpcodeByValue(opcodeValue);

        Instruction instruction = buildInstruction(dexFile, opcode,
                reader.getOffset() + reader.dexBuf.getBaseOffset() -
                        dexFile.getBuffer().getBaseOffset() - dexFile.getBaseDataOffset());
        reader.moveRelative(instruction.getCodeUnits()*2);
        return instruction;
    }
    
    private static DexBackedInstruction buildInstruction(@Nonnull DexBackedDexFile dexFile, @Nullable Opcode opcode,
                                                         int instructionStartOffset) {
        if (opcode == null) {
            return new DexBackedUnknownInstruction(dexFile, instructionStartOffset);
        }
        return switch (opcode.format) {
            case Format10t -> new DexBackedInstruction10t(dexFile, opcode, instructionStartOffset);
            case Format10x -> new DexBackedInstruction10x(dexFile, opcode, instructionStartOffset);
            case Format11n -> new DexBackedInstruction11n(dexFile, opcode, instructionStartOffset);
            case Format11x -> new DexBackedInstruction11x(dexFile, opcode, instructionStartOffset);
            case Format12x -> new DexBackedInstruction12x(dexFile, opcode, instructionStartOffset);
            case Format20bc ->
                    new DexBackedInstruction20bc(dexFile, opcode, instructionStartOffset);
            case Format20t -> new DexBackedInstruction20t(dexFile, opcode, instructionStartOffset);
            case Format21c -> new DexBackedInstruction21c(dexFile, opcode, instructionStartOffset);
            case Format21ih ->
                    new DexBackedInstruction21ih(dexFile, opcode, instructionStartOffset);
            case Format21lh ->
                    new DexBackedInstruction21lh(dexFile, opcode, instructionStartOffset);
            case Format21s -> new DexBackedInstruction21s(dexFile, opcode, instructionStartOffset);
            case Format21t -> new DexBackedInstruction21t(dexFile, opcode, instructionStartOffset);
            case Format22b -> new DexBackedInstruction22b(dexFile, opcode, instructionStartOffset);
            case Format22c -> new DexBackedInstruction22c(dexFile, opcode, instructionStartOffset);
            case Format22cs ->
                    new DexBackedInstruction22cs(dexFile, opcode, instructionStartOffset);
            case Format22s -> new DexBackedInstruction22s(dexFile, opcode, instructionStartOffset);
            case Format22t -> new DexBackedInstruction22t(dexFile, opcode, instructionStartOffset);
            case Format22x -> new DexBackedInstruction22x(dexFile, opcode, instructionStartOffset);
            case Format23x -> new DexBackedInstruction23x(dexFile, opcode, instructionStartOffset);
            case Format30t -> new DexBackedInstruction30t(dexFile, opcode, instructionStartOffset);
            case Format31c -> new DexBackedInstruction31c(dexFile, opcode, instructionStartOffset);
            case Format31i -> new DexBackedInstruction31i(dexFile, opcode, instructionStartOffset);
            case Format31t -> new DexBackedInstruction31t(dexFile, opcode, instructionStartOffset);
            case Format32x -> new DexBackedInstruction32x(dexFile, opcode, instructionStartOffset);
            case Format35c -> new DexBackedInstruction35c(dexFile, opcode, instructionStartOffset);
            case Format35ms ->
                    new DexBackedInstruction35ms(dexFile, opcode, instructionStartOffset);
            case Format35mi ->
                    new DexBackedInstruction35mi(dexFile, opcode, instructionStartOffset);
            case Format3rc -> new DexBackedInstruction3rc(dexFile, opcode, instructionStartOffset);
            case Format3rmi ->
                    new DexBackedInstruction3rmi(dexFile, opcode, instructionStartOffset);
            case Format3rms ->
                    new DexBackedInstruction3rms(dexFile, opcode, instructionStartOffset);
            case Format45cc ->
                    new DexBackedInstruction45cc(dexFile, opcode, instructionStartOffset);
            case Format4rcc ->
                    new DexBackedInstruction4rcc(dexFile, opcode, instructionStartOffset);
            case Format51l -> new DexBackedInstruction51l(dexFile, opcode, instructionStartOffset);
            case PackedSwitchPayload ->
                    new DexBackedPackedSwitchPayload(dexFile, instructionStartOffset);
            case SparseSwitchPayload ->
                    new DexBackedSparseSwitchPayload(dexFile, instructionStartOffset);
            case ArrayPayload -> new DexBackedArrayPayload(dexFile, instructionStartOffset);
            default ->
                    throw new ExceptionWithContext("Unexpected opcode format: %s", opcode.format.toString());
        };
    }
}
