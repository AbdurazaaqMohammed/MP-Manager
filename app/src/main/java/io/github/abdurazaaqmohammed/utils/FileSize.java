package io.github.abdurazaaqmohammed.utils;

import java.io.File;

public class FileSize {
    public static String getHumanReadableFileSize(long size) {
        if (size <= 0) {
            return "0B";
        }

        String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;

        double sizeInDouble = (double) size;

        while (sizeInDouble >= 1024 && unitIndex < units.length - 1) {
            sizeInDouble /= 1024;
            unitIndex++;
        }

        return String.format("%.2f%s", sizeInDouble, units[unitIndex]);
    }

}