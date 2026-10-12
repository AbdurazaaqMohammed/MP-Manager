/*
 * Copyright 2013, Google LLC
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

package com.android.tools.smali.util;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

public class CharSequenceUtils {
    /**
     * Compares two {@link CharSequence}s character by character, with exactly the semantics (and
     * therefore exactly the ordering) of {@code String.compareTo(String)}.
     *
     * <p>Ordering by {@code a.toString().compareTo(b.toString())} is the same as ordering by the
     * characters of {@code a} and {@code b} directly, because a {@link CharSequence}'s
     * {@code toString()} is by contract its character content. Comparing the sequences in place
     * removes a {@code toString()} (and its allocation, which for a type reference means running it
     * through {@code DexFormatter}) from every single comparison of every pool sort, which is one
     * of the hottest loops in {@code DexWriter}.
     */
    public static int compare(@Nonnull CharSequence left, @Nonnull CharSequence right) {
        if (left == right) {
            return 0;
        }
        if (left instanceof String && right instanceof String) {
            return ((String) left).compareTo((String) right);
        }
        int len1 = left.length();
        int len2 = right.length();
        int limit = Math.min(len1, len2);
        for (int i = 0; i < limit; i++) {
            char c1 = left.charAt(i);
            char c2 = right.charAt(i);
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return len1 - len2;
    }

    public static int listHashCode(List<? extends CharSequence> list) {
        // Same value as Lists.transform(list, TO_STRING).hashCode(): AbstractList.hashCode() over
        // the toString() of each element, without building the transformed list of Strings.
        int hashCode = 1;
        for (CharSequence element : list) {
            hashCode = hashCode * 31 + (element == null ? 0 : element.toString().hashCode());
        }
        return hashCode;
    }

    public static boolean listEquals(@Nonnull List<? extends CharSequence> list1,
                                     @Nonnull List<? extends CharSequence> list2) {
        if (list1 == list2) {
            return true;
        }
        int size = list1.size();
        if (size != list2.size()) {
            return false;
        }
        // Same result as comparing Lists.transform(list, TO_STRING), i.e. element-wise content
        // equality, but without materialising two lists of Strings first.
        Iterator<? extends CharSequence> it2 = list2.iterator();
        for (CharSequence element1 : list1) {
            if (!it2.hasNext() || compare(element1, it2.next()) != 0) {
                return false;
            }
        }
        return !it2.hasNext();
    }
}
