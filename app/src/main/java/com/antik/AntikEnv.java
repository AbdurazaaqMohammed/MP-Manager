package com.antik;

import android.content.res.AssetManager;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;


public class AntikEnv {
    public static AssetManager assets;
    public static File cacheDir;

    public static InputStream openResource(String assetPath) throws IOException {
        if (assets == null) throw new IOException("AntikEnv.assets not initialized");
        return assets.open(assetPath);
    }

    public static File tempFile(String prefix) {
        File dir = cacheDir != null ? cacheDir : new File(new File(Environment.getExternalStorageDirectory(), "MP Manager"), "tmp");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, prefix + "_" + UUID.randomUUID() + ".dex");
    }
}
