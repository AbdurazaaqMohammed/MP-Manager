package io.github.abdurazaaqmohammed.packs.ocr;

import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

/** No model weights or network calls are bundled in this optional runtime pack. */
public final class OcrPack implements ToolPack {
    @Override public String packId() { return "ocr"; }
    @Override public int version() { return 1; }
    @Override public List<ToolPlugin> tools() { return Collections.singletonList(new ScreenOcrPlugin()); }
}
