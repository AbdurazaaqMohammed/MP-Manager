package io.github.abdurazaaqmohammed.features.files.network;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RemoteTransfersTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static class MemoryClient implements RemoteClient {
        final Map<String, byte[]> files = new LinkedHashMap<>();
        boolean failDownload;
        int deletes;
        MemoryClient() { files.put("", null); }
        @Override public List<RemoteEntry> list(String directory) {
            List<RemoteEntry> result = new ArrayList<>();
            for (String path : files.keySet()) if (!path.equals(directory) && RemotePath.parent(path).equals(directory)) result.add(stat(path));
            return result;
        }
        @Override public RemoteEntry stat(String path) {
            if (!files.containsKey(path)) return null;
            byte[] bytes = files.get(path);
            return new RemoteEntry(path, bytes == null, bytes == null ? 0 : bytes.length, 0);
        }
        @Override public void download(String path, OutputStream destination) throws IOException {
            if (failDownload) { destination.write(1); throw new IOException("Simulated connection loss"); }
            destination.write(files.get(path));
        }
        @Override public void upload(String path, InputStream source, long size) throws IOException {
            if (files.containsKey(path)) throw new IOException("Conflict");
            files.put(path, source.readAllBytes());
        }
        @Override public void mkdir(String path) { files.put(path, null); }
        @Override public void rename(String path, String destination) { files.put(destination, files.remove(path)); }
        @Override public void delete(String path, boolean directory) {
            deletes++;
            files.keySet().removeIf(p -> p.equals(path) || p.startsWith(path + "/"));
        }
        @Override public void close() {}
    }

    @Test public void failedMoveKeepsSourceAndRemovesPartialLocalFile() throws Exception {
        MemoryClient client = new MemoryClient();
        client.files.put("source", new byte[]{1, 2, 3});
        client.failDownload = true;
        File output = new File(temporary.getRoot(), "destination");
        assertThrows(IOException.class, () -> RemoteTransfers.moveToLocal(client, client.stat("source"), output));
        assertNotNull(client.stat("source"));
        assertEquals(0, client.deletes);
        assertFalse(output.exists());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void successfulMoveDeletesOnlyAfterCommittedDownload() throws Exception {
        MemoryClient client = new MemoryClient();
        client.files.put("source", new byte[]{1, 2, 3});
        File output = new File(temporary.getRoot(), "destination");
        RemoteTransfers.moveToLocal(client, client.stat("source"), output);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(output.toPath()));
        assertNull(client.stat("source"));
        assertEquals(1, client.deletes);
    }

    @Test public void failedRecursiveMoveKeepsEntireRemoteSubtree() throws Exception {
        MemoryClient client = new MemoryClient();
        client.files.put("source", null);
        client.files.put("source/child", new byte[]{1, 2, 3});
        client.failDownload = true;
        assertThrows(IOException.class, () -> RemoteTransfers.moveToLocal(client, client.stat("source"),
                new File(temporary.getRoot(), "destination")));
        assertNotNull(client.stat("source"));
        assertNotNull(client.stat("source/child"));
        assertEquals(0, client.deletes);
    }

    @Test public void conflictsPreserveBothFiles() throws Exception {
        MemoryClient client = new MemoryClient();
        client.files.put("source", new byte[]{1});
        File output = temporary.newFile("existing");
        Files.write(output.toPath(), new byte[]{9});
        assertThrows(IOException.class, () -> RemoteTransfers.moveToLocal(client, client.stat("source"), output));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(output.toPath()));
        assertNotNull(client.stat("source"));
    }

    @Test public void rejectTruncatedDownloadBeforeDeletingSource() throws Exception {
        MemoryClient client = new MemoryClient();
        client.files.put("source", new byte[]{1});
        assertThrows(IOException.class, () -> RemoteTransfers.moveToLocal(client,
                new RemoteEntry("source", false, 5, 0), new File(temporary.getRoot(), "output")));
        assertEquals(0, client.deletes);
    }

    @Test public void recursiveRoundTripIncludesEmptyFilesAndFolders() throws Exception {
        MemoryClient client = new MemoryClient();
        File source = temporary.newFolder("source");
        assertTrue(new File(source, "empty-folder").mkdir());
        Files.write(new File(source, "empty-file").toPath(), new byte[0]);
        Files.write(new File(source, "data").toPath(), new byte[]{1, 2});
        RemoteTransfers.upload(client, "", source);
        File destination = new File(temporary.getRoot(), "output");
        RemoteTransfers.download(client, client.stat("source"), destination);
        assertTrue(new File(destination, "empty-folder").isDirectory());
        assertTrue(new File(destination, "empty-file").isFile());
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(new File(destination, "data").toPath()));
        assertThrows(IOException.class, () -> RemoteTransfers.upload(client, "", source));
    }

    @Test public void rejectUnsafeServerChildren() {
        assertThrows(IOException.class, () -> RemoteTransfers.requireChild("folder", new RemoteEntry("other/file", false, 1, 0)));
        assertThrows(IOException.class, () -> RemoteTransfers.requireChild("folder", new RemoteEntry("folder", true, 0, 0)));
    }

    @Test public void validateProtocolPathsAndPreserveLiteralPercentNames() {
        assertEquals("folder/測試 %2F +.txt", RemotePath.child("/folder/", "測試 %2F +.txt"));
        assertEquals("folder", RemotePath.parent("folder/file"));
        assertEquals("", RemotePath.parent(""));
        for (String bad : new String[]{"..", ".", "", "a/b", "a\\b", "a\u0000b", "a\nb"})
            assertThrows(IllegalArgumentException.class, () -> RemotePath.validateName(bad));
        assertThrows(IllegalArgumentException.class, () -> RemotePath.normalize("folder/../escape"));
        assertEquals("folder\\file", SmbClient.smbPath("folder/file"));
        assertThrows(IllegalArgumentException.class, () -> SmbClient.smbPath("file:stream"));
    }

    @Test public void validateSmbProfiles() {
        NetworkProfile p = new NetworkProfile();
        p.protocol = "smb";
        p.address = "smb://127.0.0.1:1445/Documents";
        p.validate();
        assertEquals("Documents", p.share());
        assertEquals("smb://127.0.0.1:1445/Documents/", p.canonicalAddress());
        for (String bad : new String[]{"smb://host", "smb://host/", "smb://user:pass@host/share",
                "smb://host/share/folder", "smb://host:0/share", "smb://host/share?password=secret"}) {
            p.address = bad;
            assertThrows(IllegalArgumentException.class, p::validate);
        }
    }

    @Test public void classifyAndroid17LocalNetworksWithoutBlockingPublicServers() throws Exception {
        for (String local : new String[]{"127.0.0.1", "192.168.1.1", "10.1.2.3", "172.16.0.1", "169.254.1.1", "::1", "fd00::1"})
            assertTrue(local, NetworkAccess.isLocal(java.net.InetAddress.getByName(local)));
        assertFalse(NetworkAccess.isLocal(java.net.InetAddress.getByName("8.8.8.8")));
        assertTrue(NetworkAccess.requiresPermission("smb://nas/share/"));
        assertTrue(NetworkAccess.requiresPermission("https://nas.local/dav/"));
    }

    @Test public void onlyMissingSmbStatusesAreTreatedAsAbsent() {
        assertTrue(SmbClient.isMissing(com.hierynomus.mserref.NtStatus.STATUS_NO_SUCH_FILE.getValue()));
        assertTrue(SmbClient.isMissing(com.hierynomus.mserref.NtStatus.STATUS_OBJECT_NAME_NOT_FOUND.getValue()));
        assertTrue(SmbClient.isMissing(com.hierynomus.mserref.NtStatus.STATUS_OBJECT_PATH_NOT_FOUND.getValue()));
        assertFalse(SmbClient.isMissing(com.hierynomus.mserref.NtStatus.STATUS_ACCESS_DENIED.getValue()));
        assertFalse(SmbClient.isMissing(com.hierynomus.mserref.NtStatus.STATUS_LOGON_FAILURE.getValue()));
    }
}
