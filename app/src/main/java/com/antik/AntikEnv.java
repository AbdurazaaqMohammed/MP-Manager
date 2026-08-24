package com.antik;

import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

/**
 * Bridge letting the (originally JVM CLI) antik patcher load its bundled resources
 * from MP Manager's assets instead of the classpath.
 */
public class AntikEnv {
    public static AssetManager assets;

    public static InputStream openResource(String assetPath) throws IOException {
        if (assets == null) throw new IOException("AntikEnv.assets not initialized");
        return assets.open(assetPath);
    }
}
