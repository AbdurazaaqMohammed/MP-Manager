package io.github.abdurazaaqmohammed.plugins.api.ocr;

import java.io.File;

import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

/** Optional first-party OCR runtime; the host owns capture, downloads and selection UI. */
public interface OcrToolPlugin extends ToolPlugin {
    OcrEngine createEngine(File detectionModel, File recognitionModel) throws Exception;
}
