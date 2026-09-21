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

package com.android.tools.smali.dexlib2.immutable.util;

import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;

import javax.annotation.Nonnull;
import java.util.Iterator;

public class ParamUtil {
    private static int findTypeEnd(@Nonnull String str, int index) {
        char c = str.charAt(index);
        return switch (c) {
            case 'Z', 'B', 'S', 'C', 'I', 'J', 'F', 'D' -> index + 1;
            case 'L' -> {
                while (str.charAt(index++) != ';') {
                }
                yield index;
            }
            case '[' -> {
                while (str.charAt(index++) != '[') {
                }
                yield findTypeEnd(str, index);
            }
            default ->
                    throw new IllegalArgumentException(String.format("Param string \"%s\" contains invalid type prefix: %s",
                            str, c));
        };
    }

    @Nonnull
    public static Iterable<ImmutableMethodParameter> parseParamString(@Nonnull final String params) {
        return new Iterable<>() {
            @Override
            public Iterator<ImmutableMethodParameter> iterator() {
                return new Iterator<>() {

                    private int index = 0;

                    @Override
                    public boolean hasNext() {
                        return index < params.length();
                    }

                    @Override
                    public ImmutableMethodParameter next() {
                        int end = findTypeEnd(params, index);
                        String ret = params.substring(index, end);
                        index = end;
                        return new ImmutableMethodParameter(ret, null, null);
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }
}
