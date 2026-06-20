//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.reandroid;

import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Options;
import com.reandroid.apkeditor.Util;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.coder.xml.XmlCoderLogger;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

public class CommandExecutor<T extends Options> implements APKLogger, XmlCoderLogger {
    private final T options;
    private String mLogTag;
    private boolean mEnableLog;
    private final APKLogger logger;

    public CommandExecutor(T options, String logTag, APKLogger logger) {
        this.options = options;
        this.mLogTag = logTag;
        this.mEnableLog = true;
        this.logger = logger;
    }
    protected void applyExtractNativeLibs(ApkModule apkModule, String extractNativeLibs) {
        if (extractNativeLibs != null) {
            Boolean value;
            if ("manifest".equalsIgnoreCase(extractNativeLibs)) {
                if (apkModule.hasAndroidManifest()) {
                    value = apkModule.getAndroidManifest().isExtractNativeLibs();
                } else {
                    value = null;
                }
            } else if ("true".equalsIgnoreCase(extractNativeLibs)) {
                value = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(extractNativeLibs)) {
                value = Boolean.FALSE;
            } else {
                value = null;
            }
            logMessage("Applying: extractNativeLibs=" + value);
            apkModule.setExtractNativeLibs(value);
        }
    }
    /** @deprecated */
    @Deprecated
    public void run() throws IOException {
        this.runCommand();
    }

    public void runCommand() throws IOException {
        throw new RuntimeException("Method not implemented");
    }

    protected void delete(File file) {
        if (file != null && file.exists()) {
            logger.logMessage("Delete: " + file);
            if (file.isFile()) {
                file.delete();
            } else if (file.isDirectory()) {
                Util.deleteDir(file);
            }

        }
    }

    protected T getOptions() {
        return this.options;
    }

    protected void setLogTag(String tag) {
        if (tag == null) tag = "";

        this.mLogTag = tag;
    }

    public void setEnableLog(boolean enableLog) {
        this.mEnableLog = enableLog;
    }

    public void logMessage(String msg) {
        if (this.mEnableLog) {
            logger.logMessage(this.mLogTag + msg);
        }
    }

    public void logError(String msg, Throwable tr) {
        if (this.mEnableLog) {
            logger.logError(this.mLogTag + msg, tr);
        }
    }

    public void logVerbose(String msg) {
        if (this.mEnableLog) {
            logger.logVerbose(this.mLogTag + msg);
        }
    }

    public void logMessage(String tag, String msg) {
        if (this.mEnableLog) {
            logger.logMessage(this.mLogTag + msg);
        }
    }

    public void logVerbose(String tag, String msg) {
        if (this.mEnableLog) {
            logger.logMessage(this.mLogTag + msg);
        }
    }

    protected static void clearMeta(ApkModule module) {
        removeSignature(module);
        module.setApkSignatureBlock(null);
    }

    protected static void removeSignature(ApkModule module) {
        ZipEntryMap archive = module.getZipEntryMap();
        archive.removeIf(Pattern.compile("^META-INF/.+\\.(([MS]F)|(RSA))"));
        archive.remove("stamp-cert-sha256");
    }
}
