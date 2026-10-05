/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apksig.internal.asn1.ber;

import android.text.TextUtils;

import com.android.apksig.internal.asn1.Asn1Type;
import com.android.apksig.internal.asn1.Asn1TagClass;

/**
 * ASN.1 Basic Encoding Rules (BER) constants and helper methods. See {@code X.690}.
 */
public abstract class BerEncoding {
    private BerEncoding() {}

    /**
     * Constructed vs primitive flag in the first identifier byte.
     */
    public static final int ID_FLAG_CONSTRUCTED_ENCODING = 1 << 5;

    /**
     * Tag class: UNIVERSAL
     */
    public static final int TAG_CLASS_UNIVERSAL = 0;

    /**
     * Tag class: APPLICATION
     */
    public static final int TAG_CLASS_APPLICATION = 1;

    /**
     * Tag class: CONTEXT SPECIFIC
     */
    public static final int TAG_CLASS_CONTEXT_SPECIFIC = 2;

    /**
     * Tag class: PRIVATE
     */
    public static final int TAG_CLASS_PRIVATE = 3;

    /**
     * Tag number: BOOLEAN
     */
    public static final int TAG_NUMBER_BOOLEAN = 0x1;

    /**
     * Tag number: INTEGER
     */
    public static final int TAG_NUMBER_INTEGER = 0x2;

    /**
     * Tag number: BIT STRING
     */
    public static final int TAG_NUMBER_BIT_STRING = 0x3;

    /**
     * Tag number: OCTET STRING
     */
    public static final int TAG_NUMBER_OCTET_STRING = 0x4;

    /**
     * Tag number: NULL
     */
    public static final int TAG_NUMBER_NULL = 0x05;

    /**
     * Tag number: OBJECT IDENTIFIER
     */
    public static final int TAG_NUMBER_OBJECT_IDENTIFIER = 0x6;

    /**
     * Tag number: SEQUENCE
     */
    public static final int TAG_NUMBER_SEQUENCE = 0x10;

    /**
     * Tag number: SET
     */
    public static final int TAG_NUMBER_SET = 0x11;

    /**
     * Tag number: UTC_TIME
     */
    public final static int TAG_NUMBER_UTC_TIME = 0x17;

    /**
     * Tag number: GENERALIZED_TIME
     */
    public final static int TAG_NUMBER_GENERALIZED_TIME = 0x18;

    public static int getTagNumber(Asn1Type dataType) {
        return switch (dataType) {
            case INTEGER -> TAG_NUMBER_INTEGER;
            case OBJECT_IDENTIFIER -> TAG_NUMBER_OBJECT_IDENTIFIER;
            case OCTET_STRING -> TAG_NUMBER_OCTET_STRING;
            case BIT_STRING -> TAG_NUMBER_BIT_STRING;
            case SET_OF -> TAG_NUMBER_SET;
            case SEQUENCE, SEQUENCE_OF -> TAG_NUMBER_SEQUENCE;
            case UTC_TIME -> TAG_NUMBER_UTC_TIME;
            case GENERALIZED_TIME -> TAG_NUMBER_GENERALIZED_TIME;
            case BOOLEAN -> TAG_NUMBER_BOOLEAN;
            default -> throw new IllegalArgumentException("Unsupported data type: " + dataType);
        };
    }

    public static int getTagClass(Asn1TagClass tagClass) {
        return switch (tagClass) {
            case APPLICATION -> TAG_CLASS_APPLICATION;
            case CONTEXT_SPECIFIC -> TAG_CLASS_CONTEXT_SPECIFIC;
            case PRIVATE -> TAG_CLASS_PRIVATE;
            case UNIVERSAL -> TAG_CLASS_UNIVERSAL;
            default -> throw new IllegalArgumentException("Unsupported tag class: " + tagClass);
        };
    }

    public static String tagClassToString(int typeClass) {
        return switch (typeClass) {
            case TAG_CLASS_APPLICATION -> "APPLICATION";
            case TAG_CLASS_CONTEXT_SPECIFIC -> "";
            case TAG_CLASS_PRIVATE -> "PRIVATE";
            case TAG_CLASS_UNIVERSAL -> "UNIVERSAL";
            default -> throw new IllegalArgumentException("Unsupported type class: " + typeClass);
        };
    }

    public static String tagClassAndNumberToString(int tagClass, int tagNumber) {
        String classString = tagClassToString(tagClass);
        String numberString = tagNumberToString(tagNumber);
        return TextUtils.isEmpty(classString) ? numberString : classString + " " + numberString;
    }


    public static String tagNumberToString(int tagNumber) {
        return switch (tagNumber) {
            case TAG_NUMBER_INTEGER -> "INTEGER";
            case TAG_NUMBER_OCTET_STRING -> "OCTET STRING";
            case TAG_NUMBER_BIT_STRING -> "BIT STRING";
            case TAG_NUMBER_NULL -> "NULL";
            case TAG_NUMBER_OBJECT_IDENTIFIER -> "OBJECT IDENTIFIER";
            case TAG_NUMBER_SEQUENCE -> "SEQUENCE";
            case TAG_NUMBER_SET -> "SET";
            case TAG_NUMBER_BOOLEAN -> "BOOLEAN";
            case TAG_NUMBER_GENERALIZED_TIME -> "GENERALIZED TIME";
            case TAG_NUMBER_UTC_TIME -> "UTC TIME";
            default -> "0x" + Integer.toHexString(tagNumber);
        };
    }

    /**
     * Returns {@code true} if the provided first identifier byte indicates that the data value uses
     * constructed encoding for its contents, or {@code false} if the data value uses primitive
     * encoding for its contents.
     */
    public static boolean isConstructed(byte firstIdentifierByte) {
        return (firstIdentifierByte & ID_FLAG_CONSTRUCTED_ENCODING) != 0;
    }

    /**
     * Returns the tag class encoded in the provided first identifier byte. See {@code TAG_CLASS}
     * constants.
     */
    public static int getTagClass(byte firstIdentifierByte) {
        return (firstIdentifierByte & 0xff) >> 6;
    }

    public static byte setTagClass(byte firstIdentifierByte, int tagClass) {
        return (byte) ((firstIdentifierByte & 0x3f) | (tagClass << 6));
    }

    /**
     * Returns the tag number encoded in the provided first identifier byte. See {@code TAG_NUMBER}
     * constants.
     */
    public static int getTagNumber(byte firstIdentifierByte) {
        return firstIdentifierByte & 0x1f;
    }

    public static byte setTagNumber(byte firstIdentifierByte, int tagNumber) {
        return (byte) ((firstIdentifierByte & ~0x1f) | tagNumber);
    }
}
