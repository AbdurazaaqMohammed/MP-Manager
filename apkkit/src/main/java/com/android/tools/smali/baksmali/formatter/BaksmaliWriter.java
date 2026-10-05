/*
 * Copyright 2021, Google LLC
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

package com.android.tools.smali.baksmali.formatter;

import com.android.tools.smali.dexlib2.MethodHandleType;
import com.android.tools.smali.dexlib2.ValueType;
import com.android.tools.smali.dexlib2.formatter.DexFormattedWriter;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.reference.CallSiteReference;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodHandleReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.value.AnnotationEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.BooleanEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ByteEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.CharEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.DoubleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EnumEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FieldEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FloatEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.LongEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodHandleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodTypeEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ShortEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.TypeEncodedValue;
import com.android.tools.smali.util.IndentingWriter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;


/**
 * A specialized version of DexFormattedWriter that handles quoting
 * <a href="https://source.android.com/devices/tech/dalvik/dex-format#simplename">simple names</a> containing spaces.
 */
public class BaksmaliWriter extends DexFormattedWriter {

    @Nullable private final String classContext;

    protected final char[] buffer = new char[24];

    /**
     * Lookup table for the ASCII characters that can never have a character type of
     * {@link Character#SPACE_SEPARATOR}: the ASCII letters and digits, plus the handful of
     * punctuation characters that actually occur inside a dex simple name or type descriptor
     * ({@code _ / ; $ < > [ ] - . : ( )}).
     *
     * <p>{@link Character#getType(char)} is comparatively slow, and virtually every character of
     * every name in a real dex file is covered by this table, so the slow path is essentially
     * never taken.
     */
    private static final boolean[] NOT_SPACE_SEPARATOR_ASCII = new boolean[128];

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            NOT_SPACE_SEPARATOR_ASCII[c] = true;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            NOT_SPACE_SEPARATOR_ASCII[c] = true;
        }
        for (char c = '0'; c <= '9'; c++) {
            NOT_SPACE_SEPARATOR_ASCII[c] = true;
        }
        for (char c : new char[]{'_', '/', ';', '$', '<', '>', '[', ']', '-', '.', ':', '(', ')'}) {
            NOT_SPACE_SEPARATOR_ASCII[c] = true;
        }

        // Never trust the hand picked list above. If the JDK's Unicode tables ever report one of
        // these characters as a space separator, drop it from the table so that the slow path is
        // used and the result stays exactly the same as
        // Character.getType(c) == Character.SPACE_SEPARATOR.
        for (int i = 0; i < NOT_SPACE_SEPARATOR_ASCII.length; i++) {
            if (NOT_SPACE_SEPARATOR_ASCII[i] && Character.getType((char) i) == Character.SPACE_SEPARATOR) {
                NOT_SPACE_SEPARATOR_ASCII[i] = false;
            }
        }
    }

    /**
     * Exactly equivalent to {@code Character.getType(c) == Character.SPACE_SEPARATOR}, but avoids
     * the relatively expensive {@link Character#getType(char)} call for characters that cannot
     * possibly be a space separator. Non-ASCII characters always take the {@link
     * Character#getType(char)} path so the result stays correct for arbitrary Unicode names.
     */
    private static boolean isSpaceSeparator(char c) {
        if (c < 128 && NOT_SPACE_SEPARATOR_ASCII[c]) {
            return false;
        }
        return Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    public BaksmaliWriter(Writer writer) {
        this(writer, null);
    }

    /**
     * Constructs a new BaksmaliWriter
     *
     * @param writer The {@link IndentingWriter} to write to
     * @param classContext If provided, the class will be elided from any field/method descriptors whose containing
     *                     class match this instance's classContext.
     */
    public BaksmaliWriter(Writer writer, @Nullable String classContext) {
        super(writer instanceof IndentingWriter ? writer : new IndentingWriter(writer));
        this.classContext = classContext;
    }

    @Override public void writeMethodDescriptor(MethodReference methodReference) throws IOException {
        if (methodReference.getDefiningClass().equals(classContext)) {
            writeShortMethodDescriptor(methodReference);
        } else {
            super.writeMethodDescriptor(methodReference);
        }
    }

    @Override public void writeFieldDescriptor(FieldReference fieldReference) throws IOException {
        if (fieldReference.getDefiningClass().equals(classContext)) {
            writeShortFieldDescriptor(fieldReference);
        } else {
            super.writeFieldDescriptor(fieldReference);
        }
    }

    @Override
    protected void writeClass(CharSequence type) throws IOException {
        assert type.charAt(0) == 'L';

        writer.write(type.charAt(0));

        int startIndex = 1;
        boolean hasSpace = false;
        int i;
        for (i = startIndex; i < type.length(); i++) {
            char c = type.charAt(i);

            // '/' and ';' are never space separators, so checking them before the space separator
            // test is equivalent to (and faster than) testing for a space separator first.
            if (c == '/') {
                if (i == startIndex) {
                    throw new IllegalArgumentException(
                            String.format("Invalid type string: %s", type));
                }

                writeSimpleName(type.subSequence(startIndex, i), hasSpace);
                writer.write(type.charAt(i));
                hasSpace = false;
                startIndex = i+1;
            } else if (c == ';') {
                if (i == startIndex) {
                    throw new IllegalArgumentException(
                            String.format("Invalid type string: %s", type));
                }

                writeSimpleName(type.subSequence(startIndex, i), hasSpace);
                writer.write(type.charAt(i));
                break;
            } else if (isSpaceSeparator(c)) {
                hasSpace = true;
            }
        }

        if (i != type.length() - 1 || type.charAt(i) != ';') {
            throw new IllegalArgumentException(
                    String.format("Invalid type string: %s", type));
        }
    }

    @Override
    public void writeSimpleName(CharSequence simpleName) throws IOException {
        boolean hasSpace = false;
        for (int i = 0; i < simpleName.length(); i++) {
            if (isSpaceSeparator(simpleName.charAt(i))) {
                hasSpace = true;
                break;
            }
        }
        writeSimpleName(simpleName, hasSpace);
    }

    /**
     * Writes the given simple name, potentially quoting it if requested.
     *
     * <p>The simple name will be quoted with backticks if quoted is true
     *
     * <p>A simple name should typically be quoted if it is meant to be human readable, and it contains spaces.
     *
     * @param simpleName The simple name to write. See: <a href="https://source.android.com/devices/tech/dalvik/dex-format#simplename">https://source.android.com/devices/tech/dalvik/dex-format#simplename</a>
     */
    public void writeSimpleName(CharSequence simpleName, boolean quoted) throws IOException {
        if (quoted) {
            writer.write('`');
        }
        writer.append(simpleName);
        if (quoted) {
            writer.write('`');
        }
    }

    public void writeEncodedValue(EncodedValue encodedValue) throws IOException {
        switch (encodedValue.getValueType()) {
            case ValueType.BOOLEAN:
                writeBooleanEncodedValue((BooleanEncodedValue) encodedValue);
                break;
            case ValueType.BYTE:
                writeIntegralValue(((ByteEncodedValue) encodedValue).getValue(), 't');
                break;
            case ValueType.CHAR:
                writeCharEncodedValue((CharEncodedValue) encodedValue);
                break;
            case ValueType.SHORT:
                writeIntegralValue(((ShortEncodedValue) encodedValue).getValue(), 's');
                break;
            case ValueType.INT:
                writeIntegralValue(((IntEncodedValue) encodedValue).getValue(), null);
                break;
            case ValueType.LONG:
                writeIntegralValue(((LongEncodedValue)encodedValue).getValue(), 'L');
                break;
            case ValueType.FLOAT:
                writeFloatEncodedValue((FloatEncodedValue) encodedValue);
                break;
            case ValueType.DOUBLE:
                writeDoubleEncodedValue((DoubleEncodedValue) encodedValue);
                break;
            case ValueType.ANNOTATION:
                writeAnnotation((AnnotationEncodedValue)encodedValue);
                break;
            case ValueType.ARRAY:
                writeArray((ArrayEncodedValue)encodedValue);
                break;
            case ValueType.STRING:
                writeQuotedString(((StringEncodedValue)encodedValue).getValue());
                break;
            case ValueType.FIELD:
                writeFieldDescriptor(((FieldEncodedValue)encodedValue).getValue());
                break;
            case ValueType.ENUM:
                writeEnum((EnumEncodedValue) encodedValue);
                break;
            case ValueType.METHOD:
                writeMethodDescriptor(((MethodEncodedValue)encodedValue).getValue());
                break;
            case ValueType.TYPE:
                writeType(((TypeEncodedValue)encodedValue).getValue());
                break;
            case ValueType.METHOD_TYPE:
                writeMethodProtoDescriptor(((MethodTypeEncodedValue)encodedValue).getValue());
                break;
            case ValueType.METHOD_HANDLE:
                writeMethodHandle(((MethodHandleEncodedValue)encodedValue).getValue());
                break;
            case ValueType.NULL:
                writer.write("null");
                break;
            default:
                throw new IllegalArgumentException("Unknown encoded value type");
        }
    }

    protected void writeBooleanEncodedValue(BooleanEncodedValue encodedValue) throws IOException {
        writer.write(Boolean.toString(encodedValue.getValue()));
    }

    protected void writeIntegralValue(long value, @Nullable Character suffix) throws IOException {
        if (value < 0) {
            writer.write("-0x");
            writeUnsignedLongAsHex(-value);
        } else {
            writer.write("0x");
            writeUnsignedLongAsHex(value);
        }
        if (suffix != null) {
            writer.write(suffix);
        }
    }

    protected void writeCharEncodedValue(CharEncodedValue encodedValue) throws IOException {


        char c = encodedValue.getValue();
        if ((c >= ' ') && (c < 0x7f)) {
            writer.write('\'');
            if ((c == '\'') || (c == '\"') || (c == '\\')) {
                writer.write('\\');
            }
            writer.write(c);
            writer.write('\'');
            return;
        } else if (c <= 0x7f) {
            switch (c) {
                case '\n':
                    writer.write("'\\n'");
                    return;
                case '\r':
                    writer.write("'\\r'");
                    return;
                case '\t':
                    writer.write("'\\t'");
                    return;
            }
        }

        writer.write('\'');
        writer.write("\\u");
        writer.write(Character.forDigit(c >> 12, 16));
        writer.write(Character.forDigit((c >> 8) & 0x0f, 16));
        writer.write(Character.forDigit((c >> 4) & 0x0f, 16));
        writer.write(Character.forDigit(c & 0x0f, 16));
        writer.write('\'');
    }

    protected void writeFloatEncodedValue(FloatEncodedValue encodedValue) throws IOException {
        writer.write(Float.toString(encodedValue.getValue()));
        writer.write('f');
    }

    protected void writeDoubleEncodedValue(DoubleEncodedValue encodedValue) throws IOException {
        writer.write(Double.toString(encodedValue.getValue()));
    }

    protected void writeEnum(EnumEncodedValue encodedValue) throws IOException {
        writer.write(".enum ");
        writeFieldDescriptor(encodedValue.getValue());
    }

    /**
     * Write the given {@link AnnotationEncodedValue}.
     */
    protected void writeAnnotation(AnnotationEncodedValue annotation) throws IOException {
        writer.write(".subannotation ");
        writeType(annotation.getType());
        writer.write('\n');

        writeAnnotationElements(annotation.getElements());

        writer.write(".end subannotation");
    }

    public void writeAnnotationElements(
            @Nonnull Collection<? extends AnnotationElement> annotationElements) throws IOException {
        indent(4);
        for (AnnotationElement annotationElement: annotationElements) {
            writeSimpleName(annotationElement.getName());
            writer.write(" = ");
            writeEncodedValue(annotationElement.getValue());
            writer.write('\n');
        }
        deindent(4);
    }

    /**
     * Write the given {@link ArrayEncodedValue}.
     */
    protected void writeArray(ArrayEncodedValue array) throws IOException {
        writer.write('{');
        Collection<? extends EncodedValue> values = array.getValue();
        if (values.size() == 0) {
            writer.write('}');
            return;
        }

        writer.write('\n');
        indent(4);
        boolean first = true;
        for (EncodedValue encodedValue: values) {
            if (!first) {
                writer.write(",\n");
            }
            first = false;

            writeEncodedValue(encodedValue);
        }
        deindent(4);
        writer.write("\n}");
    }

    @Override public void writeCallSite(CallSiteReference callSiteReference) throws IOException {
        writeSimpleName(callSiteReference.getName());
        writer.write('(');
        writeQuotedString(callSiteReference.getMethodName());
        writer.write(", ");
        writeMethodProtoDescriptor(callSiteReference.getMethodProto());

        for (EncodedValue encodedValue : callSiteReference.getExtraArguments()) {
            writer.write(", ");
            writeEncodedValue(encodedValue);
        }

        writer.write(")@");
        MethodHandleReference methodHandle = callSiteReference.getMethodHandle();
        if (methodHandle.getMethodHandleType() != MethodHandleType.INVOKE_STATIC) {
            throw new IllegalArgumentException("The linker method handle for a call site must be of type invoke-static");
        }
        writeMethodDescriptor((MethodReference) callSiteReference.getMethodHandle().getMemberReference());
    }

    public IndentingWriter indentingWriter() {
        return (IndentingWriter) writer;
    }

    public void writeUnsignedLongAsHex(long value) throws IOException {
        int bufferIndex = 23;
        do {
            int digit = (int)(value & 15);
            if (digit < 10) {
                buffer[bufferIndex--] = (char)(digit + '0');
            } else {
                buffer[bufferIndex--] = (char)((digit - 10) + 'a');
            }

            value >>>= 4;
        } while (value != 0);

        bufferIndex++;

        write(buffer, bufferIndex, 24-bufferIndex);
    }

    public void writeSignedLongAsDec(long value) throws IOException {
        int bufferIndex = 23;

        if (value < 0) {
            write('-');
        }

        // Two digits are emitted per iteration. The digits are extracted from the *signed* value
        // instead of from its magnitude, which keeps Long.MIN_VALUE (whose magnitude does not fit
        // in a long) working without a special case: for a negative v, v/10 truncates towards
        // zero and (v/10)*10 can never overflow, so v - (v/10)*10 is the exact remainder with the
        // sign of v. Negating it when it is negative yields exactly the digit that the previous
        // Math.abs(v % 10) implementation produced, for every input.
        long remaining = value;
        while (remaining <= -100 || remaining >= 100) {
            long quotient = remaining / 10;
            int digit = (int)(remaining - quotient * 10);
            long highQuotient = quotient / 10;
            int highDigit = (int)(quotient - highQuotient * 10);
            if (digit < 0) {
                digit = -digit;
            }
            if (highDigit < 0) {
                highDigit = -highDigit;
            }

            buffer[bufferIndex--] = (char)(digit + '0');
            buffer[bufferIndex--] = (char)(highDigit + '0');

            remaining = highQuotient;
        }

        // remaining is now in [-99, 99] and holds one or two digits.
        if (remaining <= -10) {
            long quotient = remaining / 10;
            buffer[bufferIndex--] = (char)(-(remaining - quotient * 10) + '0');
            buffer[bufferIndex--] = (char)(-quotient + '0');
        } else if (remaining < 0) {
            buffer[bufferIndex--] = (char)(-remaining + '0');
        } else if (remaining >= 10) {
            buffer[bufferIndex--] = (char)(remaining % 10 + '0');
            buffer[bufferIndex--] = (char)(remaining / 10 + '0');
        } else {
            buffer[bufferIndex--] = (char)(remaining + '0');
        }

        bufferIndex++;

        write(buffer, bufferIndex, 24-bufferIndex);
    }

    public void writeSignedIntAsDec(int value) throws IOException {
        int bufferIndex = 15;

        if (value < 0) {
            write('-');
        }

        // See writeSignedLongAsDec for why Integer.MIN_VALUE needs no special case here. The
        // arithmetic is kept in 32 bits because this is the int overload.
        int remaining = value;
        while (remaining <= -100 || remaining >= 100) {
            int quotient = remaining / 10;
            int digit = remaining - quotient * 10;
            int highQuotient = quotient / 10;
            int highDigit = quotient - highQuotient * 10;
            if (digit < 0) {
                digit = -digit;
            }
            if (highDigit < 0) {
                highDigit = -highDigit;
            }

            buffer[bufferIndex--] = (char)(digit + '0');
            buffer[bufferIndex--] = (char)(highDigit + '0');

            remaining = highQuotient;
        }

        // remaining is now in [-99, 99] and holds one or two digits.
        if (remaining <= -10) {
            int quotient = remaining / 10;
            buffer[bufferIndex--] = (char)(-(remaining - quotient * 10) + '0');
            buffer[bufferIndex--] = (char)(-quotient + '0');
        } else if (remaining < 0) {
            buffer[bufferIndex--] = (char)(-remaining + '0');
        } else if (remaining >= 10) {
            buffer[bufferIndex--] = (char)(remaining % 10 + '0');
            buffer[bufferIndex--] = (char)(remaining / 10 + '0');
        } else {
            buffer[bufferIndex--] = (char)(remaining + '0');
        }

        bufferIndex++;

        write(buffer, bufferIndex, 16-bufferIndex);
    }

    public void writeUnsignedIntAsDec(int value) throws IOException {
        if (value < 0) {
            writeSignedLongAsDec(value & 0xFFFFFFFFL);
        } else {
            writeSignedIntAsDec(value);
        }
    }

    public void writeSignedIntOrLongTo(long val) throws IOException {
        if (val<0) {
            writer.write("-0x");
            writeUnsignedLongAsHex(-val);
            if (val < Integer.MIN_VALUE) {
                writer.write('L');
            }
        } else {
            writer.write("0x");
            writeUnsignedLongAsHex(val);
            if (val > Integer.MAX_VALUE) {
                writer.write('L');
            }
        }
    }

    public void indent(int indentAmount) {
        ((IndentingWriter) writer).indent(indentAmount);
    }

    public void deindent(int indentAmount) {
        ((IndentingWriter) writer).deindent(indentAmount);
    }
}
