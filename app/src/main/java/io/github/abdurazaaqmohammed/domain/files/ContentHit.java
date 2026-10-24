package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;

/**
 * One find-in-files or XML-search match.
 */
public record ContentHit(File file, int line, String snippet, String displayPath) {
    public ContentHit(File file, int line, String snippet) {
        this(file, line, snippet, file != null ? file.getName() : "");
    }
}

