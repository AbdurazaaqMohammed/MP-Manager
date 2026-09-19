package io.github.abdurazaaqmohammed.shizuku;

import android.os.ParcelFileDescriptor;

interface IFileService {
    void destroy();
    int version();
    boolean pathExists(String path);
    boolean pathIsDir(String path);
    boolean pathIsFile(String path);
    long pathSize(String path);
    long pathMtime(String path);
    List<String> dirStat(String dirPath);
    long dirSize(String dirPath);
    ParcelFileDescriptor openRead(String path, long maxBytes);
    long writeFile(String path, in ParcelFileDescriptor data, long maxBytes);
    boolean copyFile(String src, String dst);
    boolean copyDir(String src, String dst);
    boolean mkdir(String path);
    boolean deletePath(String path);
    boolean renamePath(String from, String to);
    boolean touchPath(String path);
    boolean touchMtime(String path, long millis);
    String shell(String command, int timeoutSeconds);
    String lastError();
}
