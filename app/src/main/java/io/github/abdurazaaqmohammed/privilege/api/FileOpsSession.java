/*
 * Privilege layer — public API.
 */
package io.github.abdurazaaqmohammed.privilege.api;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Elevated file operations (read / write / list / copy / move / delete),
 * routed to whichever backend is active. Never null; the returned session
 * mirrors the legacy {@code AccessManager} semantics — operations on paths
 * the active backend cannot reach fail gracefully.
 *
 * Progress-reporting and batch overloads (deleteAll/deleteTree with
 * listeners, copyAllPreserving, moveAll, measureSet) stay on
 * {@link PrivilegeManager} until the backend SPI defines their
 * progress contracts.
 */
public interface FileOpsSession {

    boolean needsElevated(String absPath);

    boolean exists(String absPath);

    boolean elevatedExists(String absPath);

    boolean isDirectory(String absPath);

    boolean isFile(String absPath);

    File[] listWithStat(String dirPath);

    long getSize(String absPath);

    long getMtime(String absPath);

    File stageForRead(String srcAbsPath) throws IOException;

    void writeBack(File stagedCopy, String originalAbsPath) throws IOException;

    void copyFile(String src, String dest, boolean useElevated) throws IOException;

    void copyDir(String src, String dest, boolean useElevated) throws IOException;

    void move(String src, String dest, boolean useElevated) throws IOException;

    void copyPreserving(String src, String dest, boolean directory, boolean useElevated) throws IOException;

    void mkdir(String path, boolean useElevated) throws IOException;

    void delete(String path, boolean useElevated) throws IOException;

    void rename(String oldPath, String newPath, boolean useElevated) throws IOException;

    void touch(String path, boolean useElevated) throws IOException;

    boolean touchMtime(String path, long millis);

    long dirSize(String path);

    List<String> deleteAll(List<String> paths, boolean useElevated) throws IOException;

    void deleteTree(List<String> paths, boolean useElevated) throws IOException;
}
