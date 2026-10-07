package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import net.lingala.zip4j.model.enums.CompressionLevel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM regression tests for the 7z paths: creation with the shared level and
 * password, extraction, and the encrypted-candidate detection that decides
 * whether the app asks for a password or shows a plain error.
 */
public class SevenZArchiveTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final char[] PASSWORD = "secret123".toCharArray();

    private File sourceTree() throws IOException {
        File dir = tmp.newFolder("src");
        write(new File(dir, "readme.txt"), "hello 7z world\n".getBytes(StandardCharsets.UTF_8));
        File sub = new File(dir, "sub");
        //noinspection ResultOfMethodCallIgnored
        sub.mkdirs();
        write(new File(sub, "notes.md"), ("# notes\n" + "line\n").getBytes(StandardCharsets.UTF_8));
        write(new File(sub, "中文.txt"), "中文内容测试\n".getBytes(StandardCharsets.UTF_8));
        write(new File(dir, "empty.txt"), new byte[0]);
        return dir;
    }

    private static void write(File file, byte[] data) throws IOException {
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(data);
        }
    }

    private static List<File> sources(File dir) {
        File[] kids = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (kids != null) for (File k : kids) out.add(k);
        return out;
    }

    private static byte[] readAll(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }

    @Test
    public void roundTripWithUltraLevelPreservesContent() throws IOException {
        File dir = sourceTree();
        File archive = new File(tmp.getRoot(), "plain.7z");
        ArchiveUtil.create(archive, sources(dir), null, CompressionLevel.ULTRA);
        assertTrue(archive.length() > 0);

        File out = tmp.newFolder("out-ultra");
        assertTrue(ArchiveUtil.extractWithProgress(archive, out, false, null, null));

        assertArrayEquals(readAll(new File(dir, "readme.txt")), readAll(new File(out, "readme.txt")));
        assertArrayEquals(readAll(new File(dir, "sub/notes.md")), readAll(new File(out, "sub/notes.md")));
        assertArrayEquals(readAll(new File(dir, "sub/中文.txt")), readAll(new File(out, "sub/中文.txt")));
        assertArrayEquals(new byte[0], readAll(new File(out, "empty.txt")));
    }

    @Test
    public void storeLevelWritesUncompressedCopyStreams() throws IOException {
        File dir = sourceTree();
        File archive = new File(tmp.getRoot(), "store.7z");
        ArchiveUtil.create(archive, sources(dir), null, CompressionLevel.NO_COMPRESSION);

        File out = tmp.newFolder("out-store");
        assertTrue(ArchiveUtil.extractWithProgress(archive, out, false, null, null));
        // COPY streams keep the original bytes: the payload size matches exactly.
        assertEquals(readAll(new File(dir, "readme.txt")).length,
                readAll(new File(out, "readme.txt")).length);
        assertArrayEquals(readAll(new File(dir, "readme.txt")), readAll(new File(out, "readme.txt")));
    }

    @Test
    public void encryptedCreateReadsWithPasswordAndDetectsAsEncrypted() throws IOException {
        File dir = sourceTree();
        File archive = new File(tmp.getRoot(), "enc.7z");
        ArchiveUtil.create(archive, sources(dir), PASSWORD, CompressionLevel.NORMAL);

        // A content-encrypted 7z (7-Zip default -p: plain headers) must be
        // reported as password protected - this is the regression for the
        // never-prompts-for-password bug.
        assertTrue(PasswordedArchive.isEncryptedCandidate(archive));

        File out = tmp.newFolder("out-enc");
        assertTrue(ArchiveUtil.extractWithProgress(archive, out, false, PASSWORD, null));
        assertArrayEquals(readAll(new File(dir, "readme.txt")), readAll(new File(out, "readme.txt")));

        // A wrong password has to fail loudly rather than produce garbage.
        File wrong = tmp.newFolder("out-wrong");
        try {
            ArchiveUtil.extractWithProgress(archive, wrong, false, "nope".toCharArray(), null);
            fail("wrong password should throw");
        } catch (IOException expected) {
            // commons-compress signals a bad password as IOException
        }
    }

    @Test
    public void plainSevenZIsNotAnEncryptedCandidate() throws IOException {
        File dir = sourceTree();
        File archive = new File(tmp.getRoot(), "plain2.7z");
        ArchiveUtil.create(archive, sources(dir), null, CompressionLevel.ULTRA);
        assertFalse(PasswordedArchive.isEncryptedCandidate(archive));
    }

    @Test
    public void damagedSevenZIsNotAnEncryptedCandidate() throws IOException {
        File dir = sourceTree();
        File archive = new File(tmp.getRoot(), "damaged.7z");
        ArchiveUtil.create(archive, sources(dir), null, CompressionLevel.ULTRA);
        byte[] all = readAll(archive);
        // Cut past the signature header so the archive cannot be opened at all.
        byte[] cut = new byte[400];
        System.arraycopy(all, 0, cut, 0, cut.length);
        File broken = new File(tmp.getRoot(), "damaged-cut.7z");
        write(broken, cut);
        assertFalse("a broken file is not a password problem",
                PasswordedArchive.isEncryptedCandidate(broken));
    }
}
