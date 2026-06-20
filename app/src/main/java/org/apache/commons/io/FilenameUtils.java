/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.io;

import java.io.File;

/**
 * General file name and file path manipulation utilities. The methods in this class
 * operate on strings that represent relative or absolute paths. Nothing in this class
 * ever accesses the file system, or depends on whether a path points to a file that exists.
 * <p>
 * When dealing with file names, you can hit problems when moving from a Windows
 * based development machine to a Unix based production machine.
 * This class aims to help avoid those problems.
 * </p>
 * <p>
 * <strong>NOTE</strong>: You may be able to avoid using this class entirely simply by
 * using JDK {@link File File} objects and the two argument constructor
 * {@link File#File(java.io.File, String) File(File,String)}.
 * </p>
 * <p>
 * Most methods in this class are designed to work the same on both Unix and Windows.
 * Those that don't include 'System', 'Unix', or 'Windows' in their name.
 * </p>
 * <p>
 * Most methods recognize both separators (forward and backslashes), and both
 * sets of prefixes. See the Javadoc of each method for details.
 * </p>
 * <p>
 * This class defines six components within a path (sometimes called a file name or a full file name).
 * Given an absolute Windows path such as C:\dev\project\file.txt they are:
 * </p>
 * <ul>
 * <li>the full file name, or just file name - C:\dev\project\file.txt</li>
 * <li>the prefix - C:\</li>
 * <li>the path - dev\project\</li>
 * <li>the full path - C:\dev\project\</li>
 * <li>the name - file.txt</li>
 * <li>the base name - file</li>
 * <li>the extension - txt</li>
 * </ul>
 * <p>
 * Given an absolute Unix path such as /dev/project/file.txt they are:
 * </p>
 * <ul>
 * <li>the full file name, or just file name - /dev/project/file.txt</li>
 * <li>the prefix - /</li>
 * <li>the path - dev/project</li>
 * <li>the full path - /dev/project</li>
 * <li>the name - file.txt</li>
 * <li>the base name - file</li>
 * <li>the extension - txt</li>
 * </ul>
 * <p>
 * Given a relative Windows path such as dev\project\file.txt they are:
 * </p>
 * <ul>
 * <li>the full file name, or just file name - dev\project\file.txt</li>
 * <li>the prefix - null</li>
 * <li>the path - dev\project\</li>
 * <li>the full path - dev\project\</li>
 * <li>the name - file.txt</li>
 * <li>the base name - file</li>
 * <li>the extension - txt</li>
 * </ul>
 * <p>
 * Given an absolute Unix path such as /dev/project/file.txt they are:
 * </p>
 * <ul>
 * <li>the full path, full file name, or just file name - /dev/project/file.txt</li>
 * <li>the prefix - /</li>
 * <li>the path - dev/project</li>
 * <li>the full path - /dev/project</li>
 * <li>the name - file.txt</li>
 * <li>the base name - file</li>
 * <li>the extension - txt</li>
 * </ul>
 *
 *
 * <p>
 * This class works best if directory names end with a separator.
 * If you omit the last separator, it is impossible to determine if the last component
 * corresponds to a file or a directory. This class treats final components
 * that do not end with a separator as files, not directories.
 * </p>
 * <p>
 * This class only supports Unix and Windows style names.
 * Prefixes are matched as follows:
 * </p>
 * <pre>
 * Windows:
 * a\b\c.txt           --&gt; ""          --&gt; relative
 * \a\b\c.txt          --&gt; "\"         --&gt; current drive absolute
 * C:a\b\c.txt         --&gt; "C:"        --&gt; drive relative
 * C:\a\b\c.txt        --&gt; "C:\"       --&gt; absolute
 * \\server\a\b\c.txt  --&gt; "\\server\" --&gt; UNC
 *
 * Unix:
 * a/b/c.txt           --&gt; ""          --&gt; relative
 * /a/b/c.txt          --&gt; "/"         --&gt; absolute
 * ~/a/b/c.txt         --&gt; "~/"        --&gt; current user
 * ~                   --&gt; "~/"        --&gt; current user (slash added)
 * ~user/a/b/c.txt     --&gt; "~user/"    --&gt; named user
 * ~user               --&gt; "~user/"    --&gt; named user (slash added)
 * </pre>
 * <p>
 * Both prefix styles are matched, irrespective of the machine that you are
 * currently running on.
 * </p>
 *
 * @since 1.1
 */
