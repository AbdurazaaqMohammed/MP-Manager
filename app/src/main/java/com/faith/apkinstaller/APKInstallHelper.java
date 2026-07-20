package com.faith.apkinstaller;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import com.reandroid.apk.APKLogger;
import com.reandroid.archive.InputSource;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.utils.FileUtils;

@TargetApi(21)
public class APKInstallHelper {
    private final PackageInstaller packageInstaller;
    private final MainActivity context;

    public APKInstallHelper(MainActivity context) {
        this.context = context;
        this.packageInstaller = context.getPackageManager().getPackageInstaller();
    }

    public int installApk(InputSource[] apkInputSources) throws IOException {
        long totalSize = 0;

        for (InputSource inputSource: apkInputSources) totalSize += inputSource.getLength();

        PackageInstaller.SessionParams installParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        installParams.setSize(totalSize);

        int sessionId = packageInstaller.createSession(installParams);
        //Log.d(TAG, "Success: created install session [" + sessionId + "]");

        for (InputSource inputSource : apkInputSources) doWriteSession(sessionId, inputSource.openStream());

        doCommitSession(sessionId);
        //Log.d(TAG, "Success");

        return sessionId;
    }

    public int installApk(ZipFile zf, APKLogger logger) throws IOException {
        long totalSize = 0;
        List<FileHeader> fhs = zf.getFileHeaders();
        for (FileHeader fh : fhs) if (fh.getFileName().endsWith(".apk")) totalSize += fh.getUncompressedSize();

        PackageInstaller.SessionParams installParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        installParams.setSize(totalSize);

        int sessionId = packageInstaller.createSession(installParams);
        logger.logMessage("Success: created install session [" + sessionId + "]");

        for (FileHeader fh : fhs) {
            String fileName = fh.getFileName();
            if (fileName.endsWith(".apk") && doWriteSession(sessionId, zf.getInputStream(fh)) == PackageInstaller.STATUS_SUCCESS) {
                logger.logMessage( "Success: added " + fileName);
            }
        }
        logger.logMessage("Installing...");
        doCommitSession(sessionId);
        //Log.d(TAG, "Success");

        return sessionId;
    }

    private int doWriteSession(int sessionId, InputStream inputStream) throws IOException {
        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId);
             OutputStream out = session.openWrite(UUID.randomUUID().toString(), 0, inputStream.available())) {
            FileUtils.copyFile(inputStream, out);
            session.fsync(out);
            //Log.d(TAG, "Success: streamed " + total + " bytes");
            return PackageInstaller.STATUS_SUCCESS;
        } finally {
            if (inputStream != null) inputStream.close();
        }
    }

    private void doCommitSession(int sessionId) throws IOException {
        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
            Intent callbackIntent = new Intent(context, APKInstallService.class);
            PendingIntent pendingIntent = PendingIntent.getService(context, 0, callbackIntent, PendingIntent.FLAG_MUTABLE);
            session.commit(pendingIntent.getIntentSender());
            //Log.d(TAG, "install request sent");
        }
    }
}