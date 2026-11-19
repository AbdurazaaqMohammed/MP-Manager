package io.github.abdurazaaqmohammed.util.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Pure formatting rules behind the "show file permissions" file-list option. */
public class FilePermissionsTest {

    @Test
    public void formatMode_regularBits() {
        assertEquals("rwxr-x--- (750)", FilePermissions.formatMode(0750));
        assertEquals("rw-r--r-- (644)", FilePermissions.formatMode(0644));
        assertEquals("rwxrwxrwx (777)", FilePermissions.formatMode(0777));
        assertEquals("--------- (0)", FilePermissions.formatMode(0));
    }

    @Test
    public void formatMode_specialBits() {
        // setuid without owner exec shows as S, with exec as s
        assertEquals("rwSr--r-- (4644)", FilePermissions.formatMode(04644));
        assertEquals("rwsr-xr-x (4755)", FilePermissions.formatMode(04755));
        // sticky on others
        assertEquals("rwxrwxrwt (1777)", FilePermissions.formatMode(01777));
        assertEquals("rwxrwxrwT (1776)", FilePermissions.formatMode(01776));
    }

    @Test
    public void formatMode_ignoresFileTypeBits() {
        // directory bit masked out, permissions kept
        assertEquals("rwxr-xr-x (755)", FilePermissions.formatMode(040755));
        assertEquals("rw-r--r-- (644)", FilePermissions.formatMode(0100644));
        // symlink 0777
        assertEquals("rwxrwxrwx (777)", FilePermissions.formatMode(0120777));
    }

    @Test
    public void formatLsMode_validColumns() {
        assertEquals("rwxr-x--x (751)", FilePermissions.formatLsMode("rwxr-x--x"));
        assertEquals("rwxr-x--- (750)", FilePermissions.formatLsMode("rwxr-x---"));
        assertEquals("rw-r--r-- (644)", FilePermissions.formatLsMode("rw-r--r--"));
        assertEquals("rwSr--r-- (4644)", FilePermissions.formatLsMode("rwSr--r--"));
        assertEquals("rwxrwxrwt (1777)", FilePermissions.formatLsMode("rwxrwxrwt"));
    }

    @Test
    public void formatLsMode_rejectsInvalidInput() {
        assertNull(FilePermissions.formatLsMode(null));
        assertNull(FilePermissions.formatLsMode(""));
        // type character not stripped by the caller
        assertNull(FilePermissions.formatLsMode("drwxr-xr-x"));
        // single-char directory marker passed as "mode" by some constructors
        assertNull(FilePermissions.formatLsMode("d"));
        assertNull(FilePermissions.formatLsMode("rwxr-x-- "));
        assertNull(FilePermissions.formatLsMode("rwxr-xr-y"));
    }

    @Test
    public void formatOctal_parsesStatOutput() {
        assertEquals("rwxr-x--- (750)", FilePermissions.formatOctal("750"));
        assertEquals("rw-r--r-- (644)", FilePermissions.formatOctal("644"));
        assertEquals("rwsr-xr-x (4755)", FilePermissions.formatOctal("4755"));
        assertEquals("rwxr-x--- (750)", FilePermissions.formatOctal(" 750\n"));
        assertNull(FilePermissions.formatOctal(null));
        assertNull(FilePermissions.formatOctal(""));
        assertNull(FilePermissions.formatOctal("abc"));
        assertNull(FilePermissions.formatOctal("12 34"));
        assertNull(FilePermissions.formatOctal("12345"));
    }
}
