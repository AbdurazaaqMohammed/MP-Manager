package io.github.abdurazaaqmohammed.features.files;

import java.io.File;

/**
 * One history slot per pane. Moved out of MainActivity so adapters and
 * controllers share it without depending on the activity.
 */
public record NavigationHistoryEntry(File file, boolean isZip, String zipPath) {
}
