package io.github.abdurazaaqmohammed.utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipException;

public class ZipUtil {

    public static void copyEntryInZip(File zipFile, String sourceEntryPath, String destinationEntryPath) throws IOException {
        ZipFile zip = new ZipFile(zipFile);
        FileHeader sourceHeader = zip.getFileHeader(sourceEntryPath);
        if (sourceHeader != null) {
            ZipParameters parameters = new ZipParameters();
            parameters.setFileNameInZip(destinationEntryPath);
            zip.addStream(zip.getInputStream(sourceHeader), parameters);
        }
    }

    public static void moveEntryInZip(File zipFile, String sourceEntryPath, String destinationEntryPath) throws IOException {
        copyEntryInZip(zipFile, sourceEntryPath, destinationEntryPath);
        deleteEntryFromZip(zipFile, sourceEntryPath);
    }

    public static void renameEntryInZip(File zipFile, String oldEntryPath, String newEntryPath) throws IOException {
        moveEntryInZip(zipFile, oldEntryPath, newEntryPath);
    }

    public static void deleteEntryFromZip(File zipFile, String entryPath) throws net.lingala.zip4j.exception.ZipException {
        ZipFile zip = new ZipFile(zipFile);
        FileHeader header = zip.getFileHeader(entryPath);
        if (header != null) {
            zip.removeFile(header);
        }
    }

    public static void extractEntryFromZip(File zipFile, String entryPath, File destinationFolder) throws net.lingala.zip4j.exception.ZipException {
        ZipFile zip = new ZipFile(zipFile);
        zip.extractFile(entryPath, destinationFolder.getAbsolutePath());
    }

    public static void addToZip(File zipFile, File fileToAdd, String entryPath) throws ZipException, net.lingala.zip4j.exception.ZipException {
        ZipFile zip = new ZipFile(zipFile);
        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(entryPath);
        if (fileToAdd.isDirectory()) {
            zip.addFolder(fileToAdd, parameters);
        } else {
            zip.addFile(fileToAdd, parameters);
        }
    }
}