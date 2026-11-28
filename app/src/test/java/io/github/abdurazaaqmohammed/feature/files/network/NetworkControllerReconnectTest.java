package io.github.abdurazaaqmohammed.feature.files.network;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetworkControllerReconnectTest {
    @Test public void identifiesClosedSmbShareFailuresThroughWrappedCauses() {
        IOException failure = new IOException("SMB: DiskShare has already been closed",
                new IllegalStateException("DiskShare has already been closed"));

        assertTrue(NetworkController.isClosedSmbShareFailure(failure));
        assertFalse(NetworkController.isClosedSmbShareFailure(new IOException("Connection timed out")));
    }
}
