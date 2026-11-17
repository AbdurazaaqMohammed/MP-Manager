package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/** Per-run staging and non-destructive output publication for both patchers. */
public final class PatcherFiles {
    private PatcherFiles() {}

    public static File outputDirectory(Context context, File source) throws IOException {
        File parent = source.getParentFile();
        if (parent != null && parent.isDirectory() && parent.canWrite()
                && !source.getCanonicalPath().startsWith(context.getFilesDir().getCanonicalPath() + File.separator)
                && !source.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath() + File.separator)) return parent;
        File downloads = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "MP Manager");
        if (!downloads.isDirectory() && !downloads.mkdirs()) {
            throw new IOException("Cannot create output folder: " + downloads);
        }
        return downloads;
    }

    public static File publish(File source, File requested) throws IOException {
        File staged = File.createTempFile(".mp-patcher-", ".tmp", requested.getParentFile());
        File destination = null;
        try {
            Files.copy(source.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING);
            destination = reserve(requested);
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException error) {
            if (destination != null) destination.delete();
            throw error;
        } finally { staged.delete(); }
    }

    public static synchronized File reserve(File requested) throws IOException {
        String name = requested.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        File candidate = requested;
        for (int index = 2; !candidate.createNewFile(); index++) {
            candidate = new File(requested.getParentFile(), base + "_" + index + extension);
        }
        return candidate;
    }

    public static synchronized File reserveDirectory(File requested) throws IOException {
        File candidate = requested;
        for (int index = 2; !candidate.mkdir(); index++) {
            if (!candidate.exists()) throw new IOException("Cannot create output folder: " + candidate);
            candidate = new File(requested.getParentFile(), requested.getName() + "_" + index);
        }
        return candidate;
    }

    public static final class Work implements AutoCloseable {
        public final File directory;

        public Work(Context context, String prefix) throws IOException {
            directory = Files.createTempDirectory(context.getCacheDir().toPath(), prefix).toFile();
        }

        @Override public void close() throws IOException {
            try (var paths = Files.walk(directory.toPath())) {
                for (var path : paths.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) Files.deleteIfExists(path);
            }
        }
    }
}
