package io.github.abdurazaaqmohammed.plugins.api.ocr;

import android.graphics.Bitmap;

import java.util.function.BooleanSupplier;

/** Local-only inference. Instances are confined to one worker thread. */
public interface OcrEngine extends AutoCloseable {
    OcrResult recognize(Bitmap image, BooleanSupplier cancelled) throws Exception;

    @Override
    void close() throws Exception;
}
