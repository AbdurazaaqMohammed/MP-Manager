package io.github.abdurazaaqmohammed.data.repo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * First data-layer facade (convention only). Features should use this
 * instead of scattering java.io.File + FileUtils calls in adapters.
 * Delegates to java.io for now; Root/Shizuku branches move here in Phase 4.
 */
public final class FileRepository {

    public File[] listFiles(File dir) {
        if (dir == null) return new File[0];
        File[] out = dir.listFiles();
        return out != null ? out : new File[0];
    }

    public List<File> listVisible(File dir, boolean showHidden) {
        List<File> result = new ArrayList<>();
        for (File f : listFiles(dir)) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            result.add(f);
        }
        return result;
    }
}
