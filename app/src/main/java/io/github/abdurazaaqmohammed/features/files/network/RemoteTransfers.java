package io.github.abdurazaaqmohammed.features.files.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Recursive transfers with fail-on-conflict and delete-source-only-after-success semantics. */
public final class RemoteTransfers {
    private RemoteTransfers() {}

    public static void download(RemoteClient source, RemoteEntry entry, File destination) throws IOException {
        if (destination.exists()) throw new IOException("Destination already exists: " + destination.getName());
        if (entry.directory) {
            if (!destination.mkdir()) throw new IOException("Cannot create destination folder");
            for (RemoteEntry child : source.list(entry.path)) {
                requireChild(entry.path, child);
                download(source, child, new File(destination, child.name()));
            }
        } else {
            File temporary = File.createTempFile(".mp-download-", ".part", destination.getParentFile());
            try {
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    source.download(entry.path, output);
                    output.getFD().sync();
                }
                if (entry.size > 0 && temporary.length() != entry.size)
                    throw new IOException("Downloaded size does not match; source may have changed");
                java.nio.file.Files.move(temporary.toPath(), destination.toPath());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
            if (entry.modified > 0) destination.setLastModified(entry.modified);
        }
    }

    public static void upload(RemoteClient destination, String parent, File source) throws IOException {
        if (java.nio.file.Files.isSymbolicLink(source.toPath())) throw new IOException("Symbolic links cannot be uploaded");
        String path = RemotePath.child(parent, source.getName());
        if (destination.stat(path) != null) throw new IOException("Destination already exists: " + source.getName());
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot read source folder");
            destination.mkdir(path);
            for (File child : children) upload(destination, path, child);
        } else {
            try (FileInputStream input = new FileInputStream(source)) {
                destination.upload(path, input, source.length());
            }
        }
    }

    public static void moveToLocal(RemoteClient source, RemoteEntry entry, File destination) throws IOException {
        download(source, entry, destination);
        source.delete(entry.path, entry.directory);
    }

    static void requireChild(String directory, RemoteEntry child) throws IOException {
        if (child.path.equals(directory) || !RemotePath.parent(child.path).equals(directory))
            throw new IOException("Server returned a child outside the requested directory");
        RemotePath.validateName(child.name());
    }
}
