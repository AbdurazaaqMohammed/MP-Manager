package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;

/**
 * One find-in-files match. Moved out of MainActivity.
 */
public record ContentHit(File file, int line, String snippet) {
}
