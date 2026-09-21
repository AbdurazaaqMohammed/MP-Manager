/*
 * Copyright 2015, Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 * Neither the name of Google LLC nor the names of its
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

package com.android.tools.smali.dexlib2;

public class VersionMap {
    public static final int NO_VERSION = -1;

    public static int mapDexVersionToApi(int dexVersion) {
        return switch (dexVersion) {
            case 35 -> 23;
            case 37 -> 25;
            case 38 -> 27;
            case 39 -> 28;
            case 40 -> 34;
            case 41 -> 35;
            default -> NO_VERSION;
        };
    }

    public static int mapApiToDexVersion(int api) {
        if (api <= 23) {  // Android M/6
            return 35;
        }
        return switch (api) {  // Android N/7
            case 24, 25 ->  // Android N/7.1
                    37;  // Android O/8
            case 26, 27 ->  // Android O/8.1
                    38;
            case 28 ->  // Android P/9
                    39;  // Android Q/10
            // Android R/11
            // Android S/12
            // Android S/12.1
            // Android T/13
            case 29, 30, 31, 32, 33, 34 ->  // Android U/14
                    40;
            case 35 ->  // Android V/15
                    41;
            default -> NO_VERSION;
        };
    }

    public static int mapArtVersionToApi(int artVersion) {
        if (artVersion >= 170) {
            return 29;
        }
        if (artVersion >= 138) {
            return 28;
        }
        if (artVersion >= 131) {
            return 27;
        }
        if (artVersion >= 124) {
            return 26;
        }
        if (artVersion >= 79) {
            return 24;
        }
        if (artVersion >= 64) {
            return 23;
        }
        if (artVersion >= 45) {
            return 22;
        }
        if (artVersion >= 39) {
            return 21;
        }
        return 19;
    }

    public static int mapApiToArtVersion(int api) {
        if (api < 19) {
            return NO_VERSION;
        }

        return switch (api) {
            case 19, 20 -> 7;
            case 21 -> 39;
            case 22 -> 45;
            case 23 -> 64;
            case 24, 25 -> 79;
            case 26 -> 124;
            case 27 -> 131;
            case 28 -> 138;
            case 29 -> 170;
            default ->
                // 178 is the current version in the master branch of AOSP as of 2020-02-02
                    178;
        };
    }
}
