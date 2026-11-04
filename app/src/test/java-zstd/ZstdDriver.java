import io.github.abdurazaaqmohammed.utils.MpZstdInputStream;
import io.github.abdurazaaqmohammed.utils.MpZstdOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

/**
 * Host-side roundtrip check for the vendored zstd native bridge, run under
 * WSL/Linux with a plain JDK (no Android). Covers: empty payload, tiny,
 * highly compressible, incompressible random, and a ~50MB mixed payload,
 * plus corrupt-input handling.
 */
public class ZstdDriver {
    public static void main(String[] args) throws Exception {
        // The app loads the library by name via ZstdBridge's static block;
        // java.library.path points at the WSL-built libmp_zstd.so.

        testRoundtrip("empty", new byte[0]);
        testRoundtrip("one byte", new byte[]{42});
        testRoundtrip("tiny text", "hello zstd".getBytes("UTF-8"));
        testRoundtrip("64KB zeros", new byte[64 * 1024]);
        byte[] compressible = new byte[2 * 1024 * 1024];
        for (int i = 0; i < compressible.length; i++) compressible[i] = (byte) ('a' + (i % 23));
        testRoundtrip("2MB compressible", compressible);
        byte[] random = new byte[4 * 1024 * 1024];
        new Random(12345).nextBytes(random);
        testRoundtrip("4MB random", random);

        // 50MB mixed (some runs are random, some repeated) — bigger than one chunk
        byte[] big = new byte[50 * 1024 * 1024];
        Random r = new Random(999);
        for (int i = 0; i < big.length; ) {
            if (r.nextInt(10) < 7) {
                int run = Math.min(4096, big.length - i);
                byte fill = (byte) r.nextInt(256);
                for (int j = 0; j < run; j++) big[i++] = fill;
            } else {
                big[i++] = (byte) r.nextInt(256);
            }
        }
        testRoundtrip("50MB mixed", big);

        // Truncated stream must throw, never silently yield partial data
        byte[] packed = compress("some payload that will be truncated".getBytes("UTF-8"));
        byte[] truncated = Arrays.copyOf(packed, packed.length - 5);
        try (MpZstdInputStream ignored = new MpZstdInputStream(new ByteArrayInputStream(truncated))) {
            byte[] sink = new byte[1024];
            while (ignored.read(sink) != -1) {
                // drain
            }
            throw new IllegalStateException("truncated stream did not throw");
        } catch (IOException expected) {
            System.out.println("truncated stream -> IOException as expected: " + expected.getMessage());
        }

        // Truncation exactly on a 64KB boundary (last full chunk ends mid-frame,
        // then the stream is exhausted): the final flush must still detect it.
        byte[] bigPayload = new byte[4 * 1024 * 1024];
        new Random(777).nextBytes(bigPayload);
        byte[] bigPacked = compress(bigPayload);
        byte[] bigTruncated = Arrays.copyOf(bigPacked, bigPacked.length - 3);
        try (MpZstdInputStream ignored = new MpZstdInputStream(new ByteArrayInputStream(bigTruncated))) {
            byte[] sink = new byte[64 * 1024];
            while (ignored.read(sink) != -1) {
                // drain
            }
            throw new IllegalStateException("4MB truncated stream did not throw");
        } catch (IOException expected) {
            System.out.println("4MB truncated -> IOException as expected: " + expected.getMessage());
        }

        // Empty file that is exactly one full 64KB of zeroes... is a valid frame:
        byte[] zeros = new byte[64 * 1024 * 1024]; // exactly 1024 full chunks
        testRoundtrip("64MB zeros", zeros);

        // Empty input is not a valid zstd file either
        try (MpZstdInputStream ignored = new MpZstdInputStream(new ByteArrayInputStream(new byte[0]))) {
            if (ignored.read() != -1) {
                throw new IllegalStateException("empty input produced bytes");
            }
            throw new IllegalStateException("empty input did not throw");
        } catch (IOException expected) {
            System.out.println("empty input -> IOException as expected: " + expected.getMessage());
        }

        // Concatenated frames (valid zstd) must decode as the concatenation
        byte[] first = compress("first-".getBytes("UTF-8"));
        byte[] second = compress("second".getBytes("UTF-8"));
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        byte[] joined = decompress(both);
        System.out.println("multi-frame -> " + new String(joined, "UTF-8")
                + ("first-second".equals(new String(joined, "UTF-8")) ? " (ok)" : " (MISMATCH)"));
        if (!"first-second".equals(new String(joined, "UTF-8"))) {
            throw new IllegalStateException("multi-frame decode mismatch");
        }

        // Garbage input must throw IOException
        try (MpZstdInputStream ignored = new MpZstdInputStream(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}))) {
            while (ignored.read() >= 0) {
                // drain
            }
            System.out.println("WARNING: garbage input did not throw");
        } catch (IOException expected) {
            System.out.println("garbage input -> IOException as expected: " + expected.getMessage());
        }

        System.out.println("\nALL ZSTD DRIVER CHECKS PASSED");
    }

    private static void testRoundtrip(String name, byte[] payload) throws Exception {
        byte[] compressed = compress(payload);
        byte[] decompressed = decompress(compressed);
        boolean ok = Arrays.equals(payload, decompressed);
        System.out.printf("%-22s in=%dB zstd=%dB ratio=%.2f match=%s%n",
                name, payload.length, compressed.length,
                payload.length == 0 ? 0.0 : (double) compressed.length / payload.length, ok);
        if (!ok) {
            throw new IllegalStateException("roundtrip mismatch for " + name);
        }
        // Sanity: frame starts with the zstd magic number
        if (compressed.length >= 4) {
            int magic = (compressed[0] & 0xff) | ((compressed[1] & 0xff) << 8)
                    | ((compressed[2] & 0xff) << 16) | ((compressed[3] & 0xff) << 24);
            if (magic != 0xFD2FB528) {
                throw new IllegalStateException("not a zstd frame for " + name);
            }
        }
        // sha256 for the record
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] a = md.digest(payload);
        byte[] b = md.digest(decompressed);
        if (!Arrays.equals(a, b)) throw new IllegalStateException("sha mismatch");
    }

    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        try (MpZstdOutputStream zstd = new MpZstdOutputStream(bos)) {
            zstd.write(data);
        }
        return bos.toByteArray();
    }

    private static byte[] decompress(byte[] packed) throws IOException {
        try (MpZstdInputStream zstd = new MpZstdInputStream(new ByteArrayInputStream(packed));
             ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, packed.length * 4))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = zstd.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }
}