public class FilenameUtils {
    /**
     * Gets the extension of a fileName.
     * <p>
     * This method returns the textual part of the file name after the last dot.
     * There must be no directory separator after the dot.
     * </p>
     * <pre>
     * foo.txt      --&gt; "txt"
     * a/b/c.jpg    --&gt; "jpg"
     * a/b.txt/c    --&gt; ""
     * a/b/c        --&gt; ""
     * </pre>
     * <p>
     * The output will be the same irrespective of the machine that the code is running on, with the
     * exception of a possible {@link IllegalArgumentException} on Windows (see below).
     * </p>
     * <p>
     * <strong>Note:</strong> This method used to have a hidden problem for names like "foo.exe:bar.txt".
     * In this case, the name wouldn't be the name of a file, but the identifier of an
     * alternate data stream (bar.txt) on the file foo.exe. The method used to return
     * ".txt" here, which would be misleading. Commons IO 2.7 and later throw
     * an {@link IllegalArgumentException} for names like this.
     * </p>
     *
     * @param fileName the file name to retrieve the extension of.
     * @return the extension of the file or an empty string if none exists or {@code null}
     * if the file name is {@code null}.
     * @throws IllegalArgumentException <strong>Windows only:</strong> the file name parameter is, in fact,
     * the identifier of an Alternate Data Stream, for example "foo.exe:bar.txt".
     */
    public static String getExtension(final String fileName) throws IllegalArgumentException {
        if (fileName == null) {
            return null;
        }
        final int index = indexOfExtension(fileName);
        if (index == NOT_FOUND) {
            return "";
        }
        return fileName.substring(index + 1);
    }

    /**
     * Returns the index of the last extension separator character, which is a dot.
     * <p>
     * This method also checks that there is no directory separator after the last dot. To do this it uses
     * {@link #indexOfLastSeparator(String)} which will handle a file in either Unix or Windows format.
     * </p>
     * <p>
     * The output will be the same irrespective of the machine that the code is running on, with the
     * exception of a possible {@link IllegalArgumentException} on Windows (see below).
     * </p>
     * <strong>Note:</strong> This method used to have a hidden problem for names like "foo.exe:bar.txt".
     * In this case, the name wouldn't be the name of a file, but the identifier of an
     * alternate data stream (bar.txt) on the file foo.exe. The method used to return
     * ".txt" here, which would be misleading. Commons IO 2.7, and later versions, are throwing
     * an {@link IllegalArgumentException} for names like this.
     *
     * @param fileName
     *            the file name to find the last extension separator in, null returns -1
     * @return the index of the last extension separator character, or -1 if there is no such character
     * @throws IllegalArgumentException <strong>Windows only:</strong> the file name parameter is, in fact,
     * the identifier of an Alternate Data Stream, for example "foo.exe:bar.txt".
     */
    public static int indexOfExtension(final String fileName) throws IllegalArgumentException {
        if (fileName == null) {
            return NOT_FOUND;
        }
//        if (isSystemWindows()) {
//            // Special handling for NTFS ADS: Don't accept colon in the file name.
//            final int offset = fileName.indexOf(':', getAdsCriticalOffset(fileName));
//            if (offset != -1) {
//                throw new IllegalArgumentException("NTFS ADS separator (':') in file name is forbidden.");
//            }
//        }
        final int extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        final int lastSeparator = indexOfLastSeparator(fileName);
        return lastSeparator > extensionPos ? NOT_FOUND : extensionPos;
    }
    public static final char EXTENSION_SEPARATOR = '.';

    public static int indexOfLastSeparator(final String fileName) {
        if (fileName == null) {
            return NOT_FOUND;
        }
        final int lastUnixPos = fileName.lastIndexOf(UNIX_NAME_SEPARATOR);
        final int lastWindowsPos = fileName.lastIndexOf(WINDOWS_NAME_SEPARATOR);
        return Math.max(lastUnixPos, lastWindowsPos);
    }
    private static final int NOT_FOUND = -1;
    /**
     * The Unix separator character.
     */
    private static final char UNIX_NAME_SEPARATOR = '/';

    /**
     * The Windows separator character.
     */
    private static final char WINDOWS_NAME_SEPARATOR = '\\';
}