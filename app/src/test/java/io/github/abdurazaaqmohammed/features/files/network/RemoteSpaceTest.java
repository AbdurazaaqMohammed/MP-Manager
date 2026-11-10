package io.github.abdurazaaqmohammed.features.files.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteSpaceTest {
    @Test public void usedSpaceIsDerivedFromTheReportedTotalAndFree() {
        RemoteSpace space = RemoteSpace.fromTotalFree(100, 25);
        assertEquals(75, space.usedBytes);
        assertEquals(25, space.freeBytes);
    }

    @Test public void quotaTotalsAreUsedPlusAvailable() {
        assertEquals(100, RemoteSpace.fromQuota(75, 25).totalBytes);
        assertEquals(0, RemoteSpace.fromQuota(0, 0).totalBytes);
    }

    @Test public void freeOnlyDoesNotInventUsedOrTotalSpace() {
        RemoteSpace space = RemoteSpace.available(25);
        assertEquals(-1, space.totalBytes);
        assertEquals(-1, space.usedBytes);
        assertEquals(25, space.freeBytes);
    }

    @Test public void invalidOrOverflowedSpaceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RemoteSpace.fromTotalFree(10, 11));
        assertThrows(IllegalArgumentException.class, () -> RemoteSpace.fromQuota(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> RemoteSpace.available(-1));
        assertThrows(ArithmeticException.class, () -> RemoteSpace.fromQuota(Long.MAX_VALUE, 1));
    }

    private static com.hierynomus.msfscc.fileinformation.ShareInfo smbInfo(long total, long callerFree, long actualFree,
                                                                         int sectors, int bytes) throws Exception {
        byte[] encoded = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putLong(total).putLong(callerFree).putLong(actualFree).putInt(sectors).putInt(bytes).array();
        return com.hierynomus.msfscc.fileinformation.ShareInfo.parseFsFullSizeInformation(
                new com.hierynomus.protocol.commons.buffer.Buffer.PlainBuffer(encoded,
                        com.hierynomus.protocol.commons.buffer.Endian.LE));
    }

    @Test public void smbSpaceUsesAllocationUnitsAndTheAuthenticatedUsersFreeSpace() throws Exception {
        RemoteSpace space = SmbClient.spaceFromInformation(smbInfo(100, 25, 40, 8, 512));
        assertEquals(100 * 4096L, space.totalBytes);
        assertEquals(25 * 4096L, space.freeBytes);
        assertEquals(75 * 4096L, space.usedBytes);
    }

    @Test public void invalidSmbCapacityNeverBecomesAFictitiousVolume() throws Exception {
        assertNull(SmbClient.spaceFromInformation(smbInfo(100, 25, 40, 0, 512)));
        assertNull(SmbClient.spaceFromInformation(smbInfo(10, 25, 40, 8, 512)));
        assertNull(SmbClient.spaceFromInformation(smbInfo(-1, 0, 0, 8, 512)));
    }

    @Test public void overflowingSmbAllocationUnitsAreUnavailable() throws Exception {
        assertNull(SmbClient.spaceFromInformation(smbInfo(Long.MAX_VALUE, 0, 0, 8, 512)));
    }
}
