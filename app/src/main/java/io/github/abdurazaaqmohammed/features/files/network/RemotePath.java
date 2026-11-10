package io.github.abdurazaaqmohammed.features.files.network;

/** Protocol-independent paths, always relative to the configured collection/share. */
public final class RemotePath {
    private RemotePath() {}

    public static String normalize(String path) {
        if (path == null) throw new IllegalArgumentException("Missing path");
        StringBuilder result = new StringBuilder();
        for (String part : path.split("/", -1)) {
            if (part.isEmpty()) continue;
            validateName(part);
            if (result.length() > 0) result.append('/');
            result.append(part);
        }
        return result.toString();
    }

    public static void validateName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Invalid file or folder name");
        }
    }

    public static String child(String parent, String name) {
        validateName(name);
        String p = normalize(parent);
        return p.isEmpty() ? name : p + "/" + name;
    }

    public static String parent(String path) {
        String p = normalize(path);
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    public static String name(String path) {
        String p = normalize(path);
        return p.substring(p.lastIndexOf('/') + 1);
    }
}
