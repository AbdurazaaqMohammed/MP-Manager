package io.github.abdurazaaqmohammed.multiplex.apksign.data;

import java.io.IOException;

public interface DataSink {

    void consume(byte[] buf, int offset, int length) throws IOException;

}
