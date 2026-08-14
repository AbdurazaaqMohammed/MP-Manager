package io.github.abdurazaaqmohammed.utils;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

public class ArchiveUtil {

    private static final int BUFFER_SIZE = 8192;

    public static boolean isSupportedArchive(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".7z") || lower.endsWith(".rar") || lower.endsWith(".tar")
                || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".txz")
                || lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz");
    }

    public static String[] getSupportedCreateExts() {
        return new String[] { ".zip", ".7z", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".gz", ".bz2", ".xz" };
    }

    public static void extract(File archive, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".7z")) extract7z(archive, destDir);
        else if (lower.endsWith(".rar")) extractRar(archive, destDir);
        else if (lower.endsWith(".tar")) extractTar(new FileInputStream(archive), destDir);
        else if (lower.endsWith(".tgz")) extractTar(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir);
        else if (lower.endsWith(".tar.gz")) extractTar(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir);
        else if (lower.endsWith(".tbz2")) extractTar(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir);
        else if (lower.endsWith(".tar.bz2")) extractTar(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir);
        else if (lower.endsWith(".txz")) extractTar(new XZCompressorInputStream(new FileInputStream(archive), true), destDir);
        else if (lower.endsWith(".tar.xz")) extractTar(new XZCompressorInputStream(new FileInputStream(archive), true), destDir);
        else if (lower.endsWith(".gz")) extractSingleCompressed(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".gz");
        else if (lower.endsWith(".bz2")) extractSingleCompressed(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".bz2");
        else if (lower.endsWith(".xz")) extractSingleCompressed(new XZCompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".xz");
        else throw new IOException("Unsupported archive format: " + archive.getName());
    }

    public static void create(File output, List<File> sources) throws IOException {
        String lower = output.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".7z")) create7z(output, sources);
        else if (lower.endsWith(".tar")) createTar(new FileOutputStream(output), sources);
        else if (lower.endsWith(".tgz")) createTar(new GzipCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tar.gz")) createTar(new GzipCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tbz2")) createTar(new BZip2CompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tar.bz2")) createTar(new BZip2CompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".txz")) createTar(new XZCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".tar.xz")) createTar(new XZCompressorOutputStream(new FileOutputStream(output)), sources);
        else if (lower.endsWith(".gz") && sources.size() == 1) compressSingle(new GzipCompressorOutputStream(new FileOutputStream(output)), sources.get(0));
        else if (lower.endsWith(".bz2") && sources.size() == 1) compressSingle(new BZip2CompressorOutputStream(new FileOutputStream(output)), sources.get(0));
        else if (lower.endsWith(".xz") && sources.size() == 1) compressSingle(new XZCompressorOutputStream(new FileOutputStream(output)), sources.get(0));
        else throw new IOException("Unsupported archive format: " + output.getName());
    }

    private static void extract7z(File archive, File destDir) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(archive)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                String name = sanitizeEntryName(entry.getName());
                File out = new File(destDir, name);
                if (entry.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(sevenZFile.getInputStream(entry), os);
                    }
                }
            }
        }
    }

    private static void extractRar(File archive, File destDir) throws IOException {
        try (Archive rar = new Archive(archive)) {
            FileHeader fh;
            while ((fh = rar.nextFileHeader()) != null) {
                String name = sanitizeEntryName(fh.getFileName());
                File out = new File(destDir, name);
                if (fh.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        rar.extractFile(fh, os);
                    }
                }
            }
        } catch (com.github.junrar.exception.RarException e) {
            throw new IOException("Failed to extract RAR archive", e);
        }
    }

    private static void extractTar(InputStream tarInput, File destDir) throws IOException {
        try (TarArchiveInputStream tais = new TarArchiveInputStream(tarInput)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                String name = sanitizeEntryName(entry.getName());
                File out = new File(destDir, name);
                if (entry.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(tais, os);
                    }
                }
            }
        }
    }

    private static void extractSingleCompressed(InputStream compressedInput, File destDir, String archiveName, String ext) throws IOException {
        try (InputStream is = compressedInput) {
            String base = archiveName.substring(0, archiveName.length() - ext.length());
            File out = new File(destDir, base);
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                copy(is, os);
            }
        }
    }

    private static void compressSingle(OutputStream compressedOutput, File source) throws IOException {
        try (OutputStream os = new BufferedOutputStream(compressedOutput)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(source))) {
                copy(is, os);
            }
        }
    }

    private static void create7z(File output, List<File> sources) throws IOException {
        try (SevenZOutputFile sevenZOutput = new SevenZOutputFile(output)) {
            sevenZOutput.setContentCompression(SevenZMethod.LZMA2);
            for (File source : sources) addToSevenZ(sevenZOutput, source, source.isDirectory() ? source.getName() + "/" : source.getName());
        }
    }

    private static void addToSevenZ(SevenZOutputFile sevenZOutput, File file, String entryName) throws IOException {
        SevenZArchiveEntry entry = sevenZOutput.createArchiveEntry(file, entryName);
        sevenZOutput.putArchiveEntry(entry);
        if (file.isDirectory()) {
            sevenZOutput.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToSevenZ(sevenZOutput, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""));
        } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copySevenZ(is, sevenZOutput);
            }
            sevenZOutput.closeArchiveEntry();
        }
    }

    private static void copySevenZ(InputStream is, SevenZOutputFile os) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
    }

    private static void createTar(OutputStream tarOutput, List<File> sources) throws IOException {
        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(tarOutput)) {
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            taos.setAddPaxHeadersForNonAsciiNames(true);
            for (File source : sources) addToTar(taos, source, source.isDirectory() ? source.getName() + "/" : source.getName());
        }
    }

    private static void addToTar(TarArchiveOutputStream taos, File file, String entryName) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
        taos.putArchiveEntry(entry);
        if (file.isDirectory()) {
            taos.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToTar(taos, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""));
        } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copy(is, taos);
            }
            taos.closeArchiveEntry();
        }
    }

    private static String sanitizeEntryName(String entryName) {
        String cleaned = entryName.replace('\\', '/');
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        cleaned = cleaned.replaceAll("\\.\\./", "");
        return cleaned;
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
    }
}
