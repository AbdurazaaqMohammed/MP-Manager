package io.github.abdurazaaqmohammed.privilege.internal;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ShizukuDirStatParseTest {

    @Test
    public void parseDirStatLinesSupportsLegacyAndExtendedLinkPermissionRecords() {
        List<String> lines = Arrays.asList(
                "app\0371\0370\0371700000000000\037",
                "bin\0371\0370\0371700000001000\0371\037755",
                "build.prop\0370\0372048\0371700000002000\0370\037644",
                "dangling\0370\0370\0371700000003000\0371\037777"
        );

        File[] rootFiles = LsListingSupport.parseDirStatLines("/system/", lines, false);
        assertNotNull(rootFiles);
        assertEquals(4, rootFiles.length);

        RootFile app = (RootFile) rootFiles[0];
        assertTrue(app.isDirectory());
        assertFalse(app.isLink());
        assertNull(app.getRootMode());

        RootFile bin = (RootFile) rootFiles[1];
        assertTrue(bin.isDirectory());
        assertTrue(bin.isLink());
        assertEquals("755", bin.getRootMode());

        RootFile prop = (RootFile) rootFiles[2];
        assertTrue(prop.isFile());
        assertFalse(prop.isLink());
        assertEquals(2048L, prop.length());
        assertEquals("644", prop.getRootMode());

        File[] shizukuFiles = LsListingSupport.parseDirStatLines("/system", lines, true);
        assertNotNull(shizukuFiles);
        assertEquals(4, shizukuFiles.length);
        ShizukuFile sfBin = (ShizukuFile) shizukuFiles[1];
        assertTrue(sfBin.isDirectory());
        assertTrue(sfBin.isLink());
        assertTrue(sfBin.hasStat());
        assertEquals("755", sfBin.getMode());

        ShizukuFile sfDangling = (ShizukuFile) shizukuFiles[3];
        assertTrue(sfDangling.isFile());
        assertFalse(sfDangling.isDirectory());
        assertTrue(sfDangling.isLink());
    }

    @Test
    public void parseDirStatLinesDistinguishesEmptyFromNullAndParsesSingleCallLs() {
        assertNull(LsListingSupport.parseDirStatLines("/system", null, true));
        File[] empty = LsListingSupport.parseDirStatLines("/system/empty", Collections.emptyList(), false);
        assertNotNull(empty);
        assertEquals(0, empty.length);

        String singleCallOut = String.join("\n",
                "total 16",
                "drwxr-xr-x 2 root root 4096 2026-01-01 10:00 etc",
                "lrwxrwxrwx 1 root root   11 2026-01-01 10:00 bin -> /system/bin",
                "lrwxrwxrwx 1 root root   12 2026-01-01 10:00 broken -> /missing",
                "-rw-r--r-- 1 root root  512 2026-01-01 10:00 build.prop",
                LsListingSupport.LINKS_MARKER,
                "D:bin",
                "F:broken");
        File[] parsed = LsListingSupport.parseLsWithLinksOutput("/system", singleCallOut, false);
        assertNotNull(parsed);
        assertEquals(4, parsed.length);
        assertTrue(parsed[0].isDirectory());
        assertTrue(parsed[1].isDirectory());
        assertTrue(((RootFile) parsed[1]).isLink());
        assertNotNull(((RootFile) parsed[1]).getRootMode());
        assertFalse(parsed[2].isDirectory());
        assertTrue(parsed[2].isFile());
        assertTrue(((RootFile) parsed[2]).isLink());
        assertTrue(parsed[3].isFile());
        assertEquals(512L, parsed[3].length());
    }

    @Test
    public void isAndroidDataPathCoversBothDataAndObb() {
        assertTrue(ShizukuFile.isAndroidDataPath("/storage/emulated/0/Android/data"));
        assertTrue(ShizukuFile.isAndroidDataPath("/storage/emulated/0/Android/data/com.pkg/files"));
        assertTrue(ShizukuFile.isAndroidDataPath("/storage/emulated/0/Android/obb"));
        assertTrue(ShizukuFile.isAndroidDataPath("/storage/emulated/0/Android/obb/com.game/main.obb"));
        assertTrue(ShizukuFile.isAndroidDataPath("/storage/emulated/10/Android/obb/com.game"));
        assertTrue(ShizukuFile.isAndroidDataPath("/sdcard/Android/obb/"));
        assertTrue(ShizukuFile.isAndroidDataPath("/sdcard/Android/data"));

        assertFalse(ShizukuFile.isAndroidDataPath("/storage/emulated/0/Download"));
        assertFalse(ShizukuFile.isAndroidDataPath("/storage/emulated/0/Android/media"));
        assertFalse(ShizukuFile.isAndroidDataPath(""));
        assertFalse(ShizukuFile.isAndroidDataPath((String) null));
    }
}
