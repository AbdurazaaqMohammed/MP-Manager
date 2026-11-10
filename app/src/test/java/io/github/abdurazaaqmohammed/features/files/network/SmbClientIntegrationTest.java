package io.github.abdurazaaqmohammed.features.files.network;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.*;

/** Opt-in integration test. Uses a unique folder; never changes existing share contents. */
public class SmbClientIntegrationTest {
    @Test public void authenticatedSmb2RoundTripAndOperations() throws Exception {
        String host = System.getenv("MP_TEST_SMB_HOST");
        Assume.assumeTrue("Set MP_TEST_SMB_HOST to enable a live SMB test", host != null && !host.isEmpty());
        int port = Integer.parseInt(env("MP_TEST_SMB_PORT", "445"));
        try (SmbClient client = new SmbClient(host, port, env("MP_TEST_SMB_SHARE", "test"),
                env("MP_TEST_SMB_USER", ""), env("MP_TEST_SMB_PASSWORD", ""), env("MP_TEST_SMB_DOMAIN", ""))) {
            RemoteSpace space = client.space();
            if (space != null) {
                assertTrue(space.totalBytes >= space.freeBytes);
                assertEquals(space.totalBytes - space.freeBytes, space.usedBytes);
            }
            String folder = "mp-network-test-" + UUID.randomUUID();
            client.mkdir(folder);
            Throwable failure = null;
            try {
                assertTrue(client.stat(folder).directory);
                String file = folder + "/space + 測試.txt";
                byte[] bytes = new byte[]{0, 1, 2, 3, 4};
                client.upload(file, new ByteArrayInputStream(bytes), bytes.length);
                assertEquals(1, client.list(folder).size());
                assertEquals(bytes.length, client.stat(file).size);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                client.download(file, output);
                assertArrayEquals(bytes, output.toByteArray());
                assertThrows(IOException.class, () -> client.upload(file, new ByteArrayInputStream(bytes), bytes.length));
                client.rename(file, folder + "/renamed.txt");
                assertNull(client.stat(file));
                client.upload(folder + "/empty", new ByteArrayInputStream(new byte[0]), 0);
                assertEquals(0, client.stat(folder + "/empty").size);
                output.reset();
                client.download(folder + "/empty", output);
                assertEquals(0, output.size());
                assertThrows(IOException.class, () -> client.rename(folder + "/empty", folder + "/renamed.txt"));
                client.mkdir(folder + "/nested");
                client.upload(folder + "/nested/file", new ByteArrayInputStream(bytes), bytes.length);
            } catch (Exception | AssertionError e) {
                failure = e;
                throw e;
            } finally {
                try { client.delete(folder, true); }
                catch (Exception cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
            assertNull(client.stat(folder));
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
